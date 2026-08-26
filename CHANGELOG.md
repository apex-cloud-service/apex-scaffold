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
- README重写,突出商业化定位
- 架构文档优化,增加扩展指南

### Removed
- 移除业务耦合代码(待后续迭代)

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
