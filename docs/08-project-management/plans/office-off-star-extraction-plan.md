# off_* 组织/消息域提取方案（迁移至 apex-office-service）

> **状态**：已执行（2026-10-11：`V2` 建表 + 全量代码迁移 + 网关切流 + 单体侧下线均已完成，编译验证通过，进入观察期）
> **关联决策**：用户已确认「`off_*` 目标归属 `apex-office-service`」，并选择「先出提取方案」。
> **关联文档**：`apex-interfaces/src/main/resources/db/migration/OFFICE_MIGRATION_OWNERSHIP.md`、`docs/microservice-migration-plan.md §9.2/§9.3`。
> **硬约束**：**绝不执行 `mvnd clean verify`（门禁）**；**绝不改写任何已应用的 `.sql` 迁移文件**（Flyway checksum 与整份文件绑定）。

---

## 1. 目标与范围

### 1.1 目标
将单体中仍在使用、但产品上归属办公平台的 **`off_*` 六张表**及其 **约 58 个 Java 类**（PO/Mapper/Converter/RepositoryImpl/Entity/Repository/Assembler/Service/DTO/Handler/Router）整体提取到 `apex-office-service`，使其成为该服务自治的数据与代码域，实现「真正彻底的微服务」。

### 1.2 范围

| 类别 | 包含 | 不包含 |
|---|---|---|
| 数据表 | `off_message_template`、`off_message`、`off_message_recipient`、`off_department`、`off_employee`、`off_sync_history` | `office_*` 8 张表（已在 `apex-office-service V1` 重建，不在本计划范围） |
| 代码 | `Off*` 全链路（interfaces→application→domain←infrastructure）+ 3 个 Handler + 2 个 Router | `office_*` 新域代码（`apex-office` 模块，已存在），以及 `gen_*`/`sys_*` 系统域 |
| 迁移脚本 | 在 `apex-office-service` 新增独立 Flyway 迁移（`mysql/` + `postgresql/`）创建 6 张表 | 改写单体历史 `V57`/`V42` |
| 路由 | 站内信 `/api/v1/messages`、模板 `/admin-api/v1/message-templates`、组织 `/admin-api/v1/org` | 平台外发消息 `/api/office/messages`（office-service 已有，路径不冲突） |

---

## 2. 现状盘点（已验证）

### 2.1 表（由单体 `mysql/V57` / `postgresql/V42` 创建）
| 表 | 说明 | 关键键/索引 |
|---|---|---|
| `off_message_template` | 办公消息模板 | `uk(template_code, tenant_id, deleted)` |
| `off_message` | 办公消息（站内信） | `uk(message_code, tenant_id, deleted)`、`idx template_id` |
| `off_message_recipient` | 消息接收人 | `uk(message_id, recipient_id, deleted)`、`idx(recipient_id, read_status, deleted)` |
| `off_department` | 办公组织部门 | `uk(dept_code, tenant_id, deleted)`、`idx parent` |
| `off_employee` | 办公组织员工 | `uk(employee_code, tenant_id, deleted)`、`idx dept/user` |
| `off_sync_history` | 组织同步历史 | `idx(tenant_id, start_time)` |

> 共享表 `sys_menu`/`sys_permission` 种子（`V57` 中一并写入）**不在**提取范围，保留单体系统域。

### 2.2 代码（约 58 个类，均位于单体四层）
- **apex-infrastructure**（28）：6×PO `Off*PO`、6×Mapper、6×Converter、6×RepositoryImpl（`@TableName("off_*")`）。
- **apex-domain**（12）：6×Entity、6×Repository 接口。
- **apex-application**（14）：`OffMessageService`、`OffMessageTemplateService`、`OrgSyncService`；5×Assembler（`OffMessageAssembler`、`OffMessageTemplateAssembler`、`SyncHistoryAssembler`、`EmployeeAssembler`、`DepartmentAssembler`）；6×DTO（`MessageDTO`、`MessageTemplateDTO`、`SendMessageRequest`、`SyncHistoryDTO`、`DepartmentDTO`、`EmployeeDTO`）。
- **apex-interfaces**（5）：`MessageHandler`、`MessageTemplateHandler`、`OrgSyncHandler`；`MessageRouter`、`OrgSyncRouter`。

### 2.3 依赖边界（已验证为自包含）
- `OffDepartmentEntity/OffEmployeeEntity/OffSyncHistoryEntity` 及其 Repository 仅被单体内部 Converter/RepositoryImpl/Service/Assembler 引用，**无外部消费者**，可作为完整闭包整体搬迁。
- 单体当前路由面与 office-service 已有路由面**无冲突**：
  - 单体站内信：`/api/v1/messages`、`/admin-api/v1/message-templates`、`/admin-api/v1/org/*`。
  - office-service 已存在：`/api/office/messages`（平台外发）。
- `apex-office-service` 已依赖 `apex-domain`，可直接复用。

---

## 3. 关键设计决策

### 3.1 决策 A：lifting-shift 整体搬迁（推荐） vs 合并进 office_* 模型
| 方案 | 说明 | 结论 |
|---|---|---|
| **A. lift-and-shift（推荐）** | 将 6 张 `off_*` 表 + 全部 `Off*` 代码原样迁入 `apex-office-service`，新增 `V2` 迁移创建同构表；保留 `off_*` 表名与类名。 | **推荐**：风险最低、行为零变化、工期最短，符合「先自治、后收敛」的改造节奏。 |
| B. 合并进 `office_*` | 把 `off_message` 并入 `office_message`、`off_department/off_employee` 并入 `office_*` 组织模型。 | 语义不完全一致（站内信收件箱 vs 平台外发消息），需数据模型对账与影响面改造，留作后续收敛期任务。 |

> 两套消息模型差异说明：`office_message`（office-service `V1`，`target_type/send_status/failure_reason`）是**平台外发消息**；`off_message`（`recipient_id/read_status`）是**站内信**。本期**互不合并**，仅做归属搬迁。

### 3.2 决策 B：路由承载位置
- 沿用原路径（`/api/v1/messages`、`/admin-api/v1/message-templates`、`/admin-api/v1/org/*`）在 `apex-office-service` 重建，由网关统一转发切流；单体侧逐步下线这些 Router/Handler。
- 站内 SSE 通知本期不在 `off_*` 数据范围内（见 `§2.4` 既定决策），维持留 system-service，二期再定。

---

## 4. 迁移步骤（对齐 plan §9.3 流程，每阶段不触发门禁）

### Phase 1 — 新库建表（apex-office-service 新增迁移）
- 新增 `apex-office-service/src/main/resources/db/migration/mysql/V2__create_off_msg_org.sql` 与 `postgresql/` 同构脚本，`CREATE TABLE IF NOT EXISTS` 六张 `off_*` 表，DDL 从单体 `V57` 复制并保持列/索引/唯一键一致。
- MySQL 用 `DATETIME`；PG 对齐类型；幂等（沿用 AGENTS.md 规范）。
- **验收**：office-service 能用 Flyway 建表、单测脚本编译通过；**不动**单体任何 `.sql`。

### Phase 2 — 全量存量同步
- 停写窗口内 `mysqldump` 按表导出 → 导入 `apex_office` 库（`off_message_template/off_message/off_message_recipient/off_department/off_employee/off_sync_history`）。
- 或利用主从回放完成增量对齐。

### Phase 3 — 双写过渡
- 旧库（单体 `apex_data`）继续权威写；office-service 上线新 `off_*` 表并**只读**。
- 定时对账（行数 + checksum），差异告警。
- **执行状态（2026-10-11）**：已交付对账守护脚本 `scripts/microservices/reconcile-off-star-data-guard.sh`，
  封装 `reconcile-off-star-data.sh` 周期性对账；发现差异时输出告警详情（含时间戳与差异指纹）并以非 0 退出码结束。
  支持 `--dedup`（默认开，结论/指纹不变时去重，避免重复刷屏）、`--quiet`，运行摘要落 `logs/reconcile-guard-YYYYMM.log`、
  告警流水落 `logs/reconcile-guard-alert.log`。退出码：`0`=一致 / `1`=不一致 / `2`=参数错误 / `3`=前置失败。
  已实测四类路径（一致 / 差异告警 / 去重 / 恢复）与前置失败均符合预期。

### Phase 4 — 切写
- 维护窗口：先将**新写流量**切到 office-service（网关转发 `/api/v1/messages`、`/admin-api/v1/message-templates`、`/admin-api/v1/org/*` 到 office-service）。
- 单体侧对应 Handler/Router 下线（`MessageHandler`、`MessageTemplateHandler`、`OrgSyncHandler`、`MessageRouter`、`OrgSyncRouter`），`apex-application` 的 `OffMessageService/OffMessageTemplateService/OrgSyncService`、`apex-domain` Entity/Repository、`apex-infrastructure` PO/Mapper/Converter/RepositoryImpl 从单体移除。
- **回滚预案**：网关切回旧服务即恢复，旧表数据未删。

### Phase 5 — 观察期（1–2 周）
- 对账一致 + 站内信/模板/组织结构各接口业务验收通过。

### Phase 6 — 清理
- 单体（`apex_data`）六张 `off_*` 表改名 `_bak` 保留 **30 天** → 归档删除。
- 单体历史迁移 `V57`/`V42` 仍冻结登记（见归属索引），不删除（因内含 `sys_menu/sys_permission` 种子），仅在文档标注「表已迁出、由新库承载」。

---

## 5. 依赖与影响风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 站内信 SSE 通知 | 若与 `off_message` 有联动，切写后需保证通知仍可达 | 本期不动 SSE（§2.4 既定）；确认前端未直连老库 |
| 跨库 ID 引用 | `sender_id/user_id` 与 `sys_user` 逻辑关联 | 跨库只存 ID，不做 JOIN；需用户名由事件同步/冗余列回填 |
| 路由冲突 | `/api/v1/*` 可能被系统域复用 | 已核对 office-service 无同名路径；网关按前缀精确分流 |
| 迁移轨迹 | `apex_office` 无历史迁移记录 | 新库建 `V2` 即权威；单体 `_bak` 表保证可回看 |
| 单体回归 | 移除 `Off*` 类可能被其他模块隐式引用 | 已 grep 确认无外部消费者；移除前再全量 grep + ArchUnit 校验 |

---

## 6. 验证与验收标准

- 编译：受影响模块（office-service 及被引用模块）`mvnd -T4 -B compile` / 单测通过（**不跑全量 `clean verify` 门禁**）。
- 数据：`apex_office` 六表行数与单体源表一致，checksum 对账通过。
- 行为：站内信收/发/已读/未读数、模板 CRUD、组织结构 CRUD、同步历史查询，经 office-service 路由全量回归通过。
- 归档：单体 `off_*` 表 `_bak` 改名成功，30 天后删除。
- 文档：OFFICE_MIGRATION_OWNERSHIP.md、plan §9.2 更新为「off_* 已迁出」。

---

## 7. 待办清单（执行期勾选）

- [x] 新增 office-service `mysql/`、`postgresql/` 的 `V2__create_off_msg_org.sql`
- [x] 迁移 6×PO/Mapper/Converter/RepositoryImpl → office-service（或 apex-office）持久化层
- [x] 迁移 6×Entity/Repository + 3×Service + 5×Assembler + 6×DTO → apex-office/apex-office-service 业务层
- [x] 迁移 3×Handler + 2×Router → office-service interfaces，重建旧路径路由
- [x] 单库存量数据同步 + 对账
- [x] 对账守护 + 差异告警（Phase 3：`reconcile-off-star-data-guard.sh`）
- [x] 网关切流 + 单体侧 `Off*`/Router/Handler 下线
- [ ] 观察期验收 + 单体 `off_*_bak` 改名归档
- [x] 更新归属索引与 plan §9.2