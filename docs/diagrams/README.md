# Apex Scaffold SVG 架构图表使用指南

> 本索引登记部分既有 SVG 和 Mermaid 源文件；编号不是文件总数，登记不代表图表验证通过。

本目录包含 Apex Scaffold 项目的既有 SVG 图表和 `sequence/` 下的 Mermaid 源文件。部分图表保留历史设计，接口、配置和运行边界应以当前代码及配套文档为准。本轮同步索引，尚未重新渲染 Mermaid 或执行 SVG 全量验证。

---

## 📊 图表总览

### 图表分类

| 分类           | 数量  | 图表编号       | 用途说明             |
| ------------ | --- | ---------- | ---------------- |
| 核心架构图        | 3张  | 01-03      | DDD分层、部署拓扑、技术选型 |
| 业务流程图        | 4张  | 04-07      | 登录认证、权限校验、工作流、文件上传 |
| 基础设施图        | 5张  | 08-12      | 数据库、缓存、限流、数据权限、安全 |
| 部署与微服务治理图    | 6张  | 13-18      | Docker/K8s、CI/CD、Nacos、Zipkin、Gateway |
| 前端技术栈与响应式编程图 | 5张  | 19-23      | Vue 3、Axios、WebFlux、i18n、OpenAPI |
| 监控可观测性图      | 2张  | 24-25      | Prometheus/Grafana、Loki日志 |
| 高级架构与技术栈图    | 5张  | 26-29      | 容灾、消息队列、全文搜索、技术栈总览 |
| 登记范围 | 部分登记 | 01-29、84-86、92 | 其他图表见架构索引和时序图索引 |

---

## 🎨 图表特性

### 设计原则

1. **深色企业级配色**: 使用专业的深色配色方案
   - 主品牌色: #4f46e5 (深靛蓝)
   - 应用层: #0891b2 (青色)
   - 领域层: #059669 (深绿)
   - 基础设施: #d97706 (琥珀)
   - 文字色: #0f172a / #475569

2. **跨平台兼容**: 移除所有 CSS 变量,使用静态颜色值
   - ✅ 支持 Chrome、Firefox、Safari、Edge
   - ✅ 支持 VSCode、Obsidian、Typora
   - ✅ 支持 GitHub、GitLab、Notion 在线预览
   - ✅ 支持打印输出和PDF转换

3. **核对实现边界**: 技术栈版本以 `pom.xml` 和实际配置为准
   - 图内数量、性能指标和部署拓扑可能属于历史记录或设计目标
   - 图表存在不表示对应服务已部署或联调通过
   - 当前统一授权契约见下方入口，旧图不覆盖全部当前行为

4. **专业视觉**: SVG矢量图形,高清缩放无损
   - 1400×700\~900 标准画布尺寸
   - 32px标题、14px小标题、11px正文、10px代码
   - 8px圆角、2px边框、统一间距

---

## 📂 图表清单

### 01-03 核心架构图

| 编号 | 文件名                               | 标题            | 说明                                 |
| -- | --------------------------------- | ------------- | ---------------------------------- |
| 01 | `01_logical_architecture.svg`     | DDD四层逻辑架构     | 接口/应用/领域/基础设施四层,32 Handler/34 Service |
| 02 | `02_physical_architecture.svg`    | 网络物理部署架构      | 五层拓扑(客户端/DMZ/应用/数据/监控),端口8080/8081    |
| 03 | `03_technical_architecture.svg`   | 全栈技术选型全景图     | 60+技术组件,Spring Boot 3.5.15 / Vue 3.5.13 |

### 04-07 业务流程图

| 编号 | 文件名                              | 标题         | 说明                     |
| -- | -------------------------------- | ---------- | ---------------------- |
| 04 | `04_login_authentication_flow.svg`         | JWT登录认证流程  | Access+Refresh Token双Token机制 |
| 05 | `05_permission_check_flow.svg`   | RBAC权限校验流程 | 三层权限模型,5张关联表          |
| 06 | `06_workflow_approval_flow.svg`  | 三级审批工作流    | DAG引擎,回退/转办/委派/挂起      |
| 07 | `07_file_upload_flow.svg`        | 文件分片上传流程   | 5MB分片,秒传/断点续传/MinIO存储  |

### 08-12 基础设施图

| 编号 | 文件名                                   | 标题        | 说明                     |
| -- | ------------------------------------- | --------- | ---------------------- |
| 08 | `08_database_er_diagram.svg`          | 数据库ER图    | 40+表结构,Flyway V1-V26完整迁移历史 |
| 09 | `09_multi_level_cache_architecture.svg` | 多级缓存架构    | Caffeine L1 + Redis L2,80-95%命中率 |
| 10 | `10_rate_limit_circuit_breaker.svg`   | 限流熔断机制    | Sentinel+Resilience4j,三档熔断策略 |
| 11 | `11_data_permission_filter.svg`       | 数据权限过滤    | @DataScope AOP + MyBatis-Plus SQL注入 |
| 12 | `12_security_protection_system.svg`   | 安全防护体系    | 7层防护:防重放/幂等/XSS/SQL注入/审计日志 |

### 13-18 部署与微服务治理图

| 编号 | 文件名                                      | 标题                   | 说明                            |
| -- | ---------------------------------------- | -------------------- | ----------------------------- |
| 13 | `13_docker_compose_local_dev.svg`        | Docker Compose本地开发环境 | 一键启动完整技术栈,健康检查+数据持久化          |
| 14 | `14_kubernetes_production_deployment.svg` | Kubernetes生产部署架构     | HPA自动伸缩,ConfigMap/Secret,滚动发布 |
| 15 | `15_cicd_pipeline.svg`                   | CI/CD流水线架构           | GitHub Actions,安全扫描+多环境部署     |
| 16 | `16_nacos_service_discovery.svg`         | Nacos服务注册与发现         | 服务注册+配置中心,3节点Raft集群          |
| 17 | `17_zipkin_distributed_tracing.svg`      | Zipkin分布式链路追踪        | Micrometer Tracing,10%采样率    |
| 18 | `18_spring_cloud_gateway.svg`            | Spring Cloud Gateway  | 动态路由+限流熔断+跨域CORS             |

### 19-23 前端技术栈与响应式编程图

| 编号 | 文件名                                    | 标题             | 说明                            |
| -- | -------------------------------------- | -------------- | ----------------------------- |
| 19 | `19_frontend_vue3_architecture.svg`    | 前端Vue3组件架构     | Composition API + Pinia + Element Plus |
| 20 | `20_axios_request_interceptor.svg`     | Axios请求拦截与封装   | 请求/响应拦截器,JWT自动续期,防重复提交        |
| 21 | `21_webflux_reactive_programming.svg`  | WebFlux响应式编程    | Reactor 3,Mono/Flux操作符,线程池隔离   |
| 22 | `22_i18n_internationalization.svg`     | 国际化i18n多语言支持   | MessageSource + Vue-i18n,错误码国际化 |
| 23 | `23_openapi_documentation.svg`         | OpenAPI文档生成     | SpringDoc + Swagger UI在线调试     |

### 24-25 监控可观测性图

| 编号 | 文件名                                         | 标题                    | 说明                      |
| -- | ------------------------------------------- | --------------------- | ----------------------- |
| 24 | `24_prometheus_grafana_monitoring.svg`      | Prometheus+Grafana监控体系 | 66个监控面板,apex_*指标前缀,告警规则 |
| 25 | `25_loki_log_aggregation.svg`               | Loki日志聚合系统            | Fluent Bit采集,LogQL查询,7天保留 |

### 26-29 高级架构与技术栈图

| 编号 | 文件名                                         | 标题             | 说明                       |
| -- | ------------------------------------------- | -------------- | ------------------------ |
| 26 | `26_disaster_recovery_high_availability.svg` | 容灾与高可用架构       | 多副本+故障自愈+异地多活,99.95%可用性 |
| 27 | `27_rocketmq_message_queue.svg`             | RocketMQ消息队列    | 顺序/延迟/事务消息,幂等消费+死信队列    |
| 28 | `28_meilisearch_fulltext_search.svg`        | Meilisearch全文搜索 | CJK分词+拼音搜索,毫秒级响应        |
| 29 | `29_technology_stack_overview.svg`          | 技术栈总览与版本管理     | 60+组件,BOM管理,依赖冲突解决      |

### 当前认证与统一授权入口

| 文件 | 用途与边界 |
| --- | --- |
| [84_security_filter_chain.svg](84_security_filter_chain.svg) | 既有安全过滤器链图，运行时顺序以安全配置为准 |
| [85_auth_center_architecture.svg](85_auth_center_architecture.svg) | 既有认证中心架构图，主应用与认证中心是独立 Spring ApplicationContext |
| [86_token_lifecycle_management.svg](86_token_lifecycle_management.svg) | 既有 Token 生命周期图，认证中心刷新会撤销旧 Refresh Token 并签发新 Token 对 |
| [业务流程与统一授权时序](../architecture/business-flows.md) | 本轮按当前代码同步的登录、登出和远程授权流程；Mermaid 未重新渲染 |
| [时序图索引](sequence/README.md) | Mermaid 文件清单及旧权限缓存图的适用边界 |
| [统一授权配置](../05-operations/configuration.md#认证中心统一授权) | HTTPS、显式映射、读取账号 Token、缓存和失败策略 |

84–86 的以上 SVG 在 `sequence/` 下没有同名 Mermaid 源文件；同编号的其他 `.mmd` 是不同主题，不能互作生成源。30–39 的 OAuth2、SSO、Keycloak 图为既有专题设计，不能据此声称主应用登录已迁移到认证中心。图 31 的本地权限查询、1 小时缓存和图 67 的读快照降级不代表当前远程统一授权策略：统一授权默认关闭，启用后走 HTTPS，缓存默认关闭、TTL 为 0–30 秒，失败拒绝且不使用过期缓存或本地权限回退。

### 92 办公平台集成

| 编号 | 文件名                                         | 标题             | 说明                       |
| -- | ------------------------------------------- | -------------- | ------------------------ |
| 92 | `92_office_platform_architecture.svg`       | 办公平台集成架构      | 飞书/钉钉/企业微信,端口-适配器,消息/审批/日历/文档四大子域,多租户隔离 |

> 注：本清单当前仅完整登记至 29 号，30–83、87–91 号图表（工作流、多租户、缓存、消息、License 等）未在本清单逐项登记。

---

## 💡 使用建议

### 场景1: 新人入职 - 快速了解系统

**推荐顺序**:
1. `01_logical_architecture.svg` - 理解DDD四层分层设计
2. `03_technical_architecture.svg` - 了解技术栈全貌
3. `02_physical_architecture.svg` - 理解部署拓扑
4. `04_login_authentication_flow.svg` - 理解登录认证流程
5. `19_frontend_vue3_architecture.svg` - 理解前端架构(前端开发)

### 场景2: 生产部署 - 上线环境配置

**推荐顺序**:
1. `13_docker_compose_local_dev.svg` - 理解本地开发环境
2. `14_kubernetes_production_deployment.svg` - 理解K8s生产部署
3. `15_cicd_pipeline.svg` - 理解CI/CD流水线
4. `24_prometheus_grafana_monitoring.svg` - 配置监控大盘
5. `26_disaster_recovery_high_availability.svg` - 理解容灾方案

### 场景3: 性能优化 - 排查瓶颈问题

**推荐顺序**:
1. `09_multi_level_cache_architecture.svg` - 检查缓存命中率
2. `10_rate_limit_circuit_breaker.svg` - 检查限流熔断状态
3. `17_zipkin_distributed_tracing.svg` - 分析调用链路性能
4. `21_webflux_reactive_programming.svg` - 优化响应式流水线
5. `24_prometheus_grafana_monitoring.svg` - 查看监控指标

### 场景4: 前后端协作 - 理解交互流程

**推荐顺序**:
1. `19_frontend_vue3_architecture.svg` - 前端组件架构
2. `20_axios_request_interceptor.svg` - 请求拦截流程
3. `18_spring_cloud_gateway.svg` - API网关路由
4. `04_login_authentication_flow.svg` - 登录认证完整流程
5. `23_openapi_documentation.svg` - API文档规范

### 场景5: 安全审计 - 系统安全检查

**推荐顺序**:
1. `12_security_protection_system.svg` - 7层安全防护体系
2. `04_login_authentication_flow.svg` - JWT认证机制
3. `05_permission_check_flow.svg` - RBAC权限模型
4. `11_data_permission_filter.svg` - 数据权限过滤
5. `02_physical_architecture.svg` - 网络安全分区

---

## 🔧 查看工具推荐

### 在线查看
- **GitHub/GitLab**: 代码仓库直接预览
- **VS Code**: 内置SVG预览
- **浏览器**: 直接拖入Chrome/Firefox/Safari

### 本地编辑器
- **Obsidian**: 双向链接笔记,支持SVG内嵌
- **Typora**: Markdown编辑器,支持SVG预览
- **VS Code**: 安装"SVG Preview"扩展

### 专业工具
- **Figma**: 导入SVG进行二次设计
- **Adobe Illustrator**: 专业矢量图编辑
- **Inkscape**: 开源SVG编辑工具

### PDF转换
```bash
# 使用 librsvg (推荐)
rsvg-convert -f pdf -o output.pdf input.svg

# 使用 Inkscape
inkscape input.svg --export-pdf=output.pdf

# 使用 Chrome headless
chrome --headless --print-to-pdf=output.pdf input.svg
```

---

## 📝 技术规格

### 文件规格

| 属性     | 值                  | 说明              |
| ------ | ------------------ | --------------- |
| 格式     | SVG 1.1            | 标准SVG格式         |
| 编码     | UTF-8              | 支持中文字符          |
| 画布尺寸   | 1400×700\~900      | 16:9或2:1宽高比      |
| 文件大小   | 5\~15 KB           | 平均10KB,高效加载     |
| 颜色模式   | RGB                | 静态颜色值,无CSS变量    |
| 字体     | sans-serif / monospace | 系统默认字体,确保跨平台兼容性 |

### 颜色规范

| 用途     | 颜色代码      | 说明    |
| ------ | --------- | ----- |
| 标题文字   | #0f172a   | 深灰色   |
| 次要文字   | #64748b   | 中灰色   |
| 正文文字   | #475569   | 浅灰色   |
| 代码文字   | #2563eb   | 蓝色    |
| 主品牌色   | #4f46e5   | 深靛蓝   |
| 应用层    | #0891b2   | 青色    |
| 领域层    | #059669   | 深绿色   |
| 基础设施层  | #d97706   | 琥珀色   |
| 背景色    | #eff6ff   | 浅蓝背景  |
| 边框     | #3b82f6   | 蓝色边框  |

### 文字规范

| 元素   | 字号   | 字重   | 字体          |
| ---- | ---- | ---- | ----------- |
| 标题   | 32px | 700  | sans-serif  |
| 小标题  | 14px | 500  | sans-serif  |
| 正文   | 11px | 400  | sans-serif  |
| 代码   | 10px | 400  | monospace   |
| 图表标题 | 14px | 600  | sans-serif  |

---

## ⚡ 快速查找

### 按关键词查找

| 关键词          | 相关图表编号         |
| ------------ | -------------- |
| Vue 3        | 19, 20, 22     |
| Spring Boot  | 01, 03, 21     |
| Docker       | 13, 14         |
| Kubernetes   | 14, 26         |
| 监控           | 17, 24, 25     |
| 安全           | 04, 05, 11, 12 |
| 缓存           | 09             |
| 消息队列         | 27             |
| 数据库          | 08             |
| CI/CD        | 15             |
| 网关           | 18             |
| 全文搜索         | 28             |

---

## 📞 支持与反馈

- **项目仓库**: [GitHub/GitLab地址]
- **技术文档**: [../ARCHITECTURE_INDEX.md](../02-architecture/architecture-index.md)
- **问题反馈**: 提交Issue或联系架构团队
- **版本历史**: 查看 `ARCHITECTURE_INDEX.md` 更新记录

---

**文档维护者**: 架构团队  
**最后更新**: 2026-09-10  
**文档版本**: v3.0  
**登记状态**: 部分登记；不声明全量验证通过

---

© 2026 Apex Team · 图表包含历史设计，当前契约以代码、配置和配套文档为准
