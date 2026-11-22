# ZCode 会话计划归档（docs/archived/ai-sessions/zcode-plans）

原 `.zcode/plans/` 的 16 份会话计划原文，用于追溯「当时为什么这么改」。

- **迁移日期**：2026-11-16（13 份，任务 BE-015）；2026-11-18（1 份，任务 BE-042）；2026-11-22（2 份，任务 BE-045 与 BE-049）
- **内容改动**：无，逐字节一致（SHA-256 已比对）
- **原目录**：`.zcode/plans/`（被 gitignore，未入库），已保留 `MIGRATED.md` 指针

## 文件清单

| 文件 | 主题 |
| --- | --- |
| `plan-sess_0551c3f3-5890-4584-b1f1-793d4b312b6d.md` | 方案 v3 首批 A1+A2：双算法验签统一 + JWKS 闭环 |
| `plan-sess_4299cf14-153e-49cf-9fc2-ef9bbeea0aca.md` | 全仓 Javadoc 评审与补齐战役（BE-041；2026-11-18 随 BE-042 迁入） |
| `plan-sess_1d68e5f7-c904-46d9-875f-326c816f0c14.md` | 剩余任务实施计划（接续会话） |
| `plan-sess_279431bd-6b46-4313-b621-7f51a998ab72.md` | Context 式会话计划 |
| `plan-sess_3e256dff-3702-4c92-a667-5bf731a2f9e0.md` | 已完成并验证部分的抽样核对结论 |
| `plan-sess_5455f99d-6514-4012-800a-ca054165487a.md` | 全仓五任务对齐战役计划（2026-11-12） |
| `plan-sess_6ac82648-dd60-4a4a-86a1-e2b9fe83b3b9.md` | Context 式会话计划 |
| `plan-sess_6bc68901-0108-4fd5-89d7-04318ef7fd43.md` | 三项全仓评审与整改方案 |
| `plan-sess_8cf59fd0-7867-42c8-a39a-f7864a033663.md` | Context 式会话计划 |
| `plan-sess_973a93a6-045c-49ec-a6cc-43725c562033.md` | 新增 apex-tools-build 构建发布管理模块 |
| `plan-sess_fb54f920-bfdb-42e1-8975-c9fab04e431a.md` | 多端对接调整方案（PC/小程序/公众号/App 统一账户） |
| `plan-sess_feb20185-f229-42fb-980b-c95106fc12f6.md` | 微服务架构改造方案 |
| `plan-session_0646fc17bc4aa7adfc42b2fe7f64d5e9.md` | 改造目标 |
| `plan-session_ce5d418af532ac5977743f5921c5c92d.md` | 系统功能架构图评审与细化方案 |
| `plan-sess_96a81a21-c400-45c6-b58c-2b354a2cb312.md` | 全仓对齐与图表补齐战役计划（BE-045~BE-048；2026-11-22 随 BE-045 迁入） |
| `plan-sess_451ff4f7-a2ae-45c3-9103-9207e723ce7b.md` | 全模块评审、设计模式/SPI/lambda 优化与报告链修复计划（BE-049；2026-11-22 迁入） |

## 使用约定

1. 归档件是**历史快照**，不作为当前事实依据；与代码冲突时以代码与 `AGENTS.md` 为准。
2. 仍有效的结论提炼进 `docs/specs/` 或 `docs/02-architecture/`，不要直接引用归档件做决策。
3. 归档件只增不改。
