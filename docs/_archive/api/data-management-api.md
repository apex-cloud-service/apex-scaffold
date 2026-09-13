# 数据管理 API 文档

## 概述

数据管理模块提供文档管理和图表管理功能，支持文档和图表的完整生命周期管理。

**基础路径**: `/admin-api/v1/data`

---

## 文档管理 API

### 1. 创建文档

**接口**: `POST /documents`

**请求体**:
```json
{
  "title": "测试文档",
  "docType": "report",
  "category": "tech",
  "content": "文档内容",
  "tags": ["标签1", "标签2"],
  "fileUrl": "http://example.com/file.pdf",
  "fileSize": 1024
}
```

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "title": "测试文档",
    "docType": "report",
    "category": "tech",
    "status": 0,
    "viewCount": 0,
    "downloadCount": 0,
    "createTime": "2026-09-12T10:00:00"
  }
}
```

### 2. 查询文档详情

**接口**: `GET /documents/{id}`

**路径参数**:
- `id`: 文档ID

**响应**: 同创建文档

### 3. 更新文档

**接口**: `PUT /documents/{id}`

**请求体**: 同创建文档

**响应**: 同创建文档

### 4. 删除文档

**接口**: `DELETE /documents/{id}`

**响应**:
```json
{
  "code": 0,
  "msg": "success"
}
```

### 5. 分页查询文档

**接口**: `GET /documents`

**查询参数**:
- `keyword`: 关键词（可选）
- `docType`: 文档类型（可选）
- `category`: 分类（可选）
- `status`: 状态（可选）
- `page`: 页码（默认1）
- `size`: 每页数量（默认10）

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 10,
    "list": [...]
  }
}
```

### 6. 更新文档状态

**接口**: `PATCH /documents/{id}/status`

**查询参数**:
- `status`: 状态值（0=草稿，1=正常，2=归档）

### 7. 增加查看次数

**接口**: `POST /documents/{id}/view`

### 8. 增加下载次数

**接口**: `POST /documents/{id}/download`

---

## 图表管理 API

### 1. 创建图表

**接口**: `POST /charts`

**请求体**:
```json
{
  "chartName": "销售趋势图",
  "chartType": "line",
  "category": "business",
  "description": "月度销售趋势分析",
  "dataSource": "sales_db",
  "config": {
    "color": "blue",
    "smooth": true
  }
}
```

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "chartName": "销售趋势图",
    "chartType": "line",
    "category": "business",
    "status": 0,
    "viewCount": 0,
    "createTime": "2026-09-12T10:00:00"
  }
}
```

### 2. 查询图表详情

**接口**: `GET /charts/{id}`

### 3. 更新图表

**接口**: `PUT /charts/{id}`

### 4. 删除图表

**接口**: `DELETE /charts/{id}`

### 5. 分页查询图表

**接口**: `GET /charts`

**查询参数**:
- `keyword`: 关键词（可选）
- `chartType`: 图表类型（可选）
- `category`: 分类（可选）
- `status`: 状态（可选）
- `page`: 页码（默认1）
- `size`: 每页数量（默认10）

### 6. 更新图表状态

**接口**: `PATCH /charts/{id}/status`

**查询参数**:
- `status`: 状态值（0=草稿，1=正常）

### 7. 获取图表数据

**接口**: `GET /charts/{id}/data`

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "chartType": "line",
    "title": "销售趋势图",
    "xAxis": ["1月", "2月", "3月"],
    "series": [
      {
        "name": "销售额",
        "data": [100, 200, 300]
      }
    ]
  }
}
```

### 8. 增加查看次数

**接口**: `POST /charts/{id}/view`

---

## 数据字典

### 文档类型（docType）
- `contract`: 合同
- `report`: 报告
- `proposal`: 提案
- `manual`: 手册
- `other`: 其他

### 文档分类（category）
- `tech`: 技术
- `business`: 业务
- `finance`: 财务
- `hr`: 人力资源

### 图表类型（chartType）
- `line`: 折线图
- `bar`: 柱状图
- `pie`: 饼图
- `scatter`: 散点图
- `radar`: 雷达图

### 图表分类（category）
- `business`: 业务
- `tech`: 技术
- `operation`: 运营
- `finance`: 财务

### 状态（status）
- 文档：`0`=草稿，`1`=正常，`2`=归档
- 图表：`0`=草稿，`1`=正常

---

## 错误码

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 40001 | 参数错误 |
| 40004 | 资源不存在 |
| 50001 | 服务器内部错误 |

## 认证

所有接口都需要在请求头中携带 JWT Token：

```
Authorization: Bearer <token>
```

## 多租户

所有接口都会自动根据当前用户的租户ID进行数据隔离。
