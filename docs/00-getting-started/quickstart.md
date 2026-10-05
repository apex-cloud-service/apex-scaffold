# Apex Scaffold 快速开始指南

> **预计时间**: 5 分钟  
> **适用版本**: v1.3.1+  
> **最后更新**: 2026-09-12

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

### 环境检查

```bash
# 加载 sdkman（JDK + mvnd，推荐）
source ~/.sdkman/bin/sdkman-init.sh

# 检查Java版本(需要17+)
java -version

# 检查构建工具版本（推荐 mvnd，Maven Daemon；mvn 亦可）
mvnd --version

# 检查Docker(可选)
docker --version
docker compose --version
```

如果未安装,请参考:
- [JDK 17下载](https://www.oracle.com/java/technologies/downloads/#java17)（推荐 sdkman：`sdk install java 17.0.12-oracle`）
- [mvnd安装指南](https://github.com/apache/maven-mvnd)（推荐 sdkman：`sdk install mvnd`；传统 Maven 亦可）
- [Docker安装指南](https://docs.docker.com/get-docker/)

---

## 🚀 三步快速启动

### 步骤 1: 克隆项目

```bash
git clone https://github.com/your-org/apex-scaffold.git
cd apex-scaffold
```

### 方式B:下载ZIP

访问 GitHub 仓库页面,点击 "Code" → "Download ZIP",解压后进入目录。

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

### 选项:使用Docker(最简单)

```bash
# 一键启动所有依赖(MySQL + Redis + 应用)
docker compose up -d --build

# 等待30秒让服务完全启动
sleep 30

# 验证服务状态
docker compose ps
```

预期输出（容器名由 compose 自动生成，形如 apex-scaffold-mysql-1）:
```
NAME                     STATUS              PORTS
apex-scaffold-apex...-1  Up                  0.0.0.0:8080->8080/tcp
apex-scaffold-mysql-1    Up (healthy)        0.0.0.0:3306->3306/tcp
apex-scaffold-redis-1    Up (healthy)        0.0.0.0:6379->6379/tcp
```

> 💡 如需同时部署项目用到的**全部中间件**（Nacos / RocketMQ / Sentinel /
> Zipkin / Prometheus / Grafana 等），一条命令：
> `docker compose -f docker-compose.infra.yml up -d`（详见 [DEPLOYMENT.md](../05-operations/deployment.md) §6.5）

---

### 步骤 3: 验证启动

```bash
# 检查应用健康状态
curl http://localhost:8080/api/v1/health

# 预期输出：
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "status": "UP",
    "timestamp": "2026-09-12T12:00:00",
    "application": "apex-scaffold",
    "version": "1.3.1"
  },
  "traceId": null
}
```

### 访问API文档

浏览器打开:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Knife4j**: http://localhost:8080/doc.html

你应该能看到所有可用的API接口列表。

### 测试示例接口

在 Knife4j 页面找到 `示例模块` → `用户管理`,点击 "试用":

1. 点击 `/api/example/users` 的 `POST` 接口
2. 填写请求体:
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "age": 25
}
```
3. 点击 "发送"
4. 查看响应结果

---

## 📖 阅读示例代码

示例模块位于 `apex-example` 目录,展示了如何使用脚手架的核心功能:

```
apex-example/
├── src/main/java/com/apex/example/
│   ├── controller/      # REST控制器示例
│   ├── service/         # 业务逻辑层示例
│   ├── repository/      # 数据访问层示例
│   └── model/           # 实体类示例
└── src/main/resources/
    └── application.yml  # 配置文件
```

关键文件说明:

- `ExampleUserController.java` - 展示如何编写REST接口
- `ExampleUserService.java` - 展示Service层最佳实践
- `ExampleUserRepository.java` - 展示MyBatis-Plus用法
- `ExampleUser.java` - 展示实体类设计规范

详见 [example-guide.md](./example-guide.md)

---

## 🎯 下一步

### 开发者

1. 📖 **阅读架构文档**: [architecture-index.md](../02-architecture/architecture-index.md)
2. 🔧 **配置 IDE**: 导入项目，配置 Lombok、MapStruct 插件
3. 📝 **生成代码**: 使用代码生成器快速创建 CRUD 模块
   ```bash
   ./scripts/codegen/generate.sh -i examples/product.json
   ```
4. 💡 **最佳实践**: [best-practices.md](../01-guides/best-practices.md)
5. 📚 **开发手册**: [developer-handbook.md](../01-guides/developer-handbook.md)

### 运维人员

1. 🚀 **生产部署**: [deployment.md](../05-operations/deployment.md)
2. 📊 **监控配置**: [monitoring.md](../_archive/operations/monitoring.md)
3. 🔐 **配置管理**: [configuration.md](../05-operations/configuration.md)
4. 🐛 **故障排查**: [troubleshooting.md](../01-guides/troubleshooting.md)

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

# 方法 2: 修改 docker-compose.yml 端口映射
ports:
  - "8081:8080"  # 将宿主机的8081映射到容器的8080

# 方法 3: 查找并停止占用端口的进程
lsof -i :8080
kill -9 <PID>
```

---

### 问题 4: 如何重置数据库?

```bash
# 删除数据卷(⚠️ 会丢失所有数据!)
docker compose down -v

# 重新启动
docker compose up -d
```

更多问题请参考 [troubleshooting.md](../01-guides/troubleshooting.md)

---

## 🎓 学习路径

### 初学者（1-2 天）

1. ✅ 使用 basic 档位启动项目
2. ✅ 阅读 [DDD 四层架构](../architecture/01-logical-architecture-detail.md)
3. ✅ 使用代码生成器创建第一个模块
4. ✅ 运行单元测试

### 进阶者（3-5 天）

1. ✅ 使用 standard 档位部署完整环境
2. ✅ 学习 [工作流引擎](../01-guides/workflow-guide.md)
3. ✅ 学习 [安全防护机制](../05-operations/security-protections.md)
4. ✅ 配置 Prometheus + Grafana 监控

### 高级用户（1-2 周）

1. ✅ 使用 enterprise 档位部署生产环境
2. ✅ 学习 [微服务治理](../architecture/service-governance-solution.md)
3. ✅ 学习 [多级缓存架构](../architecture/advanced-features/multi-level-cache-complete-guide.md)
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
     -jar apex-interfaces-1.3.1.jar

# 2. 启用 Profile
java -jar apex-interfaces-1.3.1.jar \
     --spring.profiles.active=prod,enterprise
```

---

## 🆘 获取帮助

### 遇到问题？

1. 📖 **查看文档**: [troubleshooting.md](../01-guides/troubleshooting.md)
2. 🔍 **搜索 Issues**: [GitHub Issues](https://github.com/your-org/apex-scaffold/issues)
3. 💬 **提问讨论**: [GitHub Discussions](https://github.com/your-org/apex-scaffold/discussions)

### 报告 Bug

如果您发现 Bug，请[创建 Issue](https://github.com/your-org/apex-scaffold/issues/new) 并提供：
- 项目版本号
- 档位配置（basic/standard/enterprise）
- 错误日志
- 复现步骤

---

**文档版本**: 1.0.0  
**最后更新**: 2026-09-12  
**维护者**: Apex Team

---

**🎉 恭喜！您已完成快速开始。开始您的开发之旅吧！**
