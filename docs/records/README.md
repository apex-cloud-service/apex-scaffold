# 变更与改造记录（docs/records）

每次任务关闭、重构、缺陷修复都要留痕，供追溯与复盘。

命名：`YYYY-MM-DD-主题.md`；必含「范围 / 改动清单 / 验证证据 / 影响面 / 回滚方式」。

已登记记录：

- [2026-11-16 文档治理与目录重构](./2026-11-16-docs-governance.md)
- [2026-11-19 Podman-only 联调入口记录](./2026-11-19-podman-only-integration.md)
- [2026-11-22 设计模式 / SPI / 注释复审与整改记录](./2026-11-22-code-quality-pattern-spi.md)
- [2026-11-22 全仓对齐与图表补齐战役记录](./2026-11-22-alignment-campaign.md)
- [2026-11-22 全模块增量评审与兼容性优化](./2026-11-22-code-review-pattern-spi-lambda.md)

> 规则：记录只增不改；后续修正以追加段落写入，不覆盖历史结论。

| 日期 | 文档 | 说明 |
| --- | --- | --- |
| 2026-11-16 | [2026-11-16-directory-normalization.md](./2026-11-16-directory-normalization.md) | 目录规范化：docs 总览表 + 7 个 README + check-doc-layout.sh（BE-034） |
| 2026-11-16 | [2026-11-16-tech-debt-reverify.md](./2026-11-16-tech-debt-reverify.md) | BE-001/BE-017 复核（均已闭合）+ mvnd 守护进程故障与 mvn 回退 |
| 2026-11-17 | [../specs/2026-11-17-integration-contract-repair.md](../specs/2026-11-17-integration-contract-repair.md) | BE-036 联调修复：审批 JDBC 租户传播与作用域、菜单空集合全量、授权快照未知字段容忍（受影响模块测试全绿） |
| 2026-11-18 | [2026-11-18-javadoc-review-campaign.md](./2026-11-18-javadoc-review-campaign.md) | BE-041 全仓 Javadoc 评审：doclint 78→0 告警、53 文件纯注释修复、三批测试全绿（BE-041） |
| 2026-11-18 | [2026-11-18-multi-agent-governance.md](./2026-11-18-multi-agent-governance.md) | BE-042 多智能体并行协作治理：R0 租约、并行准入、worktree 隔离、标准交接、独立复核与逐个集成；R5 增量复审通过并关闭 |
| 2026-11-19 | [2026-11-19-podman-only-integration.md](./2026-11-19-podman-only-integration.md) | BE-043 Podman-only 联调入口、前端容器健康检查及当前本机 Podman 服务阻塞 |
| 2026-11-22 | [2026-11-22-code-quality-pattern-spi.md](./2026-11-22-code-quality-pattern-spi.md) | BE-044 全仓设计模式 / SPI / 注释复审：存储与连接器 SPI 发现加固、MQ 默认处理器让位、向量响应校验、9 处失真注释修正；受影响模块 `-am test` 全绿（Wave G 待业主确认） |
| 2026-11-22 | [2026-11-22-alignment-campaign.md](./2026-11-22-alignment-campaign.md) | BE-045~BE-048 全仓对齐与图表补齐：指南/测试文档对齐、既有图核对（无 _v2）、新增 130–145 共 16 张双源图、Javadoc 归零与规则文档/扫描脚本修复；门禁与全量 verify 见记录 §5 |
| 2026-11-22 | [2026-11-22-code-review-pattern-spi-lambda.md](./2026-11-22-code-review-pattern-spi-lambda.md) | BE-049 全模块增量评审：SSO/搜索分派收敛、内部端点错误映射去重、撤销缓存保留期修正、数据权限 OR 括起、Outbox 抢占校验、SPI 真实描述符测试、测试报告生成链修复 |
