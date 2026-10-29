# XXL-JOB 调度接入

## 实现边界

`apex-job-spring-boot-starter` 使用真实 `xxl-job-core:3.1.1`，默认 `apex.job.executor.enabled=false`，不会启动执行器端口或连接中心。五个业务 service POM 已引入 starter。业务 Bean 的 `@XxlJob` 由 XXL Spring 执行器发现；响应式业务应通过 `JobCompletion.await` 等待整个 Publisher 完成，异常和五分钟超时反馈执行失败。

`apex-job-service` 默认监听 8095，通过共享受管 WebClient 固定代理 system 的 `/admin-api/v1/jobs` 九个管理、触发和日志端点。部署配置 `apex.job.system-url` 决定唯一上游，不接受请求指定地址。入口拒绝缺失 Bearer 凭据及非法任务 ID，业务请求字段、令牌、租户和 `system:job:*` 权限继续由 system 验证。仅转发 Authorization 与 Content-Type，不信任客户端租户头。数据库、领域模型和任务日志仍归 system，代理未引入整个 system。

本服务为无状态代理：启动类已排除共享设施传递引入的 Redis/邮件自动配置与共享持久化组件，`/actuator/health` 仅对探针开放且不依赖外部中间件；安全链对 `/admin-api/v1/jobs/**` 放行（凭据校验在上游 system），其余未知路径全部拒绝。

## 启用执行器

在对应业务 service 配置以下属性，令牌应通过部署机密注入：

```yaml
apex:
  job:
    executor:
      enabled: true
      admin-addresses: https://xxl-admin.example.com/xxl-job-admin
      access-token: ${XXL_JOB_ACCESS_TOKEN}
      app-name: apex-system-service
      port: 9999
      log-path: logs/xxl-job
      log-retention-days: 30
    system:
      tenant-id: ${XXL_SYSTEM_TENANT_ID}
      job-id: ${XXL_SYSTEM_JOB_ID}
```

中心地址必须为无内嵌凭据的 HTTPS 地址，通信令牌至少32字符。生产需配置有效证书、中心与执行器双向网络可达、持久日志目录和不同服务的执行器端口。

`apexSystemJob` 固定执行部署配置指定租户的一个 system 任务。XXL 参数不能切换任务或租户。仅这个任务停止本地动态 cron，其余动态任务继续本地调度；Outbox、安全维护及其他本地调度未迁移。任务方法必须声明 `@ManagedJob`，任意 bean 方法不能执行。

XXL 中心任务和 cron 需要管理员手工配置；当前未自动同步 system cron/状态或创建中心任务。配置接管前应确认 task ID、租户、白名单和 XXL 中心任务一致。system 手工触发沿用原行为，包括可执行未启用任务，禁用业务任务时还需在 XXL 中心停用对应任务。

## workflow 对接状态

starter 提供固定 `apexWorkflowTimeout` 桥接；`apex.job.executor.enabled=true` 与 `apex.job.workflow.enabled=true` 同时开启时，桥接调用 `WorkflowTimeoutHandler.scanTimeoutTasks()`，本地扫描入口停止执行。只开启其中一个开关仍保留默认本地扫描（初始延迟一分钟，固定延迟五分钟）。扫描 Publisher 延迟至订阅才查询，并等待所有到期任务；单项错误在其他任务处理结束后传播，XXL 不会将部分失败记录为成功。

扫描互斥仅覆盖当前进程，重叠扫描反馈失败，完成、失败或取消均释放互斥；这不是跨实例锁或业务幂等保证。多实例应使用 XXL 单机路由与串行阻塞策略，并核对业务并发控制。取消无法保证已提交业务写入回滚。`ESCALATE` 当前转办给管理员 ID=1，`AUTO_COMPLETE` 调用现有自动审批逻辑，`NOTIFY` 仅记录日志、尚未发送通知，未知策略反馈失败。此实现没有原注释曾声称的超时指标计数器。

system 保存前与执行时均使用 `JobInvoker` 校验 `@ManagedJob` 方法白名单。已有可执行入口为 `searchIndexReconciliationJob.manualSync('user')`（也可选 role/menu/dept/dict/config），前提是 `meilisearch.enabled=true` 且索引端口已装配；该方法返回完整 Publisher 并传播失败。`toString()` 等未标注方法以及不存在的 Bean 均拒绝保存和执行。workflow 白名单是固定类型上的 `scanTimeoutTasks()`，不开放 bean 名称或任意方法配置，不需要跨模块引入 system 的注解。system 接管只影响开启执行器且配置正数、匹配任务 ID 的动态 cron，未匹配任务保持本地执行。搜索索引原有凌晨两点本地扫描仍存在，安排手动索引任务时应避免与其重叠。

## 聚合构建对接

根 reactor 已注册 `apex-job-spring-boot-starter` 与 `apex-job-service`，根统一管理 XXL-JOB 3.1.1 和内部 starter 版本，services BOM 管理 SpringDoc。Compose 包含任务代理和可选 `xxl-job` 中心 profile；中心完整外部配置、官方建表、HTTPS 与执行器网络仍由部署环境准备。历史定向验证如下，统一门禁结果以本次交付报告为准。

## 实际验证与限制

每次构建均先 `source scripts/build/init-build-env.sh`，使用 JDK 21.0.13.0+9-tem 和 mvnd 2.0.0-rc-3。

- starter `spotless:apply test`：4项测试通过，覆盖默认禁用、完整 Publisher 的末尾失败、固定 system/workflow 桥接失败传播。
- job-service `spotless:apply test`：2项测试通过，覆盖凭据缺失、路径白名单、非法 ID，以及无外部数据库/Redis/XXL 中心的随机端口启动。九端点 OpenAPI 补全后的再次检查见交付报告。
- system 定向测试 `JobInvokerCompletionTest,JobInvokerTenantTest,JobSchedulerTenantTest`：10项通过；最新单任务接管条件的再次检查见交付报告。

最终 starter 已执行 `install -Djacoco.skip=true -Dpmd.skip=true` 成功，五业务 service 及依赖执行 `compile -DskipTests` 成功。上述跳过选项不构成完整质量门禁。九端点 OpenAPI 补全后 job-service 两项测试再次通过，最新 system 修改后10项定向测试再次通过，`git diff --check` 通过。

本轮完善后执行：

- `mvnd -T4 -B -pl apex-system,apex-workflow -am test -Dtest=ManagedJobBehaviorTest,JobServiceTest,JobInvokerCompletionTest,JobInvokerTenantTest,JobSchedulerTenantTest,WorkflowTimeoutHandlerTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`：system 29 项、workflow 5 项通过。随后新增 Spring 代理白名单行为用例，对 `ManagedJobBehaviorTest,JobSchedulerTenantTest` 再次定向执行，10 项通过（Failures: 0 / Errors: 0）。
- starter `test spotless:check -Djacoco.skip=true`：5 项通过，包括缺失/零值/负数任务 ID 拒绝、默认禁用与失败传播。
- 格式化仅指定本次 Java 文件；system/workflow 模块全量 `spotless:check` 通过，`git diff --check` 通过，未全仓格式化。

上述为历史定向单元测试；本次全仓门禁结果见 [交付报告](../08-project-management/reports/service-enhancement-delivery-report.md)。历史验证中工作流仓储与业务服务使用 mock，覆盖真实 Publisher 的等待、延迟错误、接管开关与扫描互斥；system 使用真实索引任务和 JobInvoker，索引端口使用 mock。未验证真实 XXL 中心通信、真实 system JWT/权限链、真实 workflow 数据库业务执行、五业务服务最终集成启动或完整 `clean verify`。JaCoCo 被显式跳过，PMD 和完整 Failsafe 门禁不能由上述结果替代。
