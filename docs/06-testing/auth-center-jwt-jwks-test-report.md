# 认证中心 JWT/JWKS 测试报告 — apex-auth-center（Phase 2.1/2.2）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 报告日期：2026-10-08 ｜ 执行环境：macOS（Java 17.0.12-oracle + Maven Daemon 2.0.0-rc-3）
> 所属阶段：微服务改造执行计划 Phase 2.1/2.2（`docs/08-project-management/plans/microservice-migration-plan.md` §7.2 JWT+JWKS 非对称 / §16 风险 #1 双 kid 过渡 / §17 验收）

## 一、测试概览

| 项目 | 结果 |
|------|------|
| 模块编译（`mvnd -pl apex-auth-center -am test-compile -DskipTests`） | ✅ BUILD SUCCESS |
| 单元测试（surefire，定向 4 个测试类） | ✅ 61/61 通过（0 失败 / 0 错误 / 0 跳过） |
| 本阶段核心覆盖 | RS256 非对称签发/验签、keyId 选择与回退、密钥轮换、RefreshToken 轮换、Token 服务（签发/校验/撤销/刷新） |

## 二、测试命令

```bash
# 编译验证（主代码 + 测试代码）
mvnd -pl apex-auth-center -am test-compile -DskipTests

# 定向运行 Phase 2.1/2.2 相关测试类（-am 构建上游模块时需忽略无匹配用例的模块）
mvnd -pl apex-auth-center -am test \
  -Dtest='JwtKeyMaterialServiceTest,JwtKeyRotationServiceTest,RefreshTokenRotationServiceTest,TokenServiceImplTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 三、测试范围与结果（定向 4 个测试类）

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| `domain.service.JwtKeyMaterialServiceTest` | 8 | ✅ | RS256 签发→验签 roundtrip；无 kid 回退当前活跃公钥；按 kid 选择对应公钥；缺失公钥报错；签名不匹配拒绝；畸形 Token 拒绝；`availablePublicKeys()` JWKS 投影（活跃 + 宽限期旧公钥） |
| `domain.service.JwtKeyRotationServiceTest` | 16 | ✅ | 密钥对生成、按计划/到期轮换、密钥失效与清理、Redis 持久化、宽限期并存（双 kid 过渡） |
| `application.service.RefreshTokenRotationServiceTest` | 11 | ✅ | RefreshToken 轮换：正确令牌类型/用户匹配/家族序列递增、Token 复用检测、换发后旧令牌失效、UserNotFound/解析失败/已吊销/已过期/错误类型等异常路径；日志脱敏（不输出凭据与异常全文） |
| `application.service.TokenServiceImplTest` | 26 | ✅ | 签发（含多端统一账户 IssueTokenUnifiedAccount）、提取 userId、刷新、撤销单令牌/撤销全部用户令牌、校验（@Nested 分组共 26 用例） |

**合计：4 个测试类共 61 个用例，全部通过。**

> 用例分布说明：`RefreshTokenRotationServiceTest` 的 11 个用例含 `@Nested RotateRefreshTokenTest` 内 10 个 + 顶层日志脱敏 1 个；`TokenServiceImplTest` 的 26 个用例按 `@Nested` 分组（ExtractUserId 3 / IssueToken 6 / IssueTokenUnifiedAccount 2 / RefreshToken 3 / RevokeAllUserTokens 2 / RevokeToken 3 / ValidateToken 7）。

## 四、本阶段改造交付物

**新增（Phase 2.1/2.2）**：
- `domain/service/JwtKeyMaterialService.java`：JWT 非对称（RSA/RS256）密钥材料外观层 —— `sign(subject, claims, expireMillis)` 用当前活跃私钥签发、`parse(token)` 依 keyId 选公钥验签（无 kid 回退当前活跃公钥）、`availablePublicKeys()` 供 JWKS 端点做密钥集合投影；补中文注释说明 HS256→RS256 升级与过渡期双 kid 并存。
- `domain/service/JwtKeyMaterialServiceTest.java`：8 个用例（RS256 roundtrip、kid 选择/回退、JWKS 投影等）。

**改造（Phase 2.1/2.2）**：
- `application/service/TokenServiceImpl.java`：签发从 HS256 对称 `JwtUtil` 改为响应式 `jwtKeyMaterialService.sign(...)`（RS256）；`buildClaims` 不含 subject，subject 由 sign 方法显式传入 `String.valueOf(userId)`；`parse` 亦改走 `JwtKeyMaterialService`。
- `application/service/RefreshTokenRotationService.java` + `RefreshTokenRotationServiceTest.java`：随签发体系迁移至响应式 `JwtKeyMaterialService`（移除 `MockedStatic<JwtUtil>`，改 `@Mock JwtKeyMaterialService`），补中文注释说明迁移理由。
- `application/service/JwtKeyRotationService` 三实现合并为一：保留 `domain` 的 Redis-RSA 版本，删除 DB-HMAC 与内存-HMAC 实现（`application/service/JwtKeyRotationService`、`infrastructure/security/JwtKeyRotationService` 及其测试、`application/scheduler/JwtKeyRotationScheduler` 一并移除）。
- `TokenServiceImplTest.java`：随 `TokenServiceImpl` 重写（@Nested 分组，26 用例）。

**配置/资源治理（Phase 2.2）**：
- `application.yml`/`bootstrap.yml`：服务名收敛（bootstrap 采用目标名 `apex-auth-service`）。
- Flyway 重复 V49 修复：删除 `V49__add_high_risk_authorization_permissions.sql`，补 `V51__add_high_risk_authorization_permissions.sql`（mysql + postgresql 双套）。

## 五、事故披露与恢复记录

本轮推进 auth-center 编译与测试门禁时，先后定位并修复 2 类问题，均在本地闭环：

| # | 现象 | 根因 | 修复 |
|---|------|------|------|
| 1 | 主代码编译失败：`JwtKeyMaterialService.java:[158,52] unreported exception java.io.IOException` | `ObjectMapper.readTree(byte[])` 重载声明抛出 `IOException`（`JsonProcessingException` 父类），catch 仅捕获 `JsonProcessingException` | 导入改为 `java.io.IOException`，catch 子句改为 `catch (IllegalArgumentException \| IOException exception)` |
| 2 | 测试代码编译失败：`RefreshTokenRotationServiceTest` 9 处 "try without catch/finally" + 1 处 "class, interface, enum, or record expected" | 移除 `MockedStatic<JwtUtil>` 时遗留悬空 `try { }` 包裹体与多余大括号（位于 testRotate_TokenReuseDetected 之后） | 逐方法删除悬空 `try { }` 外层并删除多余 `}`，重跑 test-compile → BUILD SUCCESS |

> 构建工具说明：本项目强制使用 `mvnd`（Maven Daemon），禁止 `mvn`；详见 `docs/04-development/development-standards.md`。跨模块 `-Dtest` 过滤需追加 `-Dsurefire.failIfNoSpecifiedTests=false`，避免无匹配用例的上游模块（如 apex-common/apex-domain/apex-application）构建失败。

## 六、预存问题（建议后续关注）

| 问题 | 归因 | 建议 |
|------|------|------|
| 本报告仅覆盖定向 4 个测试类（Phase 2.1/2.2 改造相关），未跑 apex-auth-center 全部测试 | 聚焦本阶段改造正确性 | phase 级全门禁（`mvnd spotless:check` / `mvnd pmd:check` / ArchUnit / JaCoCo / `mvnd test`）在 Phase 2 退出标准验收时统一执行并归档 |
| 在线回归未执行 | 需 MySQL/Redis/Nacos 环境 | Phase 2 集成测试（登录/刷新/登出/MFA/OAuth2 全链路 + JWKS 验签）在具备外部依赖环境时运行 |
| 过渡期双 keyId（旧 HMAC + 新 RSA）并行验签 | 方案 §16 风险 #1 缓解策略 | 灰度 100%/流量迁移完成后，移除 `JwtAuthenticationFilter` 对称分支与旧 HMAC key，auth 侧 `general()` 不再签发对称 Token |

## 七、交付物清单（测试侧）

**已变更/新增测试代码**：
- `src/test/java/.../domain/service/JwtKeyMaterialServiceTest.java`（新增）
- `src/test/java/.../application/service/RefreshTokenRotationServiceTest.java`（改造）
- `src/test/java/.../application/service/TokenServiceImplTest.java`（重写）
- `src/test/java/.../application/service/JwtKeyRotationServiceTest.java`（删除，随实现合并）
- `src/test/java/.../infrastructure/security/JwtKeyRotationServiceTest.java`（删除，随实现合并）

**交付说明**：上述源码均按仓库规范补充中文注释（见 `docs/04-development/development-standards.md`），注释说明迁移背景、技术取舍与非对称升级理由。