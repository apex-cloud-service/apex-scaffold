# Apex Scaffold 架构文档索引

> 文档版本: v2.7\
> 最后更新: 2026-09-11\
> 适用版本: apex-scaffold v1.3.1+\
> 技术栈版本: Spring Boot 3.5.15 | Java 17 | Reactor 3.6.13 | Vue 3.5.13

**🔍 快速搜索**: [打开文档搜索工具](../doc-search.html) - 支持关键词搜索、分类过滤、实时预览

本文档提供Apex Scaffold项目所有架构图表和技术文档的快速索引。

2026-10-12 system 第二批物理迁移：当前业务四层归入 `apex-system`，由 `apex-system-service` 启动；原 interfaces/application 模块退休，共享 domain/infrastructure 仅保留通用契约和设施。当前边界见 [架构设计说明](architecture-overview.md) 与根 AGENTS；历史 SVG 和原单体图不代表当前 Maven 模块拓扑。

***

## ✅ SVG 图表质量状态

**本轮范围**: 统一授权相关文档与索引同步。未执行 SVG 全量 XML 验证、跨查看器验证或 Mermaid 重新渲染。下列历史处理记录不作为本轮验证结果；图表编号和登记数量不等于实际文件总数。

### 历史兼容性记录（本轮未复验）

| 验证项          | 状态   | 说明                                            |
| ------------ | ---- | --------------------------------------------- |
| **CSS 变量清理** | ✅ 完成 | 全部图表已移除 `:root` 变量和 `var()` 引用                |
| **暗色模式清理**   | ✅ 完成 | 全部图表已移除 `@media (prefers-color-scheme: dark)` |
| **静态颜色**     | ✅ 完成 | 全部图表使用静态颜色值（深色系企业级配色）               |
| **文本标签清理**   | ✅ 完成 | 全部图表已移除 `[Globe]`、`[Users]` 等文本标签                |
| **配色优化**     | ✅ 完成 | 升级为深色系企业级配色（#4f46e5, #0891b2, #059669, #d97706 等） |
| **XML 格式**   | 本批通过 | 45-49 号图表通过 `xmllint` 验证；历史图表存在未转义字符、重复属性等 XML 问题，待单独修复 |
| **跨查看器兼容**   | ✅ 通过 | 支持 Chrome、Safari、Firefox、VSCode、Obsidian 等    |

### 重新生成记录 (2026-09-10) 🎨

| 图表范围         | 生成日期       | 处理内容                                           |
| ------------ | ---------- | ---------------------------------------------- |
| **01-07 号**  | 2026-09-10 | 核心架构图+业务流程图,基于实际代码统计(32 Handler/34 Service) |
| **08-12 号**  | 2026-09-10 | 基础设施图,展示40+表ER关系、多级缓存、限流熔断、数据权限、安全防护         |
| **13-18 号**  | 2026-09-10 | 部署与微服务图,Podman Compose/K8s/CI/CD/Nacos/Zipkin  |
| **19-23 号**  | 2026-09-10 | 前端技术栈图,Vue 3/Axios/WebFlux/i18n/OpenAPI        |
| **24-29 号**  | 2026-09-10 | 监控+高级架构,66个Grafana面板、Loki日志、容灾、MQ、Meilisearch |
| **全部29张图表** | 2026-09-10 | 统一深色企业级配色,所有版本号与pom.xml一致,移除CSS变量           |

### 2026-09-05 视觉优化升级 🎨

**优化目标**: 提升图表专业度和视觉美感，使用更高端的配色和设计

| 优化项目 | 完成状态 | 详细说明 |
| ------- | ------- | ------- |
| **深色系配色升级** | ✅ 100% (29/29) | 所有图表升级为深色系企业级配色方案 |
| **CSS 变量清除** | ✅ 100% (29/29) | 所有 `var(--xxx)` 替换为静态颜色值 |
| **文本标签清除** | ✅ 100% (6/6) | 所有 `[Globe]`、`[Users]` 等标签已移除 |
| **专业图标系统** | ✅ 完成 | 14_network 新增 7 个专业 SVG 图标 |

**配色方案对比**:
- 主品牌色: `#6366f1` → `#4f46e5` (更深沉的靛蓝)
- 应用层: `#22d3ee` → `#0891b2` (专业青色)
- 领域层: `#10b981` → `#059669` (深绿)
- 基础设施: `#f59e0b` → `#d97706` (琥珀)
- 文字色: `#1a1d29` → `#0f172a` (更深沉)
- 次要文字: `#64748b` → `#475569` (适中灰)

**视觉提升**:
- ✅ 更专业的深色系配色，符合企业级产品标准
- ✅ 更好的层次感和视觉引导
- ✅ 100% 兼容性（移除所有 CSS 变量）
- ✅ 更清晰的文本标签（移除临时性文本标记）
- ✅ 专业的 SVG 图标系统（云、用户、防火墙、负载均衡器等）

**历史记录边界**: 上述视觉处理记录不证明当前全部 SVG 可解析或在所有查看器中正常显示；XML 问题和运行时行为仍需分别验证。

***

## 📊 架构图表 (SVG)

### 核心架构图 (3张)

| 序号 | 名称          | 文件                                                                        | 说明                          |
| -- | ----------- | ------------------------------------------------------------------------- | --------------------------- |
| 01 | DDD 四层逻辑架构图 | [01\_logical\_architecture.svg](../diagrams/01_logical_architecture.svg)     | 展示接口层、应用层、领域层、基础设施层的DDD分层设计 |
| 02 | 网络物理部署架构图   | [02\_physical\_architecture.svg](../diagrams/02_physical_architecture.svg)   | 展示生产环境的物理部署拓扑和组件分布          |
| 03 | 全栈技术选型全景图   | [03\_technical\_architecture.svg](../diagrams/03_technical_architecture.svg) | 展示前后端技术栈和中间件选型全貌            |

### 业务流程图 (4张)

| 序号 | 名称           | 文件                                                                         | 说明                      |
| -- | ------------ | -------------------------------------------------------------------------- | ----------------------- |
| 04 | JWT 登录认证流程图  | [04\_login\_authentication\_flow.svg](../diagrams/04_login_authentication_flow.svg)               | 展示用户登录、Token生成、刷新、登出全流程 |
| 05 | RBAC 权限校验流程图 | [05\_permission\_check\_flow.svg](../diagrams/05_permission_check_flow.svg)   | 展示角色权限校验和数据权限过滤流程       |
| 06 | 三级审批工作流图     | [06\_workflow\_approval\_flow.svg](../diagrams/06_workflow_approval_flow.svg) | 展示BPMN工作流引擎的流程发起、审批、流转  |
| 07 | 文件分片上传流程图    | [07\_file\_upload\_flow.svg](../diagrams/07_file_upload_flow.svg)             | 展示大文件分片上传、断点续传、秒传流程     |

### 基础设施图 (5张)

| 序号 | 名称            | 文件                                                                                                     | 说明                              |
| -- | ------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------- |
| 08 | 数据库ER图        | [08\_database\_er\_diagram.svg](../diagrams/08_database_er_diagram.svg)                                   | 展示核心业务表结构和关系                    |
| 09 | 多级缓存架构图       | [09\_multi\_level\_cache\_architecture.svg](../diagrams/09_multi_level_cache_architecture.svg)            | 展示Caffeine L1 + Redis L2 两级缓存架构 |
| 10 | Sentinel流控熔断图 | [10\_rate\_limit\_circuit\_breaker.svg](../diagrams/10_rate_limit_circuit_breaker.svg)                   | 展示Sentinel流控规则和熔断降级策略           |
| 11 | 数据权限过滤图       | [11\_data\_permission\_filter.svg](../diagrams/11_data_permission_filter.svg)                            | 展示数据权限过滤和@DataScope注解实现       |
| 12 | 安全防护体系图       | [12\_security\_protection\_system.svg](../diagrams/12_security_protection_system.svg)                     | 展示认证、授权、加密、审计等安全机制              |



### 部署与微服务治理图 (6张) - 2026-09-10 ⭐

| 序号 | 名称                      | 文件                                                                                                           | 说明                                                                     |
| -- | ----------------------- | ------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------- |
| 13 | **Podman Compose本地开发环境** | [13\_docker\_compose\_local\_dev.svg](../diagrams/13_docker_compose_local_dev.svg)                             | 展示一键启动完整技术栈的podman-compose配置，包含核心服务和企业版服务，支持健康检查和数据持久化                |
| 14 | **Kubernetes生产部署架构**    | [14\_kubernetes\_production\_deployment.svg](../diagrams/14_kubernetes_production_deployment.svg)               | 展示Deployment、HPA自动伸缩、Service/Ingress、ConfigMap/Secret等K8s核心资源及生产最佳实践 |
| 15 | **CI/CD流水线架构**          | [15\_cicd\_pipeline.svg](../diagrams/15_cicd_pipeline.svg)                                                      | 展示GitHub Actions构建、测试、安全扫描、Podman镜像构建、多环境部署完整流水线                    |
| 16 | **Nacos服务注册与发现**        | [16\_nacos\_service\_discovery.svg](../diagrams/16_nacos_service_discovery.svg)                                 | 展示服务注册、配置中心、健康检查、元数据管理及Nacos 3节点集群部署                                 |
| 17 | **Zipkin分布式链路追踪**       | [17\_zipkin\_distributed\_tracing.svg](../diagrams/17_zipkin_distributed_tracing.svg)                           | 展示Micrometer Tracing、10%采样率、TraceId/SpanId结构及典型调用链路示例                |
| 18 | **Spring Cloud Gateway** | [18\_spring\_cloud\_gateway.svg](../diagrams/18_spring_cloud_gateway.svg)                                       | 展示动态路由、全局过滤器链、限流熔断、跨域CORS、安全响应头等API网关核心功能                          |

### 前端技术栈与响应式编程图 (5张) - 2026-09-10 ⭐

| 序号 | 名称                   | 文件                                                                                                     | 说明                                                             |
| -- | -------------------- | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------- |
| 19 | **前端Vue3组件架构**       | [19\_frontend\_vue3\_architecture.svg](../diagrams/19_frontend_vue3_architecture.svg)                     | 展示Vue 3 Composition API、Pinia状态管理、Element Plus、目录结构及核心特性    |
| 20 | **Axios请求拦截与封装**     | [20\_axios\_request\_interceptor.svg](../diagrams/20_axios_request_interceptor.svg)                       | 展示请求/响应拦截器、JWT自动续期、错误处理、请求取消及防重复提交                           |
| 21 | **WebFlux响应式编程**      | [21\_webflux\_reactive\_programming.svg](../diagrams/21_webflux_reactive_programming.svg)                 | 展示Reactor 3响应式流、Mono/Flux操作符、线程池隔离策略及最佳实践                     |
| 22 | **国际化i18n多语言支持**     | [22\_i18n\_internationalization.svg](../diagrams/22_i18n_internationalization.svg)                         | 展示Spring MessageSource后端i18n、Vue-i18n前端多语言、错误码国际化及语言联动        |
| 23 | **OpenAPI文档生成**       | [23\_openapi\_documentation.svg](../diagrams/23_openapi_documentation.svg)                                 | 展示SpringDoc注解配置、Swagger UI在线调试、安全配置及Postman/Apifox集成         |

### 监控可观测性图 (2张) - 2026-09-10 ⭐

| 序号 | 名称                       | 文件                                                                                                             | 说明                                                           |
| -- | ------------------------ | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| 24 | **Prometheus+Grafana监控体系** | [24\_prometheus\_grafana\_monitoring.svg](../diagrams/24_prometheus_grafana_monitoring.svg)                       | 展示66个监控面板分类、apex\_\*指标前缀、Prometheus采集配置及典型监控面板与告警规则         |
| 25 | **Loki日志聚合系统**           | [25\_loki\_log\_aggregation.svg](../diagrams/25_loki_log_aggregation.svg)                                         | 展示Fluent Bit日志采集、LogQL查询语言、Grafana Explore面板及典型场景与最佳实践     |

### 高级架构与技术栈图 (5张) - 2026-09-10 ⭐

| 序号 | 名称              | 文件                                                                                                                     | 说明                                                                 |
| -- | --------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| 26 | **容灾与高可用架构**    | [26\_disaster\_recovery\_high\_availability.svg](../diagrams/26_disaster_recovery_high_availability.svg)                   | 展示多副本部署、故障自愈、数据备份恢复、异地多活及99.95%可用性保障措施                             |
| 27 | **RocketMQ消息队列** | [27\_rocketmq\_message\_queue.svg](../diagrams/27_rocketmq_message_queue.svg)                                             | 展示生产者/消费者配置、普通/顺序/延迟/事务消息类型、幂等消费及监控最佳实践                           |
| 28 | **Meilisearch全文搜索** | [28\_meilisearch\_fulltext\_search.svg](../diagrams/28_meilisearch_fulltext_search.svg)                                   | 展示索引配置、CJK分词、拼音搜索、LogQL查询、数据同步及典型使用场景                            |
| 29 | **技术栈总览与版本管理**  | [29\_technology\_stack\_overview.svg](../diagrams/29_technology_stack_overview.svg)                                       | 展示60+技术组件的完整清单、版本号、依赖关系、BOM管理及升级策略，基于pom.xml/package.json实际配置 |

### 当前统一授权文档与图表入口

| 入口 | 说明 |
| --- | --- |
| [业务流程与统一授权时序](../architecture/business-flows.md) | 当前主应用 JWT 身份认证与 HTTPS 远程授权链路；Mermaid 尚未重新渲染 |
| [统一授权 ADR](../architecture/system-architecture.md) | ADR-AUTH-CENTER-001，独立容器、显式映射和失败拒绝策略 |
| [统一授权配置](../05-operations/configuration.md#认证中心统一授权) | 默认关闭、HTTPS、读取账号 Token、0–30 秒 TTL、3 秒超时与默认熔断 |
| [认证接口路由契约](../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/router/AuthenticationRouter.java) | 登录、Refresh Token 轮换、登出空响应体 |
| [认证接口集成测试](../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/interfaces/handler/AuthenticationHandlerIntegrationTest.java) | 登录、登出、刷新与异常响应的 WebFlux 测试源码；不代表本次已执行 |
| [SpringDoc 审计](../03-api/springdoc-annotation-audit.md) | 主应用与认证中心范围，区分 Handler 注解、Router 元数据和运行时 OpenAPI |
| [安全过滤器链 SVG](../diagrams/84_security_filter_chain.svg) | 既有安全链图，本轮未重绘或验证 |
| [认证中心架构 SVG](../diagrams/85_auth_center_architecture.svg) | 既有认证中心图，主应用与认证中心独立 Spring ApplicationContext |
| [Token 生命周期 SVG](../diagrams/86_token_lifecycle_management.svg) | 既有生命周期图，以当前 TokenServiceImpl 和接口契约为准 |
| [Mermaid 源文件索引](../diagrams/sequence/README.md) | 实际文件清单；旧图 31 的本地权限与 1 小时缓存不代表远程授权实现 |

上述 84–86 SVG 没有同名 Mermaid 源文件；同编号 Mermaid 为不同主题。当前统一授权只映射 `SELF`/`ALL`，缓存默认关闭，失败不使用过期缓存或本地权限回退。以下 30–39 为既有认证、OAuth2、SSO 和 Keycloak 专题设计，不能据此认为主应用登录已经迁移、服务已部署或联调通过。

### 认证授权与SSO架构图 (10张) - 历史专题设计

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 30 | **OAuth2授权服务器架构** | [30\_oauth2\_authorization\_server\_architecture.svg](../diagrams/30_oauth2_authorization_server_architecture.svg) | 展示客户端层、授权端点、核心服务、数据持久层、安全防护、Token策略、资源服务器完整架构 |
| 31 | **OAuth2四种授权模式详解** | [31\_oauth2\_grant\_types\_comparison.svg](../diagrams/31_oauth2_grant_types_comparison.svg) | 授权码+PKCE、隐式(已弃用)、密码、客户端凭证四种模式的流程图和对比表 |
| 32 | **OAuth2授权码流程时序图** | [32\_oauth2\_authorization\_code\_sequence\_part1.svg](../diagrams/32_oauth2_authorization_code_sequence_part1.svg) | 从PKCE初始化到获取授权码的5个阶段详细交互流程 |
| 33 | **OAuth2 Token交换时序图** | [33\_oauth2\_token\_exchange\_sequence.svg](../diagrams/33_oauth2_token_exchange_sequence.svg) | 用授权码换取Access Token和Refresh Token的PKCE验证流程 |
| 34 | **SSO单点登录架构** | [34\_sso\_architecture.svg](../diagrams/34_sso_architecture.svg) | 统一认证中心、多应用接入、Redis Session共享、Cookie Domain策略、单点登出 |
| 35 | **SSO登录流程时序图** | [35\_sso\_login\_sequence.svg](../diagrams/35_sso_login_sequence.svg) | 首次登录、免登录、单点登出三个阶段的完整交互 |
| 36 | **Keycloak集成架构** | [36\_keycloak\_integration\_architecture.svg](../diagrams/36_keycloak_integration_architecture.svg) | Realm/Client配置、OIDC端点、用户同步、角色映射、配置开关 |
| 37 | **Keycloak OIDC认证时序图** | [37\_keycloak\_oidc\_sequence.svg](../diagrams/37_keycloak_oidc_sequence.svg) | 发起OIDC认证、获取ID Token、用户同步、生成Apex JWT的6阶段流程 |
| 38 | **统一权限认证整体架构** | [38\_unified\_auth\_architecture.svg](../diagrams/38_unified_auth_architecture.svg) | JWT+OAuth2+Keycloak三合一架构、Spring Security过滤器链、路由策略表 |
| 39 | **Token刷新与续期机制** | [39\_token\_refresh\_mechanism.svg](../diagrams/39_token_refresh_mechanism.svg) | 过期检测、自动刷新、前端无感知续期、Token轮换机制及生命周期对比 |

### 数据权限与安全防护图 (5张) - 2026-09-11 ⭐

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 40 | **数据权限过滤流程图** | [40\_data\_scope\_filter\_flow.svg](../diagrams/40_data_scope_filter_flow.svg) | 展示MyBatis-Plus拦截器自动注入WHERE条件实现5种数据权限类型的完整流程 |
| 41 | **多数据源切换架构图** | [41\_datasource\_switch\_architecture.svg](../diagrams/41_datasource_switch_architecture.svg) | 展示基于@DataSource注解+AOP切面实现读写分离与多数据源路由的5层架构 |
| 42 | **审计日志时序图** | [42\_audit\_log\_sequence.svg](../diagrams/42_audit_log_sequence.svg) | 展示@OperationLog注解+异步批量入库的审计日志记录完整时序,包含4个阶段6个参与者 |
| 43 | **SPI插件架构图** | [43\_spi\_plugin\_architecture.svg](../diagrams/43_spi_plugin_architecture.svg) | 展示基于Java ServiceLoader的存储后端动态扩展架构,包含5层结构和加载机制 |
| 44 | **安全防护层次图** | [44\_security\_protection\_layers.svg](../diagrams/44_security_protection_layers.svg) | 展示从网络层到数据层的7层防御体系,包含25+安全防护机制和监控指标 |

### 运维部署图 (5张) - 2026-09-11 ⭐

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 45 | **部署流程图** | [45\_deployment\_flow.svg](../diagrams/45_deployment_flow.svg) | 展示从构建、部署方式选择、依赖启动、Flyway迁移到健康检查和上线运维的完整流程 |
| 46 | **配置层级图** | [46\_configuration\_hierarchy.svg](../diagrams/46_configuration_hierarchy.svg) | 展示基础配置、环境配置、Profile激活、环境变量覆盖和运行时组件之间的配置优先级 |
| 47 | **故障排查决策树** | [47\_troubleshooting\_decision\_tree.svg](../diagrams/47_troubleshooting_decision_tree.svg) | 展示端口、MySQL、Redis、Maven、JWT和权限问题的排查与修复路径 |
| 48 | **监控配置链路图** | [48\_monitoring\_configuration.svg](../diagrams/48_monitoring_configuration.svg) | 展示应用指标、Exporter、Prometheus、Alertmanager、Grafana和Zipkin之间的监控配置链路 |
| 49 | **部署检查清单流程** | [49\_deployment\_checklist\_flow.svg](../diagrams/49_deployment_checklist_flow.svg) | 展示文件、后端、API、前端、路由权限、功能测试和端到端确认的上线检查闭环 |

### 开发指南图 (5张) - 2026-09-11 ⭐

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 50 | **DDD 四层开发流程** | [50_ddd_development_flow.svg](../diagrams/50_ddd_development_flow.svg) | 展示领域、基础设施、应用、接口四层的标准开发路径 |
| 51 | **WebFlux 阻塞 IO 隔离** | [51_webflux_blocking_io_isolation.svg](../diagrams/51_webflux_blocking_io_isolation.svg) | 展示事件循环、响应式链路与专用阻塞线程池的隔离边界 |
| 52 | **DDD 代码生成流水线** | [52_codegen_pipeline.svg](../diagrams/52_codegen_pipeline.svg) | 展示 JSON 表结构到四层 CRUD 代码和验证落地的流水线 |
| 53 | **质量门禁流水线** | [53_quality_gate_pipeline.svg](../diagrams/53_quality_gate_pipeline.svg) | 展示格式检查、单测、集成验证与交付门禁 |
| 54 | **API 契约流转** | [54_api_contract_flow.svg](../diagrams/54_api_contract_flow.svg) | 展示路由、安全校验、统一响应和前端消费的契约闭环 |

### 前端与 API 集成图 (3张) - 2026-09-11 ⭐

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 55 | **前端与 API 集成** | [55_frontend_api_integration.svg](../diagrams/55_frontend_api_integration.svg) | 展示前端请求封装、网关、WebFlux Handler 与统一响应的集成链路 |
| 57 | **动态路由解析** | [57_dynamic_route_resolution.svg](../diagrams/57_dynamic_route_resolution.svg) | 展示菜单权限、组件注册、动态路由与页面渲染的解析流程 |
| 58 | **API 错误响应流** | [58_api_error_response_flow.svg](../diagrams/58_api_error_response_flow.svg) | 展示参数、认证、权限和业务错误到统一响应及前端恢复的流程 |

### API 治理与测试图 (2张) - 2026-09-11 ⭐

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 56 | **API 版本演进** | [56_api_version_evolution.svg](../diagrams/56_api_version_evolution.svg) | 展示版本兼容、灰度验证、监控和生命周期管理 |
| 59 | **API 测试反馈闭环** | [59_api_testing_feedback_loop.svg](../diagrams/59_api_testing_feedback_loop.svg) | 展示契约设计、测试执行、缺陷定位、修复回归的反馈闭环 |

### 系统能力架构图 (5张) - 2026-09-11 ⭐

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 65 | **License 验证流程** | [65_license_validation_flow.svg](../diagrams/65_license_validation_flow.svg) | 展示 License 加载、RSA 签名、公钥指纹、机器指纹、有效期与商业能力门控 |
| 66 | **分布式 Bulkhead 隔离** | [66_distributed_bulkhead_isolation.svg](../diagrams/66_distributed_bulkhead_isolation.svg) | 展示按下游资源划分并发舱壁、独立容量、超时、熔断与降级 |
| 67 | **读请求降级快照** | [67_read_degradation_snapshot.svg](../diagrams/67_read_degradation_snapshot.svg) | 历史读请求降级设计；不适用于当前统一授权，远程失败不使用过期授权快照 |
| 68 | **OpenAPI 文档生成链路** | [68_openapi_documentation_flow.svg](../diagrams/68_openapi_documentation_flow.svg) | 展示文档生成链路；认证中心 OAuth2 和统一授权元数据位于 Router，运行时 OpenAPI 尚待验证 |
| 69 | **i18n 消息处理链路** | [69_i18n_message_flow.svg](../diagrams/69_i18n_message_flow.svg) | 展示请求语言、消息键、MessageSource、资源包与统一响应的国际化链路 |

### 专题架构图 (5张) - 2026-09-11 ⭐

| 序号 | 名称 | 文件 | 说明 |
| -- | -- | -- | -- |
| 60 | **工作流引擎生命周期** | [60_workflow_engine_lifecycle.svg](../diagrams/60_workflow_engine_lifecycle.svg) | 展示流程定义、实例启动、任务流转与历史追踪 |
| 61 | **消息队列可靠性** | [61_message_queue_reliability.svg](../diagrams/61_message_queue_reliability.svg) | 展示 Outbox、RocketMQ、幂等、重试与死信闭环 |
| 62 | **数据流一致性** | [62_data_flow_consistency.svg](../diagrams/62_data_flow_consistency.svg) | 展示事务、缓存、领域事件和校准流程 |
| 63 | **全文搜索处理流水线** | [63_fulltext_search_pipeline.svg](../diagrams/63_fulltext_search_pipeline.svg) | 展示数据写入、索引同步、查询和重建流程 |
| 64 | **Redis Watchdog 分布式锁** | [64_redis_watchdog_lock.svg](../diagrams/64_redis_watchdog_lock.svg) | 展示获取锁、自动续期、释放与故障行为 |

**登记范围**: 既有编号 01–69 及认证相关 84–86；本索引不是全量文件统计，不声明全部重新生成或验证通过。

***

## 📖 技术文档

### 架构设计文档

| 文档名称              | 文件路径                                                                                                                 | 说明                                                          |
| ----------------- | -------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| **系统架构设计文档**      | [architecture/system-architecture.md](../architecture/system-architecture.md)                                           | DDD分层架构、技术栈、架构决策记录(ADR)                                     |
| **01 逻辑架构详细说明**   | [architecture/01-logical-architecture-detail.md](../architecture/01-logical-architecture-detail.md)                     | 四层职责、模块映射、调用链、阻塞边界与架构约束                                     |
| **01 逻辑架构快速参考**   | [architecture/01-logical-architecture-quick-ref.md](../architecture/01-logical-architecture-quick-ref.md)               | 模块定位、依赖规则、技术能力与开发检查清单                                       |
| **01 逻辑架构架构决策**   | [architecture/01-logical-architecture-decision.md](../architecture/01-logical-architecture-decision.md)                 | DDD 四层架构、依赖倒置和模块化单体决策 (ADR-001)                             |
| **01 逻辑架构商业价值**   | [architecture/01-logical-architecture-business-value.md](../architecture/01-logical-architecture-business-value.md)     | 可验证的工程价值、度量方式和收益边界                                          |
| **02 部署架构详细说明** ⭐ | [architecture/02-physical-architecture-detail.md](../architecture/02-physical-architecture-detail.md)                   | 五层拓扑、Podman Compose 默认栈、K8s 扩展拓扑 (新增)                       |
| **02 部署架构快速参考** ⭐ | [architecture/02-physical-architecture-quick-ref.md](../architecture/02-physical-architecture-quick-ref.md)             | 端口速查、命令速查、环境变量、故障排查 (新增)                                    |
| **02 部署架构架构决策** ⭐ | [architecture/02-physical-architecture-decision.md](../architecture/02-physical-architecture-decision.md)               | 五层拓扑、Compose vs K8s、端口分离、能力分级 (ADR-002\~008) (新增)           |
| **02 部署架构商业价值** ⭐ | [architecture/02-physical-architecture-business-value.md](../architecture/02-physical-architecture-business-value.md)   | 上线速度、成本控制、安全合规、故障定位、扩展灵活性 (新增)                              |
| **03 技术架构详细说明** ⭐ | [architecture/03-technical-architecture-detail.md](../architecture/03-technical-architecture-detail.md)                 | 七层技术栈、前后端版本、依赖关系、Profile 能力矩阵 (新增)                          |
| **03 技术架构快速参考** ⭐ | [architecture/03-technical-architecture-quick-ref.md](../architecture/03-technical-architecture-quick-ref.md)           | 版本速查、命令速查、性能调优、监控指标 (新增)                                    |
| **03 技术架构架构决策** ⭐ | [architecture/03-technical-architecture-decision.md](../architecture/03-technical-architecture-decision.md)             | WebFlux、MyBatis-Plus、Vue 3、二级缓存、Profile (ADR-009\~015) (新增) |
| **03 技术架构商业价值** ⭐ | [architecture/03-technical-architecture-business-value.md](../architecture/03-technical-architecture-business-value.md) | 开发效率、性能并发、资源成本、可观测性、稳定性、扩展性 (新增)                            |
| **业务流程文档**        | [architecture/business-flows.md](../architecture/business-flows.md)                                                     | 登录认证、权限校验、工作流、文件上传流程详解                                      |
| **数据库设计文档**       | [architecture/database-design.md](../architecture/database-design.md)                                                   | 表结构设计、索引优化、主从复制配置                                           |
| **部署架构文档**        | [architecture/deployment-architecture.md](../architecture/deployment-architecture.md)                                   | Kubernetes/Podman部署方案、HPA扩缩容、高可用保障                          |
| **网络架构文档**        | [architecture/network-architecture.md](../architecture/network-architecture.md)                                         | 网络分区、防火墙规则、DDoS防护、SSL/TLS配置                                 |
| **后端技术栈文档**       | [architecture/backend-technology-stack.md](../architecture/backend-technology-stack.md)                                 | Spring Boot/WebFlux/MyBatis-Plus等后端技术栈详解、技术选型理由、性能优化策略      |
| **前端技术栈文档**       | [architecture/frontend-technology-stack.md](../architecture/frontend-technology-stack.md)                               | Vue 3/Vite 6/TypeScript/Pinia/Element Plus前端技术栈详解、开发规范、最佳实践 |
| **基础设施组件文档**      | [architecture/infrastructure-components.md](../architecture/infrastructure-components.md)                               | 应用服务、数据存储、监控可观测、容器编排、CI/CD等基础设施组件详解                         |
| **数据流转架构文档**      | [architecture/data-flow-architecture.md](../architecture/data-flow-architecture.md)                                     | 五种核心数据流、数据一致性保障、性能优化建议                                      |
| **消息队列架构文档**      | [architecture/mq-architecture.md](../architecture/mq-architecture.md)                                                   | RocketMQ集群架构、事务消息、高可用容灾、性能优化                                |
| **Redis看门狗机制**    | [architecture/redis-watchdog-mechanism.md](../architecture/redis-watchdog-mechanism.md)                                 | Redis分布式锁的自动续期机制                                            |

### 认证授权文档 (新增) - 2026-09-11 ⭐

| 文档名称 | 文件路径 | 说明 |
| -- | -- | -- |
| **OAuth2集成开发指南** ⭐ | [OAUTH2\_INTEGRATION\_GUIDE.md](../01-guides/oauth2-integration-guide.md) | OAuth2四种授权模式选择、客户端注册、PKCE实现、Token管理、安全防护、API对接示例、问题排查 |
| **Keycloak集成配置指南** ⭐ | [KEYCLOAK\_INTEGRATION\_GUIDE.md](../01-guides/keycloak-integration-guide.md) | Keycloak部署、Realm/Client配置、OIDC集成、用户同步策略、角色映射、前后端集成、生产部署 |
| **SSO单点登录实施方案** ⭐ | [SSO\_IMPLEMENTATION\_GUIDE.md](../01-guides/sso-implementation-guide.md) | SSO原理、Session共享、跨域认证、多应用集成、单点登出、安全考虑、测试验证 |

### 高级特性文档

| 文档名称             | 文件路径                                                                                                                                           | 说明                      |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| **自动重试策略**       | [architecture/advanced-features/auto-retry-strategy.md](../architecture/advanced-features/auto-retry-strategy.md)                                 | HTTP、MQ、定时任务的重试策略       |
| **多级缓存架构**       | [architecture/advanced-features/multi-level-cache-architecture.md](../architecture/advanced-features/multi-level-cache-architecture.md)           | Caffeine + Redis 两级缓存设计 |
| **安全防护体系**       | [architecture/advanced-features/security-protection-system.md](../architecture/advanced-features/security-protection-system.md)                   | JWT认证、RBAC权限、数据加密、审计日志  |
| **Sentinel流控熔断** | [architecture/advanced-features/sentinel-rate-limit-circuit-breaker.md](../architecture/advanced-features/sentinel-rate-limit-circuit-breaker.md) | Sentinel规则配置和熔断降级       |

### 运维文档

| 文档名称         | 文件路径                                                                                                 | 说明                                         |
| ------------ | ---------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **部署指南**     | [operations/deployment.md](../05-operations/deployment.md)                                                 | 生产环境部署步骤                                   |
| **配置说明**     | [operations/configuration.md](../05-operations/configuration.md)                                           | 配置项详解                                      |
| **监控指南**     | [operations/monitoring.md](../_archive/operations/monitoring.md)                                                 | Prometheus/Grafana监控配置                     |
| **监控大盘配置** ⭐ | [operations/monitoring-dashboard-configuration.md](../_archive/operations/monitoring-dashboard-configuration.md) | Prometheus配置、Grafana面板、告警规则、运维操作、最佳实践 (新增) |
| **部署检查清单**   | [operations/deployment-checklist.md](../05-operations/deployment.md)                             | 上线前检查项                                     |

### 开发规范文档

| 文档名称        | 文件路径                                                                                       | 说明                   |
| ----------- | ------------------------------------------------------------------------------------------ | -------------------- |
| **后端开发规范**  | [development-standards.md](../04-development/development-standards.md)   | Java/Spring Boot开发规范 |
| **前端开发规范**  | 独立前端仓库 `apex-admin-web` 的 `frontend-docs/frontend-development-standards.md` | Vue/TypeScript开发规范   |
| **API路由映射** | [api/route-mapping.md](../_archive/api/route-mapping.md)                                               | 前后端API路由对照表          |

### 快速开始文档

| 文档名称       | 文件路径                                    | 说明        |
| ---------- | --------------------------------------- | --------- |
| **快速开始**   | [QUICK\_START.md](../00-getting-started/quickstart.md)       | 5分钟快速启动项目 |
| **README** | [README.md](../README.md)                  | 项目介绍和功能特性 |
| **最佳实践**   | [BEST\_PRACTICES.md](../01-guides/best-practices.md) | 开发和运维最佳实践 |

***

## 🎯 按场景查找

### 场景1: 新人入职 - 快速了解系统

**推荐阅读顺序**:

1. [README.md](../README.md) - 项目介绍
2. [QUICK\_START.md](../00-getting-started/quickstart.md) - 快速启动
3. [系统架构设计文档](../architecture/system-architecture.md) - 整体架构
4. [DDD 四层逻辑架构图](../diagrams/01_logical_architecture.svg) - 分层设计
5. [全栈技术选型全景图](../diagrams/03_technical_architecture.svg) - 技术栈

### 场景2: 部署上线 - 生产环境部署

**推荐阅读顺序**:

1. [部署架构文档](../architecture/deployment-architecture.md) - 部署方案
2. [部署架构拓扑图](../diagrams/45_deployment_flow.svg) - 部署拓扑
3. [网络架构文档](../architecture/network-architecture.md) - 网络配置
4. [网络架构图](../diagrams/02_physical_architecture.svg) - 网络分区
5. [部署检查清单](../05-operations/deployment.md) - 上线检查

### 场景3: 性能优化 - 排查性能瓶颈

**推荐阅读顺序**:

1. [数据流转架构图](../diagrams/62_data_flow_consistency.svg) - 数据流分析
2. [多级缓存架构图](../diagrams/09_multi_level_cache_architecture.svg) - 缓存优化
3. [监控体系架构图](../diagrams/48_monitoring_configuration.svg) - 监控指标
4. [监控指南](../_archive/operations/monitoring.md) - 监控配置

### 场景4: 故障排查 - 定位系统问题

**推荐阅读顺序**:

1. [监控指南](../_archive/operations/monitoring.md) - 查看监控指标
2. [微服务治理架构图](../diagrams/43_spi_plugin_architecture.svg) - 服务调用链
3. [Sentinel流控熔断图](../diagrams/10_rate_limit_circuit_breaker.svg) - 限流熔断
4. [数据权限过滤图](../diagrams/11_data_permission_filter.svg) - 权限过滤机制

### 场景5: 功能开发 - 实现新业务功能

**推荐阅读顺序**:

1. [后端开发规范](../04-development/development-standards.md) - 编码规范
2. 前端开发规范（位于独立前端仓库 `apex-admin-web` 的 `frontend-docs/`） - 前端规范
3. [业务流程文档](../architecture/business-flows.md) - 业务流程
4. [数据库设计文档](../architecture/database-design.md) - 数据库设计
5. [API路由映射](../_archive/api/route-mapping.md) - API设计

### 场景6: 安全审计 - 系统安全检查

**推荐阅读顺序**:

1. [安全防护体系图](../diagrams/12_security_protection_system.svg) - 安全机制
2. [安全防护体系文档](../architecture/advanced-features/security-protection-system.md) - 安全详解
3. [网络架构文档](../architecture/network-architecture.md) - 网络安全
4. [网络架构图](../diagrams/02_physical_architecture.svg) - 防火墙规则

### 场景7: 技术选型 - 了解技术栈组成 ⭐

**推荐阅读顺序**:

1. [03 技术架构全景图 - 详细说明](../architecture/03-technical-architecture-detail.md) - 七层技术栈详解
2. [03 技术架构全景图 - 架构决策](../architecture/03-technical-architecture-decision.md) - 技术选型理由 (ADR-009\~015)
3. [技术生态全景图](../diagrams/29_technology_stack_overview.svg) - 60+技术组件全貌
4. [后端技术栈架构图](../diagrams/29_technology_stack_overview.svg) - 后端技术分层
5. [前端技术栈架构图](../diagrams/29_technology_stack_overview.svg) - 前端技术分层

### 场景8: 基础设施规划 - 了解组件部署 ⭐

**推荐阅读顺序**:

1. [基础设施组件架构图](../diagrams/46_configuration_hierarchy.svg) - 6层基础设施全貌
2. [基础设施组件文档](../architecture/infrastructure-components.md) - 组件配置与高可用方案
3. [部署架构拓扑图](../diagrams/45_deployment_flow.svg) - Kubernetes部署拓扑
4. [监控体系架构图](../diagrams/48_monitoring_configuration.svg) - 监控组件部署

### 场景9: 前后端协作开发 - 理解交互流程 ⭐

**推荐阅读顺序**:

1. [前后端交互架构图](../diagrams/58_api_error_response_flow.svg) - 完整交互流程
2. [前端技术栈文档](../architecture/frontend-technology-stack.md) - 前端实现细节
3. [后端技术栈文档](../architecture/backend-technology-stack.md) - 后端实现细节
4. [API路由映射](../_archive/api/route-mapping.md) - API接口对照
5. 前端开发规范（位于独立前端仓库 `apex-admin-web` 的 `frontend-docs/`） - 前端编码规范

### 场景10: 监控体系配置 - 搭建监控大盘 ⭐

**推荐阅读顺序**:

1. [Grafana监控面板架构图](../diagrams/48_monitoring_configuration.svg) - 66个监控面板分组
2. [Prometheus监控体系架构图](../diagrams/24_prometheus_grafana_monitoring.svg) - 监控指标采集架构
3. [监控大盘配置文档](../_archive/operations/monitoring-dashboard-configuration.md) - Prometheus/Grafana配置详解
4. [监控体系架构图](../diagrams/48_monitoring_configuration.svg) - 三大监控支柱
5. [监控指南](../_archive/operations/monitoring.md) - 运维监控实践

***

## 🔍 按关键词查找

| 关键词              | 相关文档/图表                                                |
| ---------------- | ------------------------------------------------------ |
| **前端技术栈**        | 前端技术栈架构图、前端技术栈文档、前后端交互架构图、前端开发规范                       |
| **Vue 3**        | 前端技术栈架构图、前端技术栈文档、前端开发规范                                |
| **Vite**         | 前端技术栈架构图、前端技术栈文档                                       |
| **TypeScript**   | 前端技术栈架构图、前端技术栈文档、后端技术栈文档                               |
| **Pinia**        | 前端技术栈架构图、前端技术栈文档                                       |
| **Element Plus** | 前端技术栈架构图、前端技术栈文档                                       |
| **前后端交互**        | 前后端交互架构图、API路由映射、数据流转架构图                               |
| **技术栈**          | 技术生态全景图、后端技术栈架构图、前端技术栈架构图、全栈技术选型全景图                    |
| **基础设施**         | 基础设施组件架构图、基础设施组件文档、部署架构拓扑图                             |
| **部署**           | 部署架构文档、部署架构拓扑图、部署指南、部署检查清单                             |
| **网络**           | 网络架构文档、网络架构图、物理部署架构图                                   |
| **数据流**          | 数据流转架构图、数据流转架构文档、业务流程文档                                |
| **消息队列**         | RocketMQ消息队列架构图、消息队列架构文档、数据流转架构图                       |
| **监控**           | Grafana监控面板架构图、Prometheus监控体系架构图、监控大盘配置文档、监控体系架构图、监控指南 |
| **Prometheus**   | Prometheus监控体系架构图、监控大盘配置文档、监控指南                        |
| **Grafana**      | Grafana监控面板架构图、监控大盘配置文档、监控指南                           |
| **告警**           | Prometheus监控体系架构图、监控大盘配置文档、监控体系架构图                     |
| **微服务**          | 微服务治理架构图、系统架构设计文档                                      |
| **缓存**           | 多级缓存架构图、Redis看门狗机制、后端技术栈文档                             |
| **安全**           | 安全防护体系图、安全防护体系文档、网络架构文档                                |
| **认证授权**         | JWT登录认证流程图、RBAC权限校验流程图、安全防护体系、前后端交互架构图                 |
| **工作流**          | 三级审批工作流图、业务流程文档                                        |
| **文件上传**         | 文件分片上传流程图、业务流程文档                                       |
| **数据库**          | 数据库ER图、数据库设计文档                                         |
| **限流熔断**         | Sentinel流控熔断图、微服务治理架构图                                 |
| **重试**           | 自动重试策略流程图                                              |
| **DDD**          | DDD四层逻辑架构图、系统架构设计文档                                    |
| **Spring Boot**  | 后端技术栈架构图、后端技术栈文档                                       |
| **WebFlux**      | 后端技术栈架构图、后端技术栈文档、前后端交互架构图                              |
| **Kubernetes**   | 基础设施组件架构图、部署架构文档                                       |
| **Podman**       | 基础设施组件架构图、部署架构文档                                       |
| **Prometheus**   | 监控体系架构图、基础设施组件文档                                       |
| **Grafana**      | 监控体系架构图、基础设施组件文档                                       |

***

## 📝 文档更新记录

| 日期         | 版本   | 更新内容                                                                                                             | 更新人  |
| ---------- | ---- | ---------------------------------------------------------------------------------------------------------------- | ---- |
| 2026-09-10 | v2.0 | **重大更新**: 根据项目实际实现重新生成全部29张SVG图表,升级深色企业级配色,移除CSS变量确保跨平台兼容,所有技术栈版本与pom.xml/package.json一致,新增13-29号图表详细索引 ⭐⭐⭐ | 架构团队 |
| 2026-09-04 | v1.3 | 新增2张监控架构图及1篇配套文档：Grafana监控面板架构图、Prometheus监控体系架构图，新增监控大盘配置文档，总计25张架构图 ⭐                                 | 架构团队 |
| 2026-09-04 | v1.2 | 新增2张前端架构图及1篇配套文档：前端技术栈架构图、前后端交互架构图，新增前端技术栈文档，总计23张架构图                                                    | 架构团队 |
| 2026-09-04 | v1.1 | 新增3张技术栈架构图及2篇配套文档：后端技术栈架构图、基础设施组件架构图、技术生态全景图，新增后端技术栈文档、基础设施组件文档，总计21张架构图                                 | 架构团队 |
| 2026-09-04 | v1.0 | 新增6张架构图及4篇配套文档：部署架构、网络架构、数据流转、消息队列、监控体系、微服务治理                                                            | 架构团队 |
| 2026-09-03 | v0.9 | 创建12张核心架构图及基础文档                                                                                                  | 架构团队 |

***

## 🔗 外部参考资料

- [Spring Boot官方文档](https://docs.spring.io/spring-boot/)

- [Spring WebFlux官方文档](https://docs.spring.io/spring-framework/reference/web/webflux.html)

- [Kubernetes官方文档](https://kubernetes.io/docs/)

- [RocketMQ官方文档](https://rocketmq.apache.org/docs/quick-start/)

- [Prometheus官方文档](https://prometheus.io/docs/)

- [领域驱动设计 (DDD)](https://domain-driven-design.org/)

***

**文档维护者**: 架构团队\
**审核状态**: 待人工审核\
**下次更新**: 架构变更后同步更新
