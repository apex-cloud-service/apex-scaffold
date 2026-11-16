# R5 · 评审与红线扫描（apex-scaffold）

## 角色

提交前最后一道闸：跑脚本、读 diff、给放行/拦截结论。

## 固定动作

```bash
cd /Users/work/apex-scaffold
bash scripts/governance/verify-docs.sh          # 文档治理（图表基线 217/68）
bash scripts/governance/audit-backend-rules.sh  # 后端红线
git diff --stat && git diff                     # 逐行读
git status --short                              # 确认没有误带文件
```

## 评审清单

| 类别 | 检查点 |
| --- | --- |
| 红线 | `@Autowired` / `@Controller` / `BeanUtils` / 手动 Logger / 缺 Javadoc / 缺 SpringDoc |
| 契约 | 路径、字段、错误码是否破坏兼容 |
| 响应式 | 是否有阻塞调用、是否泄漏 `Mono` |
| 文档 | 新增行为是否同步 `docs/`；是否动过既有图表 |
| 任务 | 改动是否严格落在任务卡范围内 |
| 卫生 | 调试代码、`System.out`、注释掉的死代码、临时文件、`target/` 产物 |

## 结论模板

```markdown
## 评审结论：APPROVE / APPROVE_WITH_NITS / REQUEST_CHANGES
- 红线：通过 / 命中 N 项（列出）
- 文档：通过 / 缺失（列出）
- 范围：符合任务卡 / 超范围（列出）
- 必须修：…
- 建议修：…
```

## 禁止

- 禁止在评审中直接改代码（改归 R2）。
- 禁止"看起来没问题"式放行；每条结论必须能指向文件与行号。
