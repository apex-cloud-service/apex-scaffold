# PKCE 功能端到端集成测试脚本

## 文档信息

| 项目 | 内容 |
|------|------|
| **测试类型** | 端到端集成测试 (E2E) |
| **测试范围** | PKCE 完整授权流程 |
| **测试工具** | curl / Postman / 自动化脚本 |
| **环境要求** | 本地开发环境 / 测试环境 |
| **创建日期** | 2026-09-08 |

---

## 一、测试环境准备

### 1.1 启动服务

```bash
# 切换到项目目录
cd /Users/work/apex-scaffold

# 使用 mvnd 启动应用
mvnd spring-boot:run -pl apex-interfaces
```

**预期输出**：
```
Started ApexScaffoldApplication in X.XXX seconds
```

**服务地址**：`http://localhost:8080`

---

### 1.2 测试数据准备

#### 创建测试客户端

```bash
# 使用管理员账号登录
# 在系统中注册 OAuth2 客户端

客户端配置：
- client_id: pkce-test-client
- client_secret: test-secret-12345
- redirect_uris: http://localhost:3000/callback
- grant_types: authorization_code, refresh_token
- scopes: read, write, user:profile
```

#### 创建测试用户

```bash
用户信息：
- username: test-user
- password: Test@123456
- email: testuser@example.com
```

---

## 二、PKCE S256 完整流程测试

### 2.1 生成 PKCE 参数

#### 方法 1：使用 OpenSSL (命令行)

```bash
# 生成 code_verifier (43-128 字符的随机字符串)
code_verifier=$(openssl rand -base64 32 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
echo "code_verifier: $code_verifier"

# 计算 code_challenge (SHA256 + Base64Url)
code_challenge=$(echo -n "$code_verifier" | openssl dgst -binary -sha256 | openssl base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
echo "code_challenge: $code_challenge"
```

#### 方法 2：使用 Node.js

```javascript
// generate-pkce.js
const crypto = require('crypto');

function base64URLEncode(str) {
    return str.toString('base64')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=/g, '');
}

function sha256(buffer) {
    return crypto.createHash('sha256').update(buffer).digest();
}

// 生成 code_verifier
const verifier = base64URLEncode(crypto.randomBytes(32));
console.log('code_verifier:', verifier);

// 计算 code_challenge
const challenge = base64URLEncode(sha256(verifier));
console.log('code_challenge:', challenge);
```

运行：
```bash
node generate-pkce.js
```

#### 方法 3：使用 Python

```python
# generate_pkce.py
import base64
import hashlib
import secrets

def base64_url_encode(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('utf-8')

# 生成 code_verifier
code_verifier = base64_url_encode(secrets.token_bytes(32))
print(f"code_verifier: {code_verifier}")

# 计算 code_challenge
code_challenge = base64_url_encode(
    hashlib.sha256(code_verifier.encode('utf-8')).digest()
)
print(f"code_challenge: {code_challenge}")
```

运行：
```bash
python3 generate_pkce.py
```

**示例输出**：
```
code_verifier: dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
code_challenge: E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
```

---

### 2.2 步骤 1：请求授权码

#### HTTP 请求

```http
GET /oauth2/authorize?response_type=code&client_id=pkce-test-client&redirect_uri=http://localhost:3000/callback&scope=read%20write&state=xyz123&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256 HTTP/1.1
Host: localhost:8080
```

#### curl 命令

```bash
# 设置变量
CLIENT_ID="pkce-test-client"
REDIRECT_URI="http://localhost:3000/callback"
SCOPE="read write"
STATE="xyz123"
CODE_CHALLENGE="E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
CODE_CHALLENGE_METHOD="S256"

# 请求授权页面（需要用户登录和授权）
curl -v -X GET "http://localhost:8080/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=${SCOPE}&state=${STATE}&code_challenge=${CODE_CHALLENGE}&code_challenge_method=${CODE_CHALLENGE_METHOD}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -c cookies.txt
```

#### 预期响应

**场景 1：用户未登录**
```http
HTTP/1.1 302 Found
Location: /login?redirect=/oauth2/authorize?...
```

**场景 2：用户已登录，显示授权页面**
```http
HTTP/1.1 200 OK
Content-Type: text/html

<!-- 授权确认页面 -->
<form action="/oauth2/authorize" method="post">
  <input type="hidden" name="client_id" value="pkce-test-client">
  <input type="hidden" name="scope" value="read write">
  <button type="submit" name="authorize" value="true">授权</button>
  <button type="submit" name="authorize" value="false">拒绝</button>
</form>
```

---

### 2.3 步骤 2：用户登录（如果需要）

```bash
# 用户登录
curl -v -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test-user",
    "password": "Test@123456",
    "loginType": "password"
  }' \
  -c cookies.txt \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "refreshToken": "...",
    "user": {
      "userId": "user-123",
      "username": "test-user"
    }
  }
}
```

---

### 2.4 步骤 3：用户授权

```bash
# 提交授权确认
curl -v -X POST "http://localhost:8080/oauth2/authorize" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "scope=${SCOPE}" \
  -d "state=${STATE}" \
  -d "code_challenge=${CODE_CHALLENGE}" \
  -d "code_challenge_method=${CODE_CHALLENGE_METHOD}" \
  -d "authorize=true" \
  -b cookies.txt \
  -L
```

**预期响应**：
```http
HTTP/1.1 302 Found
Location: http://localhost:3000/callback?code=abc123xyz&state=xyz123
```

**提取授权码**：
```bash
# 从 Location 头提取授权码
AUTHORIZATION_CODE="abc123xyz"
echo "Authorization Code: $AUTHORIZATION_CODE"
```

---

### 2.5 步骤 4：用授权码换取 Token（使用 PKCE）

```bash
# 使用授权码 + code_verifier 换取 Token
curl -v -X POST "http://localhost:8080/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=${AUTHORIZATION_CODE}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=test-secret-12345" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "code_verifier=${code_verifier}"
```

**预期响应**（成功）：
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJjbGllbnRfaWQiOiJwa2NlLXRlc3QtY2xpZW50IiwidXNlcl9pZCI6InVzZXItMTIzIiwic2NvcGUiOiJyZWFkIHdyaXRlIiwiaWF0IjoxNzI1Nzc4ODAwLCJleHAiOjE3MjU3ODYwMDB9.xxx",
  "token_type": "Bearer",
  "expires_in": 7200,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJjbGllbnRfaWQiOiJwa2NlLXRlc3QtY2xpZW50IiwidXNlcl9pZCI6InVzZXItMTIzIiwic2NvcGUiOiJyZWFkIHdyaXRlIiwiaWF0IjoxNzI1Nzc4ODAwLCJleHAiOjE3MjYzODM2MDB9.yyy",
  "scope": "read write"
}
```

---

### 2.6 步骤 5：使用 Access Token 访问资源

```bash
# 保存 access_token
ACCESS_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# 访问受保护资源
curl -v -X GET "http://localhost:8080/api/user/profile" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**预期响应**：
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "userId": "user-123",
    "username": "test-user",
    "email": "testuser@example.com"
  }
}
```

---

## 三、PKCE 验证失败测试

### 3.1 使用错误的 code_verifier

```bash
# 使用与 code_challenge 不匹配的 code_verifier
WRONG_VERIFIER="wrong-verifier-value-that-does-not-match"

curl -v -X POST "http://localhost:8080/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=${AUTHORIZATION_CODE}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=test-secret-12345" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "code_verifier=${WRONG_VERIFIER}"
```

**预期响应**（失败）：
```json
{
  "code": 401,
  "message": "PKCE验证失败",
  "data": null
}
```

---

### 3.2 缺少 code_verifier（当授权码包含 challenge 时）

```bash
# 不提供 code_verifier
curl -v -X POST "http://localhost:8080/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=${AUTHORIZATION_CODE}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=test-secret-12345" \
  -d "redirect_uri=${REDIRECT_URI}"
```

**预期响应**（失败）：
```json
{
  "code": 401,
  "message": "PKCE验证失败",
  "data": null
}
```

---

## 四、PKCE plain 方法测试

### 4.1 生成 PKCE 参数（plain 模式）

```bash
# plain 模式：code_challenge = code_verifier
code_verifier_plain="plain-code-verifier-for-testing-purpose"
code_challenge_plain="plain-code-verifier-for-testing-purpose"
code_challenge_method_plain="plain"

echo "code_verifier: $code_verifier_plain"
echo "code_challenge: $code_challenge_plain"
echo "method: $code_challenge_method_plain"
```

### 4.2 请求授权码（plain 模式）

```bash
curl -v -X GET "http://localhost:8080/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=${SCOPE}&state=${STATE}&code_challenge=${code_challenge_plain}&code_challenge_method=${code_challenge_method_plain}" \
  -b cookies.txt \
  -L
```

### 4.3 换取 Token（plain 模式）

```bash
# 使用相同的 code_verifier
curl -v -X POST "http://localhost:8080/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=${AUTHORIZATION_CODE_PLAIN}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=test-secret-12345" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "code_verifier=${code_verifier_plain}"
```

**预期响应**：成功返回 Token

---

## 五、自动化测试脚本

### 5.1 Bash 完整测试脚本

```bash
#!/bin/bash
# pkce-e2e-test.sh

set -e

# 配置
BASE_URL="http://localhost:8080"
CLIENT_ID="pkce-test-client"
CLIENT_SECRET="test-secret-12345"
REDIRECT_URI="http://localhost:3000/callback"
USERNAME="test-user"
PASSWORD="Test@123456"

echo "=========================================="
echo "PKCE 端到端测试脚本"
echo "=========================================="

# 1. 生成 PKCE 参数
echo "[1/6] 生成 PKCE 参数..."
code_verifier=$(openssl rand -base64 32 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
code_challenge=$(echo -n "$code_verifier" | openssl dgst -binary -sha256 | openssl base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')

echo "  code_verifier: $code_verifier"
echo "  code_challenge: $code_challenge"

# 2. 用户登录
echo "[2/6] 用户登录..."
login_response=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\",\"loginType\":\"password\"}" \
  -c cookies.txt)

echo "  登录响应: $login_response"

# 检查登录是否成功
if echo "$login_response" | grep -q '"code":200'; then
    echo "  ✅ 登录成功"
else
    echo "  ❌ 登录失败"
    exit 1
fi

# 3. 请求授权（自动授权，需要后端支持）
echo "[3/6] 请求授权码..."
# 注意：实际流程需要用户手动授权，这里假设后端支持自动授权或预授权
authorize_url="${BASE_URL}/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=read%20write&state=xyz123&code_challenge=${code_challenge}&code_challenge_method=S256&auto_approve=true"

# 获取授权码（从重定向 Location 提取）
location_header=$(curl -s -I -b cookies.txt -L "$authorize_url" | grep -i "Location:" | tail -1)
auth_code=$(echo "$location_header" | sed -n 's/.*code=\([^&]*\).*/\1/p' | tr -d '\r\n')

if [ -z "$auth_code" ]; then
    echo "  ❌ 未能获取授权码"
    echo "  提示：请手动访问授权页面并授权，然后从回调 URL 中提取授权码"
    exit 1
else
    echo "  ✅ 授权码: $auth_code"
fi

# 4. 换取 Token（正确的 code_verifier）
echo "[4/6] 换取 Token (正确的 code_verifier)..."
token_response=$(curl -s -X POST "${BASE_URL}/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=${auth_code}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "code_verifier=${code_verifier}")

echo "  Token 响应: $token_response"

# 检查是否成功获取 Token
if echo "$token_response" | grep -q '"access_token"'; then
    echo "  ✅ 成功获取 Token"
    access_token=$(echo "$token_response" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
    echo "  Access Token: ${access_token:0:50}..."
else
    echo "  ❌ 获取 Token 失败"
    exit 1
fi

# 5. 测试错误的 code_verifier（需要新的授权码）
echo "[5/6] 测试错误的 code_verifier..."
echo "  (跳过：需要新的授权码，避免授权码重复使用)"

# 6. 使用 Token 访问资源
echo "[6/6] 使用 Token 访问资源..."
api_response=$(curl -s -X GET "${BASE_URL}/api/user/profile" \
  -H "Authorization: Bearer ${access_token}")

echo "  API 响应: $api_response"

if echo "$api_response" | grep -q '"code":200'; then
    echo "  ✅ 成功访问受保护资源"
else
    echo "  ❌ 访问资源失败"
    exit 1
fi

echo "=========================================="
echo "✅ PKCE 端到端测试全部通过！"
echo "=========================================="

# 清理
rm -f cookies.txt
```

**运行脚本**：
```bash
chmod +x pkce-e2e-test.sh
./pkce-e2e-test.sh
```

---

### 5.2 Postman 集合

```json
{
  "info": {
    "name": "PKCE E2E Tests",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080"
    },
    {
      "key": "client_id",
      "value": "pkce-test-client"
    },
    {
      "key": "client_secret",
      "value": "test-secret-12345"
    },
    {
      "key": "redirect_uri",
      "value": "http://localhost:3000/callback"
    },
    {
      "key": "code_verifier",
      "value": ""
    },
    {
      "key": "code_challenge",
      "value": ""
    },
    {
      "key": "authorization_code",
      "value": ""
    },
    {
      "key": "access_token",
      "value": ""
    }
  ],
  "item": [
    {
      "name": "1. Generate PKCE Parameters",
      "event": [
        {
          "listen": "prerequest",
          "script": {
            "exec": [
              "// 生成 code_verifier",
              "const codeVerifier = CryptoJS.lib.WordArray.random(32).toString(CryptoJS.enc.Base64url);",
              "pm.collectionVariables.set('code_verifier', codeVerifier);",
              "",
              "// 计算 code_challenge",
              "const hash = CryptoJS.SHA256(codeVerifier);",
              "const codeChallenge = CryptoJS.enc.Base64url.stringify(hash);",
              "pm.collectionVariables.set('code_challenge', codeChallenge);",
              "",
              "console.log('code_verifier:', codeVerifier);",
              "console.log('code_challenge:', codeChallenge);"
            ]
          }
        }
      ],
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "{{base_url}}/oauth2/authorize?response_type=code&client_id={{client_id}}&redirect_uri={{redirect_uri}}&scope=read write&state=xyz123&code_challenge={{code_challenge}}&code_challenge_method=S256",
          "host": ["{{base_url}}"],
          "path": ["oauth2", "authorize"],
          "query": [
            {"key": "response_type", "value": "code"},
            {"key": "client_id", "value": "{{client_id}}"},
            {"key": "redirect_uri", "value": "{{redirect_uri}}"},
            {"key": "scope", "value": "read write"},
            {"key": "state", "value": "xyz123"},
            {"key": "code_challenge", "value": "{{code_challenge}}"},
            {"key": "code_challenge_method", "value": "S256"}
          ]
        }
      }
    },
    {
      "name": "2. User Login",
      "event": [
        {
          "listen": "test",
          "script": {
            "exec": [
              "pm.test('Login successful', function() {",
              "    pm.response.to.have.status(200);",
              "    const jsonData = pm.response.json();",
              "    pm.expect(jsonData.code).to.eql(200);",
              "});"
            ]
          }
        }
      ],
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"username\": \"test-user\",\n  \"password\": \"Test@123456\",\n  \"loginType\": \"password\"\n}"
        },
        "url": "{{base_url}}/api/auth/login"
      }
    },
    {
      "name": "3. Exchange Code for Token (PKCE)",
      "event": [
        {
          "listen": "test",
          "script": {
            "exec": [
              "pm.test('Token exchange successful', function() {",
              "    pm.response.to.have.status(200);",
              "    const jsonData = pm.response.json();",
              "    pm.expect(jsonData.access_token).to.exist;",
              "    pm.collectionVariables.set('access_token', jsonData.access_token);",
              "});"
            ]
          }
        }
      ],
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/x-www-form-urlencoded"
          }
        ],
        "body": {
          "mode": "urlencoded",
          "urlencoded": [
            {"key": "grant_type", "value": "authorization_code"},
            {"key": "code", "value": "{{authorization_code}}"},
            {"key": "client_id", "value": "{{client_id}}"},
            {"key": "client_secret", "value": "{{client_secret}}"},
            {"key": "redirect_uri", "value": "{{redirect_uri}}"},
            {"key": "code_verifier", "value": "{{code_verifier}}"}
          ]
        },
        "url": "{{base_url}}/oauth2/token"
      }
    },
    {
      "name": "4. Access Protected Resource",
      "event": [
        {
          "listen": "test",
          "script": {
            "exec": [
              "pm.test('Access resource successful', function() {",
              "    pm.response.to.have.status(200);",
              "    const jsonData = pm.response.json();",
              "    pm.expect(jsonData.code).to.eql(200);",
              "});"
            ]
          }
        }
      ],
      "request": {
        "method": "GET",
        "header": [
          {
            "key": "Authorization",
            "value": "Bearer {{access_token}}"
          }
        ],
        "url": "{{base_url}}/api/user/profile"
      }
    },
    {
      "name": "5. Test Wrong Code Verifier (Negative)",
      "event": [
        {
          "listen": "test",
          "script": {
            "exec": [
              "pm.test('PKCE verification should fail', function() {",
              "    pm.response.to.have.status(401);",
              "    const jsonData = pm.response.json();",
              "    pm.expect(jsonData.message).to.include('PKCE');",
              "});"
            ]
          }
        }
      ],
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/x-www-form-urlencoded"
          }
        ],
        "body": {
          "mode": "urlencoded",
          "urlencoded": [
            {"key": "grant_type", "value": "authorization_code"},
            {"key": "code", "value": "{{authorization_code}}"},
            {"key": "client_id", "value": "{{client_id}}"},
            {"key": "client_secret", "value": "{{client_secret}}"},
            {"key": "redirect_uri", "value": "{{redirect_uri}}"},
            {"key": "code_verifier", "value": "wrong-verifier-value"}
          ]
        },
        "url": "{{base_url}}/oauth2/token"
      }
    }
  ]
}
```

**导入步骤**：
1. 打开 Postman
2. Import → Raw text
3. 粘贴上述 JSON
4. 运行集合

---

## 六、测试验证清单

### 6.1 功能验证

- [ ] **S256 正常流程**
  - [ ] 生成符合规范的 code_verifier 和 code_challenge
  - [ ] 成功请求授权码
  - [ ] 使用正确的 code_verifier 成功换取 Token
  - [ ] 使用 Token 成功访问受保护资源

- [ ] **PKCE 验证失败**
  - [ ] 使用错误的 code_verifier 失败（401 错误）
  - [ ] 缺少 code_verifier 失败（401 错误）
  - [ ] 错误消息包含 "PKCE验证失败"

- [ ] **plain 方法**
  - [ ] code_challenge = code_verifier
  - [ ] 成功换取 Token

- [ ] **向后兼容**
  - [ ] 不使用 PKCE 也能正常换取 Token

### 6.2 安全验证

- [ ] **授权码一次性使用**
  - [ ] 使用同一授权码第二次换取 Token 失败

- [ ] **授权码过期**
  - [ ] 5 分钟后授权码失效

- [ ] **clientId 绑定**
  - [ ] 使用不同 clientId 换取 Token 失败

- [ ] **redirectUri 绑定**
  - [ ] 使用不同 redirectUri 换取 Token 失败

### 6.3 性能验证

- [ ] **响应时间**
  - [ ] Token 换取响应时间 < 500ms
  - [ ] PKCE 验证耗时 < 10ms

- [ ] **并发测试**
  - [ ] 100 并发请求无错误
  - [ ] 授权码不会被重复使用

---

## 七、故障排查

### 7.1 常见问题

#### 问题 1：无法获取授权码

**现象**：
- `/oauth2/authorize` 返回 404 或 500

**排查步骤**：
1. 检查服务是否启动
2. 检查客户端是否已注册
3. 检查 redirect_uri 是否匹配
4. 检查用户是否已登录

---

#### 问题 2：PKCE 验证失败

**现象**：
- 返回 401，消息 "PKCE验证失败"

**排查步骤**：
1. 检查 code_verifier 是否与生成 code_challenge 时使用的一致
2. 检查 code_challenge_method 是否正确（S256 或 plain）
3. 检查 Base64Url 编码是否正确（无 `=` 填充，`+` 替换为 `-`，`/` 替换为 `_`）

**调试命令**：
```bash
# 验证 SHA-256 哈希
echo -n "你的code_verifier" | openssl dgst -sha256 -binary | base64

# 验证 Base64Url 编码
echo -n "你的code_verifier" | openssl dgst -sha256 -binary | base64 | tr -d '=' | tr '/+' '_-'
```

---

#### 问题 3：授权码已过期

**现象**：
- 返回 401，消息 "无效或已过期的授权码"

**解决方案**：
- 授权码有效期 5 分钟，请在获取后立即使用
- 如果需要延长有效期，修改 `AUTH_CODE_TTL` 配置

---

#### 问题 4：Token 无法访问资源

**现象**：
- 使用 Token 访问 API 返回 401

**排查步骤**：
1. 检查 Token 是否过期
2. 检查 Authorization 头格式：`Bearer <token>`
3. 检查 scope 是否包含所需权限
4. 检查 JWT 签名是否正确

---

## 八、附录

### 8.1 PKCE 参数生成工具

#### 在线工具
- https://tonyxu-io.github.io/pkce-generator/
- https://example-app.com/pkce

#### 命令行工具
```bash
# 安装 pkce 工具
npm install -g pkce-challenge

# 生成 PKCE 参数
pkce
```

### 8.2 参考资料

- RFC 7636: https://datatracker.ietf.org/doc/html/rfc7636
- OAuth 2.0 PKCE: https://oauth.net/2/pkce/
- Spring Security OAuth2: https://spring.io/projects/spring-security-oauth

---

**脚本版本**：v1.0  
**创建日期**：2026-09-08  
**维护人员**：开发团队
