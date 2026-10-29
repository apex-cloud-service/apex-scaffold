#!/bin/bash
# 历史一次性工具：用于旧前端 Vue 组件注释补充，执行前请核对原始路径与内容。
###############################################################################
# 前端 Vue 组件注释补充脚本
#
# 用法：将此脚本复制到 /Users/work/apex-admin-web/ 目录下运行
#       cd /Users/work/apex-admin-web
#       bash vue-components-add-comments.sh
#
# 作者：Apex Scaffold Team
# 日期：2026-09-23
###############################################################################

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "=================================================="
echo "前端 Vue 组件注释补充脚本"
echo "=================================================="

# 检查前端项目目录
if [ ! -d "$FRONTEND_DIR" ]; then
    echo "❌ 错误：前端项目目录不存在: $FRONTEND_DIR"
    exit 1
fi

cd "$FRONTEND_DIR"
echo "✅ 工作目录: $(pwd)"

# 计数器
TOTAL=0
ADDED=0

# 函数：为 Vue 文件添加文件头注释
add_vue_comment() {
    local file="$1"
    local comment="$2"
    
    # 检查文件是否存在
    if [ ! -f "$file" ]; then
        echo "  ⚠️  文件不存在，跳过"
        return
    fi
    
    # 检查文件是否已有文件头注释
    if head -n 5 "$file" | grep -q "^<!--"; then
        echo "  ⏭️  已有注释，跳过"
        return
    fi
    
    # 添加注释到文件开头
    {
        echo "$comment"
        cat "$file"
    } > "$file.tmp"
    
    mv "$file.tmp" "$file"
    echo "  ✅ 已添加注释"
    ((ADDED++))
}

echo ""
echo "📝 开始补充 Vue 组件文件头注释..."
echo ""

# ========== system/ 目录 ==========
echo "处理 src/views/system/ 目录..."

# system/user/index.vue
echo "  - system/user/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/user/index.vue" '<!--
  用户管理页面
  ------------------------------------------------------------------
  功能：用户列表展示、分页查询、CRUD 操作、密码重置、状态管理
  
  权限：
    - system:user:view - 查看用户列表
    - system:user:create - 创建用户
    - system:user:edit - 编辑用户
    - system:user:delete - 删除用户
    - system:user:reset-password - 重置密码
-->'

# system/role/index.vue
echo "  - system/role/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/role/index.vue" '<!--
  角色管理页面
  ------------------------------------------------------------------
  功能：角色列表展示、角色 CRUD、权限分配、数据权限配置
  
  权限：
    - system:role:view - 查看角色列表
    - system:role:create - 创建角色
    - system:role:edit - 编辑角色
    - system:role:delete - 删除角色
    - system:role:permission - 分配权限
-->'

# system/menu/index.vue
echo "  - system/menu/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/menu/index.vue" '<!--
  菜单管理页面
  ------------------------------------------------------------------
  功能：菜单树展示、菜单 CRUD、菜单排序、图标选择
  
  权限：
    - system:menu:view - 查看菜单列表
    - system:menu:create - 创建菜单
    - system:menu:edit - 编辑菜单
    - system:menu:delete - 删除菜单
-->'

# system/dept/index.vue
echo "  - system/dept/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/dept/index.vue" '<!--
  部门管理页面
  ------------------------------------------------------------------
  功能：部门树展示、部门 CRUD、部门排序
  
  权限：
    - system:dept:view - 查看部门列表
    - system:dept:create - 创建部门
    - system:dept:edit - 编辑部门
    - system:dept:delete - 删除部门
-->'

# system/dict/index.vue
echo "  - system/dict/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/dict/index.vue" '<!--
  字典管理页面
  ------------------------------------------------------------------
  功能：字典类型管理、字典数据管理、字典缓存刷新
  
  权限：
    - system:dict:view - 查看字典
    - system:dict:create - 创建字典
    - system:dict:edit - 编辑字典
    - system:dict:delete - 删除字典
-->'

# system/config/index.vue
echo "  - system/config/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/config/index.vue" '<!--
  参数配置页面
  ------------------------------------------------------------------
  功能：系统参数列表展示、参数 CRUD、参数类型分类
  
  权限：
    - system:config:view - 查看参数
    - system:config:create - 创建参数
    - system:config:edit - 编辑参数
    - system:config:delete - 删除参数
-->'

# system/database/index.vue
echo "  - system/database/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/database/index.vue" '<!--
  数据库配置页面
  ------------------------------------------------------------------
  功能：数据库连接测试、配置信息采集、部署配置生成导出
  
  权限：
    - system:database:view - 查看数据库配置
    - system:database:test - 测试数据库连接
    - system:database:export - 导出配置
  
  注意：密码不通过前端保存，仅用于一次性生成配置
-->'

# system/job/index.vue
echo "  - system/job/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/job/index.vue" '<!--
  定时任务管理主页
  ------------------------------------------------------------------
  功能：定时任务列表展示、任务 CRUD、任务启停控制、手动执行
  
  权限：
    - system:job:view - 查看任务列表
    - system:job:create - 创建任务
    - system:job:edit - 编辑任务
    - system:job:delete - 删除任务
    - system:job:execute - 手动执行任务
-->'

# system/job/query.vue
echo "  - system/job/query.vue"
((TOTAL++))
add_vue_comment "src/views/system/job/query.vue" '<!--
  定时任务分页查询页面
  ------------------------------------------------------------------
  功能：定时任务高级查询、多条件筛选、分页展示
  
  权限：
    - system:job:view - 查看任务列表
-->'

# system/auth/index.vue
echo "  - system/auth/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/auth/index.vue" '<!--
  认证配置页面
  ------------------------------------------------------------------
  功能：OAuth2 客户端管理、认证配置、密钥管理
  
  权限：
    - system:auth:view - 查看认证配置
    - system:auth:edit - 编辑认证配置
-->'

# system/sso/index.vue
echo "  - system/sso/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/sso/index.vue" '<!--
  SSO 配置页面
  ------------------------------------------------------------------
  功能：SSO 提供商配置、协议配置、用户映射规则
  
  权限：
    - system:sso:view - 查看 SSO 配置
    - system:sso:edit - 编辑 SSO 配置
-->'

# system/generator/index.vue
echo "  - system/generator/index.vue"
((TOTAL++))
add_vue_comment "src/views/system/generator/index.vue" '<!--
  代码生成页面
  ------------------------------------------------------------------
  功能：数据库表选择、代码生成配置、模板选择、代码预览和下载
  
  权限：
    - system:generator:view - 查看代码生成
    - system:generator:generate - 生成代码
-->'

# ========== monitor/ 目录 ==========
echo ""
echo "处理 src/views/monitor/ 目录..."

# monitor/loginlog/index.vue
echo "  - monitor/loginlog/index.vue"
((TOTAL++))
add_vue_comment "src/views/monitor/loginlog/index.vue" '<!--
  登录日志页面
  ------------------------------------------------------------------
  功能：登录日志查询、登录成功/失败统计、IP 归属地显示
  
  权限：
    - monitor:loginlog:view - 查看登录日志
-->'

# monitor/audit/index.vue
echo "  - monitor/audit/index.vue"
((TOTAL++))
add_vue_comment "src/views/monitor/audit/index.vue" '<!--
  审计日志页面
  ------------------------------------------------------------------
  功能：操作日志查询、操作类型筛选、操作人筛选、时间范围查询
  
  权限：
    - monitor:audit:view - 查看审计日志
-->'

# monitor/api-call-log/index.vue
echo "  - monitor/api-call-log/index.vue"
((TOTAL++))
add_vue_comment "src/views/monitor/api-call-log/index.vue" '<!--
  API 调用日志页面
  ------------------------------------------------------------------
  功能：API 调用记录查询、响应时间统计、错误率分析
  
  权限：
    - monitor:api-call-log:view - 查看 API 调用日志
-->'

# monitor/online/index.vue
echo "  - monitor/online/index.vue"
((TOTAL++))
add_vue_comment "src/views/monitor/online/index.vue" '<!--
  在线用户页面
  ------------------------------------------------------------------
  功能：在线用户列表、会话信息展示、强制下线
  
  权限：
    - monitor:online:view - 查看在线用户
    - monitor:online:force-logout - 强制下线
-->'

# monitor/dashboard/index.vue
echo "  - monitor/dashboard/index.vue"
((TOTAL++))
add_vue_comment "src/views/monitor/dashboard/index.vue" '<!--
  监控大盘页面
  ------------------------------------------------------------------
  功能：系统监控指标展示、JVM 监控、数据库连接池监控、缓存监控
  
  权限：
    - monitor:dashboard:view - 查看监控大盘
-->'

# monitor/runtime-log/index.vue
echo "  - monitor/runtime-log/index.vue"
((TOTAL++))
add_vue_comment "src/views/monitor/runtime-log/index.vue" '<!--
  运行时日志页面
  ------------------------------------------------------------------
  功能：后端运行时日志查询（Logback 日志文件）、日志级别筛选
  
  权限：
    - monitor:runtime-log:view - 查看运行时日志
  
  注意：无分页，返回日志数组
-->'

# monitor/business-log/index.vue
echo "  - monitor/business-log/index.vue"
((TOTAL++))
add_vue_comment "src/views/monitor/business-log/index.vue" '<!--
  业务日志页面
  ------------------------------------------------------------------
  功能：业务操作日志查询（登录/操作/API）、分页展示、时间范围查询
  
  权限：
    - monitor:business-log:view - 查看业务日志
  
  注意：有分页，返回 PageResponse
-->'

# ========== data/ 目录 ==========
echo ""
echo "处理 src/views/data/ 目录..."

# data/document/index.vue
echo "  - data/document/index.vue"
((TOTAL++))
add_vue_comment "src/views/data/document/index.vue" '<!--
  文档管理页面
  ------------------------------------------------------------------
  功能：文档列表展示、文档 CRUD、文件上传下载、状态管理
  
  权限：
    - data:document:view - 查看文档列表
    - data:document:create - 创建文档
    - data:document:edit - 编辑文档
    - data:document:delete - 删除文档
  
  支持的文档类型：
    - contract - 合同
    - report - 报告
    - proposal - 提案
    - manual - 手册
    - other - 其他
-->'

# data/chart/index.vue
echo "  - data/chart/index.vue"
((TOTAL++))
add_vue_comment "src/views/data/chart/index.vue" '<!--
  图表管理页面
  ------------------------------------------------------------------
  功能：图表列表展示、图表 CRUD、ECharts 图表渲染、数据查询
  
  权限：
    - data:chart:view - 查看图表列表
    - data:chart:create - 创建图表
    - data:chart:edit - 编辑图表
    - data:chart:delete - 删除图表
  
  支持的图表类型：
    - line - 折线图（趋势分析）
    - bar - 柱状图（数据对比）
    - pie - 饼图（占比展示）
    - scatter - 散点图（关系分析）
    - radar - 雷达图（多维度评估）
-->'

# ========== workflow/ 目录 ==========
echo ""
echo "处理 src/views/workflow/ 目录..."

# workflow/monitor/index.vue
echo "  - workflow/monitor/index.vue"
((TOTAL++))
add_vue_comment "src/views/workflow/monitor/index.vue" '<!--
  工作流监控主页
  ------------------------------------------------------------------
  功能：工作流统计看板、流程实例列表、Tab 切换展示
  
  包含组件：
    - Statistics.vue - 统计看板
    - InstanceList.vue - 流程实例列表
  
  路由：
    - /workflow/monitor - 监控主页（静态路由）
    - /workflow/instances/:id - 流程详情页
  
  注意：使用工作流专用分页口径 {pageNum, pageSize} + {records, total}
-->'

# workflow/monitor/Statistics.vue
echo "  - workflow/monitor/Statistics.vue"
((TOTAL++))
add_vue_comment "src/views/workflow/monitor/Statistics.vue" '<!--
  工作流统计看板组件
  ------------------------------------------------------------------
  功能：展示工作流统计数据（流程实例数、待办任务数、完成任务数、超时任务数）
  
  数据来源：
    - /admin-api/v1/workflows/statistics - 统计概览
    - /admin-api/v1/workflows/statistics/trend - 趋势数据
-->'

# workflow/monitor/InstanceList.vue
echo "  - workflow/monitor/InstanceList.vue"
((TOTAL++))
add_vue_comment "src/views/workflow/monitor/InstanceList.vue" '<!--
  流程实例列表组件
  ------------------------------------------------------------------
  功能：流程实例分页列表、状态筛选、查看详情
  
  数据来源：
    - /admin-api/v1/workflows/instances - 流程实例列表
  
  注意：使用工作流专用分页口径
-->'

# workflow/monitor/InstanceDetail.vue
echo "  - workflow/monitor/InstanceDetail.vue"
((TOTAL++))
add_vue_comment "src/views/workflow/monitor/InstanceDetail.vue" '<!--
  流程实例详情页
  ------------------------------------------------------------------
  功能：流程详细信息展示、任务操作（审批/回退/转办/委派/挂起/恢复）
  
  包含组件：
    - TaskActions.vue - 任务操作按钮
  
  数据来源：
    - /admin-api/v1/workflows/instances/{id} - 流程详情
-->'

# workflow/components/TaskActions.vue
echo "  - workflow/components/TaskActions.vue"
((TOTAL++))
add_vue_comment "src/views/workflow/components/TaskActions.vue" '<!--
  任务操作按钮组件
  ------------------------------------------------------------------
  功能：提供工作流任务的各种操作按钮（审批/回退/转办/委派/挂起/恢复）
  
  Props：
    - taskId - 任务 ID
    - instanceId - 流程实例 ID
    - status - 任务状态
  
  Emits：
    - success - 操作成功后触发
-->'

# ========== office/ 目录 ==========
echo ""
echo "处理 src/views/office/ 目录..."

# office/config/index.vue
echo "  - office/config/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/config/index.vue" '<!--
  办公平台配置页面
  ------------------------------------------------------------------
  功能：飞书、钉钉、企业微信平台配置管理（AppId、AppSecret、回调 URL）
  
  权限：
    - office:config:view - 查看办公平台配置
    - office:config:edit - 编辑办公平台配置
-->'

# office/bindings/index.vue
echo "  - office/bindings/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/bindings/index.vue" '<!--
  办公平台绑定页面
  ------------------------------------------------------------------
  功能：用户与办公平台账号绑定、解绑操作
  
  权限：
    - office:binding:view - 查看绑定状态
    - office:binding:bind - 绑定账号
    - office:binding:unbind - 解绑账号
-->'

# office/console/index.vue
echo "  - office/console/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/console/index.vue" '<!--
  办公平台控制台页面
  ------------------------------------------------------------------
  功能：办公平台数据统计、消息推送统计、审批流程统计
  
  权限：
    - office:console:view - 查看办公平台控制台
-->'

# office/message/index.vue
echo "  - office/message/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/message/index.vue" '<!--
  办公平台消息管理页面
  ------------------------------------------------------------------
  功能：办公平台消息推送、消息模板管理、消息发送记录
  
  权限：
    - office:message:view - 查看消息列表
    - office:message:send - 发送消息
-->'

# office/approval/index.vue
echo "  - office/approval/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/approval/index.vue" '<!--
  办公平台审批管理页面
  ------------------------------------------------------------------
  功能：审批流程列表、发起审批、审批操作、撤销审批
  
  权限：
    - office:approval:view - 查看审批列表
    - office:approval:create - 发起审批
    - office:approval:approve - 审批操作
    - office:approval:cancel - 撤销审批
-->'

# office/organization/index.vue
echo "  - office/organization/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/organization/index.vue" '<!--
  办公平台组织架构管理页面
  ------------------------------------------------------------------
  功能：同步办公平台组织架构、部门管理、员工管理
  
  权限：
    - office:organization:view - 查看组织架构
    - office:organization:sync - 同步组织架构
-->'

# office/calendar/index.vue
echo "  - office/calendar/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/calendar/index.vue" '<!--
  办公平台日历管理页面
  ------------------------------------------------------------------
  功能：日历事件管理、会议预约、会议室预订
  
  权限：
    - office:calendar:view - 查看日历事件
    - office:calendar:create - 创建日历事件
    - office:calendar:edit - 编辑日历事件
    - office:calendar:delete - 删除日历事件
-->'

# office/document/index.vue
echo "  - office/document/index.vue"
((TOTAL++))
add_vue_comment "src/views/office/document/index.vue" '<!--
  办公平台文档管理页面
  ------------------------------------------------------------------
  功能：办公平台在线文档管理、文档协作、文档权限控制
  
  权限：
    - office:document:view - 查看文档列表
    - office:document:create - 创建文档
    - office:document:edit - 编辑文档
    - office:document:delete - 删除文档
-->'

# ========== 其他页面 ==========
echo ""
echo "处理其他页面..."

# dashboard/index.vue
echo "  - dashboard/index.vue"
((TOTAL++))
add_vue_comment "src/views/dashboard/index.vue" '<!--
  工作台页面
  ------------------------------------------------------------------
  功能：指标卡片展示、趋势图表、快捷入口
  
  路由：静态路由 /dashboard
-->'

# profile/index.vue
echo "  - profile/index.vue"
((TOTAL++))
add_vue_comment "src/views/profile/index.vue" '<!--
  个人中心页面
  ------------------------------------------------------------------
  功能：当前用户信息展示、个人资料编辑、密码修改、头像上传
  
  路由：动态路由 /profile
-->'

# ========== 组件 ==========
echo ""
echo "处理通用组件..."

# components/Pagination.vue
echo "  - components/Pagination.vue"
((TOTAL++))
add_vue_comment "src/components/Pagination.vue" '<!--
  通用分页组件
  ------------------------------------------------------------------
  基于 el-pagination 封装，内置分页参数与变更回调，
  供各列表页面统一复用（当前页/每页条数/总数）。
  
  Props：
    - total - 总条数
  
  Emits：
    - change(page, size) - 页码或每页条数变化时触发
-->'

# components/EmptyState.vue
echo "  - components/EmptyState.vue"
((TOTAL++))
add_vue_comment "src/components/EmptyState.vue" '<!--
  空状态展示组件
  ------------------------------------------------------------------
  用于列表为空时的占位展示
  
  Props：
    - description - 空状态描述文本
-->'

# components/FileUpload.vue
echo "  - components/FileUpload.vue"
((TOTAL++))
add_vue_comment "src/components/FileUpload.vue" '<!--
  基础文件上传组件
  ------------------------------------------------------------------
  封装 Element Plus el-upload，支持图片上传、文件大小校验、自动携带认证 Token
  
  Props：
    - sex - 性别路由参数（0=男/AM，1=女/AF，必填）
    - limit - 最大上传数量
    - accept - 文件类型
    - maxSize - 最大文件大小（MB）
    - listType - 列表类型（text/picture/picture-card）
    - disabled - 是否禁用
    - tip - 提示文本
  
  Emits：
    - success(result: FileUploadResult) - 上传成功
    - error(error) - 上传失败
    - remove(file) - 文件移除
-->'

# components/FileUploadEnhanced.vue
echo "  - components/FileUploadEnhanced.vue"
((TOTAL++))
add_vue_comment "src/components/FileUploadEnhanced.vue" '<!--
  增强版文件上传组件
  ------------------------------------------------------------------
  继承基础组件所有功能，额外支持：
    - 拖拽上传（:drag="true"）
    - 粘贴上传（Ctrl+V，:enable-paste="true"）
    - 图片裁剪（:enable-cropper="true"，基于 cropperjs）
    - 图片压缩（:enable-compress="true"，可配置质量和尺寸）
    - 上传进度展示
  
  Props：继承 FileUpload 的所有 Props，额外：
    - drag - 是否启用拖拽上传
    - enablePaste - 是否启用粘贴上传
    - enableCropper - 是否启用图片裁剪
    - enableCompress - 是否启用图片压缩
  
  Emits：继承 FileUpload 的所有 Emits
  
  适用场景：图片批量上传、富文本编辑器、图片管理等
-->'

# components/GlobalSearch/index.vue
echo "  - components/GlobalSearch/index.vue"
((TOTAL++))
add_vue_comment "src/components/GlobalSearch/index.vue" '<!--
  全局搜索组件
  ------------------------------------------------------------------
  集成在 AdminLayout 顶栏，支持快捷键唤起、全文搜索（Meilisearch）
  
  功能：
    - 搜索资源类型：user/role/menu/dept/dict/config
    - 搜索结果跳转到对应管理页并带 ?id= 参数
    - 索引重建功能
-->'

echo ""
echo "=================================================="
echo "✅ 所有 Vue 组件注释补充完成！"
echo "=================================================="
echo ""
echo "统计信息："
echo "  - 总共处理文件：$TOTAL 个"
echo "  - 新增注释文件：$ADDED 个"
echo "  - 已有注释（跳过）：$((TOTAL - ADDED)) 个"
echo ""
echo "已补充注释的目录："
echo "  - src/views/system/ - 系统管理（12 个）"
echo "  - src/views/monitor/ - 监控管理（7 个）"
echo "  - src/views/data/ - 数据管理（2 个）"
echo "  - src/views/workflow/ - 工作流（4 个）"
echo "  - src/views/office/ - 办公平台（8 个）"
echo "  - src/views/dashboard/ - 工作台（1 个）"
echo "  - src/views/profile/ - 个人中心（1 个）"
echo "  - src/components/ - 通用组件（5 个）"
echo ""
echo "建议执行以下命令验证："
echo "  cd /Users/work/apex-admin-web"
echo "  npm run build"
echo ""
