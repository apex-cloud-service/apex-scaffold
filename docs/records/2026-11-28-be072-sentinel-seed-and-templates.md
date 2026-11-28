# BE-072 Sentinel 动态种子对齐与模板遮蔽修正执行记录（2026-11-28）

> 任务卡：`docs/tasks/project-tasks.md` BE-072（R0/执行：ZCode，用户 2026-11-28 授权修复 BE-071 遗留观察项，基线 `3e550966`）。
> 来源：BE-071 收尾时登记的两个观察项；执行中发现并连带修正一处**新的运行时真缺陷**（见 §2）。

## 1. 观察项①：动态种子文件对齐（原计划）

`config/sentinel/sentinel-rules.json`（`SentinelDynamicRulesLoader` 默认种子，`apex.sentinel.dynamic-rules.enabled` 默认开启）原为退役短路径命名（`/auth/login` 等 12 流控 + 4 降级），资源永不匹配真实请求——整份种子为惰性规则。已重写为与静态基线一致的真实路径规则（QPS/阈值逐值对齐），`systemRules` 种子条目保留（合法的系统保护种子，非路径资源）。

## 2. 执行中发现的真缺陷：模板遮蔽（连带修正）

**发现链**：核对种子时注意到 Loader 与 `SentinelConfig` 均为 `FlowRuleManager.loadRules` 整体替换语义的 `@PostConstruct`——种子不对齐时后执行者会覆盖前者。

> **可达性边界（R5 N-6 限定）**：`sentinel-rules.json` 仅存在于仓库根 `config/sentinel/`，**不在 `apex-system` / `apex-system-service` 的 jar 内**，`src/main/resources` 亦无该目录，全仓无 `apex.sentinel.rules-file` 的生产注入点（仅测试脚本用 `/etc/apex/sentinel-rules.json`）。故**默认打包态下 Loader 读取返回 null → 记 WARN 后跳过，不执行 loadRules**，静态规则不会被清掉；「种子覆盖静态规则」只在部署方显式注入真实可达 rules-file 时成立。种子重写属**上游对齐与部署态隐患消除**，非默认态线上缺陷——定级据此下调。

进一步核查归一化机制发现更深一层：

1. `SentinelResourceUtil.PATH_VARIABLE_TEMPLATES` 仍登记 9 个退役域模板（media ×5、explore ×4）——BE-071 清理规则时遗漏了配套的归一化注册表；
2. 模板按占位符**通配**语义匹配（`matchesTemplate` 占位符命中任意非空段），故 `/api/v1/user/info`、`/api/v1/user/list` 的请求经 `cleanSentinelResource` 一律归一化为 `/api/v1/user/{userId}` 资源——**这两条静态限流规则自模板存在起即不可命中**（被遮蔽），运行时 user 域全部请求实际由 `{userId}` 200 QPS 规则覆盖。

**修正**（行为中性——被遮蔽规则本就从未生效）：
- `PATH_VARIABLE_TEMPLATES` 裁剪为唯一存活模板 `/api/v1/user/{userId}`；
- `SentinelConfig` 移除被遮蔽的 `user/info`(300)、`user/list`(50) 两条，`initFlowRules` 8→6（auth 5 + user 1），Javadoc 说明遮蔽语义；
- 两份推送模板与动态种子同步为 6+2；
- `SentinelConfigTest`：断言 8→6，fixture 全部换存活路径并按真实语义断言（`/api/v1/user/info` → `{userId}` 遮蔽行为固化为用例、无模板命中的多段路径原样保留），退役域路径 fixture 清零（BE-071 N-03 落实）。

## 3. 验证结果

- `mvnd -pl :apex-system test -Dtest='SentinelConfigTest,SentinelDynamicRulesLoaderTest'`：**20/0/0 BUILD SUCCESS**（模板↔FlowRuleManager 一致性 6/6 + 2/2）。
- `mvnd -pl :apex-system -am test` 全量复跑：见 §3.1。

### 3.1 全量复跑终态（2026-11-28）

- `mvnd -pl :apex-system -am test`：**apex-system 1811 tests, 0 failures, 0 errors, 0 skipped；BUILD SUCCESS**（首轮全量曾失败于 `SentinelResourceUtilTest` 仍断言退役模板，补齐后 1810 全绿；N-5 新增/升级护栏测试后终值 1811）。
- 护栏变异验证：种子 `user/{userId}` count 200→999 → 护栏用例 FAIL（能捕获阈值漂移），随后还原种子（`git diff` 形状复原）。
- 门禁：`check-doc-links.sh` 4097 链接 0 断裂；`verify-docs.sh --strict` 0 项；`git diff --check` clean；`mvnd -pl :apex-system spotless:check` **EXIT=0**（R5 连续两轮指认该门禁漏检，自本轮起固化进验证清单）。
- **R5 复核（两轮）**：首轮 REJECT（MF：3 文件折行致 spotless FAIL——流程性复发；N-1 索引行 8 残留、N-2 Javadoc 退役示例、N-3 种子一致性无护栏、N-4 措辞）→ 全部清零（spotless:apply、索引/Javadoc/措辞修正、新增种子↔静态基线一致性护栏测试 `defaultSeedFileShouldMatchStaticBaseline`——首轮实现误用 classpath 路径静默落空，已改与 readRuleTemplate 同源的文件系统解析并留痕）→ 增量复核 APPROVE 待回报；终态复跑 SentinelConfigTest 17 + SentinelDynamicRulesLoaderTest 4 + SentinelResourceUtilTest 5 = **26/0/0**、spotless EXIT=0。

## 4. 范围边界

- `sentinel-rules.json` 的 `systemRules` 种子（qps 5000 / cpu 0.85）与静态 `initSystemRules`（2000/0.8）数值不同，属"动态种子可整体替换"设计语义，保留并在本记录声明；
- 网关侧限流（`apex.gateway.rate-limit`）与 IP 限流未在本轮范围；
- Loader 与 SentinelConfig 的 `@PostConstruct` 执行顺序仍无显式约束——种子对齐后两者产物一致，顺序不再有行为差异；如后续引入差异化的动态规则，建议显式 `@Order` 或合并加载入口（观察项，不在本轮改动）。

## 5. 回滚方式

独立提交承载，`git revert` 逐笔回滚；模板 JSON 与 SentinelConfig 同提交保证一致性测试恒绿。
