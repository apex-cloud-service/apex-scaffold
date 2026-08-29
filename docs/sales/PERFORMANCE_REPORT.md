# Apex Scaffold 性能测试报告

**版本**: v1.1（含本机沙箱基线实测）  
**状态**: ✅ 含 2026-08-28 实测基线（沙箱环境）；正式交付建议按 §5 在目标环境复核  
**适用版本**: Apex Scaffold v1.2.0（Java 17 / Spring Boot 3.5 / WebFlux / Netty）

---

## 1. 测试目标

评估脚手架核心链路的吞吐与延迟基准，为容量规划与采购评审提供依据。
覆盖：框架基线（健康检查）、认证链路（登录）、典型读写链路（列表查询/写操作）。

## 2. 测试方法

- 工具: [wrk](https://github.com/wg/wrk) 4.2（HTTP 基准工具），脚本 `scripts/bench/bench.sh`
- 场景: 固定并发梯度（100/300/500），时长 20s，统计 Requests/sec、延迟分布（Avg/P50/P99）、错误率
- 指标采集: 服务端 Prometheus `apex_api_*`（QPS/P95/错误率）+ Grafana 大盘
- 复现: `./scripts/bench/bench.sh <base_url> <duration> <threads> <connections>`
- ⚠️ 默认配置下的压测会**先触发安全防护**（见 §4.4），属预期行为；测框架极限需
  `APEX_SECURITY_IP_RATE_LIMIT_DEFAULT_LIMIT` 提限 + `APEX_SENTINEL_SYSTEM_RULES_ENABLED=false`

## 3. 测试环境

### 3.1 本机沙箱（2026-08-28 实测）

| 项 | 值 |
|----|----|
| 应用 | Apex Scaffold v1.2.0（enterprise 档，dev profile） |
| 硬件 | macOS 12 核 / 32G（共享机器） |
| 并发占用 | **同机运行 9 个中间件容器**（MySQL/Redis/Nacos/RocketMQ/Sentinel/Zipkin/Prometheus/Grafana/Blackbox）+ 开发档 P6Spy SQL 日志 |
| JDK | 17.0.12（Oracle） |
| 说明 | 数字为**资源争用环境下限**，不代表生产档极限；用于验证压测链路可用与防护行为 |

## 4. 基线记录

### 4.1 健康检查（GET /api/v1/health，框架基线，防护关闭）

| 并发 | Requests/sec | Avg | P50 | P99 | 错误率 |
|------|-------------|-----|-----|-----|--------|
| 100 | 292.5 | 347ms | 317ms | 1.00s | 0% |
| 300 | 275.8 | — | 1.09s | 1.77s | 0% |
| 500 | 184.4 | — | 1.66s | 2.00s | 0% |

> 解读：沙箱环境下健康检查吞吐 ~200-290 req/s（同机 9 容器争抢 CPU）；
> 生产独立资源 + 生产档（无 P6Spy）预期显著高于此值，请按 §5 复核。

### 4.2 认证链路（POST /api/v1/auth/login）

| 并发 | Requests/sec | Avg | P95 | P99 | 错误率 |
|------|-------------|-----|-----|-----|--------|
| 100 | 待测 | 待测 | 待测 | 待测 | 待测 |
| 200 | 待测 | 待测 | 待测 | 待测 | 待测 |

> 需配置真实账号与验证码策略后执行（`scripts/bench/bench.sh` 已含脚本骨架）。

### 4.3 列表查询（GET /admin-api/v1/users?page=1&size=20）

| 并发 | Requests/sec | Avg | P95 | P99 | 错误率 |
|------|-------------|-----|-----|-----|--------|
| 200 | 待测 | 待测 | 待测 | 待测 | 待测 |

> 需 Bearer Token（先登录获取）。

### 4.4 安全防护行为实测（重要观察）

| 场景 | 现象 | 结论 |
|------|------|------|
| 默认 IP 限流（120 次/60s/单 IP） | 压测 ~99% 请求返回 429 | ✅ 单 IP 恶意刷量被有效拦截（防护实证） |
| Sentinel 系统保护（CPU>80%） | 高并发下开始返回 503 SystemBlockException | ✅ CPU 过载自动兜底保护（防护实证） |
| 关闭防护后 | 全部 2xx，无错误 | ✅ 框架本身在高并发下无异常/内存泄漏迹象 |

## 5. 复核指引（正式交付前）

1. 目标环境（生产档，`SPRING_PROFILES_ACTIVE=prod`）独立部署应用 + MySQL + Redis
2. 关闭安全防护后跑框架基线：`./scripts/bench/bench.sh http://<host>:8080 30s 4 200`
3. 恢复默认防护，验证 429/503 保护行为符合预期（应触发）
4. 回填 §4.2/§4.3，并更新 §3.1 环境信息

## 6. 结论与容量建议

> 待目标环境复核后填写：推荐单实例规格、支撑并发量级、扩容阈值建议。

---
**执行人**: ______　**日期**: ______　**环境哈希**: ______（`uname -a` + `java -version` + 硬件信息）
