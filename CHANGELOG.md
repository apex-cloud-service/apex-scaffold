# 变更日志

本项目遵循 [语义化版本规范](https://semver.org/lang/zh-CN/)。

## [1.1.2] - 2026-08-27

### Added
- 代码生成器（模板开发功能）：新增 apex-generator 独立模块，FreeMarker 模板引擎按表结构一键生成 DDD 五层代码（entity/repository/PO/Mapper/Converter/RepositoryImpl/Service/DTO/Handler + 路由片段 + 迁移片段 + 测试骨架），CLI 入口 scripts/codegen/generate.sh
- 系统管理种子数据：V2 迁移预置 24 个 system:* 权限码与系统管理菜单树（Flyway 管理）

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
