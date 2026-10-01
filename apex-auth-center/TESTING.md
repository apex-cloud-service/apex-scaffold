# 认证中心核心功能验证指南

## 测试环境

- **应用端口**: 8081
- **数据库**: MySQL（默认 localhost:3306/apex_auth）
- **测试用户**:
  - 用户名: `admin` / 密码: `password123`
  - 用户名: `user` / 密码: `password123`
- **租户ID**: `default`

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
  "userId": 1,
  "username": "admin",
  "tenantId": "default",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200
}
```

**验证点**:
- ✅ HTTP 状态码 200
- ✅ 返回 `accessToken` 和 `refreshToken`
- ✅ `expiresIn` 为 7200（2小时）
- ✅ `userId` 和 `username` 正确

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
- ✅ HTTP 状态码 401
- ✅ 返回错误提示
- ✅ 数据库 `failed_login_attempts` 递增

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
- ✅ 5次失败后账户锁定
- ✅ `status` 设置为 2
- ✅ `locked_until` 设置为 30 分钟后

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
echo "Refresh Token: $REFRESH_TOKEN"
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
- ✅ HTTP 状态码 200
- ✅ 返回新的 `accessToken`
- ✅ `refreshToken` 不变（不轮换）

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
curl -X POST "http://localhost:8081/auth/v1/logout/$USER_ID?tenantId=default" \
  -H "Content-Type: application/json"
```

**期望响应** (HTTP 200):
```
登出成功
```

**步骤3**: 验证 Token 已撤销
```bash
# 使用已撤销的 Token（需要实现受保护接口才能验证）
curl -X GET http://localhost:8081/auth/v1/verify \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**期望**: HTTP 401 "Token已撤销"

**验证点**:
- ✅ 登出成功
- ✅ 数据库 `auth_token` 表对应记录 `revoked=1`
- ✅ 已撤销 Token 无法通过验证

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
- ✅ 跨租户登录失败
- ✅ 用户名匹配但租户不匹配时拒绝

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

### 查询审计日志（如果实现）
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

-- 如果需要清空重建
DROP DATABASE apex_auth;
CREATE DATABASE apex_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

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

## 自动化测试脚本

```bash
#!/bin/bash
# test-auth-center.sh - 自动化验证脚本

BASE_URL="http://localhost:8081"

echo "=== 测试1: 用户登录 ==="
LOGIN_RESPONSE=$(curl -s -X POST $BASE_URL/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123",
    "tenantId": "default"
  }')

if echo "$LOGIN_RESPONSE" | jq -e '.accessToken' > /dev/null; then
  echo "✅ 登录成功"
  ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')
  REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.refreshToken')
  USER_ID=$(echo $LOGIN_RESPONSE | jq -r '.userId')
else
  echo "❌ 登录失败"
  echo "$LOGIN_RESPONSE"
  exit 1
fi

echo ""
echo "=== 测试2: Token 刷新 ==="
REFRESH_RESPONSE=$(curl -s -X POST $BASE_URL/auth/v1/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")

if echo "$REFRESH_RESPONSE" | jq -e '.accessToken' > /dev/null; then
  echo "✅ Token 刷新成功"
else
  echo "❌ Token 刷新失败"
  echo "$REFRESH_RESPONSE"
fi

echo ""
echo "=== 测试3: 用户登出 ==="
LOGOUT_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/v1/logout/$USER_ID?tenantId=default")

if [ "$LOGOUT_RESPONSE" == "登出成功" ]; then
  echo "✅ 登出成功"
else
  echo "❌ 登出失败: $LOGOUT_RESPONSE"
fi

echo ""
echo "=== 所有测试完成 ==="
```

**运行**:
```bash
chmod +x test-auth-center.sh
./test-auth-center.sh
```

---

## 测试结果记录

| 测试项 | 状态 | 备注 |
|--------|------|------|
| 用户登录 | ⬜ | |
| 错误密码 | ⬜ | |
| 账户锁定 | ⬜ | |
| Token 刷新 | ⬜ | |
| 用户登出 | ⬜ | |
| 租户隔离 | ⬜ | |

**测试人**: ____________  
**测试日期**: 2026-09-20  
**版本**: v1.2.0
