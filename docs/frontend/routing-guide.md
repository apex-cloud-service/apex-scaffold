# 工作流监控功能路由配置与服务启动指南

## ✅ 已完成的配置

### 1. 前端路由配置
已在 `/Users/work/apex-admin-web/src/router/dynamic.ts` 中添加工作流监控路由：

```typescript
'workflow/monitor/index': () => import('@/views/workflow/monitor/index.vue'),
```

### 2. 后端服务
正在启动中...

---

## 📋 菜单配置 SQL

请在后端启动后，执行以下 SQL 添加菜单配置：

```sql
-- 1. 工作流目录（一级菜单）
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, component, perms, icon, sort, visible, status, create_time)
VALUES ('工作流', 0, 0, '/workflow', NULL, NULL, 'workflow', 100, 1, 1, NOW());

SET @workflow_parent_id = LAST_INSERT_ID();

-- 2. 流程监控菜单（二级菜单）
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, component, perms, icon, sort, visible, status, create_time)
VALUES ('流程监控', @workflow_parent_id, 1, 'monitor', 'workflow/monitor/index', 'workflow:monitor:view', 'data-line', 1, 1, 1, NOW());

SET @monitor_menu_id = LAST_INSERT_ID();

-- 3. 统计查看按钮权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, component, perms, icon, sort, visible, status, create_time)
VALUES ('统计查看', @monitor_menu_id, 2, '', '', 'workflow:statistics:view', '', 1, 1, 1, NOW());

-- 4. 实例查看按钮权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, component, perms, icon, sort, visible, status, create_time)
VALUES ('实例查看', @monitor_menu_id, 2, '', '', 'workflow:instance:view', '', 2, 1, 1, NOW());

-- 5. 任务查看按钮权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, component, perms, icon, sort, visible, status, create_time)
VALUES ('任务查看', @monitor_menu_id, 2, '', '', 'workflow:task:view', '', 3, 1, 1, NOW());

-- 6. 为管理员角色分配权限（假设管理员角色ID为1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE perms LIKE 'workflow:%';
```

**SQL 文件位置**: `/tmp/workflow_menu.sql`

---

## 🚀 服务启动步骤

### 步骤 1: 后端服务启动（进行中）
```bash
cd /Users/work/apex-scaffold
mvnd spring-boot:run -pl apex-interfaces
```

**当前状态**: 🟡 正在启动...  
**预期**: 看到 "Started" 和端口 8080 的日志

### 步骤 2: 执行菜单配置 SQL
等待后端启动完成后，连接数据库执行上述 SQL。

### 步骤 3: 启动前端服务
```bash
cd /Users/work/apex-admin-web
npm run dev
```

### 步骤 4: 访问测试
浏览器访问：`http://localhost:5173/workflow/monitor`

---

## 🔍 验证检查清单

### 后端验证
- [ ] 后端服务启动成功（看到 "Started" 日志）
- [ ] 端口 8080 可访问
- [ ] Swagger 文档可访问：http://localhost:8080/doc.html
- [ ] 工作流 API 接口存在（/admin-api/v1/workflows/...）

### 数据库验证
- [ ] 菜单配置 SQL 执行成功
- [ ] sys_menu 表中有工作流相关记录
- [ ] sys_role_menu 表中有权限分配记录

### 前端验证
- [ ] 前端服务启动成功
- [ ] 路由配置正确（dynamic.ts 中有 workflow/monitor/index）
- [ ] 登录后左侧菜单显示"工作流"
- [ ] 点击"流程监控"可正常访问

### 功能验证
- [ ] 统计看板正常显示
- [ ] 图表正常渲染
- [ ] 流程实例列表可加载
- [ ] API 调用成功，无 403/404 错误

---

## ⚠️ 常见问题

### 问题 1: 菜单不显示
**原因**: 菜单配置未添加或权限未分配  
**解决**: 执行菜单配置 SQL，重新登录

### 问题 2: API 返回 403
**原因**: 权限不足  
**解决**: 确认用户角色已分配 workflow:* 权限

### 问题 3: 页面 404
**原因**: 路由配置错误  
**解决**: 检查 dynamic.ts 中路由映射是否正确

### 问题 4: 后端启动失败
**原因**: 端口占用或依赖问题  
**解决**: 
```bash
# 检查端口占用
lsof -i :8080
# 如有占用，杀掉进程
kill -9 <PID>
```

---

## 📞 下一步操作

1. ⏳ **等待后端启动完成**（当前进行中）
2. 📝 **执行菜单配置 SQL**
3. 🚀 **启动前端服务**
4. ✅ **访问测试验证**

---

**配置时间**: 2026-09-02 21:35  
**状态**: 路由已配置，后端启动中
