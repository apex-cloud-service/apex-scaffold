# 变更日志

本项目遵循 [语义化版本规范](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- **P6Spy SQL 日志（dev 专用）**：新增 spy.properties（Slf4JLogger 输出、H2/MySQL 双驱动），dev 数据源切换为 `jdbc:p6spy:h2:...`，控制台/日志文件输出带参数值的格式化 SQL（含耗时/连接号）；生产保持纯 Druid stat 监控（p6spy: false），测试环境用 logging.level 控制，`P6SpyIntegrationContractTest` 契约测试防止配置回归
- **接口文档增强**：补齐 P0 系统管理模块（菜单/字典/参数/定时任务/登录日志/验证码）约 28 个操作的 @RouterOperation 声明；SpringDoc 分组重构（移除已下线 face-analysis/interest 死分组，新增 admin-system/admin-user）；Knife4j 增强 UI 在开发环境启用（`/doc.html`）；`Knife4jIntegrationContractTest` + `OpenApiEndpointsIntegrationTest` 端到端保证不回归
- **配置全面补充（公共默认，全部环境变量可覆盖）**：Resilience4j 四件套（熔断 default/strict + 限流 + 重试 + 超时）、Sentinel 控制台通信、Micrometer 链路追踪（采样率 0.1 + Zipkin）、Nacos 注册/配置中心、RocketMQ 生产消费参数、Actuator 监控端点与健康分组、Hikari 连接池、异常钉钉告警、远程调用（remote.api 超时/重试/连接池）、文件上传限制、性能（Netty 超时 + 阻塞 IO 线程池）
- **统一构建环境**：新增 `.sdkmanrc` 固定 JDK 17.0.12-oracle；build/release/codegen/license 脚本统一 sdkman + mvnd（Maven Daemon），文档命令同步替换

### Changed
- 三档配置（basic/standard/enterprise）修正无效键：`spring.data.redis.enabled` → autoconfigure.exclude、`rocketmq.producer.enabled` → `apex.mq.consumer.enabled`、`apex-sentinel.enabled` → `apex.sentinel.*`
- 全部配置文件引入统一符号注释规范（✅ 已启用 / ❌ 已禁用 / ⚠️ 安全提示 / 💡 设计说明 / 🔒 敏感配置 / （差异）公共层差异）

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
