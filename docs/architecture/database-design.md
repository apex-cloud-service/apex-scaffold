# Apex Scaffold 数据库设计文档

> 文档版本: v1.0  
> 创建时间: 2026-09-03  
> 数据库版本: MySQL 8.0  
> 字符集: utf8mb4 / utf8mb4_unicode_ci

> **配套 E-R 图**: [核心表数据库关系图](../diagrams/08_database_er_diagram.svg)

***

## 一、数据库概览

### 1.1 数据库基本信息

| 属性        | 值                    |
| --------- | -------------------- |
| **数据库名称** | apex\_data           |
| **数据库引擎** | InnoDB               |
| **字符集**   | utf8mb4              |
| **排序规则**  | utf8mb4\_unicode\_ci |
| **表数量**   | 40+                  |
| **迁移脚本**  | Flyway V1-V20        |

### 1.2 表分类

#### 1.2.1 核心业务表 (7张)

- `sys_user` - 用户表

- `sys_role` - 角色表

- `sys_permission` - 权限表

- `sys_menu` - 菜单表

- `sys_dept` - 部门表

- `sys_tenant` - 租户表

- `sys_user_role` - 用户角色关联表

- `sys_role_permission` - 角色权限关联表

#### 1.2.2 系统管理表 (8张)

- `sys_config` - 参数配置表

- `sys_dict_type` - 字典类型表

- `sys_dict_data` - 字典数据表

- `sys_job` - 定时任务表

- `sys_job_log` - 定时任务日志表

- `sys_file` - 文件表

- `sys_notice` - 通知公告表

- `sys_oss_config` - 对象存储配置表

#### 1.2.3 日志审计表 (4张)

- `sys_login_log` - 登录日志表

- `sys_operation_log` - 操作日志表

- `sys_api_call_log` - API调用日志表

- `sys_online_user` - 在线用户表

#### 1.2.4 工作流表 (5张)

- `wf_definition` - 流程定义表

- `wf_deployment` - 流程部署表

- `wf_instance` - 流程实例表

- `wf_task` - 任务表

- `wf_approval_record` - 审批记录表

#### 1.2.5 其他业务表

- 用户合规表、黑名单表、举报表等

***

## 二、核心表设计

### 2.1 sys\_user (用户表)

**表说明**: 存储用户基本信息、认证信息、组织归属

**字段设计**:

| 字段名               | 类型       | 长度  | 可空       | 默认值                          | 说明           |
| ----------------- | -------- | --- | -------- | ---------------------------- | ------------ |
| `id`              | BIGINT   | -   | NOT NULL | -                            | 主键,雪花算法      |
| `username`        | VARCHAR  | 50  | NULL     | -                            | 登录账号,唯一      |
| `password`        | VARCHAR  | 100 | NULL     | -                            | BCrypt密码哈希   |
| `open_id`         | VARCHAR  | 64  | NULL     | -                            | 微信OpenID,唯一  |
| `union_id`        | VARCHAR  | 64  | NULL     | -                            | 微信UnionID    |
| `nick_name`       | VARCHAR  | 50  | NULL     | -                            | 昵称           |
| `avatar_url`      | VARCHAR  | 255 | NULL     | -                            | 头像URL        |
| `gender`          | INT      | -   | NULL     | 0                            | 性别(0未知1男2女)  |
| `birthday`        | DATE     | -   | NULL     | -                            | 生日           |
| `phone`           | VARCHAR  | 20  | NULL     | -                            | 手机号          |
| `email`           | VARCHAR  | 100 | NULL     | -                            | 邮箱           |
| `dept_id`         | BIGINT   | -   | NULL     | -                            | 部门ID(FK)     |
| `tenant_id`       | VARCHAR  | 64  | NULL     | -                            | 租户ID(FK)     |
| `status`          | INT      | -   | NOT NULL | 1                            | 状态(0禁用1启用)   |
| `last_login_time` | DATETIME | -   | NULL     | -                            | 最后登录时间       |
| `last_login_ip`   | VARCHAR  | 50  | NULL     | -                            | 最后登录IP       |
| `create_time`     | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间         |
| `update_time`     | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间         |
| `version`         | INT      | -   | NOT NULL | 0                            | 乐观锁版本号       |
| `deleted`         | INT      | -   | NOT NULL | 0                            | 逻辑删除(0未删1已删) |

**索引设计**:

| 索引名             | 类型   | 字段          | 说明         |
| --------------- | ---- | ----------- | ---------- |
| `PRIMARY`       | 主键   | `id`        | 主键索引       |
| `uk_username`   | 唯一索引 | `username`  | 用户名唯一      |
| `uk_open_id`    | 唯一索引 | `open_id`   | 微信OpenID唯一 |
| `idx_phone`     | 普通索引 | `phone`     | 手机号查询      |
| `idx_dept_id`   | 普通索引 | `dept_id`   | 部门查询       |
| `idx_tenant_id` | 普通索引 | `tenant_id` | 租户隔离       |

**约束说明**:

- `username`与`open_id`至少有一个非空(业务层保证)

- `password`在账号密码登录时必填

- `tenant_id`在多租户场景必填

***

### 2.2 sys\_role (角色表)

**表说明**: 存储角色信息,RBAC权限模型核心表

**字段设计**:

| 字段名           | 类型       | 长度  | 可空       | 默认值                          | 说明         |
| ------------- | -------- | --- | -------- | ---------------------------- | ---------- |
| `id`          | BIGINT   | -   | NOT NULL | -                            | 主键         |
| `role_name`   | VARCHAR  | 50  | NOT NULL | -                            | 角色名称       |
| `role_key`    | VARCHAR  | 50  | NOT NULL | -                            | 角色标识       |
| `role_sort`   | INT      | -   | NOT NULL | 0                            | 排序         |
| `data_scope`  | INT      | -   | NOT NULL | 1                            | 数据权限范围     |
| `status`      | INT      | -   | NOT NULL | 1                            | 状态(0禁用1启用) |
| `remark`      | VARCHAR  | 500 | NULL     | -                            | 备注         |
| `tenant_id`   | VARCHAR  | 64  | NULL     | -                            | 租户ID       |
| `create_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间       |
| `update_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间       |
| `version`     | INT      | -   | NOT NULL | 0                            | 乐观锁        |
| `deleted`     | INT      | -   | NOT NULL | 0                            | 逻辑删除       |

**数据权限范围枚举**:

| 值   | 说明            |
| --- | ------------- |
| `1` | 全部数据权限        |
| `2` | 自定义数据权限(指定部门) |
| `3` | 本部门数据权限       |
| `4` | 本部门及以下数据权限    |
| `5` | 仅本人数据权限       |

**索引设计**:

| 索引名                  | 类型   | 字段                    | 说明        |
| -------------------- | ---- | --------------------- | --------- |
| `PRIMARY`            | 主键   | `id`                  | 主键索引      |
| `uk_role_key_tenant` | 唯一索引 | `role_key, tenant_id` | 租户内角色标识唯一 |
| `idx_tenant_id`      | 普通索引 | `tenant_id`           | 租户隔离      |

***

### 2.3 sys\_permission (权限表)

**表说明**: 存储权限资源,包括API权限、按钮权限

**字段设计**:

| 字段名               | 类型       | 长度  | 可空       | 默认值                          | 说明               |
| ----------------- | -------- | --- | -------- | ---------------------------- | ---------------- |
| `id`              | BIGINT   | -   | NOT NULL | -                            | 主键               |
| `permission_name` | VARCHAR  | 50  | NOT NULL | -                            | 权限名称             |
| `permission_key`  | VARCHAR  | 100 | NOT NULL | -                            | 权限标识             |
| `resource_type`   | VARCHAR  | 20  | NOT NULL | -                            | 资源类型(API/BUTTON) |
| `api_path`        | VARCHAR  | 255 | NULL     | -                            | API路径            |
| `api_method`      | VARCHAR  | 10  | NULL     | -                            | HTTP方法           |
| `description`     | VARCHAR  | 255 | NULL     | -                            | 描述               |
| `create_time`     | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间             |
| `update_time`     | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间             |
| `deleted`         | INT      | -   | NOT NULL | 0                            | 逻辑删除             |

**资源类型枚举**:

- `API`: API接口权限

- `BUTTON`: 按钮权限

**索引设计**:

| 索引名                 | 类型   | 字段               | 说明     |
| ------------------- | ---- | ---------------- | ------ |
| `PRIMARY`           | 主键   | `id`             | 主键索引   |
| `uk_permission_key` | 唯一索引 | `permission_key` | 权限标识唯一 |
| `idx_resource_type` | 普通索引 | `resource_type`  | 资源类型查询 |

***

### 2.4 sys\_menu (菜单表)

**表说明**: 存储菜单树结构,支持动态路由

**字段设计**:

| 字段名           | 类型       | 长度  | 可空       | 默认值                          | 说明                    |
| ------------- | -------- | --- | -------- | ---------------------------- | --------------------- |
| `id`          | BIGINT   | -   | NOT NULL | -                            | 主键                    |
| `menu_name`   | VARCHAR  | 50  | NOT NULL | -                            | 菜单名称                  |
| `parent_id`   | BIGINT   | -   | NOT NULL | 0                            | 父菜单ID(0为根)            |
| `menu_type`   | VARCHAR  | 10  | NOT NULL | -                            | 菜单类型(DIR/MENU/BUTTON) |
| `route_path`  | VARCHAR  | 200 | NULL     | -                            | 路由路径                  |
| `component`   | VARCHAR  | 200 | NULL     | -                            | 组件路径                  |
| `perms`       | VARCHAR  | 100 | NULL     | -                            | 权限标识                  |
| `icon`        | VARCHAR  | 50  | NULL     | -                            | 图标                    |
| `sort_order`  | INT      | -   | NOT NULL | 0                            | 排序                    |
| `visible`     | INT      | -   | NOT NULL | 1                            | 是否可见(0隐藏1显示)          |
| `status`      | INT      | -   | NOT NULL | 1                            | 状态(0禁用1启用)            |
| `create_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间                  |
| `update_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间                  |
| `deleted`     | INT      | -   | NOT NULL | 0                            | 逻辑删除                  |

**菜单类型枚举**:

- `DIR`: 目录

- `MENU`: 菜单

- `BUTTON`: 按钮

**索引设计**:

| 索引名             | 类型   | 字段          | 说明    |
| --------------- | ---- | ----------- | ----- |
| `PRIMARY`       | 主键   | `id`        | 主键索引  |
| `idx_parent_id` | 普通索引 | `parent_id` | 父菜单查询 |

***

### 2.5 sys\_dept (部门表)

**表说明**: 存储部门树结构,支持多租户隔离

**字段设计**:

| 字段名           | 类型       | 长度  | 可空       | 默认值                          | 说明         |
| ------------- | -------- | --- | -------- | ---------------------------- | ---------- |
| `id`          | BIGINT   | -   | NOT NULL | -                            | 主键         |
| `dept_name`   | VARCHAR  | 50  | NOT NULL | -                            | 部门名称       |
| `parent_id`   | BIGINT   | -   | NOT NULL | 0                            | 父部门ID      |
| `ancestors`   | VARCHAR  | 500 | NULL     | -                            | 祖级列表(逗号分隔) |
| `sort_order`  | INT      | -   | NOT NULL | 0                            | 排序         |
| `leader`      | VARCHAR  | 50  | NULL     | -                            | 负责人        |
| `phone`       | VARCHAR  | 20  | NULL     | -                            | 联系电话       |
| `email`       | VARCHAR  | 100 | NULL     | -                            | 邮箱         |
| `status`      | INT      | -   | NOT NULL | 1                            | 状态(0禁用1启用) |
| `tenant_id`   | VARCHAR  | 64  | NOT NULL | -                            | 租户ID       |
| `create_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间       |
| `update_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间       |
| `deleted`     | INT      | -   | NOT NULL | 0                            | 逻辑删除       |

**索引设计**:

| 索引名               | 类型   | 字段              | 说明      |
| ----------------- | ---- | --------------- | ------- |
| `PRIMARY`         | 主键   | `id`            | 主键索引    |
| `idx_parent_id`   | 普通索引 | `parent_id`     | 父部门查询   |
| `idx_tenant_id`   | 普通索引 | `tenant_id`     | 租户隔离    |
| `uk_tenant_id_id` | 唯一索引 | `tenant_id, id` | 租户内部门唯一 |

**约束说明**:

- V19迁移脚本强制要求`tenant_id`非空

- 外键约束: `sys_user.dept_id` → `sys_dept(tenant_id, id)`

***

### 2.6 sys\_tenant (租户表)

**表说明**: 多租户架构核心表,存储租户基本信息

**字段设计**:

| 字段名             | 类型       | 长度  | 可空       | 默认值                          | 说明            |
| --------------- | -------- | --- | -------- | ---------------------------- | ------------- |
| `id`            | VARCHAR  | 64  | NOT NULL | -                            | 租户ID(主键)      |
| `tenant_name`   | VARCHAR  | 100 | NOT NULL | -                            | 租户名称          |
| `tenant_key`    | VARCHAR  | 50  | NOT NULL | -                            | 租户标识          |
| `contact_name`  | VARCHAR  | 50  | NULL     | -                            | 联系人           |
| `contact_phone` | VARCHAR  | 20  | NULL     | -                            | 联系电话          |
| `contact_email` | VARCHAR  | 100 | NULL     | -                            | 联系邮箱          |
| `expire_time`   | DATETIME | -   | NULL     | -                            | 过期时间          |
| `package_id`    | BIGINT   | -   | NULL     | -                            | 套餐ID          |
| `status`        | INT      | -   | NOT NULL | 1                            | 状态(0停用1正常2过期) |
| `create_time`   | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间          |
| `update_time`   | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间          |
| `deleted`       | INT      | -   | NOT NULL | 0                            | 逻辑删除          |

**索引设计**:

| 索引名             | 类型   | 字段           | 说明     |
| --------------- | ---- | ------------ | ------ |
| `PRIMARY`       | 主键   | `id`         | 主键索引   |
| `uk_tenant_key` | 唯一索引 | `tenant_key` | 租户标识唯一 |

***

### 2.7 sys\_user\_role (用户角色关联表)

**表说明**: 多对多关联表,用户与角色关系

**字段设计**:

| 字段名           | 类型       | 长度 | 可空       | 默认值                | 说明   |
| ------------- | -------- | -- | -------- | ------------------ | ---- |
| `user_id`     | BIGINT   | -  | NOT NULL | -                  | 用户ID |
| `role_id`     | BIGINT   | -  | NOT NULL | -                  | 角色ID |
| `create_time` | DATETIME | -  | NOT NULL | CURRENT\_TIMESTAMP | 创建时间 |

**索引设计**:

| 索引名           | 类型   | 字段                 | 说明     |
| ------------- | ---- | ------------------ | ------ |
| `PRIMARY`     | 主键   | `user_id, role_id` | 复合主键   |
| `idx_role_id` | 普通索引 | `role_id`          | 角色查询用户 |

***

### 2.8 sys\_role\_permission (角色权限关联表)

**表说明**: 多对多关联表,角色与权限关系

**字段设计**:

| 字段名             | 类型       | 长度 | 可空       | 默认值                | 说明   |
| --------------- | -------- | -- | -------- | ------------------ | ---- |
| `role_id`       | BIGINT   | -  | NOT NULL | -                  | 角色ID |
| `permission_id` | BIGINT   | -  | NOT NULL | -                  | 权限ID |
| `create_time`   | DATETIME | -  | NOT NULL | CURRENT\_TIMESTAMP | 创建时间 |

**索引设计**:

| 索引名                 | 类型   | 字段                       | 说明     |
| ------------------- | ---- | ------------------------ | ------ |
| `PRIMARY`           | 主键   | `role_id, permission_id` | 复合主键   |
| `idx_permission_id` | 普通索引 | `permission_id`          | 权限查询角色 |

***

## 三、日志审计表设计

### 3.1 sys\_login\_log (登录日志表)

**表说明**: 记录用户登录成功/失败日志

**字段设计**:

| 字段名           | 类型       | 长度  | 可空       | 默认值                | 说明           |
| ------------- | -------- | --- | -------- | ------------------ | ------------ |
| `id`          | BIGINT   | -   | NOT NULL | -                  | 主键           |
| `user_id`     | BIGINT   | -   | NULL     | -                  | 用户ID         |
| `username`    | VARCHAR  | 50  | NULL     | -                  | 用户名          |
| `login_time`  | DATETIME | -   | NOT NULL | -                  | 登录时间         |
| `ip`          | VARCHAR  | 50  | NULL     | -                  | 登录IP         |
| `location`    | VARCHAR  | 100 | NULL     | -                  | 登录地点         |
| `browser`     | VARCHAR  | 100 | NULL     | -                  | 浏览器          |
| `os`          | VARCHAR  | 100 | NULL     | -                  | 操作系统         |
| `status`      | INT      | -   | NOT NULL | -                  | 登录状态(0失败1成功) |
| `message`     | VARCHAR  | 255 | NULL     | -                  | 提示信息         |
| `create_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP | 创建时间         |

**索引设计**:

| 索引名              | 类型   | 字段           | 说明     |
| ---------------- | ---- | ------------ | ------ |
| `PRIMARY`        | 主键   | `id`         | 主键索引   |
| `idx_user_id`    | 普通索引 | `user_id`    | 用户查询   |
| `idx_login_time` | 普通索引 | `login_time` | 时间范围查询 |
| `idx_status`     | 普通索引 | `status`     | 状态查询   |

***

### 3.2 sys\_operation\_log (操作日志表)

**表说明**: 记录用户操作行为,AOP切面记录

**字段设计**:

| 字段名               | 类型       | 长度  | 可空       | 默认值                | 说明           |
| ----------------- | -------- | --- | -------- | ------------------ | ------------ |
| `id`              | BIGINT   | -   | NOT NULL | -                  | 主键           |
| `module`          | VARCHAR  | 100 | NOT NULL | -                  | 操作模块         |
| `operation_type`  | VARCHAR  | 50  | NULL     | -                  | 操作类型         |
| `description`     | VARCHAR  | 255 | NULL     | -                  | 操作描述         |
| `request_method`  | VARCHAR  | 10  | NULL     | -                  | 请求方法         |
| `request_uri`     | VARCHAR  | 500 | NULL     | -                  | 请求URI        |
| `request_params`  | TEXT     | -   | NULL     | -                  | 请求参数         |
| `response_result` | TEXT     | -   | NULL     | -                  | 响应结果         |
| `status`          | INT      | -   | NULL     | -                  | 操作状态(0成功1失败) |
| `error_msg`       | TEXT     | -   | NULL     | -                  | 错误信息         |
| `execution_time`  | BIGINT   | -   | NULL     | -                  | 执行时间(毫秒)     |
| `ip`              | VARCHAR  | 50  | NULL     | -                  | 操作IP         |
| `user_id`         | BIGINT   | -   | NULL     | -                  | 操作用户ID       |
| `user_name`       | VARCHAR  | 50  | NULL     | -                  | 操作用户名        |
| `operation_time`  | DATETIME | -   | NULL     | -                  | 操作时间         |
| `trace_id`        | VARCHAR  | 100 | NULL     | -                  | 追踪ID         |
| `create_time`     | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP | 创建时间         |

**索引设计**:

| 索引名                  | 类型   | 字段               | 说明     |
| -------------------- | ---- | ---------------- | ------ |
| `PRIMARY`            | 主键   | `id`             | 主键索引   |
| `idx_module`         | 普通索引 | `module`         | 模块查询   |
| `idx_user_id`        | 普通索引 | `user_id`        | 用户查询   |
| `idx_operation_type` | 普通索引 | `operation_type` | 操作类型查询 |
| `idx_status`         | 普通索引 | `status`         | 状态查询   |
| `idx_trace_id`       | 普通索引 | `trace_id`       | 链路追踪   |

***

### 3.3 sys\_api\_call\_log (API调用日志表)

**表说明**: 记录所有HTTP API调用,用于监控审计

**字段设计**:

| 字段名               | 类型       | 长度   | 可空       | 默认值                | 说明           |
| ----------------- | -------- | ---- | -------- | ------------------ | ------------ |
| `id`              | BIGINT   | -    | NOT NULL | -                  | 主键           |
| `service_name`    | VARCHAR  | 100  | NULL     | -                  | 服务名称         |
| `method`          | VARCHAR  | 10   | NULL     | -                  | HTTP方法       |
| `path`            | VARCHAR  | 500  | NULL     | -                  | 请求路径         |
| `url`             | VARCHAR  | 1000 | NULL     | -                  | 完整URL        |
| `response_status` | VARCHAR  | 10   | NULL     | -                  | 响应状态码        |
| `execution_time`  | BIGINT   | NULL | -        | 执行时间(毫秒)           | <br />       |
| `status`          | INT      | -    | NOT NULL | -                  | 调用状态(0失败1成功) |
| `error_msg`       | TEXT     | -    | NULL     | -                  | 错误信息         |
| `trace_id`        | VARCHAR  | 100  | NULL     | -                  | 追踪ID         |
| `request_id`      | VARCHAR  | 100  | NULL     | -                  | 请求ID         |
| `call_time`       | DATETIME | -    | NULL     | -                  | 调用时间         |
| `create_time`     | DATETIME | -    | NOT NULL | CURRENT\_TIMESTAMP | 创建时间         |

**索引设计**:

| 索引名                | 类型   | 字段             | 说明     |
| ------------------ | ---- | -------------- | ------ |
| `PRIMARY`          | 主键   | `id`           | 主键索引   |
| `idx_service_name` | 普通索引 | `service_name` | 服务查询   |
| `idx_path`         | 普通索引 | `path`         | 路径查询   |
| `idx_status`       | 普通索引 | `status`       | 状态查询   |
| `idx_trace_id`     | 普通索引 | `trace_id`     | 链路追踪   |
| `idx_call_time`    | 普通索引 | `call_time`    | 时间范围查询 |

***

## 四、工作流表设计

### 4.1 wf\_definition (流程定义表)

**表说明**: 存储工作流流程定义,支持BPMN 2.0

**字段设计**:

| 字段名           | 类型       | 长度  | 可空       | 默认值                          | 说明         |
| ------------- | -------- | --- | -------- | ---------------------------- | ---------- |
| `id`          | BIGINT   | -   | NOT NULL | -                            | 主键         |
| `name`        | VARCHAR  | 100 | NOT NULL | -                            | 流程名称       |
| `key`         | VARCHAR  | 100 | NOT NULL | -                            | 流程标识       |
| `category`    | VARCHAR  | 50  | NULL     | -                            | 流程分类       |
| `version`     | INT      | -   | NOT NULL | 1                            | 版本号        |
| `bpmn_xml`    | LONGTEXT | -   | NULL     | -                            | BPMN XML定义 |
| `form_id`     | BIGINT   | -   | NULL     | -                            | 关联表单ID     |
| `status`      | INT      | -   | NOT NULL | 1                            | 状态(0停用1启用) |
| `tenant_id`   | VARCHAR  | 64  | NULL     | -                            | 租户ID       |
| `create_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间       |
| `update_time` | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间       |
| `deleted`     | INT      | -   | NOT NULL | 0                            | 逻辑删除       |

**索引设计**:

| 索引名                     | 类型   | 字段                        | 说明     |
| ----------------------- | ---- | ------------------------- | ------ |
| `PRIMARY`               | 主键   | `id`                      | 主键索引   |
| `uk_key_version_tenant` | 唯一索引 | `key, version, tenant_id` | 流程版本唯一 |
| `idx_tenant_id`         | 普通索引 | `tenant_id`               | 租户隔离   |

***

### 4.2 wf\_instance (流程实例表)

**表说明**: 存储流程运行实例

**字段设计**:

| 字段名             | 类型       | 长度  | 可空       | 默认值                          | 说明     |
| --------------- | -------- | --- | -------- | ---------------------------- | ------ |
| `id`            | BIGINT   | -   | NOT NULL | -                            | 主键     |
| `definition_id` | BIGINT   | -   | NOT NULL | -                            | 流程定义ID |
| `business_key`  | VARCHAR  | 100 | NULL     | -                            | 业务主键   |
| `initiator_id`  | BIGINT   | -   | NOT NULL | -                            | 发起人ID  |
| `status`        | VARCHAR  | 20  | NOT NULL | -                            | 实例状态   |
| `start_time`    | DATETIME | -   | NOT NULL | -                            | 开始时间   |
| `end_time`      | DATETIME | -   | NULL     | -                            | 结束时间   |
| `tenant_id`     | VARCHAR  | 64  | NULL     | -                            | 租户ID   |
| `create_time`   | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP           | 创建时间   |
| `update_time`   | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP ON UPDATE | 更新时间   |
| `deleted`       | INT      | -   | NOT NULL | 0                            | 逻辑删除   |

**实例状态枚举**:

- `RUNNING`: 运行中

- `SUSPENDED`: 已挂起

- `COMPLETED`: 已完成

- `TERMINATED`: 已终止

**索引设计**:

| 索引名                 | 类型   | 字段              | 说明     |
| ------------------- | ---- | --------------- | ------ |
| `PRIMARY`           | 主键   | `id`            | 主键索引   |
| `idx_definition_id` | 普通索引 | `definition_id` | 流程定义查询 |
| `idx_initiator_id`  | 普通索引 | `initiator_id`  | 发起人查询  |
| `idx_status`        | 普通索引 | `status`        | 状态查询   |
| `idx_business_key`  | 普通索引 | `business_key`  | 业务主键查询 |

***

### 4.3 wf\_task (任务表)

**表说明**: 存储待办任务、已办任务

**字段设计**:

| 字段名             | 类型       | 长度  | 可空       | 默认值                | 说明     |
| --------------- | -------- | --- | -------- | ------------------ | ------ |
| `id`            | BIGINT   | -   | NOT NULL | -                  | 主键     |
| `instance_id`   | BIGINT   | -   | NOT NULL | -                  | 流程实例ID |
| `task_name`     | VARCHAR  | 100 | NOT NULL | -                  | 任务名称   |
| `task_key`      | VARCHAR  | 100 | NOT NULL | -                  | 任务标识   |
| `assignee_id`   | BIGINT   | -   | NULL     | -                  | 办理人ID  |
| `status`        | VARCHAR  | 20  | NOT NULL | -                  | 任务状态   |
| `create_time`   | DATETIME | -   | NOT NULL | CURRENT\_TIMESTAMP | 创建时间   |
| `claim_time`    | DATETIME | -   | NULL     | -                  | 签收时间   |
| `complete_time` | DATETIME | -   | NULL     | -                  | 完成时间   |
| `deleted`       | INT      | -   | NOT NULL | 0                  | 逻辑删除   |

**任务状态枚举**:

- `PENDING`: 待办

- `CLAIMED`: 已签收

- `COMPLETED`: 已完成

- `REJECTED`: 已驳回

**索引设计**:

| 索引名               | 类型   | 字段            | 说明    |
| ----------------- | ---- | ------------- | ----- |
| `PRIMARY`         | 主键   | `id`          | 主键索引  |
| `idx_instance_id` | 普通索引 | `instance_id` | 实例查询  |
| `idx_assignee_id` | 普通索引 | `assignee_id` | 办理人查询 |
| `idx_status`      | 普通索引 | `status`      | 状态查询  |

***

## 五、数据库设计规范

### 5.1 命名规范

| 对象       | 规范             | 示例                         |
| -------- | -------------- | -------------------------- |
| **表名**   | 全小写,下划线分隔,前缀分类 | `sys_user`, `wf_instance`  |
| **字段名**  | 全小写,下划线分隔      | `user_name`, `create_time` |
| **主键索引** | PRIMARY        | `PRIMARY`                  |
| **唯一索引** | uk\_字段名        | `uk_username`              |
| **普通索引** | idx\_字段名       | `idx_dept_id`              |
| **外键约束** | fk\_表名\_字段名    | `fk_user_dept_id`          |

### 5.2 字段设计规范

| 规范项      | 要求                                                                                      |
| -------- | --------------------------------------------------------------------------------------- |
| **主键**   | 统一使用`id` BIGINT,雪花算法生成                                                                  |
| **创建时间** | `create_time` DATETIME NOT NULL DEFAULT CURRENT\_TIMESTAMP                              |
| **更新时间** | `update_time` DATETIME NOT NULL DEFAULT CURRENT\_TIMESTAMP ON UPDATE CURRENT\_TIMESTAMP |
| **逻辑删除** | `deleted` INT NOT NULL DEFAULT 0 (0未删1已删)                                               |
| **乐观锁**  | `version` INT NOT NULL DEFAULT 0                                                        |
| **状态字段** | `status` INT,枚举值定义清晰                                                                    |
| **租户ID** | `tenant_id` VARCHAR(64),多租户必须                                                           |
| **字符集**  | utf8mb4,支持emoji和生僻字                                                                     |

### 5.3 索引设计规范

| 规范项      | 要求                    |
| -------- | --------------------- |
| **主键索引** | 每张表必须有主键              |
| **唯一索引** | 业务唯一字段建立唯一索引          |
| **查询索引** | 高频查询字段建立普通索引          |
| **外键字段** | 关联查询字段建立索引            |
| **复合索引** | 遵循最左前缀原则              |
| **索引长度** | VARCHAR字段索引长度不超过767字节 |

### 5.4 数据类型选择

| 数据类型         | 使用场景          |
| ------------ | ------------- |
| **BIGINT**   | 主键、ID类字段      |
| **INT**      | 状态、枚举、计数类字段   |
| **VARCHAR**  | 变长字符串(姓名、标题等) |
| **TEXT**     | 长文本(描述、内容等)   |
| **DATETIME** | 日期时间(精确到秒)    |
| **DATE**     | 日期(年月日)       |
| **DECIMAL**  | 金额、精确小数       |
| **JSON**     | 动态字段、扩展属性     |

***

## 六、数据迁移管理

### 6.1 Flyway迁移脚本

**迁移脚本命名规范**: `V{版本号}__{描述}.sql`

**已有迁移脚本清单** (V1-V20):

| 版本  | 文件名                                            | 说明                       |
| --- | ---------------------------------------------- | ------------------------ |
| V1  | `V1__init_schema.sql`                          | 初始化核心表结构                 |
| V2  | `V2__seed_system_permissions.sql`              | 系统权限初始化数据                |
| V3  | `V3__add_username_password_to_sys_user.sql`    | 用户表增加username/password   |
| V4  | `V4__seed_audit_log_permissions.sql`           | 审计日志权限数据                 |
| V5  | `V5__seed_online_user_permission.sql`          | 在线用户权限数据                 |
| V6  | `V6__create_dept_and_data_permission.sql`      | 部门表与数据权限                 |
| V7  | `V7__add_call_time_to_api_call_log.sql`        | API日志增加call\_time        |
| V8  | `V8__seed_admin_user_and_fix_menus.sql`        | 管理员用户与菜单修复               |
| V9  | `V9__add_birthday_onboarding_to_sys_user.sql`  | 用户表增加birthday/onboarding |
| V10 | `V10__add_super_admin_role.sql`                | 超级管理员角色                  |
| V11 | `V11__fix_menu_status_and_seed_monitor.sql`    | 菜单状态修复与监控菜单              |
| V12 | `V12__drop_audit_log.sql`                      | 删除旧审计日志表                 |
| V13 | `V13__seed_api_call_log_permissions.sql`       | API日志权限数据                |
| V14 | `V14__add_tenant_id_to_core_tables.sql`        | 核心表增加tenant\_id          |
| V15 | `V15__complete_tenant_isolation_schema.sql`    | 完整租户隔离架构                 |
| V16 | `V16__add_tenant_id_to_job_tables.sql`         | 定时任务表增加tenant\_id        |
| V17 | `V17__create_sys_tenant.sql`                   | 创建租户表                    |
| V18 | `V18__add_role_data_scope.sql`                 | 角色数据范围                   |
| V19 | `V19__enforce_department_tenant_integrity.sql` | 部门租户完整性约束                |
| V20 | `V20__create_workflow_engine_tables.sql`       | 工作流引擎表                   |

### 6.2 迁移策略

**开发环境**:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    clean-disabled: true
```

**生产环境**:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false  # 生产禁止baseline
    clean-disabled: true         # 禁止clean
    validate-on-migrate: true    # 迁移前验证
```

***

## 七、数据库性能优化

### 7.1 查询优化建议

1. \*\*避免SELECT \*\*\*

   - 明确指定需要的字段

   - 减少网络传输和内存占用

2. **合理使用索引**

   - 高频查询字段建立索引

   - 避免在索引列上使用函数

   - 复合索引遵循最左前缀

3. **分页查询优化**

   - 使用`id > last_id`代替`LIMIT offset, size`

   - 深分页使用延迟关联

4. **JOIN优化**

   - 小表驱动大表

   - 关联字段建立索引

   - 避免多表复杂JOIN

### 7.2 表结构优化

1. **数据类型选择**

   - 整型优于字符型

   - 固定长度优于变长

   - 合理使用TEXT/BLOB

2. **字段设计**

   - 避免NULL值(使用默认值)

   - 拆分大字段到扩展表

   - 冷热数据分离

3. **分区表** (可选)

   - 日志表按月分区

   - 提升查询和归档效率

### 7.3 监控指标

| 指标         | 阈值    | 说明   |
| ---------- | ----- | ---- |
| **慢查询**    | > 1s  | 需要优化 |
| **连接池使用率** | > 80% | 考虑扩容 |
| **锁等待**    | > 5s  | 检查事务 |
| **表空间使用率** | > 80% | 考虑归档 |

***

## 八、数据安全

### 8.1 敏感数据保护

| 字段       | 保护措施          |
| -------- | ------------- |
| **密码**   | BCrypt加密,禁止明文 |
| **手机号**  | 脱敏显示,日志脱敏     |
| **身份证号** | 加密存储,权限控制     |
| **银行卡号** | 加密存储,日志脱敏     |

### 8.2 数据备份策略

**备份频率**:

- 全量备份: 每天凌晨2点

- 增量备份: 每小时一次

- Binlog备份: 实时同步

**保留策略**:

- 全量备份保留30天

- 增量备份保留7天

- Binlog保留7天

### 8.3 数据权限控制

**数据库账号权限**:

- 应用账号: SELECT/INSERT/UPDATE/DELETE

- 运维账号: ALL PRIVILEGES

- 只读账号: SELECT (报表/数据分析)

**多租户隔离**:

- 所有查询强制tenant\_id过滤

- MyBatis-Plus拦截器自动注入

- 防止跨租户数据泄露

***

## 九、参考资料

### 9.1 内部文档

- [系统架构设计文档](/.trae/documents/系统架构设计文档.md)

- [配置文件完整性检查报告](/.trae/documents/配置文件完整性检查报告.md)

### 9.2 外部参考

- [MySQL 8.0官方文档](https://dev.mysql.com/doc/refman/8.0/en/)

- [Flyway官方文档](https://flywaydb.org/documentation/)

- [数据库设计规范最佳实践](https://www.postgresql.org/docs/current/ddl.html)

***

**文档维护者**: 架构团队\
**审核状态**: 待人工审核\
**下次更新**: 表结构变更后同步更新
