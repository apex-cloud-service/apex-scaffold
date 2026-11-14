# 示例模块能力地图（EXAMPLE_GUIDE）

`apex-example` 是脚手架「能力地图」：每个核心通用能力一个可运行的最小示例，
新用户按本文档可在 30 分钟内跑通全链路。所有端点均有集成测试覆盖（17 例全绿）。

## 运行方式

```bash
# 1. 准备独立库（示例使用 apex_example 库，与主应用隔离）
mysql -h 127.0.0.1 -u root -papex-root -e "CREATE DATABASE IF NOT EXISTS apex_example DEFAULT CHARACTER SET utf8mb4;"

# 2. 启动（统一构建环境）
source ~/.sdkman/bin/sdkman-init.sh
mvnd spring-boot:run -pl :apex-example

# 3. 打开 API 文档
open http://localhost:8080/doc.html
```

## 5 分钟逐个跑通

### 1️⃣ 用户 CRUD（DDD 全链路基线）
| 方法 | 路径 | 演示能力 |
|------|------|---------|
| POST | `/api/example/users` | 参数校验（Jakarta Validation）、统一响应 |
| GET | `/api/example/users?pageNum=1&pageSize=10` | 统一分页 `PageResponse` |
| GET | `/api/example/users/{id}` | 详情查询（未找到 → 4001/HTTP 404） |
| PUT | `/api/example/users/{id}` | 更新 |
| DELETE | `/api/example/users/{id}` | 逻辑删除（deleted=1） |
| GET | `/api/example/reports/premium` | **商业授权** `@FeatureGate`（需标准版/企业版 License） |

### 2️⃣ 核心能力演示（/api/example/capabilities/*）
| 方法 | 路径 | 演示能力 | 关键资产 |
|------|------|---------|---------|
| GET | `/api/example/capabilities/cache/{key}` | 多级缓存读穿（首次加载回填，含**击穿合并**） | `MultiLevelCacheService.getWithLoader` / `InFlightRequestMerge` |
| DELETE | `/api/example/capabilities/cache/{key}` | 缓存主动失效 | `MultiLevelCacheService.delete` |
| POST | `/api/example/capabilities/idempotent` | **写接口幂等**（相同 `Idempotency-Key` 窗口内重复请求被拒） | `@IdempotentApi` + Redis SETNX |
| POST | `/api/example/capabilities/audit` | **操作审计**（自动落 `sys_operation_log`） | `@OperationLog` + `OperationLogAspect` |
| GET | `/api/example/capabilities/stream` | **SSE 实时推送**（每秒心跳 ×10） | WebFlux `ServerSentEvent` |
| GET | `/api/example/capabilities/mask?phone=13800138000` | **数据脱敏**（手机/身份证/邮箱/姓名） | `DataMaskUtil` |
| GET | `/api/example/capabilities/export/users` | **Excel 导出**（xlsx 附件） | `ExcelExportService`（EasyExcel） |

### 快速验证命令

```bash
# 缓存读穿：连续两次调用，storageHits 不变即命中缓存
curl http://localhost:8080/api/example/capabilities/cache/demo

# 幂等：两次相同 Idempotency-Key，第二次返回 REPEAT_SUBMIT
curl -X POST http://localhost:8080/api/example/capabilities/idempotent \
  -H "Content-Type: application/json" -H "Idempotency-Key: demo-001" -d '{}'

# SSE：观察心跳流
curl -N http://localhost:8080/api/example/capabilities/stream

# 导出：下载 users.xlsx
curl -OJ http://localhost:8080/api/example/capabilities/export/users
```

## 示例代码结构

```
apex-example/src/main/java/com/apex/example/
├── ExampleApplication.java        # 启动类（scanBasePackages 显式扫描脚手架）
├── config/ExampleRouterConfig.java  # 函数式路由注册（用户 + 能力演示两组）
├── handler/
│   ├── ExampleUserHandler.java      # 用户 CRUD + @FeatureGate 示例
│   └── ExampleCapabilityHandler.java # 能力演示端点（缓存/幂等/审计/SSE/脱敏/导出）
├── service/
│   ├── ExampleUserService.java      # 用户业务（分页/脱敏/逻辑删除）
│   └── ExampleCapabilityService.java # 能力演示服务（缓存/SSE/脱敏）
├── dto/
│   ├── CreateUserRequest.java       # 请求校验 DTO
│   ├── UserResponse.java            # 响应 DTO
│   └── ExampleUserExportDTO.java    # Excel 导出表头 DTO（@ExcelProperty）
├── model/ExampleUser.java           # 领域实体（MyBatis-Plus）
└── repository/ExampleUserMapper.java
```

## 集成测试（17 例全绿）

- `UserApiIntegrationTest`（10 例）：CRUD/分页/校验/错误码/脱敏/删除链路
- `ExampleCapabilityIntegrationTest`（7 例）：缓存/幂等/审计/SSE/脱敏/导出

运行：`mvnd -o verify -pl :apex-example`（集成测试走 failsafe verify 阶段）

> 💡 测试环境以 Mock Redis 提供基础设施替身（`ExampleTestMockConfiguration`），
> 验证的是「接入方式与响应结构」；真实行为请在本地 Redis 上启动应用验证。

## 扩展新示例的约定

1. 能力示例统一挂在 `/api/example/capabilities/*`，在 `ExampleCapabilityHandler` 新增端点
2. 路由在 `ExampleRouterConfig.exampleCapabilityRoutes()` 注册（保持分组清晰）
3. 每个端点必须：Javadoc 说明演示能力 + `@Operation` Swagger 声明 + 集成测试
4. 演示数据用常量/内存数据，不依赖业务表（避免示例与应用数据耦合）
