# 定时任务分页查询接口文档

## 接口概述

**接口名称**: 定时任务分页查询
**接口路径**: `GET /admin-api/v1/jobs/page`
**接口标签**: 定时任务管理
**认证方式**: Bearer JWT Token
**版本**: v1.0.0

---

## 1. 接口描述

根据条件分页查询定时任务列表，支持按任务名称、任务分组和任务状态进行筛选。

### 1.1 功能特性

- ✅ 支持分页查询，可自定义页码和每页大小
- ✅ 支持按任务名称模糊匹配筛选
- ✅ 支持按任务分组精确匹配筛选
- ✅ 支持按任务状态筛选（启用/禁用）
- ✅ 支持多条件组合查询
- ✅ 响应式编程实现，高性能并发查询
- ✅ 统一Result响应格式

### 1.2 业务规则

- 页码从1开始，小于1时抛出参数错误
- 每页大小限制在1-100之间
- 查询条件均为可选，不传则查询全部
- 按创建时间倒序排列（最新创建的在前）

---

## 2. 请求参数

### 2.1 Query参数

| 参数名 | 类型 | 必填 | 说明 | 默认值 | 示例 | 约束 |
|--------|------|------|------|--------|------|------|
| pageNum | Integer | 是 | 页码 | - | 1 | ≥1 |
| pageSize | Integer | 是 | 每页大小 | - | 10 | 1-100 |
| jobName | String | 否 | 任务名称（模糊匹配） | null | "数据同步" | 最长100字符 |
| jobGroup | String | 否 | 任务分组（精确匹配） | null | "system" | 最长50字符 |
| status | Integer | 否 | 任务状态 | null | 1 | 0或1 |

### 2.2 状态码说明

| 状态值 | 说明 |
|--------|------|
| 0 | 禁用 |
| 1 | 启用 |

### 2.3 请求示例

#### 示例1: 基础分页查询

```http
GET /admin-api/v1/jobs/page?pageNum=1&pageSize=10 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 示例2: 按任务名称筛选

```http
GET /admin-api/v1/jobs/page?pageNum=1&pageSize=20&jobName=数据同步 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 示例3: 组合条件查询

```http
GET /admin-api/v1/jobs/page?pageNum=2&pageSize=15&jobGroup=system&status=1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## 3. 响应结果

### 3.1 响应格式

统一使用`Result<PageResponse<JobDTO>>`封装，包含以下字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| code | Integer | 响应码（0 表示成功） |
| msg | String | 响应消息 |
| data | PageResponse | 分页数据对象 |
| traceId | String | 请求追踪ID |

### 3.2 PageResponse结构

| 字段名 | 类型 | 说明 |
|--------|------|------|
| total | Long | 总记录数 |
| pageNum | Integer | 当前页码 |
| pageSize | Integer | 每页大小 |
| pages | Integer | 总页数 |
| hasNext | Boolean | 是否还有下一页（派生字段） |
| list | List\<JobDTO\> | 任务记录列表（`records` 为同一数组的序列化别名，均会输出） |

### 3.3 JobDTO结构

| 字段名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| id | Long | 是 | 任务ID | 1 |
| jobName | String | 是 | 任务名称 | "数据同步任务" |
| jobGroup | String | 是 | 任务分组 | "system" |
| invokeTarget | String | 是 | 调用目标 | "com.example.task.SyncTask" |
| cronExpression | String | 是 | Cron表达式 | "0 0 2 * * ?" |
| misfirePolicy | Integer | 是 | 错过执行策略 | 1 |
| concurrent | Integer | 是 | 是否并发执行 | 0 |
| status | Integer | 是 | 任务状态 | 1 |
| description | String | 否 | 任务描述 | "每天凌晨2点执行数据同步" |
| createTime | String | 是 | 创建时间 | "2026-09-13T10:00:00" |
| updateTime | String | 是 | 更新时间 | "2026-09-13T10:00:00" |

### 3.4 成功响应示例

```json
{
  "code": 0,
  "msg": "操作成功",
  "data": {
    "total": 100,
    "pageNum": 1,
    "pageSize": 10,
    "records": [
      {
        "id": 1,
        "jobName": "数据同步任务",
        "jobGroup": "system",
        "invokeTarget": "com.apex.scaffold.task.DataSyncTask",
        "cronExpression": "0 0 2 * * ?",
        "misfirePolicy": 1,
        "concurrent": 0,
        "status": 1,
        "description": "每天凌晨2点执行数据同步",
        "createTime": "2026-09-13T10:00:00",
        "updateTime": "2026-09-13T10:00:00"
      },
      {
        "id": 2,
        "jobName": "日志清理任务",
        "jobGroup": "system",
        "invokeTarget": "com.apex.scaffold.task.LogCleanupTask",
        "cronExpression": "0 0 3 * * ?",
        "misfirePolicy": 2,
        "concurrent": 0,
        "status": 1,
        "description": "每天凌晨3点清理过期日志",
        "createTime": "2026-09-12T15:30:00",
        "updateTime": "2026-09-12T15:30:00"
      }
    ]
  },
  "traceId": "1a2b3c4d5e6f7g8h"
}
```

### 3.5 空结果响应示例

```json
{
  "code": 0,
  "msg": "操作成功",
  "data": {
    "total": 0,
    "pageNum": 1,
    "pageSize": 10,
    "records": []
  },
  "traceId": "1a2b3c4d5e6f7g8h"
}
```

---

## 4. 错误响应

### 4.1 错误响应格式

```json
{
  "code": 错误码,
  "msg": "错误消息",
  "data": null,
  "traceId": "请求追踪ID"
}
```

### 4.2 常见错误码

| 错误码 | 错误消息 | 说明 | 解决方案 |
|--------|---------|------|----------|
| 400 | 参数错误（`PARAM_ERROR`） | pageNum/pageSize/status 等参数不符合校验规则（页码≥1、每页 1-100、status 为 0/1） | 检查参数是否符合约束条件 |
| 401 | 未授权（`UNAUTHORIZED`） | 未提供或 Token 无效 | 提供有效的 Bearer Token |
| 40101 | 登录已过期，请重新登录（`TOKEN_EXPIRED`） | JWT Token 已过期 | 重新登录获取新 Token |
| -1 | 系统错误（`ERROR`） | 服务器异常 | 联系技术支持 |

### 4.3 参数错误示例

```json
{
  "code": 1001,
  "msg": "页码必须大于等于1",
  "data": null,
  "traceId": "1a2b3c4d5e6f7g8h"
}
```

### 4.4 认证错误示例

```json
{
  "code": 03,
  "msg": "未授权访问",
  "data": null,
  "traceId": "1a2b3c4d5e6f7g8h"
}
```

---

## 5. 代码示例

### 5.1 JavaScript/Axios

```javascript
// 基础查询
axios.get('/admin-api/v1/jobs/page', {
  params: {
    pageNum: 1,
    pageSize: 10
  },
  headers: {
    'Authorization': 'Bearer ' + token
  }
})
.then(response => {
  const { total, records } = response.data.data;
  console.log(`共${total}条记录`, records);
})
.catch(error => {
  console.error('查询失败', error.response.data.msg);
});

// 条件筛选
axios.get('/admin-api/v1/jobs/page', {
  params: {
    pageNum: 1,
    pageSize: 20,
    jobName: '数据同步',
    status: 1
  },
  headers: {
    'Authorization': 'Bearer ' + token
  }
})
.then(response => {
  // 处理响应
});
```

### 5.2 Java/Spring WebClient

```java
WebClient webClient = WebClient.builder()
    .baseUrl("http://localhost:8080")
    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
    .build();

// 构建查询参数
MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
params.add("pageNum", "1");
params.add("pageSize", "10");
params.add("jobName", "数据同步");
params.add("status", "1");

// 发起请求
webClient.get()
    .uri(uriBuilder -> uriBuilder
        .path("/admin-api/v1/jobs/page")
        .queryParams(params)
        .build())
    .retrieve()
    .bodyToMono(new ParameterizedTypeReference<Result<PageResponse<JobDTO>>>() {})
    .subscribe(result -> {
        PageResponse<JobDTO> pageData = result.getData();
        System.out.println("总数: " + pageData.getTotal());
        pageData.getRecords().forEach(System.out::println);
    });
```

### 5.3 curl

```bash
# 基础查询
curl -X GET "http://localhost:8080/admin-api/v1/jobs/page?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer YOUR_TOKEN"

# 条件查询
curl -X GET "http://localhost:8080/admin-api/v1/jobs/page?pageNum=1&pageSize=20&jobName=数据同步&status=1" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 5.4 Python/requests

```python
import requests

# 配置
url = "http://localhost:8080/admin-api/v1/jobs/page"
headers = {
    "Authorization": f"Bearer {token}"
}
params = {
    "pageNum": 1,
    "pageSize": 10,
    "jobName": "数据同步",
    "status": 1
}

# 发起请求
response = requests.get(url, headers=headers, params=params)
data = response.json()

if data['code'] == 0:
    page_data = data['data']
    print(f"总数: {page_data['total']}")
    for job in page_data['records']:
        print(job)
else:
    print(f"错误: {data['msg']}")
```

---

## 6. 性能指标

### 6.1 响应时间

| 场景 | 数据量 | 预期响应时间 |
|------|--------|-------------|
| 无筛选条件 | <1000条 | <100ms |
| 无筛选条件 | 1000-10000条 | <200ms |
| 单一筛选条件 | <1000条 | <80ms |
| 多条件筛选 | <1000条 | <100ms |

### 6.2 并发能力

- 支持高并发查询（响应式编程实现）
- 阻塞I/O操作已正确隔离到专用线程池
- 建议QPS: 500-1000（具体取决于硬件配置）

### 6.3 优化建议

- ✅ 已实现并行查询（count和data同时查询）
- ✅ 已添加数据库索引（job_name, job_group, status）
- ✅ 已实现分页limit优化
- 💡 建议：高频查询可考虑添加Redis缓存

---

## 7. 注意事项

### 7.1 安全相关

- ⚠️ 必须携带有效的JWT Token，否则返回401未授权
- ⚠️ Token过期需重新登录获取
- ⚠️ 确保Token在请求头中正确设置为`Authorization: Bearer <token>`

### 7.2 参数校验

- ⚠️ pageNum和pageSize为必填参数
- ⚠️ pageNum必须≥1
- ⚠️ pageSize必须在1-100之间
- ⚠️ status只能为0或1

### 7.3 数据一致性

- 分页查询过程中，如果有新增或删除操作，可能导致翻页时出现重复或遗漏
- 建议在前端实现时，每次筛选条件变化时重置到第一页

### 7.4 性能考虑

- 避免查询过大的pageSize（建议≤100）
- 频繁查询同一条件时，建议前端实现缓存
- 大数据量翻页时，推荐使用游标分页（后续版本支持）

---

## 8. 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0.0 | 2026-09-13 | 初始版本，实现基础分页查询和筛选功能 |

---

## 9. 相关接口

| 接口名称 | 接口路径 | 说明 |
|---------|---------|------|
| 创建定时任务 | POST /admin-api/v1/jobs | 创建新的定时任务 |
| 获取任务详情 | GET /admin-api/v1/jobs/{id} | 根据ID获取任务详情 |
| 更新定时任务 | PUT /admin-api/v1/jobs/{id} | 更新任务信息 |
| 删除定时任务 | DELETE /admin-api/v1/jobs/{id} | 删除指定任务 |
| 执行定时任务 | POST /admin-api/v1/jobs/{id}/run | 手动执行任务 |
| 更新任务状态 | PUT /admin-api/v1/jobs/{id}/status | 启用/禁用任务 |

---

## 10. FAQ

### Q1: 如何实现模糊查询？

A: jobName参数支持模糊匹配，系统会在数据库层使用LIKE查询，无需手动添加通配符。

### Q2: 可以同时按多个条件筛选吗？

A: 可以，所有筛选条件都支持组合使用，系统会自动构建AND条件查询。

### Q3: 为什么查询结果和预期不符？

A: 请检查：
1. 筛选条件是否正确
2. 任务是否被删除（逻辑删除）
3. 是否有权限查看该任务

### Q4: 分页查询有总数限制吗？

A: 没有总数限制，但单次查询最多返回100条记录（pageSize上限）。

### Q5: 支持排序吗？

A: 当前版本按创建时间倒序排列（最新的在前），后续版本将支持自定义排序。

---

**文档维护**: Apex Team
**最后更新**: 2026-09-13
**文档版本**: v1.0.0
**接口版本**: v1.0.0
