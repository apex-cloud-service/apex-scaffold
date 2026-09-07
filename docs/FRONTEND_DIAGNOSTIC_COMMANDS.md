# 前端 404 问题诊断脚本

请在浏览器控制台（F12 → Console）中执行以下命令，并将结果告诉我：

## 1. 检查路由状态

```javascript
// 查看所有注册的路由
console.log("=== 所有路由 ===")
$router.getRoutes().forEach(r => {
  console.log(r.path, r.name)
})

// 查看当前路由
console.log("\n=== 当前路由 ===")
console.log("路径:", $router.currentRoute.value.path)
console.log("名称:", $router.currentRoute.value.name)
console.log("完整路由:", $router.currentRoute.value)
```

## 2. 检查用户状态

```javascript
// 查看用户 store 状态
const userStore = useUserStore()
console.log("\n=== 用户状态 ===")
console.log("Token:", !!userStore.token)
console.log("已初始化:", userStore.initialized)
console.log("权限数量:", userStore.permissions.length)
console.log("身份信息:", userStore.identity)
```

## 3. 检查 LocalStorage

```javascript
console.log("\n=== LocalStorage ===")
Object.keys(localStorage).forEach(key => {
  const value = localStorage.getItem(key)
  console.log(key + ":", value ? (value.substring(0, 50) + '...') : 'null')
})
```

## 4. 测试菜单接口

```javascript
console.log("\n=== 测试菜单接口 ===")
fetch('http://localhost:8080/admin-api/v1/menus', {
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('access_token')
  }
})
.then(r => r.json())
.then(data => {
  console.log("菜单接口返回:", data)
  console.log("code:", data.code)
  console.log("菜单数量:", data.data?.length)
})
.catch(err => console.error("接口错误:", err))
```

## 5. 检查网络请求

打开 Network 标签，刷新页面，然后：

1. 查找 `/admin-api/v1/menus` 请求
2. 查看 Status Code（状态码）
3. 查看 Response（响应内容）
4. 截图或复制响应内容

## 6. 检查控制台错误

在 Console 标签中查找：
- 红色错误信息
- 路由相关警告
- 组件加载失败信息

---

## 或者，请直接告诉我：

1. **访问什么 URL 时出现 404？**
   - 例如: http://localhost:5175/system/user
   - 还是所有页面都 404？

2. **是否能成功登录？**
   - 登录后跳转到哪个页面？

3. **侧边栏菜单是否显示？**
   - 如果显示，点击菜单后发生什么？

4. **浏览器控制台有什么红色错误？**
   - 完整复制错误信息

请提供以上任意信息，我会继续帮您诊断！
