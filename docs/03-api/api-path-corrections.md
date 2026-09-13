# API 路径与参数修正说明

> **文档版本**: v1.0  
> **创建时间**: 2026-09-10  
> **适用版本**: apex-scaffold v1.2.0+  
> **重要性**: 🔴 高 (影响前后端集成)

---

## 一、概述

本文档基于 [实现一致性修正报告](../../.trae/documents/implementation-consistency-fix-report.md) 的发现,说明实际 API 路径与参数,纠正历史文档中的错误描述。

### 1.1 核心变更

| API 类别 | 历史错误路径 | ✅ 实际路径 | 影响 |
|----------|-------------|-----------|------|
| **认证登录** | `/api/auth/login` | `/api/v1/auth/login` | 🔴 高 |
| **文件上传** | `/api/storage/upload` | `/api/v1/files/generic` | 🔴 高 |
| **工作流** | 多个未确认端点 | 仅5个已实现端点 | 🔴 高 |

---

## 二、认证登录 API

### 2.1 统一登录入口

#### 📌 基本信息
- **路径**: `POST /api/v1/auth/login`
- **Content-Type**: `application/json`
- **认证**: 无需 (公开接口)

#### 📋 请求参数

```typescript
interface LoginRequest {
  // 登录方式 (必填)
  loginType: 'WX_CODE' | 'WX_PHONE' | 'WX_SMS' | 'CL_PHONE' | 'CL_SMS' | 'PASSWORD';
  
  // 微信授权码登录 (loginType=WX_CODE 时必填)
  code?: string;
  
  // 手机号 (loginType=WX_SMS/CL_SMS 时必填)
  phone?: string;
  
  // 短信验证码 (loginType=WX_SMS/CL_SMS 时必填)
  smsCode?: string;
  
  // 微信手机号 Code (loginType=WX_PHONE 时必填)
  phoneCode?: string;
  
  // 创蓝 AccessToken (loginType=CL_PHONE 时必填)
  accessToken?: string;
  
  // 用户名 (loginType=PASSWORD 时必填)
  username?: string;
  
  // 密码 (loginType=PASSWORD 时必填)
  password?: string;
  
  // 图形验证码 ID (启用登录保护时可能需要)
  captchaId?: string;
  
  // 图形验证码内容 (启用登录保护时可能需要)
  captchaCode?: string;
  
  // 设备 ID (推荐传递,用于多设备管理)
  deviceId?: string;
  
  // 客户端 IP (由服务端自动提取,无需传递)
  clientIp?: string;
}
```

#### 📤 响应格式

**成功响应** (200 OK):
```json
{
  "code": 0,
  "msg": "操作成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "openId": "wx_openid_123456",
    "unionId": "wx_unionid_789012",
    "userInfo": {
      "id": 1001,
      "username": "user123",
      "nickname": "用户昵称",
      "avatar": "https://example.com/avatar.jpg",
      "phone": "138****8000",
      "gender": 1,
      "status": "ACTIVE"
    },
    "isNewUser": false,
    "nextAction": "LOGIN_SUCCESS"
  },
  "traceId": "trace-abc-123"
}
```

**nextAction 字段说明**:
- `LOGIN_SUCCESS`: 登录成功,直接进入应用
- `NEED_REGISTER`: 需要补充注册信息
- `NEED_BIND_PHONE`: 需要绑定手机号

**错误响应示例**:
```json
{
  "code": 1002,
  "msg": "验证码错误",
  "traceId": "trace-def-456"
}
```

#### 🔐 错误码

| code | msg | 说明 | 前端处理 |
|------|-----|------|----------|
| 1001 | 用户名或密码错误 | 凭证无效 | 提示用户重新输入 |
| 1002 | 验证码错误 | 图形验证码错误 | 刷新验证码,重新输入 |
| 1003 | 验证码已过期 | 验证码 TTL 超时 | 刷新验证码 |
| 1009 | 登录失败次数过多 | 触发暴力破解防护 | 提示用户稍后再试或联系客服 |
| 1011 | 短信验证码错误 | SMS 验证码无效 | 提示用户重新获取 |
| 1012 | 短信验证码已过期 | SMS 验证码超时 | 提示用户重新获取 |

#### 💡 前端集成示例

```typescript
// Vue 3 + TypeScript 示例
import { ref } from 'vue';
import { apiClient } from '@/utils/request';

const login = async (loginType: string, credentials: any) => {
  const response = await apiClient.post('/api/v1/auth/login', {
    loginType,
    deviceId: getDeviceId(), // 从本地存储或生成
    ...credentials
  });
  
  if (response.data.code === 0) {
    const { accessToken, refreshToken, nextAction } = response.data.data;
    
    // 存储 Token
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    
    // 根据 nextAction 跳转
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
  } else {
    // 处理错误
    if (response.data.code === 1002) {
      // 刷新验证码
      refreshCaptcha();
    }
    ElMessage.error(response.data.msg);
  }
};

// 微信授权码登录
const wxCodeLogin = (code: string) => {
  return login('WX_CODE', { code });
};

// 短信验证码登录
const smsLogin = (phone: string, smsCode: string) => {
  return login('WX_SMS', { phone, smsCode });
};

// 账号密码登录
const passwordLogin = (username: string, password: string, captcha?: any) => {
  return login('PASSWORD', {
    username,
    password,
    captchaId: captcha?.id,
    captchaCode: captcha?.code
  });
};
```

### 2.2 其他认证 API

| 路径 | 方法 | 说明 |
|------|------|------|
| `/api/v1/auth/refresh` | POST | 刷新 Access Token |
| `/api/v1/auth/logout` | POST | 退出登录 |
| `/api/v1/auth/sms/code` | POST | 发送短信验证码 |
| `/api/v1/auth/roles/my` | GET | 获取当前用户角色 |
| `/api/v1/auth/permissions/my` | GET | 获取当前用户权限 |

---

## 三、文件上传 API

### 3.1 推荐上传接口

#### 📌 基本信息
- **路径**: `POST /api/v1/files/generic`
- **Content-Type**: `multipart/form-data`
- **认证**: 需要 (Bearer Token)

#### 📋 请求参数

**Form Data 字段**:
- `file` (必填): 文件内容,类型 `File`
- `sex` (必填): 性别标识,取值 `1` (男) 或 `2` (女),用于目录路由

**Headers**:
```http
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary...
```

#### ⚠️ 限制条件

| 限制项 | 值 | 说明 |
|--------|---|------|
| **文件大小** | 10 MiB | 超过返回 400 FILE_SIZE_EXCEEDED |
| **支持格式** | jpg, png, gif, bmp, webp, heic, heif | 其他格式返回 400 |
| **Content-Type** | image/* | 必须是图片类型 |
| **魔数校验** | 强制 | 防止文件类型伪造 |
| **内容审核** | 百度云 + FGCLIP | 色情/暴恐/政治敏感检测 |

#### 📤 响应格式

**成功响应** (200 OK):
```json
{
  "code": 0,
  "msg": "操作成功",
  "data": {
    "bucket": "apex",
    "objectName": "uploads/2026/09/10/1/abc123def456.jpg",
    "url": "https://storage.example.com/apex/uploads/2026/09/10/1/abc123def456.jpg?X-Amz-Algorithm=...",
    "expiresIn": 300,
    "auditResult": {
      "baidu": "PASS",
      "fgclip": "PASS"
    }
  },
  "traceId": "trace-upload-789"
}
```

**字段说明**:
- `bucket`: 存储桶名称 (由服务端配置决定)
- `objectName`: 对象存储路径
- `url`: 预签名 URL,有效期 300 秒
- `expiresIn`: URL 过期时间 (秒)
- `auditResult`: 审核结果 (PASS/REJECT)

**错误响应示例**:
```json
{
  "code": 4001,
  "msg": "文件大小超过限制",
  "traceId": "trace-upload-error"
}
```

#### 🔐 错误码

| code | msg | 说明 | 前端处理 |
|------|-----|------|----------|
| 4001 | 文件大小超过限制 | 文件 > 10 MiB | 提示用户压缩或选择更小文件 |
| 4002 | 不支持的文件类型 | 扩展名不在白名单 | 提示用户选择图片文件 |
| 4003 | 文件魔数校验失败 | 文件头不匹配 | 提示文件损坏或类型错误 |
| 4004 | 内容审核未通过 | 百度云或 FGCLIP 拒绝 | 提示用户文件包含违规内容 |
| 5031 | 审核服务暂时不可用 | 审核服务异常 | 提示用户稍后重试 |

#### 💡 前端集成示例

```typescript
// Vue 3 + Element Plus 示例
import { ElMessage, ElLoading } from 'element-plus';
import { apiClient } from '@/utils/request';

const uploadFile = async (file: File, sex: 1 | 2) => {
  // 1. 文件大小校验
  const maxSize = 10 * 1024 * 1024; // 10 MiB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 10MB');
    return;
  }
  
  // 2. 文件类型校验
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'];
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('只支持上传图片文件');
    return;
  }
  
  // 3. 构建 FormData
  const formData = new FormData();
  formData.append('file', file);
  formData.append('sex', sex.toString());
  
  // 4. 显示加载
  const loading = ElLoading.service({
    lock: true,
    text: '上传中...',
    background: 'rgba(0, 0, 0, 0.7)',
  });
  
  try {
    // 5. 发送请求
    const response = await apiClient.post('/api/v1/files/generic', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: 60000, // 60 秒超时
    });
    
    loading.close();
    
    if (response.data.code === 0) {
      const { url, expiresIn } = response.data.data;
      ElMessage.success('上传成功');
      
      // 6. 使用预签名 URL
      return url;
    } else {
      ElMessage.error(response.data.msg || '上传失败');
    }
  } catch (error: any) {
    loading.close();
    
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('上传超时,请检查网络');
    } else if (error.response?.status === 503) {
      ElMessage.error('审核服务暂时不可用,请稍后重试');
    } else {
      ElMessage.error('上传失败:' + (error.message || '未知错误'));
    }
  }
};

// Element Plus Upload 组件集成
const handleUploadSuccess = (response: any) => {
  if (response.code === 0) {
    form.avatar = response.data.url;
  }
};

const handleUploadError = (error: any) => {
  const response = JSON.parse(error.message);
  ElMessage.error(response.msg || '上传失败');
};

const beforeUpload = (file: File) => {
  const isImage = file.type.startsWith('image/');
  const isLt10M = file.size / 1024 / 1024 < 10;
  
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

### 3.2 旧版上传接口 (已废弃)

#### 📌 基本信息
- **路径**: `POST /api/v1/file/upload`
- **状态**: ⚠️ `@Deprecated(since = "2.2.0", forRemoval = true)`
- **建议**: 使用 `/api/v1/files/generic` 替代

**历史兼容性**: 该接口仍可用但已标记为废弃,未来版本将移除。

---

## 四、工作流 API

### 4.1 已确认端点

#### ✅ 任务回退
- **路径**: `POST /api/workflow/tasks/{taskId}/rollback`
- **请求体**:
```json
{
  "targetNodeCode": "node_approve_manager",
  "reason": "信息填写有误,需要重新审批"
}
```

#### ✅ 任务转办
- **路径**: `POST /api/workflow/tasks/{taskId}/transfer`
- **请求体**:
```json
{
  "newAssigneeId": 2001,
  "reason": "转交给张三处理"
}
```

#### ✅ 任务委派
- **路径**: `POST /api/workflow/tasks/{taskId}/delegate`
- **请求体**:
```json
{
  "delegateToId": 3001,
  "reason": "暂时委派给李四代理审批"
}
```

#### ✅ 流程挂起
- **路径**: `POST /api/workflow/instances/{instanceId}/suspend`
- **请求体**:
```json
{
  "reason": "等待额外材料补充"
}
```

#### ✅ 流程恢复
- **路径**: `POST /api/workflow/instances/{instanceId}/resume`
- **请求体**:
```json
{
  "reason": "材料已补充完整,恢复审批"
}
```

### 4.2 未确认端点 (⚠️ 请勿调用)

以下端点**尚未在 `WorkflowRouter` 中确认**,前端请勿调用:

| 路径 | 方法 | 说明 | 状态 |
|------|------|------|------|
| `/api/workflow/instances` | POST | 创建流程实例 | ⚠️ 未确认 |
| `/api/workflow/tasks/pending` | GET | 查询待办任务 | ⚠️ 未确认 |
| `/api/workflow/tasks/{taskId}/complete` | POST | 完成任务 | ⚠️ 未确认 |
| `/api/workflow/instances/{instanceId}` | GET | 查询实例详情 | ⚠️ 未确认 |

**重要提示**: 如需使用以上功能,请与后端团队确认实现进度后再集成。

---

## 五、用户信息 API

| 路径 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/api/v1/user/info` | GET | 获取当前用户信息 | 需要 |
| `/api/v1/user/{userId}` | GET | 根据 ID 获取用户 | 需要 |
| `/api/v1/user/list` | POST | 分页查询用户列表 | 需要 |
| `/api/v1/user/me` | DELETE | 注销当前账号 | 需要 |

---

## 六、管理后台 API

### 6.1 路径前缀

所有管理后台 API 使用前缀: `/admin-api/v1/**`

### 6.2 权限控制

管理后台接口必须满足:
1. 携带有效的 Bearer Token
2. 用户具有对应的权限点 (通过 `@RequirePermission` 注解校验)
3. 数据权限过滤 (通过 `@DataScope` 注解)

### 6.3 常用管理端点

| 路径 | 方法 | 权限点 | 说明 |
|------|------|--------|------|
| `/admin-api/v1/users` | GET | `system:user:query` | 查询用户 |
| `/admin-api/v1/users` | POST | `system:user:add` | 新增用户 |
| `/admin-api/v1/users/{id}` | PUT | `system:user:edit` | 编辑用户 |
| `/admin-api/v1/users/{id}` | DELETE | `system:user:remove` | 删除用户 |
| `/admin-api/v1/roles` | GET | `system:role:query` | 查询角色 |
| `/admin-api/v1/menus` | GET | `system:menu:query` | 查询菜单 |
| `/admin-api/v1/workflows/instances` | GET | `workflow:instance:query` | 查询工作流实例 |
| `/admin-api/v1/workflows/statistics` | GET | `workflow:statistics:query` | 工作流统计 |

---

## 七、前端集成 Checklist

### 7.1 路径修正清单

- [ ] 登录接口从 `/api/auth/login` 改为 `/api/v1/auth/login`
- [ ] 文件上传从 `/api/storage/upload` 改为 `/api/v1/files/generic`
- [ ] 移除对未确认工作流端点的调用
- [ ] 确认所有 API 调用使用正确的前缀 (`/api/v1/` 或 `/admin-api/v1/`)

### 7.2 参数修正清单

- [ ] 登录请求增加 `loginType` 字段
- [ ] 文件上传增加 `sex` 参数 (1 或 2)
- [ ] 处理登录响应的 `nextAction` 字段
- [ ] 预签名 URL 有效期只有 300 秒,需及时使用

### 7.3 错误处理清单

- [ ] 统一错误码处理 (参考各 API 错误码表)
- [ ] 401 自动跳转登录页
- [ ] 403 提示权限不足
- [ ] 503 审核服务不可用时的友好提示

---

## 八、参考资料

### 8.1 内部文档
- [实现一致性修正报告](../../.trae/documents/implementation-consistency-fix-report.md)
- [四张时序图](../diagrams/sequence/)
- [环境变量管理指南](../_archive/operations/environment-variables.md)

### 8.2 OpenAPI 文档
- Swagger UI: `http://localhost:8080/swagger-ui.html` (开发环境)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

**最后更新**: 2026-09-10  
**维护团队**: 后端团队 + 前端团队  
**问题反馈**: 请在项目 Issue 中标记 `api-integration` 标签
