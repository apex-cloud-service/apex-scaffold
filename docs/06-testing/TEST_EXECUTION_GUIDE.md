# 测试执行指南

## 🚀 快速开始

### 环境要求
- Java 17+
- Maven 3.8+
- MySQL 8.0+ (集成测试需要)

### 运行所有测试

```bash
cd /Users/work/apex-scaffold
mvn clean test
```

### 运行特定模块测试

```bash
# 仅运行 Domain 层测试
mvn test -pl apex-domain

# 仅运行 Application 层测试
mvn test -pl apex-application

# 仅运行 Web 层集成测试
mvn test -pl apex-web
```

### 运行特定测试类

```bash
# 运行 DocumentEntityTest
mvn test -Dtest=DocumentEntityTest

# 运行 DocumentServiceImplTest
mvn test -Dtest=DocumentServiceImplTest

# 运行所有集成测试
mvn test -Dtest=*IntegrationTest
```

---

## 📊 生成测试覆盖率报告

### JaCoCo 覆盖率报告

项目已配置 JaCoCo 插件，运行以下命令生成覆盖率报告：

```bash
# 清理、测试并生成覆盖率报告
mvn clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

### 各模块覆盖率报告位置

- **apex-domain**: `apex-domain/target/site/jacoco/index.html`
- **apex-application**: `apex-application/target/site/jacoco/index.html`
- **apex-web**: `apex-web/target/site/jacoco/index.html`

### 聚合覆盖率报告

```bash
# 生成所有模块的聚合报告
mvn clean test jacoco:report-aggregate

# 查看聚合报告
open target/site/jacoco-aggregate/index.html
```

---

## 🧪 测试文件清单

### Entity 层单元测试 (apex-domain/src/test/java)

1. **DocumentEntityTest.java**
   - 测试用例: 11 个
   - 位置: `com.apex.scaffold.domain.model.entity.DocumentEntityTest`
   - 覆盖内容:
     - 文档创建
     - 信息更新
     - 文件附加
     - 状态管理（草稿/发布/归档）
     - 统计（查看次数/下载次数）
     - 枚举验证

2. **ChartEntityTest.java**
   - 测试用例: 10 个
   - 位置: `com.apex.scaffold.domain.model.entity.ChartEntityTest`
   - 覆盖内容:
     - 图表创建
     - 信息更新
     - 状态管理（草稿/发布）
     - 配置管理
     - 查看统计
     - 枚举验证

### Service 层单元测试 (apex-application/src/test/java)

3. **DocumentServiceImplTest.java**
   - 测试用例: 9 个
   - 位置: `com.apex.scaffold.application.service.impl.DocumentServiceImplTest`
   - 使用技术: Mockito + Reactor Test
   - 覆盖内容:
     - CRUD 操作
     - 异常处理
     - 分页查询
     - 状态更新
     - 统计功能

4. **ChartServiceImplTest.java**
   - 测试用例: 8 个
   - 位置: `com.apex.scaffold.application.service.impl.ChartServiceImplTest`
   - 使用技术: Mockito + Reactor Test
   - 覆盖内容:
     - CRUD 操作
     - 异常处理
     - 分页查询
     - 图表数据获取
     - 统计功能

### 集成测试 (apex-web/src/test/java)

5. **DocumentIntegrationTest.java**
   - 测试用例: 2 个
   - 位置: `com.apex.scaffold.web.integration.DocumentIntegrationTest`
   - 使用技术: WebTestClient + Spring Boot Test
   - 覆盖接口:
     - POST /admin-api/v1/data/documents
     - GET /admin-api/v1/data/documents

6. **ChartIntegrationTest.java**
   - 测试用例: 2 个
   - 位置: `com.apex.scaffold.web.integration.ChartIntegrationTest`
   - 使用技术: WebTestClient + Spring Boot Test
   - 覆盖接口:
     - POST /admin-api/v1/data/charts
     - GET /admin-api/v1/data/charts

---

## 🔧 测试配置

### JaCoCo 配置 (已在 pom.xml 中配置)

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 测试配置文件

集成测试需要配置测试数据库，在 `apex-web/src/test/resources/application-test.yml` 中配置：

```yaml
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/apex_test?useSSL=false
    username: root
    password: your_password
```

---

## 📈 预期测试结果

### 测试统计

| 层级 | 测试类数 | 测试用例数 | 预期通过率 |
|------|---------|-----------|-----------|
| Entity 层 | 2 | 21 | 100% |
| Service 层 | 2 | 17 | 100% |
| 集成测试 | 2 | 4 | 100% |
| **总计** | **6** | **42** | **100%** |

### 预期覆盖率

| 模块 | 行覆盖率 | 分支覆盖率 | 方法覆盖率 |
|------|---------|-----------|-----------|
| Domain | 95%+ | 90%+ | 100% |
| Application | 90%+ | 85%+ | 98%+ |
| Web | 80%+ | 75%+ | 90%+ |
| **平均** | **88%+** | **83%+** | **96%+** |

---

## 🐛 常见问题

### 1. 编译错误

**问题**: 找不到依赖类

**解决**: 
```bash
mvn clean install -DskipTests
mvn test
```

### 2. 集成测试失败

**问题**: 数据库连接失败

**解决**:
- 检查 MySQL 是否启动
- 检查 application-test.yml 配置
- 确保测试数据库已创建

### 3. 测试超时

**问题**: 响应式测试超时

**解决**:
- 增加 StepVerifier 超时时间
- 检查响应式链是否正确订阅

---

## 📝 持续集成

### GitHub Actions 配置示例

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Run tests
      run: mvn test
    
    - name: Generate coverage report
      run: mvn jacoco:report
    
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v3
```

---

## 🎯 测试最佳实践

### 1. 单元测试原则
- 每个测试方法只测试一个功能点
- 使用 @DisplayName 提供清晰的测试描述
- 遵循 AAA 模式（Arrange-Act-Assert）

### 2. Mock 使用规范
- 只 Mock 外部依赖（Repository、外部服务）
- 不要 Mock 被测试的对象
- 使用 Mockito.verify() 验证交互

### 3. 响应式测试
- 使用 StepVerifier 测试 Mono/Flux
- 测试异常场景
- 验证完整的响应式链

### 4. 集成测试
- 使用 @SpringBootTest 加载完整上下文
- 使用 WebTestClient 测试 API
- 测试完整的业务流程

---

## 📞 支持

如有测试相关问题，请：
1. 查看测试日志: `target/surefire-reports/`
2. 查看覆盖率报告: `target/site/jacoco/index.html`
3. 联系开发团队

---

**文档维护**: 开发团队  
**最后更新**: 2026-09-12
