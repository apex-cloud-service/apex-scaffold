# 对象存储扩展与工程重构交付报告

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

验收日期：2026-10-14；项目版本：1.3.1。对应[实施方案](../plans/storage-and-refactoring-implementation-plan.md)。

## 1. 验收结论

最终完整门禁于 2026-10-14 21:42:27 +08:00 结束为 BUILD SUCCESS，耗时 10:35；含根项目的 26 个 reactor 项目全部 SUCCESS。实际 XML 统计为 Surefire 4655 个（4652 通过、3 跳过）、Failsafe 296 个（292 通过、4 跳过），失败和错误均为 0。本次已按证据修正 system-service 测试格式与 example 测试 profile/Redis 装配隔离，保留全部质量门禁；失败轮次及跳过原因详见第 6 节。

真实 OSS、七牛、MinIO、TOS 远程桶未执行端到端验证；SDK 测试与装配测试不能替代真实云凭据、区域、网络、权限和桶策略验证。

## 2. 实施交付范围

| 范围 | 实际交付及边界 |
| --- | --- |
| 独立存储 starter | `apex-storage-spring-boot-starter` 提供 Local、MinIO、TOS、阿里 OSS、七牛五种策略，公共 SPI、严格注册表与 Boot 3 自动配置；不依赖 file 业务域 |
| file 库 | 业务端口适配 starter；记录租户、用户、提供商、桶、真实对象键；授权访问、落库补偿、孤儿依据及持久化分片会话 |
| file-service | 独立验证 ACCESS Token、可信身份上下文；V4/V5 双数据库迁移与 basic schema；孤儿清理与分片清理重试路由 |
| infrastructure | 容器管理 HTTP 资源生命周期、配置先于连接池创建；缓存 loader 空完成写负缓存，保留 Single-Flight 与租户键 |
| job 与 service 适配 | 强类型 SystemJobPort/WorkflowJobPort 替代字符串反射，保留固定任务、上下文恢复和异步失败传播 |
| office 与 api-contract | 平台策略注册表统一发送/撤回选择；Feign 单方法回调使用 lambda，保留头传播语义 |
| 配置与脚本 | file 配置、环境模板、Compose 显式注入、报告脚本维护；汇总 Surefire/Failsafe 和分列 JaCoCo XML |
| 本次最终整合 | 同步接入指南/API 的两个重试入口与 Local TTL，修正分片路由中文说明，补存储类型版本和关键配置/路径校验中文 Javadoc；最小修复 system-service 测试链格式 |

近期 file 的审核、上传、响应字段及其他已有工作区变动均保留；未创建 Git commit，未推送。

### SDK 版本与接入

| 后端 | SDK | 本次根 POM 版本 |
| --- | --- | --- |
| MinIO | `io.minio:minio` | 8.6.0 |
| TOS | `com.volcengine:ve-tos-java-sdk` | 2.6.1 |
| OSS | `com.aliyun.oss:aliyun-sdk-oss` | 3.18.1 |
| 七牛 | `com.qiniu:qiniu-java-sdk` | 7.17.0 |

版本以[根 POM](../../../pom.xml)为准。starter 云 SDK 为 optional，独立消费者须显式引入；file 已显式声明。云桶预创建，不在请求中自动建桶。默认提供商不可用时明确失败，不静默换后端。

## 3. 配置矩阵与部署条件

完整配置见[对象存储接入指南](../../05-operations/object-storage-integration.md)与[配置文档](../../05-operations/configuration.md)。

| 配置 | 默认/约束 |
| --- | --- |
| `storage.default-type` / `STORAGE_DEFAULT_TYPE` | 默认 local；支持 local、minio、volcengine、aliyun-oss、qiniu |
| `storage.bucket-name` / `STORAGE_BUCKET_NAME` | file 默认 apex；starter 不固定业务桶 |
| `storage.presigned-url-expire-seconds` | 默认 300，云签名范围 1—604800 秒，厂商限制还需真云核对 |
| `storage.local.enabled` / `storage.local.base-path` | 默认开启，默认目录 `/root/data/apex/uploads`；环境模板使用 `./data/storage`，需持久卷、权限及备份 |
| `storage.minio.enabled` | 默认关闭；显式配置 endpoint/access-key/secret-key |
| `storage.volcengine.enabled` | 默认关闭；显式配置 endpoint/region/access-key/secret-key |
| `storage.aliyun-oss.enabled` | 默认关闭；显式配置 endpoint/access-key/secret-key |
| `storage.qiniu.enabled` | 默认关闭；显式配置 region/bucket/download-domain/access-key/secret-key；下载域名需无路径的可信 HTTPS 域名并绑定桶 |
| `file.security.jwt-public-key-base64` / `FILE_JWT_PUBLIC_KEY_BASE64` | RS256 单公钥的 X509 DER Base64；无 `kid` Token 兼容；多公钥配置存在时优先使用多公钥 |
| `file.security.jwt-public-keys-base64` / `FILE_JWT_PUBLIC_KEYS_BASE64` | 静态多公钥，格式 `kid=Base64PublicKey,kid=Base64PublicKey`，按 JWT header 的 `kid` 选择；不能填 PEM/JWK/JWKS；不得分发私钥 |

Java 不自动加载 `.env`。部署脚本仅在 `APEX_ENV_FILE` 有效时加载；Compose 插值与容器注入需分别核对。生产密钥、签名 URL 和令牌不写入本报告。

## 4. API 与清理语义

实际路径见[API 路由文档](../../03-api/api-routes.md)。

- `POST /api/v1/file/orphan/{fileId}/retry`：无请求体；有效 ACCESS Token、同租户同用户，仅 ORPHANED。确认对象删除或不存在后才删记录并返回 `data=true`；未确认返回 false，异常保留错误信号。历史未知归属不能借此回填。
- `POST /api/v1/file/chunk/cleanup/{fileId}/retry`：无请求体；同租户同用户，仅 STORED 会话。`data=true` 表示本次尝试完成；部分删除失败时保留分片元数据供后续重试，不能推断所有对象已删除。
- 常规成功响应 `code=0`；HTTP 200 本身不证明清理完成。权限、网络和服务错误不能转换成无依据的成功。
- Local 上传及文件 URL 响应 `expiresIn=0` 表示签名 TTL 不适用。内容 URL 每次访问仍需身份验证和归属授权，不表示永久匿名访问；云 URL 使用正数 TTL。

## 5. 迁移、回滚与运维限制

V4 在 file_record/file_chunk 增加 tenant_id、storage_type 及归属索引；V5 在 file_record 持久化 total_chunks、chunk_size。MySQL/PostgreSQL 配对迁移位于 file-service 的各自迁移目录；生产历史 V1—V3 不改写。basic H2 schema 与新模型保持对应，但 H2 验证不能证明真实双库生产迁移通过。

历史无法证明的归属保持 NULL 并拒绝普通访问；管理员只能依据可信业务记录、上传日志和对象清单分批离线回填。未知分片初始化参数不能从后续客户端请求反推，需重新初始化。迁移前备份数据库、对象和配置；回填保留审计与可逆映射。

切换默认提供商只影响新写入，旧对象依持久化的提供商/桶/真实键访问。搬迁需暂停或隔离相关写入、逐对象复制并核验大小/摘要及 Content-Type，再受控更新映射。回滚应用时保留新字段、旧后端与对象映射，不直接删列或把全部记录改为当前默认提供商。

### 必须保留的限制

1. **静态 RS256 公钥轮换**：file 当前独立静态验签，不自动获取 auth JWKS。多公钥按 Token header 的 `kid` 选择，缺少或未知 `kid` 时拒绝；多公钥优先于单公钥，单公钥兼容无 `kid` Token。auth 更换签名密钥时需更新 file 静态公钥配置并重启；切换窗口、旧令牌存续和信任材料更新需单独验证。
2. **云与数据库非原子**：上传成功但落库失败执行删除补偿，补偿失败保存 ORPHANED 依据；若保存依据也失败，只能依据受控定位日志核查。不能宣称跨系统原子事务或零孤儿。
3. **MERGING 中断人工恢复**：进程中断、最终对象状态不确定或 exists 查询失败时保留 MERGING，不自动释放合并锁。停写/隔离会话，核对 fileId、提供商、桶、最终键和分片清单；确认最终对象不存在才受控恢复上传，存在则核实元数据或补偿。操作前备份并审计，禁止批量改回 UPLOADING。
4. **分片清理结果**：删除失败保留元数据，重试返回 true 是尝试语义；需运维核实对象清单与元数据，不将成功包装当成彻底清理。
5. **真实云与真实生产双库**：未配云凭据，不声称真实远程上传、签名访问、区域、ACL、TLS、限流或生产 MySQL/PostgreSQL 升级通过。上线前以隔离测试前缀执行上传/读取/删除/exists/签名与失败路径并 finally 清理。
6. **注释覆盖范围**：公共 SPI 和云模板定义中文参数、返回、流所有权与异常契约，覆盖方法继承这些契约；关键私有校验、自动装配及路由说明补充中文。本次不是全仓每个方法注释形式的机械检查。

## 6. 实际验证记录

### 固定环境与完整门禁

三次完整执行均使用：

```bash
source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify
```

JDK 为 `17.0.12-oracle`，mvnd 为 `2.0.0-rc-3`。没有回退 Maven、禁用 Spotless/PMD/覆盖率或弱化测试。

首次结束时间：2026-10-14 21:07:52 +08:00，耗时 16:17，BUILD FAILURE。26 个 reactor 项目中 24 SUCCESS，example 与 system-service FAILURE。workflow-service 当前 pmd.xml 无违规，已有方法引用适配保留，未做无证据的生产代码修复。

- system-service：Spotless 精确指出 `SystemJobPortConfigurationTest` 的 StepVerifier 调用链需分行，已修正。
- example：20 个默认 IT 中 17 errors、3 skipped，上下文根因是本机 Redis 连接拒绝；日志亦出现 MySQL 连接拒绝。首轮结束前后启动现有 MySQL、Redis 容器，并确认测试库存在。example 的覆盖率因上下文未启动而未达标，不绕过门槛。
- 首轮 file：59 单测；file-service：25 单测与 50 IT；全部 failures/errors/skipped 为 0。它们只证明首轮该模块验证结果。

第二次执行：2026-10-14 21:09:29—21:19:20 +08:00，耗时 09:51，BUILD FAILURE；26 个 reactor 项目中 25 SUCCESS，仅 example FAILURE。system-service 格式门禁和 workflow-service 均成功。example 的生产 Redisson 配置仍按 `spring.profiles.active=dev` 属性触发，连接到需认证的 Redis 后报 NOAUTH；`@ActiveProfiles("test")` 不等同于覆盖该属性。最小修复仅在测试入口显式设置 `spring.profiles.active=test`，并将主候选 Redisson 测试 Bean 使用独立名称，未修改生产认证或 Redis 凭据。

针对性 `mvnd -T4 -B -pl :apex-example -am verify` 于 21:29:51 结束：example 17 个启用 IT 全部成功，3 个原有 IT skipped；命令因新测试注解的 Spotless 排版失败，已按给出的单行格式修正，未降低门禁。

第三次完整执行于 21:31:50 +08:00 开始，21:42:27 结束，耗时 10:35，退出码 0，BUILD SUCCESS；26 个 reactor 项目全部成功，包括 example、workflow-service 和 system-service。编译、Surefire、默认 Failsafe、Spotless、PMD/p3c、ArchUnit 及生效的 JaCoCo 检查均按既有配置完成。未运行额外 remote-it/stress-tests 场景。

| 阶段 | 总数 | 通过 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- | --- |
| Surefire | 4655 | 4652 | 0 | 0 | 3 |
| Failsafe | 296 | 292 | 0 | 0 | 4 |
| 合计 | 4951 | 4944 | 0 | 0 | 7 |

关键模块：storage starter 11 单测；file 59 单测；file-service 25 单测、50 IT；workflow-service 36 单测、24 IT；system-service 4 单测；全部零失败、零错误、零跳过。example 20 IT 中 17 通过、3 跳过。

跳过依据来自本轮 XML：system `KeycloakConfigTest` 3 个需要真实 Keycloak 服务器；example `WorkflowIntegrationTest` 3 个因独立测试库未创建工作流表而原有 `@Disabled`；Seata `SeataAtIntegrationTest` 1 个因环境变量 `APEX_SEATA_IT` 不存在。它们均未验证对应真实环境。真实云未执行也不能添加为 XML skipped 数。

3 个模块没有 Surefire XML（license-tools、example、services-bom），17 个模块没有 Failsafe XML；这表示没有可用该阶段报告，不推断未知测试通过或额外跳过。完整命令成功与阶段 XML 状态分别记录。

报告 XML 的全量覆盖率见正式汇总；starter 行/分支为 66.67%/54.26%，file 为 59.74%/45.38%，file-service 为 92.13%/76.09%，example 为 81.98%/56.25%。这些是报告全量计数，不等同于各模块 JaCoCo check 经排除后的范围，不能据全量百分比反推所有模块都执行相同门槛；本轮实际生效门禁均通过。没有 jacoco-it XML 时不虚构独立 IT 覆盖率。

### 补充检查与报告来源

存储 starter 的后续纯注释修改运行 `source scripts/build/init-build-env.sh && mvnd -B -pl :apex-storage-spring-boot-starter spotless:check`，21:14:50 BUILD SUCCESS。注释不改变行为；不能把此命令说成完整回归。

正式报告由 `TEST_REPORT_OUTPUT_DIR=docs/08-project-management/reports/storage-refactoring-tests bash scripts/test/generate-comprehensive-test-report.sh` 生成。脚本仅解析实际存在的 XML，不执行测试；无报告标记未执行或无可用报告，不自动记通过。Surefire/Failsafe 分开统计，JSON 保留来源路径和修改时间；覆盖率分别列单测与 IT，不相加。

正式产物于 21:44:29 生成：[测试汇总](storage-refactoring-tests/test-report.md)与[可审计 JSON](storage-refactoring-tests/test-summary.json)，目录只包含这两个正式报告。来源为第三轮完整 clean verify 后的各模块 XML；脚本自身不证明门禁，门禁结论依据完整命令退出码与 reactor 终态。

最终执行 `bash -n` 检查两份报告脚本、部署 app.sh、构建初始化脚本，执行 `bash scripts/ci/check-doc-links.sh` 与 `git diff --check`，组合命令退出码 0：655 份 Markdown、2487 个链接、0 断链；Bash 语法和空白检查均通过。现有 MySQL/Redis 容器已启动以满足本机测试前提，未删除测试依赖或数据。
