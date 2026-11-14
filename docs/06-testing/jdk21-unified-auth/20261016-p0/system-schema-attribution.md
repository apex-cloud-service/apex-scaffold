# system 双库 schema 差异归因

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

本报告以 [schema-comparison.json](schema-comparison.json) 的 `domains[0]` 和 [final-schema-evidence.json](final-schema-evidence.json) 为输入。证据采集时间为 `2026-10-17T04:47:06.965634+00:00`，范围为既有 fresh migration TEST 库的只读元数据及 Flyway 历史。本文不证明生产存量升级、业务读写或跨库语义等价。

[system-schema-attribution.json](system-schema-attribution.json) 给出全部 15 个对象和 193 项列存在性差异的逐项映射：每个对象包含迁移路径、建表/视图行号、全部列的准确行号及缺失侧对应版本说明。类型和默认值按精确值规则覆盖原始逐项记录；原始值保留在输入 JSON，不因分类而删改。

## 结论与计数

15 个存在性差异全部是 MySQL 有而 PostgreSQL 无，包括 **14 张基础表和 1 个视图**。193 项列差异拆为 **167 项缺对象的衍生列差异 + 26 项共有表的真实列漂移**。因此不能把这 193 项全部算作独立的列迁移遗漏，也不能把其中 167 项作为共有表漂移排查。

| 层次 | 数量 | 方向/说明 |
| --- | ---: | --- |
| 缺基础表 | 14 | MySQL 有，PG 无 |
| 缺视图 | 1 | `v_token_health` |
| 缺对象衍生列 | 167 | 包含视图的 8 个 SELECT 输出列 |
| oauth2_authorization 共有表列 | 16 | MySQL 多 |
| sys_user 共有表列 | 9 | MySQL 多 |
| off_sync_history 共有表列 | 1 | PG 多 `update_time` |
| 列存在性合计 | 193 | 167 + 16 + 9 + 1 |

MySQL/PG 的对象数分别为 96/81，列数为 1347/1156。列数净差 191 与列存在性差异 193 不矛盾：MySQL 独有列 192、PG 独有列 1，净差为 191。

## 缺失对象归因

下表行号为对应 SQL 文件的 1 起始行号。列名与逐列行号完整列于专项 JSON 的 `missing_objects[].columns`，不是只给建表语句而省略列归因。

| 对象 | 衍生列数 | MySQL 创建迁移及行 | PostgreSQL 对应情况 |
| --- | ---: | --- | --- |
| biometric_auth | 13 | [V55](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V55__biometric_auth.sql) 第2行 | [V55](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V55__mysql_alignment_placeholder.sql) 第4行起仅注释占位 |
| device_trust | 19 | [V56](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V56__device_trust.sql) 第2行 | [V56](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V56__mysql_alignment_placeholder.sql) 第4行起仅注释占位 |
| encryption_key | 10 | [V48](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V48__security_enhancements.sql) 第157行 | PG V48 修改客户端 secret 轮换字段 |
| encryption_key_version | 8 | [V42](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V42__add_token_encryption.sql) 第145行 | PG V42 为 office 平台表 |
| ip_access_control | 12 | [V53](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V53__ip_access_control.sql) 第2行 | [V53](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V53__mysql_alignment_placeholder.sql) 第4行起仅注释占位 |
| jwt_key_version | 9 | MySQL V48 第62行 | PG V48 无对应创建 |
| login_history | 14 | MySQL V48 第95行 | PG V48 无对应创建 |
| mfa_config | 12 | [V27](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V27__mfa_config.sql) 第2行 | [PG V27](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V27__create_document_and_chart_tables.sql) 第6行创建文档表 |
| oauth2_client_secret_history | 7 | MySQL V48 第80行 | PG V48 的轮换字段不能替代历史表 |
| password_history | 5 | MySQL V48 第26行 | PG V48 无对应创建 |
| security_audit_log | 12 | MySQL V48 第39行 | PG V48 无对应创建 |
| token_rotation_history | 10 | MySQL V42 第76行 | PG V42 无对应创建 |
| token_security_event | 14 | MySQL V42 第93行 | PG V42 无对应创建 |
| trusted_device | 14 | MySQL V48 第120行 | PG V48 无对应创建 |
| v_token_health（视图） | 8 | MySQL V42 第129行；别名第131–138行 | PG V42 无对应创建 |

双库版本功能对照依据：[PG V42 office 迁移](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V42__add_office_platform_tables.sql)、[PG V48 客户端轮换迁移](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V48__add_client_secret_rotation_tracking.sql)。扫描 system PG 的全部 61 个 `V*.sql`，未发现上述 15 个对象的可执行创建语句；V53/V55/V56 仅在注释中提及 MySQL 对应文件。缺失同时由最终 catalog 确认。占位脚本声称“既有迁移覆盖”不能代替实际 DDL，版本集合相同也不能证明能力齐全。

## 共有表的 26 项列漂移

### oauth2_authorization：16 项

- [MySQL V42](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V42__add_token_encryption.sql) 第14–21、23行：`access_token_hash`、`access_token_encrypted`、`access_token_iv`、`refresh_token_hash`、`refresh_token_encrypted`、`refresh_token_iv`、`token_family`、`previous_jti`、`rotation_count`，共9列。
- [MySQL V48](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V48__security_enhancements.sql) 第145–148、150–151行：`device_id`、`device_fingerprint`、`client_ip`、`is_revoked`、`last_used_ip`、`is_encrypted`，共6列。
- [MySQL V58](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V58__add_token_revocation_tracking.sql) 第3行：`revoked_reason`，共1列；[PG V58](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V58__mysql_alignment_placeholder.sql) 仅注释占位。

PG V42/V48 分别执行 office 平台建表和客户端轮换字段迁移，并未实施上列对应变更。[PG V47](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V47__add_token_revocation_tracking.sql) 第3–7行已建立 `revoked_at`、`revoked_by`、`revoke_reason`、`last_used_at`、`use_count`；其中 `revoke_reason` 与新增的 `revoked_reason` 是不同列，不能按名称相似合并。`revoked_at` 与 `last_used_at` 双库均有，不计入列存在性差异。

### sys_user：9 项

MySQL V48 第11–19行分别新增 `is_locked`、`lock_reason`、`locked_at`、`locked_until`、`failed_login_attempts`、`last_failed_login_at`、`password_changed_at`、`password_expires_at`、`must_change_password`。PG V48 只修改 `oauth2_registered_client`，未执行这9项用户安全字段 DDL。

### off_sync_history：1 项

[PG V42](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V42__add_office_platform_tables.sql) 第130行建表、第143行定义 `update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`；功能对应的 [MySQL V57](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V57__add_office_platform_tables.sql) 第124行建表、第125–138行只有14列，没有 `update_time`。这里必须按功能对应 PG V42/MySQL V57，而不是只比较同版本号。

## 类型、默认值、可空性与索引

### 类型差异：277 项

| MySQL | PostgreSQL | 数量 | 分类及未验证范围 |
| --- | --- | ---: | --- |
| datetime(0) | timestamp without time zone(6) | 190 | 时间精度、范围与驱动语义待验证 |
| tinyint | smallint | 28 | 数值取值范围不同 |
| timestamp(0) | timestamp without time zone(6) | 20 | 时区转换、精度与范围待验证 |
| datetime(6) | timestamp without time zone(6) | 11 | 范围和绑定语义待验证 |
| longtext | text | 9 | 容量、字符集、排序规则待验证 |
| tinyint | boolean | 9 | 布尔域与转换行为待验证 |
| double | double precision | 4 | 浮点方言表示；边界语义未验证 |
| json | jsonb | 2 | 存储与运算符语义不同 |
| mediumtext | text | 2 | 容量和排序规则待验证 |
| varchar(255) | varchar(100) | 1 | `sys_user.keycloak_sub` 的长度漂移 |
| longblob | bytea | 1 | 容量和二进制绑定待验证 |

长度漂移来自 [MySQL V51](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V51__add_keycloak_fields.sql) 第11行扩为255，而 [PG V37](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V37__add_email_and_keycloak_sub_to_sys_user.sql) 第21行为100，[PG V51](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V51__mysql_alignment_placeholder.sql) 是占位。277项均可归入以上外观/结构类别，**不表示277项均已证明等价**。

### 默认值差异：208 项

| 分类 | 数量 | 说明 |
| --- | ---: | --- |
| typed NULL catalog 表达式 | 134 | MySQL null 与 PG `NULL::...`；不删除原证据 |
| 序列默认表达式 | 21 | PG `nextval(...)`；与自增分配行为的等价性未验证 |
| 字面量 cast 表示 | 40 | 同样字面内容带 PG cast；实际类型转换语义待验证 |
| 数字/布尔表示 | 8 | `0/false` 或 `1/true`；取值域待验证 |
| 时间函数或精度 | 4 | Flyway installed_on 的 CURRENT_TIMESTAMP/now()，及 outbox 三列的 CURRENT_TIMESTAMP(6)/CURRENT_TIMESTAMP |
| 字面默认值漂移 | 1 | `wf_workflow_definition.enabled`：ACTIVE/DRAFT |

最后一项来自 [MySQL V40](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V40__fix_schema_inconsistencies_based_on_po.sql) 第54行重述默认值为 ACTIVE；[PG V20](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V20__create_workflow_engine_tables.sql) 第12行是 DRAFT，[PG V40](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V40__fix_schema_inconsistencies_based_on_po.sql) 第50行仅改名，保留原默认值。此项属于真实字面漂移，不能归入 cast 噪声。

### 可空性差异：4 项

四项均为 MySQL 可空、PG 非空。MySQL V40 用 `CHANGE COLUMN` 重述定义时没有保留 NOT NULL，PG V40 用 `RENAME COLUMN` 保留旧定义。

| 列 | MySQL V40 行 | PG 原定义 | PG V40 rename 行 |
| --- | ---: | --- | ---: |
| office_message.send_status | 132 | V31:15，NOT NULL | 92 |
| office_message.target_user_ids | 118 | V31:11，NOT NULL | 86 |
| wf_workflow_definition.enabled | 54 | V20:12，NOT NULL | 50 |
| wf_workflow_definition.node_definition | 40 | V20:11，NOT NULL | 44 |

原定义见 [PG V31](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V31__create_office_message_table.sql) 和 PG V20；完整逐项引用在 JSON 的 `nullability_classification.items`。

### 索引 signature 多重集合差异：27 个对象

其中14个来自缺基础表，另外13个为共有表差异。视图没有对应索引差异。signature 比较保留有序键、唯一性、method 和重复次数；相同 signature 不证明谓词、前缀、排序规则及完整 DDL 等价。

| 共有对象 | 归因/保留未知 |
| --- | --- |
| biz_system_message | PG多一个非唯一tenant_id副本；具体历史语句来源未知 |
| doc_document | FULLTEXT(title,content) 与 GIN(to_tsvector(simple,...))；检索语义未验证 |
| oauth2_authorization | MySQL V42/V48/V58 对缺失列建立索引，并多共有列last_used_at索引 |
| off_department | PG多parent_id,status,sort_order及sync_source,external_id副本 |
| off_employee | PG多dept_id,status、employee_code,tenant_id及sync_source,external_id副本 |
| off_message | PG多sender_id,send_time副本 |
| off_message_template | PG多template_code,enabled,tenant_id |
| off_sync_history | PG多sync_source,start_time及sync_status,start_time副本 |
| sys_message_outbox | PG多status,next_attempt_at；具体历史来源未知 |
| sys_operation_log | PG多tenant_id副本；具体历史来源未知 |
| sys_role_dept | MySQL多tenant_id,dept_id；具体历史来源未知 |
| sys_user | MySQL多email副本、is_locked、password_expires_at；PG多tenant_id副本；V48安全索引已定位，其他副本来源未知 |
| sys_user_role | PG多tenant_id副本；具体历史来源未知 |

六个 off_* 对象有明确迁移顺序原因：[MySQL V43](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V43__performance_optimization_indexes.sql) 第35行要求表列存在才执行，第53–59、66–67行调用时，表要到 V57 才创建；[PG V43](../../../../apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V43__performance_optimization_indexes.sql) 第3–20行执行时，PG V42 已创建这些表。MySQL 条件跳过后没有自动补执行这些 CALL。不能把重复索引签名抹平，也不能由库存差异推断实际性能。

其他类别继续保留：column_order 73、column_generation 1155、index_raw_inventory 95、constraint_kind_counts 23、constraint_raw_inventory 95、history_checksum 56、history_script 12。它们不是本报告已逐条证明的功能缺陷；生成表达、约束语义、查询计划和业务影响保留未知。双库不同 SQL 的校验和不同不等于历史篡改。

## Mapper、PO 与 XML 使用证据

扫描范围为全仓生产 `src/main` 下 Java/XML 的对象名、差异列名和相关持久化类型。未命中仅代表未发现直接字面引用，不能排除动态 SQL、外部客户端或运行时装配。

| 对象 | 直接证据 | 作用域与结论 |
| --- | --- | --- |
| mfa_config | [MfaConfig.java](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/domain/model/MfaConfig.java) 第20行 `@Table`；[仓储](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/repository/MfaConfigRepository.java) 第15行 ReactiveCrudRepository | auth 域使用，不证明 system-service 调用此表 |
| biometric_auth | [BiometricAuth.java](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/domain/model/BiometricAuth.java) 第20行；[仓储](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/repository/BiometricAuthRepository.java) 第15行 | 同上 |
| device_trust | [DeviceTrust.java](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/domain/model/DeviceTrust.java) 第20行；[仓储](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/repository/DeviceTrustRepository.java) 第16行 | 同上 |
| ip_access_control | [IpAccessControl.java](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/domain/model/IpAccessControl.java) 第20行；[仓储](../../../../apex-components-parent/apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/repository/IpAccessControlRepository.java) 第17、45、74行 | R2DBC 映射与直接DELETE语句；auth域 |
| sys_user | [UserPO](../../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/po/UserPO.java) 第58行；[UserMapper](../../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/UserMapper.java) 第45行 BaseMapper | 九个安全差异列未发现显式映射/直接SQL引用 |
| oauth2_authorization | [领域实体](../../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/OAuth2AuthorizationEntity.java) 第13行注释 | 注释不是PO/Mapper证据；16个差异列未发现system生产Java/XML直接持久化引用 |
| off_sync_history | [OffSyncHistoryPO](../../../../apex-components-parent/apex-office/src/main/java/com/apex/scaffold/office/off/infrastructure/persistence/po/OffSyncHistoryPO.java) 第84–85行；[Mapper](../../../../apex-components-parent/apex-office/src/main/java/com/apex/scaffold/office/off/infrastructure/persistence/mapper/OffSyncHistoryMapper.java) 第73行 | 继承 [BasePO](../../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/persistence/po/BasePO.java) 第48行update_time映射，存在列依赖；不能据system历史库推断office新库缺列 |

另外11个缺失对象（encryption_key、encryption_key_version、jwt_key_version、login_history、oauth2_client_secret_history、password_history、security_audit_log、token_rotation_history、token_security_event、trusted_device、v_token_health）未发现生产 Java/XML 直接表映射。未发现这15个对象及26个漂移列的直接 XML 持久化引用。`auth_mfa_config` 与 `mfa_config` 是不同对象，OAuth2 consent 也不是 authorization 表。

system 历史迁移保留已拆往其他域的表，因此代码命中和当前服务数据所有权须分开判断。本报告没有验证相关服务启动、Mapper实际SQL执行或业务请求路径。

## 验证记录与限制

- 15对象集合与输入完全一致；193列逐项唯一、无遗漏或重复；167/26拆分成立。
- 全部193列的引用行实际包含对应列名/视图别名，迁移路径存在；双库对应脚本和附加迁移引用行号均在文件范围内。
- 类型11组总数277、默认值6组总数208、可空性4项、索引14衍生对象加13共有对象合计27，与输入核对。
- system 双库共122个迁移文件 SHA-256 与 final-schema-evidence 的 source_files 一致，输入证据绑定到当前源文件；没有以不同双库校验和断言篡改。
- 仅新增本专项 JSON/Markdown；未修改历史SQL或生产代码，未修改主report，未恢复/tmp采集脚本，未commit/push。
- 未执行Java构建、Java测试、迁移重跑、存量升级、业务读写、查询计划或性能验证。未知项保留未知，不能把外观分类计数视为语义等价证明。
