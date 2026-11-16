# 系统功能架构图评审与细化方案

## 评审发现概要

经三路并行代码审计,发现以下核心问题:

### 严重问题(内容不准确)
1. **107 auth域UML与代码严重不符**:12个类中仅TokenPair基本正确;JwtKeyEntity/SessionEntity/AuditLogEntity/MfaConfigEntity含**虚构字段和方法**(如`JwtKeyEntity.kid/publicKey/privateKey`不存在,实际是`encryptedSecret`+`status`枚举;`SessionEntity.touch()`不存在;`AuditLogEntity.toRecord()`不存在);遗漏`PasswordRecoveryEntity`、`UserEntity.accountId`(统一身份核心字段)等
2. **system域(45个实体)完全无UML类图** — 项目最大域,零UML覆盖
3. **file域完全无UML/ER/模块结构图** — 5个实体(FileEntity/FileChunkEntity/FileShareEntity/DocumentEntity/ChartEntity)无任何结构化图表
4. **common模块无UML类图** — 97号仅是包结构flowchart,且将`BusinessException`误写为`BizException`

### 完整性问题
5. 模块结构图系列(97-106)缺少`apex-file`和`apex-system`两个业务模块
6. ER表图系列(111-114)缺少file域
7. 108/109 UML需与真实代码核对修正(字段方法可能不全)

### 标准化问题
8. 三套配色方案并存:business-flows内嵌图用默认主题、multi-tenant用`#4f46e5`、其他.mmd混用`#3b82f6`/`#4f46e5`等;企业规范四色(`#4f46e5`/`#0891b2`/`#059669`/`#d97706`)未统一执行
9. 图表索引(`architecture-index.md`/`diagrams/README.md`)未登记56个.mmd源文件

## 实施计划(共4批)

### 第1批:修正现有UML图 + 补齐缺失域UML(高优先)

**1.1 修正 `docs/diagrams/107_uml_auth_center_domain.mmd`**
- 逐类对照`apex-auth/.../auth/domain/model/entity/`源码修正:
  - `UserEntity`:补齐`email/phone/openId/accountId/lastLoginAt/lastLoginIp`等8个遗漏字段
  - `TokenEntity`:补齐`tokenValue/revokedAt/revokedBy/revokeReason/tokenHash/tokenIv/deviceId`等13个字段,修正`revoke()`方法签名
  - `JwtKeyEntity`:**删除虚构的kid/publicKey/privateKey/active/rotate()/activate()/deactivate()**,改为真实的`keyVersion/encryptedSecret/status(枚举)/activatedAt/expiresAt`+`isActive()/isUsableForVerification()/isExpired()`
  - `SessionEntity`:**删除虚构的deviceInfo/touch()**,改为`deviceType/deviceId/ipAddress/userAgent/location/status`+`isActive()/updateLastActive()/terminate()/kickOut()`
  - `AuditLogEntity`:**删除虚构的toRecord()/AuditLogRecord**,改为`details`(非detail)+`eventStatus/resourceType/action/ipAddress/riskLevel`+`isSuccess()/isHighRisk()`等
  - `MfaConfigEntity`:删除虚构`verified`,补齐`required/priority`+`MfaType`枚举
  - `TotpEntity`:`recoveryCodes`→`backupCodes`(String非List),补齐`verified/usedBackupCodes`
  - `AuthConfigEntity`:删除虚构`apply()`,补齐`configType`(关键判别字段)+`isPasswordPolicy()/isSessionPolicy()`等
  - **新增`PasswordRecoveryEntity`**:状态机`REQUESTED→VERIFIED→CONSUMED`,含`channel/contactHash/status/attemptCount/expiresAt`
  - 补齐遗漏的域服务:`ClientSecretRotationService/TokenEncryptionService/BiometricAuthService`等
  - 统一配色为`theme:base`+`#4f46e5`(auth域主色靛蓝)

**1.2 新建 `docs/diagrams/110b_uml_system_domain.mmd`** — system域UML类图
- 对照`apex-system/.../domain/model/entity/`(45实体),按聚合分组绘制核心聚合:
  - 用户聚合:`UserEntity`↔`UserRoleEntity`↔`RoleEntity`↔`RolePermissionEntity`↔`PermissionEntity`
  - 组织聚合:`DeptEntity`(树形)、`TenantEntity`(含配额`TenantQuota`值对象)
  - 菜单聚合:`MenuEntity`(树形)、`DictTypeEntity`↔`DictDataEntity`
  - 配置/任务:`ConfigEntity`、`JobEntity`↔`JobLogEntity`
  - 日志聚合:`OperationLogEntity`、`LoginLogEntity`、`ApiCallLogEntity`
  - 安全策略:`SysSecurityPolicyEntity`、`SysJwtConfigEntity`、`SysSessionConfigEntity`、`BlacklistEntity`
- 标注聚合根`<<aggregate root>>`、实体`<<entity>>`、值对象`<<value object>>`
- 统一配色`#0891b2`(system域主色青)

**1.3 新建 `docs/diagrams/119_uml_file_domain.mmd`** — file域UML类图(编号119=下一个可用号)
- 对照`apex-file/.../file/domain/`源码:
  - `FileEntity`(聚合根):`storageType/totalChunks/chunkSize/fileSize/md5/sha256/storageStatus/businessType`
  - `FileChunkEntity`:`fileId FK/chunkIndex/md5/objectName/storageStatus`,工厂`stored(...)`
  - `FileShareEntity`(聚合根,2026-10-28新增):`fileId/tokenHash/expireAt/status`,`isExpired()/isRedeemable()`
  - `DocumentEntity`(聚合根):`docType/status/tags/viewCount`,`create()/publish()/archive()`
  - `ChartEntity`(聚合根):`chartType/config(Map)/status`,`create()/publish()/revertToDraft()`
  - 仓储端口:`FileRepository/FileChunkRepository/FileShareRepository/DocumentRepository/ChartRepository`(Mono/Flux)
  - 服务端口:`StorageService`(upload/download/delete/getPresignedUrl)、`StorageServiceProvider`(策略选择)、`TenantStatusPort`
  - 枚举:`StorageType/DocumentStatus/ChartType/ChartStatus`
- 统一配色`#d97706`(file/基础设施域主色琥珀)

**1.4 新建 `docs/diagrams/120_uml_common_infrastructure.mmd`** — common模块关键类UML
- 对照`apex-common/.../common/`源码:
  - 响应体系:`Result<T>`(code/msg/data/traceId)+`ResultCode`(SUCCESS=0)+`ErrorCode`枚举
  - 异常体系:`BusinessException`(code/message)+全局异常边界关系(修正97号图中的`BizException`误名)
  - 上下文体系:`TenantContext`(ThreadLocal+Reactor Context桥接)、`DataScopeContext`/`DataScopeProvider`、`DataSourceContext`
  - 调度器:`BlockingIoSchedulers`(jdbc/cache/storage/event/remote/general六池+虚拟线程开关)
  - 持久化基类:`BasePO`(@TableId/@Version/@TableLogic)、`BaseEntity`(@SuperBuilder)
- 统一配色`#4f46e5`(common主色靛蓝)

### 第2批:修正workflow/office UML + 补齐ER/模块结构图(中优先)

**2.1 修正 `108_uml_workflow_domain.mmd` 和 `109_uml_office_domain.mmd`**
- 对照`apex-workflow/.../workflow/domain/model/`和`apex-office/.../office/domain/`源码核对字段方法
- workflow:核对`WorkflowDefinition.dagJson`、`Task.timeoutStrategy/transferredFrom`、`ApprovalRecord`、`WorkflowTaskTransaction`端口
- office:核对`OfficeMessage.sendStatus/platformMessageId/retryCount`、`ApprovalProcess`、`CalendarEvent`、四端口(ApprovalPort/MessagePort/CalendarPort/DocumentPort)、三平台客户端(feishu/dingtalk/wecom)
- 补齐遗漏的关系和方法,统一配色

**2.2 新建 `docs/diagrams/121_er_file_tables.mmd`** — file域ER表关系图
- 对照file域PO和Flyway迁移,绘制:`file_record`↔`file_chunk`(1:N)、`file_record`↔`file_share`(1:N)、`document`(独立)、`chart`(独立)
- 含字段类型注释,统一配色`#3b82f6`(ER图系列蓝色)

**2.3 新建 `docs/diagrams/122_module_file_structure.mmd`** — apex-file模块结构图
- 参照104号图格式,绘制file模块四层:interfaces(router/handler)→application(service/assembler/dto)→domain(model/repository/service端口)→infrastructure(persistence/storage)
- 统一配色`#4f46e5`

**2.4 新建 `docs/diagrams/123_module_system_structure.mmd`** — apex-system模块结构图
- 绘制system模块四层,含office子域(application/office、handler/office)、governance、security、monitor等子包
- 统一配色`#4f46e5`

### 第3批:样式标准化(中优先)

**3.1 统一所有.mmd文件配色**
- 遍历`docs/diagrams/*.mmd`和`docs/diagrams/sequence/*.mmd`(共56个文件)
- 统一`%%{init}%%`头为`theme:base`,按域分配主色:
  - auth/system/common → `#4f46e5`(靛蓝)
  - workflow/office → `#059669`(绿)
  - file/infrastructure → `#d97706`(琥珀)
  - ER表图 → `#3b82f6`(蓝)
  - 统一`primaryTextColor:#0f172a`、`lineColor:#64748b`、`fontFamily:sans-serif`
- 仅改init头和themeVariables,不改图表内容结构

### 第4批:更新索引文档(低优先)

**4.1 更新 `docs/diagrams/README.md`**
- 新增119-123号图表登记
- 补充.mmd源文件与SVG的对应关系说明

**4.2 更新 `docs/architecture/core-tech-diagrams-index.md`**
- 新增system域、file域、common模块UML图表的索引条目
- 更新编号规则说明(107-123 UML/ER/模块结构系列)

## 验证方式

- 每张UML图修正/新建后,人工对照源码entity类逐字段核对
- Mermaid语法检查:确保所有`.mmd`文件可被mermaid渲染器正确解析(无语法错误)
- `git diff --check`检查空白错误
- 编号连续性:确认119-123编号无冲突
- 不涉及Java代码变更,无需Maven构建/测试

## 不在本次范围

- 不为data-flow/deployment/mq/service-governance等文档补充内嵌Mermaid(用户未明确要求,且工作量巨大,留待后续)
- 不渲染SVG(项目已有mermaid-viewer-guide,SVG渲染为独立任务)
- 不修改Java源码
