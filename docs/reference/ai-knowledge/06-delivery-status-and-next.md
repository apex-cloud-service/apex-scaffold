# 06 · 交付状态与下一步（2026-11-05）

> 主线唯一依据：`docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md`（v3.1，「执行中」）。
> 本页是**结论 + 指针**；每批次的验证细节在各批测试报告内。

## 1. 五批次状态

| 批次 | 主题 | 状态（2026-11-01） | 证据 |
| --- | --- | --- | --- |
| **A** | 统一认证收口 A1–A7 | **已完成并提交**（A6 随 B1 同批交付） | `docs/06-testing/jwt-unification/20261031-a1-a2/`、`20261031-a3-a7/` |
| **B** | 多租户完整化 B1–B9 | **已完成并提交**（B9 = M2 收官） | `20261031-b1-a6/`、`b2`～`b9/` 各目录 |
| **C** | 组件化服务化 | **除 C4 外完成**：C0/C1/C2、C3、C7、C8、C9（11-02 补 C8/C9 验证） | `20261101-c0-c2/`、`c3-c8/`、`c7/`、`c9/`、`20261102-gap-closure/` |
| **D** | 中台架构 | **D1/D2/D4/D5/D6 完成**（D5 于 11-02 提交）；D3 文档沉淀 | `20261101-d1-d6/report.md`、`20261102-gap-closure/` |
| **E** | 集成与 IoT | **E1–E4 已完成并提交**（11-02）：connector/device 双子域 + `apex-integration-service` 8094 + EMQX 真实接入 | `20261102-m5/`、`live-verification/`、`acl-and-fullstack/` |

**2026-11-10 后续整改**：针对 `docs/08-project-management/reports/middle-platform-architecture-review.md` 的 **G1–G8** 八项差距，已按 `docs/08-project-management/plans/middle-platform-remediation-plan.md` 实施完成并提交（`3b61027b`），`clean verify` 6,251 个测试通过（0 失败 / 33 跳过，来源 `docs/06-testing/middle-platform/20261110-completion/report.md`）；真实 Nacos/MySQL/Redis、RocketMQ/EMQX、Seata TC、告警触达与第三方消费方验收**仍未验证**，不得计入通过项。

**已落地能力快照**

- 认证：双算法验签（RS256 + HMAC 过渡）、JWKS 闭环、撤销传播、会话/MFA 接线、OAuth2 consent 收口、认证期租户校验、统一授权默认化。
- 多租户：租户编码统一 `default`、主库 RBAC 租户化、套餐与功能裁剪、开通引导、配额真实执行、租户级配置 V79、隔离收敛、租户管理员自助、租户化缓存键（`TenantCacheKeys`）。
- 组件化：DDD 守卫基线、starter JAR 级裁剪、api-contract 去死代码、SPI 扩展点、业务库 `AutoConfiguration.imports` 入口、事件通道自适应、`apex-boot-all` 组合部署（`BootAllApplication` 不做全根扫描）。
- 中台：统一消息门面（`NotifyApplicationService` + `NotifyChannelPort`，IM/收件箱通道）、开放平台（`apex-system` 侧 `OpenAppAdminRouter`/`InternalOpenAppRouter`/`OpenUserRouter` + `open-platform-integration.md`；auth 侧 `OpenAppRouter`/`OpenAppHandler` 已于 11-07 移除）、能力目录（`CapabilityCatalog` + `CapabilityRouter`）、前台接入契约。

## 2. 结构性现状（与批次前相比的变化）

| 项 | 变化 |
| --- | --- |
| reactor 模块 | 31 → **36**（新增 `apex-boot-all`、`apex-integration`、`apex-integration-service`、`apex-aggregation-service`、`apex-tools-deployment`、`apex-tools-build`）；2026-11-14 两级布局后为根直接 7 组、递归 **44** 个模块、含根 **45** 个 reactor 项目（6 个新 POM + 复用 services-bom 不计业务 JAR） |
| 运行入口 | 8 → **13**（六业务服务 + 网关 + 只读查询聚合 + 示例 + 任务代理 + 脱敏 + 知识库 + 组合档） |
| 迁移 | system V75 → **V91**（在 `apex-components-parent/apex-system`）；auth V59 → **V65**；office **V10**、file V7、workflow V6、integration V3、knowledge **V2**；合计 **282** 个双方言文件 |
| 主源码/测试 | 1842/610 → **2249/780**（git 跟踪口径，2026-11-19） |
| `apex-system` 四层合一 | **未变**，C4/D07 仍待执行 |
| `apex.tenant.enabled` | 缺省仍 **false**（basic/standard），Enterprise 档开启；B5 起配额守卫与该开关解耦 |

## 3. 下一步（按优先级）

1. **C4**：`apex-system` interfaces 层迁至 `apex-system-service`（21 Router / 44 Handler 量级），完成后 M3 收官。
2. **在途改动收口**：11-07～11-08 的 68 项在途改动（文档与架构图改写、认证 MFA 与开放文件路由、许可证公钥与发布配置、部署/发布脚本）**已提交**；11-10 的 54 项中台整改后续与 11-11 文档对齐批次（`60d78304`～`5e29aca5`）亦已提交；11-12 的聚合服务/统一待办/办公撤销、11-13 的七组结构与 K8s 八服务拓扑、11-14 的知识库与两级物理归位（`a63e6666`/`82c0632e`/`81b16a51`/`93d0015b`）同批提交后，11-14 的 B5/B6（`1f60da62` 测试路径适配、`e968a02d` 文档与图表同步）亦已提交，当前工作树 **16** 项在途（2026-11-14 17:10 实测：全部为已跟踪文件修改，两级布局收尾的 `.gitignore`、`README` 与测试/方案文档）；后续提交前仍跑 `mvnd -T4 -B clean verify`。
3. **P0 迁移验证**：`verified_union` 未达 586/586，Java 21 正式切换结论仍未收口 —— 这是口径问题，不是代码问题，汇报时必须显式声明。
4. **集成域后续**：设备影子 desired 写接口、规则引擎动作扩展、EMQX 生产部署参数（租户命名空间）按需排期。

## 4. 证据与口径纪律

| 事项 | 口径 |
| --- | --- |
| 「已完成」 | 必须能指到测试报告目录（`docs/06-testing/jwt-unification/<日期>-<批次>/report.md`） |
| 全量构建 | 只认 `mvnd -T4 -B clean verify` 的实际输出；单模块构建要说明范围 |
| Seata 四模式 | 缺 TC/双库时跳过，**不能记为通过** |
| 工作树在途 | 未跟踪/未提交内容不得计入「已交付」；当前 **16** 项在途（2026-11-14 17:10 实测，全部为已跟踪文件修改），68 项在途改动已于 11-07～11-08 提交、54 项中台整改后续已于 11-10～11-11 提交、11-12～11-14 聚合服务/七组结构/知识库/B5-B6 批次已提交 |
| 汇报数据 | 禁用 `git log --shortstat` 的 LOC；用提交数 / 模块数 / 测试类数 |

## 5. 跨项目待办

| 项目 | 待办 |
| --- | --- |
| apex | C4 → P0 迁移验证收口（68 项在途改动已提交）；集成域后续能力按需 |
| web | 11-03 已补齐租户套餐/能力运营/开放平台/集成中心/IoT/统一通知/在线会话/consent；剩业务侧租户切换器、IoT 影子 desired 写入 |
| cold | 仓单服务费继承改造（P0）、费率常量订正 4%/7%、盘点分区与调拨对账回归；详见 `cold-chain/MEMORY-CARD.md` |
