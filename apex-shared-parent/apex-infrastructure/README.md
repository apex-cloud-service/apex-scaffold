# Apex Infrastructure

消息中间件能力说明请参阅 [消息中间件使用说明](../docs/01-guides/message-middleware.md)。

## 模块定位

`apex-infrastructure` 是脚手架的基础设施层，提供领域层端口（仓储/网关/端口）的技术实现，以及跨领域的技术能力。

## 包结构

| 包 | 说明 |
|---|---|
| `persistence` | MyBatis-Plus 持久化实现（PO / Mapper / Converter / RepositoryImpl） |
| `cache` | 多级缓存实现（Caffeine 本地缓存 + Redis 远程缓存）与缓存一致性 |
| `storage` | 对象存储 Provider（Local / MinIO / TOS），通过 SPI 可插拔 |
| `remote` | 外部服务客户端（微信、创蓝短信、远程 API 调用封装、API 调用日志） |
| `event` | 领域事件总线实现 + Outbox 模式可靠投递 |
| `mq` | 消息队列封装（RocketMQ 生产者/消费者、死信队列处理） |
| `sms` | 短信服务实现（创蓝短信网关，可插拔） |
| `moderation` | 内容审核基础设施（图文审核对接、人工复核任务） |

## 设计原则

- **实现细节下沉**：所有技术细节（SQL、缓存、第三方 SDK）都封装在本模块，上层只面向领域端口编程。
- **可替换性**：存储、短信、消息队列等通过 SPI / 配置项切换实现，不侵入业务代码。
- **可观测性**：API 调用日志、操作审计、指标埋点统一在本模块实现。

## 使用方式

1. 领域层定义端口接口，本模块提供 `@Repository` / `@Component` 实现；
2. 通过 `@ConditionalOnProperty` 控制能力开关（如存储类型、短信渠道）；
3. 数据库迁移脚本位于 `src/main/resources/db/migration/`，由 Flyway 管理。

## 模块依赖

依赖 `apex-common` 与 `apex-domain`。禁止反向依赖其他模块。
