# Apex Domain Module

## 模块定位

`apex-domain` 是共享领域层，只承载**可跨业务域复用的基础契约与通用能力**，不依赖任何技术框架
（无 Spring Bean、无 MyBatis 注解）。system 等具体业务域的实体、仓储与适配器留在各自业务库，
不放入本模块。

## 包结构

| 包 | 说明 |
|---|---|
| `model/entity` | 共享基础实体（如 `BaseEntity` 提供雪花 ID、乐观锁与逻辑删除）；不含用户、角色、订单等具体业务实体 |
| `service` | 跨域复用的领域服务接口与端口（Token、缓存、短信、事件发布、业务指标等） |
| `gateway` | 外部网关端口（微信、创蓝短信、内容审核等，可插拔 SPI） |
| `event` | 跨域领域事件（如令牌撤销）与事件总线契约 |
| `enums` | 跨域通用枚举 |

具体业务实体（用户、角色、权限、审计、信用、订阅等）位于对应业务库，例如
`apex-system` 的 `com.apex.scaffold.domain.model.entity.UserEntity`。

## 设计原则

- **零业务耦合**：本模块仅包含跨域通用能力，不含任何特定业务语义。
- **依赖倒置**：仓储/网关/端口均为接口，具体实现由各业务库或五个 capability 基础设施 JAR 提供。
- **可插拔**：外部依赖（微信/短信/对象存储）通过 SPI 端口解耦，可按需替换实现。

## 使用方式

新增**跨域复用**能力时：

1. 先确认契约确实被两个及以上业务域使用，再放入 `model/entity`、`service` 或 `gateway`；
2. 在 `service` 或 `gateway` 定义端口接口；
3. 由消费方业务库在自己的 `infrastructure` 包提供实现（system 特性实现留在 `apex-system`）。

单一业务域（如 system）的新增实体与仓储不属于本模块，应直接落在该业务库的 `domain` 包。

## 模块依赖

仅依赖 `apex-common`。禁止反向依赖其他模块。
