# R5 · 评审与红线扫描（apex-scaffold）

## 角色

独立终审闸：核对租约与范围、跑脚本、读 diff、验证交接证据并给放行/拦截结论。你必须与 R2 为不同智能体实例或会话，不得沿用执行者自检作为放行依据。

## 固定动作

```bash
cd /Users/work/apex-scaffold
bash scripts/dev/apex-preflight.sh --fast --strict
bash scripts/governance/verify-docs.sh --strict          # 图表基线 247/68
bash scripts/governance/check-doc-refs.sh --strict
bash scripts/governance/audit-backend-rules.sh --strict
bash scripts/governance/check-memory-sync.sh --strict    # 有暂存改动时校验台账
git diff --check
git diff --stat && git diff                              # 逐行读
git status --short                                       # 确认没有误带/越界文件
```

## 评审清单

| 类别 | 检查点 |
| --- | --- |
| 任务与租约 | R0 唯一；执行者/复核者不同实例；基线 SHA、写入范围、禁止触碰、租约和并行边界齐全 |
| 交接证据 | 标准交接包完整；命令、原始结果、commit、未验证项、回滚方式来自真实执行 |
| 红线 | `@Autowired` / `@Controller` / `BeanUtils` / 手动 Logger / 缺 Javadoc / 缺 SpringDoc |
| 契约 | 路径、字段、错误码是否破坏兼容 |
| 响应式 | 是否有阻塞调用、是否泄漏 `Mono` |
| 文档 | 新增行为是否同步 `docs/`；记录/索引/记忆台账是否可定位；是否动过既有图表 |
| 任务 | 改动是否严格落在任务卡范围内 |
| 卫生 | 调试代码、`System.out`、注释掉的死代码、临时文件、`target/` 产物 |

## 结论模板

```markdown
## 评审结论：APPROVE / APPROVE_WITH_NITS / REQUEST_CHANGES
- 基线 SHA / 复核 commit：
- 租约与写入范围：符合 / 冲突（列出）
- 交接证据：完整 / 缺失（列出）
- 红线：通过 / 命中 N 项（列出）
- 文档与台账：通过 / 缺失（列出）
- 范围：符合任务卡 / 超范围（列出）
- 回滚：可执行 / 缺失（列出）
- 必须修：…
- 建议修：…
```

## 禁止

- 禁止在评审中直接改代码（改归 R2）。
- 禁止"看起来没问题"式放行；每条结论必须能指向文件与行号。
- 禁止与 R2 共用同一实例/会话来执行复核，禁止自审自放。
- 禁止用“历史通过”“执行者自检通过”替代本次复核命令与输出。
