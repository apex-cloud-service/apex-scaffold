# 变更日志

本项目遵循 [语义化版本规范](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Refactor
- **全库 Javadoc 补全与规范化**：为无注释的公开 API 补充符合规范的中文 Javadoc（5 个主模块的方法/字段/类）；清除机器生成的占位模板（`执行 {@code X} 操作` / `参数说明` / `返回值说明` / `@return 操作结果` 等），全库移除 1100+ 测试占位块与主代码冗余注释，统一重写为语义化描述
- **切面请求解析统一为 `AspectRequestUtil`**：新增 `RequestMeta` record 与 `resolveRequestMeta(Object[])`，将函数式端点（`ServerRequest`）与注解式端点（`ServerWebExchange`）的请求定位/归一化逻辑收敛为函数式 API；函数式端点直接消费 `ServerRequest` 自身的 `path()/method()/headers()/remoteAddress()`，不再调用 `ServerRequest.exchange()`（该调用在部分 WebFlux 构建与测试场景返回 null），修复 `AntiReplayAspect` / `IdempotentApiAspect` 在函数式端点上的请求解析回归
- **OperationLogAspect UA 解析声明式规则表**：设备类型/浏览器/操作系统识别由手写 if-else 链重构为 `UaRule`（`Predicate` + 目标值）有序规则表，按序首匹配，消除重复判定与维护成本

### Fixed
- **切面请求解析回归**：`resolveServerHttpRequest` 对 `ServerRequest.exchange()` 返回 null 的场景优雅降级；`fromHttpRequest` 对 URI 为 null 的 mock 场景（路径取空串）优雅降级，避免请求头/IP 解析中断

## [1.2.0] - 2026-08-29（商业化售卖就绪 P0/P1 修复）

### Fixed
- **P0-2 商业功能 FeatureGate 门控**：业务模块（RBAC/缓存/MQ/tracing/Sentinel）的关键 Bean 与切面增加 `@ConditionalOnProperty(apex.features.*)` / `@ConditionalOnLicenseFeature` 条件装配，三档版本运行期真实差异化；fork 仅修改 yml 开关不再能静默启用商业组件
- **P0-3 三档版本实质化**：① Maven Profile `basic` 真实裁剪 RocketMQ/Zipkin/Sentinel 高级依赖（三档 jar 体积与依赖图差异显著）；② `SecurityConfig` 按 `apex.features.rbac` 条件装配 RBAC 路由规则；③ `COMMERCIAL_VERSIONS.md` 矩阵逐项对齐实现（Flyway 全档提供、CSRF 全档禁用、Excel=Standard+、Sentinel=Enterprise、K8s=Enterprise、分布式锁=Enterprise 等）
- **P0-4 仓库卫生**：`backups/apex-mysql-full-20260828.sql`、`lr`（mvnd 构建日志）从工作树删除；`.gitignore` 已含 `backups/`、`lr`、`config/license/private.key`；`scripts/release/release.sh` 在交付物打包前主动清除 `private.key`，杜绝卖方私钥随交付物外泄
- **P0-5 微信支付残留清除**：删除 `application-prod.yml` 中"微信支付模块已于 1.2.0 移除"的注释占位行；微信支付相关源码、配置、测试此前已整体移除（`WeChatPayService`/`WeChatPayProperties`/`WeChatPayPayloadCodec`），工作树无残留
- **P0-6 版本收口**：父 POM 与 9 个子模块 `1.1.4 → 1.2.0`；`HealthHandler`/`MonitorMetricsCollector`/`SpringDocConfig` 兜底版本号同步；`k8s/deployment.yaml` + `k8s/README.md` + `docs/DEPLOYMENT.md` + `docs/sales/{API_SPECIFICATION,SECURITY_WHITEPAPER,PERFORMANCE_REPORT,DELIVERY_CHECKLIST}.md` 版本字符串同步；`COMMERCIAL_IMPROVEMENT_TASKS.md` 状态校准（灰度/备份脚本由"待开始"修正为"已完成"等）
- **P1-3 多租户补齐**：新增 `TenantLineInnerInterceptor` + `tenant_id` 字段注入 + `apex.tenant.enabled` 启用开关，README 的"SaaS 平台"场景宣传名副其实
- **P1-6 i18n 补齐与品牌清理**：`zh_TW`/`th_TH` 补齐与 `zh_CN` 一致的全部键；清理 i18n 文件头及 POM 注释中残留的 `Apex.Mar` 旧品牌字样，统一为 `Apex Scaffold`
- **P1-9 文档收敛**：`COMMERCIAL_IMPROVEMENT_TASKS`/`COMMERCIAL_VERSIONS`/`COMMERCIAL_GAP_ANALYSIS` 三份状态统一校准为当前代码事实；过期评审文档归档
- **P1-10 坏注释清理**：清理 `MqConsumerService`/`AbstractStorageService` 的生成式坏注释（`/** destroy */`、`加载ad` 等）

### Changed
- **JVM 启动参数统一生产级调优**：全部启动载体内置同一套 JVM 参数（可用 `JAVA_OPTS` 整体覆盖）—— 堆内存 `-Xms512m/-Xmx1024m`、G1 垃圾回收器 + `-XX:MaxGCPauseMillis=200`、元空间 `256m/512m`、OOM 自动堆转储 + `-XX:+ExitOnOutOfMemoryError` 退出自愈、滚动 GC 日志（`-Xlog:gc*` 5 份 × 20m）、`-Dfile.encoding=UTF-8`、非阻塞熵源；覆盖 `scripts/deploy/app.sh`、systemd 模板（`apex-scaffold.service`）、Dockerfile/Compose、k8s deployment/canary；新增 `docs/DEPLOYMENT.md §4.5 JVM 参数调优` 说明与各载体日志落盘对照表
- **依赖组件 JVM 调优**：`docker-compose.infra.yml` 的 Nacos（`JAVA_OPT`，JDK8 语法，镜像强制 CMS 不传 G1）、RocketMQ Namesrv/Broker（`HEAP_OPTS` + `JAVA_OPT_EXT`，`command` 预建 GC 日志目录）、Sentinel Dashboard（覆盖 `command` 注入）、Zipkin（`JAVA_OPTS`，JDK21 `-Xlog`）均加同一套优化参数；`docker-compose.yml` rocketmq profile 与应用栈监控 `config/monitoring/docker-compose.yml` 的 Zipkin 同步；DEPLOYMENT.md §6.5 补充说明

## [1.2.0-alpha] - 2026-08-28（商业化 P1 能力补齐）

### Added
- **G05 一键初始化脚本**：`scripts/init/new-project.sh` —— 复制脚手架 → 全局替换项目名/包名/端口/库名 → 包路径迁移 → 旧标识零残留校验 → 启动指引；实测新项目零残留
- **G06 Sentinel 规则 Nacos 动态下发**：新增 `SentinelNacosDataSourceConfig`（`apex.sentinel.nacos.enabled=true` 时启用），限流/熔断规则从 Nacos 热更新（约 30s 生效），静态规则兜底；`config/sentinel/` 规则 JSON 模板 + 发布说明；`SentinelNacosDataSourceConfigTest` 门禁测试（默认关闭零影响）
- **G07 前端代码生成**：generator 新增 4 个 Vue3 模板（frontendApi/frontendList/frontendForm/frontendRouteFragment），CLI/脚本支持 `-f <frontendDir>` 同时生成管理页面（TS 类型映射、列表/表单/路由注册说明）；`CodeGeneratorTest` 前端用例
- **G08 采购材料包**：`docs/sales/` —— 安全白皮书 / API 规范 / 性能测试报告（含 2026-08-28 本机沙箱实测基线：健康检查 c100=292 req/s 零错误，并实证 IP 限流 429 与 Sentinel 系统保护 503 行为）/ 交付清单；`scripts/bench/bench.sh` wrk 压测脚本
- **G09 灰度发布资产**：`k8s/canary-deployment.yaml`（金丝雀 Deployment + Service + Nginx Ingress 按权重/请求头导流）+ `k8s/README.md` 演练步骤；DEPLOYMENT.md §12.4；deployment.yaml 版本标签 v1.1.4

### Changed
- 父 POM dependencyManagement 新增 `sentinel-datasource-nacos`（1.8.9）；apex-interfaces 引入
- `application.yml` 新增 `apex.sentinel.nacos.*` 配置块（默认关闭）
- CONFIGURATION.md Sentinel 章节更新为实际实现的动态规则配置
- docs/CODEGEN.md 新增前端生成章节

## [1.1.4] - 2026-08-28

### Added
- **示例模块能力地图（G02）**：新增 `ExampleCapabilityHandler`/`ExampleCapabilityService`，演示多级缓存读穿（含击穿合并）、写接口幂等（`@IdempotentApi`）、操作审计（`@OperationLog`）、SSE 推送、数据脱敏、Excel 导出（`ExcelExportService`）7 个核心能力端点 + 7 例集成测试；`docs/EXAMPLE_GUIDE.md` 逐步跑通指南；README 新增"方式五：配套前端"入口
- **版本号统一升级 1.1.3 → 1.1.4**：父 POM 与 8 个子模块 + 运行版本号（HealthHandler/SpringDocConfig 默认值）
- **备份/恢复脚本**：`scripts/backup/db-backup.sh`（mysqldump 一致性快照 + gzip + 轮转保留 N 份 + 可选 MinIO/S3 上传；支持宿主机直连与 Docker 容器两种场景）、`db-restore.sh`（交互选择/指定文件恢复，恢复前自动备份当前库）、`README.md`；本机实测备份→恢复全链路通过
- **配套前端工程（独立仓库 apex-admin-web）**：Vue3 + Vite + TS + Pinia + Element Plus 管理后台，前后端分离对接（登录/验证码、用户/角色/菜单/部门/字典/参数/任务、登录/审计日志、在线用户、个人中心、动态路由 + 按钮权限）
- **三种部署方式统一**：`java -jar`（守护脚本 `scripts/deploy/app.sh` + systemd 模板）、Docker 单容器（Dockerfile 多阶段 + `ARG BUILD_PROFILE`）、Docker Compose（`docker-compose.yml` 应用栈）；完整部署文档 `docs/DEPLOYMENT.md`（16 章，含环境变量全表、systemd、多实例、升级回滚、备份、安全清单、FAQ）
- **本地基础设施一键编排**：`docker-compose.infra.yml` 一键部署 MySQL/Redis/Nacos/RocketMQ/Sentinel/Zipkin/Prometheus/Grafana/Blackbox 全部 9 组件；镜像拉取脚本 `scripts/deploy/pull-infra-images.sh`（带重试，容错镜像加速器故障）；`.env.example` / `.dockerignore`
- **配置增强**：Nacos 开关全部环境变量化（`NACOS_CONFIG_ENABLED` 等，无 Nacos 可完全关闭）；远程 IP 统一为 `127.0.0.1` 默认值（Nacos/Sentinel/RocketMQ/Zipkin/Redis）；Prometheus 抓取改 `host.docker.internal:8081`

### Changed
- 对齐审计：文档/脚本/配置端口、端点（`/api/v1/health`、`/actuator/health/readiness`）、环境变量、账号全量统一；k8s 编排重构为环境变量注入 + readiness/liveness 探针；TROUBLESHOOTING/CONFIGURATION/QUICK_START/README 同步

### Fixed
- **basic 档启动失败**：基础版排除 Redis 自动配置导致防重放/限流组件缺 `ReactiveStringRedisTemplate`，移除排除（基础版仍依赖 Redis 基础设施）
- **dev 档启动失败**：排除 Redisson 导致 `HotSpotRateLimitAspect` 缺 `RedissonClient`，移除排除
- **prod Druid 启动失败**：`keep-alive-between-time-millis` 与 `time-between-eviction-runs-millis` 相等（Druid 校验必崩），扫描间隔调为 30s
- **RocketMQ broker 启动即崩**：store 卷挂载点归 root 导致非 root 无写权限，去掉卷挂载 + 调小 HEAP_OPTS
- **spy.properties 残留 H2 driver**（运行时无该依赖，P6Spy 初始化崩溃），清理
- **compose 默认栈 Redis 密码联动**（prod 强制 `REDIS_PASSWORD`）、JWT ≥32 字节校验、文件上传与百度云审核联动校验
- **IdempotentApiAspect 日志 NPE**：方法参数无请求对象时 `getSignature()` 可能为 null 导致日志崩溃，新增 `signatureText` 防御性取值（回归测试覆盖）
- **Excel 导出 NoClassDefFoundError**：POI 4.1.2 运行时要求 commons-io ≥2.13，被旧版本（2.11.0）覆盖导致 EasyExcel 抛 `Can not close IO.`——父 POM dependencyManagement 固定 commons-io 2.16.1（主应用 /admin-api/v1/export/users 与 example 导出均受影响，实测修复）
- **example 测试上下文修复**：MailService 依赖 `JavaMailSender` 需 `spring.mail.host` 装配（补占位配置）；测试环境关闭 Nacos 服务发现；SSE 测试改首事件消费（避免等待完整流超时）
- **example 测试断言修正**：USER_NOT_FOUND 已按 REST 语义映射为 HTTP 404（原断言 5xx 过期），`shouldFailWhenUserNotFound`/`shouldDeleteUserSuccessfully` 更新为 404 + 4001 校验

## [1.1.3] - 2026-08-27

### Added
- **P6Spy SQL 日志（dev 专用）**：新增 spy.properties（Slf4JLogger 输出、H2/MySQL 双驱动），dev 数据源切换为 `jdbc:p6spy:h2:...`，控制台/日志文件输出带参数值的格式化 SQL（含耗时/连接号）；生产保持纯 Druid stat 监控（p6spy: false），测试环境用 logging.level 控制，`P6SpyIntegrationContractTest` 契约测试防止配置回归
- **接口文档增强**：补齐 P0 系统管理模块（菜单/字典/参数/定时任务/登录日志/验证码）约 28 个操作的 @RouterOperation 声明；SpringDoc 分组重构（移除已下线 face-analysis/interest 死分组，新增 admin-system/admin-user）；Knife4j 增强 UI 在开发环境启用（`/doc.html`）；`Knife4jIntegrationContractTest` + `OpenApiEndpointsIntegrationTest` 端到端保证不回归
- **配置全面补充（公共默认，全部环境变量可覆盖）**：Resilience4j 四件套（熔断 default/strict + 限流 + 重试 + 超时）、Sentinel 控制台通信、Micrometer 链路追踪（采样率 0.1 + Zipkin）、Nacos 注册/配置中心、RocketMQ 生产消费参数、Actuator 监控端点与健康分组、Hikari 连接池、异常钉钉告警、远程调用（remote.api 超时/重试/连接池）、文件上传限制、性能（Netty 超时 + 阻塞 IO 线程池）
- **统一构建环境**：新增 `.sdkmanrc` 固定 JDK 17.0.12-oracle；build/release/codegen/license 脚本统一 sdkman + mvnd（Maven Daemon），文档命令同步替换

### Changed
- 版本号统一升至 1.1.3：父 POM 与全部子模块（common/domain/infrastructure/application/interfaces/generator/example）同步
- 三档配置（basic/standard/enterprise）修正无效键：`spring.data.redis.enabled` → autoconfigure.exclude、`rocketmq.producer.enabled` → `apex.mq.consumer.enabled`、`apex-sentinel.enabled` → `apex.sentinel.*`
- 全部配置文件引入统一符号注释规范（✅ 已启用 / ❌ 已禁用 / ⚠️ 安全提示 / 💡 设计说明 / 🔒 敏感配置 / （差异）公共层差异）
- 面向用户文档构建命令统一 `mvn` → `mvnd`（Dockerfile 内 Maven 镜像命令保留）

### Fixed
- **prod `remote.api` 配置截断**：文件末尾 `remote:` 后无实际配置（此前一直未生效），已补全默认超时/重试/连接池
- **熔断异常类名错误**：`com.apex.infrastructure.remote.exception.*` → `com.apex.scaffold.infrastructure.remote.exception.*`（公共层新增配置后暴露的潜在启动 bug）
- **bootstrap 应用名不一致**：`apex-service` → `apex-scaffold`（与 application.yml 对齐，否则 Nacos 配置拉取/服务注册错乱）
- **配置重复顶层键**：basic 档 `apex:` 块重复导致 features 被覆盖丢失，已合并

## [1.1.2] - 2026-08-27

### Added
- 代码生成器（模板开发功能）：新增 apex-generator 独立模块，FreeMarker 模板引擎按表结构一键生成 DDD 五层代码（entity/repository/PO/Mapper/Converter/RepositoryImpl/Service/DTO/Handler + 路由片段 + 迁移片段 + 测试骨架），CLI 入口 scripts/codegen/generate.sh
- 系统管理种子数据：V2 迁移预置 24 个 system:* 权限码与系统管理菜单树（Flyway 管理）
- 可观测性配套整合：监控资产统一收敛至 `config/monitoring/` —— Prometheus 抓取配置（应用 8081 + Sentinel 8720）、10 条告警规则（服务宕机/5xx 率/P95 延迟/JVM 堆/连接池/Redis/Sentinel）、Grafana 大盘（`apex_api_*` 指标口径，14 面板自动置备）、一键 docker-compose 栈（Prometheus + Grafana + Zipkin，数据源与大盘免手工导入）
- 示例模块（apex-example）增强：分页查询（手写 COUNT/LIMIT，统一 PageResponse）、参数校验、标准错误码（4001/4002）、手机号脱敏、逻辑删除（deleteById）、集成测试 10 例全绿（覆盖分页/校验/错误码/脱敏/删除链路）

### Changed
- 版本号统一升至 1.1.2：父 POM 与全部子模块（含新增 apex-generator）同步
- 旧 MVC 指标口径大盘（`http_server_requests_*`）由 `apex-dashboard.json` 替代，README/架构文档/快速开始引用路径同步更新
- README 示例模块章节重写：新增能力演示对照表，移除过时的 ELO/兴趣图谱提示（业务代码已下线）

### Fixed
- **InputValidationFilter SQL 注入误报**：`\bDELETE\b` 等关键字正则对 "delete@example.com" 之类英文单词误判为注入，改为要求关键字处于 SQL 语法位置（前后为空白/引号/括号/逗号/分号）
- **示例模块集成测试链路**：dynamic-datasource 配置、Flyway 独立迁移目录、Redis Mock、CORS 配置、JWT 公开路径、`@Transactional` 响应式陷阱（惰性 Mono 下事务提前提交致数据不落库）、MyBatis-Plus 3.5.9+ 分页插件拆分（改手写分页）、逻辑删除 API 用法
- **spring-boot repackage classifier**：apex-interfaces 可执行包加 `executable` classifier，避免 BOOT-INF fat jar 覆盖普通 jar 导致下游模块（apex-example）类加载失败
- **文档断链**：RELEASE_v1.0.0.md 链接路径修正（docs/X.md → X.md）、内部分析文档对已删除文档的引用清理（45 个内部链接全部有效）

## [1.1.1] - 2026-08-27

### Added
- 系统管理三件套（P0-1）：菜单管理（sys_menu 树 + 按钮权限）、字典管理（sys_dict_type/sys_dict_data）、参数配置（sys_config）
- 定时任务管理（P0-2）：sys_job/sys_job_log，自研 Cron 解析器 + Reactor 动态调度，支持动态注册/启停/手动触发/执行日志
- 管理后台升级（P0-3）：静态审核控制台重写为系统管理控制台（用户/菜单/字典/参数/任务/登录日志，含图形验证码登录）
- 登录安全（P0-4）：图形验证码（Redis 一次性校验，防重放）+ 登录日志（sys_login_log，审计回溯）

### Changed
- 数据库迁移（Flyway）：V1__init_schema.sql 基线由 27 张扩展至 34 张通用表，同步 schema-it.sql
- 登录接口新增可选 captchaId/captchaCode 字段，携带时强制校验图形验证码
- WebFlux 化：删除遗留 MVC 注解式死代码（UserControllerV1/V2），管理后台短入口改为函数式重定向路由，
  GlobalExceptionHandler 移除 Spring MVC 专用异常处理器（统一由 WebExceptionHandler 兜底函数式端点）

### Removed
- 移除业务迁移契约测试残留（InterestCatalog/PhotoReport MigrationContractTest）

## [1.1.0] - 2026-08-26

### Added
- v2.0 通用化重构：完成业务代码剥离，脚手架仅保留通用能力
- 数据库迁移重建：87 个业务迁移脚本替换为干净的通用基线（V1 27 张通用表）
- 全库品牌统一：配置前缀/指标名/缓存前缀统一为 `apex.*` 命名空间
- 管理后台品牌改造：控制台更名为 Apex Scaffold，移除业务审核视图
- 文档体系重构：模块 README 重写，移除内部工作文档
- 移除全部业务耦合代码（兴趣图谱/照片对战/人脸分析/共鸣/雷达等约 500+ 文件）
- 移除业务迁移脚本、业务种子数据与内部拷贝脚本
- 移除原项目名称残留（全库零命中验证）
- 商业化机制落地（阶段 D）：
  - **License 强制启用**：默认开启校验，随包内置基础版 License 模板与官方公钥；容错降级策略（无有效 License 时基础功能放行、商业功能拒绝），fork 无法绕过
  - **License 签名缺陷修复**：签名数据统一为剔除 signature 字段的规范化 JSON，生成/验证一致
  - **Maven Profile 实质化**：basic/standard/enterprise 三档与 Spring Profile 联动，构建标识（apex-build.properties）区分产物，三档功能矩阵启动日志
  - **三档配置模板**：application-basic.yml / application-standard.yml / application-enterprise.yml
  - **双轨许可证**：LICENSE 限定基础版 MIT + LICENSE-COMMERCIAL.md 商业授权模板
  - **License 发放工具**：scripts/license/generate-license.sh（一键发放），发布脚本 scripts/release/release.sh
  - **License 管理手册**：docs/LICENSE_MANAGEMENT.md 重写（购买→发 License→激活全流程）

### Fixed
- License 签名验证数据不一致缺陷（原实现必然验签失败）
- LocalDateTime 序列化缺少 JavaTimeModule 支持
- LicenseInfo 派生 getter 被 Jackson 序列化导致反序列化失败

## [1.0.0] - 2026-08-25

### Added
- DDD五层架构设计
- Spring Boot 3.5 + WebFlux响应式支持
- JWT认证 + RBAC权限控制
- 多级缓存(Redisson + Caffeine)
- Sentinel限流熔断 + Resilience4j容错
- RocketMQ消息队列封装
- 统一响应格式 + 全局异常处理
- SpringDoc OpenAPI + Knife4j API文档
- Prometheus + Grafana监控告警
- Docker Compose一键部署
- 国际化(i18n)支持
- 文件存储抽象(Local/MinIO/S3)
- 链路追踪(Micrometer + Zipkin)
- 操作日志AOP切面

---

**注意**: 
- `[Unreleased]` 部分包含尚未发布的变更
- 日期格式为 `YYYY-MM-DD`
- 分类包括: `Added`(新增)、`Changed`(变更)、`Deprecated`(弃用)、`Removed`(移除)、`Fixed`(修复)、`Security`(安全)

[Unreleased]: https://github.com/your-org/apex-scaffold/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/your-org/apex-scaffold/releases/tag/v1.0.0
