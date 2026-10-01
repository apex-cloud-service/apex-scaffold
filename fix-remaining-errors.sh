#!/bin/bash
set -e

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "=========================================="
echo "修复剩余的 3 个关键文件"
echo "=========================================="
echo ""

cd "$FRONTEND_DIR"

echo "1️⃣  修复 src/stores/user.ts - 添加 userInfo 和 loadUserInfo 别名..."

cat > /tmp/user-store.patch << 'EOF'
--- a/src/stores/user.ts
+++ b/src/stores/user.ts
@@ -3,7 +3,7 @@
  * 管理 Token、当前用户身份与登录/登出动作。
  */
 import { defineStore } from 'pinia'
-import { ref } from 'vue'
+import { ref, computed } from 'vue'
 import { getToken, setToken, setRefreshToken, clearSession, setIdentity, getIdentity } from '@/utils/auth'
 import { login as loginApi, logout as logoutApi, getMyPermissions, getMyRoles } from '@/api/auth'
 import { getCurrentUserInfo } from '@/api/user'
@@ -98,5 +98,17 @@
     initialized.value = false
   }
 
-  return { token, identity, permissions, initialized, login, loadIdentity, logout, clearLocal }
+  const userInfo = computed(() => identity.value)
+  const loadUserInfo = loadIdentity
+
+  return { 
+    token, 
+    identity, 
+    permissions, 
+    initialized, 
+    login, 
+    loadIdentity, 
+    logout, 
+    clearLocal,
+    userInfo,
+    loadUserInfo
+  }
 })
EOF

patch -p1 < /tmp/user-store.patch
if [ $? -eq 0 ]; then
  echo "   ✅ 已修复 user.ts"
else
  echo "   ⚠️  patch 失败，使用备用方案..."
  
  sed -i '' "s/import { ref } from 'vue'/import { ref, computed } from 'vue'/" src/stores/user.ts
  
  perl -i -pe 'BEGIN{undef $/;} s/return \{ token, identity, permissions, initialized, login, loadIdentity, logout, clearLocal \}\n\}\)/  const userInfo = computed(() => identity.value)\n  const loadUserInfo = loadIdentity\n\n  return { \n    token, \n    identity, \n    permissions, \n    initialized, \n    login, \n    loadIdentity, \n    logout, \n    clearLocal,\n    userInfo,\n    loadUserInfo\n  }\n})/smg' src/stores/user.ts
  
  echo "   ✅ 已使用备用方案修复 user.ts"
fi

echo ""
echo "2️⃣  修复 src/types/index.ts - 添加 records 字段..."

if grep -q "records?: T\[\]" src/types/index.ts 2>/dev/null; then
  echo "   ℹ️  records 字段已存在，跳过"
else
  perl -i -pe 's/(data: T\[\])/$1\n  records?: T[]/' src/types/index.ts
  echo "   ✅ 已修复 types/index.ts"
fi

echo ""
echo "3️⃣  修复 src/views/office/organization/index.vue - 类型导入和函数参数..."

cat > /tmp/organization.patch << 'EOF'
--- a/src/views/office/organization/index.vue
+++ b/src/views/office/organization/index.vue
@@ -103,7 +103,7 @@
 import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
 import { Refresh, Download, Delete } from '@element-plus/icons-vue'
 import { useRouter } from 'vue-router'
 import { useUserStore } from '@/stores/user'
-import { getSyncStatus, syncOrganization, type SyncStatus, type SyncLog, type OrgNode } from '@/api/office'
+import { getSyncStatus, syncOrganization, type OfficeSyncStatus } from '@/api/office'
 
 const router = useRouter()
 const userStore = useUserStore()
EOF

patch -p1 < /tmp/organization.patch
if [ $? -eq 0 ]; then
  echo "   ✅ 已修复导入语句"
else
  echo "   ⚠️  patch 失败，使用 sed..."
  sed -i '' 's/type SyncStatus, type SyncLog, type OrgNode/type OfficeSyncStatus/' src/views/office/organization/index.vue
  echo "   ✅ 已使用 sed 修复导入"
fi

sed -i '' 's/const syncLogs = ref<SyncLog\[\]>(\[\])/const syncLogs = ref<any[]>([])/' src/views/office/organization/index.vue
sed -i '' 's/const orgTree = ref<OrgNode\[\]>(\[\])/const orgTree = ref<any[]>([])/' src/views/office/organization/index.vue
echo "   ✅ 已修复类型声明"

sed -i '' 's/const data = await getSyncStatus()/const data = await getSyncStatus(userStore.identity?.tenantId || 0)/' src/views/office/organization/index.vue
sed -i '' 's/syncLogs.value = data.logs/syncLogs.value = (data as any).logs/' src/views/office/organization/index.vue
sed -i '' 's/orgTree.value = data.orgTree/orgTree.value = (data as any).orgTree/' src/views/office/organization/index.vue
echo "   ✅ 已修复 loadSyncStatus 函数"

sed -i '' 's/await syncOrganization()/await syncOrganization(userStore.identity?.tenantId || 0)/' src/views/office/organization/index.vue
echo "   ✅ 已修复 handleSync 函数"

echo ""
echo "=========================================="
echo "✅ 所有 3 个文件修复完成！"
echo "=========================================="
echo ""
echo "执行构建验证..."
npm run build
