import os
import json
import argparse
from typing import Dict, List, Tuple

import plotly.graph_objects as go
import plotly.io as pio
from plotly.subplots import make_subplots


def load_trace(path: str) -> Dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def build_step_matrices(step: Dict, world_size: int) -> Tuple[List[List[int]], List[List[int]], int, int, int]:
    """Return (dispatch_matrix, combine_matrix, dispatch_bytes, combine_bytes, msg_count)"""
    dm = [[0 for _ in range(world_size)] for _ in range(world_size)]
    cm = [[0 for _ in range(world_size)] for _ in range(world_size)]
    dispatch_bytes = 0
    combine_bytes = 0
    msg_count = 0

    # Phase 1: dispatch ALL_TO_ALL
    for col in step["phases"][1]["collectives"]:
        for flow in col["flows"]:
            s, d, b = flow["src_rank"], flow["dst_rank"], int(flow["num_bytes"])
            dm[s][d] += b
            dispatch_bytes += b
            msg_count += 1
    # Phase 3: combine ALL_TO_ALL
    for col in step["phases"][3]["collectives"]:
        for flow in col["flows"]:
            s, d, b = flow["src_rank"], flow["dst_rank"], int(flow["num_bytes"])
            cm[s][d] += b
            combine_bytes += b
            msg_count += 1

    return dm, cm, dispatch_bytes, combine_bytes, msg_count


def estimate_comm_time_ms(dispatch_bytes: int, combine_bytes: int, msg_count: int, bw_gbps: float, lat_ns: float) -> float:
    bw_bytes_per_s = bw_gbps * 1e9 / 8.0
    data_ms = ((dispatch_bytes + combine_bytes) / bw_bytes_per_s) * 1e3
    lat_ms = (msg_count * lat_ns) / 1e6
    return data_ms + lat_ms


def gen_figure(trace: Dict, bw_gbps: float = None, lat_ns: float = None) -> go.Figure:
    meta = trace["meta"]
    world_size = int(meta["world_size"]) if "world_size" in meta else len(trace["steps"][0]["phases"][1]["collectives"][0]["ranks"])  # fallback
    bw_gbps = bw_gbps if bw_gbps is not None else float(meta.get("network_bw_gbps", 25.0))
    lat_ns = lat_ns if lat_ns is not None else float(meta.get("network_lat_ns", 250))

    steps = trace["steps"]

    # Precompute per-step matrices and estimates
    per_step = []
    tokens_list = []
    est_ms_list = []

    for st in steps:
        dm, cm, db, cb, mc = build_step_matrices(st, world_size)
        est_ms = estimate_comm_time_ms(db, cb, mc, bw_gbps, lat_ns)
        per_step.append({"dispatch": dm, "combine": cm, "dispatch_bytes": db, "combine_bytes": cb, "msg_count": mc, "est_ms": est_ms, "tokens": int(st.get("tokens", 0))})
        tokens_list.append(int(st.get("tokens", 0)))
        est_ms_list.append(est_ms)

    # Subplots: 2x2 (dispatch heatmap, combine heatmap, tokens->ms line occupying bottom row)
    fig = make_subplots(rows=2, cols=2, specs=[[{"type": "heatmap"}, {"type": "heatmap"}], [{"colspan": 2, "type": "xy"}, None]],
                        subplot_titles=("Dispatch bytes (ALL_TO_ALL)", "Combine bytes (ALL_TO_ALL)", "Tokens → estimated wall-time (communication only)"))

    # Initial frame = step 0 heatmaps
    z_dispatch = per_step[0]["dispatch"]
    z_combine = per_step[0]["combine"]
    fig.add_trace(go.Heatmap(z=z_dispatch, colorscale="Viridis", colorbar=dict(title="Bytes"), showscale=True), row=1, col=1)
    fig.add_trace(go.Heatmap(z=z_combine, colorscale="Viridis", colorbar=dict(title="Bytes"), showscale=True), row=1, col=2)

    # Line plot of tokens->ms and a marker for current step
    fig.add_trace(go.Scatter(x=tokens_list, y=est_ms_list, mode="lines+markers", name="Comm est (ms)"), row=2, col=1)
    # Marker for current frame
    fig.add_trace(go.Scatter(x=[tokens_list[0]], y=[est_ms_list[0]], mode="markers", marker=dict(size=14, color="red"), name="current step"), row=2, col=1)

    # Frames (animate across steps)
    frames = []
    for i, st in enumerate(per_step):
        frames.append(go.Frame(data=[
            go.Heatmap(z=st["dispatch"], colorscale="Viridis", showscale=True),
            go.Heatmap(z=st["combine"], colorscale="Viridis", showscale=True),
            go.Scatter(x=tokens_list, y=est_ms_list, mode="lines+markers", name="Comm est (ms)"),
            go.Scatter(x=[st["tokens"]], y=[st["est_ms"]], mode="markers", marker=dict(size=14, color="red"), name="current step"),
        ], name=f"step_{i}") )

    fig.frames = frames

    fig.update_layout(
        width=1150, height=800,
        title=f"MoE Dispatch/Combine & Tokens→Wall-time | bw={bw_gbps}Gbps lat={lat_ns}ns",
        xaxis_title="Tokens",
        yaxis_title="Estimated wall-time (ms)",
        updatemenus=[{
            "type": "buttons",
            "buttons": [
                {"label": "Play", "method": "animate", "args": [None, {"fromcurrent": True, "frame": {"duration": 1500, "redraw": True}, "transition": {"duration": 300}}]},
                {"label": "Pause", "method": "animate", "args": [[None], {"mode": "immediate", "frame": {"duration": 0}, "transition": {"duration": 0}}]},
            ],
            "direction": "left",
            "pad": {"r": 10, "t": 10},
            "x": 0.0, "y": 1.08, "xanchor": "left", "yanchor": "top"
        }]
    )

    return fig


def save_html(fig: go.Figure, output_path: str, autoplay_loop: bool = True):
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    html = pio.to_html(fig, full_html=True, include_plotlyjs="cdn")
    if autoplay_loop:
        # Inject JS to auto-play and loop
        loop_js = """
        <script>
        document.addEventListener('DOMContentLoaded', function() {
          const gd = document.querySelector('.plotly');
          if (!gd) return;
          function startLoop() {
            let idx = 0;
            function advance() {
              const frames = (gd._fullLayout || {}).frames || [];
              Plotly.animate(gd, null, {frame: {duration: 1500, redraw: true}, transition: {duration: 300}});
              idx = (idx + 1) % frames.length;
            }
            setInterval(advance, 1800);
          }
          // small delay to ensure plotly is ready
          setTimeout(startLoop, 800);
        });
        </script>
        """
        html = html.replace("</body>", loop_js + "\n</body>")
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Wrote HTML: {output_path}")


def main():
    parser = argparse.ArgumentParser(description="Plot MoE trace (v2) interactive dashboard")
    parser.add_argument("--trace-json", type=str, default="D:/juice2/docs/samples/moe_trace_v2_local_ring.json")
    parser.add_argument("--output", type=str, default="D:/juice2/results/moe_trace_interactive.html")
    parser.add_argument("--bw-gbps", type=float, default=None, help="Override bandwidth (Gbps); defaults to meta.network_bw_gbps")
    parser.add_argument("--lat-ns", type=float, default=None, help="Override latency (ns); defaults to meta.network_lat_ns")
    args = parser.parse_args()

    trace = load_trace(args.trace_json)
    fig = gen_figure(trace, bw_gbps=args.bw_gbps, lat_ns=args.lat_ns)
    save_html(fig, args.output, autoplay_loop=True)


if __name__ == "__main__":
    main()