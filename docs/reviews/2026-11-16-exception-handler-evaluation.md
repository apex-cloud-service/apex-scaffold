# 注解式异常处理 vs `ErrorWebExceptionHandler` 评估（BE-004）

> 评估日期：2026-11-16 · 范围：`apex-components-parent/apex-system` 的 `GlobalExceptionHandler`、`SentinelExceptionHandler`
> 结论：**维持现状（决策①：不迁移）**，理由与后备方案见 §4、§5。**本次评估不改任何代码。**

## 1. 现状事实（实测）

| 项 | 事实 | 取证 |
| --- | --- | --- |
| 响应式技术栈 | 各服务 POM 均声明 `spring-boot-starter-webflux`；根 POM 无 MVC starter | `grep -n "spring-boot-starter-webflux" --include=pom.xml`；`audit-backend-rules.sh` 第 7 项 |
| 注解式处理器 | `@RestControllerAdvice` 两处：`GlobalExceptionHandler`（14 个 `@ExceptionHandler`，含 `WebExchangeBindException`、`DecodingException`、`ResponseStatusException`）、`SentinelExceptionHandler`（6 个，覆盖 `FlowException`/`BlockException` 等） | `grep -n "@RestControllerAdvice\|@ExceptionHandler" <file>` |
| 已有的函数式兜底 | `apex-gateway` 已实现 `ErrorWebExceptionHandler`（`GatewayExceptionHandler`），仅服务网关层 | `grep -rn "ErrorWebExceptionHandler" --include=*.java` |
| 错误响应契约 | 异常处理器统一返回项目响应体；`WebExchangeBindException` 被显式处理，说明参数校验失败已在契约内 | 同上 |

## 2. 两种机制的定位差异

| 维度 | `@RestControllerAdvice` | `ErrorWebExceptionHandler` |
| --- | --- | --- |
| 归属 | Spring Web（MVC 与 WebFlux 均支持） | Spring Boot WebFlux 的错误派发机制（`WebExceptionHandler` 链末端） |
| 能拿到的上下文 | 方法级 `@ExceptionHandler` 精确匹配异常类型，可直接拿到方法参数、`ServerWebExchange` | 只有一个入口，需自行按异常类型分支；拿到 `ServerWebExchange` 与已解析的 `Exception` |
| 对 `WebExchangeBindException` 等 WebFlux 专用异常 | 支持（本仓正以它处理参数校验失败与解码失败） | 需自行识别并映射 |
| 排序与优先级 | `@Order` 控制多个 Advice 的相对顺序 | 通过 `@Order(-2)` 抢占默认 `DefaultErrorWebExceptionHandler` |
| 与现有契约的耦合 | 强：14+6 个处理器与错误码/报文一一对应 | 需要重写同一套映射，才不至于改变报文 |

## 3. 迁移成本与风险

- **代码量**：两个类共 20 个异常分支需重写为单个 `handle()` 内的 `instanceof` 分支树，约 400+ 行逻辑平移。
- **契约风险（高）**：异常→错误码→HTTP 状态→报文结构的映射一旦错位，直接改变对外错误响应；这正是任务卡写明"评估通过前禁止改动"的原因。
- **收益**：仅在"`@RestControllerAdvice` 在某个场景不生效"时才有意义；当前未见该现象（CI 与 `mvnd verify` 均通过，参数校验与限流异常均有测试覆盖）。
- **回归成本**：需要为 20 个分支各补一条集成断言，等于重做一遍异常测试矩阵。

## 4. 结论（决策①：维持现状）

1. **不改代码**：`@RestControllerAdvice` 在 WebFlux 下工作正常，且是本仓错误契约的事实载体。
2. **不引入第二套兜底**：`apex-system` 不新增 `ErrorWebExceptionHandler`，避免"两套异常映射并存"导致报文歧义；网关层已有该实现属合理分工（网关要处理路由前的错误）。
3. **保留触发条件**：仅当出现「Advice 未捕获、落到默认错误页」的实测案例（附请求与响应报文），才重开本卡，届时按 §5 迁移。

## 5. 后备迁移要点（若将来触发）

- 新增 `@Order(-2)` 的 `ErrorWebExceptionHandler`，**先并行**（Advice 保留）观察一个发布周期，再逐个下线 Advice 分支；
- 错误码映射抽成独立 `ErrorCodeMapper`，两种实现共用，避免漂移；
- 用同一批请求做「迁移前后报文逐字节比对」，差异为零方可切换；
- 单模块验证：`mvnd -pl :apex-system -am verify`，并跑异常分支集成测试。

## 6. 验收记录

| 项 | 结果 |
| --- | --- |
| 是否改动代码 | **否**（仅新增本文档） |
| 契约兼容性判断 | 不迁移 → 契约零变化 |
| 复核依据 | 上表"取证"列命令可复现 |
