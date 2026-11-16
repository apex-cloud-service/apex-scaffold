# 记忆卡 · apex-scaffold（后端基座）

> **用法**：新会话 / 换机器先读本卡（约 3 分钟），需要细节再按文末指针进入知识库与文档。
> **基线**：2026-11-16 实测（源码 + `git log` + `pom.xml`）· 版本 1.3.1 · 根直接聚合 7 组、递归 44 个模块、含根 45 个 reactor 项目；第二阶段叶子物理嵌套于各分组父目录
> **仓库基线**：默认工作分支 **`dev`**（长期分支 `master`/`dev`/`test`，`main` 已删除）· 远端 `origin` = `git@github.com:apex-cloud-service/apex-scaffold.git`（固定 SSH 443）· 许可 **MIT** + `LICENSE-COMMERCIAL.md` 商业双轨
> **权威优先级**：源码 > `AGENTS.md` > `docs/`（含 `docs/reference/ai-knowledge/`，原 `.workbuddy/knowledge/` 已于 11-16 迁入）> 本卡。本卡是**导航与结论**，不是规范原文；私有目录（`.workbuddy/`、`.zcode/`、`.trae/`）不入版本库，只留指针。

## 1. 一句话

Spring Boot 3.5.15 / Java 21 / WebFlux 纯响应式 / DDD 的 Java 后端基座：**API 网关 + 六个业务服务 + 只读查询聚合 + 任务代理 + 可选脱敏 + 可选知识库 + 组合部署档**，业务代码以库模块复用，运行模块只负责启动与装配。仓库不含前端源码（前端独立仓 `apex-admin-web`）。

## 2. 构建（唯一合法路径）

```bash
cd /Users/work/apex-scaffold
source scripts/build/init-build-env.sh    # 必须在当前 shell source，子进程无效
mvnd -T4 -B clean verify                  # 全量基线
```

- JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；**缺 mvnd 直接失败，不得回退系统 mvn**。
- 不得依赖 `java/current` 软链；换过 JDK 先 `mvnd --stop` 清 daemon。
- 本地仓库固定 `~/.m2/repository`；**禁止** `-Dmaven.repo.local` 指向 `target/`（`clean` 会连仓库带产物一起删）。
- 统一编排入口 `bin/apex.sh`（属 `apex-tools-build`）：只做编排，委托既有脚本与 mvnd；其 `build-manifest.yaml` 是模块注册表单一事实来源，由 `BuildManifestSyncTest` 校验与根 POM **递归聚合**模块集合（不含总根）、各模块打包插件、`release.sh SERVICES` 一致。

## 3. 拓扑、端口、库、迁移

| 运行入口 | 端口 | 数据库 | 迁移最高版本 |
| --- | --- | --- | --- |
| `apex-gateway` | 8080 | `apex_gateway`（访问日志，可选） | — |
| `apex-auth-service` | 8081 | `apex_auth`（dev `apex_auth_dev`） | V65 |
| `apex-system-service` | 8082（管理 8083） | `apex_system` | V91 |
| `apex-workflow-service` | 8093 | `apex_workflow` | V6 |
| `apex-office-service` | 8084 | `apex_office` | V10 |
| `apex-file-service` | 8085 | `apex_file` | V7 |
| `apex-integration-service` | 8094（管理 8097） | `apex_integration` | V3 |
| `apex-knowledge-service` | 8100 | `apex_knowledge`（可选档） | V2 |
| `apex-aggregation-service` | 8098 | 无（只读聚合，无 DB/迁移/事务） | — |
| `apex-job-service` | 8095 | 无（固定代理 system） | — |
| `apex-masking-service` | 8096 | 无 | — |
| `apex-example` | 8080 | `apex_example` | — |
| `apex-boot-all` | 缺省 8080 | 单进程组合多库 | 复用组件迁移 |

- 迁移**按服务归位**在业务库 / 运行模块的 `src/main/resources/db/migration/{mysql,postgresql}`。注意物理位置有历史遗留：system 迁移在 **`apex-components-parent/apex-system`**（不在 `-service`）、auth 迁移在 **`apex-services-bom/apex-auth-service`**（不在 `apex-auth`）、integration 迁移在 **`apex-components-parent/apex-integration`**（库模块，不在 `-service`）；11-14 两级布局后模块目录前面带分组父目录，`maintain.py` 的迁移键也随之变为 `分组/模块`；新增时跟随现有位置，不要另起一套。
- 规模：MySQL 141 + PostgreSQL 141 = **282 个已跟踪迁移**（office 域 11-12～11-13 增 V9/V10；system V91 与 knowledge V1 为 11-14 知识库落地新增、knowledge V2 与 auth V65 为 11-15～11-16 加固批次新增，均双方言配对）。
- system 管理端口缺省 8083；workflow 业务端口 2026-11-07 起为 8093，两者不再冲突，同机直跑无须改端口。
- 组合档 `apex-boot-all` 只装配不实现，无自有 `application.yml`；多实例部署必须显式选 MQ，否则 `apex.events.channel=auto` 退化为进程内派发。

## 4. 硬约束（违反即返工）

1. 业务 HTTP 一律 **RouterFunction + Handler**，禁 `@Controller` / `@RestController`；`@RouterOperation` 必须与实际路径、方法、参数一致。
2. 响应统一 `Result<T>`（`code`/`msg`/`data`/`traceId`），`ResultCode.SUCCESS` = **0**；OAuth2/JWKS/文件流/健康检查保持原协议。
3. 分层单向：domain 不依赖 interfaces/infrastructure/MyBatis-Plus；common、共享 domain/infra、api-contract **不得反向依赖具体业务库**；构造器注入。
4. 阻塞 I/O（JDBC、同步 Redis、文件、同步 SDK/HTTP）必须经 `BlockingIoSchedulers` 隔离 —— WebFlux 不会替你做非阻塞。
5. 写操作挂 `@OperationLog`；管理接口对齐 `@RequirePermission` 与 Flyway 权限种子；新增用户可见文案同步所属模块 i18n。
6. 文档遵循 `docs/08-project-management/DOCS_STRUCTURE_STANDARD.md`：最多 3 级、每目录有 `README.md`、禁按时间/作者/状态建目录。

## 5. 陷阱（已踩过，照抄结论）

| 现象 | 根因 → 处置 |
| --- | --- |
| `@DataScope` / `@DataSource` 偶发失效 | `BlockingIoSchedulers.jdbc()` 不传播 ThreadLocal/MDC → 在 callable 内显式装配上下文 |
| 数据源切换没生效 | `@DataSource` 方法体在装配期执行 → 查询用 `Mono.fromCallable` / `defer` 延迟 |
| CORS 配置不生效 | YAML List 注入 `@Value List` 解析为空 → 用逗号分隔字符串 |
| MQ 整体不装配 | rocketmq-spring-boot 2.3.3 以 `rocketmq.name-server` + `producer.group` 存在性为准 |
| 本地冒烟随机端口 | IDE 注入 `SERVER__PORT=0` → 显式 `SERVER_PORT=8080` |
| 请求合并内存泄漏 | `InFlightRequestMerge` 用 `remove(k, shared)` → 改 `doFinally` 自引用移除 |
| License 门禁不生效 | 切面被改成 `@Component` → 必须留在 `LicenseAutoConfiguration` 的 `@Bean` |
| 分页报错 / 触发全表解析 | 误引 `mybatis-plus-jsqlparser` → 靠 PageHelper 兼容层 |
| 汇报 LOC 失真 | `git log --shortstat` 被 HTML 产物放大 → 只报提交数 / 模块数 / 测试类数 |
| 统一授权「默认开启」判断错误 | 组件库缺省 true，但 `apex-services-bom/apex-system-service/application.yml` 覆盖为 `${APEX_AUTH_CENTER_PERMISSION_ENABLED:false}` → 排查按「运行模块 yml → 环境变量 → 组件默认」 |
| 集成服务启动即失败 | 连接器/设备子域依赖外部 EMQX；本地无 broker 时需显式关闭 MQTT 并核 `hivemq-mqtt-client` 为 optional |

## 6. 当前状态（2026-11-16）

- **规模**：主源码 **2244** 个 Java 文件、测试类 **776** 个；启动入口 **13** 个（12 个部署单元含 `apex-knowledge-service` 8100 与 `apex-example` + `apex-boot-all` 组合档）。
- **11-15～11-16 加固批次**（`8d489f4e`/`acc7baad`/`0e0c480e`/`ef5c4337`/`db968c70`/`72773898`/`2807632e`）：JWKS kid 选钥分支补齐 file/integration/aggregation、阻塞调度器租户上下文、masking 令牌家族兼容、auth 刷新令牌事务内原子轮换与重放撤销（auth **V65**）、知识库文档删除改持久清理凭证并统一问答错误协议（knowledge **V2**）、Javadoc 与构造器注入收口；未改变模块与端口拓扑。
- **主线《评审收口调整方案》**（`docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md`）：
  - 批次 **A（A1–A7 统一认证）已完成并提交**（10-31）。
  - 批次 **B（B1–B9 多租户）已完成并提交**（10-31～11-01，B9 为 M2 收官）。
  - 批次 **C 除 C4 外完成**（C0–C3、C7–C9；11-02 补齐 C8/C9 验证）。
  - 批次 **D 完成 D1/D2/D4/D5/D6**（D5 共享数据服务层 11-02 提交）；**D3 为文档沉淀**，`/open-api` 路由已补。
  - 批次 **E1–E4 已完成并提交**（11-02）：`apex-integration`（connector/device 双子域）+ `apex-integration-service` 8094，含 EMQX ACL 与真实环境验证。
- **中台架构整改 G1–G8**（`docs/08-project-management/plans/middle-platform-remediation-plan.md`）：11-10 提交 `3b61027b` 实施完成——服务侧能力二次校验默认开启、auth 域分层倒置清理、治理字段与守卫覆盖补齐；`clean verify` 6,251 测试通过，真实 Nacos/MySQL/RocketMQ/EMQX/Seata TC 与第三方消费方验收仍未验证。
- **Maven 分组**：`63e858b1`（11-13）根聚合收拢为七组并新增 6 个分组 parent（根直接 7 组、递归 44 模块/含根 45 项目，11-14 两级布局后计数；22 叶子迁移 parent、13 保留 services-bom 继承），`bba338e5` 同步文档与图表；11-13 同批提交聚合服务、办公双库迁移回归与 K8s 八服务拓扑；11-14 第二阶段叶子物理归位（37 叶子移入分组父目录、-pl 冒号化、Containerfile 两级化，起于 `4d1d9008`/`82c0632e`/`81b16a51`/`93d0015b`）。**11-14 分组收尾核验入库**（CI 八服务矩阵、start-local 数据源注入收窄、R-01 关闭、文档基线刷新，见 14.6 节），**同日 RAG 知识库落地提交**（后端 `a63e6666` 140 文件 / 前端 `1efc3cf` 8 文件，45/45 模块 verify 全绿），同日 B5/B6（`1f60da62` 测试路径适配、`e968a02d` 文档与图表同步）提交后，11-14 17:10 实测工作树 **16 项全部为修改**（`.gitignore`、`README` 与若干测试/方案文档的两级布局收尾），**不得把在途改动当作已提交**。
- **结构债**：`apex-system` 仍四层合一（interfaces/application/domain/infrastructure 同包），870 个主源码文件；C4/D07 已于 2026-11-01 裁决维持现状不实施（记录于 `docs/08-project-management/plans/platform-closure-remediation-plan.md`）。

## 7. 下一步

1. **C4 已裁决维持现状不实施**（interfaces 层留在 `apex-system`，记录于 platform-closure-remediation-plan.md），不再作为待办；M3 组件化按后续排期推进。
2. 后续提交前先跑 `mvnd -T4 -B clean verify`；改动网关过滤器或事件桥时仍需复核租户/MDC 传播（11-07～11-08 的 68 项在途改动已提交）。
3. P0 迁移验证仍未准出（`verified_union` 未达 586/586），Java 21 正式切换口径未收口 —— 属口径问题，汇报须显式声明。
4. 方案 E 之后的 P1 项（集成域规则引擎、设备影子 desired 写接口）按需排期。

## 8. 指针

| 想知道 | 去哪 |
| --- | --- |
| 协作约束（唯一权威） | `AGENTS.md` |
| 项目地图 / 模块职责 | `docs/reference/ai-knowledge/01-project-map.md` |
| 架构现状与完成度 | `docs/reference/ai-knowledge/02-apex-scaffold-current-state.md` |
| 约定与陷阱全文 | `docs/reference/ai-knowledge/03-conventions-and-pitfalls.md` |
| 批次状态与证据缺口 | `docs/reference/ai-knowledge/06-delivery-status-and-next.md`、方案原文 |
| **变更流水台账（每次改动登记）** | `docs/reference/ai-knowledge/07-change-memory.md` |
| 路由登记 | `docs/03-api/API_ROUTES.md` |
| 跨仓变动台账 | `.workbuddy/memory/PROJECTS.md`（私有目录，不入版本库） |

## 9. 维护规则

- 只在**结构性变化**（新增/删除模块、迁移归属变更、批次推进、端口或库变更）时改本卡，并同步知识库对应篇。
- 所有数字带采集日期；与源码冲突时以源码为准并回改本卡。
- 卡片保持一页：新增内容先进知识库，本卡只留结论与指针。
- **变更必登记**：任何改动都要在 `docs/reference/ai-knowledge/07-change-memory.md` 追加一条（六字段：日期/类型/摘要/提交/证据/影响面），并在提交信息末行写 `memory: 07-change-memory.md#<条目>`；规则见 `docs/specs/project-governance.md` §5.4。
- **层级契约（与本卡配对）**：`.workbuddy/memory/MEMORY.md` 是每轮自动注入的**子集**（无指针表），本卡是可导航的**全集**。冲突时以本卡与源码为准；**改任一处必须同日同步另一处**——历史上测试基线、版本号、工作树计数曾两处不一致。
- 超 30 天的当日日志移入 `.workbuddy/memory/_archive/YYYY-MM/`，不再留在 `memory/` 根层。
