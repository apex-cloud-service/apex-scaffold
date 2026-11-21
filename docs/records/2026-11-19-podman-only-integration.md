# 2026-11-19 Podman-only 联调入口记录（BE-043）

## 范围

对齐后端统一编排入口与前端镜像的 Podman 联调约束，验证 Compose 配置，并记录当前容器环境阻塞。

## 改动清单

- 后端 `apex-admin-web` Compose 服务移除固定 `container_name`，使容器名遵循 Compose 项目隔离。
- 将 Nginx 首页健康检查移入 Compose 服务定义；Podman 构建 OCI 镜像时会忽略 Containerfile `HEALTHCHECK` 声明。
- `apex.sh` 顶部说明统一为 Java JAR、Podman Compose、Kubernetes 三种部署运行时，与脚本命令实现一致。
- 既有改动已移除 `apex.sh` 内 Docker Compose 运行分支，更新 Podman-only 的构建工具与 MQTT 测试说明。

## 验证证据

- `podman-compose -p apex-scaffold -f podman-compose.yml config --services` 通过，列出 17 个服务。
- 前端 Containerfile 使用 Podman 构建成功；前端隔离 Compose 测试 68 文件/533 用例全绿，Nginx 代理模拟冒烟通过。模拟网关不作为真实后端证据。
- 本机未发现 `localhost/apex-gateway:latest`；Podman 镜像库存查询报存储路径错误。未启动后端数据库/完整服务，未动既有卷。
- `git diff --check` 通过（后端状态以完成时复验为准）。

## 影响面

仅影响 Compose 容器身份、前端容器健康探针与构建工具说明；不改变业务 API 契约。

## 回滚方式

按文件恢复 Compose/编排脚本改动。不得通过删除 Podman 卷或重置工作区回滚。

## 未完成项

完整服务拓扑、业务写入、SSE、WebSocket 和外部依赖功能仍待实测，随后由独立 R5 按最终版本复核。

## 2026-11-19 前端浏览器回归

前端 Playwright 在临时 Podman 容器中发现 HTTP 代理环境变量拦截 `webServer` 回环探测；前端已将测试基址统一为 `127.0.0.1` 并同步补入 `NO_PROXY`。首次运行还发现审批待办 E2E mock 与实际裸数组契约不一致，修正夹具后全套 12 项 E2E 在单 worker 下通过。详细命令输出及未完成的真实业务面见前端联调记录。

## 真实网关复测

- 隔离环境中，auth 直连登录 HTTP 200，但经网关出现 502/503，日志记录 `TimeoutException`。网关为 `authService` 配置 10 秒 TimeLimiter，并在 `GatewayApplicationTest` 断言配置绑定结果。固定 JDK 与 mvnd 定向测试通过（4/4）。
- 后端 Compose 为 gateway 和 system 提供同网络 auth JWKS 默认地址，允许通过环境变量覆盖。用新网关 JAR 在 `apex-fe029-verify` 复测，网关 readiness 和经前端 Nginx 转发的 `POST /auth/v1/login` 均返回 HTTP 200。本次只采集状态码，登录响应直接丢弃。
- auth 与 system Flyway 在临时 MySQL tmpfs 空库中分别成功迁移 22 和 91 条，失败数均为 0。`podman-compose down` 退出 0，按项目过滤确认容器与网络均无残留。
- 按 Router 核对并补齐集成、物联网、脱敏及消息票据的 23 条 API 路由索引。

## 扩展业务联调

- office 临时空库成功迁移 10 条，但服务启动因缺少 `security.config.encryption.key` 失败。Podman Compose 为 office 将既有 `CONFIG_ENCRYPTION_KEY` 映射为 `SECURITY_CONFIG_ENCRYPTION_KEY`，不复用 auth 的 Base64 密钥。以该映射启动 office 后，真实浏览器经网关获取消息票据 HTTP 200，使用单实例 `ws://` 直连覆盖时升级 HTTP 101；未观察到完整 ping/pong。
- 浏览器以一次性 401 触发真实刷新，`POST /auth/v1/refresh` 和字典列表重放均 HTTP 200；SSE `GET /api/v1/notifications/stream` HTTP 200 且为 `text/event-stream`。字典创建 HTTP 403，工作台聚合 HTTP 200 但三个未部署上游降级。system/office 10 秒超时仅为隔离复测覆盖，不视为正式修复。
- 验证仅运行在 `apex-fe029-verify`，MySQL 数据目录为 tmpfs；清理容器和网络，不删除卷。完整服务拓扑、业务写入、实时事件投递和独立复核未完成。

### CORS 与字典精度续测

- 网关与 system 同步配置精确前端来源后，字典类型创建 HTTP 200、`code=0`；编辑使用的路径 ID 与列表响应不一致，返回 HTTP 500、`code=4711`。另确认真实 WebSocket 101 及双向 ping/pong，尚未验证业务事件投递。
- 字典类型与数据 DTO 对外将 `id` 序列化为十进制字符串；保留 Java `Long`、路径契约和数据库 schema。`DictIdSerializationTest` 通过，固定 JDK/mvnd 重新打包 system 服务成功。正式 Compose 服务清单解析通过。

### 2026-11-20 真实业务复测

- 字典管理数据项列表不再过滤停用记录，以便管理端重新启用；公开字典读取仍保留状态过滤。后端字典定向测试 10/10，Spotless 与 system 重打包通过。隔离 Chromium 中字典类型与数据项的创建、编辑、停用、重新启用、删除均为 HTTP 200、`code=0`，请求路径保留列表中完整十进制字符串 ID。
- 隔离环境为 auth 租户回源设置 12 秒、为登录路由 TimeLimiter 设置 24 秒；仅本轮覆盖，未改变正式超时及租户校验。真实登录、身份/角色/权限/动态菜单和刷新重放通过。通知收件箱与 SSE 握手通过；总览三个未部署上游降级。WebSocket 票据 200、升级 101，但本轮 25 秒内未观察到心跳，业务投递未验收。
- 站内信收件箱、审批待办、日历区间查询均 HTTP 403。三个 Handler 都要求办公权限，隔离栈未配置可信 HTTPS auth、共享身份命名空间或同租户授权 reader，按设计拒绝。正式 Compose 补齐 auth reader 用户 ID 与 office 授权的三个非秘密变量映射；禁用、空地址和 3 秒等原有默认值不变，凭据仍由安全配置提供。使用占位值验证 `podman-compose config` 渲染的服务及全部四项映射；`OfficeAuthorizationTest`、`MessageWebSocketTest` 共 13/13 通过。这些不替代有效 reader 配置下的真实办公请求复验。
- 仅使用专属 Podman Compose 项目和 tmpfs 数据库；临时浏览器、容器和专属网络已清理，未删除卷或写入现有持久化数据库。完整服务拓扑和全量联调仍未验收。

### 2026-11-20 业务域扩展与启动修复

- workflow、file、integration 启动均因共享 `ConfigEncryptionService` 没有收到 `security.config.encryption.key` 失败。经前端的待办查询、multipart 上传及连接器列表均为 503，业务码 3007；总览 HTTP 200 但三个上游降级。同期真实 WebSocket 升级 101，双向 ping/pong 通过，不代表业务事件投递通过。
- 将既有 `CONFIG_ENCRYPTION_KEY` 到 `SECURITY_CONFIG_ENCRYPTION_KEY` 的映射从 office 服务移至共享应用环境；不改变密钥来源、格式、校验或数据库。用占位值解析正式 `podman-compose.yml`，确认四个相关服务均收到映射。修复后隔离复跑未通过 system 8082 监听探测，未执行新增业务请求；实际启动和浏览器复验仍待完成。两轮专属容器及网络均清理，不删除卷。

### 2026-11-20 扩展服务启动复验

- 使用前端 `scripts/integration/podman-verify.sh` 从新 tmpfs 空库两次启动隔离栈；auth/system Flyway 分别成功 22/91 条、失败 0，office/workflow/file/integration 的 HTTP 端口均就绪。
- 首轮 Chromium 登录初始化、字典读写、刷新重放、通知 SSE 握手、站内信 WS 心跳通过。工作流待办和图片上传 HTTP 503、`code=3007`，网关记录两次 `TimeoutException`；总览 HTTP 200 但三源降级。连接器及办公三项读取 HTTP 403；集成授权同样依赖可信 HTTPS 授权中心、共享身份命名空间和同租户专用 reader，不修改默认拒绝策略。
- 第二轮仅改隔离栈的超时及临时公钥配置。验证码和登录 HTTP 200，浏览器未发出身份初始化请求，没有执行扩展用例；业务超时修正未验收。两轮专属容器和网络均已清理，未删卷或修改既有持久化数据库。
- 第三轮认证初始化及通知收件箱、SSE 握手通过。工作流待办 HTTP 401、办公三项 HTTP 403，system 聚合日志记录两次 `Unauthorized`，拒绝原因未确认；未获得文件上传、总览非降级或连接器通过结果。该轮专属资源已清理，未删卷。

### 轮换公钥复验

- 隔离 Compose 移除 workflow、file、integration 的静态验签公钥覆盖，等待服务同步认证中心 JWKS。密码登录使用轮换密钥，与此前配置的 OAuth2 元数据公钥不是同一套；正式配置及验签策略未改动。
- 真实 Chromium 复验工作流待办 HTTP 200、业务码 0、分页正确；总览文件、工作流、集成三源均 `UP`。认证初始化、刷新重放、字典 CRUD 与双向启停、通知收件箱、SSE 握手及 WebSocket 心跳均通过；尚未验证业务事件实际投递。
- 连接器与办公三项读取仍为 HTTP 403。上传由 HTTP 401 变为 HTTP 400；经源码核对，文件服务要求普通 multipart 的 `sex` 为 `1=AM` 或 `2=AF`，前端需在传输边界映射。auth/system 临时库迁移成功 22/91 条、失败 0；专属资源已清理，未删卷。全量联调保持执行中。

### 文件元数据落库修复

- 前端修正 multipart 映射后，真实上传越过参数校验，但插入 `file_record` 时因 `create_time` 为空返回 HTTP 500。文件服务未装配已有 `BasePO` 字段依赖的自动填充处理器；在服务扫描范围内增加 MyBatis-Plus `MetaObjectHandler`，按字段注解为缺省时间与版本赋值。不修改 POM、表结构、迁移或 HTTP 契约。
- `FileServiceContextLoadTest` 在 H2 内存库通过 2/2，其中新增用例通过真实 Mapper 插入并回读非空时间及版本；文件服务重新打包和 Spotless 检查通过。
- 新 JAR 在隔离 Podman Compose 中经真实 Chromium 复验，普通 multipart 上传 HTTP 200、`code=0`，确认已越过元数据落库错误。后端线上 `data.fileName` 是存储对象键；前端旧断言读取 `objectName`，该轮未通过页面断言。响应及分片契约不改，前端在消费边界映射。
- 同轮工作流待办通过，办公收件箱、审批、日历及连接器 HTTP 403；总览 HTTP 200，workflow/integration `UP`、file 降级，聚合日志记录一次 `TimeoutException`。通知 SSE 和站内信 WS 仅验收握手与心跳，不算业务事件投递通过。auth/system 临时库迁移 22/91 条成功、失败 0，专属容器及网络已清理，未删除卷。

### 上传页面回显与总览复验

- 新前端镜像在隔离 Podman Compose 中通过真实 Chromium multipart 上传：HTTP 200、业务码 0，页面 `objectName` 与响应 `fileName` 一致。同轮工作流待办分页通过，总览 HTTP 200 且 file/workflow/integration 均 `UP`；鉴于此前 file 超时，尚不能验收稳定性。
- 办公收件箱、审批待办、日历区间和连接器列表仍返回 403，整轮退出码 1。正向验证需要可信 HTTPS 授权端点、同租户专用 reader 和已确认的共享身份命名空间。通知 SSE 握手及 WebSocket 心跳通过，业务事件投递未验收。
- auth/system 临时空库分别迁移 22/91 条、失败 0；专属容器及网络已清理，未删卷，未修改现有持久化数据库。BE-043 / FE-029 保持执行中。

### 通知中心已读复验

- 新前端镜像修复弹层列表加载后，隔离 Chromium 通过通知收件箱与已读操作。两条通知夹具仅写入经检查的 tmpfs 空库；单条已读及全部已读均返回 HTTP 200、业务码 0，分别返回 `true` 与 `1`，页面角标由 2 变为 1 后消失。
- auth/system 临时库迁移 22/91 条、失败 0；专属容器和网络已清理，未删卷。该轮未启用 RocketMQ，不能作为真实事件投递证据。BE-043 / FE-029 保持执行中。

### RocketMQ 通知消费者装配复验

- 首轮 Podman Compose 使用随机标记向临时 RocketMQ 的 `apex-notification-topic` 发布。Broker 接受消息，但 Chromium SSE 未收到；核查发现 system 没有注册通知广播消费者。
- 消费者与 `MqConsumerService` 原先在组件扫描时通过 `@ConditionalOnBean(RocketMQTemplate.class)` 评估，早于 RocketMQ 自动配置创建模板。改为在 `RocketMQAutoConfiguration` 之后导入并仍受现有队列/消费者开关控制；增加上下文测试，覆盖模板存在、模板缺失及两个开关关闭。通知消费者装配/消费、发布器定向测试共 11/11 通过，Spotless 通过；system-service 新 JAR 构建成功。
- 新 JAR 在 Broker 注册广播消费者，Chromium 完成认证并打开 SSE。该轮未取得 RocketMQ 发布回执，未收到目标帧，故不验收投递。FE 专属临时栈已退出并清理，未删除卷；后续继续定位发布链路。
