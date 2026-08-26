# 快速开始指南

本指南将帮助你在 **5分钟** 内启动 Apex Scaffold 并运行第一个示例。

## 📋 前置检查

确保你的环境满足以下要求:

```bash
# 检查Java版本(需要17+)
java -version

# 检查Maven版本(需要3.8+)
mvn -version

# 检查Docker(可选)
docker --version
docker-compose --version
```

如果未安装,请参考:
- [JDK 17下载](https://www.oracle.com/java/technologies/downloads/#java17)
- [Maven安装指南](https://maven.apache.org/install.html)
- [Docker安装指南](https://docs.docker.com/get-docker/)

## 🎯 第一步:获取代码

### 方式A:克隆仓库(推荐)

```bash
git clone https://github.com/your-org/apex-scaffold.git
cd apex-scaffold
```

### 方式B:下载ZIP

访问 GitHub 仓库页面,点击 "Code" → "Download ZIP",解压后进入目录。

## 🚀 第二步:启动服务

### 选项1:使用Docker(最简单)

```bash
# 一键启动所有依赖(MySQL + Redis + 应用)
docker-compose up -d

# 等待30秒让服务完全启动
sleep 30

# 验证服务状态
docker-compose ps
```

预期输出:
```
NAME                STATUS              PORTS
apex-mysql          Up (healthy)        0.0.0.0:3306->3306/tcp
apex-redis          Up (healthy)        0.0.0.0:6379->6379/tcp
apex-scaffold       Up                  0.0.0.0:8080->8080/tcp
```

### 选项2:本地手动启动

#### 2.1 启动MySQL和Redis

```bash
# 仅启动基础设施
docker-compose up -d mysql redis
```

#### 2.2 编译项目

```bash
mvn clean package -DskipTests
```

首次编译可能需要3-5分钟(下载依赖)。

#### 2.3 启动应用

```bash
mvn spring-boot:run -pl apex-interfaces
```

看到以下日志表示启动成功:
```
Started ApexApplication in X.XXX seconds
```

## ✅ 第三步:验证安装

### 3.1 健康检查

```bash
curl http://localhost:8080/api/health
```

预期响应:
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "status": "UP",
    "timestamp": "2026-08-25T12:00:00",
    "application": "apex-service",
    "version": "1.0.0"
  },
  "traceId": null
}
```

### 3.2 访问API文档

浏览器打开:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Knife4j**: http://localhost:8080/doc.html

你应该能看到所有可用的API接口列表。

### 3.3 测试示例接口

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

## 📖 第四步:阅读示例代码

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

## 🔧 第五步:自定义配置

编辑 `apex-interfaces/src/main/resources/application.yml`:

```yaml
# 修改JWT密钥(生产环境必须修改!)
jwt:
  secret: your-very-secret-key-at-least-32-characters

# 修改数据库密码
spring:
  datasource:
    password: your-db-password
```

重启应用使配置生效。

## 🎓 下一步

- 📚 [架构设计说明](ARCHITECTURE.md) - 了解DDD分层架构
- ⚙️ [配置项详解](CONFIGURATION.md) - 查看所有可配置项
- 💡 [最佳实践](BEST_PRACTICES.md) - 学习常见问题解决方案
- 🐛 [问题排查](TROUBLESHOOTING.md) - 常见错误与解决方法

## ❓ 常见问题

### Q1: 端口被占用怎么办?

修改 `docker-compose.yml` 中的端口映射:
```yaml
ports:
  - "8081:8080"  # 将宿主机的8081映射到容器的8080
```

### Q2: 如何查看应用日志?

```bash
# Docker方式
docker-compose logs -f apex-scaffold

# 本地方式
tail -f logs/application.log
```

### Q3: 数据库连接失败?

检查MySQL是否启动:
```bash
docker-compose ps mysql
```

如果未启动,执行:
```bash
docker-compose up -d mysql
```

### Q4: 如何重置数据库?

```bash
# 删除数据卷(⚠️ 会丢失所有数据!)
docker-compose down -v

# 重新启动
docker-compose up -d
```

## 🆘 获取帮助

- 📖 查阅 [完整文档](../README.md)
- 🐛 提交 [GitHub Issue](https://github.com/your-org/apex-scaffold/issues)
- 💬 加入社区讨论群

---

**祝你使用愉快!** 🎉
