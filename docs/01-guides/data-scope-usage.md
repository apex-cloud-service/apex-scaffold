# 自定义数据权限使用指南(MyBatis-Plus 拦截器)

> **配套架构图**: [数据权限过滤流程图](../diagrams/40_data_scope_filter_flow.svg)

## 功能说明

自定义数据权限用于控制"当前用户能看哪些数据"（行级数据隔离），由 MyBatis-Plus
内部拦截器（`DataScopeInnerInterceptor`）在 SELECT 执行前**自动注入过滤条件**，
业务代码无需手动拼接 `dept_id` / `user_id`。支持五种数据权限类型：

| 类型      | 说明         | 注入条件                       |
| ------- | ---------- | -------------------------- |
| 全部数据权限  | 不限制（超级管理员） | 不注入                        |
| 本部门数据权限 | 只能看本部门数据   | `dept_id = 当前部门`           |
| 本部门及以下  | 看本部门及全部子部门 | `dept_id IN (本部门, 子部门...)` |
| 仅本人数据权限 | 只能看自己的数据   | `user_id = 当前用户`           |
| 自定义数据权限 | 按指定部门集合可见  | `dept_id IN (指定部门)`        |

> 完整架构与设计说明见 [DATA\_SCOPE.md](./data-scope-reference.md)；本文档为使用手册。

## 快速开始

### 第一步：给查询方法加注解

在需要数据权限过滤的 Service 方法上标注 `@DataScope`：

```java
import com.apex.scaffold.common.annotation.DataScope;

@Service
public class OrderService {

    /**
     * 订单分页（按当前用户数据权限自动过滤）。
     * MyBatis-Plus 自动生成的 FROM 无别名，无需指定 deptAlias。
     */
    @DataScope
    public Mono<PageResponse<OrderDTO>> pageOrders(int page, int size) {
        return orderRepository.searchForAdministration(page, size);
    }
}
```

多表 JOIN 查询必须指定部门字段所在表的别名：

```java
@DataScope(deptAlias = "o", userAlias = "o")
public Mono<PageResponse<OrderDTO>> pageOrdersWithDept(int page, int size) {
    // SELECT o.* FROM sys_order o LEFT JOIN sys_dept d ON o.dept_id = d.id ...
}
```

### 第二步：确认用户数据权限范围来自哪里

拦截器通过 `DataScopeProvider` 解析当前用户的数据权限范围。默认实现
`MdcDataScopeProvider` 依次尝试：

1. `DataScopeContext`（ThreadLocal）—— 切面/业务代码显式装配，优先级最高；
2. MDC 键：`apex.userId` / `apex.deptId` / `apex.dataScope` / `apex.dataScopeDeptIds`
   （认证过滤器已将用户 ID 与部门 ID 写入 exchange attributes，可据此接入）。

### 第三步：按需定制装配器

定义同名 `DataScopeProvider` Bean 覆盖默认实现，例如基于角色表
`data_scope` 字段 + `sys_role_dept` 关联表的完整实现：

```java
@Configuration
public class DataScopeConfig {

    @Bean
    public DataScopeProvider dataScopeProvider(RoleDataScopeService roleDataScopeService) {
        return () -> SecurityUtils.currentUser()
                .map(user -> new DataScopeInfo(
                        roleDataScopeService.resolveType(user.getId()),  // 类型（1-5）
                        user.getId(),                                    // 用户 ID
                        user.getDeptId(),                                // 部门 ID
                        roleDataScopeService.resolveDeptIds(user.getId()))); // 可见部门集合
    }
}
```

> 提示：`DataScopeInfo.ofDept(deptId)` / `ofDeptAndChild(deptId, children)` /
> `ofSelf(userId)` / `ofCustom(deptIds)` / `all()` 静态工厂可快速构造。

## 配置项说明（@DataScope 注解）

| 参数        | 类型     | 必填 | 说明                                   | 示例             |
| --------- | ------ | -- | ------------------------------------ | -------------- |
| deptAlias | String | 否  | 部门字段所在表的别名（多表 JOIN 必填，防列歧义；无别名单表可留空） | `"o"`          |
| userAlias | String | 否  | 用户字段所在表的别名（仅本人数据权限使用）                | `"o"`          |
| deptField | String | 否  | 部门字段名（默认 `dept_id`）                  | `"org_id"`     |
| userField | String | 否  | 用户字段名（默认 `user_id`）                  | `"creator_id"` |

## 数据权限类型装配示例（DataScopeInfo）

| 场景     | 装配代码                                                                                           |
| ------ | ---------------------------------------------------------------------------------------------- |
| 全部     | `DataScopeContext.setDataScope(DataScopeInfo.all())`                                           |
| 本部门    | `DataScopeContext.setDataScope(DataScopeInfo.ofDept(100L))`                                    |
| 本部门及以下 | `DataScopeContext.setDataScope(DataScopeInfo.ofDeptAndChild(100L, List.of(100L, 101L, 102L)))` |
| 仅本人    | `DataScopeContext.setDataScope(DataScopeInfo.ofSelf(42L))`                                     |
| 自定义    | `DataScopeContext.setDataScope(DataScopeInfo.ofCustom(List.of(1L, 2L)))`                       |

## 数据模型要求

* `sys_user.dept_id`：用户所属部门（V6 迁移已建列，`UserEntity` / `UserPO` 已映射）；

* `sys_dept`：部门树（`parent_id` 自关联 + `ancestors` 祖级链，供"本部门及以下"
  递归解析子部门集合）；

* 未分配部门（`dept_id = NULL`）的用户默认不做过滤（放行），**生产环境应保证
  关键用户均归属部门**。

## 注意事项

1. **只过滤 SELECT**：拦截器仅处理查询，增删改不受影响；
2. **多表 JOIN 必须指定别名**：未指定 `deptAlias` 时按主查询第一张表注入裸列名，
   多表场景可能列歧义，请显式配置；
3. **子查询 FROM 保守跳过**：`FROM (SELECT ...)` 结构不注入，避免误伤；
4. **解析失败放行**：SQL 无法解析/改写时记录 WARN 并返回原 SQL，绝不阻断业务；
5. **与分页兼容**：PageHelper 的 count 查询同样经过拦截器，分页总数与数据范围一致；
6. **响应式线程模型**（重要）：本脚手架仓库层阻塞查询经 `BlockingIoSchedulers.jdbc()`
   切换到独立 JDBC 线程执行，**请求线程装配的 ThreadLocal/MDC 不会自动到达 JDBC 线程**。
   拦截器在"装配与查询同线程"路径完整生效（H2 端到端测试验证）；响应式链路要生效，
   任选其一：

   * 在阻塞调用内部显式装配（推荐）：

     ```java
     return Mono.fromCallable(() -> {
         DataScopeContext.setDataScope(DataScopeInfo.ofDept(deptId));
         try {
             return mapper.selectList(wrapper);
         } finally {
             DataScopeContext.clear();
         }
     }).subscribeOn(BlockingIoSchedulers.jdbc());
     ```

   * 将 JDBC 调度器替换为 ThreadLocal 透明实现（Spring Framework 6.2
     `BlockingIoSchedulers` / Reactor `Hints.THREAD_LOCAL_TRANSPARENT`），
     使请求线程 ThreadLocal 自动传播到 JDBC 线程（同时惠及 `TenantContext`、
     `DataSourceContext`）。

## 常见问题（FAQ）

**Q1：加了 @DataScope 但没过滤？**
检查三点：① 装配端是否成功解析出数据权限（看日志 `数据权限装配` / `数据权限未装配`）；
② 查询是否在"装配线程"上执行（响应式链路注意第 6 条）；③ SQL 是否多表 JOIN
（未指定 `deptAlias` 时只按主表注入）。

**Q2：如何让管理员看全部数据？**
装配端对管理员角色返回 `DataScopeInfo.all()`（类型 1），拦截器自动跳过注入。

**Q3：自定义字段名（如 org\_id）怎么配？**
`@DataScope(deptField = "org_id", userField = "creator_id")`。

**Q4：会影响未标注的查询吗？**
不会。拦截器对未装配数据权限上下文的查询零开销、零影响。

**Q5：如何验证功能正确？**
参考 `DataScopeE2eH2Test`（H2 + 真实 MyBatis-Plus 端到端），或在开发环境
给测试用户分配 `dept_id` 后观察日志中的注入 SQL。

## 相关代码位置

* 注解：`apex-common/.../common/annotation/DataScope.java`

* 上下文/装配器：`apex-common/.../common/context/`
  `{DataScopeInfo, DataScopeConfig, DataScopeContext, DataScopeProvider}.java`

* 类型枚举：`apex-common/.../common/enums/DataScopeType.java`

* 拦截器：`apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/`
  `{DataScopeInnerInterceptor, DataScopeConditionBuilder, DataScopeSqlRewriter, MdcDataScopeProvider}.java`

* 切面：`apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/DataScopeAspect.java`

* 注册：`apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java`

* 演示：`apex-system/src/main/java/com/apex/scaffold/application/service/AdminUserManagementService.java#search`

