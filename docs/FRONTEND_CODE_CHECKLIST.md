# 前端代码核查清单

> **文档版本**: v1.0  
> **创建时间**: 2026-09-10  
> **检查范围**: apex-admin-web 前端项目  
> **优先级**: 🔴 P0 - 紧急

---

## 一、核查目标

基于后端实现一致性修正,核查前端代码中可能存在的错误 API 调用,确保前后端集成正常。

---

## 二、登录模块核查

### 2.1 文件位置 (推测)

```
src/api/auth.ts           # 认证 API
src/views/login/          # 登录页面
src/store/modules/user.ts # 用户状态管理
```

### 2.2 核查清单

#### ✅ API 路径检查

```bash
# 在前端项目根目录执行
cd /Users/work/apex-admin-web

# 搜索错误的登录路径
grep -r "/api/auth/login" src/
grep -r "'/api/auth/" src/

# 预期: 应该没有结果或只在注释/文档中出现
# 如有结果,需要改为 /api/v1/auth/login
```

**修正示例**:
```typescript
// ❌ 错误
const login = () => request.post('/api/auth/login', data);

// ✅ 正确
const login = () => request.post('/api/v1/auth/login', data);
```

#### ✅ 请求参数检查

搜索登录请求代码:
```bash
grep -A 10 "auth/login" src/api/*.ts
```

确认包含以下字段:
- ✅ `loginType`: 必填
- ✅ `deviceId`: 推荐
- ✅ 根据 `loginType` 传递对应凭证

**修正示例**:
```typescript
// ❌ 错误 - 缺少 loginType
interface LoginParams {
  username: string;
  password: string;
}

// ✅ 正确 - 包含 loginType
interface LoginParams {
  loginType: 'WX_CODE' | 'WX_PHONE' | 'WX_SMS' | 'CL_PHONE' | 'CL_SMS' | 'PASSWORD';
  code?: string;
  phone?: string;
  smsCode?: string;
  username?: string;
  password?: string;
  deviceId?: string;
}
```

#### ✅ 响应处理检查

搜索登录成功后的处理逻辑:
```bash
grep -A 20 "login.*success\|loginSuccess" src/
```

确认处理 `nextAction` 字段:
```typescript
// ✅ 正确处理
const { nextAction, userInfo } = response.data;

switch (nextAction) {
  case 'LOGIN_SUCCESS':
    router.push('/home');
    break;
  case 'NEED_REGISTER':
    router.push('/register');
    break;
  case 'NEED_BIND_PHONE':
    router.push('/bind-phone');
    break;
}
```

---

## 三、文件上传模块核查

### 3.1 文件位置 (推测)

```
src/api/file.ts           # 文件 API
src/api/storage.ts        # 存储 API (如存在需要移除)
src/components/Upload/    # 上传组件
src/utils/upload.ts       # 上传工具
```

### 3.2 核查清单

#### ✅ API 路径检查

```bash
# 搜索错误的文件上传路径
grep -r "/api/storage" src/
grep -r "storage/upload" src/

# 预期: 应该没有结果
# 如有结果,需要全部改为 /api/v1/files/generic
```

**修正示例**:
```typescript
// ❌ 错误路径
const upload = (file) => request.post('/api/storage/upload', formData);

// ✅ 正确路径
const upload = (file, sex) => request.post('/api/v1/files/generic', formData);
```

#### ✅ 必填参数检查

搜索文件上传代码:
```bash
grep -A 10 "files/generic\|file/upload" src/api/*.ts
```

确认包含 `sex` 参数:
```typescript
// ❌ 错误 - 缺少 sex 参数
const uploadFile = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/api/v1/files/generic', formData);
};

// ✅ 正确 - 包含 sex 参数
const uploadFile = (file: File, sex: 1 | 2) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('sex', sex.toString());
  return request.post('/api/v1/files/generic', formData);
};
```

#### ✅ URL 处理检查

搜索上传响应处理:
```bash
grep -A 20 "upload.*success\|uploadSuccess" src/
```

确认正确处理预签名 URL:
```typescript
// ❌ 错误 - 直接保存 URL (5分钟后失效)
form.avatar = response.data.url;

// ✅ 正确 - 保存 objectName (永久有效)
form.avatar = response.data.objectName;
previewUrl.value = response.data.url; // 仅用于预览
```

#### ✅ 文件限制检查

```bash
grep -B 5 -A 15 "beforeUpload\|upload.*validate" src/
```

确认限制条件:
```typescript
const beforeUpload = (file: File) => {
  // ✅ 大小限制: 10 MiB
  const isLt10M = file.size / 1024 / 1024 < 10;
  
  // ✅ 类型限制: 图片格式
  const isImage = file.type.startsWith('image/');
  
  if (!isImage) {
    ElMessage.error('只能上传图片文件');
    return false;
  }
  if (!isLt10M) {
    ElMessage.error('图片大小不能超过 10MB');
    return false;
  }
  return true;
};
```

---

## 四、工作流模块核查

### 4.1 文件位置 (推测)

```
src/api/workflow.ts       # 工作流 API
src/views/workflow/       # 工作流页面
```

### 4.2 核查清单

#### ✅ 未实现端点检查

```bash
# 搜索可能存在的未实现端点
grep -r "workflow/instances.*POST\|POST.*workflow/instances" src/
grep -r "workflow/tasks/pending" src/
grep -r "workflow/tasks.*complete" src/
```

**修正操作**:
- 如搜索到以下调用,需要**立即移除**或**注释掉**:
  - `POST /api/workflow/instances` (创建流程实例)
  - `GET /api/workflow/tasks/pending` (查询待办)
  - `POST /api/workflow/tasks/{id}/complete` (完成任务)
  - `GET /api/workflow/instances/{id}` (实例详情)

**临时方案**:
```typescript
// ❌ 移除未实现的功能
// const createInstance = () => request.post('/api/workflow/instances', data);

// ✅ 暂时禁用或显示"开发中"提示
const createInstance = () => {
  ElMessage.warning('功能开发中,敬请期待');
  return Promise.reject(new Error('Not implemented'));
};
```

#### ✅ 已实现端点确认

确认只使用以下 5 个端点:
```typescript
// ✅ 这些可以调用
const rollbackTask = (taskId, data) => 
  request.post(`/api/workflow/tasks/${taskId}/rollback`, data);

const transferTask = (taskId, data) => 
  request.post(`/api/workflow/tasks/${taskId}/transfer`, data);

const delegateTask = (taskId, data) => 
  request.post(`/api/workflow/tasks/${taskId}/delegate`, data);

const suspendInstance = (instanceId, data) => 
  request.post(`/api/workflow/instances/${instanceId}/suspend`, data);

const resumeInstance = (instanceId, data) => 
  request.post(`/api/workflow/instances/${instanceId}/resume`, data);
```

---

## 五、通用配置核查

### 5.1 Axios 配置

```bash
# 检查 Axios 基础配置
cat src/utils/request.ts
```

确认以下配置:
```typescript
// ✅ 正确的 baseURL
axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;

// ✅ 统一错误处理
axios.interceptors.response.use(
  response => response.data,
  error => {
    const { status, data } = error.response || {};
    
    // 401 跳转登录
    if (status === 401) {
      localStorage.removeItem('accessToken');
      router.push('/login');
    }
    
    // 403 权限不足
    if (status === 403) {
      ElMessage.error('权限不足');
    }
    
    // 503 服务不可用
    if (status === 503) {
      ElMessage.error('服务暂时不可用,请稍后重试');
    }
    
    return Promise.reject(error);
  }
);
```

### 5.2 环境变量

```bash
# 检查环境变量配置
cat .env.development
cat .env.production
```

确认 API 地址:
```bash
# ✅ 开发环境
VITE_API_BASE_URL=http://localhost:8080

# ✅ 生产环境
VITE_API_BASE_URL=https://api.example.com
```

---

## 六、自动化检查脚本

### 6.1 创建检查脚本

保存为 `scripts/check-api-paths.sh`:

```bash
#!/bin/bash

echo "🔍 开始检查前端 API 路径..."
echo ""

# 检查错误的登录路径
echo "1. 检查登录路径..."
WRONG_LOGIN=$(grep -r "/api/auth/login" src/ --exclude-dir=node_modules | wc -l)
if [ "$WRONG_LOGIN" -gt 0 ]; then
  echo "❌ 发现 $WRONG_LOGIN 处错误的登录路径 /api/auth/login"
  grep -n "/api/auth/login" src/ --exclude-dir=node_modules
else
  echo "✅ 登录路径检查通过"
fi
echo ""

# 检查错误的文件上传路径
echo "2. 检查文件上传路径..."
WRONG_STORAGE=$(grep -r "/api/storage" src/ --exclude-dir=node_modules | wc -l)
if [ "$WRONG_STORAGE" -gt 0 ]; then
  echo "❌ 发现 $WRONG_STORAGE 处错误的存储路径 /api/storage"
  grep -n "/api/storage" src/ --exclude-dir=node_modules
else
  echo "✅ 文件上传路径检查通过"
fi
echo ""

# 检查未实现的工作流端点
echo "3. 检查工作流端点..."
WORKFLOW_CREATE=$(grep -r "POST.*workflow/instances\|workflow/instances.*POST" src/ --exclude-dir=node_modules | wc -l)
WORKFLOW_PENDING=$(grep -r "workflow/tasks/pending" src/ --exclude-dir=node_modules | wc -l)
WORKFLOW_COMPLETE=$(grep -r "workflow/tasks.*complete" src/ --exclude-dir=node_modules | wc -l)

if [ "$WORKFLOW_CREATE" -gt 0 ] || [ "$WORKFLOW_PENDING" -gt 0 ] || [ "$WORKFLOW_COMPLETE" -gt 0 ]; then
  echo "❌ 发现调用未实现的工作流端点"
  [ "$WORKFLOW_CREATE" -gt 0 ] && echo "  - 创建实例: $WORKFLOW_CREATE 处"
  [ "$WORKFLOW_PENDING" -gt 0 ] && echo "  - 查询待办: $WORKFLOW_PENDING 处"
  [ "$WORKFLOW_COMPLETE" -gt 0 ] && echo "  - 完成任务: $WORKFLOW_COMPLETE 处"
else
  echo "✅ 工作流端点检查通过"
fi
echo ""

# 总结
echo "==============================================="
TOTAL_ISSUES=$((WRONG_LOGIN + WRONG_STORAGE + WORKFLOW_CREATE + WORKFLOW_PENDING + WORKFLOW_COMPLETE))
if [ "$TOTAL_ISSUES" -eq 0 ]; then
  echo "✅ 所有检查通过!"
else
  echo "❌ 发现 $TOTAL_ISSUES 个问题,请修正"
  exit 1
fi
```

### 6.2 执行检查

```bash
# 进入前端项目目录
cd /Users/work/apex-admin-web

# 赋予执行权限
chmod +x scripts/check-api-paths.sh

# 执行检查
./scripts/check-api-paths.sh
```

---

## 七、核查报告模板

核查完成后,请填写以下报告:

```markdown
# 前端 API 核查报告

执行时间: 2026-09-XX
执行人: @前端负责人

## 核查结果

### 1. 登录模块
- [ ] 路径已修正为 /api/v1/auth/login
- [ ] 已添加 loginType 参数
- [ ] 已处理 nextAction 字段
- 问题数: X 个
- 修正文件: 
  - src/api/auth.ts (行号: XX)
  - src/views/login/index.vue (行号: XX)

### 2. 文件上传模块
- [ ] 路径已修正为 /api/v1/files/generic
- [ ] 已添加 sex 参数
- [ ] 已修正 URL 处理逻辑
- 问题数: X 个
- 修正文件:
  - src/api/file.ts (行号: XX)
  - src/components/Upload/index.vue (行号: XX)

### 3. 工作流模块
- [ ] 已移除未实现端点调用
- [ ] 已添加功能禁用提示
- 问题数: X 个
- 修正文件:
  - src/api/workflow.ts (行号: XX)

### 4. 通用配置
- [ ] Axios 配置正确
- [ ] 环境变量配置正确
- [ ] 错误处理完善

## 测试验证

- [ ] 登录功能测试通过
- [ ] 文件上传功能测试通过
- [ ] 工作流功能测试通过

## 遗留问题

(如有未解决的问题,请列出)

## 备注

(其他需要说明的事项)
```

---

## 八、后续跟进

### 8.1 持续集成

建议将检查脚本集成到 CI 流程:

```yaml
# .github/workflows/frontend-check.yml
name: Frontend API Check

on: [push, pull_request]

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Check API Paths
        run: |
          cd apex-admin-web
          ./scripts/check-api-paths.sh
```

### 8.2 文档同步

前端团队在修正代码后,请同步更新:
- 前端 API 对接文档
- 前端开发规范文档
- 团队 Wiki

---

**文档维护**: 后端团队 + 前端团队  
**最后更新**: 2026-09-10  
**检查周期**: 每次发布前必须执行
