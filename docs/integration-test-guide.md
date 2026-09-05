# 工作流接口联调测试文档

## 概述

本文档提供工作流 WebFlux Handler 接口的完整联调测试方案，包括测试脚本、测试用例和预期结果。

**后端接口基础路径**：`http://localhost:8080/api/workflow`

---

## 一、前置准备

### 1.1 启动后端服务

```bash
cd /Users/work/apex-scaffold
mvn spring-boot:run -pl apex-interfaces
```

### 1.2 启动前端服务

```bash
cd /Users/work/apex-admin-web
npm run dev
```

### 1.3 准备测试数据

确保数据库中存在：
- 工作流定义 (wf_definition)
- 流程实例 (wf_process_instance)
- 待办任务 (wf_task)

---

## 二、接口测试用例

### 2.1 流程回退接口

**请求**：
```bash
curl -X POST 'http://localhost:8080/api/workflow/tasks/1000/rollback' \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d '{
    "targetNodeCode": "manager_approval",
    "reason": "材料不完整，请补充后重新提交"
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

**错误场景**：
- 400：参数校验失败（targetNodeCode 或 reason 为空）
- 400：任务不存在或已完成
- 500：系统内部错误

---

### 2.2 任务转办接口

**请求**：
```bash
curl -X POST 'http://localhost:8080/api/workflow/tasks/1000/transfer' \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 10002' \
  -d '{
    "newAssigneeId": 10003,
    "reason": "本周出差，请帮忙代为审批"
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

**错误场景**：
- 400：只能转办待办任务
- 400：只能转办自己的任务
- 400：新审批人ID不能为空

---

### 2.3 任务委派接口

**请求**：
```bash
curl -X POST 'http://localhost:8080/api/workflow/tasks/1000/delegate' \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 10002' \
  -d '{
    "delegateToId": 10004,
    "reason": "请先进行初审，审核通过后我再终审"
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

**错误场景**：
- 400：只能委派待办任务
- 400：只能委派自己的任务
- 400：受托人ID不能为空

---

### 2.4 挂起流程实例接口

**请求**：
```bash
curl -X POST 'http://localhost:8080/api/workflow/instances/100/suspend' \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d '{
    "reason": "发现业务数据异常，暂停审批，待修复后恢复"
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

**错误场景**：
- 400：已完成或已驳回的流程无法挂起
- 400：流程已处于挂起状态
- 400：挂起原因不能为空

---

### 2.5 恢复流程实例接口

**请求**：
```bash
curl -X POST 'http://localhost:8080/api/workflow/instances/100/resume' \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d '{
    "reason": "业务数据已修复，恢复审批流程"
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

**错误场景**：
- 400：只能恢复挂起状态的流程
- 400：恢复原因不能为空

---

## 三、自动化测试脚本

### 3.1 创建测试脚本

**文件**：`test-workflow-api.sh`

```bash
#!/bin/bash

BASE_URL="http://localhost:8080/api/workflow"
USER_ID="1"

echo "========================================="
echo "工作流接口联调测试"
echo "========================================="
echo ""

# 测试 1: 流程回退
echo "【测试 1】流程回退接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1000/rollback" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "targetNodeCode": "manager_approval",
    "reason": "材料不完整，请补充后重新提交"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 2: 任务转办
echo "【测试 2】任务转办接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1001/transfer" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: 10002" \
  -d '{
    "newAssigneeId": 10003,
    "reason": "本周出差，请帮忙代为审批"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 3: 任务委派
echo "【测试 3】任务委派接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1002/delegate" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: 10002" \
  -d '{
    "delegateToId": 10004,
    "reason": "请先进行初审，审核通过后我再终审"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 4: 挂起流程
echo "【测试 4】挂起流程实例接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/instances/100/suspend" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "reason": "发现业务数据异常，暂停审批，待修复后恢复"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 5: 恢复流程
echo "【测试 5】恢复流程实例接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/instances/100/resume" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "reason": "业务数据已修复，恢复审批流程"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 6: 参数校验 - 空 reason
echo "【测试 6】参数校验 - 空原因"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1000/rollback" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "targetNodeCode": "manager_approval",
    "reason": ""
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 7: 参数校验 - 缺少字段
echo "【测试 7】参数校验 - 缺少字段"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1000/transfer" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "reason": "test"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

echo "========================================="
echo "测试完成"
echo "========================================="
```

### 3.2 执行测试

```bash
# 赋予执行权限
chmod +x test-workflow-api.sh

# 执行测试
./test-workflow-api.sh
```

---

## 四、前端测试步骤

### 4.1 测试待办任务操作

1. **登录系统**
   - 访问 `http://localhost:3000`
   - 使用测试账号登录

2. **进入待办任务列表**
   - 导航到「工作流管理」→「我的待办」

3. **测试回退功能**
   - 点击任务右侧的「更多操作」→「回退」
   - 选择回退节点
   - 输入回退原因
   - 点击「确定」
   - 验证：提示「回退成功」，任务列表刷新

4. **测试转办功能**
   - 点击「更多操作」→「转办」
   - 搜索并选择新审批人
   - 输入转办原因
   - 点击「确定」
   - 验证：提示「转办成功」，任务从当前用户列表中消失

5. **测试委派功能**
   - 点击「更多操作」→「委派」
   - 搜索并选择受托人
   - 输入委派说明
   - 点击「确定」
   - 验证：提示「委派成功」，任务保持在当前用户列表中

### 4.2 测试流程实例操作

1. **进入流程实例详情**
   - 导航到「工作流管理」→「流程监控」
   - 点击某个运行中的流程实例

2. **测试挂起功能**
   - 点击「挂起流程」按钮
   - 输入挂起原因
   - 点击「确定」
   - 验证：
     - 提示「流程已挂起」
     - 流程状态显示「运行中(已挂起)」
     - 所有待办任务无法审批

3. **测试恢复功能**
   - 点击「恢复流程」按钮
   - 输入恢复原因
   - 点击「确定」
   - 验证：
     - 提示「流程已恢复」
     - 流程状态显示「运行中」
     - 待办任务可以继续审批

---

## 五、常见问题排查

### 5.1 404 Not Found

**问题**：接口返回 404

**排查**：
1. 检查 WorkflowRouter 是否注册为 @Bean
2. 检查路径是否正确（/api/workflow）
3. 查看应用启动日志，确认路由已加载

**解决**：
```bash
# 查看所有注册的路由
curl http://localhost:8080/actuator/mappings
```

### 5.2 400 Bad Request

**问题**：参数校验失败

**排查**：
1. 检查请求 Content-Type 是否为 application/json
2. 检查请求体 JSON 格式是否正确
3. 检查必填字段是否传递

**示例错误响应**：
```json
{
  "code": 400,
  "message": "回退原因不能为空",
  "data": null
}
```

### 5.3 500 Internal Server Error

**问题**：系统内部错误

**排查**：
1. 查看后端日志 `logs/apex-scaffold.log`
2. 检查数据库连接是否正常
3. 检查 WorkflowService 的依赖是否正常注入

**常见原因**：
- TaskRepository 或 ProcessInstanceRepository 未注入
- 数据库表结构不匹配（未执行 V21 迁移脚本）
- Validator 未注入

---

## 六、数据库验证

### 6.1 验证回退操作

```sql
-- 查看任务状态
SELECT id, status, node_code, assignee_id, completed_time
FROM wf_task
WHERE id = 1000;

-- 预期：status = 'ROLLED_BACK', completed_time 不为 NULL

-- 查看新创建的回退任务
SELECT id, status, node_code, assignee_id, created_time
FROM wf_task
WHERE process_instance_id = (SELECT process_instance_id FROM wf_task WHERE id = 1000)
ORDER BY created_time DESC
LIMIT 1;

-- 预期：status = 'PENDING', node_code = 'manager_approval'
```

### 6.2 验证转办操作

```sql
-- 查看转办后的任务
SELECT id, assignee_id, transferred_from
FROM wf_task
WHERE id = 1001;

-- 预期：assignee_id = 10003, transferred_from = 10002
```

### 6.3 验证挂起操作

```sql
-- 查看流程实例状态
SELECT id, status, suspended
FROM wf_process_instance
WHERE id = 100;

-- 预期：status = 'SUSPENDED', suspended = 1
```

---

## 七、性能测试

### 7.1 并发测试

使用 Apache Bench 进行并发测试：

```bash
# 测试回退接口并发性能
ab -n 100 -c 10 -T 'application/json' \
  -H 'X-User-Id: 1' \
  -p rollback.json \
  http://localhost:8080/api/workflow/tasks/1000/rollback
```

**rollback.json**:
```json
{
  "targetNodeCode": "manager_approval",
  "reason": "test"
}
```

**预期结果**：
- QPS > 100
- 平均响应时间 < 100ms
- 错误率 < 1%

### 7.2 压力测试

使用 JMeter 进行压力测试：
- 线程数：50
- 循环次数：100
- Ramp-Up 时间：10 秒

---

## 八、联调检查清单

### 8.1 后端检查项

- [ ] WorkflowHandler 已创建并注册为 @Component
- [ ] WorkflowRouter 已创建并注册为 @Bean
- [ ] 5 个请求 DTO 类已创建
- [ ] 代码已通过 spotless:apply 格式化
- [ ] WorkflowService 的 5 个方法已实现
- [ ] 数据库迁移脚本 V21 已执行
- [ ] 后端服务成功启动
- [ ] 路由已注册（通过 /actuator/mappings 验证）

### 8.2 前端检查项

- [ ] src/types/workflow.ts 已更新（新增字段和类型）
- [ ] src/api/workflow.ts 已更新（新增 5 个 API 方法）
- [ ] src/views/workflow/monitor/InstanceDetail.vue 已更新（挂起/恢复功能）
- [ ] src/views/workflow/components/TaskActions.vue 已创建
- [ ] 前端服务成功启动
- [ ] API 请求地址正确（/api/workflow）

### 8.3 接口测试检查项

- [ ] 流程回退接口测试通过（HTTP 200, code: 0）
- [ ] 任务转办接口测试通过
- [ ] 任务委派接口测试通过
- [ ] 挂起流程接口测试通过
- [ ] 恢复流程接口测试通过
- [ ] 参数校验正常（必填字段为空时返回 400）
- [ ] 错误处理正常（异常情况返回对应错误码）

### 8.4 功能测试检查项

- [ ] 前端「回退」操作正常，UI 提示成功
- [ ] 前端「转办」操作正常，任务从列表中消失
- [ ] 前端「委派」操作正常，任务保持在列表中
- [ ] 前端「挂起流程」操作正常，状态更新
- [ ] 前端「恢复流程」操作正常，状态恢复
- [ ] 数据库数据正确更新
- [ ] 日志记录正常

---

## 九、验收标准

### 9.1 功能验收

✅ **接口可用性**
- 所有 5 个接口返回 HTTP 200
- 响应格式符合规范 `{ code, message, data }`
- 参数校验正常

✅ **业务逻辑正确性**
- 回退后任务状态变为 ROLLED_BACK，新任务创建
- 转办后任务审批人变更，记录转办来源
- 委派后新任务创建，原任务保持 PENDING
- 挂起后流程状态变为 SUSPENDED，suspended 字段为 true
- 恢复后流程状态变为 RUNNING，suspended 字段为 false

✅ **前端集成**
- 前端调用接口成功
- UI 操作流畅，提示信息准确
- 数据刷新及时

### 9.2 性能验收

✅ **响应时间**
- 单次请求响应时间 < 200ms
- 并发 10 请求平均响应时间 < 300ms

✅ **稳定性**
- 连续运行 100 次请求无错误
- 并发 50 请求错误率 < 1%

### 9.3 代码质量验收

✅ **代码规范**
- 通过 spotless:check
- 所有类包含完整 Javadoc

✅ **测试覆盖**
- 单元测试覆盖核心逻辑
- 集成测试验证接口可用性

---

## 十、总结

### 10.1 交付成果

- ✅ WebFlux Handler：WorkflowHandler（5 个接口方法）
- ✅ WebFlux Router：WorkflowRouter（路由配置）
- ✅ 请求 DTO：5 个请求类
- ✅ 前端类型定义：workflow.ts 更新
- ✅ 前端 API 接口：5 个 API 方法
- ✅ 前端 UI 组件：TaskActions.vue
- ✅ 前端页面更新：InstanceDetail.vue
- ✅ 测试脚本：test-workflow-api.sh
- ✅ 联调文档：本文档

### 10.2 后续工作

1. **补充集成测试**
   - 编写 Spring WebFlux Test 测试用例
   - 模拟真实请求验证路由和 Handler

2. **完善前端功能**
   - 添加超时任务提醒
   - 添加任务列表字段显示
   - 添加流程图展示增强

3. **监控告警**
   - 接入 Prometheus 监控
   - 配置异常告警规则

---

**文档版本**：1.0  
**创建时间**：2026-09-04  
**作者**：Kiro (AI Assistant)
