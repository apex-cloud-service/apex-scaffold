# 批次 C0–C2 测试报告：DDD 基线、starter JAR 级裁剪、api-contract 去死代码

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 C0/C1/C2（M3 组件化服务化首批）
- 执行日期：2026-11-01
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；下一批 C7（业务库组件入口自动装配）

## 1. 已交付变更

### C0 DDD 基线

- 新增 [限界上下文映射](../../../02-architecture/context-map.md)：6 个上下文清单（含聚合根示例）、上下文关系图（Mermaid）、合作模式表（客户-供应商 / 防腐层 / 事件驱动 / 共享内核）、编码约束与聚合战术示范基线
- 新增 `ContextBoundaryArchTest`（`apex-system` 测试）：**源码级扫描**各业务库 main 目录 import 行，断言上下文之间无直接实现依赖
  - 实现方式选择说明：最初尝试 ArchUnit 字节码导入，但**没有任何 Maven 模块同时依赖多个业务库**（架构上跨上下文编译期即不可见），单模块测试无法导入其它上下文类；故改为源码文本扫描，效果等价且可在单模块内覆盖全部上下文
  - 首次运行即通过 → 证明现有代码跨上下文零实现耦合（既有耦合全部经 `domain/gateway` 契约与 `/internal/v1/**` HTTP 防腐层）

### C1 starter JAR 级裁剪

| starter | 变更 |
| --- | --- |
| `apex-job-spring-boot-starter` | `xxl-job-core` 改 `<optional>true</optional>`；`JobExecutorAutoConfiguration` 加 `@ConditionalOnClass(name = "com.xxl.job.core.executor.impl.XxlJobSpringExecutor")`（**字符串形式**，避免类缺失时加载期 `NoClassDefFoundError`） |
| `apex-seata-spring-boot-starter` | `seata-all` 改 `<optional>true</optional>`；`SeataAutoConfiguration` 加 `@ConditionalOnClass(name = "org.apache.seata.spring.annotation.GlobalTransactionScanner")` |

效果：消费方在 basic 档可通过依赖排除真正移除实现 JAR（此前只是 Bean 不装配、JAR 常驻 classpath）。

### C2 api-contract 去死代码

- 删除 3 组 `@FeignClient` 契约与 FallbackFactory（`AuthFeignClient`/`SystemFeignClient`/`WorkflowFeignClient` + 3 个 FallbackFactory）
- 删除 `FeignReactiveBridge`、`FeignTenantTraceConfiguration`、`FeignClientConstants`
- 删除 `spring-cloud-starter-openfeign` 依赖
- 删除对应测试 5 个（`FeignReactiveBridgeTest`/`FeignContractSignatureTest`/`FeignClientFallbackFactoryTest`/`FeignTenantTraceConfigurationTest` 等）
- 保留：`contract/dto/internal/**`（`TokenValidationRequest/DTO` 等被 auth-service 内部端点真实使用）、`contract/event/**`（workflow → system 事件真实使用）
- 同步清理两处 javadoc `@link AuthFeignClient` 引用（`InternalAuthService`、`InternalAuthRouter`）

**核实依据**：全仓 `@FeignClient`/`FeignReactiveBridge` 外部消费为零（仅 api-contract 内部与 javadoc 引用），与方案 v1 评审结论一致。

## 2. 测试

| 测试 | 结果 |
| --- | --- |
| `ContextBoundaryArchTest`（新增） | **1/1 通过**（跨上下文零实现依赖） |
| 七模块及上游 `test`（含 api-contract/job/seata starter） | **BUILD SUCCESS** |

## 3. 结果

- `ContextBoundaryArchTest`：1/1 通过。
- 七模块（auth/auth-service/system/gateway/api-contract/job-starter/seata-starter）及上游 `test`：**BUILD SUCCESS**。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。

## 4. 未验证项与边界

- **JAR 级裁剪的实证构建未执行**：方案 C1 要求"以排除依赖构建 apex-file-service 通过 clean verify"作为裁剪实证，本批只完成 POM/注解改造与常规构建；实证构建（basic 档排除 xxl/seata 后启动）列为 C7 批次验证项。
- **既有缓存键迁移**（B9 顺延项）与 **`@RequirePermission` 任一满足语义**（B8 顺延项）仍在待办清单。
- **C0 的"事件必须经端口发布"与"聚合仓储返回类型"两条守卫**未新增（需先梳理现状，避免大面积失败）——列为 C7/C8 实施时补。
- **上下文映射文档**为现行基线快照，跨上下文关系变化时须同步修订（AGENTS.md 文档纪律）。
