# Postman 快速入门指南

## ⚡ 5分钟快速开始

### 第1步：导入集合（30秒）

1. 打开Postman
2. 点击左上角 **Import** 按钮
3. 拖拽以下文件到导入窗口：
   - `Apex-Scaffold-API.postman_collection.json`
   - `Development.postman_environment.json`

### 第2步：选择环境（10秒）

1. 点击右上角的环境下拉菜单
2. 选择 **Apex Scaffold - Development**

### 第3步：登录获取Token（1分钟）

1. 展开 **1. 认证管理** 文件夹
2. 点击 **用户登录** 接口
3. 点击 **Send** 按钮
4. ✅ 成功后Token会自动保存

### 第4步：测试其他接口（3分钟）

1. 展开 **2. 用户管理** 文件夹
2. 点击 **获取当前用户信息** 接口
3. 点击 **Send** 按钮
4. ✅ 查看返回的用户信息

**🎉 完成！您已经成功测试了API接口！**

---

## 📚 核心功能

### 1️⃣ 自动Token管理

✅ **无需手动复制Token**
- 登录后自动保存
- 所有接口自动使用
- Token过期自动刷新

### 2️⃣ 智能测试脚本

✅ **自动验证响应**
- 状态码检查
- 响应格式验证
- 业务逻辑断言
- 响应时间监控

### 3️⃣ 批量测试

✅ **一键运行所有测试**
```bash
# 右键点击集合 → Run collection
# 或使用命令行：
./run-tests.sh -e dev -i 1
```

---

## 🎯 常用场景

### 场景1：测试登录流程

```
1. 用户登录 ✅
   ↓ Token自动保存
2. 获取当前用户信息 ✅
   ↓ 验证登录成功
3. 退出登录 ✅
   ↓ Token自动清除
```

### 场景2：测试CRUD操作

```
1. 创建角色 ✅
   ↓ 保存创建的ID
2. 查询角色详情 ✅
   ↓ 验证数据正确
3. 更新角色 ✅
   ↓ 修改数据
4. 删除角色 ✅
   ↓ 清理数据
```

### 场景3：测试文件上传

```
1. 选择文件 ✅
   ↓ 在Body中选择文件
2. 上传文件 ✅
   ↓ 获取文件URL
3. 获取文件URL ✅
   ↓ 验证文件可访问
4. 删除文件 ✅
   ↓ 清理文件
```

---

## 🚀 命令行测试

### Linux/Mac

```bash
# 使脚本可执行
chmod +x run-tests.sh

# 运行测试
./run-tests.sh

# 使用测试环境
./run-tests.sh -e test

# 运行10次，间隔1秒
./run-tests.sh -i 10 -d 1000
```

### Windows

```batch
REM 运行测试
run-tests.bat

REM 使用测试环境
run-tests.bat -e test

REM 运行10次，间隔1秒
run-tests.bat -i 10 -d 1000
```

### 查看测试报告

测试完成后，在 `reports/` 目录查看：
- `report_20260907_100000.html` - HTML报告
- `report_20260907_100000.json` - JSON报告

---

## 💡 实用技巧

### 技巧1：快捷键

| 快捷键 | 功能 |
|--------|------|
| `Ctrl/Cmd + Enter` | 发送请求 |
| `Ctrl/Cmd + S` | 保存请求 |
| `Ctrl/Cmd + /` | 搜索 |
| `Alt + N` | 新建请求 |

### 技巧2：变量引用

在请求中使用变量：
```
{{baseUrl}}/api/v1/user/{{userId}}
```

### 技巧3：Console调试

查看详细日志：
```javascript
// 在Tests脚本中
console.log("响应数据:", pm.response.json());
console.log("响应时间:", pm.response.responseTime, "ms");
```

### 技巧4：保存响应数据

```javascript
// 保存响应中的ID
var jsonData = pm.response.json();
pm.environment.set("userId", jsonData.data.id);

// 在下一个请求中使用 {{userId}}
```

---

## ❓ 常见问题

### Q: Token过期了怎么办？

**A**: 重新执行登录接口，Token会自动更新

### Q: 为什么接口返回403？

**A**: 当前账号没有权限，请：
1. 检查账号权限
2. 或使用管理员账号

### Q: 如何添加新接口？

**A**: 
1. 右键点击文件夹
2. 选择 **Add Request**
3. 配置接口信息
4. 添加测试脚本

### Q: 如何导出测试结果？

**A**: 
1. 运行Collection Runner
2. 点击 **Export Results**
3. 保存JSON文件

---

## 📖 完整文档

查看 [README.md](./README.md) 了解更多高级功能：

- 🔐 认证流程详解
- 🧪 自定义测试脚本
- 📊 Collection Runner使用
- 🔄 数据驱动测试
- 🌐 环境变量管理
- 🔧 高级用法

---

## 🆘 获取帮助

遇到问题？

1. 查看 [README.md](./README.md)
2. 查看 [API接口测试计划](../API接口测试计划.md)
3. 联系团队成员

---

**文档版本**: 1.0  
**最后更新**: 2026-09-07
