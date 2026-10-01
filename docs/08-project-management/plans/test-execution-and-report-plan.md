# Apex Scaffold 全模块测试执行与报告生成计划

## 一、执行概述

### 1.1 测试目标

对 Apex Scaffold 项目所有模块执行全面测试,包括单元测试、集成测试、架构测试和代码质量检查,并生成详细的测试报告。

### 1.2 测试范围

**测试模块列表**:
- `apex-common` - 公共组件模块
- `apex-domain` - 领域层模块
- `apex-infrastructure` - 基础设施层模块
- `apex-application` - 应用层模块
- `apex-interfaces` - 接口层模块(启动模块)
- `apex-license-spring-boot-starter` - 授权 Starter 模块
- `apex-example` - 示例模块
- `apex-generator` - DDD 代码生成器模块

**测试类型**:
1. **单元测试** (Surefire) - 测试各模块的单元测试用例
2. **集成测试** (Failsafe) - 测试 `*IntegrationTest`、`*E2ETest`、`FlywayMigrationStartupTest` 等
3. **架构测试** (ArchUnit) - 验证 DDD 分层依赖、租户隔离、阻塞调度器隔离等架构规则
4. **代码质量检查** - Spotless 格式检查、PMD 代码质量检查

**测试 Profile**: `enterprise` - 企业版配置,包含全部功能

**报告输出目录**: `/Users/work/apex-scaffold/target/reports/`

### 1.3 报告内容

测试报告将包含:
- **测试执行统计** - 测试总数、通过数、失败数、跳过数、执行时间
- **代码覆盖率报告** - JaCoCo HTML 报告(行覆盖率、分支覆盖率)
- **失败用例详情** - 失败测试的堆栈跟踪、错误信息和重现步骤
- **模块测试矩阵** - 各模块的测试覆盖情况和通过率统计

## 二、实施步骤

### 2.1 Phase 1: 环境准备与初始化

**目标**: 确保构建环境就绪,清理旧的测试报告

**步骤**:
1. 加载 SDKMAN 环境,确保使用正确的 Java 17 和 Maven Daemon 版本
2. 清理项目构建产物,确保全新的测试环境
3. 创建测试报告输出目录

**执行命令**:
```bash
cd /Users/work/apex-scaffold
source scripts/build/init-build-env.sh
mvnd clean -DskipTests
mkdir -p target/reports/{surefire,failsafe,jacoco,quality,summary}
```

### 2.2 Phase 2: 单元测试执行 (Surefire)

**目标**: 执行所有模块的单元测试,生成测试报告和覆盖率数据

**测试范围**:
- 执行所有 `*Test.java` 文件
- 排除 `*IntegrationTest`、`*E2ETest`、`*StressTest`、`*SmokeTest` 等

**执行命令**:
```bash
mvnd clean test -P enterprise -Djacoco.skip=false
```

**预期输出**:
- 各模块 `target/surefire-reports/` - JUnit XML 和 TXT 测试报告
- 各模块 `target/jacoco.exec` - JaCoCo 覆盖率数据文件

**收集报告**:
```bash
# 复制 Surefire 测试报告到统一目录
find . -path "*/target/surefire-reports/*" -name "*.xml" -exec cp {} target/reports/surefire/ \;
find . -path "*/target/surefire-reports/*" -name "*.txt" -exec cp {} target/reports/surefire/ \;
```

### 2.3 Phase 3: 集成测试执行 (Failsafe)

**目标**: 执行集成测试、E2E 测试和启动测试

**测试范围**:
- `*IntegrationTest.java` - 集成测试
- `*E2ETest.java` - 端到端测试
- `FlywayMigrationStartupTest.java` - 数据库迁移启动测试

**执行命令**:
```bash
mvnd verify -P enterprise -DskipTests=false -Djacoco.skip=false
```

**预期输出**:
- 各模块 `target/failsafe-reports/` - Failsafe 测试报告
- 更新的 JaCoCo 覆盖率数据

**收集报告**:
```bash
# 复制 Failsafe 测试报告到统一目录
find . -path "*/target/failsafe-reports/*" -name "*.xml" -exec cp {} target/reports/failsafe/ \;
find . -path "*/target/failsafe-reports/*" -name "*.txt" -exec cp {} target/reports/failsafe/ \;
```

### 2.4 Phase 4: 生成覆盖率报告 (JaCoCo)

**目标**: 生成 HTML 格式的代码覆盖率报告

**执行命令**:
```bash
mvnd jacoco:report -P enterprise
```

**预期输出**:
- 各模块 `target/site/jacoco/` - HTML 覆盖率报告
- `index.html` - 覆盖率总览页面

**覆盖率要求**:
- 行覆盖率 (Line Coverage): ≥ 60%
- 分支覆盖率 (Branch Coverage): ≥ 50%

**收集报告**:
```bash
# 复制 JaCoCo HTML 报告到统一目录
for module in apex-common apex-domain apex-infrastructure apex-application apex-interfaces apex-license-spring-boot-starter; do
  if [ -d "$module/target/site/jacoco" ]; then
    mkdir -p target/reports/jacoco/$module
    cp -r $module/target/site/jacoco/* target/reports/jacoco/$module/
  fi
done
```

### 2.5 Phase 5: 代码质量检查

**目标**: 执行 Spotless 格式检查和 PMD 代码质量检查

**5.1 Spotless 格式检查**:
```bash
mvnd spotless:check > target/reports/quality/spotless-check.log 2>&1
```

**5.2 PMD 代码质量检查**:
```bash
mvnd pmd:check > target/reports/quality/pmd-check.log 2>&1
```

**预期输出**:
- `target/reports/quality/spotless-check.log` - Spotless 检查日志
- `target/reports/quality/pmd-check.log` - PMD 检查日志
- 各模块 `target/pmd.xml` - PMD XML 报告

### 2.6 Phase 6: 架构测试执行 (ArchUnit)

**目标**: 验证项目架构规则,确保分层依赖、租户隔离和响应式编程规范

**架构测试范围**:
- DDD 分层依赖规则(domain 不依赖 application/infrastructure/interfaces)
- 租户隔离规则(数据查询必须带租户过滤)
- 阻塞 I/O 隔离规则(JDBC/MyBatis 使用 BlockingIoSchedulers)
- WebFlux 响应式编程规则(接口返回 Mono/Flux,无 `.block()` 调用)

**执行方式**:
架构测试包含在单元测试中,已在 Phase 2 执行。特定的 ArchUnit 测试类:
- `*ArchitectureTest.java`
- `*LayerDependencyTest.java`
- `*TenantIsolationTest.java`

### 2.7 Phase 7: 生成综合测试报告

**目标**: 汇总所有测试结果,生成 Markdown 和 HTML 格式的综合报告

**7.1 数据收集**

从以下来源收集测试数据:
- Surefire XML 报告 (`target/reports/surefire/*.xml`)
- Failsafe XML 报告 (`target/reports/failsafe/*.xml`)
- JaCoCo HTML 报告 (`target/reports/jacoco/*/index.html`)
- Spotless 检查日志 (`target/reports/quality/spotless-check.log`)
- PMD 检查日志 (`target/reports/quality/pmd-check.log`)

**7.2 报告生成脚本**

创建 `scripts/test/generate-comprehensive-test-report.sh` 脚本:
- 解析 XML 报告,统计测试数量、通过率、失败率
- 提取 JaCoCo 覆盖率数据
- 汇总各模块测试结果
- 生成 Markdown 格式报告
- 使用 Pandoc 或类似工具转换为 HTML(可选)

**7.3 报告模板**

报告包含以下章节:
1. **执行摘要** - 测试概况、环境信息、执行时间
2. **测试统计总览** - 总测试数、通过数、失败数、跳过数、通过率
3. **模块测试矩阵** - 各模块测试详情表格
4. **代码覆盖率分析** - 各模块覆盖率数据、图表、未覆盖代码热点
5. **失败用例详情** - 失败测试的名称、模块、错误信息、堆栈跟踪
6. **代码质量报告** - Spotless 和 PMD 检查结果
7. **架构合规性报告** - ArchUnit 测试结果、违反规则的详情
8. **建议与改进** - 针对失败测试和低覆盖率的改进建议

**输出文件**:
- `target/reports/summary/test-report.md` - Markdown 格式报告
- `target/reports/summary/test-report.html` - HTML 格式报告(可选)
- `target/reports/summary/test-summary.json` - JSON 格式统计数据

## 三、测试环境配置

### 3.1 构建工具

- **Java 版本**: 17.0.12-oracle (由 `.sdkmanrc` 指定)
- **构建工具**: Maven Daemon (mvnd) 2.0.0-rc-3
- **Spring Boot 版本**: 3.5.15

### 3.2 测试框架

- **JUnit 5**: 5.10.2 - 主测试框架
- **Mockito**: 5.11.0 - Mock 框架
- **AssertJ**: 3.25.3 - 流式断言库
- **ArchUnit**: 1.3.0 - 架构测试框架
- **Reactor Test**: Spring WebFlux 响应式测试支持
- **Embedded Redis**: 1.4.3 - 嵌入式 Redis(用于集成测试)

### 3.3 测试插件

- **maven-surefire-plugin**: 3.2.5 - 单元测试执行
- **maven-failsafe-plugin**: 3.2.5 - 集成测试执行
- **jacoco-maven-plugin**: 0.8.12 - 代码覆盖率
- **spotless-maven-plugin**: 代码格式检查
- **maven-pmd-plugin**: 代码质量检查

### 3.4 Profile 配置

使用 `enterprise` Profile 运行测试:
- 包含全部功能模块
- 启用所有集成测试
- 需要完整的外部服务支持(MySQL、Redis、RocketMQ 等)

## 四、预期结果

### 4.1 测试执行预期

根据现有测试文件统计:
- **apex-domain**: ~150+ 单元测试
- **apex-application**: ~50+ 单元测试
- **apex-infrastructure**: ~30+ 集成测试
- **apex-interfaces**: ~20+ 集成测试、E2E 测试
- **apex-common**: ~10+ 单元测试
- **apex-license-spring-boot-starter**: ~5+ 单元测试

**总计预期**: ~265+ 测试用例

### 4.2 覆盖率预期

基于现有 JaCoCo 报告:
- **apex-domain**: 行覆盖率 ~75%,分支覆盖率 ~65%
- **apex-application**: 行覆盖率 ~70%,分支覆盖率 ~60%
- **apex-infrastructure**: 行覆盖率 ~60%,分支覆盖率 ~50%
- **apex-interfaces**: 行覆盖率 ~65%,分支覆盖率 ~55%
- **apex-common**: 行覆盖率 ~80%,分支覆盖率 ~70%

**整体预期**: 行覆盖率 ≥ 65%,分支覆盖率 ≥ 55%

### 4.3 代码质量预期

- **Spotless**: 预期通过(所有代码已格式化)
- **PMD**: 可能存在少量警告(优先级 P3-P5)

## 五、风险与限制

### 5.1 环境限制

- **MySQL 依赖**: 部分集成测试需要 MySQL 连接
  - **缓解措施**: 使用嵌入式 H2 数据库或跳过需要 MySQL 的测试
- **Redis 依赖**: 部分测试需要本地 Redis 实例
  - **缓解措施**: 使用 Embedded Redis
- **RocketMQ 依赖**: 消息队列集成测试需要 RocketMQ
  - **缓解措施**: 使用 Mock 或跳过相关测试

### 5.2 时间预估

- **Phase 1**: 环境准备 - ~2 分钟
- **Phase 2**: 单元测试 - ~5-8 分钟
- **Phase 3**: 集成测试 - ~10-15 分钟
- **Phase 4**: 覆盖率报告 - ~2 分钟
- **Phase 5**: 代码质量检查 - ~3 分钟
- **Phase 6**: 架构测试 - 已包含在 Phase 2
- **Phase 7**: 生成报告 - ~2 分钟

**总计**: ~25-35 分钟

### 5.3 已知问题

根据 `AGENTS.md` 和现有配置:
- `apex-interfaces` 模块排除了 `ApiCallLogIntegrationTest` 和 `OpenApiEndpointsIntegrationTest`
- 部分集成测试在没有外部服务时会被跳过
- Lombok 注解会产生编译警告(预期行为)

## 六、成功标准

### 6.1 测试执行成功标准

- 所有单元测试通过率 ≥ 95%
- 集成测试通过率 ≥ 90% (考虑环境限制)
- 无关键(P1/P2)级别的测试失败
- 架构测试 100% 通过

### 6.2 覆盖率成功标准

- 整体行覆盖率 ≥ 60% (满足 POM 要求)
- 整体分支覆盖率 ≥ 50% (满足 POM 要求)
- 核心业务模块(domain、application)覆盖率 ≥ 70%

### 6.3 代码质量成功标准

- Spotless 检查 100% 通过
- PMD 检查无 P1/P2 级别问题
- 无安全漏洞或敏感信息泄露

## 七、验证与交付

### 7.1 验证步骤

1. 检查测试报告目录结构完整
2. 验证所有模块的测试报告已生成
3. 确认覆盖率报告可以在浏览器中打开
4. 检查综合报告的数据准确性

### 7.2 交付清单

- [ ] 测试执行日志 (`target/reports/summary/execution.log`)
- [ ] Surefire 测试报告 (`target/reports/surefire/`)
- [ ] Failsafe 测试报告 (`target/reports/failsafe/`)
- [ ] JaCoCo 覆盖率报告 (`target/reports/jacoco/`)
- [ ] 代码质量检查报告 (`target/reports/quality/`)
- [ ] 综合测试报告 Markdown (`target/reports/summary/test-report.md`)
- [ ] 综合测试报告 HTML (`target/reports/summary/test-report.html`)
- [ ] 测试统计 JSON (`target/reports/summary/test-summary.json`)

## 八、后续改进建议

### 8.1 测试改进

- 增加更多边界场景测试
- 提高并发测试覆盖率
- 增加性能基准测试
- 完善 E2E 测试场景

### 8.2 报告改进

- 集成 SonarQube 进行持续质量监控
- 生成趋势报告,跟踪覆盖率变化
- 添加测试执行时间分析
- 可视化测试覆盖率热力图

### 8.3 自动化改进

- 将测试报告生成集成到 CI/CD 流水线
- 自动发布测试报告到内部文档站点
- 测试失败时自动通知相关开发人员
- 定期执行测试并生成趋势报告

---

**文档版本**: 1.0  
**创建日期**: 2026-09-15  
**作者**: Apex Team  
**状态**: 待执行
