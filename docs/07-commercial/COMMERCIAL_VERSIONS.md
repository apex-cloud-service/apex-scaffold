# 商业化版本说明

Apex Scaffold 提供三个商业化版本,满足不同规模企业的需求。

## 📊 版本对比

| 功能特性 | 基础版 (Basic) | 标准版 (Standard) | 企业版 (Enterprise) |
|---------|---------------|------------------|-------------------|
| **价格** | 免费开源 | ¥9,999/年 | ¥49,999/年 |
| **适用场景** | 个人项目、小型应用 | 中型企业、创业公司 | 大型企业、高并发系统 |
| **技术支持** | 社区支持 | 邮件支持 (5工作日响应) | 专属客服 (2小时响应) |
| **更新频率** | 季度更新 | 月度更新 | 每周更新 + 紧急补丁 |
| **SLA保障** | ❌ | 99.5% | 99.9% |

### 🔐 核心功能

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| JWT 认证 | ✅ | ✅ | ✅ |
| 统一响应格式 | ✅ | ✅ | ✅ |
| 参数校验 | ✅ | ✅ | ✅ |
| API 文档 (Swagger) | ✅ | ✅ | ✅ |
| WebFlux 函数式编程 | ✅ | ✅ | ✅ |
| MyBatis-Plus | ✅ | ✅ | ✅ |

### 🛡️ 安全与权限

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| RBAC 权限管理 | ❌ | ✅ | ✅ |
| 数据权限控制 | ❌ | ✅ | ✅ |
| XSS 防护 | ✅ | ✅ | ✅ |
| SQL 注入防护 | ✅ | ✅ | ✅ |
| CSRF 防护\* | ❌ | ❌ | ❌ |
| 操作审计日志（安全合规） | ❌ | ❌ | ✅ |
| 机器指纹绑定 | ❌ | ✅ | ✅ |

\* 微信小程序场景下 Spring Security 全局关闭 CSRF，三档行为一致；若后续接入 H5 Web 端可按需按版本开启。

### ⚡ 性能与扩展

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| Redis 缓存 | ❌ | ✅ | ✅ |
| 连接池优化 | ✅ | ✅ | ✅ |
| 多数据源支持 | ❌ | ❌ | ✅ |
| 读写分离 | ❌ | ❌ | ✅ |
| 分布式锁 (Redisson) | ❌ | ❌ | ✅ |

> **实现口径勘误（2026-11-11，以代码为准）**：`RedissonConfig` 当前按
> `spring.profiles.active=dev` 条件装配（且需显式启用 `RedissonProperties`），
> **未按商业档位门控**；上表 Redisson 行属商业策略规划，与当前代码实现不一致，
> 购买决策请先与维护方确认实际门控实现。

### 📡 消息与集成

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| RocketMQ 集成 | ❌ | ❌ | ✅ |
| 微信 SDK 集成 | ❌ | ✅ | ✅ |
| 对象存储 (MinIO/TOS) | ❌ | ✅ | ✅ |
| 邮件服务 | ❌ | ✅ | ✅ |
| 短信服务 | ❌ | ✅ | ✅ |

### 🎛️ 系统管理（开箱即用）

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| 管理后台控制台 | ✅ | ✅ | ✅ |
| 菜单管理（树 + 按钮权限） | ✅ | ✅ | ✅ |
| 字典管理 | ✅ | ✅ | ✅ |
| 参数配置 | ✅ | ✅ | ✅ |
| 定时任务调度（自研 Cron） | ✅ | ✅ | ✅ |
| 登录日志审计 | ✅ | ✅ | ✅ |
| 图形验证码 | ✅ | ✅ | ✅ |
| 代码生成器（DDD 五层一键生成） | ✅ | ✅ | ✅ |

### 🧰 开发体验

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| WebFlux 函数式端点 | ✅ | ✅ | ✅ |
| DDD 五层架构 | ✅ | ✅ | ✅ |
| Knife4j 增强 API 文档 | ✅ | ✅ | ✅ |
| P6Spy SQL 日志（dev） | ✅ | ✅ | ✅ |
| 统一响应格式 | ✅ | ✅ | ✅ |
| 国际化 (i18n) | ✅ | ✅ | ✅ |
| 参数校验 (JSR-380) | ✅ | ✅ | ✅ |

### 📊 监控与运维

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| Prometheus 监控 | ❌ | ✅ | ✅ |
| Grafana 仪表盘 | ❌ | ✅ | ✅ |
| 告警规则（钉钉通知） | ❌ | ✅ | ✅ |
| Zipkin 链路追踪 | ❌ | ❌ | ✅ |
| Sentinel 限流熔断\*\* | ❌ | ✅ | ✅ |
| Resilience4j 容错 | ❌ | ✅ | ✅ |
| 健康检查 | ✅ | ✅ | ✅ |

\*\* Sentinel 依赖 `apex.features.monitoring` 属性开关，standard/enterprise 均为 true，basic 为 false；与 Prometheus 共用同一条 Maven Profile 控制。

### 🗄️ 数据库

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| MySQL 支持 | ✅ | ✅ | ✅ |
| Flyway 数据库迁移（各档位默认启用；system basic 亦为 MySQL + Flyway） | ✅ | ✅ | ✅ |
| 逻辑删除 | ✅ | ✅ | ✅ |
| 分页查询 | ✅ | ✅ | ✅ |

### 📦 其他功能

| 功能 | Basic | Standard | Enterprise |
|------|-------|----------|------------|
| Excel 导入导出 | ❌ | ✅ | ✅ |
| PDF 生成 | ❌ | ❌ | ✅ |
| CORS 跨域 | ✅ | ✅ | ✅ |
| Podman 支持 | ✅ | ✅ | ✅ |
| Kubernetes 部署脚本 | ❌ | ❌ | ✅ |

## 🔧 三档差异实现机制（v1.2.0）

三档版本通过**编译期 Maven Profile + 运行期 Spring 条件门控**双层控制实现功能差异，保证：
- **basic 构建产物不包含高级依赖 jar**（真裁剪，BOOT-INF/lib 中不会出现 RocketMQ / Sentinel / Zipkin）
- **运行期属性开关与 Spring Bean 装配对齐**（`apex.features.*` 为 false 时 Bean 不注册）

### 属性矩阵（Maven Profile → 运行期 `apex.features.*`）

| 属性 | Basic | Standard | Enterprise | 说明 |
|------|-------|----------|------------|------|
| `apex.features.jwt-auth` | true | true | true | JWT 认证（三档均启用） |
| `apex.features.rbac` | **false** | true | true | RBAC 权限管理 + `@PreAuthorize` 方法级安全 |
| `apex.features.cache` | **false** | true | true | Caffeine L1 + Redis L2 多级缓存（Redisson 装配口径见上文勘误，不随本开关门控） |
| `apex.features.message-queue` | false | **false** | true | RocketMQ 集成（生产者/消费者/死信队列处理/幂等切面） |
| `apex.features.monitoring` | **false** | true | true | Sentinel 限流熔断 + Prometheus + Grafana + 钉钉告警 |
| `apex.features.tracing` | false | **false** | true | Zipkin Brave 全链路追踪上报 |

### 条件门控生效原理

1. **Maven Profile 编译裁剪**：
   - `basic` profile 在顶层 `dependencyManagement` 中把高级依赖声明为 `<optional>true</optional>`；
   - 业务库（如 `apex-system`）的 `basic` profile 对上游依赖添加 `<exclusions>`；
   - `spring-boot-maven-plugin` 打包时可选依赖 / 被排除依赖不进入 `BOOT-INF/lib`。

2. **Spring 条件装配（双条件门控）**：
   - `@ConditionalOnProperty(prefix = "apex.features", name = "xxx", havingValue = "true", matchIfMissing = true)` 控制属性开关；
   - `@ConditionalOnClass(SomeAdvancedClass.class)` 兜底：当 basic 构建缺少类时即使属性被强制设为 true 也不会装配，避免 NoClassDefFoundError。
   - 关键配置类：`RbacMethodSecurityConfig`、`MultiLevelCacheConfig`、`SentinelConfig`、`TraceConfig`、`MqProducerService`/`UserMessageListener` 等均按此模式配置。

3. **RBAC 特殊处理**：
   - JWT 认证链（SecurityConfig）三档始终激活；
   - `@EnableReactiveMethodSecurity` 独立封装为 `RbacMethodSecurityConfig`，只有 `rbac=true` 时激活；
   - `rbac=false`（basic）时管理端路径降级为「仅需认证」，不做 ADMIN/SUPER_ADMIN 角色校验。

## 💰 定价策略

### 基础版 (Basic)
- **价格**: 免费
- **许可证**: MIT License
- **获取方式**: GitHub 直接下载
- **适合人群**: 
  - 个人开发者学习
  - 小型项目快速原型开发
  - 预算有限的初创团队

### 标准版 (Standard)
- **价格**: ¥9,999/年 或 ¥29,999 永久授权
- **包含内容**:
  - 所有标准版功能
  - 12个月技术支持
  - 月度功能更新
  - 优先 Issue 处理
- **适合人群**:
  - 中型企业信息化项目
  - 有稳定技术团队的创业公司
  - 需要生产级可靠性的项目

### 企业版 (Enterprise)
- **价格**: ¥49,999/年 或 ¥149,999 永久授权
- **包含内容**:
  - 所有企业版功能
  - 专属技术顾问
  - 2小时紧急响应
  - 定制化开发支持
  - 源码级问题排查
  - 年度架构评审
  - K8s 部署最佳实践
- **适合人群**:
  - 大型企业核心业务系统
  - 高并发、高可用要求场景
  - 需要深度定制的企业
  - 金融、医疗等强监管行业

## 🎯 升级路径

```
基础版 → 标准版 → 企业版
```

- 可随时升级到更高版本
- 已支付费用可抵扣新版本费用
- 代码兼容,无需重构

## 📞 购买方式

### 在线购买
访问官网: https://apex-scaffold.example.com/pricing

### 企业采购
- 邮箱: sales@apex-scaffold.example.com
- 电话: 400-XXX-XXXX
- 支持对公转账、合同签署

### 试用申请
- 标准版: 30天免费试用
- 企业版: 14天 POC 验证
- 联系销售获取试用 License

## ❓ 常见问题

### Q: 不同版本的代码有什么区别?
A: 通过 Maven Profile 控制功能模块的引入。基础版只包含核心依赖,企业版包含全部高级功能。编译时根据选择的 Profile 自动裁剪不需要的依赖。

### Q: 可以从基础版升级到标准版吗?
A: 可以。只需修改 `pom.xml` 中的 Profile 配置,重新编译即可。所有代码完全兼容。

### Q: 技术支持包括哪些内容?
A: 
- 标准版: 邮件咨询、Bug 修复、使用指导
- 企业版: 专属客服、远程协助、源码级调试、架构咨询、定制开发

### Q: 是否提供培训服务?
A: 企业版客户可享受 2 天的现场技术培训(差旅另计)。标准版提供在线视频教程和文档。

### Q: 开源协议是什么?
A: Apex Scaffold 采用双轨许可证。基础版采用 [MIT License](../../LICENSE)，可自由商用。标准版和企业版为商业授权（[LICENSE-COMMERCIAL.md](../../LICENSE-COMMERCIAL.md)），禁止二次分发，需购买 License 授权文件激活。

## 📝 许可证说明

- **基础版**: [MIT License](../../LICENSE) - 允许商业使用、修改、分发
- **标准版/企业版**: [商业授权](../../LICENSE-COMMERCIAL.md) - 仅限购买方内部使用,禁止转售或开源
- **授权机制**: 商业版内置 License 验证，未配置有效授权时商业功能自动降级关闭（基础功能不受影响），详见 [LICENSE_MANAGEMENT.md](license-management.md)
- **机器指纹绑定**（v1.2.1）: 标准版/企业版签发 License 时可绑定客户机器指纹（MAC + CPU + 主板 SHA-256），防止 License 复制到未授权机器；容器/K8s 部署支持 `apex.license.machine-fingerprint-override` 手动覆盖，详见 [LICENSE_MANAGEMENT.md](license-management.md)

详细条款请查看 [LICENSE](../../LICENSE) 与 [LICENSE-COMMERCIAL.md](../../LICENSE-COMMERCIAL.md)。
