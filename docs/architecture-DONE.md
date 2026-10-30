# Spire Ledger architecture: removed items

从 [architecture.md](architecture.md) 移出的条目，原文保留。

## 2026-10-30

- 构建档：“`full`（默认，含 `replay`）；`no-replay`（剔除 `replay` 与 SaveState，同一 modid），作为上游拒绝授权时的退路，平时不发布。”
- M4：“`no-replay` 构建档要求回合重打可剔除，届时拆出 `spireledger.record` 与 `spireledger.replay`（proposed）。”

  结案：上游 STSStateSaver 2026-10-29 加了 MIT 许可证（`a762bc1cae`），这一退路不再需要，用户决定删除（2026-10-30）。该构建档从未实现，代码无改动。
