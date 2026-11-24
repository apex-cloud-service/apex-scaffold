# BE-063 Wave B1 交接：本地缓存 TTL 生命周期

## 范围

- 基线：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；工作区已有改动完整保留。
- 仅修改 `LocalCacheService` 与同包新增测试；不改 Redis stale/回填策略（BE-051）、外部契约、生产配置、POM、迁移或部署。
- 执行者/R0：当前 Codex 会话；独立 R5：Pauli，不参与实现；全局集成租约 `WAITING`。

## 改动清单

- 移除与 Caffeine 条目生命周期分离的 `ttlTracker`，避免条目被容量淘汰或清空后辅助 Map 长期保留 key。
- 有自定义 TTL 的值和到期时刻封装为同一缓存条目；读取时判定过期。无自定义 TTL 的值仍按原格式存储，批写不继承旧 key 的 TTL。
- 新增回归用例覆盖过期读、批写覆盖过期条目、单写 TTL 更新、删除/清空及容量淘汰。

## 验证证据

- 固定 JDK `21.0.13.0+9-tem` / mvnd `2.0.0-rc-3` 执行：
  `mvnd -B -pl :apex-infrastructure-cache -am test spotless:check`
- 结果：BUILD SUCCESS；`apex-common` 593、`apex-domain` 9、`apex-infrastructure-cache` 138 tests，失败 0、错误 0、跳过 0；各模块覆盖率门禁与 Spotless 通过。
- 初次 Spotless 检查指出新增测试三处断行，按格式修正后复跑全绿。
- `git diff --check`、`bash scripts/governance/verify-docs.sh --strict`、`bash scripts/governance/check-doc-refs.sh --strict`、`bash scripts/governance/audit-backend-rules.sh --strict` 通过。
- `check-memory-sync.sh --strict` 未执行：工作区没有暂存改动；不为校验而暂存包含用户内容的差异。
- 未运行全仓 `clean verify`、压测、服务集成、真实集群发布，也未生成全仓测试报告；这些仍属后续波次。

## 影响面

仅本地 L1 缓存的自定义 TTL 元数据生命周期及批写时 TTL 行为。无 Redis 行为、HTTP/消息契约、数据迁移或服务部署变化。容量与压力下的性能收益尚未测量。

## 独立复核与状态

R5 Pauli 对两个源码文件的最终 diff 给出 `APPROVE_WITH_NITS`，无必改项；建议补正 TTL 边界测试并统一测试缓存模式。本波可按治理规则放行，以上建议作为后续缓存验证项。未暂存、提交、推送、部署或关闭任务。

## 回滚方式

仅撤回本波 `LocalCacheService.java` 和新增 `LocalCacheServiceTest.java` 的对应差异，并同步将本记录/索引/任务结果追加更正；不得还原整份共享文件或清理其他工作区改动。
