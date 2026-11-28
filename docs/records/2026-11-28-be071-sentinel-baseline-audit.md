# BE-071 Sentinel 静态基线全量审计执行记录（2026-11-28）

> 任务卡：`docs/tasks/project-tasks.md` BE-071（R0/执行：ZCode，用户 2026-11-28 授权，基线 `cd3e64b7`）。
> 纪律：**逐条证据先行**——每条规则路径先对照 system 模块（SentinelConfig 宿主）实际注册的路由字面量全集判定存活性，再删改；证据方法与初版启发式误报教训一并留痕。

## 1. 证据方法与结论

- 证据集：从 `apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/{router,config}` 提取全部路由注册字面量（`path = "..."` 与 `route().GET/POST/...("..."` 两形态，去重后 223 个）。
- 判定：规则路径精确命中注册集为 ALIVE；带路径变量的规则回退一级父路径命中为 ALIVE*；否则 DEAD。
- 初版启发式（按前缀在含 SentinelConfig 的目录内 grep）产生大量循环论证误报（35 条「疑似死亡」中含 8 条实际存活），已废弃；**最终以字面量精确匹配为准**。
- 结论：流控 42 条中 **8 条存活**（auth 5：login/refresh/sms/cl-sms/logout + user 3：info/list/{userId}）、**34 条死亡**；降级 18 条（代码构造口径）中 **2 条存活**（auth login/refresh）、**16 条死亡**。死亡规则全部属于退役业务域（文件/媒体/排名/同频/其他/V1/V2 探索投票/外部依赖/慢调用组）。

## 2. 逐条证据清单

完整判定输出（FLOW 42 行 + DEGRADE 18 行，含 ALIVE/DEAD 逐条标注）见审计脚本运行留痕；关键样例：
- ALIVE：`/api/v1/auth/login`（PublicApiRouter.java:478 等 2 处注册）、`/api/v1/user/{userId}`、`/api/v1/user/info`、`/api/v1/user/list`、`/api/v1/auth/refresh`（降级）等 8+2 条；
- DEAD 代表：`/api/v1/media/photos/{photoId}/vote`、`/api/v1/ranking/list`、`/api/v1/same-frequency/status`、`/api/v2/explore/choice-2/current`、`/api/v1/oss/upload`、`/api/v1/wechat/pay` 等 34+16 条——全仓（含网关路由谓词）零注册。

## 3. 改动清单（证据后执行）

1. `SentinelConfig`：
   - 删除 7 个死流控方法（file/media/ranking/sameFrequency/other/exploreV2/exploreV1）与 5 个死降级方法（file/media/external/exploreV1/slowCall）+ `createSlowCallDegradeRule` 辅助；
   - `initFlowRules` 42→8（仅 auth+user）、`initDegradeRules` 30→2（仅 authDegrade）；构造器移除两个慢调用阈值字段与 `@Value`（`apex.sentinel.degrade.slow-call.*` 配置键随之失效：.env.example 未收录；`application-enterprise.yml` 中的同名块经 R5 MF-03 指认后已同步删除）；
   - 类头 Javadoc 分组表重写（auth 5 + user 3），补 BE-071 审计说明。
2. `config/sentinel/apex-sentinel-flow-rules.json`：42→8；`apex-sentinel-degrade-rules.json`：30→2（保持原紧凑单行格式；初版 json.dump 重排格式已即时还原）。
3. `SentinelConfigTest`：模板一致性断言 42→8、30→2；删除 `shouldContainFileUploadDegradeRule` 与 `shouldApplyConfiguredAiSlowCallThreshold`（规则已不存在）；构造器调用 6 参→4 参。`normalizePathVariables` 系列用例保留（纯函数测试，fixture 不依赖路由存在性）。
4. 活文档计数回写（终值 8/2）：`docs/05-operations/configuration.md`、`config/sentinel/README.md`、`docs/architecture/service-governance-solution.md`、`docs/07-commercial/sales/SECURITY_WHITEPAPER.md`、`docs/architecture/advanced-features/sentinel-rate-limit-circuit-breaker.md`（4 处）。
5. 图表（§0.3 `_v2` 成对）：
   - `13_docker_compose_local_dev_v2.mmd/.svg`：v1 为单体时代拓扑（8080/8081、apex_data、`./monitoring/` 挂载已随 BE-070 失效），v2 按现行 `podman-compose.yml`/`podman-compose.infra.yml` 实测重绘（主栈默认 Profile 服务/端口、可选 Profile 6 组、infra 栈、security-key-check、apex-observability 网络）；登记于 `docs/diagrams/README.md` 13 行；
   - `44_security_protection_layers_v2.mmd/.svg`：Sentinel 计数更正 8+2，其余条目逐项核实（GlobalGuardWebFilter/InputValidationFilter/ScanTrafficGuardWebFilter 实测存在；@AntiReplay/@IdempotentApi/@DataScope/@TenantAware/@RequirePermission 注解实测存在；百度审核适配器实测存在）；登记于 `docs/02-architecture/architecture-index.md` 44 行。

## 4. 范围边界

- `config/sentinel/sentinel-rules.json`（动态规则种子，12 流控/4 降级/1 系统保护，短路径命名 `/auth/login` 等，经 `SentinelDynamicRulesLoader`/`SentinelRuleFileWatcher` 消费）**不在本轮范围**：其资源命名与静态基线不同源，属独立机制，留待后续单独评估（观察项，见任务卡）。
- 网关侧 `apex.gateway.rate-limit.*` 与 IP 限流为另一套机制，未在本轮范围。
- 42→8 为终值：仅保留 system 模块当前实际注册路径；后续新增业务路由时应随路由同步新增规则（模板一致性测试强制约束）。

## 5. 验证结果

- `mvnd -pl :apex-system -am test -Dtest='SentinelConfigTest,SentinelDynamicRulesLoaderTest'`：**20/0/0 BUILD SUCCESS**（模板↔FlowRuleManager 一致性 8/8、降级 2/2）。
- `mvnd -pl :apex-system -am test` 全量复跑：见 §5.1。

### 5.1 全量复跑终态（2026-11-28）

- `mvnd -pl :apex-system -am test`：**apex-system 1810 tests, 0 failures, 0 errors, 0 skipped；BUILD SUCCESS**（相对 BE-067 轮 1812 净减 2 = 删除 file-upload/AI 慢调用两个死规则用例）。
- `bash scripts/ci/check-doc-links.sh`：4096 链接 0 断裂；`verify-docs.sh --strict`：0 项问题（图表基线 svg 265→267 / mmd 86→88，`_v2` 成对新增）。
- `mvnd -pl :apex-system spotless:check`：**EXIT=0**（R5 MF-01 指认的 13 处尾随空白已由 `spotless:apply` 修复）；`git diff --check`：clean。
- R5 复核结论（两轮）：首轮 **REJECT**（4 MF + 3 N，均为机械收尾项；审计实质全部证实——R5 以独立前缀组合还原法自建 167 条注册集，34+16 全量零命中而非抽样）→ MF-01（空白/spotless）、MF-02（能力表 30→2 漏改）、MF-03（enterprise yml 死块 + 记录口径）、MF-04（孤儿 Javadoc）与 N-02（表格措辞收窄）全部修复；增量复核 **APPROVE**（R5 实跑核销：spotless:check/diff-check EXIT=0、SentinelConfigTest 17/0/0、yml 删除干净无残留、越界为零、N-01 两份历史材料横幅预存豁免成立）。N-03 归一化纯函数 fixture 留作后续优化。

## 6. 回滚方式

全部改动以独立提交承载，逐笔 `git revert`；JSON 模板与 SentinelConfig 同一提交保证一致性测试恒绿。
