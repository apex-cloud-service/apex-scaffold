# 📚 Apex Scaffold 文档中心

欢迎来到 Apex Scaffold 文档中心！这里汇集了系统架构、开发指南、API 接口、运维部署等全方位的技术文档。

---

## 📖 文档导航

### [00. 快速开始](./00-getting-started/)

快速入门指南和示例代码，帮助您在 5 分钟内启动项目。

- [快速开始指南](./00-getting-started/quickstart.md) - 5 分钟快速启动
- [示例指南](./00-getting-started/example-guide.md) - 示例代码说明
- [示例总结](./00-getting-started/examples-summary.md) - 示例模块汇总
- [入门架构指南](./00-getting-started/onboarding-guide.md) - 架构快速上手

### [01. 使用指南](./01-guides/)

功能使用指南、最佳实践和问题排查。

- [最佳实践](./01-guides/best-practices.md) - 开发最佳实践
- [开发手册](./01-guides/developer-handbook.md) - 完整开发指南
- [故障排查](./01-guides/troubleshooting.md) - 常见问题解决
- [工作流指南](./01-guides/workflow-guide.md) - 工作流引擎使用
- [数据权限](./01-guides/data-scope-usage.md) - 数据权限过滤
- [SSO 集成](./01-guides/sso-implementation-guide.md) - 单点登录
- [文档搜索](./01-guides/doc-search-guide.md) - 全文搜索

### [02. 架构设计](./02-architecture/)

系统架构、技术选型和设计决策。

- [架构总览](./02-architecture/architecture-overview.md) - 系统架构概览
- [架构演进](./02-architecture/architecture-evolution.md) - 架构演进历程
- [逻辑架构](architecture/01-logical-architecture-detail.md) - DDD 四层架构
- [物理架构](architecture/02-physical-architecture-detail.md) - 部署架构
- [技术架构](architecture/03-technical-architecture-detail.md) - 技术栈全景
- [数据库设计](architecture/database-design.md) - 数据模型设计
- [多级缓存](architecture/advanced-features/multi-level-cache-complete-guide.md) - 缓存架构

### [03. API 文档](./03-api/)

API 接口文档、规范和版本管理。

- [路由映射](_archive/api/route-mapping.md) - 前后端路由对应
- [API 版本管理](./03-api/api-version-management.md) - 版本控制策略
- [SpringDoc 注解](./03-api/springdoc-annotation-templates.md) - API 文档注解
- [数据管理 API](_archive/api/data-management-api.md) - 数据管理接口

**在线文档**: 启动项目后访问
- Swagger UI: http://localhost:8080/swagger-ui.html
- Knife4j UI: http://localhost:8080/doc.html

### [04. 开发文档](./04-development/)

代码生成、开发规范和工具使用。

- [代码生成](./04-development/codegen.md) - DDD 代码生成器
- [JavaDoc 指南](./04-development/javadoc-guide.md) - 文档注释规范
- [Lombok 指南](./04-development/lombok-guide.md) - Lombok 使用
- [JMH 基准测试](./04-development/jmh-benchmark-guide.md) - 性能测试
- [SVG 维护](./04-development/svg-maintenance-guide.md) - 图表维护
- [前端开发](./04-development/frontend/) - 前端开发指南
- [后端开发规范](./04-development/backend-development-standards.md) - 后端规范
- [前端开发规范](./04-development/frontend-development-standards.md) - 前端规范

### [05. 运维部署](./05-operations/)

生产部署、配置管理和监控告警。

- [部署指南](./05-operations/deployment.md) - Docker/K8s 部署
- [配置管理](./05-operations/configuration.md) - 配置项说明
- [安全防护](./05-operations/security-protections.md) - 安全体系
- [高级特性](./05-operations/advanced-features.md) - 高级功能

### [06. 测试文档](./06-testing/)

测试策略、测试报告和质量保证。

- [集成测试指南](./06-testing/integration-test-guide.md) - 集成测试
- [API 测试报告](./06-testing/api-test-report.md) - 接口测试
- [API 测试计划](./06-testing/api-test-plan.md) - 测试计划

### [07. 商业化文档](./07-commercial/)

商业版本、授权管理和销售资料。

- [授权管理](./07-commercial/license-management.md) - 授权系统
- [商业版本](07-commercial/COMMERCIAL_VERSIONS.md) - 版本对比
- [销售资料](./07-commercial/sales/) - 销售支持文档

### [08. 项目管理](./08-project-management/)

项目管理、实施计划和需求管理。

- [P0 系统管理](./08-project-management/p0-system-management.md) - 核心系统
- [脚手架审查](./08-project-management/scaffold-review-guide.md) - 代码审查
- [功能需求](./08-project-management/function-enhancement-requirements.md) - 需求管理
- [实施计划](./08-project-management/plans/) - 各类实施计划

---

## 🎨 架构图表

完整的架构图表位于 [diagrams](./diagrams/) 目录，包含 70+ 张专业 SVG 图表：

- **系统架构**: 逻辑架构、物理架构、技术架构
- **业务流程**: 登录认证、权限校验、工作流审批、文件上传
- **技术架构**: 缓存架构、限流熔断、自动重试、安全防护
- **部署架构**: Docker Compose、Kubernetes、CI/CD 流水线
- **监控体系**: Prometheus、Grafana、Zipkin、日志聚合

详见 [diagrams/README.md](./diagrams/README.md)

---

## 🚀 快速链接

### 新手入门

1. [快速开始](./00-getting-started/quickstart.md) - 5 分钟启动项目
2. [架构总览](./02-architecture/architecture-overview.md) - 了解系统架构
3. [示例代码](./00-getting-started/example-guide.md) - 学习示例代码
4. [最佳实践](./01-guides/best-practices.md) - 开发规范

### 开发者

1. [开发手册](./01-guides/developer-handbook.md) - 完整开发指南
2. [代码生成器](./04-development/codegen.md) - 快速生成代码
3. [API 文档](./03-api/) - 接口文档
4. [测试指南](./06-testing/integration-test-guide.md) - 测试实践

### 运维人员

1. [部署指南](./05-operations/deployment.md) - 生产部署
2. [配置管理](./05-operations/configuration.md) - 配置说明
3. [故障排查](./01-guides/troubleshooting.md) - 问题诊断
4. [监控告警](./diagrams/README.md) - 监控体系

---

## 📝 文档规范

本文档库遵循以下规范：

- **Markdown 格式**: 所有文档使用 Markdown 编写
- **图表标准**: 使用 SVG 格式，支持云原生图标
- **链接规范**: 使用相对路径，便于文档迁移
- **版本控制**: 文档随代码一起版本管理
- **持续更新**: 代码变更时同步更新文档

---

## 🔍 搜索文档

使用以下方式快速查找文档：

1. **GitHub 搜索**: 在仓库中搜索关键词
2. **IDE 搜索**: 使用 IDE 的文件搜索功能
3. **文档导航**: 使用本页面的导航链接
4. **目录索引**: 查看各子目录的 README.md

---

## 🤝 贡献文档

欢迎贡献文档改进！

1. Fork 本仓库
2. 创建文档分支
3. 编写或修改文档
4. 提交 Pull Request

文档贡献指南请参考项目根目录的 CONTRIBUTING.md

---

## 📮 反馈与支持

- **问题反馈**: [GitHub Issues](https://github.com/your-org/apex-scaffold/issues)
- **功能建议**: [GitHub Discussions](https://github.com/your-org/apex-scaffold/discussions)
- **安全问题**: security@your-org.com

---

**文档版本**: 2.0.0  
**最后更新**: 2026-09-12  
**维护团队**: Apex Team

---

<div align="center">

**📖 开始您的 Apex Scaffold 之旅！**

[快速开始](./00-getting-started/quickstart.md) • [架构设计](./02-architecture/) • [API 文档](./03-api/) • [运维部署](./05-operations/)

</div>
