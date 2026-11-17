# 2026-11-17 联调修复复核要点清单（BE-036~BE-040 / FE-026）

> 本清单供独立复核人（R5 / 任意非实现方）使用。实现方为主控 ZCode（用户 2026-11-17 授权），
> **本清单由实现方整理，不构成复核结论**。复核通过后由复核人在两仓任务大盘更新状态。

## 1. 评审对象与版本

- 分支：两仓 `dev`（已同步 `master`/`test`）
- 前端 apex-admin-web：`95a7f62`（代码）+ 4 个文档提交，HEAD `fba7711`
- 后端 apex-scaffold：`102fa274`（BE-036）→ `1731b622`（BE-037）→ `12d7fdd4`（BE-038）→ `cc3303e4`（BE-039）→ `5080efac`（BE-040）+ 文档提交，HEAD `49bc5649`
- 复核范围：`git log 102fa274^..49bc5649`（后端）、`git log 8ab7d22^..fba7711`（前端）

## 2. 各任务复核要点

### BE-036（`102fa274`）审批租户上下文 / 菜单空集合 / 快照兼容

- [ ] `ApprovalProcessRepositoryImpl.withTenantScoped`：JDBC callable 内 `TenantContext.callWithTenant`
      是否在成功与异常两条路径都恢复原 ThreadLocal；`findById` 是否有租户限定；
      save 的实体租户与可信租户不一致是否拒绝。
- [ ] 五个查询方法是否全部走 `withTenantScoped`（当时遗漏 save/update/delete/findById，
      由复核代理补齐——重点看这些后补的方法）。
- [ ] `MenuService.buildScopedTree`：空 `menuIds` = 该维度全量的改动是否会让
      denied/配置损坏的套餐意外放行（denied 分支应先行返回空）。
- [ ] 四个 `PermissionClient` 只加了 `@JsonIgnoreProperties(ignoreUnknown = true)`：
      确认身份校验、成功码校验、权限码校验均未放宽；`roles` 字段未被用于授权判断。

### BE-037（`1731b622`）三平台日历客户端

- [ ] Bean 名 `<platform>CalendarClient` 与 `OfficeCalendarService#getCalendarClient` 一致。
- [ ] 凭据缺失 fail-closed：`secret()` 从环境变量读取，未配置时在发请求前抛业务异常。
- [ ] **设计限制复核**（非缺陷，但需认可）：飞书/钉钉事件接口需父日历标识，
      本系统事件模型无 calendarId，统一用组织者主日历（`organizerId` 承担）；
      `getEventDetail`/`updateEvent` 当时端口签名缺组织者故显式拒绝（BE-038 已扩）。
- [ ] 会议室可用性三平台均显式拒绝——是否接受该边界。

### BE-038（`12d7fdd4`）端口扩展与组织者换算

- [ ] `CalendarPort.getEventDetail/updateEvent` 新增 `organizerId` 是否为契约变更的正确解
      （备选：事件表加 calendarId 列，需迁移，未采纳）。
- [ ] `resolvePlatformUserId`：查 `office_organization_mapping` 反查；未命中/空值/非法平台
      是否 fail-closed 且不发平台请求；是否绝不用系统 ID 充当平台 ID。
- [ ] 创建路径改用 `currentTenantIdReactive()` 的原因（ThreadLocal 在阻塞调度器下丢失）。

### BE-039（`cc3303e4`）平台基址可配置 / 三个落库时间缺陷

- [ ] `apex.office.platforms[*].base-url` 缺省回落官方地址，确认生产行为不变。
- [ ] `create_time`/`update_time` 显式赋值与 `deleteById` 的 `UpdateWrapper.set`：
      **根因是 apex-office 未依赖 apex-infrastructure-persistence，MetaObjectHandler
      不生效**。复核替代方案（模块内显式赋值 + UpdateWrapper）是否可接受，
      或是否倾向另立任务补依赖（依赖边界变更，需另行评审）。
- [ ] 端到端验证使用本地模拟平台（`/tmp/apex-dev/mock-feishu-calendar.py`，临时脚本已停止，
      **不入库**）——确认该验证口径可接受。

### BE-040（`5080efac`）FieldFill 根治

- [ ] 两个 `AuditFieldFillHandler` 用 `metaObject.getValue/setValue` 而非
      `strictInsertFill`（后者依赖 MP TableInfo 注册，纯单测 NPE）——确认该取舍。
- [ ] **auth 属性名是 `createdAt/updatedAt`**（与共享填充器填的
      `createTime/updateTime` 不同）——这是共享填充器即使装配也覆盖不了 auth 的
      原因之一，确认 handler 属性名正确。
- [ ] `OfficeCalendarService` 已移除 BE-039 的临时显式赋值，改由填充器负责——
      确认无遗漏路径（更新/取消/删除的 wrapper 路径不受 fill 影响，BE-039 的
      显式写法保留在仓储层）。
- [ ] auth 的 MFA/TOTP 真实写流程未实测（需外部凭据）——确认可接受。

### FE-026（`95a7f62` 及后续口径提交）

- [ ] 审批裸数组客户端分页：分页数学（total/pages/hasNext/越界页）。
- [ ] 日历时间转换：空格转 T、UTC/偏移换算本地墙钟、未选时间默认当前月。
- [ ] 用户状态 1=正常 0=禁用（与后端 `UserStatus` 契约一致）。
- [ ] 写路径平台标识转小写（后端按 `<platform>CalendarClient` Bean 名查找）。

## 3. 可复跑的验证命令

```bash
# 前端（apex-admin-web）
npx vue-tsc --noEmit
npx vitest run            # 68 文件 / 533 用例
npm run build

# 后端（apex-scaffold，固定 JDK 21.0.13.0+9-tem + mvnd，先 source scripts/build/init-build-env.sh）
mvnd -B -pl :apex-system,:apex-office,:apex-office-service,:apex-workflow-service,:apex-integration-service,:apex-knowledge-service,:apex-auth -am test
mvnd -B -pl :apex-office,:apex-system,:apex-office-service,:apex-integration-service,:apex-workflow-service,:apex-knowledge-service,:apex-auth spotless:check
```

真实链路（需本地环境：podman infra + `/tmp/apex-dev/start-backend.sh` + 模拟平台
`mock-feishu-calendar.py`，均为临时脚本不入库）：经网关 18 条读接口 18/18；
日历 CRUD 端到端 6/6；审批租户隔离（跨租户查询返回不存在）。

## 4. 已知限制（实现方声明，请复核人确认可接受）

1. 真实平台凭据缺失，日历/审批的**平台侧写操作**仅在本地模拟平台验证；
   真实飞书/钉钉/企微 API 的字段语义未经真实环境确认。
2. auth 的 MFA/TOTP 写路径随 BE-040 消除潜在 500，但真实流程未实测。
3. 会议室可用性查询三平台均显式拒绝（需建筑/楼层层级或独立权限）。
4. `CalendarPort` 详情/更新仍以组织者主日历为父资源；若需支持非主日历，
   需给事件表加 calendar_id 列（迁移）+ 端口再扩展——未做，需另立任务。
5. 审批列表 `processName/currentNode/createdAt` 的修复（实体补透传字段）在
   BE-036 二轮完成，真实数据验证过；但审批的**写操作**（发起/同意/撤销）
   依赖真实飞书应用，本地未闭环。
6. 环境脚本（`/tmp/apex-dev/*`）与模拟平台为临时产物，不入库；重建方法
   在两仓记录中有说明。

## 5. 数据与状态

- 验证用测试数据已全部清理（`itest-`/`read-check-`/`E2E ` 前缀记录数 0）。
- 两仓任务卡：BE-036~BE-040、FE-026 统一为「待复核」，复核通过后由主控/复核人
  推进「已完成」。
- 台账：前端 #37~#42、后端 #44~#51（append-only）。
- 方案：两仓 `docs/specs/2026-11-17-*.md`（均为事后补记，非事前评审）。
