# 自定义数据权限（MyBatis-Plus 拦截器）

> 基于 MyBatis-Plus 内部拦截器（`InnerInterceptor`）实现的**自定义数据权限**能力：
> 在 SELECT 执行前自动注入部门/用户过滤条件，业务代码无需手动拼接 `dept_id` / `user_id`。
>
> 版本：2.3.0+ ｜ 模块：`apex-common`（契约）+ `apex-infrastructure-persistence`（拦截器）+ `apex-system` 的 `interfaces` 包（装配）
>
> 📖 **使用手册**（快速上手/注解参考/FAQ）见 [DATA_SCOPE_USAGE.md](./data-scope-usage.md)；
> 本文档为架构与设计说明。

---

## 1. 功能概览

| 数据权限类型 | 枚举 | 注入的 SQL 条件 | 典型场景 |
|---|---|---|---|
| 全部数据权限 | `DataScopeType.ALL` | 不注入 | 超级管理员 |
| 本部门数据权限 | `DataScopeType.DEPT` | `dept_id = 当前部门` | 部门主管 |
| 本部门及以下 | `DataScopeType.DEPT_AND_CHILD` | `dept_id IN (本部门, 子部门...)` | 含下级部门的角色 |
| 仅本人数据权限 | `DataScopeType.SELF` | `user_id = 当前用户` | 个人数据 |
| 自定义数据权限 | `DataScopeType.CUSTOM` | `dept_id IN (指定部门集合)` | 按角色绑定可见部门 |

核心链路：

```
@DataScope 注解(Service 方法)
   │ DataScopeAspect 装配
   ▼
DataScopeContext(ThreadLocal: DataScopeInfo + DataScopeConfig)
   │ DataScopeInnerInterceptor.beforeQuery 读取
   ▼
DataScopeSqlRewriter(JSQLParser 改写 SQL: 定位目标表 → 注入 WHERE)
   ▼
BoundSql.sql 反射回写 → MyBatis 执行过滤后的查询
```

## 2. 快速上手

### 2.1 单表查询（MyBatis-Plus 自动 SQL）

```java
// sys_user 表查询：自动注入 dept_id 条件（无别名场景，MP 自动生成的 FROM sys_user）
@DataScope
public Mono<PageResponse<UserDTO>> pageUsers(...) {
    return userRepository.searchForAdministration(keyword, status, page, size);
}
```

### 2.2 多表 JOIN 查询（必须指定别名）

```java
// 指定部门/用户字段所在表的别名，避免多表列歧义
@DataScope(deptAlias = "u", userAlias = "u")
public Mono<PageResponse<UserDTO>> pageUserWithDept(...) {
    // SELECT u.* FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.id ...
}
```

### 2.3 自定义字段

```java
// 非标准字段名（如 org_id / creator_id）
@DataScope(deptField = "org_id", userField = "creator_id")
public Mono<List<XxxDTO>> listXxx(...) { ... }
```

### 2.4 编程式装配（不依赖注解切面）

```java
DataScopeContext.setDataScope(
        DataScopeInfo.ofDeptAndChild(deptId, childDeptIds),
        DataScopeConfig.from(dataScopeAnnotation));
// 执行查询... 必须 finally { DataScopeContext.clear(); }
```

## 3. 数据权限范围从哪来？

拦截器通过 `DataScopeProvider` 接口解析当前用户的数据范围，与数据来源解耦。

**默认实现 `MdcDataScopeProvider`**（`apex-infrastructure-persistence`）解析顺序：

1. `DataScopeContext`（ThreadLocal）—— 显式装配（切面 / 业务代码），优先级最高；
2. MDC 键：
   - `apex.userId`：当前用户 ID（`SELF` 类型）
   - `apex.deptId`：当前用户所属部门 ID（`DEPT` 类型，缺省回退）
   - `apex.dataScope`：数据权限类型编码 1-5
   - `apex.dataScopeDeptIds`：可见部门集合（逗号分隔，`DEPT_AND_CHILD` / `CUSTOM`）

认证过滤器（`JwtAuthenticationFilter`）已将用户 ID 与部门 ID 写入 exchange attributes
（`apex.userId` / `apex.deptId`），可供自定义装配端消费。

**替换实现**：定义同名 `DataScopeProvider` Bean 即可覆盖默认装配器，例如基于
`sys_role.data_scope` 字段 + `sys_role_dept` 关联表的完整角色数据权限实现：

```java
@Bean
public DataScopeProvider dataScopeProvider() {
    return () -> currentUser()
            .map(user -> new DataScopeInfo(
                    roleDataScope(user), user.getId(), user.getDeptId(), visibleDeptIds(user)));
}
```

## 4. 数据模型要求

- `sys_user.dept_id`：用户所属部门（V6 迁移已建列；`UserEntity` / `UserPO` 已映射）；
- `sys_dept`：部门树（`parent_id` 自关联 + `ancestors` 祖级链，供 `DEPT_AND_CHILD`
  递归解析子部门集合）；
- 未分配部门（`dept_id = NULL`）的用户默认不做数据权限过滤（放行），
  **生产环境应保证关键用户均归属部门**。

## 5. 响应式线程模型（重要）

本脚手架是 WebFlux 响应式应用，仓库层阻塞查询统一经
`BlockingIoSchedulers.jdbc()` 切换到**独立的 JDBC 线程池**执行：

```java
return Mono.fromCallable(() -> mapper.selectPage(...)).subscribeOn(BlockingIoSchedulers.jdbc());
```

⚠️ 当前 JDBC 调度器为普通 `Schedulers.newBoundedElastic`（无 ThreadLocal 透明传播），
**请求线程装配的 ThreadLocal / MDC 不会自动到达 JDBC 线程**。因此：

1. **拦截器本身**在"装配与查询同线程"的执行路径下**完整生效**（MySQL 端到端测试验证）；
2. **响应式链路**要让拦截器生效，任选其一：
   - 在阻塞调用内部显式装配（推荐，精确）：

     ```java
     return Mono.fromCallable(() -> {
         DataScopeContext.setDataScope(info);           // info 由上游 Reactor Context 传递
         try {
             return mapper.selectList(wrapper);
         } finally {
             DataScopeContext.clear();
         }
     }).subscribeOn(BlockingIoSchedulers.jdbc());
     ```
   - 将 JDBC 调度器替换为 ThreadLocal 透明实现（Spring Framework 6.2
     `BlockingIoSchedulers` / Reactor `Hints.THREAD_LOCAL_TRANSPARENT` 机制），
     使请求线程的 ThreadLocal 自动传播到 JDBC 线程（可同时惠及 `TenantContext`）；
   - 同步执行路径（定时任务、非 WebFlux 调用方、测试）天然生效，无需处理。

拦截器对未装配上下文的查询**零影响**（解析为空即放行），升级改造无风险。

## 6. 边界与安全

- **只注入 SELECT**：`beforeQuery` 仅拦截查询，增删改不受影响；
- **子查询 FROM 表保守跳过**：`FROM (SELECT ...)` 结构不注入，避免误伤子查询；
- **解析失败放行**：SQL 无法解析/改写时记录 WARN 并返回原 SQL，绝不阻断业务；
- **注入值均为服务端强类型数字**（`Long`），无 SQL 注入面；
- **与分页兼容**：PageHelper 的 count 查询同样经过拦截器，分页总数与数据范围一致；
- **多表 JOIN 必须指定别名**：未指定 `deptAlias` 时按主查询第一张表注入裸列名，
  多表场景可能产生列歧义，请显式配置别名。

## 7. 测试覆盖

| 测试 | 覆盖点 |
|---|---|
| `DataScopeTypeTest` / `DataScopeInfoTest` / `DataScopeContextTest`（common） | 枚举、值对象、上下文生命周期与旧 API 兼容 |
| `DataScopeConditionBuilderTest` | 五种类型条件生成、别名/字段配置、缺值降级 |
| `DataScopeSqlRewriterTest` | 真实 JSQLParser 的 SQL 注入：WHERE/无 WHERE/JOIN 别名/UNION/子查询/分组排序分页/解析失败 |
| `DataScopeInnerInterceptorTest` | 完整链路：装配 → 解析 → 改写 → 反射回写 `BoundSql.sql` |
| `DataScopeE2eTest` | MySQL + 真实 MyBatis-Plus：不同范围下查询结果正确过滤 |

## 8. 相关代码位置

- 契约：`apex-common/.../common/annotation/DataScope.java`、
  `common/enums/DataScopeType.java`、`common/context/{DataScopeInfo,DataScopeConfig,DataScopeContext,DataScopeProvider}.java`
- 拦截器：`apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/`
  `{DataScopeInnerInterceptor, DataScopeConditionBuilder, DataScopeSqlRewriter, MdcDataScopeProvider}.java`
- 装配：`apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/DataScopeAspect.java`、
  `interfaces/security/JwtAuthenticationFilter.java`
- 注册：`apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java`
- 演示：`apex-system/src/main/java/com/apex/scaffold/application/service/AdminUserManagementService.java#search`
