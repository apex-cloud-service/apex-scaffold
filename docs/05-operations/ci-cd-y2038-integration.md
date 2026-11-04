# CI/CD 集成说明 - Y2038 时间戳检查

本文档说明如何在 CI/CD 流水线中集成 Y2038 时间戳问题的自动检查机制。

## 集成概述

我们已将以下检查集成到 GitHub Actions CI/CD 流水线中:

1. **数据库脚本检查** (`database-check` job)
2. **ArchUnit 架构测试** (`unit-test` job 中的专项测试)

这些检查会在每次提交和 Pull Request 时自动运行,确保新增代码符合时间字段使用规范。

## 集成详情

### 1. 数据库脚本检查任务 (database-check)

**任务位置**: `.github/workflows/ci-cd.yml` 第 16-38 行

**运行时机**: 
- 推送到 `main` 或 `develop` 分支时
- 创建针对 `main` 分支的 Pull Request 时

**检查内容**:
- 扫描所有 MySQL Flyway 迁移脚本
- 检测是否使用了 `TIMESTAMP` 类型(应使用 `DATETIME`)
- 排除合法的 `CURRENT_TIMESTAMP` 用法

**执行步骤**:
```yaml
- name: Check Flyway scripts for TIMESTAMP type usage
  run: |
    chmod +x scripts/dev/check/check-flyway-timestamp-usage.sh
    bash scripts/dev/check/check-flyway-timestamp-usage.sh
```

**失败处理**:
- 如果检查失败,任务会上传检查结果到 GitHub Artifacts
- 构建、发布等后续任务将被阻止
- PR 无法合并,直到问题修复

**示例输出**:
```
========================================
检查 Flyway 脚本中的 TIMESTAMP 类型使用...
========================================

==> 检查 MySQL 脚本中的 TIMESTAMP 类型使用
✗ 发现 1 个 MySQL 脚本使用了 TIMESTAMP 类型

问题详情:
----------------------------------------
apex-system/src/main/resources/db/migration/mysql/V25__oauth2_authorization_server_schema.sql:
17:    client_secret_expires_at TIMESTAMP NULL COMMENT '客户端密钥过期时间',
----------------------------------------

[ERROR] MySQL 数据库必须使用 DATETIME 类型，禁止使用 TIMESTAMP 类型
[ERROR] TIMESTAMP 类型范围限制为 1970-2038，存在 Y2038 问题
```

### 2. ArchUnit 架构测试 (unit-test job)

**任务位置**: `.github/workflows/ci-cd.yml` 第 101-102 行

**运行时机**: 
- 在单元测试任务 (`unit-test`) 中执行
- 每次提交和 Pull Request 时运行

**检查内容**:
- 检查 PO 类和领域实体中的时间字段是否使用 `LocalDateTime`
- 检查是否存在使用 32 位整数存储时间戳的情况
- 验证时间字段命名规范

**执行步骤**:
```yaml
- name: Run ArchUnit architecture tests
  run: mvn -B -pl apex-infrastructure test -Dtest=TimeFieldArchTest,TenantIsolationArchTest
```

**测试规则**:

1. **PO 层时间字段检查** (`time_fields_in_po_must_use_local_date_time`)
   - 检查 `persistence.po` 包中的时间点字段
   - 强制使用 `LocalDateTime` 类型
   - 匹配字段名: `createTime`, `updateTime`, `deletedAt`, `expireAt`, `loginTime`, `callTime` 等

2. **领域层时间字段检查** (`entity_time_fields_must_use_local_date_time`)
   - 检查 `domain` 包中的时间点字段
   - 强制使用 `LocalDateTime` 类型
   - 确保领域模型与持久化模型一致

**测试报告**:
```
[INFO] Running com.apex.scaffold.arch.TimeFieldArchTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

### 3. 任务依赖关系

我们已更新 CI/CD 流水线的任务依赖关系,确保质量检查通过后才进行构建和发布:

```yaml
build-image:
  needs: [database-check, code-quality, unit-test, integration-test]

release:
  needs: [database-check, code-quality, unit-test, integration-test]
```

**依赖链**:
```
database-check ──┐
code-quality ────┤
unit-test ───────┼──> build-image ──> Podman 镜像发布
integration-test ┘                   或
                                     release ──> GitHub Release
```

## 本地验证

在提交代码前,建议在本地运行以下检查:

### 检查 Flyway 脚本

```bash
# 进入项目根目录
cd /Users/work/apex-scaffold

# 运行数据库脚本检查
bash scripts/dev/check/check-flyway-timestamp-usage.sh
```

### 运行 ArchUnit 测试

```bash
# 运行所有架构测试
mvnd -pl apex-infrastructure test -Dtest=*ArchTest

# 仅运行时间字段测试
mvnd -pl apex-infrastructure test -Dtest=TimeFieldArchTest
```

## CI/CD 执行流程

### 提交到分支时

1. **数据库脚本检查** (database-check)
   - ✅ 通过: 检查下一个任务
   - ❌ 失败: 阻止后续任务,提示开发者修复

2. **代码质量检查** (code-quality)
   - 编译检查
   - Spotless 代码格式检查
   - PMD 静态分析

3. **单元测试** (unit-test)
   - 运行所有单元测试
   - **运行 ArchUnit 架构测试**(包含时间字段检查)
   - 上传测试报告和覆盖率

4. **集成测试** (integration-test)
   - 启动 MySQL 和 Redis 服务
   - 运行集成测试

5. **构建镜像** (build-image, 仅 main 分支)
   - 构建 JAR 包
   - 构建 Podman 镜像
   - 推送到 Docker Hub

### Pull Request 时

PR 检查会自动运行以下任务:
- ✅ Database Script Check
- ✅ Code Quality Check
- ✅ Unit Tests (含 ArchUnit)
- ✅ Integration Tests

所有检查通过后,PR 才能合并。

## 常见问题

### Q1: 如果 database-check 失败怎么办?

**原因**: 新增或修改的 Flyway 脚本中使用了 `TIMESTAMP` 类型。

**解决方案**:
1. 查看 CI 日志中的错误详情
2. 将 `TIMESTAMP` 改为 `DATETIME`
3. 重新提交代码

**示例**:
```sql
-- ❌ 错误
client_secret_expires_at TIMESTAMP NULL

-- ✅ 正确
client_secret_expires_at DATETIME NULL
```

### Q2: 如果 ArchUnit 测试失败怎么办?

**原因**: 代码中存在不符合时间字段使用规范的情况。

**解决方案**:
1. 查看测试报告中的具体违规字段
2. 将时间点字段改为 `LocalDateTime` 类型
3. 确保字段命名符合规范

**示例**:
```java
// ❌ 错误: 使用 Long 存储时间点
private Long createTime;

// ✅ 正确: 使用 LocalDateTime
private LocalDateTime createTime;
```

### Q3: 如何跳过某些特殊情况?

**不推荐跳过检查!** 

如果确实存在特殊情况(如第三方库要求、遗留系统兼容等),可以:

1. **数据库脚本**: 在检查脚本中添加排除规则
2. **ArchUnit 测试**: 修改测试规则,添加例外条件

但必须在 PR 中说明跳过的原因,并经过团队审查。

### Q4: 如何查看检查结果?

**GitHub Actions 页面**:
1. 进入仓库 → Actions 标签页
2. 选择对应的工作流运行记录
3. 查看 `database-check` 或 `unit-test` 任务的日志

**失败时**:
- 下载 Artifacts 中的检查结果文件
- 查看详细的错误信息和文件列表

## 最佳实践

### 1. 提交前本地检查

```bash
# 完整的提交前检查流程
mvnd spotless:check                                          # 代码格式
bash scripts/dev/check/check-flyway-timestamp-usage.sh       # 数据库脚本
mvnd -pl apex-infrastructure test -Dtest=TimeFieldArchTest   # 架构测试
mvnd -P basic clean test                                     # 单元测试
```

### 2. 新增 Flyway 脚本规范

创建新的 Flyway 迁移脚本时:

```sql
-- ✅ 推荐: MySQL 使用 DATETIME
CREATE TABLE example (
    id BIGINT PRIMARY KEY,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expire_at DATETIME NULL
);

-- ❌ 禁止: MySQL 使用 TIMESTAMP
CREATE TABLE example (
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 会触发 CI 失败
);
```

### 3. Java 实体类规范

```java
// ✅ 推荐: 使用 LocalDateTime
@Data
@TableName("example")
public class ExamplePO extends BasePO {
    @TableField("create_time")
    private LocalDateTime createTime;
    
    @TableField("expire_at")
    private LocalDateTime expireAt;
}

// ❌ 禁止: 使用 Integer/Long 存储时间点
private Integer createTime;  // 会触发 ArchUnit 测试失败
private Long expireAt;       // 会触发 ArchUnit 测试失败
```

### 4. 时间字段命名规范

**时间点字段**(使用 LocalDateTime):
- `createTime`, `updateTime`, `deletedAt`
- `expireAt`, `expiredAt`
- `loginTime`, `logoutTime`
- `accessTime`, `heartbeatTime`
- `callTime`, `operationTime`
- `issuedAt`, `expiresAt`

**耗时字段**(使用 Long,表示毫秒数):
- `executionTime` (执行耗时)
- `duration` (持续时长)
- `elapsedTime` (经过时间)

## 维护说明

### 更新检查脚本

如果需要修改 Flyway 脚本检查逻辑:

1. 编辑 `scripts/dev/check/check-flyway-timestamp-usage.sh`
2. 在本地测试验证
3. 提交代码后自动应用到 CI/CD

### 更新 ArchUnit 规则

如果需要修改架构测试规则:

1. 编辑 `apex-infrastructure-persistence/src/test/java/com/apex/scaffold/arch/TimeFieldArchTest.java`
2. 本地运行测试验证
3. 提交代码后自动应用到 CI/CD

### 添加新的检查

如果需要添加新的时间字段相关检查:

1. 在 `scripts/dev/check/` 目录下创建新的检查脚本
2. 在 `.github/workflows/ci-cd.yml` 中添加新的检查步骤
3. 更新本文档

## 相关文档

- **开发规范**: [AGENTS.md#持久化、迁移与租户隔离](../../AGENTS.md#持久化迁移与租户隔离)
- **Y2038 分析报告**: [Y2038-timestamp-analysis-report.md](../_archive/trae-documents/Y2038-timestamp-analysis-report.md)
- **检查脚本**: [check-flyway-timestamp-usage.sh](../../scripts/dev/check/check-flyway-timestamp-usage.sh)
- **架构测试**: [TimeFieldArchTest.java](../../apex-infrastructure-persistence/src/test/java/com/apex/scaffold/arch/TimeFieldArchTest.java)
- **CI/CD 配置**: [ci-cd.yml](../../.github/workflows/ci-cd.yml)

---

**文档版本**: 1.0.0  
**最后更新**: 2026-09-23  
**维护者**: Apex Scaffold Team
