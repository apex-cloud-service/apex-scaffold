## Context
用户要求评审当前项目的代码、文档、脚本及配置，并同步修复问题。初步只读核对发现认证链、安全上下文、事件发布和部署说明存在具体不一致；另有事务与发布打包风险需要进一步复现。目标是修复已证实问题并建立回归验证，而不是只输出评审报告，也不将抽样审查称为全仓无缺陷。

## 执行方案
### 1. 建立基线并完成专项核对
- 检查实时 Git 状态，保留已有改动；阅读目标模块 POM、配置和测试。
- 补齐因限流未完成的脚本／配置检查，重点核对环境初始化、Compose 注入、启动及发布包路径、凭据过滤和失败退出。
- 所有构建在当前 shell source `scripts/build/init-build-env.sh`，使用固定 JDK 与 mvnd、默认 Maven 本地仓库；不执行真实发布、密钥轮换或生产迁移。

### 2. 优先修复认证与租户安全
重点文件：
- `apex-auth-service/.../config/AuthCenterConfig.java`
- `apex-common/.../constant/SystemConstants.java`、网关 JWT 过滤器及对应路由配置
- `apex-auth-service/.../interfaces/handler/MfaHandler.java`、`AuthenticationRouter.java`
- `apex-auth/.../application/service/AuthenticationApplicationService.java`、`OAuth2Service.java`、`TotpService.java`
- auth 的 TOTP/MFA 持久化实现及相应测试

措施：
- 将 MFA 挑战交换纳入准确的公开路径规则；仍由已签名、有效、一次性挑战执行认证，不公开 MFA 管理接口。
- 将挑战中的可信租户覆盖完整用户查询／TOTP／令牌签发调用链，复用 `TenantContext` 的上下文桥接。
- 自助 MFA 操作从已验证认证上下文解析用户和租户，对现有 DTO 中不匹配的身份明确拒绝；保留 DTO 兼容性，不允许客户端选择其他主体。
- 用测试核对 OAuth2 password grant 是否绕过 MFA、租户状态或锁定策略；若证实则按 fail-closed 原则修复，不通过弱化主登录规则解决。
- 验证 TOTP 时间步消费失败、并发激活和后续写入失败；复用现有 JDBC 同步事务模式修复需原子完成的本地写入，不给 Mono 方法套 JDBC `@Transactional`。

### 3. 修复密码恢复与局部一致性
- `apex-auth/.../application/service/PasswordRecoveryApplicationService.java`：将撤销事件的返回 Mono 组合进主链，覆盖真实订阅与失败分支；沿用现有撤销状态契约。
- `apex-auth/.../domain/service/PasswordPolicyService.java`：对确认链和密码历史验证中的同步 BCrypt 使用惰性调用与 `BlockingIoSchedulers.general()` 隔离，测试实际线程及错误传播。
- 核对 `OfficeOrganizationSyncService`、`OfficeRepositoryImpl`、`OfficeTransactionConfig`：no-op 响应式事务不视为 JDBC 原子性；对证实存在的镜像／映射／检查点部分写入问题建立局部同步事务或沿用既有幂等补偿。不将远程同步放进长事务。

### 4. 修复脚本、配置和维护已有文档
- `scripts/release/release.sh`、`.github/workflows/release.yml`：统一版本校验、必要目录与工具 JAR 路径、私钥及卖方工具过滤；明确二进制发布包不具备完整源码构建上下文。优先复用统一组装逻辑并为包内容加检查。已有产物不擅自覆盖，真实发布不执行。
- `.github/workflows/doc-link-check.yml`：补齐根／模块 Markdown 与检查器的触发范围，内部链接检查成为强制门禁；构建步骤加载固定 SDKMAN 环境，外链检查不作为内部链接通过的替代。
- 修正 `docs/00-getting-started/quickstart.md`、`onboarding-guide.md`、`docs/05-operations/deployment.md`、`docs/07-commercial/license-management.md`、`docs/README.md`、`apex-domain/README.md`、`scripts/README.md` 中已证实的启动依赖、`.env` 加载、dev 首次迁移、容器健康端口、密钥命令、授权 CLI 与业务模块归属错误。
- 仅维护已有相关文档，不新建 README 或评审报告，不改写已执行的 Flyway 历史脚本，不扩展本次无关功能。

## 验证
1. 先补能复现的回归测试，再修到通过，重点覆盖匿名挑战交换、本人／他人／跨租户 MFA、挑战重放、OAuth2 拒绝策略、事件订阅及失败、局部写入回滚。
2. 受影响模块执行 `mvnd -T4 -B -pl <模块> -am test`，按实际依赖选择 auth-service、gateway、office-service、tools-deployment 等；必要时对真实数据库与服务安全链执行 verify。
3. 脚本语法检查、YAML／Compose 静态解析、发布组装的隔离临时目录回归；不启动生产服务、不上传发布包。
4. 运行 `scripts/ci/check-doc-links.sh`、Spotless 检查及 `git diff --check`。
5. 最后尝试 `mvnd -T4 -B clean verify`。如容器、Redis、远程依赖或固定构建工具缺失，明确报告失败／跳过范围，不称为完整通过。

## 交付
说明发现及修复的主要问题、实际变更范围、测试结果与未验证运行条件。不创建 Git commit、不推送、不发版。