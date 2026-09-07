# Apex Scaffold 架构文档索引

> 文档版本: v1.1\
> 最后更新: 2026-09-05\
> 适用版本: apex-scaffold v1.2.0+\
> 技术栈版本: Spring Boot 3.5.15 | Java 17 | Reactor 3.6.13

本文档提供Apex Scaffold项目所有架构图表和技术文档的快速索引。

***

## ✅ SVG 图表质量状态

**最近更新**: 2026-09-05 | **验证范围**: 全部 29 张 SVG 图表

### 兼容性验证

| 验证项          | 状态   | 说明                                            |
| ------------ | ---- | --------------------------------------------- |
| **CSS 变量清理** | ✅ 完成 | 全部图表已移除 `:root` 变量和 `var()` 引用                |
| **暗色模式清理**   | ✅ 完成 | 全部图表已移除 `@media (prefers-color-scheme: dark)` |
| **静态颜色**     | ✅ 完成 | 全部图表使用静态颜色值（深色系企业级配色）               |
| **文本标签清理**   | ✅ 完成 | 全部图表已移除 `[Globe]`、`[Users]` 等文本标签                |
| **配色优化**     | ✅ 完成 | 升级为深色系企业级配色（#4f46e5, #0891b2, #059669, #d97706 等） |
| **XML 格式**   | ✅ 通过 | 全部图表通过 `xmllint` 验证                           |
| **跨查看器兼容**   | ✅ 通过 | 支持 Chrome、Safari、Firefox、VSCode、Obsidian 等    |

### 修复记录

| 图表范围        | 修复日期       | 处理内容                         |
| ----------- | ---------- | ---------------------------- |
| **01-03 号** | 2026-09-04 | CSS 变量 → 静态颜色，完成配套文档（共 12 份） |
| **04-12 号** | 2026-09-05 | CSS 变量 → 静态颜色，部分配套文档已存在      |
| **13-25 号** | 2026-09-05 | 创建时已使用静态颜色，验证通过无需修复          |
| **26-29 号** | 2026-09-05 | 新创建，直接使用静态颜色，XML 转义正确        |

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

**结论**: 全部 29 张 SVG 图表状态优秀，视觉专业度大幅提升，可在各类查看器中正常显示。

***

## 📊 架构图表 (SVG)

### 核心架构图 (3张)

| 序号 | 名称          | 文件                                                                        | 说明                          |
| -- | ----------- | ------------------------------------------------------------------------- | --------------------------- |
| 01 | DDD 四层逻辑架构图 | [01\_logical\_architecture.svg](diagrams/01_logical_architecture.svg)     | 展示接口层、应用层、领域层、基础设施层的DDD分层设计 |
| 02 | 网络物理部署架构图   | [02\_physical\_architecture.svg](diagrams/02_physical_architecture.svg)   | 展示生产环境的物理部署拓扑和组件分布          |
| 03 | 全栈技术选型全景图   | [03\_technical\_architecture.svg](diagrams/03_technical_architecture.svg) | 展示前后端技术栈和中间件选型全貌            |

### 业务流程图 (4张)

| 序号 | 名称           | 文件                                                                         | 说明                      |
| -- | ------------ | -------------------------------------------------------------------------- | ----------------------- |
| 04 | JWT 登录认证流程图  | [04\_login\_auth\_flow.svg](diagrams/04_login_auth_flow.svg)               | 展示用户登录、Token生成、刷新、登出全流程 |
| 05 | RBAC 权限校验流程图 | [05\_permission\_check\_flow.svg](diagrams/05_permission_check_flow.svg)   | 展示角色权限校验和数据权限过滤流程       |
| 06 | 三级审批工作流图     | [06\_workflow\_approval\_flow.svg](diagrams/06_workflow_approval_flow.svg) | 展示BPMN工作流引擎的流程发起、审批、流转  |
| 07 | 文件分片上传流程图    | [07\_file\_upload\_flow.svg](diagrams/07_file_upload_flow.svg)             | 展示大文件分片上传、断点续传、秒传流程     |

### 基础设施图 (5张)

| 序号 | 名称            | 文件                                                                                                     | 说明                              |
| -- | ------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------- |
| 08 | 数据库ER图        | [08\_database\_er\_diagram.svg](diagrams/08_database_er_diagram.svg)                                   | 展示核心业务表结构和关系                    |
| 09 | Sentinel流控熔断图 | [09\_sentinel\_rate\_limit\_circuit\_breaker.svg](diagrams/09_sentinel_rate_limit_circuit_breaker.svg) | 展示Sentinel流控规则和熔断降级策略           |
| 10 | 多级缓存架构图       | [10\_multi\_level\_cache\_architecture.svg](diagrams/10_multi_level_cache_architecture.svg)            | 展示Caffeine L1 + Redis L2 两级缓存架构 |
| 11 | 自动重试策略流程图     | [11\_auto\_retry\_strategy\_flow.svg](diagrams/11_auto_retry_strategy_flow.svg)                        | 展示HTTP/MQ/定时任务的重试策略             |
| 12 | 安全防护体系图       | [12\_security\_protection\_system.svg](diagrams/12_security_protection_system.svg)                     | 展示认证、授权、加密、审计等安全机制              |

### 新增架构图 (6张) - 2026-09-04

| 序号 | 名称                  | 文件                                                                          | 说明                                                                                |
| -- | ------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| 13 | **部署架构拓扑图**         | [13\_deployment\_architecture.svg](diagrams/13_deployment_architecture.svg) | 展示DMZ区、应用层、数据层、中间件层、监控层的Kubernetes/Docker部署架构                                     |
| 14 | **网络架构图**           | [14\_network\_architecture.svg](diagrams/14_network_architecture.svg)       | 展示公网区、DMZ区、应用区、数据区、运维管理区五层网络分区及三层防火墙规则                                            |
| 15 | **数据流转架构图**         | [15\_data\_flow\_architecture.svg](diagrams/15_data_flow_architecture.svg)  | 展示用户请求、写操作、异步消息、文件上传、外部服务调用五种核心数据流及一致性保障                                          |
| 16 | **RocketMQ消息队列架构图** | [16\_mq\_architecture.svg](diagrams/16_mq_architecture.svg)                 | 展示RocketMQ生产者、NameServer、Broker主从、消费者集群及高可用机制                                     |
| 17 | **监控体系架构图**         | [17\_monitoring\_architecture.svg](diagrams/17_monitoring_architecture.svg) | 展示Prometheus/Grafana (Metrics)、ELK (Logs)、Zipkin (Traces) 三大监控支柱及AlertManager告警系统 |
| 18 | **微服务治理架构图**        | [18\_microservice\_governance.svg](diagrams/18_microservice_governance.svg) | 展示Nacos服务注册发现、Sentinel熔断限流、Sleuth链路追踪及配置管理体系                                      |

### 新增技术栈与基础设施图 (3张) - 2026-09-04 ⭐

| 序号 | 名称            | 文件                                                                              | 说明                                                      |
| -- | ------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------- |
| 19 | **后端技术栈架构图**  | [19\_backend\_technology\_stack.svg](diagrams/19_backend_technology_stack.svg)  | 展示Spring Boot、WebFlux、MyBatis-Plus等后端技术栈的6层架构及各层职责与协作关系 |
| 20 | **基础设施组件架构图** | [20\_infrastructure\_components.svg](diagrams/20_infrastructure_components.svg) | 展示应用服务、数据存储、监控可观测、网关负载均衡、容器编排、CI/CD六层基础设施组件及数据流向        |
| 21 | **技术生态全景图**   | [21\_technology\_ecosystem.svg](diagrams/21_technology_ecosystem.svg)           | 展示60+技术组件的全景视图，包括核心技术栈、数据存储、微服务治理、可观测性、DevOps工具链五大区域    |

### 新增前端技术栈图 (2张) - 2026-09-04 ⭐

| 序号 | 名称           | 文件                                                                                     | 说明                                                                  |
| -- | ------------ | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| 22 | **前端技术栈架构图** | [22\_frontend\_technology\_stack.svg](diagrams/22_frontend_technology_stack.svg)       | 展示Vue 3.5、Vite 6、TypeScript 5、Pinia、Element Plus等前端技术栈的5层架构及30+核心依赖 |
| 23 | **前后端交互架构图** | [23\_frontend\_backend\_interaction.svg](diagrams/23_frontend_backend_interaction.svg) | 展示Vue 3前端与Spring Boot WebFlux后端的完整交互流程，包含登录、权限、业务请求、错误处理5个核心场景      |

### 新增监控大盘架构图 (2张) - 2026-09-04 ⭐

| 序号 | 名称                    | 文件                                                                                                 | 说明                                                     |
| -- | --------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| 24 | **Grafana监控面板架构图**    | [24\_grafana\_dashboard\_architecture.svg](diagrams/24_grafana_dashboard_architecture.svg)         | 展示66个监控面板的11个分组结构、10组告警规则、数据源配置及核心功能特性                 |
| 25 | **Prometheus监控体系架构图** | [25\_prometheus\_monitoring\_architecture.svg](diagrams/25_prometheus_monitoring_architecture.svg) | 展示6个抓取目标、100+指标类型、PromQL查询引擎、告警规则、Recording Rules及容量规划 |

### 新增高级架构图 (4张) - 2026-09-05 ⭐

| 序号 | 名称               | 文件                                                                         | 说明                                                           |
| -- | ---------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------ |
| 26 | **容灾切换与故障恢复流程图** | [26\_disaster\_recovery\_flow.svg](diagrams/26_disaster_recovery_flow.svg) | 展示主库故障检测、Sentinel投票、自动切换、数据校验、恢复验证完整流程，RTO < 5min，RPO < 1min |
| 27 | **CI/CD流水线详细图**  | [27\_cicd\_pipeline\_detail.svg](diagrams/27_cicd_pipeline_detail.svg)     | 展示代码提交→构建→测试→扫描→部署完整流水线，包含单元测试、安全扫描、滚动发布、自动回滚                |
| 28 | **API网关详细架构图**   | [28\_api\_gateway\_detail.svg](diagrams/28_api_gateway_detail.svg)         | 展示Spring Cloud Gateway核心模块：认证授权、路由转发、限流熔断、监控插件、安全防护          |
| 29 | **安全威胁模型图**      | [29\_security\_threat\_model.svg](diagrams/29_security_threat_model.svg)   | 展示OWASP Top 10威胁、STRIDE模型、纵深防御策略、20种常见威胁及对应防护措施              |

**总计**: 29张架构图

***

## 📖 技术文档

### 架构设计文档

| 文档名称              | 文件路径                                                                                                                 | 说明                                                          |
| ----------------- | -------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| **系统架构设计文档**      | [architecture/system-architecture.md](architecture/system-architecture.md)                                           | DDD分层架构、技术栈、架构决策记录(ADR)                                     |
| **01 逻辑架构详细说明**   | [architecture/01-logical-architecture-detail.md](architecture/01-logical-architecture-detail.md)                     | 四层职责、模块映射、调用链、阻塞边界与架构约束                                     |
| **01 逻辑架构快速参考**   | [architecture/01-logical-architecture-quick-ref.md](architecture/01-logical-architecture-quick-ref.md)               | 模块定位、依赖规则、技术能力与开发检查清单                                       |
| **01 逻辑架构架构决策**   | [architecture/01-logical-architecture-decision.md](architecture/01-logical-architecture-decision.md)                 | DDD 四层架构、依赖倒置和模块化单体决策 (ADR-001)                             |
| **01 逻辑架构商业价值**   | [architecture/01-logical-architecture-business-value.md](architecture/01-logical-architecture-business-value.md)     | 可验证的工程价值、度量方式和收益边界                                          |
| **02 部署架构详细说明** ⭐ | [architecture/02-physical-architecture-detail.md](architecture/02-physical-architecture-detail.md)                   | 五层拓扑、Docker Compose 默认栈、K8s 扩展拓扑 (新增)                       |
| **02 部署架构快速参考** ⭐ | [architecture/02-physical-architecture-quick-ref.md](architecture/02-physical-architecture-quick-ref.md)             | 端口速查、命令速查、环境变量、故障排查 (新增)                                    |
| **02 部署架构架构决策** ⭐ | [architecture/02-physical-architecture-decision.md](architecture/02-physical-architecture-decision.md)               | 五层拓扑、Compose vs K8s、端口分离、能力分级 (ADR-002\~008) (新增)           |
| **02 部署架构商业价值** ⭐ | [architecture/02-physical-architecture-business-value.md](architecture/02-physical-architecture-business-value.md)   | 上线速度、成本控制、安全合规、故障定位、扩展灵活性 (新增)                              |
| **03 技术架构详细说明** ⭐ | [architecture/03-technical-architecture-detail.md](architecture/03-technical-architecture-detail.md)                 | 七层技术栈、前后端版本、依赖关系、Profile 能力矩阵 (新增)                          |
| **03 技术架构快速参考** ⭐ | [architecture/03-technical-architecture-quick-ref.md](architecture/03-technical-architecture-quick-ref.md)           | 版本速查、命令速查、性能调优、监控指标 (新增)                                    |
| **03 技术架构架构决策** ⭐ | [architecture/03-technical-architecture-decision.md](architecture/03-technical-architecture-decision.md)             | WebFlux、MyBatis-Plus、Vue 3、二级缓存、Profile (ADR-009\~015) (新增) |
| **03 技术架构商业价值** ⭐ | [architecture/03-technical-architecture-business-value.md](architecture/03-technical-architecture-business-value.md) | 开发效率、性能并发、资源成本、可观测性、稳定性、扩展性 (新增)                            |
| **业务流程文档**        | [architecture/business-flows.md](architecture/business-flows.md)                                                     | 登录认证、权限校验、工作流、文件上传流程详解                                      |
| **数据库设计文档**       | [architecture/database-design.md](architecture/database-design.md)                                                   | 表结构设计、索引优化、主从复制配置                                           |
| **部署架构文档**        | [architecture/deployment-architecture.md](architecture/deployment-architecture.md)                                   | Kubernetes/Docker部署方案、HPA扩缩容、高可用保障                          |
| **网络架构文档**        | [architecture/network-architecture.md](architecture/network-architecture.md)                                         | 网络分区、防火墙规则、DDoS防护、SSL/TLS配置                                 |
| **后端技术栈文档**       | [architecture/backend-technology-stack.md](architecture/backend-technology-stack.md)                                 | Spring Boot/WebFlux/MyBatis-Plus等后端技术栈详解、技术选型理由、性能优化策略      |
| **前端技术栈文档**       | [architecture/frontend-technology-stack.md](architecture/frontend-technology-stack.md)                               | Vue 3/Vite 6/TypeScript/Pinia/Element Plus前端技术栈详解、开发规范、最佳实践 |
| **基础设施组件文档**      | [architecture/infrastructure-components.md](architecture/infrastructure-components.md)                               | 应用服务、数据存储、监控可观测、容器编排、CI/CD等基础设施组件详解                         |
| **数据流转架构文档**      | [architecture/data-flow-architecture.md](architecture/data-flow-architecture.md)                                     | 五种核心数据流、数据一致性保障、性能优化建议                                      |
| **消息队列架构文档**      | [architecture/mq-architecture.md](architecture/mq-architecture.md)                                                   | RocketMQ集群架构、事务消息、高可用容灾、性能优化                                |
| **Redis看门狗机制**    | [architecture/redis-watchdog-mechanism.md](architecture/redis-watchdog-mechanism.md)                                 | Redis分布式锁的自动续期机制                                            |

### 高级特性文档

| 文档名称             | 文件路径                                                                                                                                           | 说明                      |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| **自动重试策略**       | [architecture/advanced-features/auto-retry-strategy.md](architecture/advanced-features/auto-retry-strategy.md)                                 | HTTP、MQ、定时任务的重试策略       |
| **多级缓存架构**       | [architecture/advanced-features/multi-level-cache-architecture.md](architecture/advanced-features/multi-level-cache-architecture.md)           | Caffeine + Redis 两级缓存设计 |
| **安全防护体系**       | [architecture/advanced-features/security-protection-system.md](architecture/advanced-features/security-protection-system.md)                   | JWT认证、RBAC权限、数据加密、审计日志  |
| **Sentinel流控熔断** | [architecture/advanced-features/sentinel-rate-limit-circuit-breaker.md](architecture/advanced-features/sentinel-rate-limit-circuit-breaker.md) | Sentinel规则配置和熔断降级       |

### 运维文档

| 文档名称         | 文件路径                                                                                                 | 说明                                         |
| ------------ | ---------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **部署指南**     | [operations/deployment.md](operations/deployment.md)                                                 | 生产环境部署步骤                                   |
| **配置说明**     | [operations/configuration.md](operations/configuration.md)                                           | 配置项详解                                      |
| **监控指南**     | [operations/monitoring.md](operations/monitoring.md)                                                 | Prometheus/Grafana监控配置                     |
| **监控大盘配置** ⭐ | [operations/monitoring-dashboard-configuration.md](operations/monitoring-dashboard-configuration.md) | Prometheus配置、Grafana面板、告警规则、运维操作、最佳实践 (新增) |
| **部署检查清单**   | [operations/deployment-checklist.md](operations/deployment-checklist.md)                             | 上线前检查项                                     |

### 开发规范文档

| 文档名称        | 文件路径                                                                                       | 说明                   |
| ----------- | ------------------------------------------------------------------------------------------ | -------------------- |
| **后端开发规范**  | [standards/backend-development-standards.md](standards/backend-development-standards.md)   | Java/Spring Boot开发规范 |
| **前端开发规范**  | [standards/frontend-development-standards.md](standards/frontend-development-standards.md) | Vue/TypeScript开发规范   |
| **API路由映射** | [api/route-mapping.md](api/route-mapping.md)                                               | 前后端API路由对照表          |

### 快速开始文档

| 文档名称       | 文件路径                                    | 说明        |
| ---------- | --------------------------------------- | --------- |
| **快速开始**   | [QUICK\_START.md](QUICK_START.md)       | 5分钟快速启动项目 |
| **README** | [README.md](README.md)                  | 项目介绍和功能特性 |
| **最佳实践**   | [BEST\_PRACTICES.md](BEST_PRACTICES.md) | 开发和运维最佳实践 |

***

## 🎯 按场景查找

### 场景1: 新人入职 - 快速了解系统

**推荐阅读顺序**:

1. [README.md](README.md) - 项目介绍
2. [QUICK\_START.md](QUICK_START.md) - 快速启动
3. [系统架构设计文档](architecture/system-architecture.md) - 整体架构
4. [DDD 四层逻辑架构图](diagrams/01_logical_architecture.svg) - 分层设计
5. [全栈技术选型全景图](diagrams/03_technical_architecture.svg) - 技术栈

### 场景2: 部署上线 - 生产环境部署

**推荐阅读顺序**:

1. [部署架构文档](architecture/deployment-architecture.md) - 部署方案
2. [部署架构拓扑图](diagrams/13_deployment_architecture.svg) - 部署拓扑
3. [网络架构文档](architecture/network-architecture.md) - 网络配置
4. [网络架构图](diagrams/14_network_architecture.svg) - 网络分区
5. [部署检查清单](operations/deployment-checklist.md) - 上线检查

### 场景3: 性能优化 - 排查性能瓶颈

**推荐阅读顺序**:

1. [数据流转架构图](diagrams/15_data_flow_architecture.svg) - 数据流分析
2. [多级缓存架构图](diagrams/10_multi_level_cache_architecture.svg) - 缓存优化
3. [监控体系架构图](diagrams/17_monitoring_architecture.svg) - 监控指标
4. [监控指南](operations/monitoring.md) - 监控配置

### 场景4: 故障排查 - 定位系统问题

**推荐阅读顺序**:

1. [监控指南](operations/monitoring.md) - 查看监控指标
2. [微服务治理架构图](diagrams/18_microservice_governance.svg) - 服务调用链
3. [Sentinel流控熔断图](diagrams/09_sentinel_rate_limit_circuit_breaker.svg) - 限流熔断
4. [自动重试策略流程图](diagrams/11_auto_retry_strategy_flow.svg) - 重试机制

### 场景5: 功能开发 - 实现新业务功能

**推荐阅读顺序**:

1. [后端开发规范](standards/backend-development-standards.md) - 编码规范
2. [前端开发规范](standards/frontend-development-standards.md) - 前端规范
3. [业务流程文档](architecture/business-flows.md) - 业务流程
4. [数据库设计文档](architecture/database-design.md) - 数据库设计
5. [API路由映射](api/route-mapping.md) - API设计

### 场景6: 安全审计 - 系统安全检查

**推荐阅读顺序**:

1. [安全防护体系图](diagrams/12_security_protection_system.svg) - 安全机制
2. [安全防护体系文档](architecture/advanced-features/security-protection-system.md) - 安全详解
3. [网络架构文档](architecture/network-architecture.md) - 网络安全
4. [网络架构图](diagrams/14_network_architecture.svg) - 防火墙规则

### 场景7: 技术选型 - 了解技术栈组成 ⭐

**推荐阅读顺序**:

1. [03 技术架构全景图 - 详细说明](architecture/03-technical-architecture-detail.md) - 七层技术栈详解
2. [03 技术架构全景图 - 架构决策](architecture/03-technical-architecture-decision.md) - 技术选型理由 (ADR-009\~015)
3. [技术生态全景图](diagrams/21_technology_ecosystem.svg) - 60+技术组件全貌
4. [后端技术栈架构图](diagrams/19_backend_technology_stack.svg) - 后端技术分层
5. [前端技术栈架构图](diagrams/22_frontend_technology_stack.svg) - 前端技术分层

### 场景8: 基础设施规划 - 了解组件部署 ⭐

**推荐阅读顺序**:

1. [基础设施组件架构图](diagrams/20_infrastructure_components.svg) - 6层基础设施全貌
2. [基础设施组件文档](architecture/infrastructure-components.md) - 组件配置与高可用方案
3. [部署架构拓扑图](diagrams/13_deployment_architecture.svg) - Kubernetes部署拓扑
4. [监控体系架构图](diagrams/17_monitoring_architecture.svg) - 监控组件部署

### 场景9: 前后端协作开发 - 理解交互流程 ⭐

**推荐阅读顺序**:

1. [前后端交互架构图](diagrams/23_frontend_backend_interaction.svg) - 完整交互流程
2. [前端技术栈文档](architecture/frontend-technology-stack.md) - 前端实现细节
3. [后端技术栈文档](architecture/backend-technology-stack.md) - 后端实现细节
4. [API路由映射](api/route-mapping.md) - API接口对照
5. [前端开发规范](standards/frontend-development-standards.md) - 前端编码规范

### 场景10: 监控体系配置 - 搭建监控大盘 ⭐

**推荐阅读顺序**:

1. [Grafana监控面板架构图](diagrams/24_grafana_dashboard_architecture.svg) - 66个监控面板分组
2. [Prometheus监控体系架构图](diagrams/25_prometheus_monitoring_architecture.svg) - 监控指标采集架构
3. [监控大盘配置文档](operations/monitoring-dashboard-configuration.md) - Prometheus/Grafana配置详解
4. [监控体系架构图](diagrams/17_monitoring_architecture.svg) - 三大监控支柱
5. [监控指南](operations/monitoring.md) - 运维监控实践

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
| **Docker**       | 基础设施组件架构图、部署架构文档                                       |
| **Prometheus**   | 监控体系架构图、基础设施组件文档                                       |
| **Grafana**      | 监控体系架构图、基础设施组件文档                                       |

***

## 📝 文档更新记录

| 日期         | 版本   | 更新内容                                                                     | 更新人               |
| ---------- | ---- | ------------------------------------------------------------------------ | ----------------- |
| 2026-09-04 | v1.3 | 新增2张监控架构图及1篇配套文档：Grafana监控面板架构图、Prometheus监控体系架构图，新增监控大盘配置文档，总计25张架构图 ⭐  | 架构团队 |
| 2026-09-04 | v1.2 | 新增2张前端架构图及1篇配套文档：前端技术栈架构图、前后端交互架构图，新增前端技术栈文档，总计23张架构图                    | 架构团队 |
| 2026-09-04 | v1.1 | 新增3张技术栈架构图及2篇配套文档：后端技术栈架构图、基础设施组件架构图、技术生态全景图，新增后端技术栈文档、基础设施组件文档，总计21张架构图 | 架构团队 |
| 2026-09-04 | v1.0 | 新增6张架构图及4篇配套文档：部署架构、网络架构、数据流转、消息队列、监控体系、微服务治理                            | 架构团队 |
| 2026-09-03 | v0.9 | 创建12张核心架构图及基础文档                                                          | 架构团队 |

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
