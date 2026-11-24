# BE-063 Wave C1 交接：网关断路器唯一性测试

## 范围

按 Wave B2 独立 R5 的非阻断建议，收紧 `GatewayRouteProfileConfigTest` 中 auth/workflow 熔断器断言。三份配置源（base、prod、Nacos）里，每条目标路由必须且只能有一个 `CircuitBreaker`，并保持预期实例名和 fallback URI。

## 改动清单

- 将原先“至少有一个匹配 CircuitBreaker”的断言改为精确一个，防止重复 filter 被测试漏检。
- 不改生产 YAML、route id/URI/Path、阈值、TimeLimiter、重试、fallback 语义或下游依赖。

## 验证证据

- 固定环境：JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`。
- 命令：`mvnd -B -pl :apex-gateway -am test spotless:check`。
- BUILD SUCCESS；`apex-common` 593、`apex-gateway` 171 项，失败 0、错误 0、跳过 0；Spotless 通过。
- `bash scripts/governance/verify-docs.sh --strict`、`bash scripts/governance/check-doc-refs.sh --strict`、`bash scripts/governance/audit-backend-rules.sh --strict` 和 `git diff --check` 通过。
- 本波独立 R5 复核待完成；此测试只断言静态配置，不证明生产熔断故障行为或容量指标。

## 影响面

仅增强网关路由配置测试的重复项检出能力，不改变服务运行行为。真实产线配置覆盖、下游故障触发及切流仍未验证。

## 回滚方式

移除本波唯一性断言并恢复原有配置断言；保留 B2 的配置修正及其他工作区改动。文档索引和台账以追加更正方式同步。

## 独立复核追加

Pauli 独立 R5 对 C1 本波 diff 给出 `APPROVE_WITH_NITS`，无必改项。建议未来补充同一配置源若同时出现两个候选 auth route id 时的数量断言；当前每种配置仅使用一个候选，现有用例按此已知结构有效。本结论只覆盖本波测试及文档，不复核/放行 B1、B2；R5 未重跑构建。
