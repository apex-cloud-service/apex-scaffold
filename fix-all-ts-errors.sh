#!/bin/bash
set -e

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "=========================================="
echo "彻底修复所有 TypeScript 类型错误"
echo "=========================================="
echo ""

cd "$FRONTEND_DIR"

echo "1️⃣  彻底修复 organization/index.vue..."
echo "   读取原始导入行..."
original_import=$(grep -n "import.*getSyncStatus.*from '@/api/office'" src/views/office/organization/index.vue | head -1)
echo "   原始导入: $original_import"

sed -i '' 's/import {.*getSyncStatus.*} from .@\/api\/office./import { getSyncStatus, triggerSync } from '\''@\/api\/office'\''/' src/views/office/organization/index.vue

sed -i '' '/type { SyncStatus, SyncLog, OrgNode }/d' src/views/office/organization/index.vue

grep -n "getSyncStatus" src/views/office/organization/index.vue | head -5
echo "   ✅ 已修复导入"

echo ""
echo "2️⃣  修复 approval/index.vue 的数据访问..."
perl -i -pe 's/response\.data\.records/response.data.data/g' src/views/office/approval/index.vue
perl -i -pe 's/\.records/\.data/g' src/views/office/approval/index.vue
echo "   ✅ 已修复 approval/index.vue"

echo ""
echo "3️⃣  修复 message/index.vue 的数据访问..."
perl -i -pe 's/response\.data\.records/response.data.data/g' src/views/office/message/index.vue
perl -i -pe 's/\.records/\.data/g' src/views/office/message/index.vue
echo "   ✅ 已修复 message/index.vue"

echo ""
echo "4️⃣  修复类型断言 - bindings..."
perl -i -pe 's/PLATFORM_NAMES\[row\.platform\]/PLATFORM_NAMES[row.platform as OfficePlatform]/g' src/views/office/bindings/index.vue
perl -i -pe 's/\[platform\]/[platform as OfficePlatform]/g' src/views/office/bindings/index.vue
echo "   ✅ 已修复 bindings/index.vue"

echo ""
echo "5️⃣  修复类型断言 - calendar..."
perl -i -pe 's/PLATFORM_NAMES\[record\.platform\]/PLATFORM_NAMES[record.platform as OfficePlatform]/g' src/views/office/calendar/index.vue
perl -i -pe 's/platformNames\[record\.platform\]/platformNames[record.platform as OfficePlatform]/g' src/views/office/calendar/index.vue
echo "   ✅ 已修复 calendar/index.vue"

echo ""
echo "6️⃣  修复类型断言 - document..."
perl -i -pe 's/PLATFORM_NAMES\[record\.platform\]/PLATFORM_NAMES[record.platform as OfficePlatform]/g' src/views/office/document/index.vue
perl -i -pe 's/platformNames\[record\.platform\]/platformNames[record.platform as OfficePlatform]/g' src/views/office/document/index.vue
echo "   ✅ 已修复 document/index.vue"

echo ""
echo "7️⃣  修复 callback.vue 参数类型..."
perl -i -pe 's/router\.push\(state\)/router.push(state ?? "\/dashboard")/g' src/views/office/callback.vue
perl -i -pe 's/router\.push\(state \|\| /router.push(state ?? /' src/views/office/callback.vue
echo "   ✅ 已修复 callback.vue"

echo ""
echo "8️⃣  注释 console.vue 未使用变量..."
perl -i -pe 's/^(\s*)const platform = /\1\/\/ const platform = /' src/views/office/console/index.vue
echo "   ✅ 已修复 console.vue"

echo ""
echo "=========================================="
echo "✅ 所有修复完成！执行构建验证..."
echo "=========================================="
echo ""

npm run build

if [ $? -eq 0 ]; then
  echo ""
  echo "🎉🎉🎉 构建成功！所有 TypeScript 类型错误已修复！"
else
  echo ""
  echo "❌ 构建仍有错误，请检查输出信息"
  exit 1
fi
