# 模块划分与微服务架构评审报告

- 评审日期:2026-11-07
- 评审对象:仓库 35 个 Maven 模块、网关 + 六业务服务 + 任务代理/脱敏服务的运行拓扑、模块间依赖与契约
- 评审依据:`AGENTS.md`(模块与物理边界、依赖表)、`docs/02-architecture/context-map.md`(DDD 战略基线)、`apex-tools-build` 模块注册表(build-manifest)、根 `pom.xml`、各模块 POM 与源码实测
- 评审方法:逐模块 POM 依赖核对、包结构扫描、跨服务调用与契约使用追踪、网关路由核对、文档与代码一致性抽查
- 结论性质:初版为评审结论与建议方案;2026-11-07 经确认后按第 5 节方案执行修复(处置结果见第 7 节,F1/F2 处置依据执行期复核证据做了修订);2026-11-08 补充执行未尽事项(MEMORY-CARD 端口残留、F8–F12 逐项处置、测试计数口径溯源),见第 7 节对应条目

## 1. 架构现状速览

| 维度 | 现状 |
| --- | --- |
| 模块总数 | 35(根 POM 聚合,build-manifest 四方一致性门禁覆盖) |
| 运行服务 | 网关 8080 + system 8082/8083 + auth 8081 + workflow 8093 + office 8084 + file 8085 + integration 8094/8097;任务代理 8095、脱敏服务 8096(optional) |
| 业务库 | system/auth/workflow/file/office/integration 六库,库与运行模块分离 |
| 数据自治 | 六域独立 Flyway 迁移链(MySQL/PostgreSQL 双轨),独立库 |
| 共享内核 | `apex-common`(109 类)、`apex-domain`(19 类),无具体业务实体 |
| 共享设施 | 按能力拆分为五个 JAR(persistence/cache/messaging/remote/integration) |
| 跨服务契约 | `apex-api-contract`(16 个主类:事件 5 + 内部 DTO 11,含本轮新增 `TenantStatusSnapshotDTO`) |
| 组合部署 | `apex-boot-all` 仅装配(4 类),basic/示例档单进程形态 |
| 门禁 | ArchUnit 分层/网关/租户守卫、BuildManifestSyncTest、Spotless、PMD/p3c、JaCoCo |

## 2. 符合项(已达到微服务架构要求的方面)

1. **库/运行模块分离**:六个业务库承载领域/应用/专属基础设施,七个运行模块仅做装配与接口适配;`apex-boot-all` 只装配不含业务实现,组合部署形态与微服务形态共用同一批业务库,避免双轨实现。
2. **数据库自治**:六域独立迁移目录、独立版本链(`FlywayMigrationVersionTest` 校验一致性),不跨库事务,一致性走 Outbox/补偿;符合 database-per-service。
3. **共享内核克制**:`apex-common`/`apex-domain` 体量可控且无业务实体,system 专属实体/仓储/迁移留在 system;跨域网关契约(`apex-domain` 的 gateway 包)保持最小集。
4. **共享设施按能力拆分**:五个 infrastructure 能力 JAR 替代单一大 JAR,业务库可按需依赖(office/workflow 不依赖任何 infrastructure JAR 即可成库),耦合面真实收紧。
5. **防腐层(ACL)模式落地**:消费域自定义端口(`TenantStatusPort` 在 auth/file 各自 domain 定义、`OfficeOrgUserQueryPort` 及消费端快照 DTO 在 office 定义),实现类在运行模块,不直接读对方库表。
6. **内部端点隔离**:服务间调用走 `/internal/v1/**`,网关对该前缀强制 403,仅集群内直连;外部契约与内部契约物理分层。
7. **远程调用能力统一**:同步远程调用收敛到 `apex-infrastructure-remote`(RemoteApiClient、熔断、混沌注入、ApiCallObservation),服务间无 Feign,统一 WebClient。
8. **交付口径单一事实来源**:build-manifest 登记模块分类/交付四态/端口,`BuildManifestSyncTest` 强制与根 POM、打包插件、release.sh SERVICES 四方一致。
9. **服务增强可插拔**:job/seata starter 默认禁用、按依赖装配;job-service 不迁移 system 数据所有权,只代理九个任务接口;masking-service 无持久化。
10. **网关薄层**:`GatewayArchTest` 强制网关无业务四层实现、无 Servlet 栈;路由与环境变量注入(`ROUTE_*_URI`)分离。
11. **许可与商业化隔离**:`apex-license-*` 与 `apex-license-tools`(卖方内部工具)边界清晰,发行剥离口径登记在 manifest。
12. **运维/构建工具不进运行期**:`apex-tools-deployment`、`apex-tools-build` 零生产依赖、不参与服务装配,工具与运行态隔离。

## 3. 发现项

分级口径:P1 结构性问题(影响架构一致性,建议排期整改);P2 漂移/规范问题(低成本修正);P3 观察项(记录既有取舍,本轮不动)。

### F1(P1)六服务接口层归属不一致,integration 偏离主流形态

> **处置修订(2026-11-07 执行时复核)**:初版报告将 integration 判定为"偏离主流形态、半迁移迹象"。深入核实后该定性不成立——integration 的接口层在业务库内是 C7 组件化的刻意设计:`IntegrationServiceBoundaryTest` 明文记载 connector/device 接口装配经 `apex.component.integration.interfaces-enabled` 开关守护、组件入口经 `AutoConfiguration.imports` 自动装配;业务库内协议层使 `apex-integration` 成为"引入依赖即获得完整能力(含 HTTP API)"的自含组件,boot-all 薄 jar(运行 classpath 仅含六个业务库,不含 service 模块)因此能提供 integration 域的 HTTP 端点。auth/file/office/workflow 四个组件入口注释"协议层归属运行模块"反映的是 Phase 6 拆分后的历史形态(C7 改造时协议层已在 service,无法回迁),两者都是有意为之。真实问题是**该约定从未写成文字**。故本项修复为:将协议层归属口径写入 AGENTS.md 与 context-map,不做物理迁移(迁移反而回退 boot-all integration HTTP 与 C7 自含组件能力)。

实测接口层(Router/Handler)归属:

| 服务 | 业务库内 | 运行模块内 | 形态 |
| --- | --- | --- | --- |
| auth | 0 | 14 Router / 16 Handler | A |
| file | 0 | 7 Router / 8 Handler | A |
| office | 0 | 15 Router / 17 Handler | A |
| workflow | 0(HTTP 层;库内 `WorkflowTimeoutHandler` 为调度器,非 HTTP) | 4 Router / 5 Handler | A |
| system | 34 Router / 55 Handler | 0 | B(D07 裁决维持现状) |
| integration | 2 Router + 6 Handler(含 2 个 Responses 辅助类与 connector/device 两个 dto 子包,共 29 文件) | 0(HTTP 层;service 内有 interfaces/security 安全件) | B(C7 自含组件设计) |

- 影响(修订后):两套形态均为刻意设计但缺少成文约定,新增服务与组件消费方无所适从;context-map 演进方向 C4 的表述("apex-system interfaces 层迁至运行模块")与 D07 裁决结果相矛盾,误导后续演进。
- 修复:口径成文(见 R3 修订),不迁移。

### F2(P1)apex-system 接口层在业务库内且包名与模块不对应

> **处置修订(2026-11-07 执行时复核)**:system 接口层迁移已被项目裁决"维持现状不实施"——`docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md` v3.2(2026-11-01)记录"C4(interfaces 迁移)经裁决维持现状不实施",`docs/08-project-management/plans/platform-closure-remediation-plan.md` 同样记载"C4 interfaces 迁移(已裁决维持现状)"。初版报告建议的 R4 迁移与该裁决冲突,且会造成 boot-all system 域失去 HTTP 能力,撤销。本项修复收敛为:修正 context-map 中与裁决矛盾的 C4 表述,将裁决口径成文;包根命名空间差异(`com.apex.scaffold.interfaces` 等)作为已知历史兼容项保留(AGENTS.md 已有"Java 包名保持不变"约束),如需变更须单独立项裁决。

- `apex-system` 顶层包为 `com.apex.scaffold.{interfaces,application,domain,infrastructure,autoconfigure}`,是六库中唯一没有 `<域>` 根命名空间的库;其余五库均为 `com.apex.scaffold.<域>.*`。
- 影响:按包名编写的架构规则存在歧义源;模块名与包根不对应造成 IDE 定位与心智负担。
- 约束:Java 包名保持不变是迁移期兼容决定(见 AGENTS.md),本项作为已知项管理。

### F3(P2)跨服务契约表达分裂,内部契约无防漂移载体

- `apex-api-contract` 实际覆盖:auth 开放应用注册(OpenApp*)、token 校验(TokenValidation*)、五个领域事件。消费方实测:apex-system、apex-auth、apex-auth-service、apex-office-service。
- 未入契约的内部契约:
  - 租户状态:`GET /internal/v1/tenants/{code}/status`(system 提供,auth-service 的 RemoteTenantStatusAdapter 以 JsonUtil 手工解析),system 与 auth 两端各自定义数据形态,无共享 DTO、无契约测试;
  - office → system 组织/用户查询:office 用消费端 ACL DTO(`OfficeOrgUserSnapshot` 等,消费端定义属合理 ACL),但端点路径与响应结构只存在于实现代码。
- 事件契约半悬挂:`WorkflowInstanceStartedEvent`/`TaskAssignedEvent`/`TaskCompletedEvent` 发布方(workflow-service)引用 contract,但全仓未见消费方引用这些事件类;context-map 宣称"工作流 → 系统:事件驱动,CONTRACT 订阅方 SYS"与当前代码不完全一致。
- 根 POM 注释将 api-contract 描述为"Feign 接口 + 共享 DTO",与"服务间不引入 Feign"的现行事实不符。
- 影响:内部接口漂移只能靠联调发现;文档宣称的契约关系与代码不一致,误导后续接入方。

### F4(P2)context-map 缺失 integration 上下文

- `docs/02-architecture/context-map.md`(M3/批次 C0 基线)上下文清单仅列系统治理/认证授权/工作流/文件/办公协同/网关六项,M5/E4 加入的 integration(connector + device 子域)未纳入;上下文关系图同样缺失。
- 文件自述"代码结构与本文不一致时以代码为准,并应同步修订本文",当前即处于应修订状态。

### F5(P2)根 POM 注释漂移

- `pom.xml` 中 `apex-api-contract` 注释含"Feign 接口"(F3 同源);
- basic profile 依赖裁剪注释仍写"apex-interfaces 侧高级依赖",该模块已退出 reactor;
- 注释引用的 `docs/08-project-management/plans/microservice-migration-plan.md` 存在(该链接有效,无需处理)。

### F6(P2)端口规划存在既有冲突,网关兜底默认值形态不一

- workflow 业务端口 8083 与 system 管理端口 8083 冲突,当前靠 Compose hostPort 8093 规避(manifest 已登记);同机直跑两个服务仍需人工错开。
- 网关路由兜底默认值形态不一:auth/workflow/file/office/system/integration 用 `lb://apex-*-service`,而 office 消息两条路由与 job/masking 用硬编码 `http://127.0.0.1:80xx`、office WS 用 `ws://127.0.0.1:8084`。关闭 Nacos 时改明确地址是既定口径,但默认值两套形态并存,依赖注入遗漏时故障面不同。
- job-service `ROUTE_JOB_URI`、masking `ROUTE_MASKING_URI` 为 optional 服务,网关路由常驻但其目标可缺席,健康语义已在 manifest 说明,无阻塞问题(记录备查)。

### F7(P3)仓库根目录残留物

- `:-`(232 字节 macOS 代码签名 designated 字符串,误生成且曾误入库,处置时删除);
- `apex-interfaces/`、`apex-application/` 空目录(模块已退役,目录残留);
- `backups/`、`_tmp/` 已被 .gitignore 覆盖但仍占工作区;根 `target/` 目录存在。
- 均不影响构建,属工作区卫生问题。

### F8(P3)`PageResponse` 三份定义(已知项)

- common 版(含 hasNext)、system 版、file 版并存,字段与序列化不一致;AGENTS.md 已有"修改前检查实际使用类型"的口径。收敛涉及接口兼容,单列方案不在本轮执行。

### F9(P3)双持久化栈并存(既有迁移产物)

- system/auth 同时持有 MyBatis-Plus(JDBC)与 R2DBC 依赖;workflow/auth 租户隔离走手写 JdbcTemplate fail-closed 口径。技术栈收敛属大工程,记录为观察项。

### F10(P3)apex-example 名实不符

- 该模块实际是 basic/示例档与 k8s 单体形态的聚合运行模块(optional 交付、显式 mainClass、镜像构建入口),生产依赖 apex-system;"example"命名易误导为纯示例代码。更名影响交付脚本与文档,列为观察项。

### F11(P3)apex-infrastructure 兼容壳无退役口径

- 旧坐标兼容聚合仍在 reactor(manifest deliverable: none),无实现源码;保留是兼容决定,但未见退役时间表或下游使用说明。

### F12(P3)office 站内信实时通道单实例约束(既有设计)

- WS 票据与连接注册表为单实例内存态,两条路由必须指向同一实例,水平扩展需引入外部注册表或会话粘滞;AGENTS.md 已如实声明。属能力边界而非缺陷,列为演进观察。

## 4. 微服务架构符合度评估

| 维度 | 评价 | 说明 |
| --- | --- | --- |
| 服务边界与单一职责 | 良好 | 六域边界与 context-map 基本对齐;system 职责偏宽(平台治理聚合面大)属规模问题而非划分错误;integration 双子域包结构隔离可接受 |
| 数据自治 | 优秀 | 独立库 + 独立迁移链 + 双数据库语义核对 |
| 独立部署 | 良好 | 七 service-jar 交付口径明确,optional 服务按需部署;端口冲突(F6)已于 2026-11-07 消除(见第 7 节) |
| 服务间通信 | 良好 | 统一 WebClient + 熔断,无 Feign;内部端点网关封禁 |
| 契约管理 | 中(评审时点) | 评审时覆盖面窄、内部契约双端定义、事件消费关系与文档不一致(F3),已按第 7 节处置:契约清单文档化 + tenant status 契约化 |
| 去中心化治理/形态统一 | 中(评审时点) | 接口层两套形态经核实均为刻意设计(F1/F2),归属口径已成文;持久化栈并存(F9)已裁决维持 |
| 容错与弹性 | 良好 | 熔断/混沌注入/降级能力集中在 remote 能力层 |
| 可观测性 | 良好 | Prometheus/SkyWalking/logstash、管理端口分离、traceId 口径统一 |
| 演进能力 | 优秀 | boot-all 组合部署、C7 组件化/C8 事件通道方向已立项 |

总体结论:**模块划分合理、规范程度较高,微服务架构主干要求(数据自治、独立部署、薄网关、契约隔离、可插拔增强)均成立**;主要欠账集中在"形态统一"(F1/F2)与"契约治理"(F3),均为可分批收敛的结构性问题,不存在需要推翻重来的划分错误。(上述欠账已于 2026-11-07/08 全部处置,见第 7 节。)

## 5. 优化调整方案(待确认后执行)

原则:只做任务需要的变更;每批独立提交、独立验证;不与日常功能迭代混做;文档与代码同批同步。

### R1 文档基线修正(P2/F4/F5,低风险)

> 执行修订:SYS↔INTEGRATION 关系以「系统聚合各域统计端点」边表达——integration 不经 ACL 消费 system 内部端点(其权限校验走 auth `/auth/v1` 公开契约),详见第 7 节 R1。

- context-map 上下文清单与关系图补 integration(connector/device)上下文及相关关系;
- 根 POM 修正 api-contract 注释("Feign 接口"→ 按实际:跨服务内部 API 与共享 DTO)、basic profile 注释去掉 apex-interfaces 表述;
- 核实 workflow 事件实际消费方,按核实结果修订 context-map 事件关系描述(有消费则补图,无消费则标注"发布方就绪、消费方未接入")。
- 验证:`scripts/ci/check-doc-links.sh`、文档 diff 核对;不涉及 Java 代码。

### R2 仓库卫生清理(P3/F7,低风险)

- 删除根目录 `:-` 文件与空的 `apex-interfaces/`、`apex-application/` 目录;`backups/`、`_tmp/`、根 `target/` 属本地忽略物,确认无引用后一并清除。
- 验证:`git status` 干净、`mvnd -T4 -B -pl apex-tools-build -am test` 不受影响。

### R3 协议层归属口径成文(P1/F1 修订处置,低风险)

> 修订说明:初版本项为"integration 接口层迁至运行模块"。复核 C7 组件设计与 boot-all 装配机制后撤销物理迁移(理由见 F1 处置修订),改为将两套形态的约定与理由写入权威文档,消除"无所适从"的实际影响。

- AGENTS.md「模块与物理边界」补充协议层归属口径:auth/file/office/workflow 协议层在运行模块(Phase 6 形态);integration 为 C7 自含组件试点,协议层在业务库内经 `apex.component.integration.interfaces-enabled` 开关守护;system 接口层维持现状(D07 裁决)。
- context-map 补 integration 上下文、修正 C4 表述为裁决口径、补充协议层归属说明。
- 验证:`scripts/ci/check-doc-links.sh`;不涉及 Java 代码。

### R4 system 接口层迁移——撤销,改为裁决口径成文(P1/F2 修订处置)

> 修订说明:初版本项为"system 接口层迁至运行模块(高风险)"。执行时发现 D07 已于 2026-11-01 裁决"维持现状不实施"(两处计划文档记录),继续执行将与裁决冲突并使 boot-all system 域失去 HTTP 能力。撤销迁移。

- context-map 演进方向 C4 行修正为裁决口径(维持现状),消除与 `unified-auth-tenant-platform-remediation-plan.md`/`platform-closure-remediation-plan.md` 记录的矛盾。
- 包根命名空间差异列为已知兼容项,如需变更须单独立项裁决。
- 验证:`scripts/ci/check-doc-links.sh`。

### R5 内部契约治理(P2/F3,中风险)

- 建立 `/internal/v1/**` 内部契约清单文档(提供方、消费方、路径、请求/响应结构、DTO 归属),先文档化防漂移基线;
- 评估将 tenant status 请求/响应 DTO 提升入 `apex-api-contract`,system/auth 两端改为共用(contract 模块零业务实体约束不受影响,均为跨服务 DTO);office 的消费端 ACL DTO 保持域内定义,仅登记端点契约;
- 按核实结果处理 workflow 事件消费关系(与 R1 联动)。
- 验证:受影响模块 `-am test`;契约 DTO 移动需两端编译与契约测试通过。

### R6 端口与网关兜底治理(P2/F6,中风险)

- 消除 workflow 8083/system 管理端口 8083 冲突:workflow 业务端口改为 8093(与 boot-all 域端口、Compose hostPort、同机开发指引既有口径一致;Prometheus 抓取目标已是 8093,无需变更);同步 manifest(移除 hostPort 特例)、workflow `application.yml`、Compose 映射、AGENTS/README/现行文档;历史测试报告与计划文档不改写。
- 网关路由兜底统一为「注册成员默认 `lb://`,非注册成员(optional 服务)默认直连、由部署环境注入」的单一口径:office 消息与 WS 两条路由兜底从硬编码 `127.0.0.1:8084` 改为 `lb://apex-office-service`(已核实 Spring Cloud Gateway `WebsocketRoutingFilter` 按 `Upgrade: websocket` 请求头匹配并 `convertHttpToWs`,`lb://` 对 WS 握手成立;Compose 单实例满足两路由同实例约束);job/masking 保持直连兜底(不在注册中心),与 Compose 既有注入模式一致,口径成文。
- 验证:manifest 同步测试、网关模块测试、Compose 配置核对、文档链接检查。

### R7 观察项处置

初版口径为"本轮不执行";2026-11-08 经确认后逐项处置完毕,处置方式与边界见第 7 节 F8–F12 各条(F8 代码对齐、F10 文档澄清、F9/F11/F12 裁决记录)。

### 建议执行顺序与门禁

R1 → R2 → R3 → R5 → R6。每批:受影响模块 `-am test` + `spotless:check` + `git diff --check`;全部批次结束后以 `mvnd -T4 -B clean verify` 全量门禁收口(以反应堆 BUILD SUCCESS 与测试计数为准)。

## 6. 刻意设计项(评审确认保留,不作为问题)

- system 迁移期保留 `interfaces/application` 旧包名(AGENTS.md 既有约束,D07 裁决维持现状);
- job-service 只代理 system 九个任务接口、不迁移数据所有权;
- masking 策略启动配置、无 CRUD;
- integration 的 connector/device 双子域包结构隔离(同库不同包,当前规模不拆库);
- 五基础设施 JAR 中 cache 对 integration 仅 test scope 等按需依赖口径。

## 7. 处置结果(2026-11-07 执行,2026-11-08 补充)

- R1:已执行——context-map 补 integration 上下文、C4 表述修正为 D07 裁决口径、事件关系按核实结果修订;根 POM 两处注释修正;AGENTS.md 补协议层归属口径。
- R2:已执行——删除 `:-` 误生成文件、`apex-interfaces/` 与 `apex-application/` 空目录、根 `target/` 构建残渣;`backups/` 经查为个人机器备份、`_tmp/` 为 javadoc 扫描工作区、`tools/local-deployment.jar` 为脚本产物,均保留不删。
- R3/R4:按修订处置执行——协议层归属与 D07 裁决口径成文,无代码迁移;初版物理迁移建议撤销(证据见 F1/F2 处置修订)。
- R5:已执行——新增 `docs/03-api/internal-api-contracts.md` 内部契约清单;`TenantStatusSnapshot` 契约化为 `apex-api-contract` 的 `TenantStatusSnapshotDTO`,system 提供方与 auth-service 消费方同步改用,消除手写 JSON 解析漂移;事件消费关系核实结果:契约事件发布方均类型化引用,消费方现状为消费端 JSON 解析(auth 租户事件监听器、system 用户消息监听器),workflow 事件仓内暂无消费方,已在清单与 context-map 如实记录。
- R6:已执行——workflow 业务端口 8083→8093 全仓同步(manifest 移除 hostPort 特例、Compose 映射、chaos 脚本、AGENTS/README 与现行运维文档);网关 office 消息/WS 兜底在 application.yml、application-prod.yml 与 `config/nacos/apex-gateway.yaml` 三处统一为 `lb://apex-office-service`,注册/非注册成员兜底口径成文;网关守护测试(`GatewayApplicationTest`/`GatewayRouteProfileConfigTest`)同步放宽为接受 `lb://`/`ws`/`wss` 并修正 javadoc 中"lb:// 丢弃升级握手"的过时论断(依据:SCG 4.3.0 `WebsocketRoutingFilter` 字节码核实,`Upgrade: websocket` 请求头触发 `convertHttpToWs`);历史测试报告与计划文档不改写。
- 验证结果:`mvnd -T4 -B clean verify` 全量门禁 **BUILD SUCCESS**(反应堆 36 模块全绿;2026-11-07 首跑 wall clock 11:37,2026-11-08 F8 处置后复跑 14:36);failsafe 391 例 0 失败 0 错误(含 integration 双库迁移演练 2 例,经本机容器运行时执行;首次全量跑曾因本机无 Docker 环境报 Testcontainers 无法连接,启动 OrbStack 并以 `DOCKER_HOST` 指向后复跑通过,属环境依赖而非代码问题);`scripts/ci/check-doc-links.sh` 738 文件 3536 链接 0 断裂;`git diff --check` 干净。
- 测试计数口径结论(2026-11-08 溯源):全量门禁存在两种口径——**控制台 Results 块合计 6211**(surefire 5820 + failsafe 391,skip 33),**XML 报告文件合计 6147**(surefire 1270 文件 5756 + failsafe 42 文件 391);差异 64 全部在 apex-system——该模块存在两个 surefire 执行块(主块 1757 + interfaces 层块 19/skip 5),报告文件口径未完整保留主块全部类文件。两种口径 failures/errors 均为 0。2026-11-05 基线的 6181 为控制台口径,与当前同口径 6211 相比 +30,与期间新增测试(架构契约测试、站内通知测试、本轮 +2 契约测试)量级吻合,无测试丢失。
- F8:已处置(2026-11-08 补充)——system/file 两份 `PageResponse` 新增派生属性 `hasNext`(当前页码小于总页数;不落字段,构造器与 builder 两路径口径一致),构造参数校验口径与 common 对齐(`pageSize` 空或非正抛 `IllegalArgumentException`、`total` 空按 0);`records` 序列化别名确认为 common 历史兼容产物,system/file 不引入以免同一列表在响应中重复出现——wire shape 的 `hasNext` 维度已对齐,`records` 差异为保留决策,合并不再单独立项。
- F9:已裁决记录(2026-11-08)——双持久化栈(MyBatis-Plus/JDBC + R2DBC)为迁移期产物,现状维持;auth 的 R2DBC 与 JdbcTemplate 并用、workflow/auth 手写租户口径(fail-closed)已在 AGENTS.md 持久化章节如实记载。技术栈收敛影响面大(数据访问层重写级),仅在出现明确驱动(如虚拟线程 jdbc 池钉住载体线程的实测问题)时单独立项。
- F10:已处置(2026-11-08,经确认取舍)——保留 `apex-example` 名称(坐标变更将连动交付 jar 名与镜像构建参数,破坏交付兼容,收益仅为命名观感);补强 pom `<description>` 与 `ExampleApplication` javadoc,澄清"示例档与 k8s 单体形态聚合运行入口"定位;manifest 既有登记不变。
- F11:已核实并记录(2026-11-08)——全仓生产依赖 `apex-infrastructure` 兼容壳的模块为零(仅根 POM 与 services-bom 的 dependencyManagement 登记及其自身聚合),壳仅服务历史外部消费者;退役需先确认下游无消费,在 AGENTS.md/manifest 现有"旧坐标兼容"口径上不另设时间表。
- F12:已裁决记录(2026-11-08)——office WS 单实例内存态为既有设计边界(AGENTS.md 与 `docs/05-operations/in-app-messaging.md` 已完整记载约束与 HTTP 补偿路径);跨实例实时广播(Redis 注册表/pub-sub)属新能力建设,需独立方案与水平扩展场景驱动,不随模块评审实施。
