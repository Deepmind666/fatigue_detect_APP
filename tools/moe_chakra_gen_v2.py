import argparse, json, os, random
from typing import List, Dict

# MoE trace generator (v2): EP mapping strategies, network params, algorithm, skew control

def round_allocation(total: int, weights: List[float]) -> List[int]:
    s = sum(weights)
    if s <= 0:
        weights = [1.0] * len(weights)
        s = float(len(weights))
    norm = [w / s for w in weights]
    raw = [total * w for w in norm]
    ints = [int(x) for x in raw]
    rem = total - sum(ints)
    fracs = [(i, raw[i] - ints[i]) for i in range(len(raw))]
    fracs.sort(key=lambda t: t[1], reverse=True)
    for i in range(rem):
        ints[fracs[i % len(fracs)][0]] += 1
    return ints


def gen_weights(experts: int, skew: float, rnd: random.Random) -> List[float]:
    # Dirichlet-like via Gamma(shape=skew, scale=1). skew>1 => more peaky
    # skew==1 => near uniform; skew<1 => heavy-tail
    weights = [rnd.gammavariate(skew, 1.0) for _ in range(experts)]
    # ensure positivity
    for i in range(experts):
        if weights[i] <= 1e-9:
            weights[i] = 1e-6
    return weights


def expert_to_rank(expert_idx: int, experts: int, world_size: int, mapping: str, experts_per_rank: int) -> int:
    if mapping == "round_robin":
        return expert_idx % world_size
    if mapping == "local":
        per = experts_per_rank if experts_per_rank > 0 else max(1, (experts + world_size - 1) // world_size)
        return min(expert_idx // per, world_size - 1)
    # default contiguous blocks (similar to local w/ computed per)
    per = max(1, (experts + world_size - 1) // world_size)
    return min(expert_idx // per, world_size - 1)


def gen_step_flows(step_id: str, tokens: int, world_size: int, experts: int, top_k: int,
                   bytes_per_token: int, capacity_factor: float, seed: int,
                   ep_mapping: str, experts_per_rank: int, algorithm: str, skew: float) -> Dict:
    rnd = random.Random(seed)
    weights = gen_weights(experts, skew, rnd)

    total_assignments = tokens * max(1, top_k)
    per_expert_assign = round_allocation(total_assignments, weights)

    cap = int(capacity_factor * (tokens / max(1, experts))) * max(1, top_k)
    overflow = 0
    for i in range(experts):
        if per_expert_assign[i] > cap:
            overflow += per_expert_assign[i] - cap
            per_expert_assign[i] = cap
    if overflow > 0:
        caps = [max(0, cap - per_expert_assign[i]) for i in range(experts)]
        total_cap = sum(caps)
        if total_cap > 0:
            add = round_allocation(overflow, [c if c > 0 else 0.0 for c in caps])
            for i in range(experts):
                per_expert_assign[i] += add[i]

    dispatch_bytes = [[0 for _ in range(world_size)] for _ in range(world_size)]
    combine_bytes = [[0 for _ in range(world_size)] for _ in range(world_size)]
    per_rank_tokens = round_allocation(tokens, [1.0] * world_size)

    for origin in range(world_size):
        origin_share_assign = round_allocation(per_rank_tokens[origin] * max(1, top_k), weights)
        for e_idx, assign in enumerate(origin_share_assign):
            if assign == 0:
                continue
            dest = expert_to_rank(e_idx, experts, world_size, ep_mapping, experts_per_rank)
            bytes_amt = assign * bytes_per_token
            dispatch_bytes[origin][dest] += bytes_amt
            combine_bytes[dest][origin] += bytes_amt

    ranks = list(range(world_size))
    flows_dispatch = []
    flows_combine = []
    for s in range(world_size):
        for d in range(world_size):
            b = dispatch_bytes[s][d]
            if b > 0:
                flows_dispatch.append({
                    "src_rank": s, "dst_rank": d, "num_bytes": b,
                    "tensor": "tokens", "step_id": step_id, "phase_index": 1,
                    "label": f"dispatch s{s}->d{d}"
                })
            b2 = combine_bytes[s][d]
            if b2 > 0:
                flows_combine.append({
                    "src_rank": s, "dst_rank": d, "num_bytes": b2,
                    "tensor": "tokens", "step_id": step_id, "phase_index": 3,
                    "label": f"combine s{s}->d{d}"
                })

    phases = [
        {"index": 0, "name": "pre_compute", "collectives": [], "compute_cycles": int(tokens * 300)},
        {"index": 1, "name": "dispatch", "collectives": [{
            "type": "ALL_TO_ALL", "ranks": ranks, "flows": flows_dispatch,
            "tensor": "tokens", "algorithm": algorithm}], "compute_cycles": 0},
        {"index": 2, "name": "expert_compute", "collectives": [], "compute_cycles": int(tokens * 600)},
        {"index": 3, "name": "combine", "collectives": [{
            "type": "ALL_TO_ALL", "ranks": ranks, "flows": flows_combine,
            "tensor": "tokens", "algorithm": algorithm}], "compute_cycles": 0},
        {"index": 4, "name": "post_compute", "collectives": [], "compute_cycles": int(tokens * 200)},
    ]

    return {
        "id": step_id,
        "tokens": tokens,
        "capacity_factor": capacity_factor,
        "experts": experts,
        "top_k": top_k,
        "routing": "softmax",
        "weights_skew": skew,
        "phases": phases,
    }


def generate_trace(world_size: int, experts: int, top_k: int, tokens_list: List[int],
                   bytes_per_token: int, capacity_factor: float, seed: int,
                   ep_mapping: str, experts_per_rank: int, algorithm: str,
                   network_bw_gbps: float, network_lat_ns: int) -> Dict:
    meta = {
        "world_size": world_size,
        "npus_per_node": world_size,
        "parallelism": "dp+ep",
        "topology": f"ring-npus={world_size}",
        "network_bw_gbps": network_bw_gbps,
        "network_lat_ns": network_lat_ns,
        "bytes_per_token": bytes_per_token,
        "version": "moe-chakra-v2",
    }
    steps = []
    for idx, tk in enumerate(tokens_list):
        step_seed = seed + idx
        steps.append(gen_step_flows(step_id=f"step_{idx}", tokens=tk, world_size=world_size,
                                    experts=experts, top_k=top_k, bytes_per_token=bytes_per_token,
                                    capacity_factor=capacity_factor, seed=step_seed,
                                    ep_mapping=ep_mapping, experts_per_rank=experts_per_rank,
                                    algorithm=algorithm, skew=1.25))
    return {"meta": meta, "steps": steps}


def parse_args():
    p = argparse.ArgumentParser(description="Generate Chakra-compatible MoE trace JSON (v2)")
    p.add_argument("--world-size", type=int, default=4)
    p.add_argument("--experts", type=int, default=8)
    p.add_argument("--top-k", type=int, default=2)
    p.add_argument("--tokens", type=str, default="1024")
    p.add_argument("--bytes-per-token", type=int, default=2048)
    p.add_argument("--capacity-factor", type=float, default=1.25)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--ep-mapping", type=str, choices=["contiguous","round_robin","local"], default="contiguous")
    p.add_argument("--experts-per-rank", type=int, default=0)
    p.add_argument("--algorithm", type=str, choices=["ring","tree","bucket"], default="ring")
    p.add_argument("--network-bw-gbps", type=float, default=25.0)
    p.add_argument("--network-lat-ns", type=int, default=250)
    p.add_argument("--skew", type=float, default=1.25)
    p.add_argument("--output-json", type=str, required=True)
    return p.parse_args()


def main():
    args = parse_args()
    tokens_list = [int(x) for x in args.tokens.split(",") if x.strip()]
    trace = generate_trace(world_size=args.world_size, experts=args.experts, top_k=args.top_k,
                           tokens_list=tokens_list, bytes_per_token=args.bytes_per_token,
                           capacity_factor=args.capacity_factor, seed=args.seed,
                           ep_mapping=args.ep_mapping, experts_per_rank=args.experts_per_rank,
                           algorithm=args.algorithm, network_bw_gbps=args.network_bw_gbps,
                           network_lat_ns=args.network_lat_ns)
    # Inject skew in each step via generator (already set default)
    for s in trace["steps"]:
        s["weights_skew"] = args.skew
    out = args.output_json
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(trace, f, ensure_ascii=False, indent=2)
    print(f"Wrote JSON: {out}")


if __name__ == "__main__":
    main()