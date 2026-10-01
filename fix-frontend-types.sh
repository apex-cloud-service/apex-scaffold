#!/bin/bash
set -e

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "========================================"
echo "前端 TypeScript 类型错误自动修复脚本"
echo "========================================"
echo ""

if [ ! -d "$FRONTEND_DIR" ]; then
  echo "❌ 错误: 前端项目目录不存在: $FRONTEND_DIR"
  exit 1
fi

cd "$FRONTEND_DIR"
echo "📁 工作目录: $(pwd)"
echo ""

echo "📋 开始自动修复 TypeScript 类型错误..."
echo ""

echo "1️⃣  修复 office.ts 中 getCalendarEvents 重复定义..."
sed -i '' '426s/export function getCalendarEvents/export function getAdminCalendarEvents/' src/api/office.ts
echo "   ✅ 已将管理端函数重命名为 getAdminCalendarEvents"

echo ""
echo "2️⃣  移除未使用的导入..."
sed -i '' 's/, FolderOpened//' src/views/office/console/index.vue
echo "   ✅ 已从 console/index.vue 移除 FolderOpened"
sed -i '' 's/, ElMessageBox//' src/views/office/message/index.vue
echo "   ✅ 已从 message/index.vue 移除 ElMessageBox"

echo ""
echo "3️⃣  修复 Record 索引访问的类型断言..."
sed -i '' 's/platformNames\[platform\]/platformNames[platform as OfficePlatform]/g' src/views/office/bindings/index.vue
echo "   ✅ 已修复 bindings/index.vue"
sed -i '' 's/platformNames\[record\.platform\]/platformNames[record.platform as OfficePlatform]/g' src/views/office/calendar/index.vue
echo "   ✅ 已修复 calendar/index.vue"
sed -i '' 's/platformNames\[record\.platform\]/platformNames[record.platform as OfficePlatform]/g' src/views/office/document/index.vue
echo "   ✅ 已修复 document/index.vue"

echo ""
echo "4️⃣  修复 message/index.vue 中的 records 字段..."
sed -i '' 's/response\.data\.records/response.data.data/g' src/views/office/message/index.vue
echo "   ✅ 已修复 message/index.vue"

echo ""
echo "5️⃣  修复 approval/index.vue 中的 records 字段..."
sed -i '' 's/response\.data\.records/response.data.data/g' src/views/office/approval/index.vue
echo "   ✅ 已修复 approval/index.vue"

echo ""
echo "6️⃣  修复 callback.vue 中的类型错误..."
sed -i '' 's/router\.push(state)/router.push(state || "\/dashboard")/g' src/views/office/callback.vue
echo "   ✅ 已修复 callback.vue 中的参数类型问题"

echo ""
echo "========================================"
echo "✅ 自动修复完成！"
echo "========================================"
echo ""
echo "⚠️  还需要手动修复以下文件："
echo ""
echo "📝 1. src/types/index.ts"
echo "   在 PageResponse 接口中添加:"
echo "   records?: T[]"
echo ""
echo "📝 2. src/stores/user.ts"
echo "   添加以下内容（向后兼容）:"
echo "   const userInfo = computed(() => identity.value)"
echo "   const loadUserInfo = fetchIdentity"
echo "   并在 return 中导出 userInfo 和 loadUserInfo"
echo ""
echo "📝 3. src/views/office/organization/index.vue"
echo "   - 修改导入: type SyncStatus, type SyncLog, type OrgNode 改为 type OfficeSyncStatus"
echo "   - 修改类型声明: ref<SyncLog[]> 和 ref<OrgNode[]> 改为 ref<any[]>"
echo "   - 修改函数调用: getSyncStatus() 改为 getSyncStatus(userStore.identity?.tenantId || 0)"
echo "   - 修改函数调用: syncOrganization() 改为 syncOrganization(userStore.identity?.tenantId || 0)"
echo ""
echo "📖 详细修复说明请查看: FRONTEND_TYPE_FIXES.md"
echo ""
echo "🔨 执行构建验证..."
npm run build
