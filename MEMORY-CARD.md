# 记忆卡 · apex-scaffold（后端基座）

> **用法**：新会话 / 换机器先读本卡（约 3 分钟），需要细节再按文末指针进入知识库与文档。
> **基线**：2026-11-04 实测（源码 + `git log` + `pom.xml`）· 版本 1.3.1 · 34 个 reactor 模块
> **权威优先级**：源码 > `AGENTS.md` > `docs/` > `.workbuddy/knowledge/` > 本卡。本卡是**导航与结论**，不是规范原文。

## 1. 一句话

Spring Boot 3.5.15 / Java 21 / WebFlux 纯响应式 / DDD 的 Java 后端基座：**API 网关 + 六个业务服务 + 任务代理 + 可选脱敏 + 组合部署档**，业务代码以库模块复用，运行模块只负责启动与装配。仓库不含前端源码（前端独立仓 `apex-admin-web`）。

## 2. 构建（唯一合法路径）

```bash
cd /Users/work/apex-scaffold
source scripts/build/init-build-env.sh    # 必须在当前 shell source，子进程无效
mvnd -T4 -B clean verify                  # 全量基线
```

- JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；**缺 mvnd 直接失败，不得回退系统 mvn**。
- 不得依赖 `java/current` 软链；换过 JDK 先 `mvnd --stop` 清 daemon。
- 本地仓库固定 `~/.m2/repository`；**禁止** `-Dmaven.repo.local` 指向 `target/`（`clean` 会连仓库带产物一起删）。

## 3. 拓扑、端口、库、迁移

| 运行入口 | 端口 | 数据库 | 迁移最高版本 |
| --- | --- | --- | --- |
| `apex-gateway` | 8080 | `apex_gateway`（访问日志，可选） | — |
| `apex-auth-service` | 8081 | `apex_auth`（dev `apex_auth_dev`） | V61 |
| `apex-system-service` | 8082（管理 8083） | `apex_system` | V88 |
| `apex-workflow-service` | 8083（同机联调 8093） | `apex_workflow` | V6 |
| `apex-office-service` | 8084 | `apex_office` | V8 |
| `apex-file-service` | 8085 | `apex_file` | V7 |
| `apex-integration-service` | 8094（管理 8097） | `apex_integration` | V3 |
| `apex-job-service` | 8095 | 无（固定代理 system） | — |
| `apex-masking-service` | 8096 | 无 | — |
| `apex-boot-all` | 缺省 8080 | 单进程组合多库 | 复用组件迁移 |

- 迁移**按服务归位**在业务库 / 运行模块的 `src/main/resources/db/migration/{mysql,postgresql}`。注意物理位置有历史遗留：system 迁移在 **`apex-system`**（不在 `-service`）、auth 迁移在 **`apex-auth-service`**（不在 `apex-auth`）、integration 迁移在 **`apex-integration`**（库模块，不在 `-service`）；新增时跟随现有位置，不要另起一套。
- 规模：MySQL 130 + PostgreSQL 130 = **260 个已跟踪迁移**。
- system 管理端口缺省 8083，**会与 workflow 冲突**，同机必改。
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
| 统一授权「默认开启」判断错误 | 组件库缺省 true，但 `apex-system-service/application.yml` 覆盖为 `${APEX_AUTH_CENTER_PERMISSION_ENABLED:false}` → 排查按「运行模块 yml → 环境变量 → 组件默认」 |
| 集成服务启动即失败 | 连接器/设备子域依赖外部 EMQX；本地无 broker 时需显式关闭 MQTT 并核 `hivemq-mqtt-client` 为 optional |

## 6. 当前状态（2026-11-04）

- **规模**：主源码 **2099** 个 Java 文件、测试类 **711** 个；启动入口 **10** 个（9 个部署单元 + `apex-boot-all` 组合档）。
- **主线《评审收口调整方案》**（`docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md`）：
  - 批次 **A（A1–A7 统一认证）已完成并提交**（10-31）。
  - 批次 **B（B1–B9 多租户）已完成并提交**（10-31～11-01，B9 为 M2 收官）。
  - 批次 **C 除 C4 外完成**（C0–C3、C7–C9；11-02 补齐 C8/C9 验证）。
  - 批次 **D 完成 D1/D2/D4/D5/D6**（D5 共享数据服务层 11-02 提交）；**D3 为文档沉淀**，`/open-api` 路由已补。
  - 批次 **E1–E4 已完成并提交**（11-02）：`apex-integration`（connector/device 双子域）+ `apex-integration-service` 8094，含 EMQX ACL 与真实环境验证。
- **工作树**：27 项未提交（2026-11-03 实测），集中在网关/事件桥/监控编排与部署脚本；**不得把在途改动当作已交付**。
- **结构债**：`apex-system` 仍四层合一（interfaces/application/domain/infrastructure 同包），859 个主源码文件，C4/D07 未执行。

## 7. 下一步

1. 执行 **C4**：`apex-system` 的 interfaces 层迁至 `apex-system-service`（方案 D07），收口 M3。
2. 提交当前 27 项在途改动前先跑 `mvnd -T4 -B clean verify`，并核对网关过滤器与事件桥的租户/MDC 传播。
3. P0 迁移验证仍未准出（`verified_union` 未达 586/586），Java 21 正式切换口径未收口 —— 属口径问题，汇报须显式声明。
4. 方案 E 之后的 P1 项（集成域规则引擎、设备影子 desired 写接口）按需排期。

## 8. 指针

| 想知道 | 去哪 |
| --- | --- |
| 协作约束（唯一权威） | `AGENTS.md` |
| 项目地图 / 模块职责 | `.workbuddy/knowledge/01-project-map.md` |
| 架构现状与完成度 | `.workbuddy/knowledge/02-apex-scaffold-current-state.md` |
| 约定与陷阱全文 | `.workbuddy/knowledge/03-conventions-and-pitfalls.md` |
| 批次状态与证据缺口 | `.workbuddy/knowledge/06-delivery-status-and-next.md`、方案原文 |
| 路由登记 | `docs/03-api/API_ROUTES.md` |
| 跨仓变动台账 | `.workbuddy/memory/PROJECTS.md` |

## 9. 维护规则

- 只在**结构性变化**（新增/删除模块、迁移归属变更、批次推进、端口或库变更）时改本卡，并同步知识库对应篇。
- 所有数字带采集日期；与源码冲突时以源码为准并回改本卡。
- 卡片保持一页：新增内容先进知识库，本卡只留结论与指针。
- **层级契约（与本卡配对）**：`.workbuddy/memory/MEMORY.md` 是每轮自动注入的**子集**（无指针表），本卡是可导航的**全集**。冲突时以本卡与源码为准；**改任一处必须同日同步另一处**——历史上测试基线、版本号、工作树计数曾两处不一致。
- 超 30 天的当日日志移入 `.workbuddy/memory/_archive/YYYY-MM/`，不再留在 `memory/` 根层。
