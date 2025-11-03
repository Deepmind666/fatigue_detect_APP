import argparse, json, os, random
from typing import List, Dict, Tuple

# Simple MoE trace generator producing Chakra-compatible JSON

def round_allocation(total: int, weights: List[float]) -> List[int]:
    s = sum(weights)
    if s <= 0:
        weights = [1.0] * len(weights)
        s = float(len(weights))
    norm = [w / s for w in weights]
    raw = [total * w for w in norm]
    ints = [int(x) for x in raw]
    rem = total - sum(ints)
    # distribute remainder by largest fractional parts
    fracs = [(i, raw[i] - ints[i]) for i in range(len(raw))]
    fracs.sort(key=lambda t: t[1], reverse=True)
    for i in range(rem):
        ints[fracs[i % len(fracs)][0]] += 1
    return ints


def expert_to_rank(expert_idx: int, experts: int, world_size: int) -> int:
    # contiguous assignment of experts to ranks
    per = max(1, experts // world_size)
    # allow remainder experts to be placed on last rank
    rank = min(expert_idx // per, world_size - 1)
    return rank


def gen_step_flows(step_id: str, tokens: int, world_size: int, experts: int, top_k: int,
                   bytes_per_token: int, capacity_factor: float, seed: int) -> Dict:
    rnd = random.Random(seed)
    # routing weights across experts (Dirichlet-like via uniform and normalize)
    weights = [rnd.random() + 0.1 for _ in range(experts)]

    # total assignments reflect top-k replication
    total_assignments = tokens * max(1, top_k)
    per_expert_assign = round_allocation(total_assignments, weights)

    # capacity handling: cap per-expert and spill to others proportionally
    cap = int(capacity_factor * (tokens / max(1, experts))) * max(1, top_k)
    overflow = 0
    for i in range(experts):
        if per_expert_assign[i] > cap:
            overflow += per_expert_assign[i] - cap
            per_expert_assign[i] = cap
    if overflow > 0:
        # redistribute overflow by remaining capacity
        caps = [max(0, cap - per_expert_assign[i]) for i in range(experts)]
        total_cap = sum(caps)
        if total_cap > 0:
            add = round_allocation(overflow, [c if c > 0 else 0.0 for c in caps])
            for i in range(experts):
                per_expert_assign[i] += add[i]
        # else: drop overflow (represents drop due to capacity)

    # aggregate flows between ranks
    dispatch_bytes = [[0 for _ in range(world_size)] for _ in range(world_size)]
    combine_bytes = [[0 for _ in range(world_size)] for _ in range(world_size)]

    # equally split origin tokens across ranks (data parallel symmetry)
    per_rank_tokens = round_allocation(tokens, [1.0] * world_size)

    # for each origin rank, proportionally allocate its share to experts
    for origin in range(world_size):
        origin_share_assign = round_allocation(per_rank_tokens[origin] * max(1, top_k), weights)
        for e_idx, assign in enumerate(origin_share_assign):
            if assign == 0:
                continue
            dest = expert_to_rank(e_idx, experts, world_size)
            bytes_amt = assign * bytes_per_token
            dispatch_bytes[origin][dest] += bytes_amt
            combine_bytes[dest][origin] += bytes_amt

    # build Chakra-like structures
    ranks = list(range(world_size))
    flows_dispatch = []
    flows_combine = []
    for s in range(world_size):
        for d in range(world_size):
            b = dispatch_bytes[s][d]
            if b > 0:
                flows_dispatch.append({
                    "src_rank": s,
                    "dst_rank": d,
                    "num_bytes": b,
                    "tensor": "tokens",
                    "step_id": step_id,
                    "phase_index": 1,
                    "label": f"dispatch s{s}->d{d}"
                })
            b2 = combine_bytes[s][d]
            if b2 > 0:
                flows_combine.append({
                    "src_rank": s,
                    "dst_rank": d,
                    "num_bytes": b2,
                    "tensor": "tokens",
                    "step_id": step_id,
                    "phase_index": 3,
                    "label": f"combine s{s}->d{d}"
                })

    phases = [
        {"index": 0, "name": "pre_compute", "collectives": [], "compute_cycles": int(tokens * 300)},
        {"index": 1, "name": "dispatch", "collectives": [{
            "type": "ALL_TO_ALL", "ranks": ranks, "flows": flows_dispatch, "tensor": "tokens"}], "compute_cycles": 0},
        {"index": 2, "name": "expert_compute", "collectives": [], "compute_cycles": int(tokens * 600)},
        {"index": 3, "name": "combine", "collectives": [{
            "type": "ALL_TO_ALL", "ranks": ranks, "flows": flows_combine, "tensor": "tokens"}], "compute_cycles": 0},
        {"index": 4, "name": "post_compute", "collectives": [], "compute_cycles": int(tokens * 200)},
    ]

    return {
        "id": step_id,
        "tokens": tokens,
        "capacity_factor": capacity_factor,
        "experts": experts,
        "top_k": top_k,
        "routing": "softmax",
        "phases": phases,
    }


def generate_trace(world_size: int, experts: int, top_k: int, tokens_list: List[int],
                   bytes_per_token: int, capacity_factor: float, seed: int) -> Dict:
    meta = {
        "world_size": world_size,
        "npus_per_node": world_size,  # simple single-node assumption
        "parallelism": "dp+ep",
        "network": f"ring-npus={world_size}",
        "bytes_per_token": bytes_per_token,
        "version": "moe-chakra-v0"
    }
    steps = []
    for idx, tk in enumerate(tokens_list):
        step_seed = seed + idx
        steps.append(gen_step_flows(step_id=f"step_{idx}", tokens=tk, world_size=world_size,
                                    experts=experts, top_k=top_k, bytes_per_token=bytes_per_token,
                                    capacity_factor=capacity_factor, seed=step_seed))
    return {"meta": meta, "steps": steps}


def parse_args():
    p = argparse.ArgumentParser(description="Generate Chakra-compatible MoE trace JSON")
    p.add_argument("--world-size", type=int, default=4)
    p.add_argument("--experts", type=int, default=8)
    p.add_argument("--top-k", type=int, default=2)
    p.add_argument("--tokens", type=str, default="1024")
    p.add_argument("--bytes-per-token", type=int, default=2048)
    p.add_argument("--capacity-factor", type=float, default=1.25)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--output-json", type=str, required=True)
    return p.parse_args()


def main():
    args = parse_args()
    tokens_list = [int(x) for x in args.tokens.split(",") if x.strip()]
    trace = generate_trace(world_size=args.world_size, experts=args.experts, top_k=args.top_k,
                           tokens_list=tokens_list, bytes_per_token=args.bytes_per_token,
                           capacity_factor=args.capacity_factor, seed=args.seed)
    out = args.output_json
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(trace, f, ensure_ascii=False, indent=2)
    print(f"Wrote JSON: {out}")


if __name__ == "__main__":
    main()