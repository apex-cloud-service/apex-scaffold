# BE-063 Wave B2：网关生产档熔断接线

## 现状与问题

gateway 的 `application.yml` 与 `config/nacos/apex-gateway.yaml` 均为 auth/workflow 路由配置 `CircuitBreaker` filter，分别指向 `authService`、`workflowService`，fallback 为 `forward:/__gateway/fallback`。`application-prod.yml` 整表重定义 `spring.cloud.gateway.routes`，同两条路由缺少 filter，导致生产 profile 路由绕过既有熔断与统一 fallback。当前配置阈值及 resilience4j 实例在基础配置定义，不在本波调整。

## 目标与范围

- 在 prod 的 auth/workflow 两条现有路由补齐与 base/Nacos 一致的 filter 接线。
- 扩充 `GatewayRouteProfileConfigTest`，对 base、prod、Nacos 三个配置源分别断言这两条路由的 filter 名、实例名与 fallback URI。
- 不改变路由标识、URI、Path、顺序、下游服务、熔断阈值、TimeLimiter、重试、fallback 响应、认证及服务发现模式。

## 验收

- gateway `-am test` 与 `spotless:check` 通过。
- 严格文档、引用、后端红线和 diff 空白检查通过。
- 静态契约测试只证明配置文件接线完整，不证明真实生产配置覆盖优先级、服务调用行为、熔断触发阈值或产线故障恢复；这些需在发布验证阶段另行演练。

## 回滚

独立撤回 prod 两个 filter 区块及新增测试断言，不修改其他路由配置。没有数据库、接口或模块迁移。
