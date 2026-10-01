#!/bin/bash
set -e

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "=========================================="
echo "修复剩余的 18 个 TypeScript 类型错误"
echo "=========================================="
echo ""

cd "$FRONTEND_DIR"

echo "1️⃣  修复 organization/index.vue 的导入错误（还有残留）..."
sed -i '' 's/import { getSyncStatus, syncOrganization, type SyncStatus, type SyncLog, type OrgNode } from/import { getSyncStatus, syncOrganization } from/' src/views/office/organization/index.vue
echo "   ✅ 已彻底移除未导出的类型"

echo ""
echo "2️⃣  修复 organization/index.vue 的 userStore 引用..."
sed -i '' 's/getSyncStatus(userStore/getSyncStatus(useUserStore()/' src/views/office/organization/index.vue
sed -i '' 's/syncOrganization(userStore/syncOrganization(useUserStore()/' src/views/office/organization/index.vue
echo "   ✅ 已修复 userStore 调用"

echo ""
echo "3️⃣  修复 approval/index.vue 的 .data.records 引用..."
sed -i '' 's/response\.data\.data/response.data.data/g' src/views/office/approval/index.vue
perl -i -pe 's/\.data\.records/\.data\.data/g' src/views/office/approval/index.vue
echo "   ✅ 已修复 approval/index.vue"

echo ""
echo "4️⃣  修复 message/index.vue 的 .data.records 引用..."
perl -i -pe 's/\.data\.records/\.data\.data/g' src/views/office/message/index.vue
echo "   ✅ 已修复 message/index.vue"

echo ""
echo "5️⃣  修复 bindings/calendar/document 的类型断言..."
sed -i '' 's/platformNames\[platform\]/platformNames[platform as OfficePlatform]/g' src/views/office/bindings/index.vue
sed -i '' 's/platformNames\[record\.platform\]/platformNames[record.platform as OfficePlatform]/g' src/views/office/calendar/index.vue
sed -i '' 's/platformNames\[record\.platform\]/platformNames[record.platform as OfficePlatform]/g' src/views/office/document/index.vue
echo "   ✅ 已修复类型断言"

echo ""
echo "6️⃣  修复 callback.vue 的参数问题..."
sed -i '' 's/const state = /const state: string | undefined = /' src/views/office/callback.vue
sed -i '' 's/router\.push(state)/router.push(state ?? "\/dashboard")/' src/views/office/callback.vue
echo "   ✅ 已修复 callback.vue"

echo ""
echo "7️⃣  修复 console.vue 未使用变量..."
sed -i '' 's/const platform = /\/\/ const platform = /' src/views/office/console/index.vue
echo "   ✅ 已注释未使用变量"

echo ""
echo "=========================================="
echo "✅ 所有修复完成！"
echo "=========================================="
echo ""
echo "执行最终构建验证..."
npm run build
