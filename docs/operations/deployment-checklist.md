# 工作流监控功能部署检查清单

[查看部署检查流程图](../diagrams/49_deployment_checklist_flow.svg)

## ✅ 文件完整性检查

### 后端文件（8个）
- [x] WorkflowDefinitionDTO.java
- [x] WorkflowInstanceDTO.java
- [x] WorkflowInstanceDetailDTO.java
- [x] WorkflowTaskDTO.java
- [x] WorkflowStatisticsDTO.java
- [x] WorkflowTrendDTO.java
- [x] WorkflowQueryService.java
- [x] WorkflowStatisticsService.java

### 前端文件（6个）
- [x] src/types/workflow.ts
- [x] src/api/workflow.ts
- [x] src/views/workflow/monitor/index.vue
- [x] src/views/workflow/monitor/Statistics.vue
- [x] src/views/workflow/monitor/InstanceList.vue
- [x] src/views/workflow/monitor/InstanceDetail.vue

### 文档文件（5个）
- [x] docs/WORKFLOW_MONITOR_GUIDE.md
- [x] docs/WORKFLOW_DESIGNER_GUIDE.md
- [x] .trae/documents/工作流可视化设计器实现进度报告.md
- [x] .trae/documents/前端完整代码文件.md
- [x] .trae/documents/前端文件创建完成报告.md
- [x] .trae/documents/工作流监控功能完整实现报告.md

---

## 🚀 部署前检查清单

### 1. 后端部署检查

#### 编译检查
```bash
cd /Users/work/apex-scaffold
mvnd clean compile -DskipTests
```
✅ **预期结果**: BUILD SUCCESS

#### 启动检查
```bash
mvnd spring-boot:run -pl apex-interfaces
```
✅ **预期结果**: 
- 服务启动成功
- 端口 8080 监听正常
- 无错误日志

#### API 可用性检查
访问 Swagger 文档：`http://localhost:8080/doc.html`

检查以下接口是否存在：
- [x] GET /admin-api/v1/workflows/definitions/{id}/detail
- [x] GET /admin-api/v1/workflows/instances
- [x] GET /admin-api/v1/workflows/instances/{id}/detail
- [x] GET /admin-api/v1/workflows/tasks/my
- [x] GET /admin-api/v1/workflows/statistics
- [x] GET /admin-api/v1/workflows/statistics/trend

---

### 2. 前端部署检查

#### 依赖检查
```bash
cd /Users/work/apex-admin-web
npm list echarts
```
✅ **预期结果**: echarts@5.6.0

#### 编译检查
```bash
npm run build
```
✅ **预期结果**: Build completed successfully

#### 启动检查
```bash
npm run dev
```
✅ **预期结果**: 
- 服务启动成功
- 端口 5173 (或其他) 监听正常
- 无编译错误

---

### 3. 路由配置检查

在 `/Users/work/apex-admin-web/src/router/` 中添加路由配置：

```typescript
// 在 index.ts 或 dynamic.ts 中添加
{
  path: '/workflow',
  component: AdminLayout,
  meta: { title: '工作流', icon: 'workflow' },
  children: [
    {
      path: 'monitor',
      name: 'WorkflowMonitor',
      component: () => import('@/views/workflow/monitor/index.vue'),
      meta: { 
        title: '流程监控', 
        permission: 'workflow:monitor:view',
        icon: 'data-line'
      }
    },
    {
      path: 'instances/:id',
      name: 'WorkflowInstanceDetail',
      component: () => import('@/views/workflow/monitor/InstanceDetail.vue'),
      meta: { 
        title: '流程详情', 
        hidden: true,
        activeMenu: '/workflow/monitor'
      }
    }
  ]
}
```

- [ ] 路由配置已添加
- [ ] 路由可以正常访问

---

### 4. 权限配置检查

在系统菜单管理中添加以下权限：

```sql
-- 工作流监控菜单
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, perms, icon, sort, visible, status)
VALUES ('工作流监控', 0, 1, '/workflow/monitor', 'workflow:monitor:view', 'data-line', 100, 1, 1);

-- 统计查询权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, perms, icon, sort, visible, status)
VALUES ('统计查询', (SELECT id FROM sys_menu WHERE perms='workflow:monitor:view'), 2, '', 'workflow:statistics:view', '', 1, 1, 1);

-- 实例查询权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, perms, icon, sort, visible, status)
VALUES ('实例查询', (SELECT id FROM sys_menu WHERE perms='workflow:monitor:view'), 2, '', 'workflow:instance:view', '', 2, 1, 1);
```

- [ ] 权限配置已添加
- [ ] 角色已分配权限

---

### 5. 功能测试检查

#### 统计看板测试
访问：`http://localhost:5173/workflow/monitor`

- [ ] 页面正常加载
- [ ] 4个指标卡片显示正常
- [ ] 状态分布饼图渲染正常
- [ ] 趋势折线图渲染正常
- [ ] 可以切换7天/30天视图

#### 流程实例列表测试
切换到"流程实例" Tab

- [ ] 表格正常加载
- [ ] 分页功能正常
- [ ] 状态筛选功能正常
- [ ] "查看详情"按钮可点击

#### 流程实例详情测试
点击某个实例的"查看详情"

- [ ] 页面正常跳转
- [ ] 基本信息显示正常
- [ ] 任务列表显示正常
- [ ] 返回按钮正常工作

---

### 6. 数据准备检查

#### 测试数据准备（可选）
如果数据库中没有流程数据，可以插入测试数据：

```sql
-- 插入测试流程实例
INSERT INTO wf_process_instance (definition_id, process_code, title, status, initiator_id, business_key, start_time, create_time)
VALUES 
  (1, 'leave_approval', '张三的请假申请', 'RUNNING', 10, 'LEAVE-2026-001', NOW(), NOW()),
  (1, 'leave_approval', '李四的请假申请', 'APPROVED', 11, 'LEAVE-2026-002', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (1, 'leave_approval', '王五的请假申请', 'REJECTED', 12, 'LEAVE-2026-003', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 插入测试任务
INSERT INTO wf_task (process_instance_id, task_name, task_type, status, assignee_id, node_id, start_time, create_time)
VALUES
  (1, '部门主管审批', 'USER_TASK', 'PENDING', 20, 'node_2', NOW(), NOW()),
  (2, '部门主管审批', 'USER_TASK', 'APPROVED', 20, 'node_2', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));
```

- [ ] 测试数据已插入（如需要）

---

### 7. API 接口测试

使用 Postman 或 curl 测试以下接口：

#### 统计概览
```bash
curl -X GET "http://localhost:8080/admin-api/v1/workflows/statistics" \
  -H "Authorization: Bearer YOUR_TOKEN"
```
- [ ] 返回 200 状态码
- [ ] 数据格式正确

#### 趋势数据
```bash
curl -X GET "http://localhost:8080/admin-api/v1/workflows/statistics/trend?days=7" \
  -H "Authorization: Bearer YOUR_TOKEN"
```
- [ ] 返回 200 状态码
- [ ] 数据格式正确

#### 流程实例列表
```bash
curl -X GET "http://localhost:8080/admin-api/v1/workflows/instances?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer YOUR_TOKEN"
```
- [ ] 返回 200 状态码
- [ ] 分页数据正确

---

## 📋 部署步骤

### 步骤 1: 后端部署
```bash
cd /Users/work/apex-scaffold

# 编译
mvnd clean package -DskipTests

# 启动
mvnd spring-boot:run -pl apex-interfaces

# 或使用 jar 包启动
java -jar apex-interfaces/target/apex-interfaces-1.0.0.jar
```

### 步骤 2: 前端部署
```bash
cd /Users/work/apex-admin-web

# 开发环境
npm run dev

# 生产环境
npm run build
# 将 dist 目录部署到 Nginx 或其他 Web 服务器
```

### 步骤 3: 配置路由
在前端路由文件中添加工作流监控路由配置（参考上方"路由配置检查"）

### 步骤 4: 配置权限
在系统菜单管理中添加权限配置（参考上方"权限配置检查"）

### 步骤 5: 验证功能
按照"功能测试检查"章节逐项验证功能

---

## ⚠️ 注意事项

1. **数据库表结构**: 确保已执行工作流引擎的数据库迁移脚本
2. **License 验证**: 工作流监控功能需要 Enterprise License
3. **跨域配置**: 确保后端已配置 CORS，允许前端域名访问
4. **Token 认证**: 确保前端请求已正确携带 Authorization Token
5. **时区设置**: 确保前后端时区设置一致，避免时间显示错误

---

## 🐛 常见问题排查

### 问题 1: 页面空白
- 检查浏览器控制台是否有错误
- 检查路由是否正确配置
- 检查权限是否已分配

### 问题 2: API 调用失败
- 检查后端服务是否正常启动
- 检查 Token 是否有效
- 检查权限码是否正确

### 问题 3: 统计数据为 0
- 检查数据库中是否有流程数据
- 检查时间范围是否正确
- 检查 SQL 查询逻辑

### 问题 4: 图表不显示
- 检查 echarts 依赖是否已安装
- 检查浏览器控制台是否有错误
- 检查数据格式是否正确

---

## 📞 支持

如遇问题，请查看以下文档：

1. **使用指南**: `/Users/work/apex-scaffold/docs/WORKFLOW_MONITOR_GUIDE.md`
2. **完整实现报告**: `/Users/work/apex-scaffold/.trae/documents/工作流监控功能完整实现报告.md`
3. **API 文档**: `http://localhost:8080/doc.html`

---

**检查清单版本**: v1.0  
**最后更新**: 2026-09-02 21:30
