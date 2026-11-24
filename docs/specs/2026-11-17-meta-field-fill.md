# 2026-11-17 根治 office/auth 字段自动填充失效（BE-040）

## 1. 背景

BE-039 端到端验证暴露：`apex-office` 与 `apex-auth` 的 PO 使用了
`@TableField(fill = FieldFill.INSERT / INSERT_UPDATE)`，但两个模块**均未依赖
`apex-infrastructure-persistence`**，其中的 `MyMetaObjectHandler`（`MetaObjectHandler`
实现）既不在依赖里也不在服务扫描包内，导致全部 fill 字段无人填充。

后果：
- `@TableField(fill=...)` 字段即使为 null 也会进入 INSERT/UPDATE 语句（MP 对 fill
  字段不做非空裁剪），`office_calendar_event.create_time/update_time` 为 NOT NULL 且
  显式 null 时触发非空约束失败（BE-039 已复现并在服务层临时显式赋值绕过）。
- `apex_auth_dev.auth_mfa_config`/`auth_totp` 同样存在 `fill` 注解，MFA 启用与
  TOTP 绑定的写路径存在同样的潜在 500。
- 时间列本身有 `DEFAULT CURRENT_TIMESTAMP`，若字段被正常裁剪则无需填充器；
  问题恰恰出在 fill 字段不会被裁剪。

## 2. 目标

- office / auth 两个运行服务的 `MetaObjectHandler` 生效，fill 字段在插入/更新时
  自动获得时间值。
- 不改变 `apex-office`/`apex-auth` 的模块依赖边界（保持 `common + domain`，
  auth 另有 `infrastructure-integration`、`api-contract`），不为引入一个 25 行的
  处理器而给业务库挂上整套持久化设施。
- 已显式赋值的调用点不受影响（strict fill 只填充 null 值）。
- `OfficeCalendarService` 中 BE-039 的临时显式赋值移除，改由填充器负责，并以
  真实端到端回归证明。

## 3. 影响面（模块 / 接口 / 数据）

| 位置 | 变更 |
| --- | --- |
| `apex-office` | 新增 `com.apex.scaffold.office.infrastructure.config.AuditFieldFillHandler`（该 config 包已存在，含 MyBatis-Plus 配置类） |
| `apex-auth` | 新增 `com.apex.scaffold.auth.infrastructure.config.AuditFieldFillHandler`（新建 config 包，auth-service 扫描 `com.apex.scaffold.auth`） |
| `apex-office` 应用服务 | 移除 BE-039 的临时显式时间赋值 |
| 测试 | 两个 handler 的单元用例 + 现有回归 |

不新增模块、不新增 POM 依赖（MyBatis-Plus core 已在两模块依赖树内）、不改 HTTP
契约、不改数据库。

## 4. 兼容性

- handler 采用与共享 `MyMetaObjectHandler` 相同的 strict 语义（仅填 null 值）：
  业务代码已显式赋值的时间不会被覆盖。
- 只填 `createTime`/`updateTime` 两个属性；`version`/`deleted` 在 office/auth 的
  PO 中无 fill 注解且类型混用（Integer/Boolean），不纳入填充，维持现状。
- auth 的时间列名为 `created_at`/`updated_at`（属性名 `createTime`/`updateTime`），
  由既有 `@TableField` 的列名映射完成，handler 无需感知列名。
- 两服务扫描 `com.apex.scaffold.office` / `com.apex.scaffold.auth`，新类位于各自
  组件包内，天然被扫描，无需改服务启动类。

## 5. 回滚方案

未提交工作区按文件回退即可；提交后 `git revert`。无数据库迁移与数据变更。

## 6. 验收标准

1. 单元用例：insertFill/updateFill 对 null 的 createTime/updateTime 填充时间值；
   已有值不被覆盖。
2. 移除 `OfficeCalendarService` 临时赋值后，经真实网关创建日历事件成功，且库中
   `create_time/update_time` 非空（证明填充器在运行服务内生效）。
3. `:apex-office`、`:apex-auth`、`:apex-office-service`、`:apex-auth-service` 四个
   模块测试与 `spotless:check` 通过。
4. 记录与任务卡同步，写明 auth 侧 MFA/TOTP 写路径为潜在缺陷已随本修复消除、
   但真实平台写操作仍需平台凭据（外部依赖不变）。

## 2026-11-24 独立 R5 复核（REJECT）

对实现提交 `5080efac` 的整包复核结论为 **REJECT**，4 项必改。架构决策（模块内各放一个 `MetaObjectHandler`，不把 `apex-infrastructure-persistence` 拉进业务库）经复核确认为正确，无需改。

- **MF-1（真实缺陷）**：填充器仅覆盖 `createTime`/`updateTime`；office 审批发起与站内信发送的插入路径所依赖的字段未被填充，导致这些写入仍会失败。需补齐（以 `hasSetter` 守卫）并补插入用例。
- **MF-2（真实缺陷）**：更新与取消路径的 `update_time` 未被刷新，且在 MySQL `ON UPDATE` 语义与 PG 无触发器的情况下时间会冻结。需在更新/取消时刷新并断言。
- **MF-3**：本文 §4 关于属性名/列名的说明与实现不符（auth 实际属性为 `createdAt`/`updatedAt`），需更正。
- **MF-4**：残余限制表述失准——原记录把缺口描述为“仅外部凭据”，但 auth MFA/TOTP 的**持久化**验证可本地完成（H2 用例即可闭合），只有真实认证器端到端登录属外部可选。

复核同时指出非阻断问题：`AuditFieldFillHandlerTest` 的用例显示名用 `createTime/updateTime` 但断言 `getCreatedAt/getUpdatedAt`；`CalendarEventRepositoryImpl` 中“本模块未装配 MetaObjectHandler”的注释理由已失真（显式 `update_time` 赋值仍应保留）；台账 #51 提交列仍写“未提交”；两个 handler 为普通 `@Component` 且无 `@ConditionalOnMissingBean`，当前无重复装配但未来同 classpath 存在多实例时 MP 取首个。

复核未覆盖：未运行 `mvnd`/`clean verify`、未执行新增用例、未启动服务与数据库；`update_time` 冻结属基于生成 SQL 与数据库语义的推断，未观测真实行值。

任务保持 **待复核**，上述必改未修完前不得关闭。

### 2026-11-24 必改修复进展

- **MF-1 已修复**：office 填充器同时覆盖两套命名（`createTime/updateTime` 与 `createdAt/updatedAt`），以 `hasSetter` 守卫；新增 2 条 `ApprovalProcessPO` 插入/更新用例。`insertFill`/`updateFill` 现对审批流程、审批撤销等 `createdAt/updatedAt` PO 生效。
- **MF-2 已修复**：`CalendarEventRepositoryImpl.update`、`DocumentInfoRepositoryImpl.update`、`ApprovalCancelOperationRepositoryImpl.update` 显式刷新 `update_time`（填充器 strict 语义不会覆盖 PO 带回的旧值）；同时纠正两处“本模块无 MetaObjectHandler”的失真注释。
- **验证**：`mvnd -B -pl :apex-office -am test spotless:check` BUILD SUCCESS，`apex-office` **204 项**（原 202 + 新增 2），0 失败/错误/跳过。
- **仍未处理**：MF-3（规格 §4 属性名说明已在本节更正，原文保留）、MF-4（auth MFA/TOTP 持久化本地 H2 用例未补）、台账 #51 提交列、handler 的 `@ConditionalOnMissingBean` 建议、`AuditFieldFillHandlerTest` 显示名与断言不一致。BE-040 保持待复核。
