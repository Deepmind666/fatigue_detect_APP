# MoE Trace v2 使用说明与Astra-sim 2.0集成

## 新增能力
- EP映射策略：`contiguous`、`round_robin`、`local(每rank固定专家数)`。
- 网络参数：`network_bw_gbps`、`network_lat_ns` 写入 `meta`。
- 集体算法：`algorithm in {ring, tree, bucket}` 写入每个 `collective`。
- 路由不均匀度：`weights_skew`（Dirichlet/Gamma形状参数），用于控制专家负载偏斜。

## 生成器命令
```powershell
python D:\juice2\tools\moe_chakra_gen_v2.py \
  --world-size 8 --experts 64 --top-k 2 \
  --tokens 1024,4096 --bytes-per-token 2048 \
  --capacity-factor 1.15 --seed 777 \
  --ep-mapping local --experts-per-rank 8 \
  --algorithm ring --network-bw-gbps 25.0 --network-lat-ns 250 \
  --skew 1.5 \
  --output-json D:\juice2\docs\samples\moe_trace_v2_local_ring.json
```

## 输出结构
- `meta`: `world_size, npus_per_node, parallelism, topology, network_bw_gbps, network_lat_ns, bytes_per_token, version`。
- `steps[N]`:
  - 关键属性：`tokens, capacity_factor, experts, top_k, routing, weights_skew`。
  - `phases`:
    - `dispatch`/`combine`：各含一个 `collective`，字段有 `type=ALL_TO_ALL, ranks, flows, tensor, algorithm`。
    - 其余 `pre/expert/post_compute`：用 `compute_cycles` 占位，留待策略决定重叠。

## Astra-sim 2.0 集成建议
- 解析器读取 `flows` 并按 `num_bytes` 生成网络事件；网络后端据 `network_bw_gbps` 和 `network_lat_ns` 估计时延。
- `algorithm` 字段用于选择集体实现（如 ring 的分桶顺序）。若2.0提供更细接口，可将 `flows` 转译为具体消息序列。
- EP映射与 DP/EP 关系：当前假设每步数据并行均匀切分；`local` 映射下每rank持有固定数量专家，方便评估跨rank的 dispatch/combine 流量。

## 适配提示
- 若要进一步对齐到实际集体实现，可把 `flows` 细化到桶/阶段（bucket index）——目前按(src,dst)聚合，便于快速仿真。
- 如需 Protobuf：参考 `schemas/moe_chakra_v2.proto`，用 `protoc` 生成绑定并序列化。

## 示例文件
- `D:\juice2\docs\samples\moe_trace_v2_local_ring.json`：8 rank、每rank 8 专家、ring 算法、轻度偏斜。