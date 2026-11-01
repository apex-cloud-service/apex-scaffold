# 记忆卡 · apex-scaffold（后端基座）

> **用法**：新会话 / 换机器先读本卡（约 3 分钟），需要细节再按文末指针进入知识库与文档。
> **基线**：2026-11-01 实测（源码 + `git log` + `pom.xml`）· 版本 1.3.1 · 33 个 reactor 模块
> **权威优先级**：源码 > `AGENTS.md` > `docs/` > `.workbuddy/knowledge/` > 本卡。本卡是**导航与结论**，不是规范原文。

## 1. 一句话

Spring Boot 3.5.15 / Java 21 / WebFlux 纯响应式 / DDD 的 Java 后端基座：**API 网关 + 五个业务服务 + 任务代理 + 可选脱敏**，业务代码以库模块复用，运行模块只负责启动与装配。仓库不含前端源码（前端独立仓 `apex-admin-web`）。

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
| `apex-auth-service` | 8081 | `apex_auth`（dev `apex_auth_dev`） | V60 |
| `apex-system-service` | 8082 | `apex_system` | V80 |
| `apex-workflow-service` | 8083（同机联调 8093） | `apex_workflow` | V5 |
| `apex-office-service` | 8084 | `apex_office` | V7 |
| `apex-file-service` | 8085 | `apex_file` | V7 |
| `apex-job-service` | 8095 | 无（固定代理 system） | — |
| `apex-masking-service` | 8096 | 无 | — |
| `apex-boot-all` | 缺省 8080 | 单进程组合多库 | 复用组件迁移 |

- 迁移**按服务归位**在业务库 / 运行模块的 `src/main/resources/db/migration/{mysql,postgresql}`。注意物理位置有历史遗留：system 迁移在 **`apex-system`**（不在 `-service`）、auth 迁移在 **`apex-auth-service`**（不在 `apex-auth`）；新增时跟随现有位置，不要另起一套。
- 规模：MySQL 116 + PostgreSQL 116 = **232 个已跟踪迁移**；`apex-integration` 另有 V1（未跟踪，随模块一起在途）。
- system 管理端口缺省 8083，**会与 workflow 冲突**，同机必改。

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

## 6. 当前状态（2026-11-01）

- **规模**：主源码 1889 个 Java 文件、测试类 653 个；运行入口 9 个（8 部署单元 + `apex-boot-all` 组合档）。
- **主线《评审收口调整方案》**（`docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md`）：
  - 批次 **A（A1–A7 统一认证）已完成并提交**（10-31，含测试报告 `docs/06-testing/jwt-unification/20261031-a1-a2/report.md`）。
  - 批次 **B（B1–B9 多租户）已完成并提交**（10-31～11-01，B9 为 M2 收官）。
  - 批次 **C 主体已提交**（C0–C3、C7–C9：DDD 基线、starter 裁剪、SPI、组件入口自动装配、事件通道自适应、`apex-boot-all`）；**C4（`apex-system` interfaces 迁移）未做**。
  - 批次 **D 部分提交**（D1 统一消息门面 / D2 开放平台 / D4 能力目录 / D6 前台接入契约）；D3、D5 未启动。
  - 批次 **E1 在途未跟踪**：`apex-integration` 新模块（连接器域 + V1 迁移），工作树 `??` 状态。
- **工作树**：13 项（2026-11-01 15:46 实测：4 修改 + 5 删除 + 本卡新增 + 3 未跟踪；**本卡提交后为 12 项**）。修改 `.gitignore`/`README`/`pom.xml`，`weekly-reports/` 删除并迁入 `periodic-reports/`，`apex-integration` 未跟踪。
- **结构债**：`apex-system` 仍四层合一（interfaces/application/domain/infrastructure 同包）。

## 7. 下一步

1. 提交 / 验收 `apex-integration`（批次 E1），补 E1 测试报告与 `apex-services-bom` 装配说明。
2. 执行 **C4**：`apex-system` 的 interfaces 层迁至 `apex-system-service`（方案 D07）。
3. 批次 D 剩余：D3 能力目录文档、D5 共享数据服务层。
4. P0 迁移验证仍未准出（`verified_union` 未达 586/586），Java 21 正式切换口径未收口。

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
