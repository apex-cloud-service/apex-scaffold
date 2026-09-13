# Apex Admin Web 前端技术栈详细文档

> 文档版本: v1.0
> 创建时间: 2026-09-04
> 适用版本: apex-admin-web v1.0+
> 前端工程路径: `/Users/work/apex-admin-web`

> **配套架构图**:
> - [前端技术栈架构图](../diagrams/29_technology_stack_overview.svg) ⭐
> - [前后端交互架构图](../diagrams/58_api_error_response_flow.svg) ⭐
> - [全栈技术选型全景图](../diagrams/03_technical_architecture.svg)

---

## 目录

1. [技术栈概览](#1-技术栈概览)
2. [构建工具与开发环境](#2-构建工具与开发环境)
3. [核心框架层](#3-核心框架层)
4. [UI组件库与样式](#4-ui组件库与样式)
5. [数据请求与工具](#5-数据请求与工具)
6. [功能模块](#6-功能模块)
7. [技术选型理由](#7-技术选型理由)
8. [性能优化策略](#8-性能优化策略)
9. [开发规范](#9-开发规范)
10. [常见问题](#10-常见问题)

---

## 1. 技术栈概览

Apex Admin Web 是基于 **Vue 3.5** + **Vite 6** + **TypeScript 5** 构建的现代化企业级管理后台前端工程。

### 1.1 核心技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **框架** | Vue 3 | 3.5+ | 渐进式JavaScript框架，Composition API |
| **构建工具** | Vite | 6.x | 下一代前端构建工具，极速HMR |
| **语言** | TypeScript | 5.x | 类型安全的JavaScript超集 |
| **状态管理** | Pinia | 2.x | 新一代Vue状态管理库 |
| **路由** | Vue Router | 4.x | Vue官方路由管理器 |
| **UI组件** | Element Plus | 2.x | 企业级Vue 3组件库 |
| **HTTP客户端** | Axios | 1.x | 基于Promise的HTTP库 |
| **图表库** | ECharts | 5.x | 强大的数据可视化库 |
| **包管理器** | pnpm | 8.x | 快速、节省磁盘空间的包管理器 |

### 1.2 开发环境要求

```bash
Node.js >= 18.0.0
pnpm >= 8.0.0
浏览器: Chrome 90+, Edge 90+, Firefox 88+, Safari 14+
```

### 1.3 项目启动

```bash
# 进入前端工程目录
cd /Users/work/apex-admin-web

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
# 访问: http://localhost:5173

# 构建生产版本
pnpm build

# 预览生产构建
pnpm preview
```

---

## 2. 构建工具与开发环境

### 2.1 Vite 6 - 极速构建工具

#### 核心特性
- **⚡ 极速冷启动**: 使用原生ESM，无需打包即可启动
- **🔥 即时热更新(HMR)**: 模块更新 < 50ms
- **📦 优化构建**: 基于Rollup的生产构建
- **🔌 丰富插件**: Vue、TypeScript、自动导入等

#### Vite配置要点

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // 自动导入Vue API
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
    }),
    // 自动导入组件
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'es2015',
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'terser',
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'echarts': ['echarts'],
        },
      },
    },
  },
})
```

### 2.2 TypeScript 5 - 类型安全

#### TSConfig配置

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,

    /* 模块解析 */
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",

    /* 类型检查 */
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,

    /* 路径别名 */
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"],
  "exclude": ["node_modules", "dist"]
}
```

#### 类型定义规范

```typescript
// src/types/api.ts
// 统一定义API请求和响应类型

/** 通用响应包装 */
export interface Result<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页请求参数 */
export interface PageQuery {
  pageNum: number
  pageSize: number
  orderBy?: string
  orderDirection?: 'ASC' | 'DESC'
}

/** 分页响应数据 */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

/** 用户信息 */
export interface UserInfo {
  userId: number
  username: string
  realName: string
  avatar?: string
  roles: string[]
  permissions: string[]
}
```

### 2.3 ESLint + Prettier - 代码规范

#### ESLint配置

```javascript
// .eslintrc.cjs
module.exports = {
  root: true,
  env: {
    browser: true,
    es2021: true,
    node: true,
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    'plugin:@typescript-eslint/recommended',
    'prettier',
  ],
  parser: 'vue-eslint-parser',
  parserOptions: {
    ecmaVersion: 'latest',
    parser: '@typescript-eslint/parser',
    sourceType: 'module',
  },
  plugins: ['vue', '@typescript-eslint'],
  rules: {
    'vue/multi-word-component-names': 'off',
    '@typescript-eslint/no-explicit-any': 'warn',
    '@typescript-eslint/no-unused-vars': 'warn',
  },
}
```

#### Prettier配置

```json
{
  "semi": false,
  "singleQuote": true,
  "printWidth": 100,
  "trailingComma": "es5",
  "arrowParens": "avoid",
  "endOfLine": "lf"
}
```

### 2.4 Husky + lint-staged - Git Hooks

```json
{
  "husky": {
    "hooks": {
      "pre-commit": "lint-staged",
      "commit-msg": "commitlint -E HUSKY_GIT_PARAMS"
    }
  },
  "lint-staged": {
    "*.{js,ts,vue}": [
      "eslint --fix",
      "prettier --write"
    ],
    "*.{css,scss,vue}": [
      "prettier --write"
    ]
  }
}
```

---

## 3. 核心框架层

### 3.1 Vue 3.5 - 渐进式框架

#### Composition API + `<script setup>`

```vue
<template>
  <div class="user-list">
    <el-table :data="userList" :loading="loading" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="realName" label="真实姓名" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @current-change="fetchList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserList, deleteUser } from '@/api/system'
import type { UserInfo, PageQuery } from '@/types/api'
import { ElMessage, ElMessageBox } from 'element-plus'

// 响应式数据
const loading = ref(false)
const userList = ref<UserInfo[]>([])
const selectedRows = ref<UserInfo[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 获取列表
const fetchList = async () => {
  loading.value = true
  try {
    const query: PageQuery = { pageNum: pageNum.value, pageSize: pageSize.value }
    const res = await getUserList(query)
    userList.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

// 删除用户
const handleDelete = async (row: UserInfo) => {
  await ElMessageBox.confirm('确认删除该用户?', '警告', { type: 'warning' })
  await deleteUser(row.userId)
  ElMessage.success('删除成功')
  await fetchList()
}

// 多选
const handleSelectionChange = (rows: UserInfo[]) => {
  selectedRows.value = rows
}

// 页面加载时获取数据
onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.user-list {
  padding: 20px;
}
</style>
```

#### 响应式原理

Vue 3 使用 **Proxy** 实现响应式系统，相比Vue 2的`Object.defineProperty`有以下优势：
- 可以监听数组索引和长度变化
- 可以监听属性的新增和删除
- 性能更好，初始化速度提升2倍+

### 3.2 Vue Router 4 - 路由管理

#### 静态路由配置

```typescript
// src/router/index.ts
import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'House', affix: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

export default router
```

#### 动态路由与权限守卫

```typescript
// src/router/permission.ts
import router from './index'
import { useAuthStore } from '@/stores/auth'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

// 白名单
const whiteList = ['/login', '/404']

router.beforeEach(async (to, from, next) => {
  NProgress.start()

  const authStore = useAuthStore()
  const hasToken = authStore.token

  if (hasToken) {
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else {
      // 检查是否已获取用户信息和动态路由
      const hasRoles = authStore.roles && authStore.roles.length > 0
      if (hasRoles) {
        next()
      } else {
        try {
          // 获取用户信息
          await authStore.getUserInfo()
          // 生成动态路由
          const accessRoutes = await authStore.generateRoutes()
          // 动态添加路由
          accessRoutes.forEach(route => router.addRoute(route))
          // 触发重定向
          next({ ...to, replace: true })
        } catch (error) {
          // Token失效，清除信息并跳转登录
          await authStore.logout()
          next(`/login?redirect=${to.path}`)
          NProgress.done()
        }
      }
    }
  } else {
    // 无Token
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})
```

### 3.3 Pinia 2 - 状态管理

#### Store定义

```typescript
// src/stores/auth.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, logout, getUserInfo as fetchUserInfo } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/auth'
import type { UserInfo } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const token = ref(getToken())
  const userInfo = ref<UserInfo | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  // 计算属性
  const username = computed(() => userInfo.value?.username || '')
  const isAdmin = computed(() => roles.value.includes('admin'))

  // 登录
  const doLogin = async (username: string, password: string) => {
    const res = await login({ username, password })
    const accessToken = res.data.token
    token.value = accessToken
    setToken(accessToken)
  }

  // 获取用户信息
  const getUserInfo = async () => {
    const res = await fetchUserInfo()
    userInfo.value = res.data
    roles.value = res.data.roles
    permissions.value = res.data.permissions
  }

  // 登出
  const doLogout = async () => {
    await logout()
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    removeToken()
  }

  // 权限检查
  const hasPermission = (permission: string) => {
    return permissions.value.includes(permission) || isAdmin.value
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    username,
    isAdmin,
    doLogin,
    getUserInfo,
    doLogout,
    hasPermission,
  }
}, {
  persist: {
    // 持久化到 LocalStorage
    key: 'apex-auth',
    paths: ['token'],
  },
})
```

#### Store使用

```vue
<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

// 访问状态
console.log(authStore.username)
console.log(authStore.isAdmin)

// 调用方法
const handleLogin = async () => {
  await authStore.doLogin('admin', '123456')
}

// 权限检查
const canEdit = authStore.hasPermission('system:user:edit')
</script>
```

---

## 4. UI组件库与样式

### 4.1 Element Plus 2 - 企业级组件库

#### 核心特性
- **60+ 组件**: Table, Form, Dialog, Tree, Upload等
- **TypeScript支持**: 完整的类型定义
- **按需导入**: 自动导入插件支持
- **主题定制**: SCSS变量覆盖

#### 常用组件示例

**表格组件**

```vue
<template>
  <el-table
    :data="tableData"
    :loading="loading"
    border
    stripe
    highlight-current-row
    @selection-change="handleSelectionChange"
  >
    <el-table-column type="selection" width="55" />
    <el-table-column type="index" label="序号" width="60" />
    <el-table-column prop="name" label="姓名" sortable />
    <el-table-column prop="email" label="邮箱" width="200" />
    <el-table-column prop="status" label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'danger'">
          {{ row.status === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="200" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
```

**表单组件**

```vue
<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="formRules"
    label-width="100px"
  >
    <el-form-item label="用户名" prop="username">
      <el-input v-model="formData.username" placeholder="请输入用户名" clearable />
    </el-form-item>

    <el-form-item label="密码" prop="password">
      <el-input
        v-model="formData.password"
        type="password"
        placeholder="请输入密码"
        show-password
      />
    </el-form-item>

    <el-form-item label="角色" prop="roleIds">
      <el-select v-model="formData.roleIds" multiple placeholder="请选择角色">
        <el-option
          v-for="role in roleOptions"
          :key="role.value"
          :label="role.label"
          :value="role.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="状态" prop="status">
      <el-radio-group v-model="formData.status">
        <el-radio :label="1">启用</el-radio>
        <el-radio :label="0">禁用</el-radio>
      </el-radio-group>
    </el-form-item>

    <el-form-item>
      <el-button type="primary" @click="handleSubmit">提交</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

const formRef = ref<FormInstance>()
const formData = reactive({
  username: '',
  password: '',
  roleIds: [],
  status: 1,
})

const formRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  roleIds: [
    { required: true, message: '请选择角色', trigger: 'change' },
  ],
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  // 提交表单
}

const handleReset = () => {
  formRef.value?.resetFields()
}
</script>
```

### 4.2 Sass/SCSS - CSS预处理器

#### 主题定制

```scss
// src/styles/element/index.scss
// Element Plus 主题定制

@use 'element-plus/theme-chalk/src/mixins/mixins' as *;
@use 'element-plus/theme-chalk/src/common/var' as *;

// 主题色
$--colors: (
  'primary': (
    'base': #409EFF,
  ),
  'success': (
    'base': #67C23A,
  ),
  'warning': (
    'base': #E6A23C,
  ),
  'danger': (
    'base': #F56C6C,
  ),
  'error': (
    'base': #F56C6C,
  ),
  'info': (
    'base': #909399,
  ),
);

// 字体
$--font-size-base: 14px;
$--font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;

// 边框圆角
$--border-radius-base: 4px;
```

#### 全局样式

```scss
// src/styles/index.scss
@import './variables.scss';
@import './element/index.scss';

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  width: 100%;
  height: 100%;
  font-family: $font-family;
  font-size: $font-size-base;
  color: $text-color-primary;
  background-color: $bg-color;
}

// 滚动条样式
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-thumb {
  background-color: rgba(0, 0, 0, 0.2);
  border-radius: 4px;

  &:hover {
    background-color: rgba(0, 0, 0, 0.3);
  }
}

// 工具类
.text-center {
  text-align: center;
}

.text-right {
  text-align: right;
}

.clearfix::after {
  content: '';
  display: table;
  clear: both;
}
```

### 4.3 ECharts 5 - 数据可视化

```vue
<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

const initChart = () => {
  if (!chartRef.value) return

  chartInstance = echarts.init(chartRef.value)

  const option: EChartsOption = {
    title: {
      text: '用户增长趋势',
    },
    tooltip: {
      trigger: 'axis',
    },
    legend: {
      data: ['新增用户', '活跃用户'],
    },
    xAxis: {
      type: 'category',
      data: ['1月', '2月', '3月', '4月', '5月', '6月'],
    },
    yAxis: {
      type: 'value',
    },
    series: [
      {
        name: '新增用户',
        type: 'line',
        data: [120, 200, 150, 280, 370, 420],
        smooth: true,
      },
      {
        name: '活跃用户',
        type: 'line',
        data: [100, 180, 140, 260, 350, 400],
        smooth: true,
      },
    ],
  }

  chartInstance.setOption(option)

  // 响应式
  window.addEventListener('resize', handleResize)
}

const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  initChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.chart-container {
  width: 100%;
  height: 400px;
}
</style>
```

---

## 5. 数据请求与工具

### 5.1 Axios - HTTP客户端

#### 请求封装

```typescript
// src/utils/request.ts
import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getToken } from './auth'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data

    // code不为0表示业务错误
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')

      // 401: Token过期
      if (res.code === 401) {
        const authStore = useAuthStore()
        authStore.doLogout()
        window.location.href = '/login'
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res
  },
  (error) => {
    console.error('Response error:', error)

    let message = '请求失败'
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权，请重新登录'
          const authStore = useAuthStore()
          authStore.doLogout()
          window.location.href = '/login'
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求资源不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        default:
          message = `连接错误 ${error.response.status}`
      }
    } else if (error.request) {
      message = '网络连接失败'
    }

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
```

#### API模块示例

```typescript
// src/api/system.ts
import request from '@/utils/request'
import type { Result, PageQuery, PageResult, UserInfo } from '@/types/api'

/**
 * 获取用户列表
 */
export function getUserList(params: PageQuery) {
  return request<Result<PageResult<UserInfo>>>({
    url: '/system/user/list',
    method: 'get',
    params,
  })
}

/**
 * 获取用户详情
 */
export function getUserDetail(userId: number) {
  return request<Result<UserInfo>>({
    url: `/system/user/${userId}`,
    method: 'get',
  })
}

/**
 * 新增用户
 */
export function addUser(data: Partial<UserInfo>) {
  return request<Result<void>>({
    url: '/system/user',
    method: 'post',
    data,
  })
}

/**
 * 更新用户
 */
export function updateUser(userId: number, data: Partial<UserInfo>) {
  return request<Result<void>>({
    url: `/system/user/${userId}`,
    method: 'put',
    data,
  })
}

/**
 * 删除用户
 */
export function deleteUser(userId: number) {
  return request<Result<void>>({
    url: `/system/user/${userId}`,
    method: 'delete',
  })
}
```

### 5.2 工具库

#### Day.js - 日期处理

```typescript
// src/utils/date.ts
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'

dayjs.locale('zh-cn')
dayjs.extend(relativeTime)
dayjs.extend(utc)
dayjs.extend(timezone)

/**
 * 格式化日期
 */
export function formatDate(date: Date | string | number, format = 'YYYY-MM-DD HH:mm:ss') {
  return dayjs(date).format(format)
}

/**
 * 相对时间
 */
export function fromNow(date: Date | string | number) {
  return dayjs(date).fromNow()
}

/**
 * 时区转换
 */
export function toTimezone(date: Date | string | number, tz = 'Asia/Shanghai') {
  return dayjs(date).tz(tz)
}
```

#### Lodash-es - 工具函数

```typescript
import { debounce, throttle, cloneDeep, isEqual } from 'lodash-es'

// 防抖
const searchDebounced = debounce((keyword: string) => {
  console.log('搜索:', keyword)
}, 300)

// 节流
const scrollThrottled = throttle(() => {
  console.log('滚动')
}, 100)

// 深拷贝
const cloned = cloneDeep(obj)

// 深度比较
const isSame = isEqual(obj1, obj2)
```

---

## 6. 功能模块

### 6.1 认证授权模块

- 登录/登出
- JWT Token管理
- 权限指令 `v-permission`
- 路由守卫

### 6.2 系统管理模块

- 用户管理
- 角色管理
- 菜单管理
- 部门管理
- 字典管理
- 配置管理

### 6.3 监控审计模块

- 登录日志
- 操作日志
- 在线用户

### 6.4 工作流模块

- 流程设计器
- 任务审批
- 流程监控

### 6.5 代码生成模块

- CRUD自动生成
- Vue组件生成
- API接口生成

---

## 7. 技术选型理由

### 7.1 为什么选择Vue 3？

1. **Composition API**: 更好的逻辑复用和代码组织
2. **性能提升**: 比Vue 2快2倍+
3. **TypeScript支持**: 完整的类型推断
4. **Tree-shaking**: 更小的打包体积
5. **生态成熟**: 社区活跃，插件丰富

### 7.2 为什么选择Vite？

1. **极速启动**: 原生ESM，无需打包
2. **即时HMR**: 热更新 < 50ms
3. **按需编译**: 只编译当前页面
4. **优化构建**: 基于Rollup，产物优化
5. **插件丰富**: Vue、React、TypeScript等

### 7.3 为什么选择TypeScript？

1. **类型安全**: 编译时发现错误
2. **智能提示**: IDE支持更好
3. **代码可维护性**: 重构更安全
4. **团队协作**: 接口契约明确
5. **工程化**: 大型项目必备

### 7.4 为什么选择Pinia？

1. **轻量级**: 比Vuex小60%+
2. **TypeScript友好**: 完整类型推断
3. **Composition API风格**: 更符合Vue 3
4. **DevTools支持**: 调试体验好
5. **插件系统**: 持久化、订阅等

### 7.5 为什么选择Element Plus？

1. **企业级组件**: 60+组件满足大部分需求
2. **Vue 3原生支持**: 不是移植版本
3. **TypeScript**: 完整类型定义
4. **文档完善**: 中英文文档齐全
5. **社区活跃**: Issues响应快

---

## 8. 性能优化策略

### 8.1 构建优化

```typescript
// vite.config.ts
export default defineConfig({
  build: {
    // 代码分割
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          'echarts': ['echarts'],
        },
      },
    },
    // 压缩
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
  },
})
```

### 8.2 路由懒加载

```typescript
const routes = [
  {
    path: '/user',
    component: () => import('@/views/system/user/index.vue'),
  },
]
```

### 8.3 组件懒加载

```vue
<script setup>
import { defineAsyncComponent } from 'vue'

const HeavyComponent = defineAsyncComponent(() =>
  import('@/components/HeavyComponent.vue')
)
</script>
```

### 8.4 虚拟滚动

```vue
<template>
  <el-table-v2
    :columns="columns"
    :data="data"
    :width="700"
    :height="400"
    fixed
  />
</template>
```

### 8.5 请求防抖

```typescript
import { useDebounceFn } from '@vueuse/core'

const handleSearch = useDebounceFn((keyword: string) => {
  // 搜索逻辑
}, 300)
```

---

## 9. 开发规范

详见 [前端开发规范文档](../04-development/frontend-development-standards.md)

---

## 10. 常见问题

### Q1: 如何配置代理解决跨域？

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
```

### Q2: 如何自定义Element Plus主题？

参见 [4.2 Sass/SCSS](#42-sassscss---css预处理器)

### Q3: 如何实现按钮级权限控制？

```typescript
// src/directives/permission.ts
import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

export const permission: Directive = {
  mounted(el, binding) {
    const { value } = binding
    const authStore = useAuthStore()

    if (value && !authStore.hasPermission(value)) {
      el.parentNode?.removeChild(el)
    }
  },
}
```

```vue
<template>
  <el-button v-permission="'system:user:delete'" type="danger">
    删除
  </el-button>
</template>
```

### Q4: 如何处理Token过期？

参见 [5.1 Axios - HTTP客户端](#51-axios---http客户端) 的响应拦截器

### Q5: 如何优化首屏加载速度？

1. 路由懒加载
2. 组件懒加载
3. Gzip压缩
4. CDN加速
5. 图片懒加载
6. 骨架屏

---

## 附录

### A. 依赖清单

```json
{
  "dependencies": {
    "vue": "^3.5.0",
    "vue-router": "^4.4.0",
    "pinia": "^2.2.0",
    "element-plus": "^2.8.0",
    "axios": "^1.7.0",
    "echarts": "^5.5.0",
    "@vueuse/core": "^11.0.0",
    "dayjs": "^1.11.0",
    "lodash-es": "^4.17.21",
    "nprogress": "^0.2.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.1.0",
    "vite": "^6.0.0",
    "typescript": "^5.5.0",
    "@typescript-eslint/eslint-plugin": "^8.0.0",
    "@typescript-eslint/parser": "^8.0.0",
    "eslint": "^9.0.0",
    "eslint-plugin-vue": "^9.27.0",
    "prettier": "^3.3.0",
    "sass": "^1.77.0"
  }
}
```

### B. 目录结构

```
apex-admin-web/
├── public/             # 静态资源
├── src/
│   ├── api/            # API接口
│   ├── assets/         # 资源文件
│   ├── components/     # 公共组件
│   ├── directives/     # 自定义指令
│   ├── layouts/        # 布局组件
│   ├── router/         # 路由配置
│   ├── stores/         # Pinia状态
│   ├── styles/         # 全局样式
│   ├── types/          # 类型定义
│   ├── utils/          # 工具函数
│   ├── views/          # 页面视图
│   ├── App.vue         # 根组件
│   ├── main.ts         # 入口文件
│   └── settings.ts     # 应用设置
├── .eslintrc.cjs       # ESLint配置
├── .prettierrc         # Prettier配置
├── tsconfig.json       # TypeScript配置
├── vite.config.ts      # Vite配置
└── package.json        # 依赖配置
```

### C. 相关文档

- [前端开发规范](../04-development/frontend-development-standards.md)
- [API路由映射](../_archive/api/route-mapping.md)
- [前端迁移指南](../04-development/frontend/migration-guide.md)
- [路由配置指南](../04-development/frontend/routing-guide.md)

---

**文档维护**: 架构团队
**最后更新**: 2026-09-04
