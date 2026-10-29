# 缓存专题文档索引

> 本目录集中存放 Apex Scaffold 缓存相关的设计文档、实施方案与运维指南。

## 文档清单

| 文档 | 说明 | 状态 |
|---|---|---|
| [CACHE_OPTIMIZATION_SOLUTION.md](./CACHE_OPTIMIZATION_SOLUTION.md) | 缓存穿透 / 击穿 / 雪崩完整解决方案 | ✅ 已实施 |
| [CACHE_USAGE_GUIDE.md](./CACHE_USAGE_GUIDE.md) | 缓存使用指南与最佳实践 | ✅ 已完成 |
| [CACHE_PRODUCTION_CONFIG.md](./CACHE_PRODUCTION_CONFIG.md) | 生产环境配置示例 | ✅ 已完成 |
| [CACHE_MONITORING_ALERTS.md](./CACHE_MONITORING_ALERTS.md) | 监控告警配置指南 | ✅ 已完成 |
| [CACHE_PERFORMANCE_TUNING.md](./CACHE_PERFORMANCE_TUNING.md) | 性能优化与调优指南 | ✅ 已完成 |
| [GRAFANA_SETUP_GUIDE.md](./GRAFANA_SETUP_GUIDE.md) | Grafana 配置指南 | ✅ 已完成 |
| [GRAFANA_DASHBOARD_OPTIMIZATION.md](./GRAFANA_DASHBOARD_OPTIMIZATION.md) | Dashboard 优化说明 | ✅ 已完成 |

## Grafana Dashboard 配置文件

| 文件 | 说明 | 推荐 |
|------|------|------|
| [grafana-cache-dashboard.json](./grafana-cache-dashboard.json) | 基础版 Dashboard | 适合入门 |
| [grafana-cache-dashboard-optimized.json](./grafana-cache-dashboard-optimized.json) | 优化版 Dashboard | ⭐ 生产推荐 |

## Grafana 导入指南

| 文档 | 说明 | 适用场景 |
|------|------|----------|
| [GRAFANA_QUICK_START.md](./GRAFANA_QUICK_START.md) | 🚀 快速开始 | ⭐ 首次导入必读 |
| [GRAFANA_IMPORT_GUIDE.md](./GRAFANA_IMPORT_GUIDE.md) | 详细导入指南 | 5种导入方法详解 |
| [GRAFANA_SETUP_GUIDE.md](./GRAFANA_SETUP_GUIDE.md) | Grafana 配置指南 | 面板配置和使用 |
| [GRAFANA_DASHBOARD_OPTIMIZATION.md](./GRAFANA_DASHBOARD_OPTIMIZATION.md) | Dashboard 优化说明 | 优化细节和对比 |

## 导入脚本

| 脚本 | 说明 |
|------|------|
| [scripts/ops/monitoring/grafana/import-dashboard.sh](../../scripts/ops/monitoring/grafana/import-dashboard.sh) | 自动化导入脚本 |

## 相关文档（其他目录）

| 文档 | 路径 | 说明 |
|---|---|---|
| 多级缓存完整指南 | [docs/architecture/advanced-features/multi-level-cache-complete-guide.md](../architecture/advanced-features/multi-level-cache-complete-guide.md) | L1 Caffeine + L2 Redis 架构详解 |
| 多级缓存架构 | [docs/architecture/advanced-features/multi-level-cache-architecture.md](../architecture/advanced-features/multi-level-cache-architecture.md) | 架构图与流程 |
| 读降级指南 | [docs/01-guides/read-degradation-guide.md](../01-guides/read-degradation-guide.md) | DB 故障读降级使用说明 |
| 工程规范 | [AGENTS.md](../../AGENTS.md) | 缓存编码、阻塞 I/O、租户隔离等约束 |

## 核心代码

| 组件 | 路径 |
|---|---|
| `MultiLevelCacheService` | [apex-infrastructure-cache/.../cache/service/MultiLevelCacheService.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) |
| `BloomFilterService` ⭐ | [apex-infrastructure-cache/.../cache/service/BloomFilterService.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/BloomFilterService.java) |
| `CachePrewarmService` ⭐ | [apex-infrastructure-cache/.../cache/service/CachePrewarmService.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/CachePrewarmService.java) |
| `LocalCacheService` | [apex-infrastructure-cache/.../cache/service/LocalCacheService.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/LocalCacheService.java) |
| `CacheConsistencyService` | [apex-infrastructure-cache/.../cache/service/CacheConsistencyService.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheConsistencyService.java) |
| `LogicalExpiryWrapper` ⭐ | [apex-infrastructure/.../cache/wrapper/LogicalExpiryWrapper.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/wrapper/LogicalExpiryWrapper.java) |
| `CachePrewarmStrategy` ⭐ | [apex-infrastructure-cache/.../cache/prewarm/CachePrewarmStrategy.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/prewarm/CachePrewarmStrategy.java) |
| `CacheProperties` | [apex-infrastructure-cache/.../cache/config/CacheProperties.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/config/CacheProperties.java) |
| `InFlightRequestMerge` | [apex-infrastructure-cache/.../cache/support/InFlightRequestMerge.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/support/InFlightRequestMerge.java) |
| `CacheLockAspect` | [apex-infrastructure-cache/.../cache/aspect/CacheLockAspect.java](../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/aspect/CacheLockAspect.java) |

> ⭐ 表示本次缓存优化方案新增的组件
