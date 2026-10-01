# SpringDoc/Swagger 注解完整性检查脚本

## 概述

`check-springdoc-annotations.sh` 是一个用于检查 Apex Scaffold 项目中 SpringDoc/Swagger 注解完整性的自动化脚本。该脚本会扫描项目代码，验证 API 文档注解的覆盖情况，帮助确保在线 API 文档的完整性和准确性。

## 功能特性

### 1. Router 类检查
- 检查所有 Router 类是否添加了 `@RouterOperations` 注解
- 统计已注解和缺失注解的 Router 类数量
- 计算注解覆盖率

### 2. Handler 方法检查
- 检查所有 Handler 类的 public 方法是否添加了 `@Operation` 注解
- 统计每个 Handler 的方法数量和注解数量
- 标识缺失注解的方法

### 3. DTO 类检查
- 检查 Request、Response、DTO、VO 等数据传输对象
- 验证类级别和字段级别的 `@Schema` 注解
- 统计 @Schema 注解总数

### 4. JavaDoc 检查
- 验证 Handler 方法是否有完整的 JavaDoc 注释
- 检查注释格式是否符合规范

## 使用方法

### 基本用法

```bash
# 在项目根目录执行
cd /Users/work/apex-scaffold
./scripts/dev/check/check-springdoc-annotations.sh
```

### 生成详细报告

```bash
# 生成报告文件
./scripts/dev/check/check-springdoc-annotations.sh --report /tmp/springdoc-report.txt

# 查看报告内容
cat /tmp/springdoc-report.txt
```

### 详细模式（显示每个文件的检查结果）

```bash
# 启用详细输出
./scripts/dev/check/check-springdoc-annotations.sh --verbose

# 或使用短参数
./scripts/dev/check/check-springdoc-annotations.sh -v
```

### 查看帮助信息

```bash
./scripts/dev/check/check-springdoc-annotations.sh --help
```

## 输出说明

### 正常输出示例

```
========================================
SpringDoc/Swagger 注解完整性检查
========================================
项目路径: /Users/work/apex-scaffold
检查时间: 2026-09-20 08:26:31

========================================
1. 检查 Router 类 @RouterOperations 注解
========================================
✓ DocumentRouter
✓ ChartRouter
✓ WorkflowRouter
✗ MessageRouter 缺少 @RouterOperations 注解

ℹ Router 类统计: 总数=18, 已注解=13, 缺失=4

========================================
2. 检查 Handler 方法 @Operation 注解
========================================
✓ HealthHandler (方法:3, @Operation:3)
✓ WorkflowHandler (方法:12, @Operation:12)
⚠ AuthHandler (方法:10, @Operation:9, 缺失:1)

ℹ Handler 统计: 类数=53, 方法总数=273
ℹ              @Operation:265, 缺失:8

========================================
3. 检查 DTO @Schema 注解
========================================
ℹ 检查模块: apex-interfaces
⚠ RollbackTaskRequest 没有 @Schema 注解
✓ LoginRequest

ℹ DTO 总统计: 总数=146, 有@Schema=113, 缺失=33
ℹ            @Schema注解总数=1233

========================================
4. 检查 Handler 方法 JavaDoc
========================================
✓ 所有 Handler 方法都有 JavaDoc
或
⚠ 发现 265 个方法缺少 JavaDoc

========================================
检查总结
========================================

┌─────────────────────────────────────────────┐
│          SpringDoc 注解完整性报告            │
├─────────────────────────────────────────────┤
│ 项目               │   总数 │ 已注解 │
├─────────────────────────────────────────────┤
│ Router 类           │       18 │       13 │
│ Handler 方法       │      273 │      265 │
│ DTO 类              │      146 │      113 │
├─────────────────────────────────────────────┤
│ @Schema 注解总数 │               1233 │
└─────────────────────────────────────────────┘

覆盖率统计:
  Router @RouterOperations: 72%
  Handler @Operation: 97%
  DTO @Schema: 77%
```

### 状态符号说明

- `✓` - 检查通过，注解完整
- `✗` - 检查失败，完全缺少注解
- `⚠` - 警告，部分缺少注解
- `ℹ` - 信息提示

### 退出码

- `0` - 所有检查通过
- `1` - 发现缺失的注解

## 检查标准

### Router 类要求

Router 类必须添加 `@RouterOperations` 注解来描述路由配置：

```java
@RouterOperations({
    @RouterOperation(
        path = "/api/workflow/definitions",
        method = RequestMethod.GET,
        beanClass = WorkflowHandler.class,
        beanMethod = "queryDefinitions",
        operation = @Operation(
            operationId = "queryWorkflowDefinitions",
            summary = "查询工作流定义列表",
            description = "分页查询工作流定义列表，支持按名称、分类等条件过滤",
            tags = {"工作流管理"}
        )
    )
})
```

### Handler 方法要求

Handler 的所有 public 方法必须添加 `@Operation` 注解：

```java
@Operation(
    operationId = "queryWorkflowDefinitions",
    summary = "查询工作流定义列表",
    description = "分页查询工作流定义列表，支持按名称、分类等条件过滤。返回分页结果，包含工作流定义的基本信息、版本号、状态等。"
)
public Mono<ServerResponse> queryDefinitions(ServerRequest request) {
    // ...
}
```

### DTO 类要求

Request、Response、DTO、VO 类必须添加 `@Schema` 注解：

```java
@Data
@Schema(description = "工作流任务回退请求")
public class RollbackTaskRequest {
    
    @Schema(description = "任务ID", required = true, example = "task-001")
    private String taskId;
    
    @Schema(description = "回退原因", example = "需要重新审核")
    private String reason;
}
```

## 报告文件格式

使用 `--report` 参数生成的报告文件包含：

1. **检查摘要** - 总体统计信息
2. **缺失清单** - 所有缺失注解的详细列表
   - 缺失 @RouterOperations 的 Router 类
   - 缺失 @Operation 的 Handler 方法
   - 缺失 @Schema 的 DTO 类
3. **建议修复** - 针对性的修复建议

## 集成到 CI/CD

### 方法 1：使用覆盖率检查脚本（推荐）

项目提供了专门的 CI/CD 覆盖率检查脚本，可以设置最低覆盖率要求：

```bash
#!/bin/bash
# ci-check-annotations.sh

set -e

echo "检查 SpringDoc 注解覆盖率..."

# 使用默认覆盖率要求（Router 70%, Handler 95%, DTO 75%）
./scripts/ci/check-springdoc-coverage.sh

# 或者自定义覆盖率要求
# ./scripts/ci/check-springdoc-coverage.sh --min-router 80 --min-handler 95 --min-dto 80
```

脚本会：
- 自动运行完整检查
- 提取覆盖率数据
- 与设定的最低要求比较
- 返回 0（通过）或 1（不通过）

### 方法 2：使用完整检查脚本

如果需要更详细的报告，可以直接使用检查脚本：

```bash
#!/bin/bash
# ci-check-annotations-detailed.sh

set -e

echo "检查 SpringDoc 注解完整性..."
./scripts/dev/check/check-springdoc-annotations.sh --report build/springdoc-report.txt

# 检查退出码
if [ $? -ne 0 ]; then
    echo "❌ SpringDoc 注解检查失败"
    echo "详细报告: build/springdoc-report.txt"
    exit 1
fi

echo "✅ SpringDoc 注解检查通过"
```

### GitHub Actions 集成示例

```yaml
name: Code Quality Check

on: [push, pull_request]

jobs:
  springdoc-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'oracle'
      
      - name: Check SpringDoc Annotations Coverage
        run: |
          chmod +x scripts/ci/check-springdoc-coverage.sh
          ./scripts/ci/check-springdoc-coverage.sh
      
      - name: Upload Report
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: springdoc-report
          path: /tmp/springdoc-report.txt
```

### GitLab CI 集成示例

```yaml
springdoc-check:
  stage: test
  script:
    - chmod +x scripts/ci/check-springdoc-coverage.sh
    - ./scripts/ci/check-springdoc-coverage.sh --min-router 80 --min-handler 95 --min-dto 80
  artifacts:
    when: on_failure
    paths:
      - /tmp/springdoc-report.txt
    expire_in: 1 week
  only:
    - merge_requests
    - main
```

## 常见问题

### Q1: 脚本报告 BaseHandler 缺少注解，是否需要修复？

A: BaseHandler 是抽象基类，通常不需要 @Operation 注解。如果不是对外暴露的 API Handler，可以忽略这个警告。

### Q2: 某些 Router 使用了注解式编程，是否需要 @RouterOperations？

A: 项目规范要求使用函数式 Router + Handler 模式，不应该使用 @Controller 或 @RestController。如果发现注解式接口，应该按规范重构为函数式路由。

### Q3: JavaDoc 检查报告很多缺失，如何批量添加？

A: 可以使用项目提供的批量添加脚本：

```bash
./scripts/dev/format/batch-add-javadoc.sh apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler
```

### Q4: 为什么有些 DTO 没有 @Schema 注解？

A: 可能是新增的 DTO 类。应该按照规范添加类级别和字段级别的 @Schema 注解，确保 API 文档完整。

## 相关脚本

- `scripts/dev/format/batch-add-javadoc.sh` - 批量添加 JavaDoc 注释
- `scripts/dev/check/scan-javadoc-issues.sh` - 扫描 JavaDoc 问题
- `scripts/dev/check/scan-lombok-issues.sh` - 扫描 Lombok 使用问题

## 维护说明

### 修改检查规则

如果需要修改检查规则，编辑脚本中的相关函数：

- `check_router_annotations()` - Router 类检查逻辑
- `check_handler_annotations()` - Handler 方法检查逻辑
- `check_dto_schema()` - DTO 检查逻辑
- `check_handler_javadoc()` - JavaDoc 检查逻辑

### 添加新的检查项

在脚本末尾的主流程中添加新的检查函数调用即可。

## 版本历史

- v1.0.0 (2026-09-20) - 初始版本
  - Router 类 @RouterOperations 注解检查
  - Handler 方法 @Operation 注解检查
  - DTO @Schema 注解检查
  - JavaDoc 完整性检查
  - 生成详细报告功能

## 许可证

本脚本是 Apex Scaffold 项目的一部分，遵循项目许可证。
