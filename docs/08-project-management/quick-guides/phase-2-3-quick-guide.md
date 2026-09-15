# 办公平台集成 - 快速实施指南

本指南帮助开发者快速完成阶段二和阶段三的剩余功能。

---

## 🚀 快速开始

### 1. 查看已完成的示例

组织同步功能是一个完整的参考实现，包含所有层次的代码：

```bash
# 查看领域层
cat apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficeOrganizationService.java

# 查看应用层
cat apex-application/src/main/java/com/apex/scaffold/application/office/OfficeOrganizationSyncService.java

# 查看基础设施层（飞书客户端）
cat apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/client/FeishuOrganizationClient.java

# 查看接口层
cat apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/office/OfficeOrganizationHandler.java
```

### 2. 使用模板快速生成新模块

基于组织同步功能，复制模式实现新模块：

```bash
# 例如：实现消息发送功能
# 1. 复制并重命名文件
cp apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficeOrganizationService.java \
   apex-domain/src/main/java/com/apex/scaffold/domain/office/message/OfficeMessageService.java

# 2. 全局替换关键词
sed -i '' 's/Organization/Message/g' apex-domain/src/main/java/com/apex/scaffold/domain/office/message/OfficeMessageService.java

# 3. 调整业务逻辑
# 手动编辑文件，修改方法签名和业务逻辑
```

---

## 📝 实施步骤模板

### 步骤 1：实现领域层

**目标**：定义业务实体和服务接口

**清单**：
- [ ] 创建聚合根（Entity）
- [ ] 创建值对象（Value Object）
- [ ] 定义领域服务接口
- [ ] 添加完整的 JavaDoc 注释

**示例**（消息模块）：
```java
// apex-domain/src/main/java/com/apex/scaffold/domain/office/message/OfficeMessage.java
/**
 * 办公平台消息聚合根
 *
 * <p>表示一条发送到外部办公平台的消息，包含消息内容、接收人、发送状态等信息。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
public class OfficeMessage {
    private Long id;
    private String tenantId;
    private OfficePlatform platform;
    private MessageType messageType;
    private String content;
    private String senderId;
    private String messageId;
    private String sendStatus;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private Integer deleted;
}
```

### 步骤 2：实现应用层

**目标**：编排业务用例

**清单**：
- [ ] 创建应用服务类
- [ ] 定义 Command 对象
- [ ] 实现业务编排逻辑
- [ ] 添加事务管理
- [ ] 添加完整的 JavaDoc 注释

**示例**（消息模块）：
```java
// apex-application/src/main/java/com/apex/scaffold/application/office/message/OfficeMessageApplicationService.java
/**
 * 办公平台消息应用服务
 *
 * <p>负责消息发送的业务编排，包括消息校验、平台路由、发送执行和状态追踪。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeMessageApplicationService {

    private final OfficeRepository officeRepository;
    private final OfficeMessageService officeMessageService;
    private final TransactionalOperator transactionalOperator;

    /**
     * 发送消息
     *
     * @param tenantId 租户标识
     * @param command  发送命令
     * @return 发送结果
     */
    public Mono<MessageSendResult> sendMessage(String tenantId, SendMessageCommand command) {
        return officeRepository.findApp(tenantId, command.platform())
            .flatMap(app -> officeMessageService.sendMessage(
                tenantId,
                command.platform(),
                command.messageType(),
                command.content(),
                command.recipients()
            ))
            .flatMap(result -> saveMessageRecord(tenantId, command, result)
                .map(record -> MessageSendResult.success(record.getId(), result.messageId()))
            )
            .as(transactionalOperator::transactional)
            .onErrorResume(error -> {
                log.error("发送消息失败，租户：{}", tenantId, error);
                return Mono.error(new BusinessException(
                    ResultCode.BUSINESS_ERROR,
                    "发送消息失败：" + error.getMessage()
                ));
            });
    }
}
```

### 步骤 3：实现基础设施层

**目标**：实现技术细节和外部集成

**清单**：
- [ ] 定义平台客户端 SPI
- [ ] 实现各平台客户端（飞书、钉钉、企业微信）
- [ ] 创建 PO、Mapper、Converter
- [ ] 实现 Repository
- [ ] 添加完整的 JavaDoc 注释

**示例**（飞书消息客户端）：
```java
// apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/client/FeishuMessageClient.java
/**
 * 飞书消息客户端
 *
 * <p>实现飞书平台的消息发送功能，包括文本消息、Markdown 消息和卡片消息。
 *
 * <p>API 文档：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/im-v1/message/create
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
public class FeishuMessageClient implements OfficePlatformMessageClient {

    private final WebClient webClient;

    @Override
    public OfficePlatform platform() {
        return OfficePlatform.FEISHU;
    }

    @Override
    public Mono<MessageSendResult> sendMessage(
        String appId,
        String secret,
        MessageType messageType,
        String content,
        List<String> recipients
    ) {
        return getTenantAccessToken(appId, secret)
            .flatMap(token -> webClient.post()
                .uri("/open-apis/im/v1/messages")
                .header("Authorization", "Bearer " + token)
                .bodyValue(buildMessageBody(messageType, content, recipients))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseResponse)
            );
    }
}
```

### 步骤 4：实现接口层

**目标**：提供 HTTP API

**清单**：
- [ ] 创建 Request/Response DTO
- [ ] 实现 Handler 类
- [ ] 配置 Router
- [ ] 添加完整的 SpringDoc 注解
- [ ] 添加完整的 JavaDoc 注释

**示例**（消息 Handler）：
```java
// apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/office/OfficeMessageHandler.java
/**
 * 办公平台消息处理器
 *
 * <p>提供消息发送相关的 HTTP 接口处理。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台消息", description = "办公平台消息发送接口")
public class OfficeMessageHandler {

    private final OfficeMessageApplicationService messageApplicationService;

    /**
     * 发送消息
     *
     * @param request HTTP 请求
     * @return 发送结果
     */
    @Operation(
        summary = "发送消息",
        description = "发送文本、Markdown或卡片消息到外部办公平台",
        tags = {"办公平台消息"}
    )
    public Mono<ServerResponse> sendMessage(ServerRequest request) {
        return RequestHandlerUtil.validateAndMap(request, SendMessageRequest.class)
            .flatMap(req -> {
                String tenantId = RequestHandlerUtil.getTenantId(request);
                return messageApplicationService.sendMessage(tenantId, toCommand(req))
                    .flatMap(result -> ServerResponse.ok()
                        .bodyValue(Result.success(toResponse(result))));
            })
            .onErrorResume(RequestHandlerUtil::handleError);
    }
}
```

### 步骤 5：编写数据库迁移脚本

**清单**：
- [ ] 设计表结构
- [ ] 编写 MySQL 迁移脚本
- [ ] 编写 PostgreSQL 迁移脚本
- [ ] 确保脚本幂等

**示例**（消息表）：
```sql
-- apex-interfaces/src/main/resources/db/migration/mysql/V29__add_office_message_tables.sql

-- 消息记录表
CREATE TABLE IF NOT EXISTS office_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
  platform VARCHAR(20) NOT NULL COMMENT '平台类型',
  message_type VARCHAR(20) NOT NULL COMMENT '消息类型',
  content TEXT COMMENT '消息内容',
  sender_id VARCHAR(128) COMMENT '发送人ID',
  message_id VARCHAR(128) COMMENT '外部消息ID',
  send_status VARCHAR(20) COMMENT '发送状态',
  error_message TEXT COMMENT '错误信息',
  created_at DATETIME COMMENT '创建时间',
  updated_at DATETIME COMMENT '更新时间',
  version INT DEFAULT 0 COMMENT '版本号',
  deleted TINYINT DEFAULT 0 COMMENT '删除标记',
  INDEX idx_tenant_platform (tenant_id, platform),
  INDEX idx_sender (sender_id),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='办公平台消息记录表';

-- 消息接收人表
CREATE TABLE IF NOT EXISTS office_message_recipient (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  message_id BIGINT NOT NULL COMMENT '消息ID',
  recipient_type VARCHAR(20) NOT NULL COMMENT '接收人类型',
  recipient_id VARCHAR(128) NOT NULL COMMENT '接收人ID',
  read_status TINYINT DEFAULT 0 COMMENT '已读状态',
  read_at DATETIME COMMENT '已读时间',
  created_at DATETIME COMMENT '创建时间',
  INDEX idx_message (message_id),
  INDEX idx_recipient (recipient_id),
  FOREIGN KEY (message_id) REFERENCES office_message(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='办公平台消息接收人表';
```

### 步骤 6：编写测试代码

**清单**：
- [ ] 编写单元测试
- [ ] 编写集成测试
- [ ] Mock 外部依赖
- [ ] 覆盖正常和异常场景

**示例**（应用服务测试）：
```java
// apex-application/src/test/java/com/apex/scaffold/application/office/message/OfficeMessageApplicationServiceTest.java
/**
 * 办公平台消息应用服务测试
 *
 * @author Apex Team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
class OfficeMessageApplicationServiceTest {

    @Mock
    private OfficeRepository officeRepository;

    @Mock
    private OfficeMessageService officeMessageService;

    @Mock
    private TransactionalOperator transactionalOperator;

    @InjectMocks
    private OfficeMessageApplicationService service;

    @Test
    @DisplayName("发送消息成功")
    void sendMessage_success() {
        // Given
        String tenantId = "tenant-001";
        SendMessageCommand command = SendMessageCommand.builder()
            .platform(OfficePlatform.FEISHU)
            .messageType(MessageType.TEXT)
            .content("测试消息")
            .recipients(List.of("user-001"))
            .build();

        // Mock
        when(officeRepository.findApp(tenantId, OfficePlatform.FEISHU))
            .thenReturn(Mono.just(mockApp()));
        when(officeMessageService.sendMessage(any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(mockResult()));

        // When
        StepVerifier.create(service.sendMessage(tenantId, command))
            // Then
            .assertNext(result -> {
                assertNotNull(result);
                assertTrue(result.success());
            })
            .verifyComplete();
    }
}
```

---

## 🛠️ 开发工具和命令

### 代码格式化
```bash
# 检查格式
mvnd spotless:check

# 自动格式化
mvnd spotless:apply
```

### 代码质量检查
```bash
# PMD 检查
mvnd pmd:check

# 运行测试
mvnd test -P basic
```

### 构建项目
```bash
# 编译
mvnd clean compile -DskipTests

# 打包
mvnd clean package -DskipTests
```

### 启动项目
```bash
# 启动应用
mvnd -pl apex-interfaces spring-boot:run -P dev

# 访问 Swagger
open http://localhost:8080/swagger-ui.html
```

---

## 📚 参考资源

### 外部平台 API 文档

**飞书开放平台**
- 通讯录：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/department/list
- 消息：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/im-v1/message/create
- 审批：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/approval-v4/instance/list
- 日历：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/calendar-v4/calendar-event/create
- 文档：https://open.feishu.cn/document/ukTMukTMukTM/uUDN04SN0QjL1QDN/document-docx/docx-overview

**钉钉开放平台**
- 通讯录：https://open.dingtalk.com/document/orgapp/obtain-the-department-list-v2
- 消息：https://open.dingtalk.com/document/orgapp/asynchronous-sending-of-enterprise-session-messages
- 审批：https://open.dingtalk.com/document/orgapp/obtain-approval-data
- 日历：https://open.dingtalk.com/document/orgapp/create-an-event
- 文档：https://open.dingtalk.com/document/orgapp/create-a-new-document

**企业微信开放平台**
- 通讯录：https://developer.work.weixin.qq.com/document/path/90208
- 消息：https://developer.work.weixin.qq.com/document/path/90236
- 审批：https://developer.work.weixin.qq.com/document/path/91510
- 日历：https://developer.work.weixin.qq.com/document/path/93647
- 文档：https://developer.work.weixin.qq.com/document/path/97459

### 内部文档
- 实施计划：`.trae/documents/phase-2-3-implementation-plan.md`
- 进度报告：`.trae/documents/phase-2-progress-report.md`
- 实施总结：`.trae/documents/phase-2-3-implementation-summary.md`
- 编码规范：`AGENTS.md`

---

## ⚠️ 注意事项

### 必须遵守的规范
1. ✅ 所有类和方法添加完整的 JavaDoc 注释（中文）
2. ✅ 接口层添加完整的 SpringDoc 注解
3. ✅ 使用 MapStruct 进行对象转换
4. ✅ 使用 Lombok 注解，禁止手写 getter/setter
5. ✅ 使用构造器注入，禁止字段注入
6. ✅ 响应式编程，使用 Mono/Flux
7. ✅ 阻塞 I/O 必须使用 BlockingIoSchedulers 隔离
8. ✅ 所有查询必须包含租户 ID
9. ✅ 使用 `mvnd` 而非 `mvn`
10. ✅ 提交前运行 `mvnd spotless:check` 和 `mvnd test`

### 常见错误
- ❌ 忘记添加 JavaDoc 注释
- ❌ 忘记添加 SpringDoc 注解
- ❌ 手写对象转换代码
- ❌ 使用字段注入（@Autowired）
- ❌ 在响应式链中使用 .block()
- ❌ 查询时忘记租户隔离
- ❌ 使用 mvn 命令

---

## 🎯 每日检查清单

### 开发前
- [ ] 拉取最新代码：`git pull`
- [ ] 加载 SDK 环境：`cd /Users/work/apex-scaffold && sdk env`
- [ ] 查看任务列表

### 开发中
- [ ] 参考组织同步示例代码
- [ ] 遵循 DDD 分层结构
- [ ] 添加完整的注释和文档

### 开发后
- [ ] 运行格式化：`mvnd spotless:apply`
- [ ] 运行测试：`mvnd test -P basic`
- [ ] 检查 JavaDoc：`scripts/scan-javadoc-issues.sh`
- [ ] 提交代码（使用 Conventional Commits 格式）

---

## 📞 获取帮助

遇到问题时：
1. 查看组织同步功能的实现代码
2. 查看实施计划文档
3. 查看外部平台 API 文档
4. 查看 AGENTS.md 编码规范

---

**版本**: 1.0  
**更新时间**: 2026-09-14  
**适用范围**: 阶段二和阶段三所有剩余功能开发
