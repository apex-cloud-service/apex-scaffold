# Maven 子模块物理归位（第二阶段）调整方案

| 项 | 内容 |
| --- | --- |
| 状态 | **已确认执行**（2026-11-14 用户确认：§8 八项裁决均按默认建议执行；按 Q8 节奏 B0–B1 完成后暂停展示 diff，等待放行 B2–B7） |
| 日期 | 2026-11-14 |
| 前置 | 第一阶段 Maven 七组分组重构已完成（commit `63e858b1`、文档同步 `bba338e5`），见 `docs/08-project-management/plans/maven-parent-grouping-optimization-plan.md` |
| 范围 | 将 37 个叶子模块目录从仓库根级物理移动到各自分组父目录之下；分组父目录仍留在仓库根级。模块 Maven 坐标、Java 包名、生产依赖、业务接口、服务与端口全部不变 |
| 调研方式 | 四路只读调研（POM / 脚本·CI·容器 / Java·测试·资源 / 文档·图表）+ 关键承重事实人工复核 |

---

## 1. 规则评审结论

### 1.1 现状（第一阶段设计）

当前物理布局为**全部叶子目录平铺在仓库根级**，7 个分组父 POM 目录也在根级，用 `../apex-xxx` 聚合同级目录：

```
apex-scaffold/
├── pom.xml                    ← 根 POM，只聚合 7 个分组父
├── apex-shared-parent/        ← 仅含 pom.xml
│   └── pom.xml                ← <module>../apex-common</module> 等 9 条
├── apex-common/               ← 叶子平铺根级（含源码）
├── apex-domain/
├── …（37 个叶子全部平铺）
├── apex-services-bom/
│   └── pom.xml                ← <module>../apex-gateway</module> 等 11 条
├── apex-gateway/
└── …
```

第一阶段 AGENTS.md 明文约束"所有现有叶子目录仍在仓库根级，分组用 `../apex-xxx` 聚合同级目录，不移动源码、资源或 target"。本方案是用户决策对该约束的**第二阶段推翻**，执行后须同步重写 AGENTS.md / README 中的对应表述（见 §3.6）。

### 1.2 评审结论：**技术上可行，可以执行，但有 4 项必须遵守的前提**

**可行性依据：**

1. Maven 完全支持嵌套聚合目录；叶子移入分组父后，34 个"聚合=继承"叶子的 `relativePath` 反而可以简化为默认值，结构更标准。
2. 关键运行机制全部与物理目录无关（调研已逐项验证）：模块坐标、Java 包名 `com.apex.scaffold.*`、Spring 组件扫描、`@MapperScan`、Flyway `classpath:db/migration/...`、mapper `classpath*:mapper/**/*.xml`、logback 运行时相对路径、Testcontainers、MapStruct、ArchUnit classpath 扫描（`@AnalyzeClasses(packages=...)`）。
3. 生产代码（src/main/java）除 1 处（`apex-tools-deployment` 的 `TokenV42Recover`）外**零**仓库布局假设。
4. `git mv` 移动时源码内容不变，Git rename 检测可保留历史（`git log --follow` 有效）。

**收益：**

- 物理布局 = 逻辑聚合树，新人导航直观，IDE 项目树天然按分组归拢。
- 消除 `../apex-xxx` 跨目录聚合这种非常规写法，`relativePath` 回归 Maven 默认语义。
- 顺带把全仓 `-pl` 统一为 `:artifactId` 形式，对未来的目录调整免疫。

**代价与风险：**

- 一次性调整面大：**必改约 180+ 个文件、约 1100+ 处引用**（详见 §2、§4），且存在 2 个"静默失效"测试（改不完整时测试仍绿但守护面缩水，见 §2.2）。
- AGENTS.md / 文档 / 图表大面积措辞与路径改写。
- 历史报告类文档中的路径叙述会过期（可用豁免策略控制，见 §3.6 / §7）。
- 无任何功能收益，属于纯结构性治理投入。

**4 项必须遵守的前提（缺一不可）：**

1. **`.gitignore` 公钥规则先行**（安全项）：`*.key` 全局忽略 + `!apex-common/.../public.key` 白名单是唯一放行规则。若先移动后改规则，任何非 `git mv` 的移动方式（如 shell `mv` + `git add`）都会导致公钥静默漏出版本控制。必须在移动前先把白名单改为新路径（新旧行并存，移动稳定后再删旧行）。
2. **`apex-tools-build` 三处布局契约测试必须与结构调整 lockstep**：`MavenModuleInventory.java:75` 硬断言"模块目录 = 仓库根直下"、`BuildManifestSyncTest.java:256` 硬编码 `apex-example/pom.xml`、`BuildPathContractTest.java:37/56-58` 锁定根 POM argLine 与 fork 工作目录契约。不改测试，`mvnd verify` 必红；改了实现不改测试也一样红。这是好消息——门禁会强制同步，不会漏改。
3. **根 POM 日志路径必须同步改**：`pom.xml:1156/1178` 的 `-Dapex.log.dir=${project.basedir}/../logs` 依赖"叶子在根级一级"假设，移动后会写到分组父目录下（`apex-xxx-parent/logs`）。必须改为 `${project.basedir}/../../logs`（所有测试执行模块统一在两级深度，纯路径算术、零语义风险），并与 `BuildPathContractTest` 同批改。
4. **执行期间统一用根级全量命令跑门禁**：B1 之后所有 `-pl :apex-xxx`（裸路径形式）立即失效，执行过程中的验证一律用 `mvnd -T4 -B clean verify` 等根级命令，不得依赖 AGENTS.md 里的单模块命令（它们本身在 B6 才被改写）。

### 1.3 目标布局

```
apex-scaffold/
├── pom.xml                        ← 根 POM <modules> 不变（仍 7 条）
├── apex-shared-parent/
│   ├── pom.xml                    ← <module>apex-common</module> 等 9 条（去 ../）
│   ├── apex-common/
│   ├── apex-domain/
│   ├── apex-api-contract/         ← 例外①：聚合在 shared，parent 继承 services-bom
│   ├── apex-infrastructure-persistence/ … apex-infrastructure/（共 9 叶）
├── apex-components-parent/
│   ├── pom.xml                    ← 6 条
│   ├── apex-system/ … apex-integration/（共 6 叶）
├── apex-starters-parent/
│   ├── pom.xml                    ← 4 条
│   └── 4 个 starter/
├── apex-services-bom/
│   ├── pom.xml                    ← 11 条
│   ├── apex-gateway/ … apex-knowledge-service/（共 11 叶）
├── apex-applications-parent/
│   ├── pom.xml                    ← 2 条
│   ├── apex-example/
│   └── apex-boot-all/             ← 例外③：聚合在 applications，parent 继承 services-bom
├── apex-tools-parent/
│   ├── pom.xml                    ← 4 条
│   └── apex-license-tools/ apex-generator/ apex-tools-deployment/ apex-tools-build/
├── apex-business-parent/
│   ├── pom.xml                    ← 1 条
│   └── apex-knowledge/
└── docs/ scripts/ config/ k8s/ monitoring/ tools/ Containerfile …（根级非模块目录不动）
```

### 1.4 关键裁决点（2026-11-14 用户确认，全部按默认裁决执行）

| # | 裁决点 | 默认裁决 | 理由 |
| --- | --- | --- | --- |
| D1 | 三个"聚合组≠继承父"例外模块（`apex-api-contract`、`apex-integration`、`apex-boot-all`）物理归属 | **随聚合父目录**（shared/components/applications），`relativePath` 改为 `../../apex-services-bom/pom.xml` | 保持"物理目录 = 聚合树"的直觉；继承关系是 Maven 语义层面的事，`BuildManifestSyncTest` 已锁定 parent 契约，不受物理位置影响。备选：随 services-bom 目录（物理与继承一致，但破坏"物理=聚合树"，且 services-bom 目录出现三个非服务叶子） |
| D2 | `-pl` 形式 | **全仓统一改 `:artifactId`**（脚本 21 处可执行调用 + 文档有效区 281 处命令） | Maven `-pl` 的非冒号值按"相对路径"解析，移动后裸路径形式全部失效；`:artifactId` 按坐标匹配、对目录层级免疫。两级路径形式也可行但留下再次移动的隐患 |
| D3 | 模块物理路径的单一事实来源 | **不加注册表字段**，用"分组约定 + 门禁强制"：更新后的 `MavenModuleInventory` 断言强制"每个模块目录 = 仓库根/分组父目录/artifactId"，脚本侧提供 `apex_module_dir()` 解析函数按约定查找 | `build-manifest.yaml` 保持坐标维度（schema 不动）；约定本身被 verify 门禁锁死。备选：manifest 增 `path` 字段（需 schema 递增 + awk 解析同步，收益边际） |
| D4 | 测试日志目录锚定 | **根 POM argLine 改 `${project.basedir}/../../logs`** | 所有跑测试的模块统一在两级深度，路径算术零风险；备选 `${maven.multiModuleProjectDirectory}/logs`（深度无关，但依赖属性插值语义，且未注入时退化为字面量） |
| D5 | 历史文档策略 | **a 类链接必须修**（`DocLinksChecker` 门禁校验链接目标存在，无豁免空间）；b 类反引号叙述路径对历史报告/专项目录**豁免不改写**（沿用 `docs/_archive` 已有先例） | 控制工作量与改写风险；过期叙述不产生构建失败 |
| D6 | 提交节奏 | **按批次分组提交（Conventional Commits）**，每批过对应门禁 | 便于回滚与评审；是否 commit 待用户确认（工作纪律：未获明确要求不 commit） |

---

## 2. 影响面全景（调研结论）

### 2.1 Maven POM（45 个文件全动）

| 对象 | 改动 | 数量 |
| --- | --- | --- |
| 根 `pom.xml` | `<modules>` **不变**；`maven-surefire-plugin` / `maven-failsafe-plugin` 的 argLine（L1156/L1178）`../logs` → `../../logs`；`BuildPathContractTest` 断言同步 | 1 文件 2 行 |
| 7 个分组父 | `<module>../apex-xxx</module>` → `<module>apex-xxx</module>`，共 37 条（9/6/4/11/2/4/1）；`<parent>` 不动 | 7 文件 37 行 |
| 34 个"聚合=继承"叶子 | 删除 `<relativePath>../<聚合父>/pom.xml</relativePath>`（移动后默认 `../pom.xml` 恰好命中同目录分组父，GAV 匹配） | 34 文件 |
| 3 个例外叶子 | `apex-api-contract`、`apex-integration`、`apex-boot-all`：`<relativePath>` 改 `../../apex-services-bom/pom.xml`（默认值会落到聚合父导致 GAV 不匹配），并更新其上方说明注释 | 3 文件 |
| 其他 POM 路径类配置 | 已全量排查：PMD `excludeFromFailureFile`（相对模块 basedir，行为前后一致）、sonar 报告路径、jacoco、spotless、enforcer、shade 均无迁移影响；全仓无 flatten 插件 | 0 |

### 2.2 Java 测试与主代码（15 个文件 / 22 项失效：20 硬失败 + 2 静默）

统一修复套路：**"向上找根标记（AGENTS.md，参照 `ContextBoundaryArchTest` 现有做法）定仓库根 + 模块路径加分组前缀"**，不依赖"`..` 一跳"或"同级巧合"。

| 失效项 | 位置 | 现状假设 | 修复 |
| --- | --- | --- | --- |
| `NoBoundedElasticArchGuardTest` | apex-system `…/arch/NoBoundedElasticArchGuardTest.java:98-107` | 向上找含 `apex-common` 的祖先当根 + 8 模块名拼根级路径 | 根标记改 AGENTS.md；模块路径加分组前缀（改为从聚合 POM 或映射表解析 `<分组父>/<module>`） |
| `NoAutowiredArchTest` | 同目录 `:97-118` | 同上，且 `listModuleDirs` 只列根级 `apex-*`（嵌套后归零，`>15` 断言必炸） | 同上，遍历分组父下一级 |
| `ContextBoundaryArchTest` | 同目录 `:36-43` | 六上下文路径相对仓库根 | 六条路径加 `apex-components-parent/` 前缀 |
| `LogbackConfigurationTest` | apex-system `interfaces/filter/…:27,40` | `../apex-auth-service/...` 一跳跨组 | 向上找根 + 分组前缀 |
| `SentinelConfigTest` | apex-system `interfaces/config/…:363-372` | `user.dir/../config/sentinel` | `..` 改根定位 |
| `SqlScriptCommentContractTest` | apex-system `interfaces/test/…:64-70` | 根定位回退逻辑命中父目录 → **静默缩水**（只扫 components-parent 内 SQL） | 根标记改 AGENTS.md，恢复全仓扫描 |
| `CapabilityUsagePropertiesTest` | apex-system `application/config/…:35-42,125` | 仓库相对 OPEN_ROUTER_FILES | 根定位 |
| `CapabilityCatalogDocContractTest` | apex-system `application/capability/…:35` | `../docs/02-architecture/...` | `../..` 或根定位 |
| `GatewayRouteProfileConfigTest` | apex-gateway `:61,152,214-216,417-429` | `../config/nacos/...` + 部分靠"同级巧合" | 根定位 + 分组前缀 |
| `ComponentAssemblyContextTest` | apex-boot-all `:46-50` | 断言 `../apex-auth/.../interfaces` **不存在** → 嵌套后恒不成立，**静默失效**（守护报废） | 路径加 `apex-components-parent/` 前缀 |
| `DomainEventOutboxDialectTest` | apex-infrastructure-messaging `:42-44` | `multiModuleProjectDirectory` 根 + `apex-auth-service/...` | 路径加 `apex-services-bom/` 前缀 |
| `MessageOutboxDialectTest` | 同模块 `:60-62` | 根 + `apex-system/...` | 加 `apex-components-parent/` 前缀 |
| `AuthDataSourceConfigContractTest` | apex-auth-service `:224,266-276` | podman-compose.yml 定根 + `apex-auth-service/...` | 路径加 `apex-services-bom/` 前缀 |
| `AuthMySqlTlsContractIntegrationTest` | 同模块 `:234,269-278` | 同上（failsafe 执行） | 同上 |
| `TokenV42Recover`（**主代码**） | apex-tools-deployment `:45-46,48-49,817-838` | `V42_SOURCE` 仓库相对 + 从 jar 上溯 3 级 | `V42_SOURCE` 加 `apex-components-parent/` 前缀；`SOURCE_PARENT_LEVELS` 3→4（内容哈希不受影响） |
| `TokenV42RecoverTest` / `ConsentV40UpgradeTest` | 同模块测试 | `apex.root` 根 + 旧相对路径 | 路径加分组前缀 |
| `MavenModuleInventory`（门禁） | apex-tools-build 测试 `:75` | 断言模块目录 = `root/<artifactId>` | 改为按 `aggregator` 字段推导两级断言（聚合父即目录名；aggregator=根时仍为根级）——这是新布局的强制契约 |
| `BuildManifestSyncTest`（门禁） | 同目录 `:256` | `apex-example/pom.xml` 根级路径 | 加 `apex-applications-parent/` 前缀 |
| `BuildPathContractTest`（门禁） | 同目录 `:37,53-60,63-79` | argLine 含 `../logs`；fork cwd = `root/apex-tools-build`；合成探针按一级布局建模 | argLine 断言改 `../../logs`；模块路径加 `apex-tools-parent/`；合成探针重排为两级布局 |

**验证不受影响（28 项，抽样列举）：** `LayeringArchTest`、`TenantIsolationArchTest`、`TimeFieldArchTest`、`GatewayArchTest`、`PersistenceContractTest`、`SystemServiceBoundaryTest`、`IntegrationServiceBoundaryTest`（全 classpath）；`FlywayMigrationVersionTest` / `PostgresqlMigrationVersionTest` / 各服务 `FileFlywayMigrationTest` 等（模块相对路径优先，随模块一起移动）；`DocLinksChecker` / `DocSearchServer` / `LocalDeployment` / `TotpMigrationBundle` / `apex-generator` / `apex-license-tools`（全部入参或 temp 目录）；全仓 yml 的 flyway/mapper locations 全部 `classpath:`；`apex-aggregation-service`、`apex-example` 零路径引用。

**附带发现（不阻塞本次，可选优化）：** 存量测试里有若干"同级巧合"型路径（`OfficeWorkflowBooleanIntegrationTest`、`DatabaseIndex*LiveTest`、`GatewayRouteProfileConfigTest` 部分），本次建议顺手统一为根定位，消除未来隐患。

### 2.3 脚本与构建编排（约 31 个文件 / 约 300 处功能引用）

失效根因三类：① `-pl` 全部为裸目录形式（可执行调用 21 处，`:artifactId` 形式全仓 0 处）；② JAR 产物路径按 `<模块>/target/<模块>-*.jar`（仓库根相对）约定散布全链路；③ 个别脚本自身目录定位假设（`apex.sh` 的 `../..`）。

| 类别 | 会失效文件 | 要点 |
| --- | --- | --- |
| 主线发布/构建/部署 | `scripts/release/release.sh`、`scripts/build/build.sh`（部分，L29 构建标识）、`scripts/deploy/assemble-topology.sh`、`scripts/deploy/service/app.sh`、`scripts/deploy/service/start-local.sh` | SERVICES 数组保持 artifactId；JAR 查找与 `-pl` 改 `:artifactId`/`apex_module_dir()`；`release.sh:168`、`build.sh:29` 的 `apex-system/target/classes/apex-build.properties` 加前缀 |
| CI 被调脚本 | `scripts/ci/check-doc-links.sh`、`scripts/ci/check-doc-consistency.sh` | JAR 路径、`find apex-system/src/...` 等 |
| utils | `local-deployment.sh`、`start-doc-search.sh`、`token-v42-recover.sh`、`check-commercialization-readiness.sh`、`extract-env-vars.sh` | fat jar 统一改为 `apex-tools-parent/apex-tools-deployment/target/local-deployment.jar`（建议抽公共变量）；多个 `safe_find apex-system/src/...` 加前缀 |
| codegen/license/bench | `scripts/codegen/generate.sh`、`scripts/license/generate-license.sh`、`scripts/bench/bench-vt.sh` | `-pl` + classpath 拼接路径 |
| test | `run-dual-database-migration.sh`、`verify-middle-platform.sh`、`verify-infrastructure-websocket.sh`、`run-mqtt-integration.sh`、`check-migration-integrity.sh`（约 50 处迁移脚本路径） | `-pl` 加 `:`；迁移资源路径加前缀 |
| dev/ops | `archive-openapi.sh`（CI release 依赖）、`check_javadoc.sh`、`check-handler-javadoc.sh`、`check-flyway-timestamp-usage.sh`、`scan-lombok-issues.sh`、`check-springdoc-annotations-v2.sh`、`batch-add-javadoc.sh`、`batch-add-lombok.sh` | 扫描路径加前缀；纯 echo 提示文本（`quick-start.sh`、`new-project.sh` 等）顺手更新 |
| 构建编排入口 | `apex-tools-build/bin/apex.sh`（整体失效） | L24-25 `PROJECT_ROOT=SCRIPT_DIR/../..` 改为向上找根标记（`.mvn/` 或 AGENTS.md）或 `../../..`；manifest 路径、全部 `-pl`、`"${svc}"/target/...` 三处、`cp apex-tools-deployment/target/...`、`--build-arg SERVICE_MODULE` 追加 `MODULE_PATH` |
| 公共库 | `scripts/lib/common.sh` | `get_project_root` / `safe_find` 本身无层级假设（已验证）；新增 `apex_module_dir()` 帮助函数；示例注释更新。注意：嵌套后"向上找 pom.xml"不再唯一（分组父也有 pom.xml），根定位应改用 `.mvn/` / AGENTS.md 标记 |

### 2.4 CI（4 个 workflow 直接/间接失效）

| 文件 | 改动 |
| --- | --- |
| `.github/workflows/ci-cd.yml` | L169 `apex-example/src/main/resources/db/init.sql` 加前缀；L211-218 八服务矩阵保持 artifactId、build args 追加 `MODULE_PATH`；L285-292 release 附件路径 8 行两级化 |
| `.github/workflows/doc-link-check.yml` | L51 `-pl :apex-tools-deployment` → `-pl :apex-tools-deployment` |
| `.github/workflows/release.yml` / `doc-consistency-check.yml` | 无直接路径，随被调脚本修复间接恢复 |
| `security-scan.yml` / `frontend-ci.yml` | 不失效（frontend 指向的 `apex-admin-web/**` 不在本次移动清单） |

### 2.5 容器与部署

| 文件 | 改动 |
| --- | --- |
| `Containerfile` | 47 行叶子 POM `COPY apex-xxx/pom.xml apex-xxx/` 两级化；SOURCE 模式 `dependency:go-offline` 前置层同步；PREBUILT 模式 `test -f` 校验与最终 `COPY --from=builder` 改用 `MODULE_PATH`（新增 build-arg，apex.sh 与 compose 注入；Containerfile 内置 case 映射作默认兜底）。`ContainerBuildContract` 门禁会自动校验 COPY 清单与新递归 POM 集合一致 |
| `Containerfile.quick` | L11 `${SERVICE_MODULE}/target/...` 改用 `MODULE_PATH` |
| `.containerignore` | 22 条放行规则两级化；gitignore 语义要求先放行父目录（`!apex-services-bom/`、`!apex-*-parent/` 等）再放行 `target/` 与 JAR |
| `podman-compose.yml` | 挂载点 `./tools/local-deployment.jar` 不变；`SERVICE_MODULE` 值不变（artifactId）；是否补传 `MODULE_PATH` 按 D2/D3 裁决（若 Containerfile 内置映射则 compose 零改动） |
| `k8s/*.yaml`（3 个） | **不失效**（仅镜像名/标签），产镜像链路修复后自动恢复 |
| `.gitignore` | L61 公钥白名单两级化（**安全项，B0 先行**）；`.zcodeignore`、`.gitattributes`、`.mvn/`、`.sdkmanrc` 不失效 |

### 2.6 模块注册表 `build-manifest.yaml`

**数据零改动**（只登记 artifactId/category/deliverable/port/mainClass，无路径字段；awk 行式解析契约不动）。仅两处注释复核：头注"42 个 Maven 模块"已过期（现登记 44 个，顺手修正）；如采纳 D3 备选才涉及 schema 递增。

### 2.7 文档与图表

| 维度 | 数量与结论 |
| --- | --- |
| 有效区（非 `docs/_archive`）含模块路径引用的 md | **191 个文件 / 约 1780 处**；其中 a 类（链接/命令/真实路径，移动后必失效）**71 文件 709 处链接 + 13 处命令 + 6 处模块 README 相对链接 ≈ 728 处**，受 `check-doc-links.sh` 门禁强制 |
| b 类（反引号/表格叙述路径） | 约 1050 处；非链接、门禁不校验 → 按 D5 豁免历史报告类，仅强制改写"有效指导文档"（见 §3.6） |
| c 类（仅模块名） | ≈ 6845 处，不用改 |
| 归档区 | `docs/_archive/` 823 处 / 116 文件，**豁免**（沿用既有"归档不改写"先例） |
| 需重写（矛盾表述句） | 9 篇：`README.md`（L60"不存在嵌套源码路径"句）、`AGENTS.md`（§模块与物理边界）、`apex-tools-build/README.md`（三处"artifactId 仍对应根级目录"）、`apex-common/README.md`、`docs/04-development/build-tools-usage.md`（74 处路径，最重）、`docs/04-development/development-standards.md`、`docs/02-architecture/architecture-overview.md`、`docs/08-project-management/delivery/delivery-scope.md`、`docs/diagrams/README.md`（维护规则三处）；另有 `MEMORY-CARD.md` |
| 需重绘图表 | **内联 mermaid 85 块中 0 张需要重绘**（全部为业务流程/时序/状态图，按模块名绘制、模块名不变）。需改的是 2 个双源图：`docs/diagrams/128_maven_module_groups.mmd/.svg` 与 `129_maven_parent_inheritance.mmd/.svg`（NOTE 明文"叶子物理目录仍在仓库根/`../apex-xxx` 聚合"，与新布局直接矛盾；必须 .mmd 与 SVG 双源同改，禁止只改一侧）；`95_module_dependency_graph` 仅副标题口径复核（0-1 处） |
| 其他资产 | 全仓 0 个 png/puml/drawio；217 个 SVG 中仅上述 2 个刻画 Maven 布局；`97-106/122/123` 等包结构 SVG 刻画 Java 包名（不变），**不受影响** |
| 模块自带 README | 5 个模块有 README；`apex-tools-build`、`apex-common` 必改；6 处 `../docs/...` 相对链接深度 +1 |
| 零散 | 根级 `aggregation-optimization-plan.md`、`architecture-review-report.md`（各含路径引用）；`scripts/lib/README.md`、`scripts/dev/check/` 2 个 md、`config/monitoring/grafana/README.md` 各 1 处 |
| 顺手甄别项 | 有效区存在大量**已过期引用**（指向已退役模块：`apex-interfaces` 410 处、`apex-application` 247 处、`apex-auth-center` 74 处、`apex-admin-web` 154 处）——不在本次混改，已另立任务：`docs/08-project-management/plans/retired-module-reference-cleanup-plan.md`（§8-Q7 已确认） |

### 2.8 明确不受影响清单（评审兜底）

模块 Maven 坐标与依赖关系、Java 包名与 Spring 扫描、端口与运行拓扑、Flyway/mapper classpath 加载、logback 运行时兜底（`${user.dir}/logs`）、容器内绝对路径（`/root/data/apex/uploads`、`/etc/apex/public.key`）、k8s 清单、`podman-compose.infra.yml`/`sonar.yml`、`.sdkmanrc`、`lombok.config`、根级非模块目录（`docs/ scripts/ config/ k8s/ monitoring/ tools/ data/ logs/ backups/`）、`docs/03-api/internal-api-contracts.md`（0 路径引用）、全部内联 mermaid。

---

## 3. 执行方案（七个批次）

> 执行前置：`source scripts/build/init-build-env.sh && mvnd --stop`（AGENTS.md 环境约束）；全程在专用分支或工作区进行；每批跑对应门禁后再进下一批。

### B0 安全前置与基线（0.25 人日，其中基线 verify 后台 ~20min）

1. **先改 `.gitignore:61`**：新增 `!apex-shared-parent/apex-common/src/main/resources/license/public.key` 等新路径白名单（新旧行并存，B1 稳定后删旧行）。
2. 跑一次全量基线 `mvnd -T4 -B clean verify` 确认起点全绿（当前工作区干净、上次全量门禁已过，此步可与 B1 准备并行）。
3. 记录 `-pl` / 路径引用的 rg 扫描快照，供收尾比对。

### B1 物理移动与 POM（0.5 人日）

1. `git mv` 37 个叶子目录到分组父下（§1.3 映射表），不用 shell `mv`。
2. 改 7 个分组父 `<modules>`（37 行去 `../`）。
3. 改 37 个叶子 `<relativePath>`（34 删 / 3 改 `../../apex-services-bom/pom.xml`）。
4. 改根 `pom.xml` 两处 argLine `../../logs`。
5. 验证：`mvnd -T4 -B clean verify` 到 tools-build 之外红是**预期**（契约测试未改），本轮仅验证反应堆可解析、编译可过：用 `mvnd -T4 -B clean package -DskipTests -Dpmd.skip=true -Dspotless.check.skip=true -Djacoco.skip=true` 先确认 45 项目 BUILD SUCCESS，再进 B2。

### B2 构建门禁与编排（0.75 人日）

1. `MavenModuleInventory.java:75` 断言改两级（按 `aggregator` 推导）。
2. `BuildManifestSyncTest.java:256` 路径前缀。
3. `BuildPathContractTest.java` argLine 断言 / fork cwd 断言 / 合成探针三处 lockstep。
4. `build-manifest.yaml` 头注"42"→"44"（顺手）。
5. `apex.sh`：PROJECT_ROOT 根标记定位、manifest 路径、`-pl` 改 `:`、JAR 查找两级化（或走 `apex_module_dir`）、`MODULE_PATH` build-arg 注入。
6. 验证：`mvnd -T4 -B -pl :apex-tools-build -am test` 绿（此后 verify 门禁的布局契约即生效，能兜住 B3/B4 的漏改）。

### B3 脚本与 CI（1.25 人日）

1. `scripts/lib/common.sh` 新增 `apex_module_dir()`（候选：根级直下 → 7 个分组父下一级，按分组约定查找，返回含 pom.xml 者）。
2. 按 §2.3 清单修 30 个脚本：`-pl` 全改 `:artifactId`；`${MODULE}/target` 类改走 `apex_module_dir` 或显式两级路径；fat jar 路径统一变量；扫描路径加前缀；echo 提示文本顺手更新。
3. 修 2 个 CI workflow（§2.4）。
4. 验证：改动脚本 `bash -n` 全过；抽查 `check-doc-links.sh`、`release.sh dry-run`（如有）可运行。

### B4 容器与部署（0.5 人日）

1. `Containerfile`：静态 COPY 清单两级化（47 行）；新增 `ARG MODULE_PATH`（内置 case 映射兜底）；PREBUILT 校验与最终 COPY 改 `${MODULE_PATH}`。
2. `Containerfile.quick`、`.containerignore` 两级化（先放行父目录）。
3. `podman-compose.yml` 按 Containerfile 兜底方案决定是否补传 `MODULE_PATH`（目标：compose 零改动或最小改动）。
4. 验证：`ContainerBuildContract` / `BuildManifestSyncTest` 门禁自动校验一致性；如本地有 podman 环境则冒烟构建 1 个服务镜像，否则如实标注"未验证运行条件"。

### B5 Java 测试与主代码（1.25 人日）

1. 按 §2.2 清单修 15 个文件 / 22 项，统一"根标记（AGENTS.md）+ 分组前缀"套路；两个静默失效项（`SqlScriptCommentContractTest`、`ComponentAssemblyContextTest`）修复后需**显式验证守护面恢复**（如断言扫描到跨分组文件），防止"改完仍绿但没扫到"。
2. 顺手项（可选）：统一"同级巧合"型路径为根定位。
3. 验证：受影响模块 `mvnd -T4 -B -pl :apex-system,:apex-gateway,:apex-auth-service,:apex-infrastructure-messaging,:apex-boot-all,:apex-tools-deployment -am test`。

### B6 文档与图表（1.5–2 人日，最大体量批次）

1. **9+1 篇重写**（§2.7）：README、AGENTS.md（含"不移动源码"表述反转、Flyway 表 6 行、`BlockingIoSchedulers` 链接、`-pl` 命令）、build-tools-usage（74 处）、development-standards、architecture-overview、delivery-scope、diagrams/README、两模块 README、MEMORY-CARD。
2. **a 类链接批量修（709+ 处 / 71 文件）**：按"模块 → 分组父"映射表脚本化替换（`apex-xxx/` → `<分组父>/apex-xxx/`），相对链接深度 +1；**跳过指向已退役模块的过期引用**（不混改）。
3. **图表双源**：`128_maven_module_groups.mmd/.svg`、`129_maven_parent_inheritance.mmd/.svg` 重绘（NOTE 文案、层级示意）；`95` 副标题复核。
4. b 类叙述：仅改有效指导文档（配置/部署/构建/交付），历史报告按 D5 豁免。
5. 根级两份工作文档轻量同步或移入归档（待确认 §8-Q4）。
6. 验证：`scripts/ci/check-doc-links.sh` 全绿（3788 扫描基线口径）。

### B7 全量收尾门禁（0.5 人日）

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B clean verify          # 全量门禁（遇双库 IT 容器竞争失败按 AGENTS 口径 -T1 单独复核）
mvnd -T4 -B spotless:check
bash -n $(git diff --name-only -- '*.sh')
scripts/ci/check-doc-links.sh
git diff --check
```

验收标准：反应堆 45 项目 BUILD SUCCESS、测试 `Failures: 0 / Errors: 0`（对照基线 6181 单测 + 388 集成口径，测试计数不下降——两个静默失效项修复后守护面恢复而非缩水）；doc-links 全绿；`BuildManifestSyncTest` 四方一致（根 POM 递归 modules ↔ manifest ↔ release.sh SERVICES ↔ Containerfile COPY）。

---

## 4. 工作量评估

| 批次 | 内容 | 文件数 | 引用/行数 | 人工估时 |
| --- | --- | ---: | ---: | ---: |
| B0 | 安全前置 + 基线 | 1 | 2 行 | 0.25 人日（基线后台跑） |
| B1 | git mv + POM | 38（37 移动 + 根 POM；另有 44 个 pom 编辑） | ~80 行 | 0.5 人日 |
| B2 | 门禁契约 + apex.sh 骨架 | 5 | ~40 处 | 0.75 人日 |
| B3 | 脚本 + CI | 33 | ~320 处 | 1.25 人日 |
| B4 | 容器部署 | 4 | ~90 行 | 0.5 人日 |
| B5 | Java 测试/主代码 | 15 | 22 项 | 1.25 人日 |
| B6 | 文档 + 图表 | ≥85 必改（a 类 71 + 重写 9 + 双源图 4 文件 + 杂项） | ~730 必改处；b 类可选 ~1050 处 | 1.5–2 人日 |
| B7 | 全量门禁收尾 | — | — | 0.5 人日 |
| **合计** | | **约 180–200 文件** | **约 1100–1200 处必改** | **约 6.25–7.5 人日** |

- 人工视角：机械替换占 ~70%，可脚本化按映射表执行；真正需要判断的是 B2 契约设计、B5 静默失效修复、B6 的 9 篇重写与 2 张双源图。
- Agent 辅助执行视角：机械批次（B1/B3/B4 大部、B6 替换类）可在一个会话内批量完成，但受全量 verify（~20min/次）与评审节奏约束，整体日历时间预计 2–4 天。
- 不确定度最大的两项：`Containerfile`/compose 链路的镜像构建冒烟（依赖本地 podman 环境）；B6 若不豁免 b 类叙述则工作量上限上浮 ~1 人日。

---

## 5. 风险与回滚

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| 公钥漏出版本控制 | 高（不可逆后果） | B0 先行改 `.gitignore`；仅用 `git mv`；收尾 `git ls-files | grep public.key` 核验 |
| 契约测试改错方向导致门禁失真 | 高 | B2 单批小步走，断言逐条对齐新布局；`MavenModuleInventory` 改用 `aggregator` 推导而非硬编码映射表 |
| 静默失效（测试绿但守护报废） | 中高 | §2.2 两个静默项修复后加"扫描面恢复"显式断言；B7 测试计数不低于基线 |
| 漏改某个低频脚本 | 中 | `rg` 扫描快照比对（B0 留底）；`BuildPathContractTest`/`ContainerBuildContract` 兜住主链路；`bash -n` 批量校验 |
| compose/k8s 镜像链路无法本地验证 | 中 | 有 podman 则冒烟；无则如实标注未验证条件，交 CI 首跑验证 |
| `-T4` 双库 IT 容器竞争假失败 | 低 | 按 AGENTS 既有口径 `-T1` 单独复核，不改弱 SLA |
| 历史文档改写引入新断链 | 中 | 只修 a 类链接 + 门禁复跑；b 类豁免不动 |
| mvnd daemon / JDK 环境漂移 | 低 | 每批前 `sdk env` + `mvnd --stop`（AGENTS 约束） |

回滚：按批次分组提交（若获授权），任一批可 `git revert`；B1 物理移动用 `git mv` 反向移动即可，内容零变更、rename 历史可 `--follow`。IDE 侧需重新导入 Maven 工程（`.idea` 不入库，用户侧操作）。

---

## 6. 豁免清单（本次不改写）

| 豁免对象 | 理由 |
| --- | --- |
| `docs/_archive/**`（116 文件 / 823 处） | 历史快照，沿用既有先例 |
| `scripts/ci/source-archive/**`、`scripts/archive/**` | 归档脚本 |
| `scripts/performance/results/20261113/start-minimal-stack.sh` | 历史压测产物快照（3 处路径；若用户要求可顺手修） |
| 有效区历史报告/专项测试文档的 b 类叙述（`docs/reports/*` 已完成报告、`docs/06-testing/*` 专项） | 叙述过期不影响链接门禁；a 类链接仍必修 |
| 退役模块过期引用（`apex-interfaces` / `apex-application` / `apex-auth-center` / `apex-admin-web` 共约 885 处） | 与本次主题无关，已另立任务：`retired-module-reference-cleanup-plan.md` |
| `CHANGELOG.md` 等历史记录 | 历史不改写 |

---

## 7. 与 AGENTS.md / README 既有约束的关系

- AGENTS.md"不移动源码、资源或 target"为第一阶段约束语句，本方案执行即用户决策推翻，AGENTS.md §模块与物理边界、README §Maven 分组与父链的对应句子在 B6 反转为新布局描述；AGENTS.md 继续作为唯一权威来源。
- `build-manifest.yaml` 同步清单第 1 条"所属分组 POM `<modules>`"、第 5 条"AGENTS.md 分类表"在本方案中均被覆盖，不新增漂移口径。
- 本次不改变：模块坐标、生产依赖、业务接口、服务与端口、Flyway 归属、租户与安全边界——AGENTS.md 其余章节无需连锁修改。

---

## 8. 裁决记录（2026-11-14 用户确认：八项均按默认建议执行）

| # | 问题 | 裁决 |
| --- | --- | --- |
| Q1 | 三个例外模块（`apex-api-contract`/`apex-integration`/`apex-boot-all`）物理归属 | 随聚合父目录，relativePath 跨两级指向 apex-services-bom（D1） |
| Q2 | `-pl` 形式 | 全仓统一 `:artifactId`（D2，B3 执行） |
| Q3 | 注册表是否新增 `path` 字段 | 不新增，分组约定 + `MavenModuleInventory` 门禁锁定（D3） |
| Q4 | 根级两份工作文档 | 轻量同步（B6 执行） |
| Q5 | b 类历史报告叙述 | 豁免，仅 a 类链接必修（D5） |
| Q6 | 提交节奏 | 按批次分组提交 Conventional Commits（D6） |
| Q7 | 退役模块过期引用（约 885 处） | 另立任务：`retired-module-reference-cleanup-plan.md`，不混入本次 |
| Q8 | 执行节奏 | B0–B1 完成后暂停展示 diff，等待放行后续批 |

---

## 9. 执行记录

| 批次 | 状态 | 内容与结果 | 门禁 | 提交 |
| --- | --- | --- | --- | --- |
| B0 | ✅ 2026-11-14 | `.gitignore` 新增 `!apex-shared-parent/apex-common/.../public.key` 白名单（与旧行并存，B7 收尾清理旧行）；rg 快照存 `/tmp/apex-b1-snapshot/`（`-pl` 引用 23 处、`<module>../` 37 条、fat jar 引用 14 处）；基线依据 = HEAD `e83c99ee` 工作区干净且同 HEAD 上一次全量 verify 全绿（11-14 知识库战役记录），未重复跑基线 | — | 随 B1 一并提交 |
| B1 | ✅ 2026-11-14 | `git mv` 37 叶子（3467 个 rename 条目，根级仅剩 7 分组父）；7 分组父 `<module>` 去 `../`（9/6/4/11/2/4/1）；25 叶子整行删 relativePath、9 个单行 `<parent>` 内联叶子摘除 relativePath 片段、4 文件（gateway/auth-service/file-service/workflow-service）连带删 relativePath 说明注释；3 例外改 `../../apex-services-bom/pom.xml`（api-contract 注释同步改写）；根 POM argLine 两处 `../logs` → `../../logs` | `mvnd -T4 -B clean package -DskipTests -Dmaven.javadoc.skip=true` 全反应堆 **BUILD SUCCESS（45/45，2:47）**；布局契约测试按计划留待 B2 lockstep，本批跳过测试 | build 提交 |
| B2 | ✅ 2026-11-14 | `MavenModuleInventory` 布局断言改按聚合者推导两级目录（含 6 处合成树夹具适配与 `physicallyNestedLeaf` 语义反转为越组失败）；`ContainerBuildContract` 放行契约改收仓库根相对目录；`BuildManifestSyncTest` 真实仓库路径前缀；`BuildPathContractTest` 三处 lockstep；`apex.sh` 根定位改 `.mvn/` 标记 + `colonize_ids`（`-pl` 冒号化）+ `apex_module_dir`（zsh 覆盖版）+ JAR 查找/MODULE_PATH 注入；common.sh 新增 `apex_module_dir`；Containerfile 45 行 COPY 两级化 + 双阶段 `ARG MODULE_PATH`；`Containerfile.quick`、`.containerignore` 同步；manifest 注释 42→44。B4 主体（容器三件套）并入本批以保证门禁完整性 | `mvnd -pl :apex-tools-build -am test` **67/67 全绿**（含真实仓库四方一致性） | build 提交 |
| B3 | ✅ 2026-11-14 | 17 文件 `-pl` 冒号化（release.sh 用 `${SERVICES/#/:}`、verify-middle-platform 用 zsh 分词前缀、run-dual-database/assemble-topology 变量冒号）；29 文件路径前缀映射（perl 最长匹配）；release.sh JAR 循环/services-bom 前缀、app.sh/start-local.sh 通配一级目录、build.sh 提示文本、check_javadoc 默认值前缀；2 个 CI workflow（doc-link-check 冒号、ci-cd init.sql 与 8 行 release 产物路径）。**教训**：①perl `%g` 中 `apex-common=>...` 带连字符裸键被解析为减法、`map {($_=>...)}` 花括号被解析为匿名哈希——改用 `"module:group"` 字符串对 + split 后修复；②zsh 下 common.sh 的 `get_project_root` 回退 `$0` 命中本模块 pom——apex.sh 内用 `.mvn/` 锚定的 PROJECT_ROOT 覆盖 `apex_module_dir`；③`apex_module_dir` 须返回模块目录（漏拼 `/$1` 曾致 status 全部"未构建"） | 全部脚本 `bash -n`/`zsh -n` 通过；`-pl` 裸形式清零；前缀配对校验零错误；`apex.sh status` 冒烟 12 模块产物正确渲染 | build 提交 |
| B4 残留 | ✅ 2026-11-14 | podman-compose.yml 确认零改动：8 个 build 段仅传 SERVICE_MODULE 且全部为 services-bom 模块，Containerfile 默认 `MODULE_PATH=apex-services-bom/${SERVICE_MODULE}` 恰好覆盖；apex.sh 对 apex-example 显式注入 MODULE_PATH；k8s 清单仅镜像名，产镜像链路修复后自动恢复。镜像构建冒烟未执行（本地环境约束），如实标注为未验证运行条件 | compose 参数核对 | — |
| B5 | ✅ 2026-11-14 | 17 文件 / 22 项路径失效修复：根定位统一为仓库根独有标记（AGENTS.md / podman-compose.yml / multiModuleProjectDirectory）；跨组路径常量加分组前缀；两个静默失效守护补自检（SqlScriptComment ≥3 分组覆盖断言、ComponentAssemblyContext 业务库源码目录存在性自检）；NoBoundedElastic 受约束模块目录缺失即失败；TokenV42Recover SOURCE_PARENT_LEVELS 3→4。首轮门禁暴露 OPEN_ROUTER_FILES 常量未前缀（只修了解析深度），已修正重跑 | `mvnd -T4 -B -pl :apex-system,:apex-gateway,:apex-auth-service,:apex-infrastructure-messaging,:apex-boot-all,:apex-tools-deployment -am test` BUILD SUCCESS（5287 单测 0 失败） | test 提交 |
| B6 | ✅ 2026-11-14 | 机械层：docs 有效区 716 链接 + 归档区 241 文件链接目标按分组映射重指向（归档叙述豁免、链接门禁强制）、html href/src、docs 82 文件 -pl 冒号化；重写：AGENTS/README/双源图 128+129（mmd 与 SVG 同改）/build-tools-usage 等 15+ 篇/模块 README 4 个/根级两工作文档/MEMORY-CARD。**豁免策略修正**：归档叙述不改写但链接目标必须重指向（门禁约束）；发现既有漂移如实标注（95 图未含知识库依赖边；docs 中退役模块 -pl 命令与 HTML 图内示意命令留待 Q7） | `check-doc-links.sh` 全绿（757 md + 17 html，3701 链接 0 断裂）；三轮迭代消除 250→2→0（双前缀去重正则首版有误、归档树误前缀 2 处） | docs 提交 |
| B7 | ✅ 2026-11-14 | `.gitignore` 旧公钥白名单行清理（去重后仅保留新路径行，公钥 2 处追踪保持）；首轮全量 verify 死于 B5 引入的 ConsentV40UpgradeTest palantir 格式违规（test 门禁不含 spotless），`spotless:apply` 修复 10 个测试文件（纯格式）后重跑；全量脚本 `bash -n`/`zsh -n` 扫尾通过；`git diff --check` 干净。工作区的 MEMORY-CARD 与 weekly 周报为用户侧在途改动，按纪律保留不动、不混入提交 | `mvnd -T4 -B clean verify` **BUILD SUCCESS（45/45 模块，14:33；surefire 6509/0 失败/0 错误/35 跳过，高于基线 6181——含知识库新增；Failsafe/Spotless/PMD/JaCoCo 随 verify 全过）** | 本笔提交 |
