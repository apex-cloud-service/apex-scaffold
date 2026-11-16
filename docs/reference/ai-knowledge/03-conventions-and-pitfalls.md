# 03 · 约定与陷阱（apex）

> 违反即返工。本文是提炼，**权威原文为 `/Users/work/apex-scaffold/AGENTS.md`**，冲突时以 AGENTS.md 为准。

## 1. 构建环境（唯一合法路径）

```bash
cd /Users/work/apex-scaffold
source scripts/build/init-build-env.sh     # 必须在当前 shell source
java -version                              # 期望 21.0.13
mvnd --version                             # 期望 2.0.0-rc-3
mvnd -T4 -B clean verify
```

- 固定 JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；**不得依赖 `java/current` 链接**。
- **缺 mvnd 直接失败**，不得回退系统 `mvn`。
- 换过 JDK 先 `mvnd --stop`，否则 daemon 用旧 JDK 编译出诡异错误。
- 本地仓库固定 `~/.m2/repository`；**禁止** `-Dmaven.repo.local` 指向 `target/`（`clean` 会连产物带仓库一起删）。

## 2. 模块与依赖

- 新增能力先想清楚归属：**system 专属**实体/仓储/Mapper/适配器留在 `apex-system`，不要往共享 `apex-domain` / `apex-infrastructure-*` 塞。
- common、共享 domain/infra、api-contract **不得反向依赖具体业务库**。
- 业务库依赖共享设施前先看目标 POM，不要机械套「所有业务库都依赖 infrastructure」。
- `apex-infrastructure` 只是旧坐标兼容壳，**不要往里加源码**。

## 3. 接口层

| 规则 | 说明 |
| --- | --- |
| 只用 RouterFunction + Handler | 禁 `@Controller`、`@RestController`、注解式映射 |
| Handler 只做校验 + 调应用服务 | 不堆持久化与核心业务规则 |
| `@RouterOperation` 必须真实 | 路径、方法、参数、响应与实际一致 |
| 权限与种子一致 | `@RequirePermission` ↔ Flyway 权限种子 ↔ 管理角色 |
| 写操作挂审计 | `@OperationLog` → `sys_operation_log` |
| 公网禁入 | `/internal/**` 对外 403 |
| office 双路由 | 消息 HTTP 与 WS 握手必须指向**同一实例**（票据与连接注册表是单实例内存态） |

## 4. 响应与异常

- 业务 JSON 统一 `Result<T>`：`code` / `msg` / `data` / `traceId`；**`ResultCode.SUCCESS` = 0，不是 HTTP 200**。
- OAuth2、JWKS、文件流、健康检查保持原协议，不强行包装。
- 业务失败用 `BusinessException` / `ResultCode`；技术异常交给既有 WebFlux 边界（`GlobalExceptionHandler(WebExceptionHandler)` + `@RestControllerAdvice` 兜底）。
- **不要吞异常**，也不要把失败改写成成功。
- 新增用户可见文案同步所属模块 `i18n`（system 在 `apex-system/src/main/resources/i18n/`）。

## 5. 数据与迁移

- 迁移**按服务归位**：`apex-system` / `apex-auth-service` / `apex-file-service` / `apex-office-service` / `apex-workflow-service` 各自的 `db/migration/{mysql,postgresql}`。
- 双方言必须同时补；迁移一旦合并**不可改历史文件**，只能追加新版本（2026-11-16 实测最高：system **V91**、auth **V65**、file **V7**、office **V10**、workflow **V6**、knowledge **V2**；合计 282 个文件，`apex-integration` V1–V3 已跟踪）。
- 注意迁移**物理位置与逻辑归属不一致的历史遗留**：system 迁移在 `apex-system`（不在 `apex-system-service`），auth 迁移在 `apex-auth-service`（不在 `apex-auth`）。新增时跟随现有位置，别另起一套。
- 测试里断言迁移数量的用例（`FlywayMigrationStartupTest` 等）要随版本更新，否则红灯。
- 分页依赖 PageHelper 兼容层，**勿引 `mybatis-plus-jsqlparser`**。
- 分页 DTO 有多份 `PageResponse`（common / system / file 各一份；2026-11-08 起 system 与 file 版已补派生 `hasNext`，字段集与 common 对齐），改前仍先确认实际类型与契约测试。

## 6. 阻塞 I/O

JDBC、MyBatis-Plus、同步 Redis/Redisson、文件 I/O、同步存储 SDK、同步 HTTP **一律**经 `BlockingIoSchedulers` 隔离。WebFlux 不会把它们变非阻塞。

## 7. 已踩过的坑（照抄结论即可）

| 现象 | 根因 | 处置 |
| --- | --- | --- |
| `@DataScope` / `@DataSource` 偶发失效 | `BlockingIoSchedulers.jdbc()` 不传播 ThreadLocal/MDC | 在 callable 内显式装配上下文 |
| 数据源切换没生效 | `@DataSource` 方法体在**装配期**执行，`doFirst` 到**订阅期**才生效 | 查询用 `Mono.fromCallable` / `defer` 延迟 |
| CORS 配置不生效 | `cors.allowed-origins` 用 YAML List 注入 `@Value List` 解析为空 | 用**逗号分隔字符串** |
| MQ 组件整体不装配 | rocketmq-spring-boot 2.3.3 以 `rocketmq.name-server` + `producer.group` 存在性为准 | 两者同时提供，否则视为不启用 |
| 本地冒烟端口随机 | IDE 注入 `SERVER__PORT=0` | 显式 `SERVER_PORT=8080` |
| 请求合并内存泄漏 | `InFlightRequestMerge` 用 `remove(k, shared)` | 改 `doFinally` 自引用移除 |
| 指标断言「不存在」误判 | 未注册的 meter | 用 `registry.find()` 而非直接取值 |
| License 门禁不生效 | 切面被改成 `@Component`，脱离自动装配 | 必须留在 `LicenseAutoConfiguration` 的 `@Bean` |
| 改了组件库默认值但运行期没生效 | 运行模块 `application.yml` 用 `${ENV:默认值}` 覆盖了组件库默认（如 `apex.features.auth-center-permission`：`apex-system` 缺省 true，`apex-system-service` 覆盖为 false） | 排查顺序：运行模块 yml → 环境变量 → 组件库默认值 |
| 租户 SQL 抛「租户上下文未装配」 | `DefaultTenantLineHandler` fail-closed，异步/调度线程未传播上下文 | 跨调度器调用前显式装配 `TenantContext`；`BlockingIoSchedulers` 内同样要装 |
| 组合部署下事件/消息不跨进程 | `apex-boot-all` 单进程拓扑按 `apex.events.channel=auto` 退化为进程内派发 | 多实例部署必须显式选 MQ 通道，不能沿用 auto |
| 汇报 LOC 失真 | `git log --shortstat` 被 HTML 产物放大 | 用提交数 / 模块数 / 测试类数 |
| 校验链后续步骤静默不执行 | Reactor `Mono.then()` 只发完成信号、不携带元素，链上的 `flatMap` 拿不到值 | 校验链一律 `then(Mono.defer(() -> ...))`，不要 `then(x.flatMap(...))` 省事 |
| 仓库脚本在 macOS 报 `mapfile: command not found` 或 `${var^^}: bad substitution` | 系统自带 bash 3.2，不支持 `mapfile`/关联数组/大小写替换 | 数组用 `for` + `sed` 取值，转大写用 `tr`；切勿假设 bash 4+ |
| Nacos / RocketMQ 容器传 `-XX:+UseG1GC` 后 JVM 启动即崩，报 `Conflicting collector combinations` | 镜像内 `runserver.sh` 强制 CMS | 这两个组件**不传** G1 与停顿目标；且它们是 JDK8，GC 日志只能 `-Xloggc`（Zipkin 3.4 是 JDK21，才用 `-Xlog:gc*`） |
| 给 Sentinel 容器加 `JAVA_OPTS` 无效果 | 镜像 `Cmd` 是固定 `java` 命令，不展开该变量 | 必须整体覆盖 `command` |
| RocketMQ 容器 GC 日志打不开 | 镜像无 `/home/rocketmq/logs`，JVM 打开日志文件不自动建父目录 | `command` 改为先 `mkdir -p` 再 `exec` |
| systemd 单元里变量不生效 | `Environment=` 优先级高于 `EnvironmentFile=` | 同一变量只在一处声明 |

## 8. 文档规范（写文档前先看）

必读：`docs/08-project-management/DOCS_STRUCTURE_STANDARD.md`。

- 最多 3 级目录；每个目录有 `README.md`。
- **禁止**按时间（`reports/2026-09/`）、作者、状态（`drafts/`）组织。
- 同一信息只保留一份，用链接而非复制。
- 改文档后跑 `scripts/ci/check-doc-links.sh`（校验路径、大小写、锚点、是否被 gitignore）。
- 注释语言与格式见 `docs/04-development/development-standards.md`；`scan-javadoc-issues.sh` 默认输出到 `_tmp/`。

## 9. 提交前自检

1. `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify` 通过（只跑单模块时说明范围）。
2. 新增/改动端点：`@RouterOperation` 与 SpringDoc 覆盖测试同步。
3. 新增迁移：双方言齐备 + 迁移计数断言更新。
4. 新增用户可见文案：i18n 四语言（如涉及）齐备。
5. 文档链接可解析；不在根目录留中间产物（放 `_tmp/`）。
6. 若改契约：同日评估 `apex-admin-web` 的 `src/api` 与页面。
