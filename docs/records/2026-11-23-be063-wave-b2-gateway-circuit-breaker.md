# BE-063 Wave B2 交接：网关生产档熔断接线

## 范围

- 基线：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；工作区既有改动保留。
- 仅调整 gateway `application-prod.yml` 中 auth/workflow 路由既有熔断器接线，并加强 `GatewayRouteProfileConfigTest`。
- 不改 URI、Path、route id、阈值、TimeLimiter、fallback 响应、Nacos 配置、部署和接口。

## 改动清单

- prod auth 路由使用 `authService` 并转发 `forward:/__gateway/fallback`。
- prod workflow 路由使用 `workflowService` 并转发同一 fallback。
- 三档配置（基础、prod、Nacos）分别断言两条路由具备正确 filter、实例名和 fallback URI。

## 验证证据

- 固定 JDK `21.0.13.0+9-tem` / mvnd `2.0.0-rc-3` 执行：`mvnd -B -pl :apex-gateway -am test spotless:check`。
- BUILD SUCCESS；`apex-common` 593、`apex-gateway` 171 tests，失败 0、错误 0、跳过 0；Spotless 通过。
- 该测试静态解析三个配置源，不启动服务或触发生产熔断。
- `verify-docs.sh --strict` 通过（0 项问题）；`check-doc-refs.sh --strict` 通过（0 项）；`audit-backend-rules.sh --strict` 无阻断项；`git diff --check` 通过。
- 未验证真实生产配置中心覆盖、下游故障熔断、阈值/SLO、流量切换或故障恢复。

## 影响面

生产 profile 下 auth/workflow 路由重新接入基础配置已声明的熔断保护。阈值和 fallback 语义未变；运行行为尚需部署环境演练确认。

## 复核与回滚

独立 R5 Pauli 给出 `APPROVE_WITH_NITS`，无必改项；建议测试筛选 CircuitBreaker 后断言恰有一个，避免正确项掩盖重复过滤器。本波按治理规则放行，后续测试加固可单独登记。未暂存、提交、推送或部署。回滚仅移除 prod 两处过滤器和本波对应测试断言，保留其他用户改动。
