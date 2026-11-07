# 模块划分与微服务架构评审报告

- 评审日期:2026-11-07
- 评审对象:仓库 35 个 Maven 模块、网关 + 六业务服务 + 任务代理/脱敏服务的运行拓扑、模块间依赖与契约
- 评审依据:`AGENTS.md`(模块与物理边界、依赖表)、`docs/02-architecture/context-map.md`(DDD 战略基线)、`apex-tools-build` 模块注册表(build-manifest)、根 `pom.xml`、各模块 POM 与源码实测
- 评审方法:逐模块 POM 依赖核对、包结构扫描、跨服务调用与契约使用追踪、网关路由核对、文档与代码一致性抽查
- 结论性质:本文档只陈述评审结论与建议方案,所有优化调整均在方案确认后另行实施

## 1. 架构现状速览

| 维度 | 现状 |
| --- | --- |
| 模块总数 | 35(根 POM 聚合,build-manifest 四方一致性门禁覆盖) |
| 运行服务 | 网关 8080 + system 8082/8083 + auth 8081 + workflow 8083 + office 8084 + file 8085 + integration 8094/8097;任务代理 8095、脱敏服务 8096(optional) |
| 业务库 | system/auth/workflow/file/office/integration 六库,库与运行模块分离 |
| 数据自治 | 六域独立 Flyway 迁移链(MySQL/PostgreSQL 双轨),独立库 |
| 共享内核 | `apex-common`(109 类)、`apex-domain`(19 类),无具体业务实体 |
| 共享设施 | 按能力拆分为五个 JAR(persistence/cache/messaging/remote/integration) |
| 跨服务契约 | `apex-api-contract`(18 类:事件 5 + 内部 DTO 13) |
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

实测接口层(Router/Handler)归属:

| 服务 | 业务库内 | 运行模块内 | 形态 |
| --- | --- | --- | --- |
| auth | 0 | 14 Router / 16 Handler | A |
| file | 0 | 7 Router / 8 Handler | A |
| office | 0 | 15 Router / 17 Handler | A |
| workflow | 0(HTTP 层;库内 `WorkflowTimeoutHandler` 为调度器,非 HTTP) | 4 Router / 5 Handler | A |
| system | 34 Router / 55 Handler | 0 | B(历史遗留) |
| integration | 3 Router / 6 Handler | 0(HTTP 层;service 内已有 interfaces/security 安全件) | B |

- 形态 A 是其余四个服务的统一形态,也是 context-map C4/D07 的演进目标("apex-system interfaces 层迁至运行模块,使五服务形态统一")。
- integration 是 M5/E4 新拆服务,却采用了形态 B,且出现半迁移迹象(HTTP Handler 在库、安全切面/过滤器在 service),边界语义模糊:业务库承载了协议适配职责,违反"业务库 = 领域+应用+基础设施"的既定口径。
- 影响:六服务两套装配模型,新增服务无所适从;`apex-integration` 无法作为纯业务库被其他运行形态(boot-all)以与 auth/office 相同的语义复用。

### F2(P1)apex-system 接口层在业务库内且包名与模块不对应

- `apex-system` 顶层包为 `com.apex.scaffold.{interfaces,application,domain,infrastructure,autoconfigure}`,是六库中唯一没有 `<域>` 根命名空间的库;其余五库均为 `com.apex.scaffold.<域>.*`。
- 34 个 Router / 55 个 Handler 位于业务库的 `interfaces` 包,与 F1 同源;C4/D07 已记录迁移方向但未落地。
- 影响:按包名编写的架构规则存在歧义源;模块名与包根不对应造成 IDE 定位与心智负担;`apex-system-service` 与 `apex-system` 的职责边界(装配 vs 实现)只能靠约定维持。
- 约束:Java 包名保持不变是迁移期兼容决定(见 AGENTS.md),本项作为演进方向管理,不建议与日常迭代混做。

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

- `:-`(232 字节 macOS 代码签名 designated 字符串,误生成文件,未入库未忽略);
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
| 独立部署 | 良好 | 七 service-jar 交付口径明确,optional 服务按需部署;端口冲突(F6)是摩擦点 |
| 服务间通信 | 良好 | 统一 WebClient + 熔断,无 Feign;内部端点网关封禁 |
| 契约管理 | 中 | api-contract 覆盖面窄、内部契约双端定义、事件消费关系与文档不一致(F3) |
| 去中心化治理/形态统一 | 中 | 接口层归属两套形态(F1/F2),持久化栈并存(F9) |
| 容错与弹性 | 良好 | 熔断/混沌注入/降级能力集中在 remote 能力层 |
| 可观测性 | 良好 | Prometheus/SkyWalking/logstash、管理端口分离、traceId 口径统一 |
| 演进能力 | 优秀 | boot-all 组合部署、C7 组件化/C8 事件通道方向已立项 |

总体结论:**模块划分合理、规范程度较高,微服务架构主干要求(数据自治、独立部署、薄网关、契约隔离、可插拔增强)均成立**;主要欠账集中在"形态统一"(F1/F2)与"契约治理"(F3),均为可分批收敛的结构性问题,不存在需要推翻重来的划分错误。

## 5. 优化调整方案(待确认后执行)

原则:只做任务需要的变更;每批独立提交、独立验证;不与日常功能迭代混做;文档与代码同批同步。

### R1 文档基线修正(P2/F4/F5,低风险)

- context-map 上下文清单与关系图补 integration(connector/device)上下文及 SYS↔INTEGRATION 关系;
- 根 POM 修正 api-contract 注释("Feign 接口"→ 按实际:跨服务内部 API 与共享 DTO)、basic profile 注释去掉 apex-interfaces 表述;
- 核实 workflow 事件实际消费方,按核实结果修订 context-map 事件关系描述(有消费则补图,无消费则标注"发布方就绪、消费方未接入")。
- 验证:`scripts/ci/check-doc-links.sh`、文档 diff 核对;不涉及 Java 代码。

### R2 仓库卫生清理(P3/F7,低风险)

- 删除根目录 `:-` 文件与空的 `apex-interfaces/`、`apex-application/` 目录;`backups/`、`_tmp/`、根 `target/` 属本地忽略物,确认无引用后一并清除。
- 验证:`git status` 干净、`mvnd -T4 -B -pl apex-tools-build -am test` 不受影响。

### R3 integration 接口层迁至运行模块(P1/F1,中风险)

- 将 `apex-integration` 内 `connector/interfaces`、`device/interfaces` 的 3 Router + 6 Handler 迁至 `apex-integration-service` 的 `integrationservice/interfaces`,包名对齐 auth/file/office/workflow 形态;库内仅保留 domain/application/infrastructure。
- 同步:迁移目录内引用的资源/i18n、SpringDoc 路径元数据核对、`IntegrationServiceBoundaryTest` 扫描声明、AGENTS.md 模块表与依赖表相应行、context-map(C4 说明范围扩展到 integration)。
- 迁移前核对库内 handler 对库内 application 服务的 import 全部保留合法(业务库依赖不变)。
- 验证:`mvnd -T4 -B -pl apex-integration-service -am test` + `spotless:check`;本批涉及包移动,补跑受影响模块 `verify`。

### R4 system 接口层迁至运行模块(P1/F2,高风险,单独立项)

- 34 Router / 55 Handler + `interfaces` 包内安全配置、资源与配套测试从 `apex-system` 迁至 `apex-system-service`(C4/D07 落地);包根命名空间是否同步改为 `com.apex.scaffold.system.*`/`systemservice.*` 作为独立决策项(涉及全仓架构测试规则与扫描声明,建议第二步单独做)。
- 影响面大:SystemServiceApplication 扫描声明、i18n 资源路径、`LayeringArchTest`、交付脚本回归。建议在 R1/R3/R5 完成后单独排期,先出专项迁移清单再实施。

### R5 内部契约治理(P2/F3,中风险)

- 建立 `/internal/v1/**` 内部契约清单文档(提供方、消费方、路径、请求/响应结构、DTO 归属),先文档化防漂移基线;
- 评估将 tenant status 请求/响应 DTO 提升入 `apex-api-contract`,system/auth 两端改为共用(contract 模块零业务实体约束不受影响,均为跨服务 DTO);office 的消费端 ACL DTO 保持域内定义,仅登记端点契约;
- 按核实结果处理 workflow 事件消费关系(与 R1 联动)。
- 验证:受影响模块 `-am test`;契约 DTO 移动需两端编译与契约测试通过。

### R6 端口与网关兜底治理(P2/F6,中风险)

- 评估消除 workflow 8083/system 管理端口 8083 冲突:候选方案为 workflow 业务端口改 8093(与 Compose hostPort 口径对齐,改动 manifest/AGENTS/README/Compose/部署脚本)或 system 管理端口迁出;以影响面评估结果定案;
- 统一网关路由兜底默认值口径(建议:全部路由默认 `lb://`,无注册中心场景由部署环境显式注入 `ROUTE_*_URI`,文档明确);job/masking/office-WS 路由随口径统一。
- 验证:manifest 同步测试、网关模块测试、Compose 配置核对。

### R7 记录为观察项、本轮不执行

- `PageResponse` 收敛(F8):涉及接口兼容,需独立契约评估;
- 持久化栈收敛(F9)、office WS 水平扩展(F12)、apex-example 更名(F10)、apex-infrastructure 退役时间表(F11):均需独立方案与兼容性评估,本轮仅记录。

### 建议执行顺序与门禁

R1 → R2 → R3 → R5 → R6 →(R4 专项立项)。每批:受影响模块 `-am test` + `spotless:check` + `git diff --check`;R3 完成后补跑受影响模块 `verify`;全部批次结束后以 `mvnd -T4 -B clean verify` 全量门禁收口(以反应堆 BUILD SUCCESS 与测试计数为准)。

## 6. 刻意设计项(评审确认保留,不作为问题)

- system 迁移期保留 `interfaces/application` 旧包名(AGENTS.md 既有约束,R4 落地前不动);
- job-service 只代理 system 九个任务接口、不迁移数据所有权;
- masking 策略启动配置、无 CRUD;
- integration 的 connector/device 双子域包结构隔离(同库不同包,当前规模不拆库);
- 五基础设施 JAR 中 cache 对 integration 仅 test scope 等按需依赖口径。
