# Apex Example - 示例模块

本模块展示了如何使用 **Apex Scaffold** 脚手架快速构建一个标准的 WebFlux 应用。

## 📋 功能特性

- ✅ 完整的 CRUD 操作（创建、查询、更新、删除）
- ✅ WebFlux 函数式编程风格（Handler + Router）
- ✅ MyBatis-Plus 数据访问层
- ✅ JWT 认证集成
- ✅ Swagger API 文档
- ✅ 参数校验（JSR-303）
- ✅ 统一响应格式封装
- ✅ 逻辑删除支持
- ✅ 手机号脱敏处理

## 🚀 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < src/main/resources/db/init.sql
```

### 2. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/apex_example?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
  
  data:
    redis:
      host: localhost
      port: 6379
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

或运行主类 `com.apex.example.ExampleApplication`

### 4. 访问 API 文档

打开浏览器访问：http://localhost:8080/doc.html

## 📖 API 接口说明

### 用户管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/example/users` | 创建用户 |
| GET | `/api/example/users` | 查询用户列表 |
| GET | `/api/example/users/{id}` | 查询用户详情 |
| PUT | `/api/example/users/{id}` | 更新用户 |
| DELETE | `/api/example/users/{id}` | 删除用户（逻辑删除） |

### 请求示例

#### 创建用户

```bash
curl -X POST http://localhost:8080/api/example/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "age": 25,
    "phone": "13800138000"
  }'
```

#### 查询用户列表

```bash
curl -X GET http://localhost:8080/api/example/users
```

#### 查询用户详情

```bash
curl -X GET http://localhost:8080/api/example/users/1
```

## 🏗️ 代码结构

```
apex-example/
├── src/main/java/com/apex/example/
│   ├── config/              # 配置类
│   │   └── ExampleRouterConfig.java    # 路由配置
│   ├── dto/                 # DTO 数据传输对象
│   │   ├── CreateUserRequest.java      # 创建用户请求
│   │   └── UserResponse.java           # 用户响应
│   ├── handler/             # WebFlux Handler
│   │   └── ExampleUserHandler.java     # 用户处理器
│   ├── model/               # 实体类
│   │   └── ExampleUser.java            # 用户实体
│   ├── repository/          # 数据访问层
│   │   └── ExampleUserMapper.java      # Mapper 接口
│   ├── service/             # 业务逻辑层
│   │   └── ExampleUserService.java     # 用户服务
│   └── ExampleApplication.java         # 启动类
├── src/main/resources/
│   ├── db/                  # 数据库脚本
│   │   └── init.sql                    # 初始化脚本
│   └── application.yml                # 配置文件
└── pom.xml
```

## 💡 最佳实践

### 1. 实体类设计

- 使用 Lombok 简化代码
- 实现 Serializable 接口
- 使用 MyBatis-Plus 注解配置映射关系

### 2. DTO 分层

- Request DTO：接收前端参数，包含校验注解
- Response DTO：返回给前端的数据，避免暴露内部字段

### 3. Service 层

- 使用 `@Transactional` 管理事务
- 使用 Mono 进行响应式编程
- 业务逻辑封装在 Service 层

### 4. Handler 层

- 使用 ServerRequest/ServerResponse 处理 HTTP 请求
- 集成 Swagger 文档注解
- 统一异常处理和响应格式

### 5. 安全实践

- 敏感信息脱敏（如手机号）
- 逻辑删除而非物理删除
- 参数校验防止非法输入

## 🔧 扩展指南

### 添加新模块

1. 复制 `apex-example` 目录并重命名
2. 修改包名和类名
3. 在父 `pom.xml` 中添加新模块
4. 按照相同模式编写业务代码

### 集成其他功能

参考 [Apex Scaffold 文档](../docs/) 了解如何集成：

- 消息队列（RocketMQ）
- 缓存（Redisson）
- 限流熔断（Sentinel）
- 链路追踪（Zipkin）
- 监控指标（Prometheus）

## 📝 注意事项

- 本模块仅用于演示，生产环境请根据实际需求调整
- 数据库密码建议使用环境变量或配置中心管理
- JWT Secret 必须使用强随机字符串
- 建议启用 HTTPS 保证通信安全

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License
