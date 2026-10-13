# 贡献指南

感谢你对 Apex Scaffold 项目的关注!我们欢迎各种形式的贡献,包括代码、文档、测试用例和问题反馈。

## 📋 目录

- [行为准则](#行为准则)
- [如何贡献](#如何贡献)
- [开发环境搭建](#环境准备)
- [提交PR流程](#提交pr流程)
- [代码规范](#代码规范)
- [测试要求](#测试要求)
- [版本发布](#版本发布)

## 行为准则

本项目采用 [Contributor Covenant](https://www.contributor-covenant.org/) 行为准则。参与即表示你同意遵守该准则。请尊重所有贡献者,保持友好和专业的交流氛围。

## 如何贡献

### 1. 报告Bug

如果你发现了Bug,请:

1. 搜索 [Issues](https://github.com/your-org/apex-scaffold/issues) 确认是否已有人报告
2. 如果没有,创建新的Issue,包含:
   - 清晰的标题
   - 复现步骤
   - 预期行为 vs 实际行为
   - 环境信息(JDK版本、OS等)
   - 相关日志或截图

### 2. 提出新功能

如果你想添加新功能:

1. 先在 [Issues](https://github.com/your-org/apex-scaffold/issues) 中讨论
2. 说明功能的使用场景和价值
3. 等待维护者确认后再开始编码

### 3. 改进文档

文档改进随时欢迎!包括:

- 修正错别字或语法错误
- 补充缺失的说明
- 添加示例代码
- 翻译文档

### 4. 代码贡献

#### 前置条件

- JDK 17+
- Maven Daemon (mvnd) 2.0.0+
- Git
- SDKMAN (推荐)

#### 环境准备

项目使用 SDKMAN 管理 JDK 和构建工具：

```bash
# 1. 激活项目环境（必须）
cd /Users/work/apex-scaffold && sdk env

# 2. 验证环境
java -version    # 应显示 17.0.12
mvnd --version   # 应显示 2.0.0-rc-3

# 3. 如环境未安装，执行：
sdk install java 17.0.12-oracle
sdk install mvnd 2.0.0-rc-3
```

#### 开发流程

```bash
# 1. Fork仓库
# 在GitHub上点击"Fork"按钮

# 2. 克隆你的Fork
git clone https://github.com/your-username/apex-scaffold.git
cd apex-scaffold

# 3. 添加上游远程仓库
git remote add upstream https://github.com/your-org/apex-scaffold.git

# 4. 创建特性分支
git checkout -b feature/your-feature-name

# 5. 开发并测试
mvnd clean test

# 6. 提交更改
git add .
git commit -m "feat: add new feature description"

# 7. 同步上游最新代码
git fetch upstream
git rebase upstream/main

# 8. 推送到你的Fork
git push origin feature/your-feature-name

# 9. 创建Pull Request
# 在GitHub上打开你的Fork,点击"Compare & pull request"
```

## 提交PR流程

### PR标题规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式:

```
<type>: <description>

[optional body]

[optional footer]
```

**Type类型**:
- `feat`: 新功能
- `fix`: Bug修复
- `docs`: 文档更新
- `style`: 代码格式调整(不影响逻辑)
- `refactor`: 重构(不新增功能也不修复Bug)
- `test`: 测试相关
- `chore`: 构建工具、依赖更新等

**示例**:
```
feat: add user profile caching
fix: resolve JWT token expiration issue
docs: update quick start guide
refactor: simplify order service logic
```

### PR描述模板

```markdown
## 变更说明

简要描述本次PR的目的和解决的问题。

## 相关Issue

Closes #123

## 实现细节

- 添加了XXX功能
- 修改了YYY逻辑
- 优化了ZZZ性能

## 测试

- [x] 单元测试已通过
- [x] 集成测试已通过
- [ ] 手动测试截图(如适用)

## 检查清单

- [ ] 代码符合项目规范
- [ ] 已添加必要的注释
- [ ] 已更新相关文档
- [ ] 无破坏性变更(如有需明确说明)
```

## 代码规范

### Java编码规范

遵循 [阿里巴巴Java开发手册](https://github.com/alibaba/p3c):

1. **命名规范**
   - 类名: PascalCase (`UserService`)
   - 方法名: camelCase (`getUserById`)
   - 常量: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
   - 包名: 全小写 (`com.apex.service`)

2. **注释规范**
   - 公共类和方法必须有Javadoc
   - 复杂逻辑需要行内注释说明
   - 避免无意义的注释

3. **异常处理**
   - 不要捕获通用Exception
   - 使用自定义业务异常(BusinessException)
   - 记录异常日志时包含上下文信息

4. **日志规范**
   - 使用SLF4J + Lombok @Slf4j
   - 禁止使用System.out.println
   - 敏感信息必须脱敏

### 提交前检查

```bash
# 1. 代码格式化
mvnd spotless:apply

# 2. 运行测试
mvnd clean test

# 3. 检查代码质量
mvnd pmd:check

# 4. 生成覆盖率报告
mvnd jacoco:report
```

## 测试要求

### 单元测试

- 核心业务逻辑必须有单元测试覆盖
- 测试覆盖率目标: ≥ 70%
- 使用JUnit 5 + Mockito + AssertJ

**示例**:
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void testCreateUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("testuser");
        
        when(userRepository.save(any()))
            .thenReturn(Mono.just(new UserEntity()));
        
        // When
        Mono<UserDTO> result = userService.create(request);
        
        // Then
        StepVerifier.create(result)
            .expectNextMatches(dto -> dto.getUsername().equals("testuser"))
            .verifyComplete();
    }
}
```

### 集成测试

- API接口必须有集成测试
- 使用@SpringBootTest + WebTestClient

## 版本发布

### 版本号规范

遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/):

- `MAJOR.MINOR.PATCH`
- MAJOR: 不兼容的API变更
- MINOR: 向后兼容的功能新增
- PATCH: 向后兼容的Bug修复

**示例**:
- `1.0.0`: 首次正式发布
- `1.1.0`: 新增缓存功能
- `1.1.1`: 修复JWT过期Bug
- `2.0.0`: API重大重构

### 发布流程

1. 更新版本号(`pom.xml`)
2. 更新CHANGELOG.md
3. 打Git Tag(`git tag v1.0.0`)
4. 推送到远程(`git push origin v1.0.0`)
5. 在GitHub Release页面创建Release
6. 上传JAR到Maven Central(可选)

## 📞 联系方式

- 💬 讨论区: [GitHub Discussions](https://github.com/your-org/apex-scaffold/discussions)
- 🐛 问题反馈: [GitHub Issues](https://github.com/your-org/apex-scaffold/issues)
- 📧 邮件: your-email@example.com

## 🙏 致谢

感谢所有为Apex Scaffold做出贡献的开发者!

<a href="https://github.com/your-org/apex-scaffold/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=your-org/apex-scaffold" />
</a>

---

**Happy Coding!** 🚀
