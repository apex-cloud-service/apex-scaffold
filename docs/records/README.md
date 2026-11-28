# 变更与改造记录（docs/records）

每次任务关闭、重构、缺陷修复都要留痕，供追溯与复盘。

命名：`YYYY-MM-DD-主题.md`；必含「范围 / 改动清单 / 验证证据 / 影响面 / 回滚方式」。

已登记记录：

- [2026-11-16 文档治理与目录重构](./2026-11-16-docs-governance.md)
- [2026-11-19 Podman-only 联调入口记录](./2026-11-19-podman-only-integration.md)
- [2026-11-22 设计模式 / SPI / 注释复审与整改记录](./2026-11-22-code-quality-pattern-spi.md)
- [2026-11-22 全仓对齐与图表补齐战役记录](./2026-11-22-alignment-campaign.md)
- [2026-11-22 全模块增量评审与兼容性优化](./2026-11-22-code-review-pattern-spi-lambda.md)
- [2026-11-22 全模块 Handler SpringDoc/Swagger 注解补齐](./2026-11-22-handler-annotation-completion.md)
- [2026-11-23 BE-043、BE-049 责任与租约接管](./2026-11-23-task-ownership-handoff.md)
- [2026-11-23 BE-063 Wave A 基线交接](./2026-11-23-be063-wave-a-handoff.md)
- [2026-11-23 BE-063 Wave B1 本地缓存 TTL 生命周期](./2026-11-23-be063-wave-b1-cache-ttl.md)
- [2026-11-23 BE-063 Wave B2 网关生产档熔断接线](./2026-11-23-be063-wave-b2-gateway-circuit-breaker.md)
- [2026-11-23 BE-063 Wave C1 网关断路器唯一性测试](./2026-11-23-be063-wave-c1-gateway-circuit-breaker-test.md)
- [2026-11-23 BE-063 Wave D1 Kubernetes 灰度隔离](./2026-11-23-be063-wave-d1-kubernetes-release-isolation.md)
- [2026-11-23 BE-063 Wave E 全仓门禁与报告](./2026-11-23-be063-wave-e-full-verify.md)
- [2026-11-23 BE-064 构建门禁修复](./2026-11-23-be064-build-gate-repair.md)
- [2026-11-23 BE-065 PMD 全仓规则违规修复](./2026-11-23-be065-pmd-rule-repair.md)
- BE-063 Wave B 测试汇总与命令结果登记于 [测试索引](../06-testing/README.md)；逐波交接保留在各自记录中。

> 规则：记录只增不改；后续修正以追加段落写入，不覆盖历史结论。

| 日期 | 文档 | 说明 |
| --- | --- | --- |
| 2026-11-16 | [2026-11-16-directory-normalization.md](./2026-11-16-directory-normalization.md) | 目录规范化：docs 总览表 + 7 个 README + check-doc-layout.sh（BE-034） |
| 2026-11-16 | [2026-11-16-tech-debt-reverify.md](./2026-11-16-tech-debt-reverify.md) | BE-001/BE-017 复核（均已闭合）+ mvnd 守护进程故障与 mvn 回退 |
| 2026-11-17 | [../specs/2026-11-17-integration-contract-repair.md](../specs/2026-11-17-integration-contract-repair.md) | BE-036 联调修复：审批 JDBC 租户传播与作用域、菜单空集合全量、授权快照未知字段容忍（受影响模块测试全绿） |
| 2026-11-18 | [2026-11-18-javadoc-review-campaign.md](./2026-11-18-javadoc-review-campaign.md) | BE-041 全仓 Javadoc 评审：doclint 78→0 告警、53 文件纯注释修复、三批测试全绿（BE-041） |
| 2026-11-18 | [2026-11-18-multi-agent-governance.md](./2026-11-18-multi-agent-governance.md) | BE-042 多智能体并行协作治理：R0 租约、并行准入、worktree 隔离、标准交接、独立复核与逐个集成；R5 增量复审通过并关闭 |
| 2026-11-19 | [2026-11-19-podman-only-integration.md](./2026-11-19-podman-only-integration.md) | BE-043 Podman-only 联调入口、前端容器健康检查及当前本机 Podman 服务阻塞 |
| 2026-11-22 | [2026-11-22-code-quality-pattern-spi.md](./2026-11-22-code-quality-pattern-spi.md) | BE-044 全仓设计模式 / SPI / 注释复审：存储与连接器 SPI 发现加固、MQ 默认处理器让位、向量响应校验、9 处失真注释修正；Wave G、独立 R5 `APPROVE` 与 45/45 全量验证完成；代码提交 `43e33baf`、治理收尾 `8d8dc31c`，任务已关闭 |
| 2026-11-22 | [2026-11-22-alignment-campaign.md](./2026-11-22-alignment-campaign.md) | BE-045~BE-048 全仓对齐与图表补齐：指南/测试文档对齐、既有图核对（无 _v2）、新增 130–145 共 16 张双源图、Javadoc 归零与规则文档/扫描脚本修复；门禁与全量 verify 见记录 §5 |
| 2026-11-22 | [2026-11-22-code-review-pattern-spi-lambda.md](./2026-11-22-code-review-pattern-spi-lambda.md) | BE-049 全模块增量评审：SSO/搜索分派收敛、内部端点错误映射去重、撤销缓存保留期修正、数据权限 OR 括起、Outbox 抢占校验、SPI 真实描述符测试、测试报告生成链修复 |
| 2026-11-23 | [2026-11-22-handler-annotation-completion.md](./2026-11-22-handler-annotation-completion.md) | BE-060 全模块 Handler SpringDoc/Swagger 注解补齐：10 模块 Router/Handler/DTO 四档覆盖率 100%、扫描脚本从单模块扩到全模块并新增写操作 requestBody 检查、4 个既有契约测试全绿、HTTP 契约零变化 |
| 2026-11-28 | [2026-11-28-be066-alignment-campaign.md](./2026-11-28-be066-alignment-campaign.md) | BE-066 全仓文档/脚本/配置/图表对齐与增强：四切片评审修复（模块/API/配置/脚本索引）、时序图 90/91 `_v2` 成对修订、9 个历史件补横幅、脚本索引补 governance、新登记工单 BE-067~070 |
| 2026-11-28 | [2026-11-28-be067-070-remediation.md](./2026-11-28-be067-070-remediation.md) | BE-067~070 跟进修复（先规格后动手）：interests 死白名单/Sentinel 死规则清理（43→42 连带同步）、aggregation todo 前缀死键迁移 + 哨兵值绑定测试、system prod 管理端口 8081→8083、根级 monitoring/ 重复目录删除；新登记 BE-071 |
| 2026-11-28 | [2026-11-28-be071-sentinel-baseline-audit.md](./2026-11-28-be071-sentinel-baseline-audit.md) | BE-071 Sentinel 静态基线全量审计：逐条路由字面量证据（流控 8 存活/34 死、降级 2/16），SentinelConfig 手术 + 模板 JSON 同步 + 计数终值 8/2 回写五处活文档 + 13/44 号图 `_v2` 成对重绘；sentinel-rules.json 种子文件列为后续观察项 |
