# 数据源动态切换使用指南(自定义 @DataSource 注解)

> **配套架构图**: [多数据源切换架构图](diagrams/41_datasource_switch_architecture.svg)

## 功能说明

通过自定义注解 `@DataSource` 在方法（或类）上声明目标数据源，由
`DataSourceSwitchAspect` 切面在方法执行期间自动切换、结束后自动切回，
实现**读写分离 / 多数据源路由**。切换引擎复用 Baomidou dynamic-datasource
（`DynamicRoutingDataSource`），本脚手架在其上提供：

- 自定义 `@DataSource` 注解（方法级优先于类级）；

- 响应式适配（Mono/Flux：订阅时切换、终止时释放，避免 ThreadLocal 泄漏）；

- 切换埋点：`apex.datasource.switch.count{target=数据源名}`；

- 统一切换日志（debug 级）。

```java
// 读操作走从库
@DataSource("slave")
public Mono<UserDTO> getUserFromSlave(Long id) { ... }

// 写操作显式走主库（与类级默认相反时）
@DataSource("master")
public Mono<Void> updateUser(Long id, ...) { ... }
```

## 快速开始

### 第一步：配置数据源（application-\*.yml）

```yaml
spring:
  datasource:
    dynamic:
      primary: master          # 默认数据源
      strict: false            # 未找到指定数据源时回退主库（不抛异常）
      datasource:
        master:                # 主库（写）
          url: ${DB_MASTER_URL:jdbc:mysql://127.0.0.1:3306/apex_data?...}
          username: ${DB_MASTER_USERNAME:root}
          password: ${DB_MASTER_PASSWORD:apex-root}
          driver-class-name: com.mysql.cj.jdbc.Driver
          type: com.alibaba.druid.pool.DruidDataSource
        slave:                 # 从库（读）
          url: ${DB_SLAVE_URL:jdbc:mysql://127.0.0.1:3306/apex_data?...}
          username: ${DB_SLAVE_USERNAME:root}
          password: ${DB_SLAVE_PASSWORD:apex-root}
          driver-class-name: com.mysql.cj.jdbc.Driver
          type: com.alibaba.druid.pool.DruidDataSource
```

dev 环境已内置 `slave` 演示配置（默认指向同一 MySQL 实例，无需额外部署）；
生产环境通过 `DB_SLAVE_URL` 等环境变量指向真实只读副本。

### 第二步：在方法/类上标注注解

```java
@Service
public class ReportService {

    /** 报表查询走从库（方法级） */
    @DataSource("slave")
    public Mono<ReportDTO> getReport(Long id) {
        // 查询必须延迟到订阅期执行（见"注意事项"第 3 条）
        return Mono.fromCallable(() -> reportMapper.selectById(id));
    }
}

/** 类级默认：整个服务走从库 */
@DataSource("slave")
@Service
public class ReadOnlyQueryService {
    public Mono<XxxDTO> query1(...) { ... }
    public Mono<XxxDTO> query2(...) { ... }

    /** 方法级覆盖：本方法走主库 */
    @DataSource("master")
    public Mono<Void> write(...) { ... }
}
```

### 第三步：验证切换

1. 观察 debug 日志：`数据源切换: method=xxx, target=slave`；
2. 查询指标：`apex.datasource.switch.count{target="slave"}`；
3. 路由探针：执行 `scripts/datasource/setup-slave.sh` 初始化主/从探针表
   （`routing_probe.source` 分别为 MASTER/SLAVE），切换后查询返回值即当前数据源。

## 配置项说明（@DataSource 注解）

| 参数    | 类型     | 必填 | 说明                                                               | 示例        |
| ----- | ------ | -- | ---------------------------------------------------------------- | --------- |
| value | String | 否  | 数据源名称（对应 `spring.datasource.dynamic.datasource` 下的键；默认 `master`） | `"slave"` |

> 注：本注解与 Baomidou 内置 `@DS` 等价但为本脚手架自研（响应式适配/埋点/日志），
> 请统一使用本注解；`value()` 为空/空白时使用主库（不执行切换）。

## 脚本与初始化

```bash
# 一键初始化演示从库 + 主/从路由探针表（默认 root/apex-root，可环境变量覆盖）
./scripts/datasource/setup-slave.sh

# 手动执行 SQL
mysql -uroot -p < scripts/datasource/init-slave.sql
```

- `scripts/datasource/init-slave.sql`：创建 `apex_data_slave` 库 + 主/从 `routing_probe` 探针表；

- 生产环境无需执行（指向真实只读副本，仅配置 `DB_SLAVE_*` 环境变量即可）。

## 注意事项

1. **与事务的关系**：切面顺序为最高优先级（`@Order(HIGHEST_PRECEDENCE)`），
   保证先于事务切面执行，连接从正确的数据源获取；同一切面内事务与切换同时存在时，
   切换应发生在事务方法外层；
2. **主从延迟**：读写分离场景写后立即读可能命中未同步的从库，需要强一致的读
   请显式 `@DataSource("master")` 或走主库查询；
3. **响应式语义（重要）**：切换在<em>订阅时</em>生效（切面 `doFirst`），而响应式
   方法体在<em>装配期</em>（订阅前）执行。因此**查询必须延迟到订阅期**：

   - 正确：`Mono.fromCallable(() -> mapper.selectById(id))`（示例模块 `getUserFromSlave` 即此写法）；

   - 错误：方法体直接 `mapper.selectById(id)`（装配期执行，早于切换，查询仍在原数据源）；
4. **响应式线程模型**：切换基于线程上下文（ThreadLocal），要求查询与切换同线程。
   本脚手架主应用仓库层经 `BlockingIoSchedulers.jdbc()` 切换到独立 JDBC 线程，
   **请求线程的切换不会自动到达 JDBC 线程**。响应式链路要生效，任选其一：

   - 在阻塞调用内部显式切换（推荐）：

     ```java
     return Mono.fromCallable(() -> {
         DataSourceContext.push("slave");
         DynamicDataSourceContextHolder.push("slave");
         try {
             return mapper.selectList(wrapper);
         } finally {
             DataSourceContext.pop();
             DynamicDataSourceContextHolder.poll();
         }
     }).subscribeOn(BlockingIoSchedulers.jdbc());
     ```

   - 将 JDBC 调度器替换为 ThreadLocal 透明实现（Spring Framework 6.2
     `BlockingIoSchedulers` / Reactor `Hints.THREAD_LOCAL_TRANSPARENT`），
     使请求线程 ThreadLocal 自动传播到 JDBC 线程（同时惠及 `TenantContext`、
     `DataScopeContext`）；

   - 同步执行路径（定时任务、非 WebFlux 调用方、测试）天然生效；
5. **未找到数据源**：`strict: false` 时回退主库不抛异常；生产建议 `strict: true`
   提前暴露配置错误；
6. **嵌套切换**：`DataSourceContext` 使用切换栈，支持"外层从库、内层报表库"，
   内层结束后自动回到外层数据源。

## 常见问题（FAQ）

**Q1：加了 @DataSource("slave") 但没切到从库？**
检查：① 方法体是否在装配期同步执行了查询（见注意事项第 3 条）；② 查询是否在
JDBC 线程执行且未显式装配（见注意事项第 4 条）；③ slave 数据源是否已配置
（`strict: false` 时缺配置静默回退主库）。

**Q2：与 @DS 有什么区别？**
等价但本注解多了响应式适配、切换埋点与统一日志；功能上二者可混用（切换栈互相同步）。

**Q3：如何确认当前线程正在使用哪个数据源？**
`DataSourceContext.current()`（本脚手架）或 `DynamicDataSourceContextHolder.peek()`（Baomidou）。

**Q4：如何验证功能正确？**
参考 `DataSourceSwitchE2eTest`（双 MySQL schema + DynamicRoutingDataSource 端到端路由验证）
与 `DataSourceSwitchAspectTest`（真实切面代理测试）。

## 相关代码位置

- 注解：`apex-common/.../common/annotation/DataSource.java`

- 上下文：`apex-common/.../common/context/DataSourceContext.java`

- 切面：`apex-interfaces/.../interfaces/aspect/DataSourceSwitchAspect.java`

- 配置：`apex-interfaces/.../resources/application-dev.yml`（slave 演示块）、
  `apex-example/.../resources/application.yml`

- 演示：`apex-example/.../service/ExampleUserService.java#getUserFromSlave`

- 脚本：`scripts/datasource/{setup-slave.sh, init-slave.sql}`

- 测试：`DataSourceContextTest`（common）、`DataSourceSwitchE2eTest`（infra）、
  `DataSourceSwitchAspectTest`（interfaces）

