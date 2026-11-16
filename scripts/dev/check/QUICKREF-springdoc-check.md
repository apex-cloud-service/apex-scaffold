# SpringDoc 注解检查工具 - 快速参考

## 🚀 快速开始

### 检查当前项目的注解覆盖率

```bash
cd /Users/work/apex-scaffold
./scripts/dev/check/check-springdoc-annotations.sh
```

### 生成详细报告

```bash
./scripts/dev/check/check-springdoc-annotations.sh --report /tmp/report.txt
cat /tmp/report.txt
```

### CI/CD 覆盖率检查

```bash
./scripts/ci/check-springdoc-coverage.sh
```

## 📊 当前覆盖率状态

| 检查项 | 当前覆盖率 | 最低要求 | 状态 |
|--------|-----------|---------|------|
| Router @RouterOperations | 72% | 70% | ✅ 达标 |
| Handler @Operation | 97% | 95% | ✅ 达标 |
| DTO @Schema | 77% | 75% | ✅ 达标 |

## 📝 常用命令

### 查看帮助

```bash
./scripts/dev/check/check-springdoc-annotations.sh --help
./scripts/ci/check-springdoc-coverage.sh --help
```

### 详细模式（显示每个文件的检查结果）

```bash
./scripts/dev/check/check-springdoc-annotations.sh --verbose
```

### 自定义 CI/CD 覆盖率要求

```bash
./scripts/ci/check-springdoc-coverage.sh --min-router 80 --min-handler 95 --min-dto 80
```

## 🔧 缺失注解修复指南

### Router 类缺少 @RouterOperations

**缺失的 Router 类**：
- MessageRouter
- ApiRouter
- OrgSyncRouter
- OfficeIntegrationRouter

**修复步骤**：
1. 查看已完成的示例（DocumentRouter、ChartRouter、WorkflowRouter）
2. 添加必要的 import：
   ```java
   import io.swagger.v3.oas.annotations.Operation;
   import io.swagger.v3.oas.annotations.Parameter;
   import org.springdoc.core.annotations.RouterOperation;
   import org.springdoc.core.annotations.RouterOperations;
   import org.springframework.web.bind.annotation.RequestMethod;
   ```
3. 在 @Bean 方法上添加 @RouterOperations 注解
4. 运行检查验证：`./scripts/dev/check/check-springdoc-annotations.sh`

### Handler 方法缺少 @Operation

**修复方法**：

查看已有的 Handler 方法示例，添加 @Operation 注解：

```java
@Operation(
    operationId = "queryWorkflowDefinitions",
    summary = "查询工作流定义列表",
    description = "分页查询工作流定义列表，支持按名称、分类等条件过滤"
)
public Mono<ServerResponse> queryDefinitions(ServerRequest request) {
    // 方法实现
}
```

### DTO 类缺少 @Schema

**缺失的 DTO 类**（部分）：
- RollbackTaskRequest
- CompleteTaskRequest
- OAuth2ClientRequest
- WorkflowTrendDTO
- DatabaseConfigRequest
- AdminUserDetailResponse
- 等 33 个

**修复方法**：

1. 类级别添加 @Schema：
   ```java
   @Data
   @Schema(description = "工作流任务回退请求")
   public class RollbackTaskRequest {
       // ...
   }
   ```

2. 字段级别添加 @Schema：
   ```java
   @Schema(description = "任务ID", required = true, example = "task-001")
   private String taskId;
   
   @Schema(description = "回退原因", example = "需要重新审核")
   private String reason;
   ```

### 批量添加 JavaDoc

```bash
./scripts/dev/format/batch-add-javadoc.sh apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler
```

## 📖 完整文档

详细使用说明请查看：[scripts/README-check-springdoc.md](README-check-springdoc.md)

## 🎯 提交前检查清单

- [ ] 运行 SpringDoc 注解检查
- [ ] 覆盖率达到最低要求
- [ ] 所有新增接口都有完整注解
- [ ] 代码格式检查通过（`mvnd spotless:check`）
- [ ] 编译通过（`mvnd clean compile -DskipTests`）

## 🆘 常见问题

**Q: 脚本报告 "项目路径错误"？**
A: 确保从项目根目录运行脚本，或使用绝对路径。

**Q: BaseHandler 缺少注解，是否需要修复？**
A: BaseHandler 是抽象基类，如果是内部辅助方法可以忽略，如果是公共 API 方法则需要添加。

**Q: 如何提高覆盖率到 90%+？**
A: 按优先级修复：先修复 Router 类（影响大），再修复 Handler 方法，最后完善 DTO。

**Q: CI/CD 检查失败怎么办？**
A: 查看详细报告（`cat /tmp/springdoc-report.txt`），定位缺失的注解，逐个修复。

## 📞 支持

遇到问题请查看：
- 完整文档：`scripts/README-check-springdoc.md`
- 工作总结：`docs/_archive/trae-documents/SpringDoc注解完善工作总结.md`
- 项目规范：`AGENTS.md`

---

**最后更新**: 2026-09-20
**维护者**: Apex Scaffold Team
