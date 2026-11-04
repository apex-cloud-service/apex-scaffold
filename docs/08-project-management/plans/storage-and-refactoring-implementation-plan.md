# 对象存储扩展与工程重构实施方案

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**目标：**完成阿里云 OSS、七牛云服务端上传与配套存储能力，将通用存储基础设施提取为 Spring Boot starter，修正文件服务的数据归属、阻塞 I/O 和失败补偿，并完成有事实依据的跨模块重构及对应交付。

**授权与范围：**用户于本轮明确授权由实施者选择方案并执行，无需再次请求方案确认。保留工作区已有 job、Seata、脱敏等未提交改动，不提交或推送 Git。采用前轮方案 1B、2A：完整文件扩展与重点跨模块重构、文件归属治理。本方案先于本轮生产代码变更落盘。

## 一、当前状态与选择依据

1. `apex-file` 实际仅有 Local、MinIO、火山 TOS；`StorageService` 的 OSS 注释超前于实现。端口含上传、下载、删除、存在性、GET 预签名和照片 URL 前缀。
2. `StorageServiceProvider` 固定注入三个具名实现并降级，而 `DefaultStorageConfiguration` 明确选择默认后端，两者规则不一致；新增后端会继续扩大重复选择逻辑。
3. 现有 `AbstractStorageService` 已采用模板方法，应保留其合理部分；新适配器采用策略和厂商 SDK 适配器，无需额外复杂工厂。
4. 存储实现位于 file 业务包，依赖 file 库才能使用；独立 starter 应不包含数据库、路由及文件业务校验。
5. 文件链路需重新核对租户、用户归属、提供商持久化、下载实际读取线程及分片失败清理；不得将对象键前缀当作授权。
6. 跨模块静态证据包括受管 HTTP 工厂静态池先于 ApplicationRunner 配置、缓存 loader 空完成无法进入 flatMap、job 桥接字符串反射、office 发送/撤回重复平台分支和 Feign 单方法匿名拦截器。
7. 当前工作区已有大量未提交功能增强。修改这些文件前读取当前内容及 diff，只合并本任务必要内容。

## 二、总体架构与明确决策

### 2.1 通用存储 starter

- 新建 `apex-storage-spring-boot-starter`，只依赖 common、Spring 自动配置/验证/Reactor 和存储 SDK，不反向依赖 file。
- 将通用存储端口、类型及适配器放在独立 storage 包。file 中既有业务端口通过明确适配保留源代码契约，业务层不引用厂商 SDK。
- Local、MinIO、TOS、`aliyun-oss`、`qiniu` 均保留明确类型；已有 `storage.*` 配置保留其有效含义。默认 Local，云后端显式开启。
- Boot 3 使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册。支持自定义 Bean，并验证多后端、默认选择、重复类型、缺配置、SDK 缺失情形。
- 指定后端不可用或类型错误时明确失败，不静默写入其他后端。不得以旧 Provider 降级行为掩盖失败。
- 由根 `pom.xml` 统一管理 SDK 版本和新 reactor 模块；file POM 移除迁移后的重复 SDK 依赖并接入 starter。版本按可解析官方发布版本及 Java 17 兼容性确定并写入实际交付清单。

### 2.2 提供商契约

- OSS、七牛实现完整上传、下载、删除、exists、私有 GET 访问 URL，不仅注册配置。
- 云桶由部署预创建；不得请求时自动建云桶。七牛配置区域及与桶匹配的下载域名，拒绝任意桶混用。
- 上传保留 Content-Type，返回真实对象键；旧后端既有键不迁移。新云实现明确对象键命名，业务始终使用实际返回值。
- 只有明确不存在才返回 empty/false；权限、网络及服务错误传播错误信号。删除不存在语义通过契约测试固定。
- 同步 SDK 和文件读写延迟到订阅时并使用 `BlockingIoSchedulers.storage()`；下载响应实际读取也必须隔离。
- 文档明确输入流所有权。取消、成功和失败都释放下载响应；不可重放流不自动重试。签名 TTL 为正且在允许范围内。
- 日志不得包含凭据、上传令牌或完整签名 URL；HTTP 下载目标只能由可信配置域名构造。

### 2.3 文件治理

- 修改 `apex-file` 中 FileService、ChunkUploadService、文件模型/仓储/转换器及对应测试，`apex-file-service` 中 Handler/路由/身份装配与测试。
- 文件记录持久化租户、上传用户、提供商、桶及实际对象键；分片会话和读取、删除、exists、签名先检查可信请求身份与记录归属。
- 重新扫描 file 迁移版本，在当前 V1–V3 之后追加配对迁移；不得改写 V1–V3。同步 basic H2 schema。
- 历史没有可靠归属的记录不自动赋予当前访问者；保留空归属并拒绝常规用户访问，交付受控回填操作说明。可信历史来源才允许管理员离线回填。
- 使用项目现有认证上下文和租户传递规则；无可信身份时拒绝业务访问，不盲信客户端任意身份头。
- 云存储与 JDBC 不宣称跨系统原子事务；落库失败执行补偿，补偿失败保留可追踪依据。分片缺失不能静默跳过，清理失败不得丢失元数据。

### 2.4 有边界的跨模块优化

| 目标 | 文件与方式 | 必须保留的语义 |
| --- | --- | --- |
| 受管 HTTP 资源 | infrastructure 的 ManagedWebClientFactory/Initializer、RemoteApiClientConfig 及现有消费者；配置先于池创建，容器管理生命周期 | 超时、codec、脱敏过滤器、请求取消与关闭 |
| 缓存空结果 | infrastructure 的 MultiLevelCacheService；显式区分数据、负缓存、未命中，loader empty 写负缓存 | Single-Flight、租户完整键、错误可重试；不顺带改变旧值格式 |
| job 桥接 | job starter 的 SystemJobBridge/WorkflowJobBridge/AutoConfiguration，system/workflow service 适配端口 | starter 不依赖业务模块，固定任务、线程上下文恢复、异步失败反馈 |
| office 策略 | OfficeMessageService 与 MessagePort，以平台注册表统一选择 | 未知平台错误、幂等和各厂商行为 |
| lambda | api-contract 的 FeignTenantTraceConfiguration 及本次变更中单方法回调/委派 | 头覆盖、类型令牌及多方法回调保留，不进行全仓机械替换 |

不新增独立 HTTP/MQ starter：目前边界收益不足，避免一次引入多个不必要模块。存储 starter 是本次实际模块交付。

## 三、实施步骤与文件责任

1. **特征测试与契约固定。**读取目标模块 POM/配置/测试，补充先失败测试；记录存储不存在、失败分类、类型选择、身份、流和生命周期规则。
2. **存储扩展及提取。**新增 starter POM、独立 SPI、五种适配器、配置属性及自动配置 imports；更新根 POM、file 接入适配、默认选择和测试。先编译、单测，再上下文验证。
3. **文件业务治理。**新增双数据库迁移和 basic schema，更新模型、持久化、授权及补偿；保持 HTTP 请求/响应形状，仅增加严格授权行为。补充权限、并发、缺片、错误与取消测试。
4. **跨模块重构。**各项先补失败或特征测试，再最小改动；不覆盖本轮前已有功能。
5. **配置交付。**更新 `.env.example`、file service `application*.yml`、`podman-compose.yml` 的显式变量注入，保持 basic 默认 Local。验证 Java 不自动加载 .env 的说明。
6. **脚本与文档。**修正 `scripts/test/generate-test-report.sh`、`generate-comprehensive-test-report.sh` 的旧模块与报告源，分列 Surefire/Failsafe。更新 configuration、configuration-inventory、deployment、api-routes、测试指引和架构/模块边界。新增本任务专用存储接入指南与最终报告。
7. **验收。**固定环境运行受影响模块测试、完整 verify、文档链接和脚本检查；根据实际报告填写测试结论，不将跳过当通过。

## 四、注释与交付要求

- 本次新增及实质修改的类型、构造器、方法添加中文 Javadoc；说明用途、参数、返回、异常，以及适用的线程/流所有权/授权/签名/补偿规则。类型按项目约定包含作者、版本和 since。
- XML/YAML/SQL/Shell 为新配置或不直观约束加中文说明；不记录真实凭据，不用注释虚构已通过状态。
- 测试说明应指出保护的业务行为，避免仅复述实现。复杂异常分类保留具名方法，不压成难维护的 lambda 链。
- 最终报告包含新增/修改模块、配置矩阵、迁移与回滚约束、SDK 版本、实际命令和结果、跳过原因、真实云验证范围及上线前条件。

## 五、验证矩阵和验收标准

| 层级 | 必测场景 |
| --- | --- |
| 存储适配器 | 上传/读取/删除/exists/签名；特殊对象键；Content-Type；TTL；404/权限/网络错误；流关闭及取消 |
| Boot 装配 | 默认 Local；五类型选择；关闭后端；未知类型；重复注册；配置缺失；自定义 Bean；独立接入不扫描 file |
| 文件业务 | 跨租户/用户拒绝；历史无归属拒绝；提供商切换后旧记录正确路由；分片会话归属；缺片与清理失败；落库补偿 |
| 跨模块 | 缓存 empty 连续查询和并发；HTTP 配置先于连接池；job 迟到错误和上下文恢复；office 未知/重复平台；Feign 头传播 |
| 工程 | Spotless、PMD/p3c、ArchUnit、JaCoCo、Surefire、Failsafe、文档链接、Shell 语法、diff 空白 |
| 真云 | OSS/七牛显式远程测试，独立测试对象前缀，finally 清理；没有环境则标记 skipped/未验证 |

所有构建、测试、依赖解析均先在当前 shell `source scripts/build/init-build-env.sh`，只用固定 JDK 17.0.12-oracle 和 mvnd 2.0.0-rc-3。主验证命令为 `mvnd -T4 -B clean verify`，受影响快速验证按实际模块 `-pl ... -am test`。报告必须区分单测、默认 IT 和真云 IT。没有真实云凭据不阻碍代码与离线契约交付，但不得声称真云端到端通过。

## 六、完成记录

2026-10-14 完成代码与离线契约交付、最终整合及正式报告。五后端 starter、file 归属/补偿/持久化分片与两类重试路由、跨模块 HTTP/缓存/job/office/Feign 重构及配置脚本已完成；接入指南和 API 文档同步 orphan/chunk cleanup retry 与 Local `expiresIn=0`。

最终按固定 JDK 17.0.12-oracle、mvnd 2.0.0-rc-3 执行 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify`，21:42:27 结束，耗时 10:35，26 个 reactor 项目全部 BUILD SUCCESS。首两轮失败按证据最小修复 system-service 格式与 example 测试 profile/Redis 装配隔离，未禁门禁或回退近期 file 改动。实际 XML：Surefire 4655（4652 通过、3 跳过），Failsafe 296（292 通过、4 跳过），失败/错误均 0。

跳过为真实 Keycloak 3、example 独立库缺工作流表 3、未启用真实 Seata TC 1；真实 OSS/七牛/MinIO/TOS 云端到端未执行。静态 RS256 公钥协调轮换、历史未知归属离线回填、云与数据库非原子以及 MERGING 中断人工恢复仍为明确运维限制，不能记作已消除。

详见[交付报告](../reports/storage-refactoring-delivery-report.md)、[正式测试汇总](../reports/storage-refactoring-tests/test-report.md)与[统计 JSON](../reports/storage-refactoring-tests/test-summary.json)。报告脚本已实际运行；文档链接、相关 Bash 语法与 diff 空白检查完成。未创建 Git commit 或推送。
