# 变更日志

本项目遵循 [语义化版本规范](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 初始商业化版本发布
- MIT开源许可证
- 完整的文档体系(快速开始、配置详解、架构说明、最佳实践)
- 贡献指南和行为准则
- 单元测试框架(JUnit 5 + Mockito + AssertJ)

### Changed
- v2.0 通用化重构：完成业务代码剥离，脚手架仅保留通用能力
- 数据库迁移重建：87 个业务迁移脚本替换为干净的通用基线（V1 27 张通用表）
- 全库品牌统一：配置前缀/指标名/缓存前缀统一为 `apex.*` 命名空间
- 管理后台品牌改造：控制台更名为 Apex Scaffold，移除业务审核视图
- 文档体系重构：模块 README 重写，移除内部工作文档

### Removed
- 移除全部业务耦合代码（兴趣图谱/照片对战/人脸分析/共鸣/雷达等约 500+ 文件）
- 移除业务迁移脚本、业务种子数据与内部拷贝脚本
- 移除原项目名称残留（全库零命中验证）

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
