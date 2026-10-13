# 办公域迁移文件归属索引（只读）

> 本文件是**只读索引**，用于回答「V29–V57 中哪些迁移、哪些表现在归属谁」。
> 本文件不参与 Flyway 执行（`.sql` 之外的文件不被解析），也不会改动任何 `.sql` 迁移文件。
>
> **关键约束**：下面列出的所有迁移文件**一律冻结**。Flyway 校验（prod `validate-on-migrate=true`、`repair=false`）
> 会把 `checksum` 与整份文件（含注释）绑定，任何编辑已应用迁移文件（哪怕只加注释）都会导致校验失败、
> 单体无法启动。因此**归属调整永不通过改写 `.sql` 完成**，只能通过新增迁移或本索引/文档登记。

## 1. 三方归属分类

| 归属 | 含义 | 代表表 |
|---|---|---|
| `apex-office-service`（新服务库 `apex_office`） | 表已从单体解耦，由该服务自有的 Flyway 链（`V1__init_office_schema.sql`，独立版本号从 V1 起）重建 | `office_platform_app`、`office_identity_binding`、`office_organization_mapping`、`office_sync_task`、`office_message`、`office_approval_process`、`office_calendar_event`、`office_document_info`（共 8 张） |
| 单体 `apex-data` 共享域（cross-cutting）→ **已迁出至 `apex-office-service`**（2026-10-11） | 组织/消息域：`off_*` 表；代码（PO/Mapper/Service/Handler）已整体迁入 `apex-office` / `apex-office-service`，单体侧已删除；迁移文件因内含 `sys_*` 种子**仍冻结不删**，登记为「表已迁出、由新库承载」 | `off_department`、`off_employee`、`off_sync_history`、`off_message`、`off_message_recipient`、`off_message_template` |
| 单体 `apex-data` 非办公（系统域） | 与办公无关的表，始终归属单体 | `gen_*`、`sys_jwt_config`、`sys_sso_*`、`sys_security_policy`、`sys_session_config` 等 |

## 2. 逐文件归属（MySQL 目录）

| 迁移文件 | 创建对象 | 归属分类 |
|---|---|---|
| `mysql/V29__create_office_platform_tables.sql` | `office_platform_app`、`office_identity_binding`、`office_organization_mapping`、`office_sync_task` | `apex-office-service`（已在 V1 重建） |
| `mysql/V30__add_office_admin_permissions.sql` | `sys_menu`、`sys_role_menu`（office:app:* / office:binding:* 权限） | 共享 `sys_*`（系统域，留单体） |
| `mysql/V31__create_office_message_table.sql` | `office_message`（旧列名 recipient_type/status/result_message） | `apex-office-service`（V1 以 PO 列名重建） |
| `mysql/V32__create_office_approval_process_table.sql` | `office_approval_process` | `apex-office-service`（已在 V1 重建） |
| `mysql/V33__add_office_platform_menus.sql` | `sys_menu`(id 2000-2058)、`sys_role_menu`（office:* 权限） | 共享 `sys_*`（系统域，留单体） |
| `mysql/V34__create_office_calendar_event_table.sql` | `office_calendar_event` | `apex-office-service`（已在 V1 重建） |
| `mysql/V35__create_office_document_info_table.sql` | `office_document_info` | `apex-office-service`（已在 V1 重建） |
| `mysql/V36__add_office_message_menu_and_permissions.sql` | `sys_menu`、`sys_role_menu`（office:message:* 权限） | 共享 `sys_*`（系统域，留单体） |
| `mysql/V41__add_generator_auth_sso_office_tables.sql` | `gen_*`、`sys_jwt_config`、`sys_session_config`、`sys_security_policy`、`sys_sso_*`（办公部分**未建表**，复用 `office_*`） | 系统域为主（单体），不可整份删除 |
| `mysql/V57__add_office_platform_tables.sql` | `off_message_template`、`off_message`、`off_message_recipient`、`off_department`、`off_employee`、`off_sync_history` + `sys_menu`(2100) + `sys_permission`(3300-3302) | **已迁出**（2026-10-11）：`off_*` 表/代码迁往 `apex-office-service`（新库 `apex_office`，`V2__create_off_msg_org.sql` 重建）；文件因含 `sys_*` 种子冻结保留，登记「表已迁出、由新库承载」 |

MySQL 侧 `V29–V36, V41, V57`（共 10 个文件）与办公域相关，其余 `V37–V56, V58-V59` 为认证/安全/系统域，不在本索引范围。

## 3. 逐文件归属（PostgreSQL 目录）

PostgreSQL 目录与 MySQL 目录共享同一逻辑链，但版本号**错位对齐**：`mysql/V57` 的 PostgreSQL 等价物是 `postgresql/V42__add_office_platform_tables.sql`（`off_*` 表 + 菜单/权限种子）；`postgresql/V51–V58` 为 `mysql_alignment_placeholder.sql` 无操作占位，用于对齐版本序列。

| 迁移文件 | 创建对象 | 归属分类 |
|---|---|---|
| `postgresql/V29__create_office_platform_tables.sql` | `office_platform_app`、`office_identity_binding`、`office_organization_mapping`、`office_sync_task` | `apex-office-service` |
| `postgresql/V30__add_office_admin_permissions.sql` | `sys_menu`、`sys_role_menu` | 共享 `sys_*`（系统域） |
| `postgresql/V31__create_office_message_table.sql` | `office_message`（旧列名） | `apex-office-service` |
| `postgresql/V32__create_office_approval_process_table.sql` | `office_approval_process` | `apex-office-service` |
| `postgresql/V33__add_office_platform_menus.sql` | `sys_menu`、`sys_role_menu` | 共享 `sys_*`（系统域） |
| `postgresql/V34__create_office_calendar_event_table.sql` | `office_calendar_event` | `apex-office-service` |
| `postgresql/V35__create_office_document_info_table.sql` | `office_document_info` | `apex-office-service` |
| `postgresql/V36__add_office_message_menu_and_permissions.sql` | `sys_menu`、`sys_role_menu` | 共享 `sys_*`（系统域） |
| `postgresql/V41__add_generator_auth_sso_office_tables.sql` | `gen_*`、`sys_jwt_config`、`sys_session_config`、`sys_security_policy`、`sys_sso_*` | 系统域为主（单体） |
| `postgresql/V42__add_office_platform_tables.sql` | `off_message_template`、`off_message`、`off_message_recipient`、`off_department`、`off_employee`、`off_sync_history` + 菜单/权限 | **已迁出**（2026-10-11）：`off_*` 表/代码迁往 `apex-office-service`（新库 `apex_office`，`V2__create_off_msg_org.sql` 重建）；文件因含 `sys_*` 种子冻结保留，登记「表已迁出、由新库承载」 |
| `postgresql/V51__…V58__mysql_alignment_placeholder.sql` | 无操作占位（仅注释） | 系统域对齐，不在办公范围 |

## 4. 备注与后续

- `office_message` 存在**两套列名**：旧单体 `V31/V36`（`recipient_type/status/result_message`）与 `apex-office-service V1`（`target_type/target_user_ids/send_status/failure_reason`，与 `OfficeMessagePO` 对齐）。以新服务 `V1` 及其 PO 为准。
- `off_*`（组织/消息域）**已迁出**（2026-10-11）：`apex-office-service` 通过 `mysql/`、`postgresql/` 的 `V2__create_off_msg_org.sql` 新建同构表；`Off*PO/Mapper/Converter/RepositoryImpl/Entity/Repository/Service/Assembler/DTO`（50 个类）已迁入 `apex-office`，`3×Handler + 2×Router`（5 个类）已迁入 `apex-office-service interfaces.off`，单体侧对应 55 个类已删除。网关 office-domain 路由已补齐 `/admin-api/v1/message-templates`。待观察期验收后，将单体侧六张 `off_*` 表改名 `_bak` 保留 30 天后归档（见 `docs/microservice-migration-plan.md §9.3`）。
- **不在本索引范围**的共享表（`gen_*`、`sys_*` 等）归属单体系统域，与办公域拆分无关。