#!/bin/bash

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM_NAME="${REALM_NAME:-apex}"
CLIENT_ID="${CLIENT_ID:-apex-client}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"

echo "=== Keycloak Realm 初始化脚本 ==="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM_NAME"
echo "Client ID: $CLIENT_ID"
echo ""

# 1. 获取 admin access token
echo "[1/3] 获取 admin access token..."
ACCESS_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASSWORD" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ "$ACCESS_TOKEN" = "null" ] || [ -z "$ACCESS_TOKEN" ]; then
  echo "错误：无法获取 access token"
  exit 1
fi

echo "✅ 成功获取 access token"

# 2. 创建 realm
echo ""
echo "[2/3] 创建 realm: $REALM_NAME..."
curl -s -X POST "$KEYCLOAK_URL/admin/realms" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"realm\": \"$REALM_NAME\",
    \"enabled\": true,
    \"displayName\": \"Apex Scaffold\",
    \"loginTheme\": \"keycloak\",
    \"accountTheme\": \"keycloak\",
    \"adminTheme\": \"keycloak\",
    \"emailTheme\": \"keycloak\"
  }"

echo "✅ 成功创建 realm"

# 3. 创建 public client
echo ""
echo "[3/3] 创建 client: $CLIENT_ID..."
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"$CLIENT_ID\",
    \"name\": \"Apex Scaffold Client\",
    \"description\": \"Apex Scaffold OAuth2 Client\",
    \"enabled\": true,
    \"clientAuthenticatorType\": \"client-secret\",
    \"redirectUris\": [
      \"http://localhost:8080/login/oauth2/code/keycloak\",
      \"http://localhost:3000/auth/callback\"
    ],
    \"webOrigins\": [
      \"http://localhost:8080\",
      \"http://localhost:3000\"
    ],
    \"publicClient\": true,
    \"standardFlowEnabled\": true,
    \"implicitFlowEnabled\": false,
    \"directAccessGrantsEnabled\": true,
    \"serviceAccountsEnabled\": false
  }"

echo "✅ 成功创建 client"

echo ""
echo "=== 初始化完成 ==="
echo "✅ Realm: $REALM_NAME"
echo "✅ Client: $CLIENT_ID"
echo ""
echo "验证："
echo "  curl $KEYCLOAK_URL/realms/$REALM_NAME/.well-known/openid-configuration"
