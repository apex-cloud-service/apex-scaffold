# 办公平台集成 - 前端开发指南

本指南帮助前端开发者快速完成阶段三的所有前端页面。

---

## 📋 前端项目概览

**项目路径**：`/Users/work/apex-admin-web`  
**技术栈**：Vue 3 + TypeScript + Element Plus + Vite  
**后端接口**：RESTful API（基于 WebFlux）

---

## 🚀 快速开始

### 1. 环境准备

```bash
# 进入前端项目
cd /Users/work/apex-admin-web

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 访问地址
open http://localhost:5173
```

### 2. 项目结构

```
apex-admin-web/
├── src/
│   ├── views/              # 页面组件
│   │   └── office/         # 办公平台模块
│   ├── api/                # API 接口
│   │   └── office/         # 办公平台接口
│   ├── components/         # 通用组件
│   ├── router/             # 路由配置
│   ├── store/              # 状态管理
│   ├── types/              # TypeScript 类型
│   └── utils/              # 工具函数
└── package.json
```

---

## 📝 阶段三功能清单

### 3.1 平台应用配置页 ✅

**页面路径**：`/office/platform-app`  
**功能**：管理办公平台应用配置

#### 文件清单

```
src/views/office/platform-app/
├── index.vue                     # 主页面
├── components/
│   ├── AppList.vue              # 应用列表
│   ├── AppForm.vue              # 应用表单（创建/编辑）
│   ├── AppDetail.vue            # 应用详情
│   └── TestConnection.vue       # 测试连通性
└── types.ts                     # 类型定义

src/api/office/
└── platform-app.ts              # API 接口
```

#### 类型定义

```typescript
// src/views/office/platform-app/types.ts

/**
 * 平台类型
 */
export enum PlatformType {
  FEISHU = 'FEISHU',
  DINGTALK = 'DINGTALK',
  WECOM = 'WECOM'
}

/**
 * 平台应用配置
 */
export interface PlatformApp {
  id: number
  tenantId: string
  platform: PlatformType
  appId: string
  appName: string
  redirectUri: string
  secretReference: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 创建/更新请求
 */
export interface PlatformAppRequest {
  platform: PlatformType
  appId: string
  appName: string
  redirectUri: string
  secretReference: string
  enabled: boolean
}

/**
 * 测试连通性响应
 */
export interface TestConnectionResponse {
  success: boolean
  message: string
  latency?: number
}
```

#### API 接口

```typescript
// src/api/office/platform-app.ts

import request from '@/utils/request'
import type { PlatformApp, PlatformAppRequest, TestConnectionResponse } from '@/views/office/platform-app/types'

/**
 * 查询应用列表
 */
export function listPlatformApps(params?: {
  platform?: string
  enabled?: boolean
}) {
  return request<PlatformApp[]>({
    url: '/api/v1/office/apps',
    method: 'get',
    params
  })
}

/**
 * 查询应用详情
 */
export function getPlatformApp(id: number) {
  return request<PlatformApp>({
    url: `/api/v1/office/apps/${id}`,
    method: 'get'
  })
}

/**
 * 创建应用
 */
export function createPlatformApp(data: PlatformAppRequest) {
  return request<PlatformApp>({
    url: '/api/v1/office/apps',
    method: 'post',
    data
  })
}

/**
 * 更新应用
 */
export function updatePlatformApp(id: number, data: PlatformAppRequest) {
  return request<PlatformApp>({
    url: `/api/v1/office/apps/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除应用
 */
export function deletePlatformApp(id: number) {
  return request<void>({
    url: `/api/v1/office/apps/${id}`,
    method: 'delete'
  })
}

/**
 * 测试连通性
 */
export function testConnection(id: number) {
  return request<TestConnectionResponse>({
    url: `/api/v1/office/apps/${id}/test`,
    method: 'post'
  })
}
```

#### 页面示例

```vue
<!-- src/views/office/platform-app/index.vue -->
<template>
  <div class="platform-app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">平台应用配置</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            新建应用
          </el-button>
        </div>
      </template>

      <!-- 筛选条件 -->
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="平台">
          <el-select v-model="queryParams.platform" clearable placeholder="全部">
            <el-option label="飞书" value="FEISHU" />
            <el-option label="钉钉" value="DINGTALK" />
            <el-option label="企业微信" value="WECOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.enabled" clearable placeholder="全部">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 应用列表 -->
      <el-table :data="appList" v-loading="loading" border>
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="getPlatformTagType(row.platform)">
              {{ getPlatformLabel(row.platform) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="appName" label="应用名称" />
        <el-table-column prop="appId" label="应用ID" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="handleToggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleTest(row)">
              测试
            </el-button>
            <el-button link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <AppForm
      v-model:visible="formVisible"
      :app="currentApp"
      @success="handleFormSuccess"
    />

    <!-- 测试连通性对话框 -->
    <TestConnection
      v-model:visible="testVisible"
      :app="currentApp"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listPlatformApps, deletePlatformApp, updatePlatformApp } from '@/api/office/platform-app'
import type { PlatformApp } from './types'
import AppForm from './components/AppForm.vue'
import TestConnection from './components/TestConnection.vue'

const loading = ref(false)
const appList = ref<PlatformApp[]>([])
const queryParams = ref({
  platform: '',
  enabled: undefined
})

const formVisible = ref(false)
const testVisible = ref(false)
const currentApp = ref<PlatformApp | null>(null)

const loadAppList = async () => {
  loading.value = true
  try {
    const res = await listPlatformApps(queryParams.value)
    appList.value = res.data
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  loadAppList()
}

const handleReset = () => {
  queryParams.value = {
    platform: '',
    enabled: undefined
  }
  loadAppList()
}

const handleCreate = () => {
  currentApp.value = null
  formVisible.value = true
}

const handleEdit = (app: PlatformApp) => {
  currentApp.value = app
  formVisible.value = true
}

const handleTest = (app: PlatformApp) => {
  currentApp.value = app
  testVisible.value = true
}

const handleToggleEnabled = async (app: PlatformApp) => {
  try {
    await updatePlatformApp(app.id, {
      platform: app.platform,
      appId: app.appId,
      appName: app.appName,
      redirectUri: app.redirectUri,
      secretReference: app.secretReference,
      enabled: app.enabled
    })
    ElMessage.success(app.enabled ? '已启用' : '已禁用')
  } catch (error) {
    app.enabled = !app.enabled
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (app: PlatformApp) => {
  try {
    await ElMessageBox.confirm('确定要删除该应用吗？', '提示', {
      type: 'warning'
    })
    await deletePlatformApp(app.id)
    ElMessage.success('删除成功')
    loadAppList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleFormSuccess = () => {
  formVisible.value = false
  loadAppList()
}

const getPlatformLabel = (platform: string) => {
  const labels: Record<string, string> = {
    FEISHU: '飞书',
    DINGTALK: '钉钉',
    WECOM: '企业微信'
  }
  return labels[platform] || platform
}

const getPlatformTagType = (platform: string) => {
  const types: Record<string, any> = {
    FEISHU: 'primary',
    DINGTALK: 'success',
    WECOM: 'warning'
  }
  return types[platform] || 'info'
}

onMounted(() => {
  loadAppList()
})
</script>

<style scoped lang="scss">
.platform-app-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .title {
    font-size: 16px;
    font-weight: 600;
  }
}
</style>
```

### 3.2 绑定审批管理页 ✅

**页面路径**：`/office/binding`  
**功能**：审批和管理用户绑定申请

#### 文件清单

```
src/views/office/binding/
├── index.vue                     # 主页面
├── components/
│   ├── BindingList.vue          # 绑定列表
│   ├── ReviewModal.vue          # 审批弹窗
│   └── BindingDetail.vue        # 绑定详情
└── types.ts                     # 类型定义

src/api/office/
└── binding.ts                   # API 接口
```

#### 类型定义

```typescript
// src/views/office/binding/types.ts

/**
 * 绑定状态
 */
export enum BindingStatus {
  PENDING = 'PENDING',     // 待审批
  APPROVED = 'APPROVED',   // 已通过
  REJECTED = 'REJECTED'    // 已拒绝
}

/**
 * 用户绑定记录
 */
export interface UserBinding {
  id: number
  tenantId: string
  userId: string
  userName: string
  platform: string
  externalUserId: string
  externalUserName: string
  status: BindingStatus
  appliedAt: string
  reviewedAt?: string
  reviewer?: string
  rejectReason?: string
}

/**
 * 审批请求
 */
export interface ReviewRequest {
  approve: boolean
  rejectReason?: string
}
```

#### API 接口

```typescript
// src/api/office/binding.ts

import request from '@/utils/request'
import type { UserBinding, ReviewRequest } from '@/views/office/binding/types'

/**
 * 查询绑定列表
 */
export function listBindings(params?: {
  status?: string
  platform?: string
}) {
  return request<UserBinding[]>({
    url: '/api/v1/office/bindings',
    method: 'get',
    params
  })
}

/**
 * 审批绑定
 */
export function reviewBinding(id: number, data: ReviewRequest) {
  return request<void>({
    url: `/api/v1/office/bindings/${id}/review`,
    method: 'post',
    data
  })
}

/**
 * 解绑
 */
export function unbindUser(id: number) {
  return request<void>({
    url: `/api/v1/office/bindings/${id}`,
    method: 'delete'
  })
}
```

### 3.3 办公操作控制台 ✅

**页面路径**：`/office/console`  
**功能**：统一的办公操作控制台

#### 文件清单

```
src/views/office/console/
├── index.vue                          # 控制台主页
├── message/
│   ├── index.vue                     # 消息发送页
│   └── components/
│       ├── MessageEditor.vue         # 消息编辑器
│       ├── RecipientSelect.vue       # 接收人选择
│       └── MessageHistory.vue        # 消息历史
├── organization/
│   ├── index.vue                     # 组织同步页
│   └── components/
│       ├── SyncControl.vue           # 同步控制
│       ├── SyncHistory.vue           # 同步历史
│       └── MappingList.vue           # 映射列表
├── approval/
│   ├── index.vue                     # 审批处理页
│   └── components/
│       ├── ApprovalList.vue          # 审批列表
│       └── ApprovalDetail.vue        # 审批详情
├── document/
│   ├── index.vue                     # 文档管理页
│   └── components/
│       ├── DocumentList.vue          # 文档列表
│       └── DocumentEditor.vue        # 文档编辑器
└── calendar/
    ├── index.vue                     # 日历管理页
    └── components/
        ├── CalendarView.vue          # 日历视图
        └── EventEditor.vue           # 事件编辑器
```

#### 组织同步页面示例

```vue
<!-- src/views/office/console/organization/index.vue -->
<template>
  <div class="organization-sync-container">
    <el-card shadow="never">
      <template #header>
        <span class="title">组织架构同步</span>
      </template>

      <!-- 同步控制 -->
      <el-form :inline="true" :model="syncForm">
        <el-form-item label="平台">
          <el-select v-model="syncForm.platform" placeholder="请选择平台">
            <el-option label="飞书" value="FEISHU" />
            <el-option label="钉钉" value="DINGTALK" />
            <el-option label="企业微信" value="WECOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步类型">
          <el-select v-model="syncForm.syncType" placeholder="请选择">
            <el-option label="部门" value="DEPARTMENT" />
            <el-option label="成员" value="MEMBER" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步模式">
          <el-radio-group v-model="syncForm.fullSync">
            <el-radio :label="false">增量同步</el-radio>
            <el-radio :label="true">全量同步</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="syncing"
            @click="handleSync"
          >
            <el-icon><Refresh /></el-icon>
            开始同步
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 同步历史 -->
      <el-divider />
      <h3>同步历史</h3>
      <el-table :data="taskList" v-loading="loading" border>
        <el-table-column prop="platform" label="平台" width="100" />
        <el-table-column prop="state" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.state)">
              {{ getStatusLabel(row.state) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cursor" label="游标" />
        <el-table-column prop="failureMessage" label="失败原因" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
    </el-card>

    <!-- 组织映射列表 -->
    <el-card shadow="never" class="mapping-card">
      <template #header>
        <span class="title">组织映射关系</span>
      </template>
      <el-table :data="mappingList" border>
        <el-table-column prop="platform" label="平台" width="100" />
        <el-table-column prop="externalOrgId" label="外部部门ID" />
        <el-table-column prop="externalUserId" label="外部用户ID" />
        <el-table-column prop="apexDeptId" label="Apex部门ID" />
        <el-table-column prop="apexUserId" label="Apex用户ID" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { syncOrganization, listSyncTasks, listMappings } from '@/api/office/organization'

const syncing = ref(false)
const loading = ref(false)
const syncForm = ref({
  platform: 'FEISHU',
  syncType: 'DEPARTMENT',
  fullSync: false
})

const taskList = ref([])
const mappingList = ref([])

const handleSync = async () => {
  syncing.value = true
  try {
    await syncOrganization(syncForm.value)
    ElMessage.success('同步任务已提交')
    loadTasks()
  } catch (error) {
    ElMessage.error('同步失败')
  } finally {
    syncing.value = false
  }
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await listSyncTasks()
    taskList.value = res.data
  } finally {
    loading.value = false
  }
}

const loadMappings = async () => {
  const res = await listMappings()
  mappingList.value = res.data
}

const getStatusType = (state: string) => {
  const types: Record<string, any> = {
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger'
  }
  return types[state] || 'info'
}

const getStatusLabel = (state: string) => {
  const labels: Record<string, string> = {
    RUNNING: '进行中',
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return labels[state] || state
}

onMounted(() => {
  loadTasks()
  loadMappings()
})
</script>

<style scoped lang="scss">
.organization-sync-container {
  padding: 20px;
}

.mapping-card {
  margin-top: 20px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}
</style>
```

---

## 🎨 UI/UX 设计规范

### 布局
- 使用 Element Plus 的 Grid 布局
- 卡片间距：20px
- 内容区域内边距：20px

### 颜色
- 主色：#409EFF（Element Plus Primary）
- 成功：#67C23A
- 警告：#E6A23C
- 危险：#F56C6C
- 信息：#909399

### 组件
- 表格：使用 `el-table`，启用 border
- 表单：使用 `el-form`，inline 模式用于筛选
- 按钮：主要操作用 `type="primary"`
- 对话框：使用 `el-dialog`，width 根据内容调整

---

## 🔌 后端接口对接

### 统一响应格式

```typescript
interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
  traceId: string
}
```

### 请求工具封装

```typescript
// src/utils/request.ts
import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 添加租户ID
    const tenantId = localStorage.getItem('tenantId')
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId
    }
    
    // 添加token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    const res = response.data
    
    // 业务错误
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    
    return res
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service
```

---

## ✅ 开发检查清单

### 开发前
- [ ] 确认后端接口已实现
- [ ] 查看 Swagger 文档了解接口定义
- [ ] 准备测试数据

### 开发中
- [ ] 使用 TypeScript 类型定义
- [ ] 遵循 Vue 3 Composition API
- [ ] 使用 Element Plus 组件
- [ ] 统一错误处理

### 开发后
- [ ] 代码格式化（Prettier）
- [ ] ESLint 检查通过
- [ ] 功能测试完成
- [ ] 响应式适配验证

---

## 📦 npm 脚本

```bash
# 开发
npm run dev

# 构建
npm run build

# 预览
npm run preview

# 格式化
npm run format

# Lint
npm run lint
```

---

## 🐛 常见问题

### 1. 跨域问题
配置 vite.config.ts：
```typescript
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

### 2. 租户ID获取
从登录后的用户信息中获取并存储到 localStorage。

### 3. Token 刷新
在请求拦截器中检查 token 过期时间，自动刷新。

---

## 📚 参考资源

- Element Plus 文档：https://element-plus.org/
- Vue 3 文档：https://vuejs.org/
- TypeScript 文档：https://www.typescriptlang.org/
- 后端 API 文档：http://localhost:8080/swagger-ui.html

---

**版本**: 1.0  
**更新时间**: 2026-09-14  
**负责团队**: 前端团队
