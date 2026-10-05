# 认证中心核心功能验证指南

本文是验证步骤与预期行为，不是测试通过报告。需分别验证独立认证中心和主应用授权开关；两者的登录入口与用户 ID 不可混用。

## 测试环境

- **应用端口**: 8081
- **数据库**: MySQL（默认 localhost:3306/apex_auth）
- **测试用户**:
  - 用户名: `admin` / 密码: `password123`
  - 用户名: `user` / 密码: `password123`
- **租户ID**: `default`
- 示例账户来自 `V46__insert_test_users.sql`（admin=1001、user=1002、tenant1/test=1003），密码为脚本声明值；须确认实际迁移及密码可用。
- 启动前注入 `JWT_SECRET`、`TOKEN_ENCRYPTION_KEY` 和 `CONFIG_ENCRYPTION_KEY`；统一授权联调另需配置读取账号及 HTTPS 入口。

## 前置准备

### 1. 启动 MySQL
```bash
# 确保 MySQL 服务运行
mysql.server start  # macOS
# 或 sudo service mysql start  # Linux

# 创建数据库（如果不存在）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS apex_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 2. 构建并启动应用
```bash
cd /Users/work/apex-scaffold
sdk env
mvnd -pl apex-auth-center -am clean package -DskipTests
cd apex-auth-center
mvnd spring-boot:run
```

**期望输出**:
```
Started AuthCenterApplication in X.XXX seconds
```

## 测试用例

### 测试1：用户登录

**请求**:
```bash
curl -X POST http://localhost:8081/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123",
    "tenantId": "default",
    "deviceId": "test-device-001"
  }' | jq .
```

**期望响应** (HTTP 200):
```json
{
  "userId": 1001,
  "username": "admin",
  "tenantId": "default",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200
}
```

**验证点**:
- 检查： HTTP 状态码 200
- 检查： 返回 `accessToken` 和 `refreshToken`
- 检查： `expiresIn` 为 7200（2小时）
- 检查： `userId` 和 `username` 正确

---

### 测试2：错误密码登录

**请求**:
```bash
curl -X POST http://localhost:8081/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "wrong-password",
    "tenantId": "default"
  }'
```

**期望响应** (HTTP 401):
```
用户名或密码错误
```

**验证点**:
- 检查： HTTP 状态码 401
- 检查： 返回错误提示
- 检查： 数据库 `failed_login_attempts` 递增

---

### 测试3：账户锁定机制

**请求** (连续5次错误密码):
```bash
for i in {1..5}; do
  echo "=== 尝试 $i ==="
  curl -X POST http://localhost:8081/auth/v1/login \
    -H "Content-Type: application/json" \
    -d '{
      "username": "user",
      "password": "wrong",
      "tenantId": "default"
    }'
  echo
done
```

**期望行为**:
- 前4次: HTTP 401 "用户名或密码错误"
- 第5次: HTTP 403 "账户已锁定，请稍后重试"

**验证点**:
- 检查： 5次失败后账户锁定
- 检查： `status` 设置为 2
- 检查： `locked_until` 设置为 30 分钟后

---

### 测试4：Token 刷新

**步骤1**: 先登录获取 `refreshToken`
```bash
RESPONSE=$(curl -s -X POST http://localhost:8081/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123",
    "tenantId": "default"
  }')

REFRESH_TOKEN=$(echo $RESPONSE | jq -r '.refreshToken')
test -n "$REFRESH_TOKEN"
```

**步骤2**: 使用 `refreshToken` 刷新
```bash
curl -X POST http://localhost:8081/auth/v1/refresh \
  -H "Content-Type: application/json" \
  -d "{
    \"refreshToken\": \"$REFRESH_TOKEN\"
  }" | jq .
```

**期望响应** (HTTP 200):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 7200,
  "tokenType": "Bearer"
}
```

**验证点**:
- 检查： HTTP 状态码 200
- 检查： 返回新的 `accessToken`
- 检查： `refreshToken` 轮换，旧令牌被撤销；保存新的 Access/Refresh Token

---

### 测试5：用户登出

**步骤1**: 先登录获取 `userId`
```bash
RESPONSE=$(curl -s -X POST http://localhost:8081/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123",
    "tenantId": "default"
  }')

USER_ID=$(echo $RESPONSE | jq -r '.userId')
ACCESS_TOKEN=$(echo $RESPONSE | jq -r '.accessToken')
echo "User ID: $USER_ID"
```

**步骤2**: 登出
```bash
curl -X POST "http://localhost:8081/auth/v1/logout/$USER_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -o /dev/null -w "%{http_code}"
```

**期望响应**：HTTP 200，空响应体。路径用户必须与 Token 用户一致；请求租户参数不能覆盖 Token 租户。

**步骤3**: 使用已撤销 Token 重试上面的登出请求，应被安全链拒绝。不要调用未注册的 `/auth/v1/verify`。同时检查同租户该用户的 Token 撤销记录。

**验证点**:
- 检查： 登出成功
- 检查： 数据库 `auth_token` 表对应记录 `revoked=1`
- 检查： 已撤销 Token 无法通过验证

---

### 测试6：租户隔离

**请求** (tenant1 的用户尝试用 default 租户登录):
```bash
curl -X POST http://localhost:8081/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test",
    "password": "password123",
    "tenantId": "default"
  }'
```

**期望响应** (HTTP 401):
```
用户名或密码错误
```

**验证点**:
- 检查： 跨租户登录失败
- 检查： 用户名匹配但租户不匹配时拒绝

---

## 数据库验证

### 查询用户状态
```sql
SELECT id, tenant_id, username, status, failed_login_attempts, locked_until, last_login_at
FROM auth_user
WHERE tenant_id = 'default';
```

### 查询 Token 记录
```sql
SELECT id, user_id, token_type, revoked, expires_at, created_at
FROM auth_token
WHERE tenant_id = 'default'
ORDER BY created_at DESC
LIMIT 10;
```

### 查询审计日志
```sql
SELECT user_id, event_type, event_status, client_ip, created_at
FROM auth_audit_log
WHERE tenant_id = 'default'
ORDER BY created_at DESC
LIMIT 10;
```

---

## 常见问题

### Q1: 应用启动失败 - 数据库连接错误
**解决**:
```bash
# 检查 MySQL 是否运行
mysql.server status

# 检查数据库是否存在
mysql -u root -p -e "SHOW DATABASES LIKE 'apex_auth';"

# 修改配置文件中的数据库密码
vi apex-auth-center/src/main/resources/application.yml
```

### Q2: Flyway 迁移失败 - 版本冲突
**解决**:
```sql
-- 查看当前 Flyway 版本
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;


```

当前激活迁移路径为 `classpath:db/migration/mysql`。先核对历史表、脚本版本及校验和，再按环境制定修复方案；不要直接删除数据库。

### Q3: 登录返回 500 错误
**解决**:
```bash
# 查看应用日志
tail -f logs/apex-auth-center.log

# 常见原因：
# 1. 数据库表未创建 → 检查 Flyway 是否执行
# 2. JWT 密钥未配置 → 检查 application.yml
# 3. 用户数据不存在 → 检查 V46 种子脚本是否执行
```

### Q4: Token 验证失败
**解决**:
```bash
# 解析 JWT Token 查看内容
echo "YOUR_TOKEN" | jq -R 'split(".") | .[1] | @base64d | fromjson'

# 检查 Token 是否已过期
# 检查数据库 revoked 状态
```

---

## 统一授权验证矩阵

先在中心准备启用且未锁定的独立读取账号，将其 ID 配置到 `AUTH_CENTER_AUTHORIZATION_READER_USER_ID`，取得该账号的有效 ACCESS Token。主应用配置唯一映射与 HTTPS 入口；映射示例仅为结构示例，不会创建账号。

```bash
curl --fail-with-body -sS "$AUTH_CENTER_HTTPS_URL/auth/v1/authorization/users/$CENTER_USER_ID" \
  -H "Authorization: Bearer $AUTH_CENTER_READER_TOKEN" | jq '.data'
```

成功时核对 `data.tenantId`、`data.userId` 与中心映射一致，`permissions` 仅含有效权限编码，`roles` 含有效角色编码与范围。不要输出或保存真实 Token 到测试报告。

| 场景 | 预期与检查点 |
| --- | --- |
| 开关关闭 | 主应用保持原有授权来源；中心故障不影响该分支 |
| 开关开启、有效映射、缓存 0 | 权限与角色来自中心，不能依赖本地角色或 Token 中旧角色放行 |
| 无 Token、失效 Token | 安全链拒绝请求；记录实际 HTTP 状态 |
| 非读取账号的有效 Token | 403；管理员身份不能替代读取账号资格 |
| 路径不是正整数 | 合资格读取账号请求返回 400 |
| 读取账号/目标账号停用、锁定、软删除或跨租户 | 拒绝读取，不能返回授权快照 |
| 主租户上下文缺失、映射缺失或重复 | 拒绝授权，不能默认按相同 ID 查询 |
| 中心响应租户/用户不匹配、列表无效 | 客户端拒绝响应，不能回退本地权限 |
| HTTPS 配置非法、中心不可达、超时、熔断 | 受保护主应用请求拒绝授权；超时 3 秒，无过期缓存降级 |
| 缓存 0–30 秒 | 验证主租户/用户隔离、快照过期及撤权后的 TTL 延迟 |
| TTL 小于 0 或大于 30 | `UnifiedAuthorizationService` 构造失败 |
| 部门/自定义范围 | DEPT 与 DEPT_AND_CHILD 使用主应用已认证且启用的部门信息；CUSTOM、空值和未知中心范围拒绝，中心部门 ID 不直接用于主库 |

本矩阵包含中心端 HTTP 和主应用服务/切面验证；远程异常的最终 HTTP 状态取决于调用路径的异常处理，不能把所有失败都断言为 403。

## 自动化与文档检查

从仓库根目录加载 `.sdkmanrc` 环境后运行。以下是待执行命令，不表示已经通过；服务联调另需数据库、缓存、账号与 HTTPS 入口。

```bash
sdk env
mvnd -pl apex-auth-center -am test
mvnd -pl apex-auth-center -am spotless:check pmd:check
bash scripts/ci/check-doc-consistency.sh
bash scripts/ci/check-springdoc-coverage.sh
```

已有针对性测试包括 `UnifiedAuthorizationQueryServiceTest`、`UnifiedAuthorizationHandlerTest`、主应用 `UnifiedAuthorizationServiceTest`、`AuthCenterPermissionClientTest`、`RequirePermissionAspectTest`、`JwtAuthenticationFilterTest` 和数据权限测试。SpringDoc 脚本当前扫描主应用，不能代替认证中心 Router 审计或运行时 `/v3/api-docs` 验证。

---

## 测试结果记录

| 测试项 | 状态 | 备注 |
|--------|------|------|
| 用户登录 | ⬜ | |
| 错误密码 | ⬜ | |
| 账户锁定 | ⬜ | |
| Token 刷新 | ⬜ | |
| 用户登出 | ⬜ | |
| 租户隔离 | 未执行 | |
| 统一授权与失败拒绝 | 未执行 | |
| 缓存隔离与撤权延迟 | 未执行 | |
| 运行时 OpenAPI 契约 | 未执行 | |

**测试人**: ____________  
**测试日期**: ____________
**版本**: v1.3.1
