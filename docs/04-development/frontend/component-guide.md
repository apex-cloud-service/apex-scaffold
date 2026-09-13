# 前端组件使用指南

**文件已复制成功**: 2026-09-07  
**组件位置**: 
- `src/components/EmptyState.vue`
- `src/utils/validate.ts`

---

## ✅ 复制完成确认

```bash
✓ EmptyState.vue → /Users/work/apex-admin-web/src/components/EmptyState.vue (1.3KB)
✓ validate.ts    → /Users/work/apex-admin-web/src/utils/validate.ts (2.9KB)
```

---

## 📚 使用指南

### 1. EmptyState组件使用

#### 基础用法

```vue
<template>
  <el-card>
    <!-- Loading状态 -->
    <template v-if="loading">
      <el-skeleton :rows="5" animated />
    </template>
    
    <!-- 空数据状态 -->
    <template v-else-if="list.length === 0">
      <EmptyState description="暂无用户数据">
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </EmptyState>
    </template>
    
    <!-- 有数据时显示表格 -->
    <template v-else>
      <el-table :data="list">
        <!-- 表格列 -->
      </el-table>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import { ref } from 'vue'

const loading = ref(false)
const list = ref([])

const openCreate = () => {
  // 新增逻辑
}
</script>
```

#### 不同类型的空状态

```vue
<!-- 默认类型 -->
<EmptyState description="暂无数据" />

<!-- 搜索无结果 -->
<EmptyState type="search" description="未找到匹配的结果">
  <el-button @click="clearSearch">清除搜索条件</el-button>
</EmptyState>

<!-- 网络错误 -->
<EmptyState type="network" description="网络连接失败">
  <el-button @click="retry">重试</el-button>
</EmptyState>

<!-- 权限不足 -->
<EmptyState type="permission" description="您没有权限查看此内容" />

<!-- 自定义图片 -->
<EmptyState 
  image="https://example.com/custom-image.svg" 
  :image-size="150"
  description="自定义提示"
/>
```

---

### 2. validate工具库使用

#### 导入校验规则

```typescript
import { 
  validatePhone, 
  validateEmail, 
  validatePassword,
  validateURL,
  validatePort
} from '@/utils/validate'
```

#### 在表单中使用

**用户表单示例**:

```vue
<template>
  <el-form :model="form" :rules="formRules" ref="formRef">
    <el-form-item label="昵称" prop="nickname">
      <el-input v-model="form.nickname" placeholder="请输入昵称" />
    </el-form-item>
    
    <el-form-item label="手机号" prop="phone">
      <el-input v-model="form.phone" placeholder="请输入手机号" />
    </el-form-item>
    
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" placeholder="请输入邮箱" />
    </el-form-item>
    
    <el-form-item label="密码" prop="password">
      <el-input v-model="form.password" type="password" placeholder="请输入密码" />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { validatePhone, validateEmail, validatePassword } from '@/utils/validate'

const formRef = ref()
const form = reactive({
  nickname: '',
  phone: '',
  email: '',
  password: ''
})

const formRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度2-20字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, validator: validatePhone, trigger: 'blur' }
  ],
  email: [
    { validator: validateEmail, trigger: 'blur' }
  ],
  password: [
    { required: true, validator: validatePassword, trigger: 'blur' }
  ]
}

const handleSubmit = async () => {
  await formRef.value.validate()
  // 提交逻辑
}
</script>
```

**数据库配置表单示例**:

```vue
<script setup lang="ts">
import { validateURL, validatePort } from '@/utils/validate'

const dbFormRules = {
  name: [
    { required: true, message: '请输入数据源名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度2-50字符', trigger: 'blur' }
  ],
  url: [
    { required: true, message: '请输入JDBC URL', trigger: 'blur' },
    { validator: validateURL, trigger: 'blur' }
  ],
  host: [
    { required: true, message: '请输入主机地址', trigger: 'blur' }
  ],
  port: [
    { required: true, message: '请输入端口', trigger: 'blur' },
    { validator: validatePort, trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}
</script>
```

#### 可用的校验规则

| 函数名 | 用途 | 规则 |
|--------|------|------|
| `validatePhone` | 手机号校验 | 11位数字,1开头 |
| `validateEmail` | 邮箱校验 | 标准邮箱格式 |
| `validatePassword` | 密码强度校验 | 至少8位,含大小写字母和数字 |
| `validateURL` | URL格式校验 | http://或https://开头 |
| `validatePort` | 端口号校验 | 1-65535 |
| `validateChineseName` | 中文姓名校验 | 2-4个汉字 |
| `validateIDCard` | 身份证号校验 | 18位标准格式 |
| `validateIP` | IP地址校验 | xxx.xxx.xxx.xxx格式 |
| `validateInteger` | 整数校验 | 整数 |
| `validatePositiveInteger` | 正整数校验 | 大于0的整数 |

---

## 🎯 需要修改的页面

### 使用EmptyState组件

以下页面需要将空数据展示替换为EmptyState组件:

1. ✅ `src/views/system/user/index.vue` - 用户管理
2. ✅ `src/views/system/menu/index.vue` - 菜单管理
3. ✅ `src/views/system/role/index.vue` - 角色管理
4. ✅ `src/views/system/dept/index.vue` - 部门管理
5. ✅ `src/views/system/dict/index.vue` - 字典管理
6. ✅ `src/views/system/config/index.vue` - 参数配置
7. ✅ `src/views/system/database/index.vue` - 数据库配置
8. ✅ `src/views/system/job/index.vue` - 定时任务
9. ✅ `src/views/monitor/loginlog/index.vue` - 登录日志
10. ✅ `src/views/monitor/audit/index.vue` - 审计日志

### 使用validate校验规则

以下页面需要补充表单校验:

1. ✅ `src/views/system/user/index.vue` - 用户表单(手机/邮箱/密码)
2. ✅ `src/views/system/dept/index.vue` - 部门表单(名称/排序)
3. ✅ `src/views/system/dict/index.vue` - 字典表单(名称/类型)
4. ✅ `src/views/system/config/index.vue` - 参数表单(键名/键值)
5. ✅ `src/views/system/database/index.vue` - 数据库表单(URL/端口)
6. ✅ `src/views/system/job/index.vue` - 任务表单(cron表达式)
7. ✅ `src/views/profile/index.vue` - 个人信息(手机/邮箱)

---

## 📝 修改示例

### 修改前 (用户管理页面)

```vue
<template>
  <el-card>
    <el-table v-if="list.length > 0" :data="list">
      <!-- 表格列 -->
    </el-table>
    <div v-else class="empty-text">暂无数据</div>
  </el-card>
</template>

<script setup lang="ts">
const formRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' }
  ]
}
</script>
```

### 修改后 (用户管理页面)

```vue
<template>
  <el-card>
    <template v-if="loading">
      <el-skeleton :rows="5" animated />
    </template>
    
    <template v-else-if="list.length === 0">
      <EmptyState description="暂无用户数据">
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </EmptyState>
    </template>
    
    <template v-else>
      <el-table :data="list">
        <!-- 表格列 -->
      </el-table>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import { validatePhone, validateEmail } from '@/utils/validate'

const formRules = {
  phone: [
    { required: true, validator: validatePhone, trigger: 'blur' }
  ],
  email: [
    { validator: validateEmail, trigger: 'blur' }
  ]
}
</script>
```

---

## ✅ 验证步骤

### 1. 验证组件可用

```bash
cd /Users/work/apex-admin-web

# 启动开发服务器
npm run dev

# 访问任意列表页面
# 清空数据,应该看到EmptyState组件展示
```

### 2. 验证校验规则

```bash
# 访问任意表单页面
# 输入错误格式的手机号/邮箱
# 应该看到对应的校验错误提示
```

### 3. 前端构建验证

```bash
cd /Users/work/apex-admin-web

# 构建项目
npm run build

# 应该无错误,无警告
```

---

## 🎉 完成检查清单

- [x] EmptyState.vue 复制成功
- [x] validate.ts 复制成功
- [ ] 在各列表页面中使用EmptyState组件
- [ ] 在各表单页面中使用validate校验规则
- [ ] 测试组件展示效果
- [ ] 测试校验规则生效
- [ ] 前端构建无错误

---

## 📚 相关文档

- [前端开发规范](../frontend-development-standards.md)
- [Element Plus表单文档](https://element-plus.org/zh-CN/component/form.html)
- [Vue 3 Composition API](https://cn.vuejs.org/guide/extras/composition-api-faq.html)

---

**文档创建**: 2026-09-07  
**组件版本**: v1.0.0  
**最后更新**: 2026-09-07  
**状态**: ✅ 组件已就绪,可立即使用
