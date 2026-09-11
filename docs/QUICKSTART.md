# Apex Scaffold 快速开始指南

> **预计时间**: 5 分钟  
> **适用版本**: v1.2.0+  
> **最后更新**: 2026-09-09

本指南帮助您在 5 分钟内从零开始运行 Apex Scaffold 项目。

---

## 📋 前置要求

### 必需（basic 档位）

- ✅ **JDK 17+** - 推荐使用 `17.0.12-oracle`
- ✅ **Maven** - 推荐使用 `mvnd 2.0.0-rc-3`（速度快 3 倍）

### 可选（standard/enterprise 档位）

- 🔵 **MySQL 8.0+** - 标准档位及以上需要
- 🔵 **Redis 7.0+** - 标准档位及以上需要
- 🟢 **RocketMQ 5.x** - 企业档位需要
- 🟢 **Nacos 2.x** - 企业档位需要

---

## 🚀 三步快速启动

### 步骤 1: 克隆项目

```bash
git clone https://github.com/your-org/apex-scaffold.git
cd apex-scaffold
```

---

### 步骤 2: 选择档位启动

Apex Scaffold 提供三种档位，按需选择：

#### 🟢 Basic 档位（最简单，无需外部依赖）

```bash
# 1. 直接启动（无需配置）
./scripts/build/build.sh -Pbasic
./scripts/deploy/app.sh start dev basic

# 2. 访问应用
curl http://localhost:8080/actuator/health
```

**适用场景**: 本地开发、快速演示、学习项目架构

---

#### 🔵 Standard 档位（标准企业应用）

```bash
# 1. 启动基础设施
docker-compose -f docker-compose.infra.yml up -d

# 等待 MySQL 和 Redis 启动完成（约 30 秒）
docker-compose -f docker-compose.infra.yml ps

# 2. 配置环境变量
cp .env.example .env
vim .env  # 修改数据库和 Redis 密码

# 3. 构建并启动
./scripts/build/build.sh -Pstandard
./scripts/deploy/app.sh start dev standard
```

**必填环境变量**:
```bash
# .env 文件
DB_MASTER_URL=jdbc:mysql://localhost:3306/apex_db
DB_MASTER_USERNAME=root
DB_MASTER_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your-secret-key-at-least-32-bytes
```

**适用场景**: 生产环境、中型企业

---

#### 🟣 Enterprise 档位（完整企业特性）

```bash
# 1. 启动完整基础设施
docker-compose -f docker-compose.infra.yml up -d

# 2. 配置环境变量（包含 RocketMQ、Nacos）
cp .env.example .env
vim .env

# 3. 构建并启动
./scripts/build/build.sh -Penterprise
./scripts/deploy/app.sh start dev enterprise
```

**额外必填环境变量**:
```bash
# 除 standard 配置外，还需要：
ROCKETMQ_NAMESERVER=localhost:9876
NACOS_SERVER_ADDR=localhost:8848
ZIPKIN_BASE_URL=http://localhost:9411
```

**适用场景**: 大型企业、微服务架构、高并发场景

---

### 步骤 3: 验证启动

```bash
# 检查应用健康状态
curl http://localhost:8080/actuator/health

# 预期输出：
{
  "status": "UP"
}

# 访问 Swagger API 文档
open http://localhost:8080/swagger-ui.html
```

---

## 🎯 下一步

### 开发者

1. 📖 **阅读架构文档**: [ARCHITECTURE_INDEX.md](docs/ARCHITECTURE_INDEX.md)
2. 🔧 **配置 IDE**: 导入项目，配置 Lombok、MapStruct 插件
3. 📝 **生成代码**: 使用代码生成器快速创建 CRUD 模块
   ```bash
   ./scripts/codegen/generate.sh -i examples/product.json
   ```

### 运维人员

1. 🚀 **生产部署**: [deployment.md](docs/operations/deployment.md)
2. 📊 **监控配置**: [monitoring.md](docs/operations/monitoring.md)
3. 🔐 **环境变量**: [environment-variables.md](docs/operations/environment-variables.md)

---

## 🐛 常见问题

### 问题 1: 启动失败 - "Cannot create PoolableConnectionFactory"

**原因**: 数据库连接失败

**解决方案**:
```bash
# 1. 检查 MySQL 是否启动
docker-compose -f docker-compose.infra.yml ps

# 2. 检查环境变量
echo $DB_MASTER_URL
echo $DB_MASTER_PASSWORD

# 3. 测试数据库连通性
mysql -h localhost -P 3306 -u root -p
```

---

### 问题 2: Redis 连接超时

**原因**: Redis 未启动或密码错误

**解决方案**:
```bash
# 1. 检查 Redis 是否启动
docker-compose -f docker-compose.infra.yml ps

# 2. 测试 Redis 连通性
redis-cli -h localhost -p 6379 -a your_password ping
```

---

### 问题 3: 端口冲突

**原因**: 8080 端口被占用

**解决方案**:
```bash
# 方法 1: 更改应用端口
export SERVER_PORT=8081
./scripts/deploy/app.sh start dev basic

# 方法 2: 查找并停止占用端口的进程
lsof -i :8080
kill -9 <PID>
```

---

## 📚 完整文档

### 架构文档

- 📖 [架构文档索引](docs/ARCHITECTURE_INDEX.md) - 29 张架构图完整索引
- 🏗️ [DDD 四层架构](docs/architecture/01-logical-architecture-detail.md)
- 🌐 [技术架构全景](docs/architecture/03-technical-architecture-detail.md)

### 开发指南

- 🔧 [代码生成器](docs/CODEGEN.md) - 一键生成 CRUD 模块
- 🔐 [安全防护](docs/SECURITY_PROTECTIONS.md) - 认证、授权、加密
- 🔄 [工作流引擎](docs/WORKFLOW_GUIDE.md) - BPMN 2.0 审批流

### 运维指南

- 🚀 [部署指南](docs/operations/deployment.md) - Docker/K8s 部署
- 📊 [监控体系](docs/operations/monitoring.md) - Prometheus + Grafana
- 🔐 [环境变量](docs/operations/environment-variables.md) - 172 个变量完整说明

---

## 🆘 获取帮助

### 遇到问题？

1. 📖 **查看文档**: [故障排查指南](docs/TROUBLESHOOTING.md)
2. 🔍 **搜索 Issues**: [GitHub Issues](https://github.com/your-org/apex-scaffold/issues)
3. 💬 **提问讨论**: [GitHub Discussions](https://github.com/your-org/apex-scaffold/discussions)

### 报告 Bug

如果您发现 Bug，请[创建 Issue](https://github.com/your-org/apex-scaffold/issues/new) 并提供：
- 项目版本号
- 档位配置（basic/standard/enterprise）
- 错误日志
- 复现步骤

---

## 🎓 学习路径

### 初学者（1-2 天）

1. ✅ 使用 basic 档位启动项目
2. ✅ 阅读 [DDD 四层架构](docs/architecture/01-logical-architecture-detail.md)
3. ✅ 使用代码生成器创建第一个模块
4. ✅ 运行单元测试

### 进阶者（3-5 天）

1. ✅ 使用 standard 档位部署完整环境
2. ✅ 学习 [工作流引擎](docs/WORKFLOW_GUIDE.md)
3. ✅ 学习 [安全防护机制](docs/SECURITY_PROTECTIONS.md)
4. ✅ 配置 Prometheus + Grafana 监控

### 高级用户（1-2 周）

1. ✅ 使用 enterprise 档位部署生产环境
2. ✅ 学习 [微服务治理](docs/architecture/18-microservice-governance-detail.md)
3. ✅ 学习 [容灾切换](docs/architecture/26-disaster-recovery-flow-detail.md)
4. ✅ 自定义扩展（SPI、插件）

---

## ⚡ 性能提示

### 开发环境加速

```bash
# 1. 使用 mvnd（速度提升 3 倍）
sdk install mvnd 2.0.0-rc-3
mvnd clean package -DskipTests

# 2. 启用离线模式（已下载依赖后）
mvnd -o clean package

# 3. 并行构建
mvnd -T 4 clean package
```

### 生产环境优化

```bash
# 1. JVM 参数优化
java -Xms512m -Xmx1024m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar apex-interfaces-1.2.0.jar

# 2. 启用 Profile
java -jar apex-interfaces-1.2.0.jar \
     --spring.profiles.active=prod,enterprise
```

---

**文档版本**: 1.0.0  
**最后更新**: 2026-09-09  
**维护者**: Apex Team

---

**🎉 恭喜！您已完成快速开始。开始您的开发之旅吧！**

<div align="center">

[⬆️ 返回顶部](#apex-scaffold-快速开始指南) • [📖 查看完整文档](../ARCHITECTURE_INDEX.md) • [💬 加入讨论](https://github.com/your-org/apex-scaffold/discussions)

</div>
