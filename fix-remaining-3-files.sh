#!/bin/bash
set -e

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "=========================================="
echo "修复剩余的 3 个关键文件"
echo "=========================================="
echo ""

cd "$FRONTEND_DIR"

echo "1️⃣  修复 src/stores/user.ts..."

# 1. 添加 computed 导入
if ! grep -q "import { ref, computed } from 'vue'" src/stores/user.ts; then
  sed -i '' "s/import { ref } from 'vue'/import { ref, computed } from 'vue'/" src/stores/user.ts
  echo "   ✅ 已添加 computed 导入"
else
  echo "   ℹ️  computed 已导入，跳过"
fi

# 2. 在 return 语句前添加 userInfo 和 loadUserInfo
if ! grep -q "const userInfo = computed" src/stores/user.ts; then
  # 使用 perl 在 return 语句前插入新代码
  perl -i -pe 'BEGIN{undef $/;} s/(  function clearLocal\(\): void \{[^}]+\})\n\n(  return \{)/\1\n\n  const userInfo = computed(() => identity.value)\n  const loadUserInfo = loadIdentity\n\n\2/' src/stores/user.ts
  
  # 更新 return 语句添加新的导出
  perl -i -pe 'BEGIN{undef $/;} s/return \{ token, identity, permissions, initialized, login, loadIdentity, logout, clearLocal \}/return { \n    token, \n    identity, \n    permissions, \n    initialized, \n    login, \n    loadIdentity, \n    logout, \n    clearLocal,\n    userInfo,\n    loadUserInfo\n  }/smg' src/stores/user.ts
  
  echo "   ✅ 已添加 userInfo 和 loadUserInfo"
else
  echo "   ℹ️  userInfo 已存在，跳过"
fi

echo ""
echo "2️⃣  修复 src/types/index.ts..."

# 在 PageResponse 接口中添加 records 字段
if ! grep -q "records?: T\[\]" src/types/index.ts; then
  perl -i -pe 's/(  data: T\[\])$/\1\n  records?: T[]/' src/types/index.ts
  echo "   ✅ 已添加 records 字段"
else
  echo "   ℹ️  records 字段已存在，跳过"
fi

echo ""
echo "3️⃣  修复 src/views/office/organization/index.vue..."

# 修复导入语句
sed -i '' 's/getSyncStatus, syncOrganization, type SyncStatus, type SyncLog, type OrgNode/getSyncStatus, syncOrganization, type OfficeSyncStatus/' src/views/office/organization/index.vue
echo "   ✅ 已修复导入语句"

# 修复类型声明
sed -i '' 's/const syncLogs = ref<SyncLog\[\]>(\[\])/const syncLogs = ref<any[]>([])/' src/views/office/organization/index.vue
sed -i '' 's/const orgTree = ref<OrgNode\[\]>(\[\])/const orgTree = ref<any[]>([])/' src/views/office/organization/index.vue
echo "   ✅ 已修复类型声明"

# 修复 loadSyncStatus 函数
sed -i '' 's/const data = await getSyncStatus()/const data = await getSyncStatus(userStore.identity?.tenantId || 0)/' src/views/office/organization/index.vue
sed -i '' 's/syncLogs\.value = data\.logs/syncLogs.value = (data as any).logs/' src/views/office/organization/index.vue
sed -i '' 's/orgTree\.value = data\.orgTree/orgTree.value = (data as any).orgTree/' src/views/office/organization/index.vue
echo "   ✅ 已修复 loadSyncStatus 函数"

# 修复 handleSync 函数
sed -i '' 's/await syncOrganization()/await syncOrganization(userStore.identity?.tenantId || 0)/' src/views/office/organization/index.vue
echo "   ✅ 已修复 handleSync 函数"

echo ""
echo "=========================================="
echo "✅ 所有 3 个文件修复完成！"
echo "=========================================="
echo ""
echo "执行构建验证..."
npm run build
