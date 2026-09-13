# Postman 测试集合使用指南

## 📦 文件说明

本目录包含以下文件：

- `Apex-Scaffold-API.postman_collection.json` - API测试集合
- `Development.postman_environment.json` - 开发环境配置
- `Test.postman_environment.json` - 测试环境配置

## 🚀 快速开始

### 1. 导入集合

1. 打开Postman
2. 点击左上角 `Import` 按钮
3. 选择 `Apex-Scaffold-API.postman_collection.json` 文件
4. 点击 `Import` 完成导入

### 2. 导入环境

1. 点击右上角的环境下拉菜单
2. 点击 `Import` 
3. 选择 `Development.postman_environment.json` 和 `Test.postman_environment.json`
4. 导入后选择 `Apex Scaffold - Development` 环境

### 3. 配置环境变量

导入后，您可以根据实际情况修改环境变量：

**开发环境 (Development)**
- `baseUrl`: `http://localhost:8080` (本地开发服务器地址)
- `username`: `admin` (测试用户名)
- `password`: `admin123` (测试密码)

**测试环境 (Test)**
- `baseUrl`: `http://test-api.example.com` (测试服务器地址)
- `username`: `testuser`
- `password`: `testpass123`

### 4. 执行测试

#### 方式1：单个接口测试

1. 展开左侧的集合树
2. 选择要测试的接口
3. 点击 `Send` 按钮
4. 查看响应结果和测试结果

#### 方式2：批量执行测试

1. 右键点击集合或文件夹
2. 选择 `Run collection` 或 `Run folder`
3. 在Collection Runner中配置执行参数
4. 点击 `Run` 开始批量测试

## 📋 集合结构

```
Apex Scaffold API
├── 1. 认证管理
│   ├── 用户登录 ⭐
│   ├── 刷新Token
│   ├── 退出登录
│   ├── 用户注册
│   └── 发送短信验证码
├── 2. 用户管理
│   ├── 获取当前用户信息 ⭐
│   ├── 根据ID获取用户信息
│   └── 分页查询用户列表
├── 3. 授权管理
│   ├── 获取当前用户角色
│   ├── 获取当前用户权限
│   ├── 获取所有角色
│   ├── 创建角色
│   └── 删除角色
├── 4. 菜单管理
│   ├── 查询菜单树
│   ├── 查询菜单详情
│   ├── 创建菜单
│   ├── 更新菜单
│   └── 删除菜单
├── 5. 字典管理
│   ├── 查询字典类型列表
│   ├── 创建字典类型
│   ├── 查询字典数据
│   └── 创建字典数据
├── 6. 文件管理
│   ├── 上传文件
│   ├── 获取文件URL
│   ├── 检查文件存在
│   └── 删除文件
└── 7. 健康检查
    ├── 健康检查
    └── 获取系统信息
```

**⭐ 标记** 表示首次使用必须执行的接口

## 🔐 认证流程

### 自动Token管理

集合已配置自动Token管理，您只需：

1. **首次使用**：执行 `1. 认证管理 > 用户登录` 接口
2. **自动保存**：登录成功后，Token会自动保存到环境变量
3. **自动使用**：后续所有需要认证的接口会自动带上Token
4. **Token刷新**：Token过期后执行 `刷新Token` 接口
5. **退出登录**：执行 `退出登录` 接口会自动清除Token

### Token验证机制

```javascript
// 登录接口的Test脚本会自动执行：
if (jsonData.code === 200 && jsonData.data && jsonData.data.accessToken) {
    pm.environment.set("accessToken", jsonData.data.accessToken);
    pm.environment.set("refreshToken", jsonData.data.refreshToken);
    console.log("Token已保存到环境变量");
}
```

### 手动管理Token

如果需要手动管理Token：

1. 点击右上角的环境按钮
2. 找到 `accessToken` 和 `refreshToken` 变量
3. 手动输入Token值
4. 保存环境变量

## 🧪 测试脚本说明

### 全局测试脚本

每个请求都会自动执行以下测试：

```javascript
// 1. 验证响应时间
pm.test("响应时间应该小于2秒", function () {
    pm.expect(pm.response.responseTime).to.be.below(2000);
});

// 2. 验证响应格式
pm.test("响应应该是JSON格式", function () {
    pm.response.to.be.json;
});
```

### 接口级测试脚本

部分关键接口包含特定的测试脚本，例如：

**用户登录接口**：
- 验证状态码为200
- 验证响应包含code、data字段
- 验证业务状态码为200
- 验证Token存在
- 自动保存Token到环境变量

**获取当前用户信息接口**：
- 验证状态码为200
- 验证业务状态码为200
- 验证响应包含用户ID
- 验证响应包含用户名

### 自定义测试脚本

您可以为任何接口添加自定义测试脚本：

1. 选择接口
2. 切换到 `Tests` 标签
3. 编写JavaScript测试代码
4. 使用Postman提供的断言方法

**常用断言示例**：

```javascript
// 验证状态码
pm.test("状态码为200", function () {
    pm.response.to.have.status(200);
});

// 验证响应体
pm.test("包含特定字段", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('code');
    pm.expect(jsonData.code).to.eql(200);
});

// 验证响应时间
pm.test("响应时间小于500ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(500);
});

// 验证数据类型
pm.test("ID应该是数字", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data.id).to.be.a('number');
});

// 验证数组
pm.test("返回的是数组且有数据", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data).to.be.an('array');
    pm.expect(jsonData.data.length).to.be.above(0);
});
```

## 📊 Collection Runner

### 运行整个集合

1. 点击集合右侧的 `...` 按钮
2. 选择 `Run collection`
3. 配置运行参数：
   - **Iterations**: 运行次数（默认1次）
   - **Delay**: 请求间隔时间（毫秒）
   - **Data File**: 数据驱动测试的CSV/JSON文件
4. 点击 `Run` 开始执行

### 运行特定文件夹

如果只想测试某个模块：

1. 右键点击文件夹（如"认证管理"）
2. 选择 `Run folder`
3. 配置并运行

### 查看测试报告

运行完成后，Collection Runner会显示：
- ✅ 通过的测试数量
- ❌ 失败的测试数量
- 📊 每个请求的详细结果
- ⏱️ 总执行时间和平均响应时间

### 导出测试报告

1. 点击 `Export Results` 按钮
2. 选择导出格式（JSON）
3. 保存报告文件

## 🔄 数据驱动测试

### 准备数据文件

创建CSV文件 `test-users.csv`：

```csv
username,password,expectedCode
admin,admin123,200
testuser,testpass,200
wronguser,wrongpass,401
```

### 使用数据文件

1. 在Collection Runner中点击 `Select File`
2. 选择CSV文件
3. 在请求中使用变量：`{{username}}`, `{{password}}`
4. 集合会对每行数据执行一次

### 在测试脚本中使用

```javascript
pm.test("登录结果符合预期", function () {
    var jsonData = pm.response.json();
    var expectedCode = pm.iterationData.get("expectedCode");
    pm.expect(jsonData.code).to.eql(parseInt(expectedCode));
});
```

## 🌐 环境变量

### 内置环境变量

| 变量名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| baseUrl | string | API基础URL | http://localhost:8080 |
| username | string | 测试用户名 | admin |
| password | secret | 测试密码 | admin123 |
| accessToken | secret | 访问令牌 | 自动管理 |
| refreshToken | secret | 刷新令牌 | 自动管理 |

### 添加自定义变量

1. 点击右上角的环境按钮
2. 选择当前环境
3. 点击 `Add` 添加新变量
4. 在请求中使用 `{{变量名}}`

### 变量作用域

Postman变量有以下作用域（优先级从高到低）：

1. **Data variables** - 数据文件中的变量
2. **Local variables** - 当前请求的临时变量
3. **Environment variables** - 环境变量（推荐使用）
4. **Collection variables** - 集合变量
5. **Global variables** - 全局变量

## 📝 常见问题

### Q1: 为什么接口返回401？

**A**: Token可能已过期或未设置。解决方法：
1. 重新执行 `用户登录` 接口
2. 检查环境变量中的 `accessToken` 是否有值
3. 确认选择了正确的环境

### Q2: 为什么接口返回403？

**A**: 当前用户没有权限。解决方法：
1. 确认测试账号具有所需权限
2. 联系管理员分配权限
3. 使用管理员账号测试

### Q3: 如何调试请求？

**A**: 使用Postman Console：
1. 点击底部的 `Console` 按钮
2. 查看完整的请求和响应信息
3. 在脚本中使用 `console.log()` 输出调试信息

### Q4: 如何处理文件上传？

**A**: 文件上传接口已配置：
1. 打开 `6. 文件管理 > 上传文件` 请求
2. 在 `Body` 中选择文件
3. 确保 `Content-Type` 为 `multipart/form-data`

### Q5: 如何并发测试？

**A**: 使用Collection Runner：
1. 设置 `Iterations` 为并发数
2. 设置 `Delay` 为0（无延迟）
3. 查看响应时间分布

### Q6: 如何导出Newman脚本？

**A**: Newman是Postman的命令行工具：
```bash
# 安装Newman
npm install -g newman

# 运行集合
newman run Apex-Scaffold-API.postman_collection.json \
  -e Development.postman_environment.json \
  --reporters cli,html \
  --reporter-html-export report.html
```

## 🔧 高级用法

### Pre-request脚本

在请求发送前执行的脚本：

```javascript
// 生成随机数据
pm.environment.set("randomEmail", 
  `test${Math.floor(Math.random() * 10000)}@example.com`);

// 设置时间戳
pm.environment.set("timestamp", new Date().getTime());

// 计算签名
var signature = CryptoJS.MD5(pm.environment.get("secretKey")).toString();
pm.environment.set("signature", signature);
```

### 动态变量

Postman支持内置的动态变量：

- `{{$guid}}` - 生成GUID
- `{{$timestamp}}` - 当前时间戳
- `{{$randomInt}}` - 随机整数
- `{{$randomUUID}}` - 随机UUID

### 链式请求

在Test脚本中设置下一个请求的参数：

```javascript
// 保存当前创建的ID
var jsonData = pm.response.json();
pm.environment.set("createdUserId", jsonData.data.id);

// 下一个请求可以使用 {{createdUserId}}
```

### 条件跳过

根据条件跳过某些测试：

```javascript
// 如果是生产环境，跳过删除操作
if (pm.environment.get("baseUrl").includes("production")) {
    postman.setNextRequest(null); // 停止执行
}
```

## 📖 参考资源

- [Postman官方文档](https://learning.postman.com/docs/getting-started/introduction/)
- [Postman测试脚本指南](https://learning.postman.com/docs/writing-scripts/test-scripts/)
- [Newman命令行工具](https://learning.postman.com/docs/running-collections/using-newman-cli/command-line-integration-with-newman/)
- [Swagger UI](http://localhost:8080/doc.html) - 在线API文档

## 🤝 贡献

如果您发现问题或有改进建议：

1. 在项目中提Issue
2. 提交Pull Request
3. 联系团队成员

## 📜 更新日志

### v1.0.0 (2026-09-07)
- ✅ 初始版本
- ✅ 包含核心业务接口
- ✅ 自动Token管理
- ✅ 基础测试脚本
- ✅ 开发和测试环境配置

---

**文档版本**: 1.0  
**最后更新**: 2026-09-07  
**维护团队**: Apex Scaffold Team
