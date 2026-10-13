# v1.0.0 Release Notes

## 🎉 首次正式发布

Apex Scaffold v1.0.0 是一个面向中小型企业的**企业级后端开发脚手架**,提供开箱即用的基础设施组件,帮助团队快速启动新项目。

## ✨ 核心特性

### 架构设计
- 🏗️ **DDD五层架构** - Common、Domain、Application、Infrastructure、Interfaces清晰分层
- 🔌 **响应式编程** - 基于Spring WebFlux,支持高并发场景
- 📦 **模块化设计** - Maven多模块项目,按需引入依赖

### 安全认证
- 🔐 **JWT认证** - 无状态Token认证,支持分布式部署
- 🛡️ **RBAC权限** - 基于角色的访问控制(标准版/企业版)
- ✅ **参数校验** - JSR-303注解校验,防止非法输入

### 数据访问
- 💾 **MyBatis-Plus** - 增强版MyBatis,简化CRUD操作
- 🔄 **多级缓存** - Redisson + Caffeine二级缓存策略(标准版/企业版)
- 📊 **分页查询** - 内置PageHelper支持

### 高可用保障
- ⚡ **限流熔断** - Sentinel + Resilience4j双重保护(企业版)
- 📨 **消息队列** - RocketMQ封装,支持异步解耦(企业版)
- 🔒 **分布式锁** - Redisson实现(企业版)

### 监控运维
- 📈 **Prometheus监控** - 指标采集与告警(标准版/企业版)
- 🔍 **Zipkin链路追踪** - 全链路性能分析(企业版)
- 🏥 **健康检查** - Spring Boot Actuator集成

### 开发体验
- 📝 **API文档** - SpringDoc OpenAPI + Knife4j可视化
- 🐳 **容器化部署** - Podman + Kubernetes一键部署
- 🧪 **测试框架** - JUnit 5 + Mockito + AssertJ完整覆盖

## 📦 安装方式

### Maven依赖

```xml
<dependency>
    <groupId>com.apex.scaffold</groupId>
    <artifactId>apex-common</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 容器镜像

```bash
podman pull docker.io/apexscaffold/apex-scaffold:1.0.0
```

### Kubernetes部署

```bash
kubectl apply -f k8s/deployment.yaml
```

## 🚀 快速开始

```bash
# 1. 克隆项目
git clone https://github.com/apex-scaffold/apex-scaffold.git
cd apex-scaffold

# 2. 编译项目
mvn clean package -DskipTests

# 3. 启动应用
mvn spring-boot:run -pl apex-interfaces

# 4. 访问API文档
open http://localhost:8080/doc.html
```

详细教程请查看 [快速开始指南](../00-getting-started/quickstart.md)

## 📖 文档

- [快速开始](../00-getting-started/quickstart.md) - 5分钟上手教程
- [架构设计](../architecture/01-logical-architecture-detail.md) - DDD架构详解
- [配置说明](../05-operations/configuration.md) - 120+配置项说明
- [最佳实践](../01-guides/best-practices.md) - 编码规范与安全实践
- [问题排查](../01-guides/troubleshooting.md) - 常见问题解决方案
- [商业版本](COMMERCIAL_VERSIONS.md) - 版本对比与定价

## 🆕 示例模块

新增 `apex-example` 模块,展示如何使用脚手架构建标准Web应用:

- 完整的CRUD操作示例
- WebFlux函数式编程风格
- JWT认证集成
- Swagger API文档
- 单元测试与集成测试

运行示例:

```bash
cd apex-example
mvn spring-boot:run
```

## 🔧 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS版本 |
| Spring Boot | 3.5.15 | WebFlux响应式框架 |
| MyBatis-Plus | 3.5.5 | ORM框架 |
| Redisson | 3.27.0 | Redis客户端 |
| RocketMQ | 5.1.4 | 消息队列 |
| Sentinel | 1.8.6 | 限流熔断 |
| Resilience4j | 2.1.0 | 容错框架 |
| Lombok | 1.18.30 | 代码简化工具 |
| MapStruct | 1.5.5 | 对象映射 |

## 🐛 已知问题

暂无

## 📝 变更日志

完整变更历史请查看 [CHANGELOG.md](../../CHANGELOG.md)

## 🙏 致谢

感谢以下开源项目的支持:

- [Spring Boot](https://spring.io/projects/spring-boot)
- [MyBatis-Plus](https://baomidou.com/)
- [Redisson](https://redisson.org/)
- [RocketMQ](https://rocketmq.apache.org/)
- [Sentinel](https://sentinelguard.io/)
- [Resilience4j](https://resilience4j.readme.io/)

## 📄 许可证

本项目基础版基于 MIT License 开源。标准版和企业版为商业授权。

详细条款请查看 [LICENSE](../../LICENSE) 文件。

## 📞 联系方式

- 🌐 官网: https://apex-scaffold.example.com
- 📧 邮箱: support@apex-scaffold.example.com
- 💬 GitHub Issues: https://github.com/apex-scaffold/apex-scaffold/issues

---

**Made with ❤️ by Apex Team**
