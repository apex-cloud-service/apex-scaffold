# docs/reference（受控参考）

## 用途与边界

- 唯一的受控子目录是 [`ai-knowledge/`](./ai-knowledge/README.md)：AI 协作知识库（接口契约图、变更记忆台账、领域速查等）。
- 这里的内容**进入提交必读**：改动代码/文档须同步 `ai-knowledge/*change-memory*.md` 台账（规范 §5.4，`scripts/governance/check-memory-sync.sh` 校验）。
- 禁止把 AI 私有目录（`.workbuddy/`、`.zcode/`、`.trae/`）的原文抄进本目录（规范 §5.4 禁止项 ①）。

## 索引

| 文档 | 说明 |
| --- | --- |
| [ai-knowledge/README.md](./ai-knowledge/README.md) | 知识库入口（收录范围 + 各文件职责） |
| [ai-knowledge/07-change-memory.md](./ai-knowledge/07-change-memory.md) | 变更记忆台账（每条改动一行，含证据与影响面） |
