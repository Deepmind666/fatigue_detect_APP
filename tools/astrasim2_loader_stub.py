import json
from typing import Dict, List

# Stub: translate v2 JSON trace into simulator events
# Replace "Simulator" and its APIs with actual Astra-sim 2.0 interfaces when available

class Simulator:
    def __init__(self, bw_gbps: float, lat_ns: int):
        self.bw_gbps = bw_gbps
        self.lat_ns = lat_ns

    def submit_compute(self, rank: int, cycles: int, label: str):
        # TODO: map cycles to time with NPU frequency
        pass

    def submit_msg(self, src: int, dst: int, num_bytes: int, algorithm: str, label: str):
        # TODO: route by algorithm (ring/tree/bucket) to underlying network backend
        pass


def load_trace(json_path: str) -> Dict:
    with open(json_path, "r", encoding="utf-8") as f:
        return json.load(f)


def drive_sim(trace: Dict):
    meta = trace["meta"]
    sim = Simulator(bw_gbps=meta.get("network_bw_gbps", 25.0), lat_ns=meta.get("network_lat_ns", 250))
    world_size = meta["world_size"]

    for step in trace["steps"]:
        # Phase 0: pre_compute
        for rank in range(world_size):
            sim.submit_compute(rank, step["phases"][0]["compute_cycles"], label=f"pre_compute:{step['id']}")

        # Phase 1: dispatch
        for col in step["phases"][1]["collectives"]:
            algo = col.get("algorithm", "ring")
            for flow in col["flows"]:
                sim.submit_msg(flow["src_rank"], flow["dst_rank"], flow["num_bytes"], algo, label=flow["label"])            

        # Phase 2: expert_compute
        for rank in range(world_size):
            sim.submit_compute(rank, step["phases"][2]["compute_cycles"], label=f"expert_compute:{step['id']}")

        # Phase 3: combine
        for col in step["phases"][3]["collectives"]:
            algo = col.get("algorithm", "ring")
            for flow in col["flows"]:
                sim.submit_msg(flow["src_rank"], flow["dst_rank"], flow["num_bytes"], algo, label=flow["label"])           

        # Phase 4: post_compute
        for rank in range(world_size):
            sim.submit_compute(rank, step["phases"][4]["compute_cycles"], label=f"post_compute:{step['id']}")


if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser(description="Astra-sim 2.0 loader stub for MoE trace v2")
    p.add_argument("--trace-json", type=str, required=True)
    args = p.parse_args()
    t = load_trace(args.trace_json)
    drive_sim(t)