# 对象存储接入与迁移指南

## 1. 边界与默认行为

`apex-storage-spring-boot-starter` 仅直接依赖内部 `apex-common`，不扫描 file 业务包，不依赖文件记录、租户实体或数据库。Boot 自动配置注册 `ObjectStorage` 策略和 `StorageRegistry`；`apex-file` 通过 `ObjectStorageServiceAdapter` 保留领域端口。独立消费者不需要引入 file 服务或复制组件扫描配置。

支持且仅支持五种稳定类型：`local`、`minio`、`volcengine`、`aliyun-oss`、`qiniu`。默认 `local`，Local 默认启用，四种云策略默认关闭。默认类型不存在、未启用或策略重复时明确失败，不能回退 Local 或其他桶。默认类型只决定新对象写入；历史记录按持久化的提供商、桶和真实对象键读取。

Local 根目录下按桶隔离，拒绝路径穿越和符号链接。Local 没有 HTTP 签名服务，`presignGet` 明确报不支持，不能用公开静态目录替代授权下载。云策略不会创建桶，也不会替业务配置租户隔离；桶、区域、凭据授权和网络须在部署前准备。

## 2. 接入与配置

完整变量见[环境模板](../../.env.example)，运行配置见[file application.yml](../../apex-services-bom/apex-file-service/src/main/resources/application.yml)。Java 不自动读取 `.env`；直接部署应通过受控环境或外部 Spring 配置注入。Compose 仅注入其 `environment` 显式声明的变量，不能把插值当作全量注入。

### 独立消费者

引入 `com.apex.scaffold:apex-storage-spring-boot-starter:1.3.1`，后续升级以本仓库 [starter POM](../../apex-starters-parent/apex-storage-spring-boot-starter/pom.xml) 的实际版本为准。云 SDK 为 optional，消费者启用相应后端时需显式加入对应依赖：

| 后端 | SDK 坐标 | 本仓库版本 |
| --- | --- | --- |
| MinIO | `io.minio:minio` | 8.6.0 |
| 火山 TOS | `com.volcengine:ve-tos-java-sdk` | 2.6.1 |
| 阿里云 OSS | `com.aliyun.oss:aliyun-sdk-oss` | 3.18.1 |
| 七牛 | `com.qiniu:qiniu-java-sdk` | 7.17.0 |

版本以[根 POM](../../pom.xml)依赖管理为准。`apex-file` 已显式声明云 SDK，独立使用 starter 不会自动获得 optional SDK。通过构造器注入 `StorageRegistry`，调用 `defaultStorage()` 或 `require("minio")`；不要自行创建厂商客户端或静态全局单例。桶与对象键由调用方传入，不拼接带认证信息的 URL。

### 公共字段

| Spring 属性 | file 环境变量 | 默认值/约束 |
| --- | --- | --- |
| `storage.default-type` | `STORAGE_DEFAULT_TYPE` | `local`；五种类型之一 |
| `storage.bucket-name` | `STORAGE_BUCKET_NAME` | file 默认 `apex`，不是 starter 固定桶 |
| `storage.presigned-url-expire-seconds` | `STORAGE_PRESIGNED_URL_EXPIRE_SECONDS` | file 默认 300；签名范围 1—604800 秒 |
| `storage.local.enabled` | `STORAGE_LOCAL_ENABLED` | true |
| `storage.local.base-path` | `STORAGE_LOCAL_BASE_PATH` | starter/file 默认 `/root/data/apex/uploads`；环境模板为 `./data/storage` |

Compose 将 `./data/storage` 挂载为 `/app/data/storage` 并固定容器内 base-path，不采用模板中的主机相对路径作为容器路径。部署前确认运行用户可读写，备份与数据库记录协同；迁移既有卷时先停写并核实旧对象所在目录，不要直接移动或覆盖旧卷。Local 多副本部署必须具备一致的共享存储或明确的单实例约束。

### 五后端字段

所有 access-key/secret-key 留空，不内置生产凭据；启用后必填。

| 后端 | Spring 属性 | file 环境变量 |
| --- | --- | --- |
| MinIO | `storage.minio.enabled` | `STORAGE_MINIO_ENABLED`（false） |
| MinIO | `storage.minio.endpoint` / `access-key` / `secret-key` | `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` |
| TOS | `storage.volcengine.enabled` | `STORAGE_VOLCENGINE_ENABLED`（false） |
| TOS | `storage.volcengine.endpoint` / `region` | `STORAGE_VOLCENGINE_ENDPOINT` / `STORAGE_VOLCENGINE_REGION` |
| TOS | `storage.volcengine.access-key` / `secret-key` | `STORAGE_VOLCENGINE_ACCESS_KEY` / `STORAGE_VOLCENGINE_SECRET_KEY` |
| OSS | `storage.aliyun-oss.enabled` | `STORAGE_ALIYUN_OSS_ENABLED`（false） |
| OSS | `storage.aliyun-oss.endpoint` / `access-key` / `secret-key` | `STORAGE_ALIYUN_OSS_ENDPOINT` / `STORAGE_ALIYUN_OSS_ACCESS_KEY` / `STORAGE_ALIYUN_OSS_SECRET_KEY` |
| 七牛 | `storage.qiniu.enabled` | `STORAGE_QINIU_ENABLED`（false） |
| 七牛 | `storage.qiniu.access-key` / `secret-key` | `STORAGE_QINIU_ACCESS_KEY` / `STORAGE_QINIU_SECRET_KEY` |
| 七牛 | `storage.qiniu.region` / `bucket` / `download-domain` | `STORAGE_QINIU_REGION` / `STORAGE_QINIU_BUCKET` / `STORAGE_QINIU_DOWNLOAD_DOMAIN` |

MinIO 的 endpoint 包含协议，例如 `https://minio.example.com`；HTTPS 不由旧 `MINIO_SECURE` 控制。独立 starter 在没有 `storage.minio.endpoint` 属性时兼容 `minio.endpoint/access-key/secret-key`；file 主配置已声明新前缀，因此应使用表中变量，不混配两套前缀。当前没有 `storage.s3`、`storage.local.url-prefix` 或 `STORAGE_LOCAL_PUBLIC_BASE_URL` 实现。

七牛 region 使用 SDK 支持的区域 ID，必须与桶所在区域一致；download-domain 必须已绑定该桶且证书可用，例如 `https://download.example.com`，不能带路径、末尾斜杠、用户信息、查询参数或片段。业务 `STORAGE_BUCKET_NAME` 应与 `STORAGE_QINIU_BUCKET` 一致；策略明确拒绝访问另一个桶。不能把任意请求域名拼入签名 URL。不同七牛桶需要不同策略实例/配置设计，当前单个策略不提供任意多桶域名映射。

### 云端上线步骤

1. 用厂商控制台或受控 IaC 预创建私有桶，确定区域、endpoint、DNS/TLS。应用身份仅授予所需对象上传、读取、删除和元数据查询权限，不授予无关管理权限。
2. 七牛额外绑定下载域名和证书，核对区域 ID。MinIO 确认 file 容器及签名消费者均能访问 endpoint，不能将容器内不可外达地址作为客户端签名访问地址。
3. 设置目标后端 enabled=true 以及完整凭据，保持仍有历史记录的旧策略启用。不要把密钥、完整签名 URL、Bearer Token 输出到日志或工单。
4. 设置 `STORAGE_DEFAULT_TYPE` 和默认桶，在测试租户执行上传、下载校验、exists、签名和删除；Local 只测授权内容接口及签名拒绝。
5. 验证跨租户、跨用户、伪造身份头、过期签名及错误区域/权限均失败。发布后观察对象写失败、元数据落库失败和孤儿清理，不以健康检查替代真实存储验证。

## 3. file JWT 与访问授权

当前 auth 的 `JwtKeyMaterialService` 使用活跃 RSA 私钥签发 RS256，而不是简单依赖 `JWT_SECRET`。file 支持单公钥和静态多公钥：`file.security.jwt-public-key-base64`（`FILE_JWT_PUBLIC_KEY_BASE64`）或 `file.security.jwt-public-keys-base64`（`FILE_JWT_PUBLIC_KEYS_BASE64`）必须配置 X509 DER Base64 公钥，不是 PEM 文本、证书文件、JWK 或 JWKS JSON。多公钥格式为 `kid=Base64PublicKey,kid=Base64PublicKey`，按 JWT header 的 `kid` 选择，且多公钥优先于单公钥。只分发公钥，不分发 auth 私钥。

从可信密钥管理渠道取得公钥；若已具备同一公钥的 PEM 文件，可在受控终端转换：

```bash
openssl pkey -pubin -in auth-public.pem -outform DER | openssl base64 -A
```

转换结果作为部署变量注入，不写入仓库。不要随机生成新公钥来“补齐配置”，否则现有 Token 无法验证。file 不自动请求 JWKS；多公钥模式下缺少或未知 `kid` 会拒绝认证，单公钥模式兼容无 `kid` Token。auth 自动轮换后须更新静态公钥配置并重启 file，旧 Token 宽限期并不自动在 file 生效。生产发布须制定同步轮换/重新登录窗口，不能宣称透明轮换已完成。

`FILE_JWT_SECRET` 仅为历史 HMAC 签发方的兼容入口，要求与实际签名密钥完全一致、UTF-8 至少 32 字节；RS256 部署保持为空，不因为 Compose 已传入 `JWT_SECRET` 就认为 file 已获信任材料。未配置信任材料时接口拒绝认证。

Token 必须有有效 expiration、`tokenType=ACCESS`、正数 userId 和非空 tenantId。`X-User-Id` / `X-Tenant-Id` 不能建立身份。下载、预览、删除、exists、签名及分片会话均应在可信上下文中验证归属，不得仅凭桶名/对象键绕过记录授权。Local 使用授权 `/api/v1/file/content` 或 `/api/v1/file/preview`，不暴露裸文件目录。云预签名是可转交的临时访问能力，签发前校验业务权限，控制 TTL 并避免缓存或泄漏。

## 4. 流、错误与重试契约

`ObjectStorage.upload(bucket, key, source, contentType)` 返回实际对象键，输入流借用：调用方负责关闭，不能在订阅前关闭。下载返回流的消费方负责关闭；可使用 `withDownload` 在成功、错误、取消时释放。消费函数里的阻塞读取同样隔离到 `BlockingIoSchedulers.storage()`，不要在 Netty 事件循环读取文件，也不要手动 subscribe 或在请求链 block。

同步 SDK/文件操作延迟到订阅后在 storage 池执行。只有明确的对象不存在转为空或 false；鉴权失败、网络故障、超时、配置错误不能伪装为 missing。签名 TTL 越界明确失败。云端实际最大 TTL、签名约束仍受厂商策略限制。

不对不可重放的 InputStream 自动重试。业务要重试上传，必须每次重新打开完整可重放来源，并确认稳定对象键、覆盖语义和请求幂等性。删除结果不确定时重新查询确认，不盲目删除数据库记录。只在可确认的瞬态失败下进行有界退避；凭据、区域、权限错误应修复配置。重试期间保留租户/用户与原提供商、桶、键，不切换默认策略“救活”请求。

云写入与 JDBC 不具跨系统原子性。对象写入成功但记录保存失败时，file 尝试删除补偿；补偿也失败时保留孤儿清理依据。数据库不可用导致清理记录也无法落库时，须依受控日志中的提供商/桶/对象键人工核查。所有者可通过 `POST /api/v1/file/orphan/{fileId}/retry` 重试 `ORPHANED` 记录的清理：必须使用有效 ACCESS Token，租户与用户均匹配记录；仅确认对象已删除或不存在才删除清理记录，返回 `data=true`，未确认删除返回 `data=false`，异常仍传播。未知历史归属不能借此入口回填或绕过授权。

已完成的 `STORED` 分片会话可通过 `POST /api/v1/file/chunk/cleanup/{fileId}/retry` 重试分片清理，认证与归属要求相同。响应 `data=true` 仅表示本次尝试执行完成，不保证所有对象已删除：任何分片删除失败都会保留该会话的分片元数据，便于再次重试。两入口均无请求体，`fileId` 为路径参数；正常业务包装 `code=0`，不能把 HTTP 200 当作删除成功依据。

进程中断或云上传结果不确定时，会话可能保留 `MERGING`，清理重试入口不能释放该状态。管理员应先停写或隔离会话，核对受控日志中的 fileId、提供商、桶、最终对象键，检查最终对象及分片是否完整；确认最终对象不存在才能受控恢复上传状态，存在则核实元数据或删除补偿后恢复。操作须备份并留审计记录，不能直接把所有 MERGING 批量改回 UPLOADING。

Local 上传返回的授权内容 URL 及 `/api/v1/file/url` 的 `expiresIn=0` 表示签名 TTL 不适用，每次访问仍需认证与归属授权；不是永久匿名访问。云地址仍使用配置的正数签名 TTL。

## 5. 迁移与回滚

### 数据库与历史归属

file 数据库版本链独立，生产迁移位于 [file 迁移目录](../../apex-services-bom/apex-file-service/src/main/resources/db/migration/)。MySQL/PostgreSQL 配对 V4 增加文件和分片的 tenant_id、storage_type，历史值保留 NULL；V5 保存文件会话 total_chunks、chunk_size，无法核实的旧分片会话需重新初始化。不要修改已执行迁移、删除 Flyway 历史或把未知文件批量归给当前用户/默认租户。

迁移前备份 file 数据库、存储对象/卷及旧配置，并验证恢复流程。可信管理员根据旧业务记录、上传日志和对象清单核对 tenantId、userId、storageType、bucketName、实际对象键；无法证实的记录隔离，不能开放访问。回填应分批审计且可逆，不在本指南提供一条覆盖所有历史数据的 SQL。

### 切换默认提供商

1. 盘点历史文件、未完成分片、孤儿对象及旧策略，记录可审计的对象映射清单。
2. 先配置新云后端并验证；保留旧后端可读。仅切换 `storage.default-type` 不会搬迁旧对象，也不会改写记录。
3. 需要搬迁时暂停或隔离相关写入，逐对象复制并核验大小/摘要与 Content-Type，再以受控事务更新该记录的提供商、桶和真实键。持久化映射用于回滚；不要依赖默认桶猜旧对象位置。
4. 逐租户验证权限和新旧读路径；保留源对象至观察窗口结束，清理前再次核对引用与备份。

### 回滚原则

优先回滚新写入默认策略/配置，仍保留所有被历史记录引用的后端。若已有对象迁移，按映射清单恢复元数据指向并验证源对象完整性；不能只回退配置就认为数据回滚完成。不得直接运行不识别新归属字段的旧应用并重新开放接口。数据库迁移通常前向修复，必要恢复备份时需同时恢复对应对象与写入窗口，并在停写后执行。不得删除正在使用的云桶、目录或加密/签名信任配置作为“清理”。

## 6. 本次重构模式选择

- 存储采用策略注册表 + 厂商适配器，file 通过适配保留领域端口；`CloudStorage` 模板统一缺失与错误边界，`BlockingObjectStorage` 统一同步调度边界。不是通过 switch 静默选择任意提供商。
- HTTP 使用容器管理的 `ManagedWebClientFactory`：配置绑定后创建连接池，创建客户端保留调用日志过滤器，容器销毁时释放池；同步销毁桥接不等于允许请求链 block。未新增独立 HTTP/MQ starter。
- job 使用 `SystemJobPort` / `WorkflowJobPort` 强类型桥接而非字符串反射，执行器等待 Publisher 完成并传播异步错误；system 桥接使用部署固定租户/任务并恢复线程上下文，不信任任意调度参数。
- office 消息通过 `EnumMap<OfficePlatform, MessagePort>` 策略注册表选择，重复/未知平台明确失败，发送与撤回复用选择规则。
- Feign 单方法拦截器采用 lambda，沿用可信上下文注入；已有租户/trace 头保留，不覆盖。lambda 仅用于清晰的函数式回调，不机械替换有状态策略、生命周期对象或多方法适配器。

## 7. 验证命令与结果边界

以下为维护者执行指引，不表示本文编写时已执行构建或真实云测试。在仓库根目录先加载指定环境；不得回退系统 Maven。

```bash
source scripts/build/init-build-env.sh
java -version
mvnd --version
# 受影响模块及依赖的快速单元/架构验证，不包含完整 Failsafe 门禁
mvnd -T4 -B -pl :apex-file-service,:apex-storage-spring-boot-starter -am test
# 完整门禁（独立安排执行，准备测试所需外部环境）
mvnd -T4 -B clean verify
# 报告只汇总现存 XML，不执行测试；需先打包 apex-tools-deployment
mvnd -B -pl :apex-tools-deployment -am package -DskipTests
bash scripts/test/generate-comprehensive-test-report.sh
```

报告分别统计 Surefire、Failsafe 和 XML skipped；无报告模块标注“单元/集成”缺失，不能编造通过或未执行用例数。真实 MinIO/TOS/OSS/七牛测试需专用桶、最小权限和清理策略，未准备凭据时必须明确记为未验证；本地/Mock 契约测试不能替代真实云端行为。应覆盖资源释放、特殊对象键、空文件、错误凭据、无桶、跨租户、补偿失败和重试恢复。测试范围与实际结果由本次最终测试报告单独记录。

脚本/文档维护检查：

```bash
bash -n scripts/test/generate-test-report.sh scripts/test/generate-comprehensive-test-report.sh
bash scripts/ci/check-doc-links.sh
git diff --check
```

参见[配置说明](configuration.md)、[部署指南](deployment.md)、[测试文档](../06-testing/README.md)。
