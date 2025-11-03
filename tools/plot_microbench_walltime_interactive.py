import os
import re
import argparse
import sys
from typing import Dict, List, Tuple

# Make official repo root available on sys.path at runtime via PYTHONPATH
# We will import Chakra protolib modules from the official repo when executing.

import plotly.graph_objects as go
import plotly.io as pio

try:
    from extern.graph_frontend.chakra.schema.protobuf.et_def_pb2 import GlobalMetadata, Node
    from extern.graph_frontend.chakra.src.third_party.utils.protolib import openFileRd as open_file_rd
    from extern.graph_frontend.chakra.src.third_party.utils.protolib import decodeMessage as decode_message
except Exception:
    # Defer import error to runtime instructions: user must set PYTHONPATH to official repo root
    GlobalMetadata = None
    Node = None
    open_file_rd = None
    decode_message = None


def parse_size_bytes(label: str) -> int:
    """Parse size like '1MB', '8MB', '64MB' into bytes."""
    m = re.match(r"^(\d+)MB$", label, re.IGNORECASE)
    if not m:
        raise ValueError(f"Unknown size label: {label}")
    mb = int(m.group(1))
    return mb * 1024 * 1024


def scan_benchmark_dirs(base_dir: str) -> List[Tuple[int, str, str]]:
    """Scan subdirs like '4npus_1MB', return list of (npus, size_label, path)."""
    res: List[Tuple[int, str, str]] = []
    if not os.path.isdir(base_dir):
        return res
    for name in os.listdir(base_dir):
        full = os.path.join(base_dir, name)
        if not os.path.isdir(full):
            continue
        m = re.match(r"^(\d+)npus_(\d+MB)$", name, re.IGNORECASE)
        if m:
            npus = int(m.group(1))
            size_label = m.group(2).upper()
            res.append((npus, size_label, full))
    res.sort(key=lambda t: (t[0], parse_size_bytes(t[1])))
    return res


def decode_comm_bytes_per_npu(prefix_dir: str, et_stem: str, npus: int) -> Dict[int, int]:
    """Sum comm_size from ET files: prefix_dir contains files <et_stem>.<rank>.et."""
    if open_file_rd is None:
        raise RuntimeError("Chakra protolib not available; ensure PYTHONPATH includes official repo root")
    res: Dict[int, int] = {i: 0 for i in range(npus)}
    for npu in range(npus):
        et_path = os.path.join(prefix_dir, f"{et_stem}.{npu}.et")
        if not os.path.exists(et_path):
            raise FileNotFoundError(f"ET file not found: {et_path}")
        et = open_file_rd(et_path)
        gm = GlobalMetadata()
        node = Node()
        decode_message(et, gm)
        while decode_message(et, node):
            for attr in node.attr:
                if attr.name == "comm_size":
                    res[npu] += int(attr.int64_val)
                    break
        et.close()
    return res


def estimate_walltime_ms(comm_bytes: int, bw_gbps: float, latency_ns: float, steps: int) -> float:
    bw_bytes_per_s = bw_gbps * 1e9 / 8.0
    data_ns = (comm_bytes / bw_bytes_per_s) * 1e9
    return (data_ns + steps * latency_ns) / 1e6


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--official-root", type=str, default="D:/astra-sim-official")
    parser.add_argument("--benchmark", type=str, default="reduce_scatter", choices=["reduce_scatter","allreduce","allgather","broadcast"])
    parser.add_argument("--output", type=str, default=None)
    parser.add_argument("--bw-gbps", type=float, default=50.0)
    parser.add_argument("--lat-ns", type=float, default=500.0)
    args = parser.parse_args()

    base_dir = os.path.join(args.official_root, "examples", "workload", "microbenchmarks", args.benchmark)
    combos = scan_benchmark_dirs(base_dir)
    if not combos:
        raise SystemExit(f"No benchmark subdirs found under {base_dir}")

    # Deduce ET stem by sampling first dir
    sample_dir = combos[0][2]
    et_files = [f for f in os.listdir(sample_dir) if f.endswith(".et")]
    if not et_files:
        raise SystemExit(f"No ET files found in {sample_dir}")
    # typical format: reduce_scatter.<rank>.et -> stem = reduce_scatter
    et_stem = et_files[0].split(".")[0]

    # Build caches per combo
    est_cache: Dict[Tuple[int,str], Dict[int, float]] = {}
    bytes_cache: Dict[Tuple[int,str], Dict[int, int]] = {}

    # Determine output path
    out_dir = os.path.join(args.official_root, "results")
    os.makedirs(out_dir, exist_ok=True)
    if args.output:
        out_html = args.output
    else:
        out_html = os.path.join(out_dir, f"{args.benchmark}_walltime_interactive.html")

    # Create initial figure for first combo
    npus0, size0, path0 = combos[0]
    bytes0 = decode_comm_bytes_per_npu(path0, et_stem, npus0)
    steps0 = max(1, npus0 - 1)
    est0 = {i: estimate_walltime_ms(bytes0[i], args.bw_gbps, args.lat_ns, steps0) for i in range(npus0)}
    est_cache[(npus0,size0)] = est0
    bytes_cache[(npus0,size0)] = bytes0

    indices0 = list(range(npus0))
    fig = go.Figure(
        data=[go.Bar(x=indices0, y=[est0[i] for i in indices0], name="Estimated Wall-time (ms)", marker_color="#4C78A8")],
        layout=go.Layout(
            title=f"{args.benchmark} — Estimated Wall-time per NPU",
            xaxis_title="NPU rank",
            yaxis_title="Wall-time (ms)",
            updatemenus=[{
                "type": "buttons",
                "buttons": [
                    {"label": "Play", "method": "animate", "args": [None, {"frame": {"duration": 600, "redraw": True}, "transition": {"duration": 300}, "fromcurrent": True}]},
                    {"label": "Pause", "method": "animate", "args": [[None], {"mode": "immediate", "frame": {"duration": 0, "redraw": False}}]}
                ]
            }]
        )
    )

    # Frames across (npus, size)
    frames = []
    for (npus, size_label, dpath) in combos:
        key = (npus, size_label)
        if key not in bytes_cache:
            bmap = decode_comm_bytes_per_npu(dpath, et_stem, npus)
            bytes_cache[key] = bmap
            steps = max(1, npus - 1)
            est_map = {i: estimate_walltime_ms(bmap[i], args.bw_gbps, args.lat_ns, steps) for i in range(npus)}
            est_cache[key] = est_map
        est_map = est_cache[key]
        indices = list(range(npus))
        frames.append(go.Frame(
            name=f"npus_{npus}_size_{size_label}",
            data=[go.Bar(x=indices, y=[est_map[i] for i in indices])],
            layout=go.Layout(title_text=f"{args.benchmark} — Wall-time per NPU (npus={npus}, size={size_label}, bw={args.bw_gbps}Gbps, lat={args.lat_ns}ns)")
        ))

    fig.frames = frames

    # Slider for sizes and dropdown for npus; keep Play/Pause buttons by appending dropdown
    sizes_for_np0 = [size for (n,size,_) in combos if n == npus0]
    dropdown_buttons = [{
        "label": f"npus {n}",
        "method": "animate",
        "args": [[f"npus_{n}_size_{s}"] , {"mode": "immediate", "frame": {"duration": 0, "redraw": True}}]
    } for (n,s,_) in combos if s == sizes_for_np0[0]]

    existing_upmenus = list(fig.layout.updatemenus) if fig.layout.updatemenus else []
    existing_upmenus.append({"type": "dropdown", "buttons": dropdown_buttons})

    fig.update_layout(
        sliders=[{
            "steps": [{
                "label": s,
                "method": "animate",
                "args": [[f"npus_{npus0}_size_{s}"], {"mode": "immediate", "frame": {"duration": 0, "redraw": True}}]
            } for s in sizes_for_np0],
            "currentvalue": {"prefix": "size: "}
        }],
        updatemenus=existing_upmenus
    )

    # Write HTML with auto-looping JS to cycle frames indefinitely
    html = pio.to_html(fig, include_plotlyjs='cdn', full_html=True, div_id="microbench_plot", auto_play=False)
    html += """
<script>
(function(){
  const div = document.getElementById('microbench_plot');
  if(!div) return;
  // Collect frame names for cycling
  const frames = (div._transitionData && div._transitionData._frames) || div._frames || [];
  const names = frames.map(f => f.name);
  if(names.length === 0) return;
  let idx = 0;
  let playing = true;
  function step(){
    if(!playing) return;
    idx = (idx + 1) % names.length;
    Plotly.animate(div, [names[idx]], {mode:'immediate', frame:{duration:600, redraw:true}, transition:{duration:300}});
    setTimeout(step, 700);
  }
  // Start looping after initial render
  setTimeout(step, 800);
  // Expose a pause/resume toggle via keyboard (space bar)
  window.addEventListener('keydown', function(e){
    if(e.code === 'Space') { playing = !playing; if(playing) step(); }
  });
})();
</script>
"""
    with open(out_html, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Saved interactive HTML to {out_html}")


if __name__ == "__main__":
    main()