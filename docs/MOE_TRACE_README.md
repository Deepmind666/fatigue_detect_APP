# MoE 动态 Trace 生成与Astra-sim 2.0集成指南

## 文件结构
- `schemas/moe_chakra.proto`: 最小可用的 Chakra/Protobuf schema（MoE取向）。
- `tools/moe_chakra_gen.py`: 生成器脚本，输出 Chakra 兼容的 JSON。
- `docs/samples/moe_trace_sample.json`: 示例输出（world=4, experts=8, top_k=2, tokens=[1024,2048]）。

## 生成器用法
```powershell
python D:\juice2\tools\moe_chakra_gen.py \
  --world-size 4 --experts 8 --top-k 2 \
  --tokens 1024,2048 --bytes-per-token 2048 \
  --capacity-factor 1.25 --seed 123 \
  --output-json D:\juice2\docs\samples\moe_trace.json
```
参数含义：
- `world-size`: rank数量（DP视角）。
- `experts`: 总专家数，按连续方式映射到rank（EP）。
- `top-k`: MoE路由的K值；总派发量为`tokens*top_k`。
- `tokens`: 每step的token数，逗号分隔。
- `bytes-per-token`: 每token载荷（字节），用于估算通信量。
- `capacity-factor`: 专家容量系数；超过容量的分配将溢出并按剩余容量再分配（无法分配的视为丢弃）。
- `seed`: 随机种子，稳定生成可复现实验。

## 输出结构（Chakra兼容JSON）
顶层：
```json
{
  "meta": {"world_size": 4, "parallelism": "dp+ep", ...},
  "steps": [ { step }, { step } ]
}
```
每个`step`包含：
- 关键属性：`tokens`, `capacity_factor`, `experts`, `top_k`, `routing`。
- `phases`：
  - `pre_compute`/`expert_compute`/`post_compute`：使用`compute_cycles`占位。
  - `dispatch`与`combine`：各含一个`ALL_TO_ALL` `collective`，内部是按(src,dst)聚合的`flows`，字段有`src_rank`,`dst_rank`,`num_bytes`,`tensor`,`step_id`,`phase_index`等。

## 与Astra-sim 2.0的集成路径
- Workload驱动读取此JSON，将`collective.type == "ALL_TO_ALL"`的`flows`映射为网络事件；可直接按`num_bytes`入队至网络后端。
- `compute_cycles`可按比例换算为`ns/ms`并入离散事件引擎；或由策略模块决定重叠。
- 若需Protobuf：使用`schemas/moe_chakra.proto`，用`protoc`生成语言绑定后，按同字段序列化。示例（Python）：
```powershell
protoc --python_out=. D:\juice2\schemas\moe_chakra.proto
```
- EP映射：当前采用“连续专家映射到rank”的简化模型；可替换为真实EP分片映射（例如每rank持有`E_local`个专家或跨rank分布式专家）。

## 可扩展点
- 更细的路由统计：将`flows`按expert粒度保留（现为按rank聚合）。
- 拓扑：`meta.network`可扩展为多层拓扑/不同集体算法（ring/tree/bucket）。
- 负载动态：为每`step`提供不同权重分布/容量设置，或引入延迟/拥塞模型。

## 适配到Astra-sim 1.0（可选）
- 若需要快速实验，可将`ALL_TO_ALL`的`flows`展开为每rank的`send/recv`脚本；此README保留为后续补充，不强制依赖。

## 质量保证
- 生成器使用确定性随机种子，保证同参数重复运行输出一致。
- 所有目录在写入前自动创建；JSON为人类可读，便于审阅与版本化。