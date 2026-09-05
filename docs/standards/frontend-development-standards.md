# Apex Scaffold 前端开发规范

**版本**: v1.0.0  
**适用范围**: `apex-admin-web` 独立管理后台前端工程  
**技术栈**: Vue 3.5、Vite 6、TypeScript 5.x、Pinia、Vue Router 4、Element Plus、Axios、ECharts  
**最后更新**: 2026-09-03

> 前端源码位于独立工程 `/Users/work/apex-admin-web`。本文档依据当前迁移文档、路由文档和该工程现有源码整理；具体组件、API 和 Store 文件以独立工程实际内容为准。

---

## 目录

1. [适用范围与基本原则](#1-适用范围与基本原则)
2. [项目结构](#2-项目结构)
3. [Vue 组件规范](#3-vue-组件规范)
4. [TypeScript 规范](#4-typescript-规范)
5. [状态管理规范](#5-状态管理规范)
6. [API 请求规范](#6-api-请求规范)
7. [路由与权限规范](#7-路由与权限规范)
8. [Element Plus 与交互规范](#8-element-plus-与交互规范)
9. [样式与主题规范](#9-样式与主题规范)
10. [性能与可维护性](#10-性能与可维护性)
11. [构建、测试与提交](#11-构建测试与提交)
12. [开发检查清单](#12-开发检查清单)

---

## 1. 适用范围与基本原则

本规范适用于 `apex-admin-web` 的页面、组件、API 模块、Pinia Store、路由、指令和构建配置。

基本原则：

- 优先使用 Vue 3 `<script setup lang="ts">` 和 Composition API。
- 页面负责展示、交互和状态组合，业务请求集中放在 `src/api/`。
- 服务端状态和会话状态放入 Pinia，局部表单状态保留在组件内部。
- 所有接口数据通过明确的 TypeScript 类型表达，禁止用 `any` 掩盖契约问题。
- 动态路由只允许加载固定注册表中的组件，不根据后端字符串直接执行动态 import。
- 权限控制前后端同时执行，前端控制用于改善体验，不能代替后端鉴权。
- 页面应覆盖加载中、空数据、请求失败、无权限和会话失效状态。

---

## 2. 项目结构

当前工程采用以下主要结构：

```text
src/
├── api/             API 请求模块
├── components/      可复用组件
├── directives/      自定义指令
├── layouts/         页面布局
├── router/          静态与动态路由
├── stores/          Pinia 状态
├── types/           共享类型
├── utils/           请求、认证等工具
├── views/           页面视图
├── App.vue          根组件
├── main.ts          应用入口
└── settings.ts      应用设置
```

目录职责：

| 目录 | 约定 |
|---|---|
| `src/views` | 按业务和菜单组织页面，例如 `system/user`、`monitor/audit` |
| `src/components` | 与具体业务弱相关、可跨页面复用的组件 |
| `src/api` | 按业务域拆分请求函数，例如 `auth.ts`、`system.ts`、`monitor.ts` |
| `src/stores` | 会话、权限和全局 UI 状态 |
| `src/types` | API、页面和领域对象的共享类型 |
| `src/router` | 静态路由、动态菜单路由和导航守卫 |
| `src/utils` | 请求封装、Token 操作和无 UI 业务工具 |
| `src/directives` | 跨页面使用的指令，例如按钮权限指令 |

新增页面时，优先沿用现有业务目录和页面形态；不要为了单个页面创建新的全局抽象。

---

## 3. Vue 组件规范

### 3.1 组件文件

- 页面组件使用 `index.vue`，目录名使用业务语义命名。
- 组件名使用 PascalCase，页面目录使用小写短横线或现有业务命名。
- 组件脚本统一使用 `<script setup lang="ts">`。
- 模板、脚本、样式按模板、脚本、样式顺序组织。
- 组件只保留当前视图需要的状态和行为，跨页面状态移入 Store。

```vue
<template>
  <el-button type="primary" :loading="loading" @click="loadList">
    查询
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    await fetchData()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
}
</style>
```

### 3.2 Props 与 Emits

- Props 使用接口或类型别名集中定义。
- Props 只读，子组件不得直接修改传入对象。
- 事件名使用描述性动词，例如 `change`、`submit`、`close`。
- 复杂对象优先传递明确类型，不使用 `Record<string, any>`。
- 组件需要双向绑定时遵循 Vue 的 `modelValue` 与 `update:modelValue` 约定。

```ts
interface Props {
  visible: boolean
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '详情',
})

const emit = defineEmits<{
  close: []
  submit: [id: number]
}>()
```

### 3.3 生命周期与异步操作

- 页面首次加载数据使用 `onMounted` 或路由进入后的明确触发点。
- 组件卸载时清理定时器、事件监听器和 ECharts 实例。
- 异步请求使用 `try/finally` 管理 loading 状态。
- 页面离开后不再更新已销毁组件的状态。
- 监控大盘等自动刷新功能必须提供停止条件，并避免重复创建定时器。

---

## 4. TypeScript 规范

### 4.1 编译约束

项目 `tsconfig` 开启了 `strict`、`noUnusedLocals`、`noUnusedParameters`、`noFallthroughCasesInSwitch` 和 `noEmit`。新增代码必须在严格类型检查下通过。

- 优先使用 `interface` 描述可扩展对象结构，使用 `type` 表达联合类型、映射类型和组合类型。
- 不使用隐式 `any`。
- 不使用无依据的类型断言；必须断言时先说明数据来源和边界。
- API 响应、表单、表格行和分页查询参数分别定义类型。
- 常量映射使用 `as const` 保留字面量类型。
- 处理可空数据时显式判断，不依赖运行时恰好存在。

```ts
export interface PageQuery {
  page: number
  size: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
}

export type UserStatus = 0 | 1
```

### 4.2 类型组织

- 跨模块共享类型放在 `src/types/`。
- 只被单个 API 模块使用的查询参数类型可以放在对应 `src/api/*.ts`。
- 页面专用类型放在页面脚本中，避免把局部类型污染为全局类型。
- 后端字段命名和前端类型命名保持契约一致；时间字段统一按现有 API 返回格式处理。
- 修改后端 DTO 或分页结构时，同步修改前端类型、API 函数和页面渲染逻辑。

### 4.3 常见类型错误

避免以下写法：

```ts
const value: any = response.data
const item = data as User
items.push({} as User)
```

优先通过泛型、类型守卫、默认值和接口定义解决类型问题。

---

## 5. 状态管理规范

### 5.1 Store 边界

当前使用 Pinia setup store 风格，主要 Store 包括：

- `src/stores/user.ts`：Token、当前用户身份、角色、权限和初始化状态。
- `src/stores/permission.ts`：权限码判断。
- `src/stores/app.ts`：侧边栏折叠、主题和 UI 偏好。

Store 只管理跨组件或跨路由共享的状态。表格筛选、弹窗可见性和临时表单值默认放在页面组件中。

### 5.2 会话状态

- Token 通过现有认证工具从 `localStorage` 读取和写入。
- 登录成功后保存 `accessToken`，再加载用户身份、角色和权限。
- 使用 `initialized` 防止多个路由守卫重复加载身份。
- 401 响应统一清理本地会话并跳转登录页。
- 不在日志、错误提示或 URL 中输出 Token。

### 5.3 Store 编码

- Store 使用 `defineStore`，状态用 `ref` 或 `reactive`，派生值用 `computed`。
- 对外暴露有业务语义的方法，例如 `loadIdentity`、`logout`、`hasPermission`。
- 异步 Action 明确返回 `Promise`，由调用方决定 loading 和提示时机。
- 不在 Store 中直接操作页面 DOM。
- 同一请求避免由多个组件重复触发，必要时在 Store 中缓存初始化结果。

```ts
export const usePermissionStore = defineStore('permission', () => {
  const permissions = ref<string[]>([])

  function hasPermission(code: string): boolean {
    if (!code || permissions.value.length === 0) {
      return true
    }
    return code.split(',').some((item) => permissions.value.includes(item))
  }

  return { permissions, hasPermission }
})
```

---

## 6. API 请求规范

### 6.1 请求封装

统一使用 `src/utils/request.ts` 提供的 Axios 实例和 `request/get/post/put/del` 方法。页面和 Store 不直接创建 Axios 实例。

当前请求封装约定：

- `baseURL` 来自 `VITE_API_BASE_URL`。
- 默认超时时间为 30 秒。
- 请求拦截器统一添加 `Authorization: Bearer <token>`。
- `Result.code === 0` 时返回 `result.data`。
- 业务错误转换为 `ApiError`，由统一逻辑展示错误和 `traceId`。
- HTTP 401 清理会话并跳转登录页。
- 文件流等非标准响应按原始响应返回。

### 6.2 API 模块

API 按业务域拆分到 `src/api/`，函数名使用动词和资源名，例如 `getCurrentUser`、`listUsers`、`createJob`。

```ts
export function listUsers(
  params: UserQuery,
): Promise<PageResponse<User>> {
  return get<PageResponse<User>>(
    '/admin-api/v1/users',
    params as Record<string, unknown>,
  )
}
```

- API 路径不散落在页面模板和 Store 中。
- 查询参数、请求体和返回值都使用类型。
- `/api/v1` 用于认证和当前用户等业务接口。
- `/admin-api/v1` 用于后台管理、监控和工作流接口。
- 不在请求函数中弹出页面级提示，除非该 API 明确是全局基础设施能力。
- 删除、批量操作和重复提交操作需要结合按钮 loading、确认框或幂等机制。

### 6.3 错误处理

- 页面捕获请求异常后恢复 loading 状态，并给用户可执行的提示。
- 统一错误提示优先使用请求封装提供的 `showError` 行为。
- 不把后端堆栈、SQL、内部地址或凭证展示给用户。
- 空列表是正常业务状态，应显示空态，不应当作异常处理。
- 对 401、403、404 和 5xx 保持不同的用户反馈和页面行为。

---

## 7. 路由与权限规范

### 7.1 静态路由

路由入口为 `src/router/index.ts`，使用 `createWebHistory()`。静态路由包括登录、首页、工作台、工作流监控和 404 页面。

- 登录页不要求 Token。
- 管理页面统一使用布局组件承载。
- 未匹配路径进入 404。
- 路由名称、路径和菜单 `path` 保持稳定，修改前确认后端菜单契约。

### 7.2 动态路由

动态路由位于 `src/router/dynamic.ts`，由后端菜单树驱动：

1. 从 `/admin-api/v1/menus` 拉取菜单树。
2. `menuType=0` 表示目录，`menuType=1` 表示菜单，`menuType=2` 表示按钮权限。
3. 页面组件通过固定的 `VIEW_REGISTRY` 映射加载。
4. `visible=0` 的菜单可以注册路由但不显示在侧边栏。
5. 后端 `component` 字段必须与前端注册表键一致。
6. 按钮权限不生成路由，只用于页面操作控制。

新增页面时，先添加视图组件，再在 `VIEW_REGISTRY` 中显式注册加载器，最后确认后端菜单的 `component`、`path` 和权限码。

```ts
const VIEW_REGISTRY = {
  'monitor/audit/index': () => import('@/views/monitor/audit/index.vue'),
  'monitor/api-call-log/index': () =>
    import('@/views/monitor/api-call-log/index.vue'),
} as const
```

禁止将后端返回的任意字符串直接传入动态 import。这样可以避免路径不可控，也能让构建工具正确分析分包。

### 7.3 路由守卫

全局守卫负责：

- 无 Token 时跳转登录页并保留 `redirect`。
- 已有 Token 但身份未初始化时加载用户身份。
- 动态路由尚未添加时执行一次菜单路由构建。
- 避免重复添加动态路由和重复请求菜单。

路由守卫不能承载页面业务请求、复杂数据处理或弹窗逻辑。

### 7.4 按钮权限

使用已注册的 `v-permission` 指令控制按钮级操作：

```vue
<el-button
  v-permission="'staff:manage'"
  type="success"
  :icon="Plus"
  @click="openCreate"
>
  新增
</el-button>
```

- 权限码使用后端定义的稳定值。
- 无权限时由指令移除操作元素。
- 空权限码和当前项目约定的空权限集合行为保持一致。
- 前端隐藏按钮不能代替后端 `@RequirePermission` 等服务端校验。

---

## 8. Element Plus 与交互规范

### 8.1 组件使用

- 全局使用 Element Plus 中文语言包和全局样式。
- 表单使用 `el-form`、`el-form-item` 和规则校验。
- 表格统一处理 loading、空数据、分页和超长文本。
- 危险操作使用确认弹窗，并在请求期间禁用重复提交。
- 需要展示完整内容时使用 `show-overflow-tooltip` 或详情弹窗。
- 图标按钮应提供清晰的 `title` 或 `aria-label`。

### 8.2 表单

- 表单模型使用明确接口类型。
- 新建和编辑场景区分默认值与回填值，避免复用对象造成引用污染。
- 提交前执行校验，校验失败不发请求。
- 提交成功后关闭弹窗、刷新列表并清理表单状态。
- 重置操作同时清空筛选条件并将分页恢复到第一页。

### 8.3 分页与列表

- 统一复用现有 `Pagination` 组件。
- 页码变化只更新分页参数并重新加载列表。
- 新查询和重置操作将 `page` 设为 1。
- `size` 使用项目已有选项，不在页面中随意定义不同分页协议。
- 请求失败时保留筛选条件，避免用户重复输入。

### 8.4 ECharts

- 图表实例在挂载后创建，在卸载时销毁。
- 容器必须有稳定高度，避免图表初始化为 0 高度。
- 窗口尺寸变化时调用 `resize`，并清理监听器。
- 自动刷新必须清理定时器，刷新间隔遵循现有监控页面约定。
- 数据为空时显示空状态或清晰的无数据提示。

---

## 9. 样式与主题规范

### 9.1 SFC 样式

当前页面主要使用 `<style scoped>`，未配置 SCSS、Less 或 Tailwind。新增页面默认沿用该方式。

- 使用 2 个空格缩进，遵循 `.editorconfig` 的 UTF-8、LF 和行尾清理规则。
- 页面样式放在页面组件内，跨页面样式才考虑放入 `App.vue` 或共享组件。
- 类名使用有语义的名称，避免 `.box`、`.red`、`.left1` 等含义不稳定的名称。
- 不使用深层选择器覆盖无关页面组件。
- 复用 Element Plus 变量和现有主题机制，不重复定义整套颜色系统。

### 9.2 主题

`src/stores/app.ts` 管理主题状态，并通过 `document.documentElement` 的 `dark` class 应用主题。

- 主题切换必须通过 Store 统一处理。
- 颜色优先使用 Element Plus CSS 变量。
- 明暗主题下都要检查文本对比度、边框、表格、弹窗和图表颜色。
- 不在单个页面硬编码只适用于某一主题的颜色。
- 主题偏好持久化行为保持与现有 `localStorage` 约定一致。

### 9.3 响应式布局

- 页面内容区使用弹性布局、网格或 Element Plus 布局组件。
- 表格在窄屏下提供横向滚动或合理隐藏次要列。
- 筛选表单允许换行，按钮和输入框不得相互覆盖。
- 固定高度只用于表格、图表和布局中确有需要的区域。
- 文字、按钮和状态标签必须在常见桌面宽度下完整显示。

---

## 10. 性能与可维护性

- 页面和动态路由组件使用按需加载，避免把所有业务页面打入首屏。
- 列表请求避免在多个生命周期和守卫中重复调用。
- 大列表使用分页；长列表需要评估虚拟滚动或服务端筛选。
- 高频输入查询使用防抖，连续点击操作使用 loading 或节流。
- 监听器、定时器、订阅和图表实例必须在卸载时清理。
- 避免在模板中执行复杂计算，使用 `computed` 或预处理数据。
- 避免无意义的深层 `watch`，明确监听源和副作用范围。
- 图片、图表和大模块使用按需加载，构建后关注 chunk 体积。
- 不在前端保存长期有效的敏感凭证，不把 Token 拼接到普通日志和查询参数中。

---

## 11. 构建、测试与提交

### 11.1 当前可用命令

项目要求 Node.js `>=18.0.0`，当前 `package.json` 提供：

```bash
npm run dev
npm run build
npm run preview
```

`npm run build` 会依次执行：

```bash
vue-tsc --noEmit
vite build
```

开发服务器默认使用 5173 端口，`/api` 和 `/admin-api` 代理到后端 8080，构建产物输出到 `dist`。

### 11.2 测试与代码检查现状

当前独立前端工程尚未配置 `test`、`lint`、`format` 或 `typecheck` 独立脚本，也未发现 Vitest、Jest、Vue Test Utils、Playwright、Cypress、ESLint、Prettier 或 Stylelint 配置。

因此当前提交前最低要求是：

```bash
npm run build
```

新增复杂交互、权限逻辑、数据转换或公共组件时，应补充与工程工具链匹配的自动化测试。引入测试或 lint 工具时，需要同时补充 npm script、配置文件和团队执行说明，不能只添加依赖。

### 11.3 提交规范

前端提交遵循仓库统一的 Conventional Commits：

```text
feat(web): add API call log view
fix(router): avoid duplicate dynamic routes
docs(web): update frontend standards
```

提交说明应包含影响范围和验证命令。前后端联调变更还应说明接口路径、权限码、菜单组件名和兼容性影响。

---

## 12. 开发检查清单

### 新增页面

- [ ] 页面放在正确的 `src/views` 业务目录。
- [ ] 使用 `<script setup lang="ts">` 和明确类型。
- [ ] API 请求位于对应 `src/api` 模块。
- [ ] 覆盖加载中、空数据、错误和无权限状态。
- [ ] 分页、重置、表单校验和重复提交处理符合现有模式。
- [ ] 动态页面已加入 `VIEW_REGISTRY`。
- [ ] 后端菜单 `path`、`component` 和权限码已核对。
- [ ] 明暗主题和窄屏布局已检查。

### 修改会话、权限或路由

- [ ] Token 读写仍通过现有认证工具。
- [ ] 401、403 和登录重定向行为未被破坏。
- [ ] 动态路由不会重复添加。
- [ ] 组件加载路径来自固定注册表。
- [ ] 按钮权限使用 `v-permission`，服务端仍保留最终鉴权。
- [ ] 权限为空、菜单为空和接口失败场景已验证。

### 提交代码

- [ ] `npm run build` 通过。
- [ ] 无未使用的局部变量、参数和导入。
- [ ] 无新增无依据的 `any` 或类型断言。
- [ ] 无明文 Token、密钥和敏感数据日志。
- [ ] 已清理定时器、监听器和图表实例。
- [ ] 提交信息符合 Conventional Commits。
- [ ] 联调变更已记录接口和权限影响。

---

## 参考资料

- [前端迁移指南](../frontend/migration-guide.md)
- [路由配置与启动指南](../frontend/routing-guide.md)
- [前端项目 `package.json`](/Users/work/apex-admin-web/package.json)
- [Vue 官方文档](https://vuejs.org/)
- [TypeScript 官方文档](https://www.typescriptlang.org/docs/)
- [Pinia 官方文档](https://pinia.vuejs.org/)
- [Vue Router 官方文档](https://router.vuejs.org/)
- [Element Plus 官方文档](https://element-plus.org/)

**文档维护**: Apex Team  
**版本历史**: v1.0.0（2026-09-03，初始版本）
