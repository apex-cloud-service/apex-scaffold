## 改造目标

评审并优化全仓库 Java 代码：将所有传统冒号形式 `switch` 语句改为 Java 21 箭头形式（switch 表达式或语句式箭头 switch），并将适合的 for 循环改为 lambda/stream。代码库已高度现代化（73/87 个 switch 已是箭头形式，匿名函数式接口内部类为 0），本次仅改造确认可改善可读性的点。

## 一、switch 改造（14 处，12 个文件）

### A 类：改为 switch 表达式 `return switch(x) { case ... -> 值; }`（case 内直接 return 单值，无 break）

1. **`apex-generator/.../FieldDefinition.java:91`** — `sqlType()`：6 case String→String，`return switch (simpleType()) { case "Long" -> "BIGINT"; ... default -> "VARCHAR(255)"; };`
2. **`apex-system/.../domain/model/entity/UserComplianceEntity.java:276`** — `ShadowBanStage.next()`：4 case 枚举→枚举，`return switch (this) { case NONE -> STAGE_1; ... default -> STAGE_1; };`
3. **`apex-office/.../application/OfficeDocumentService.java:457`** — `convertDocumentType()`：3 case 枚举→String，`return switch (documentType) { case DOC -> "doc"; ... default -> "doc"; };`
4. **`apex-auth/.../domain/service/ResourcePermissionService.java:450`** — `hasRequiredPermission()`：4 case String→boolean，`return switch (requiredPermission) { case "READ" -> userPermissions.contains(...); ... default -> false; };`
5. **`apex-infrastructure-cache/.../cache/aspect/CacheLockAspect.java:162`** — `handleFallback()`：3 case 枚举→Object/throw，`case RETURN_NULL -> null; case RETURN_DEFAULT -> invokeFallbackMethod(...); case THROW_EXCEPTION, default -> { throw new BusinessException(...); } }`
6. **`apex-workflow/.../application/scheduler/WorkflowTimeoutHandler.java:137`** — `handleTimeoutTask()`：改为 switch 表达式赋值 `result = switch (strategy) { case "ESCALATE", "AUTO_COMPLETE" -> workflowService.handleTimeoutTask(task); case "NOTIFY" -> { log.info(...); yield Mono.empty(); } default -> Mono.error(...); };`，删除末尾 break。

### B 类：改为语句式箭头 switch `switch(x) { case ... -> { ... } }`（case 体含副作用/多语句，消除 break）

7. **`apex-system/.../interfaces/config/OpenApiDocumentationCustomizer.java:533`** — `addOauthResponses()`：6 case String，每个 case 体含 responses 副作用 + `return true`。改为语句式箭头 switch：case 体用 `{ }` 包裹，末尾保持 `return true`，default `return false`。注意首个 case 合并 `case "initiateOidcLogin", OP_ID_INITIATE_SOCIAL_LOGIN ->`。
8. **`apex-system/.../application/listener/ResourceIndexEventListener.java:49`** — `onResourceIndexEvent()`：3 case 枚举，case 体是 Reactor 链 + `.subscribe()`。改为语句式箭头 switch：`case CREATE, UPDATE -> { indexSyncPort...subscribe(); }`、`case DELETE -> { ... }`、`default -> log.warn(...)`。删除 break。
9. **`apex-file/.../application/service/impl/ChartServiceImpl.java:248`** — `generateChartData()`：5 case String，case 体修改局部 `xAxis`（需保留可变性，不能用表达式赋值）+ `series.add`。改为语句式箭头 switch：`case "line", "bar" -> { series.add(...); series.add(...); }` 等，default 空块或省略。删除 break。
10. **`apex-infrastructure-messaging/.../mq/alert/MqAlertManager.java:277`** — `publishAlert()`：2 case 枚举（已用合并写法但仍是冒号形式）。改为 `case INFO, WARNING -> log.warn(message);`、`case ERROR, CRITICAL -> log.error(message);`、`default -> log.warn(...);`。删除 break。
11. **`apex-auth/.../infrastructure/security/ConfigEncryptionCLI.java:44`** — `main()`：4 case String，case 体含多语句 + System.exit。改为语句式箭头 switch，case 体用 `{ }`。删除 break。
12. **`apex-auth/.../infrastructure/security/ConfigEncryptionCLI.java:168`** — `interactiveMode()`：3 case String，同上处理。
13. **`scripts/utils/ConfigEncryptionTool.java:54`** — `main()`：5 case String（含 "help"/"-h"/"--help" 合并），case 体含局部变量声明 + 多语句。改为语句式箭头 switch。删除 break。注意 case 体内有局部变量声明（plaintext/ciphertext/key），箭头块 `{ }` 内声明无问题。
14. **`scripts/utils/ConfigEncryptionTool.java:258`** — 交互循环内：2 case String（"e"/"encrypt"、"d"/"decrypt" 合并），case 体含局部变量 + 多语句。改为语句式箭头 switch。

### 改写规则
- 枚举 switch 不在 case 后加枚举类名前缀（`case NONE ->` 而非 `case ShadowBanStage.NONE ->`）。
- 多 case 合并用逗号：`case "ESCALATE", "AUTO_COMPLETE" ->`。
- 无 `default` 且枚举已全覆盖的，不强行补 default（遵循 Java 21 编译器对枚举穷尽性检查）。但若原代码有 default（含兜底返回），保留对应 default 分支。
- 语句式箭头 case 体只有单条语句时不加大括号；多条语句或含局部变量声明时加 `{ }`。
- 不改变任何业务逻辑、控制流、日志文案、返回值。仅改写 switch 语法形态。

## 二、lambda 改造（5 处，保留 2 处受检异常不改）

15. **`apex-system/.../infrastructure/search/MeilisearchAdapter.java:99`** — for→`stream().map(hit -> mapToEntity((Map<String,Object>) hit)).toList()`，`@SuppressWarnings("unchecked")` 提到方法级或保留局部。
16. **`apex-infrastructure-persistence/.../datapermission/MdcDataScopeProvider.java:99`** — for→`Arrays.stream(value.split(COMMA_SEPARATOR)).map(String::trim).map(MdcDataScopeProvider::parseLongSafe).filter(Objects::nonNull).toList()`。
17. **`apex-common/.../util/LogSafetyUtil.java:83`** — for→`excluded == null ? Set.of() : Arrays.stream(excluded).map(LogSafetyUtil::normalize).collect(Collectors.toSet())`。
18. **`apex-tools-deployment/.../DocLinksChecker.java:186`** — for→`checked.stream().map(CheckedLink::target).collect(Collectors.toSet())`。
19. **`apex-tools-deployment/.../DocLinksChecker.java:260`** — for→`Arrays.stream(stdout.split("\n")).filter(s -> !s.isBlank()).map(projectRoot::resolve).forEach(ignored::add)`。
20. **`apex-workflow/.../application/validation/WorkflowDefinitionGraph.java:307`** — for→`nodes.keySet().stream().filter(nodeId -> !visited.contains(nodeId)).toList()`。

### 明确不改的点
- **MysqlClient.rows()、TokenV42Recover.columnMetadata()**：涉及受检异常（JsonProcessingException/Exception），改 stream 需 lambda 内 try-catch 包裹，可读性下降，保留 for 循环（用户已确认）。
- **WorkflowFeignClientFallbackFactory**：技术上是单方法接口可转 lambda，但同伴 Auth/System 降级工厂不是函数式接口，为风格一致保留匿名类。
- **WeChatAppAdvancedService 的 ThreadFactory 匿名类**：持有可变状态 AtomicInteger，改 lambda 需重构状态归属，价值低，保留。
- 所有 Reactor 响应式链、带复杂副作用的 for 循环、索引依赖的 for 循环均不动。

## 三、执行顺序与分组

按模块依赖与隔离性分组执行，便于定位问题：

- **批次 1（共享层，影响面最广但改动最简单）**：apex-common LogSafetyUtil、apex-infrastructure-persistence MdcDataScopeProvider、apex-infrastructure-cache CacheLockAspect、apex-infrastructure-messaging MqAlertManager。
- **批次 2（业务库）**：apex-auth ResourcePermissionService + ConfigEncryptionCLI、apex-office OfficeDocumentService、apex-file ChartServiceImpl、apex-workflow WorkflowTimeoutHandler + WorkflowDefinitionGraph、apex-system UserComplianceEntity + ResourceIndexEventListener + OpenApiDocumentationCustomizer + MeilisearchAdapter、apex-generator FieldDefinition。
- **批次 3（独立脚本）**：scripts/utils/ConfigEncryptionTool、apex-tools-deployment DocLinksChecker。

## 四、验证方案

1. **编译验证**：`source scripts/build/init-build-env.sh` 后执行 `mvnd -T4 -B -pl <受影响模块> -am test` 针对各批次。完整门禁 `mvnd -T4 -B clean verify` 作为最终回归。
2. **格式检查**：`mvnd -T4 -B spotless:check`；若 Spotless 报格式差异，按其规则 `spotless:apply` 仅作用于本次改动文件。
3. **架构检查**：本次仅改语法形态，不触碰分层/调度器/租户边界，现有 ArchTest（LayeringArchTest、NoBoundedElasticArchGuardTest 等）应不受影响，但仍纳入 verify 确认。
4. **空白检查**：`git diff --check` 确认无行尾空白。
5. **scripts 独立验证**：ConfigEncryptionTool.java 不在 Maven reactor，用 `javac`（21.0.13）单独编译确认语法正确。
6. **不改业务逻辑**：所有改写保持控制流、返回值、日志文案、异常类型不变；switch 表达式的 default 分支与原 default 等价。

## 五、不涉及的范围

- 不引入 switch 模式匹配（`case 类型 i ->`）：仓库无典型 instanceof 类型分支场景，强行引入无收益。
- 不改已为箭头形式的 73 个 switch。
- 不改任何配置、POM、测试逻辑（除非测试中的 switch 也是传统形式——经核查测试类 switch 全已是箭头形式，无需改动）。
- 不创建新文档、不提交 Git commit。