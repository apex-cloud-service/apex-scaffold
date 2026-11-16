# 实施与迁移计划（docs/plans）

存放可执行的拆解计划：重构步骤、分批迁移、版本迭代排期。

与 `specs/` 分工：`specs` 回答「做不做、做成什么样」；`plans` 回答「分几步、每步怎么验、怎么回滚」。

子目录：

| 目录 | 内容 |
| --- | --- |
| `archived-ai-sessions/` | 原 `.trae/documents` 迁入的 4 份历史设计/迁移文档（只读归档） |

计划模板：每步必须写「改动范围 / 验证命令（`mvnd -pl … -am`）/ 回滚动作」。

> 既有计划文档仍保留在 `docs/08-project-management/plans/`，不搬迁；新计划写入本目录。
