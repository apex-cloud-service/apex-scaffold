# 2026-11-17 办公日历端口扩展与组织者身份换算方案（BE-038）

## 1. 背景

BE-037 补齐了 feishu/dingtalk/wecom 三个 `CalendarClient`，但真实联调暴露两个契约级限制：

1. `CalendarPort.getEventDetail`/`updateEvent` 只接收 `platformEventId`。飞书与钉钉的事件读写都以「日历」为父资源，缺父日历标识无法定位事件，这两个操作只能显式拒绝。
2. 三个平台的外部接口要求的是**平台侧用户标识**（open_id / unionId / userid），而前端与事件模型传的是系统用户 ID（如 `1001`）。直接透传会把系统 ID 当平台 ID 使用。

系统内已有 `office_organization_mapping` 表（`platform` + `tenant_id` + `apex_user_id` → `external_user_id`），是解决第 2 点的现成数据源，但目前只有 `BaseMapper`，缺查询方法。

## 2. 目标

- 端口方法补父资源标识，使飞书/钉钉的详情、更新、删除可用。
- 新增「系统用户 → 平台用户」换算，优先查 `office_organization_mapping`；查不到时给出明确业务错误，不猜测、不回退成系统 ID。
- 保持既有 fail-closed 语义、租户隔离与错误可读性。
- 向后兼容：不破坏已有调用方与现有测试。

## 3. 影响面（模块 / 接口 / 数据）

| 层 | 变更 |
| --- | --- |
| domain | `CalendarPort` 的 `getEventDetail`/`updateEvent`/`deleteEvent`/`cancelEvent` 增加父资源参数（`organizerId`），与 `createEvent` 的语义一致 |
| infrastructure | 三个 `*CalendarClient` 用该参数作为主日历/组织者定位；新增映射查询实现 |
| application | `OfficeCalendarService` 在调用客户端前完成「系统用户 ID → 平台用户 ID」换算；换算失败直接报错 |
| 数据 | 复用 `office_organization_mapping`，**不新增表、不改迁移** |
| 接口 | 对外 HTTP 路径与字段不变；`organizerId` 在响应中仍回显调用方传入值，避免前端感知变化 |

## 4. 兼容性

- 端口是内部契约，改动影响范围限于 `apex-office` 与三个客户端；无外部消费者。
- 现有调用方传入的 `organizerId` 是系统用户 ID，新增换算后行为从「直接透传给平台」变为「先换算再透传」，属于修正而非破坏。
- 映射缺失时行为由「平台报错（不可读）」变为「业务错误（可读）」，是改进。
- `createEvent` 的签名不变。

## 5. 回滚方案

改动集中在 `apex-office` 单模块内的端口与客户端，未涉及数据库迁移与部署配置。回滚按 `git revert` 单个提交即可；无数据兼容问题。

## 6. 验收标准

1. `CalendarPort` 扩展后，飞书/钉钉的 `getEventDetail`/`updateEvent`/`deleteEvent` 能构造出「主日历 + 事件」的完整路径，不再直接拒绝。
2. 组织者换算：命中映射时使用 `external_user_id`；未命中、空值、跨租户时返回明确业务异常且**不发起平台请求**。
3. 新增回归用例覆盖：端口签名、路径构造、换算命中/未命中/跨租户、缺凭据 fail-closed。
4. `:apex-office` 模块测试与 `spotless:check` 通过；`apex-office-service` 不回归。
5. 真实链路验证到凭据层（无平台凭据，无法完成真实创建），结论与边界写入 `docs/records/`。
