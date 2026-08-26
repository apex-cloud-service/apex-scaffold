# Apex Domain Module

## 模块定位

`apex-domain` 是脚手架的领域层，承载纯领域模型与领域能力定义，不依赖任何技术框架（无 Spring Bean、无 MyBatis 注解）。

## 包结构

| 包 | 说明 |
|---|---|
| `model/entity` | 领域实体（用户、角色、权限、文件、审计、订阅、信用、举报、黑名单等） |
| `model/valueobject` | 值对象（手机号、微信 OpenID 等） |
| `model/moderation` | 内容审核领域模型（决策、场景、状态） |
| `repository` | 仓储接口（领域层只定义接口，实现位于基础设施层） |
| `service` | 领域服务接口与端口（Token、缓存、短信、存储、事件发布等） |
| `gateway` | 外部网关端口（微信、创蓝短信、内容审核等，可插拔 SPI） |
| `event` | 领域事件（用户注册/登录、信用变动、订阅变更等）与事件总线 |
| `enums` | 通用枚举（登录方式、存储类型、用户阶段等） |

## 设计原则

- **零业务耦合**：本模块仅包含通用领域能力，不含任何特定业务语义。
- **依赖倒置**：仓储/网关/端口均为接口，具体实现由 `apex-infrastructure` 提供。
- **可插拔**：外部依赖（微信/短信/对象存储）通过 SPI 端口解耦，可按需替换实现。

## 使用方式

新增领域能力时：

1. 在 `model/entity` 定义实体（继承 `BaseEntity` 获得雪花 ID / 乐观锁 / 逻辑删除）；
2. 在 `repository` 定义仓储接口；
3. 在 `service` 或 `port` 定义领域服务接口；
4. 在 `apex-infrastructure` 提供 MyBatis-Plus 实现。

## 模块依赖

仅依赖 `apex-common`。禁止反向依赖其他模块。
