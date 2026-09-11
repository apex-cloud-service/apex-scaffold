# Apex Scaffold 业务流程文档

> 文档版本: v1.0
> 创建时间: 2026-09-03
> 适用版本: apex-scaffold v2.5.0+

> **配套流程图**:
> - [JWT 登录认证流程图](../diagrams/04_login_authentication_flow.svg)
> - [RBAC 权限校验流程图](../diagrams/05_permission_check_flow.svg)
> - [三级审批工作流图](../diagrams/06_workflow_approval_flow.svg)
> - [文件分片上传流程图](../diagrams/07_file_upload_flow.svg)
> - [数据流转架构图](../diagrams/62_data_flow_consistency.svg)
> - [RocketMQ消息队列架构图](../diagrams/61_message_queue_reliability.svg)

---

## 一、文档概述

本文档详细描述 Apex Scaffold 系统的核心业务流程,包括:
- 用户登录认证流程 (JWT Token)
- 权限校验流程 (RBAC + 数据权限)
- 工作流审批流程 (BPMN引擎)
- 文件上传流程 (多存储策略)

每个流程包含**流程图**和**详细步骤说明**,便于开发人员理解业务逻辑和系统实现。

---

## 二、用户登录认证流程

### 2.1 流程概述

**目标**: 验证用户身份,签发JWT Token,建立会话

**涉及组件**:
- 前端: Vue 3 + Axios
- 后端: Spring WebFlux + JWT
- 数据库: MySQL (sys_user表)
- 缓存: Redis (Token存储)

**认证方式**:
1. 账号密码登录 (username + password)
2. 微信登录 (OpenID + UnionID)
3. 手机号登录 (phone + 验证码)

### 2.2 账号密码登录流程

#### 2.2.1 前端流程

**Step 1**: 用户输入
- 用户在登录页面输入`username`和`password`
- 前端进行基础校验(非空、长度)

**Step 2**: 提交登录请求
```javascript
POST /admin-api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

**Step 3**: 接收响应
- 成功: 200 OK,返回`{accessToken, refreshToken, userInfo}`
- 失败: 401 Unauthorized,显示错误提示

**Step 4**: 存储Token
```javascript
localStorage.setItem('accessToken', response.data.accessToken)
localStorage.setItem('refreshToken', response.data.refreshToken)
```

#### 2.2.2 后端流程

**Step 1**: 参数验证
- 校验username/password非空
- 校验用户名格式(4-20字符,字母数字下划线)
- 校验密码格式(6-20字符)

**Step 2**: 查询用户
```java
Mono<User> user = userRepository.findByUsername(username);
```

**Step 3**: 用户存在性检查
- 用户不存在 → 返回`401 用户不存在`
- 用户状态禁用 → 返回`401 账号已禁用`

**Step 4**: 密码验证
```java
boolean matches = BCrypt.checkpw(password, user.getPassword());
```

- 密码错误 → 返回`401 密码错误`
- 密码正确 → 继续

**Step 5**: 生成JWT Token
```java
String accessToken = jwtUtil.generateAccessToken(userId);
String refreshToken = jwtUtil.generateRefreshToken(userId);
```

**Token结构**:
- **AccessToken**: 有效期2小时,用于API请求认证
- **RefreshToken**: 有效期7天,用于刷新AccessToken

**JWT Payload**:
```json
{
  "sub": "10001",           // 用户ID
  "username": "admin",      // 用户名
  "roles": ["ADMIN"],       // 角色列表
  "iat": 1693728000,        // 签发时间
  "exp": 1693735200         // 过期时间
}
```

**Step 6**: 缓存Token到Redis
```java
redisTemplate.opsForValue().set(
  "token:" + userId,
  accessToken,
  2, TimeUnit.HOURS
);
```

**Step 7**: 记录登录日志
```java
LoginLog log = LoginLog.builder()
  .userId(userId)
  .username(username)
  .ip(request.getRemoteAddr())
  .status(1) // 成功
  .loginTime(LocalDateTime.now())
  .build();
loginLogRepository.save(log);
```

**Step 8**: 返回响应
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "userInfo": {
      "userId": 10001,
      "username": "admin",
      "nickName": "管理员",
      "avatarUrl": "https://...",
      "roles": ["ADMIN"]
    }
  }
}
```

### 2.3 Token刷新流程

**场景**: AccessToken过期,使用RefreshToken刷新

**请求**:
```javascript
POST /admin-api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**后端处理**:
1. 验证RefreshToken有效性
2. 提取userId
3. 生成新的AccessToken
4. 更新Redis缓存
5. 返回新Token

### 2.4 登出流程

**请求**:
```javascript
POST /admin-api/v1/auth/logout
Authorization: Bearer {accessToken}
```

**后端处理**:
1. 解析Token获取userId
2. 删除Redis中的Token缓存
3. 记录登出日志
4. 返回成功

---

## 三、权限校验流程

### 3.1 流程概述

**权限模型**: RBAC (Role-Based Access Control) + 数据权限

**权限层级**:
1. **接口权限** (API Permission): 控制用户能否访问某个API
2. **按钮权限** (Button Permission): 控制前端按钮显示
3. **数据权限** (Data Scope): 控制用户能查看哪些数据

### 3.2 RBAC权限校验流程

#### 3.2.1 请求拦截

**Step 1**: 接收API请求
```
GET /admin-api/v1/users?deptId=1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**Step 2**: JWT Token解析
```java
String token = request.getHeaders().getFirst("Authorization")
  .replace("Bearer ", "");
Claims claims = jwtUtil.parseToken(token);
Long userId = claims.get("sub", Long.class);
```

**Step 3**: Token有效性检查
- Token格式错误 → `401 Token格式错误`
- Token已过期 → `401 Token已过期`
- Redis中Token不存在 → `401 Token已失效`

#### 3.2.2 角色权限查询

**Step 4**: 查询用户角色
```sql
SELECT r.* FROM sys_role r
INNER JOIN sys_user_role ur ON r.id = ur.role_id
WHERE ur.user_id = ? AND r.deleted = 0
```

**Step 5**: 查询角色权限
```sql
SELECT p.* FROM sys_permission p
INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
WHERE rp.role_id IN (?) AND p.deleted = 0
```

**权限缓存策略**:
```java
// 缓存用户权限列表 (1小时)
@Cacheable(value = "user:permissions", key = "#userId")
public List<Permission> getUserPermissions(Long userId) {
  // 查询逻辑
}
```

#### 3.2.3 权限匹配

**Step 6**: API路径匹配
```java
String requestPath = "/admin-api/v1/users";
String requestMethod = "GET";

boolean hasPermission = permissions.stream()
  .filter(p -> p.getResourceType().equals("API"))
  .anyMatch(p ->
    PathMatcher.match(p.getApiPath(), requestPath) &&
    p.getApiMethod().equalsIgnoreCase(requestMethod)
  );
```

**匹配规则**:
- 精确匹配: `/admin-api/v1/users` == `/admin-api/v1/users`
- 通配符匹配: `/admin-api/v1/users/*` 匹配 `/admin-api/v1/users/123`
- 方法匹配: GET、POST、PUT、DELETE

**Step 7**: 权限判断
- 有权限 → 继续执行
- 无权限 → `403 Forbidden 无操作权限`

### 3.3 数据权限过滤流程

#### 3.3.1 数据范围定义

**数据权限范围枚举**:

| dataScope | 说明 | SQL条件 |
|-----------|------|---------|
| `1` | 全部数据权限 | 无限制 |
| `2` | 自定义数据权限 | `dept_id IN (指定部门)` |
| `3` | 本部门数据权限 | `dept_id = 用户部门` |
| `4` | 本部门及以下数据权限 | `dept_id IN (部门树)` |
| `5` | 仅本人数据权限 | `create_user = 用户ID` |

#### 3.3.2 数据权限拦截器

**MyBatis-Plus拦截器实现**:

```java
@Component
public class DataScopeInterceptor implements InnerInterceptor {

  @Override
  public void beforeQuery(Executor executor,
                          MappedStatement ms,
                          Object parameter,
                          RowBounds rowBounds,
                          ResultHandler resultHandler,
                          BoundSql boundSql) {

    // 1. 获取当前用户
    Long userId = SecurityContextHolder.getUserId();

    // 2. 查询用户角色的数据范围
    Integer dataScope = roleService.getUserDataScope(userId);

    // 3. 根据数据范围拼接SQL
    String sqlCondition = buildSqlCondition(userId, dataScope);

    // 4. 动态添加WHERE条件
    String originalSql = boundSql.getSql();
    String newSql = injectDataScope(originalSql, sqlCondition);

    // 5. 替换SQL
    ReflectionUtil.setFieldValue(boundSql, "sql", newSql);
  }
}
```

**SQL改写示例**:

**原始SQL**:
```sql
SELECT * FROM sys_user WHERE status = 1
```

**数据权限改写后** (本部门及以下):
```sql
SELECT * FROM sys_user
WHERE status = 1
  AND dept_id IN (1, 2, 3, 5, 8)  -- 部门树
```

#### 3.3.3 租户隔离

**多租户自动注入**:

```java
@Component
public class TenantInterceptor implements InnerInterceptor {

  @Override
  public void beforeQuery(...) {
    String tenantId = TenantContext.getTenantId();

    // 自动注入 tenant_id 条件
    String condition = " AND tenant_id = '" + tenantId + "'";

    // SQL改写
    String newSql = originalSql + condition;
  }
}
```

**租户上下文管理**:
```java
public class TenantContext {
  private static final ThreadLocal<String> TENANT_ID =
    new ThreadLocal<>();

  public static void setTenantId(String tenantId) {
    TENANT_ID.set(tenantId);
  }

  public static String getTenantId() {
    return TENANT_ID.get();
  }

  public static void clear() {
    TENANT_ID.remove();
  }
}
```

---

## 四、工作流审批流程

### 4.1 流程概述

**工作流引擎**: 基于BPMN 2.0规范的轻量级工作流引擎

**核心表**:
- `wf_definition`: 流程定义表
- `wf_instance`: 流程实例表
- `wf_task`: 任务表
- `wf_approval_record`: 审批记录表

### 4.2 流程发起

**Step 1**: 用户发起流程
```javascript
POST /admin-api/v1/workflow/instances
Content-Type: application/json

{
  "definitionId": 1001,
  "businessKey": "LEAVE-2026-001",
  "variables": {
    "days": 3,
    "reason": "事假",
    "startDate": "2026-09-05"
  }
}
```

**Step 2**: 创建流程实例
```java
ProcessInstance instance = ProcessInstance.builder()
  .definitionId(definitionId)
  .businessKey(businessKey)
  .initiatorId(userId)
  .status("RUNNING")
  .startTime(LocalDateTime.now())
  .tenantId(tenantId)
  .build();
processInstanceRepository.save(instance);
```

**Step 3**: 解析BPMN获取下一节点
```java
WorkflowDefinition definition =
  definitionRepository.findById(definitionId);
String bpmnXml = definition.getBpmnXml();

// 解析BPMN,获取Start节点的下一节点
BpmnModel model = BpmnParser.parse(bpmnXml);
FlowNode nextNode = model.getNextNode("start");
```

**Step 4**: 创建第一个任务
```java
Task task = Task.builder()
  .instanceId(instanceId)
  .taskName(nextNode.getName())
  .taskKey(nextNode.getKey())
  .assigneeId(nextNode.getAssignee())
  .status("PENDING")
  .build();
taskRepository.save(task);
```

### 4.3 任务审批

**Step 1**: 审批人查看待办任务
```javascript
GET /admin-api/v1/workflow/tasks/pending
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "total": 5,
    "records": [
      {
        "taskId": 10001,
        "taskName": "部门主管审批",
        "instanceId": 5001,
        "businessKey": "LEAVE-2026-001",
        "initiatorName": "张三",
        "createTime": "2026-09-03 10:00:00"
      }
    ]
  }
}
```

**Step 2**: 签收任务
```javascript
POST /admin-api/v1/workflow/tasks/{taskId}/claim
```

**后端处理**:
```java
task.setStatus("CLAIMED");
task.setClaimTime(LocalDateTime.now());
taskRepository.save(task);
```

**Step 3**: 提交审批
```javascript
POST /admin-api/v1/workflow/tasks/{taskId}/complete
Content-Type: application/json

{
  "approved": true,  // true=通过, false=驳回
  "comment": "同意请假"
}
```

**Step 4**: 完成任务
```java
task.setStatus("COMPLETED");
task.setCompleteTime(LocalDateTime.now());
taskRepository.save(task);

// 记录审批记录
ApprovalRecord record = ApprovalRecord.builder()
  .taskId(taskId)
  .approverId(userId)
  .approved(approved)
  .comment(comment)
  .approveTime(LocalDateTime.now())
  .build();
approvalRecordRepository.save(record);
```

**Step 5**: 流程流转

**通过**: 创建下一个任务
```java
FlowNode nextNode = model.getNextNode(currentNode.getKey());
if (nextNode != null) {
  createTask(nextNode);
} else {
  // 流程结束
  instance.setStatus("COMPLETED");
  instance.setEndTime(LocalDateTime.now());
}
```

**驳回**: 根据驳回策略处理
- 驳回到发起人: 流程终止
- 驳回到上一节点: 重新创建上一个任务
- 驳回重新提交: 回到第一个任务节点

### 4.4 流程状态机

**流程实例状态**:
- `RUNNING`: 运行中
- `SUSPENDED`: 已挂起
- `COMPLETED`: 已完成
- `TERMINATED`: 已终止

**任务状态**:
- `PENDING`: 待办
- `CLAIMED`: 已签收
- `COMPLETED`: 已完成
- `REJECTED`: 已驳回

**状态转换规则**:
```
PENDING → CLAIMED (签收)
CLAIMED → COMPLETED (通过)
CLAIMED → REJECTED (驳回)
PENDING → TERMINATED (流程终止)
```

---

## 五、文件上传流程

### 5.1 流程概述

**支持的存储策略**:
1. **本地存储**: 文件存储在应用服务器本地磁盘
2. **MinIO**: 私有化对象存储,S3兼容API
3. **火山云TOS**: 公有云对象存储,CDN全球加速

**存储策略配置**:
```yaml
file:
  storage:
    type: minio  # local / minio / tos
    max-size: 10MB
    allowed-types:
      - image/jpeg
      - image/png
      - application/pdf
```

### 5.2 前端上传流程

**Step 1**: 用户选择文件
```javascript
<input type="file" @change="handleFileChange" />
```

**Step 2**: 前端预检
```javascript
function handleFileChange(event) {
  const file = event.target.files[0];

  // 大小检查
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过10MB');
    return;
  }

  // 类型检查
  const allowedTypes = ['image/jpeg', 'image/png', 'application/pdf'];
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('不支持的文件类型');
    return;
  }

  uploadFile(file);
}
```

**Step 3**: 上传文件
```javascript
function uploadFile(file) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('bizType', 'avatar'); // 业务类型

  axios.post('/admin-api/v1/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (progressEvent) => {
      const percent = Math.round(
        (progressEvent.loaded * 100) / progressEvent.total
      );
      console.log(`上传进度: ${percent}%`);
    }
  }).then(response => {
    const { fileId, accessUrl } = response.data;
    ElMessage.success('上传成功');
  });
}
```

### 5.3 后端处理流程

**Step 1**: 接收文件
```java
@PostMapping("/upload")
public Mono<Result<FileUploadResponse>> upload(
  @RequestPart("file") FilePart filePart,
  @RequestParam("bizType") String bizType
) {
  return fileService.upload(filePart, bizType);
}
```

**Step 2**: 后端校验
```java
// 文件大小校验
if (filePart.headers().getContentLength() > maxSize) {
  throw new BusinessException("文件大小超过限制");
}

// 文件类型校验
String contentType = filePart.headers().getContentType().toString();
if (!allowedTypes.contains(contentType)) {
  throw new BusinessException("不支持的文件类型");
}

// 病毒扫描 (可选)
if (virusScanEnabled) {
  virusScanner.scan(filePart);
}
```

**Step 3**: 生成文件名
```java
String originalFilename = filePart.filename();
String extension = FilenameUtils.getExtension(originalFilename);
String newFilename = UUID.randomUUID().toString() + "." + extension;
```

**Step 4**: 根据策略存储文件

#### 策略1: 本地存储
```java
String basePath = "/upload/files/";
String datePath = LocalDate.now().format(
  DateTimeFormatter.ofPattern("yyyy/MM/dd")
);
String fullPath = basePath + datePath + "/" + newFilename;

File targetFile = new File(fullPath);
targetFile.getParentFile().mkdirs();

filePart.transferTo(targetFile).block();
```

#### 策略2: MinIO
```java
MinioClient minioClient = MinioClient.builder()
  .endpoint(minioConfig.getEndpoint())
  .credentials(minioConfig.getAccessKey(),
               minioConfig.getSecretKey())
  .build();

String bucketName = "apex-files";
String objectName = datePath + "/" + newFilename;

minioClient.putObject(
  PutObjectArgs.builder()
    .bucket(bucketName)
    .object(objectName)
    .stream(filePart.content(), -1, 10485760)
    .contentType(contentType)
    .build()
);
```

#### 策略3: 火山云TOS
```java
TOSClient tosClient = new TOSClient(
  tosConfig.getRegion(),
  tosConfig.getAccessKeyId(),
  tosConfig.getAccessKeySecret()
);

String bucketName = "apex-files";
String objectKey = datePath + "/" + newFilename;

PutObjectRequest request = new PutObjectRequest(bucketName, objectKey)
  .withContent(filePart.content())
  .withContentType(contentType);

tosClient.putObject(request);
```

**Step 5**: 保存文件元数据
```java
FileEntity fileEntity = FileEntity.builder()
  .fileName(newFilename)
  .originalName(originalFilename)
  .filePath(fullPath)
  .fileSize(filePart.headers().getContentLength())
  .contentType(contentType)
  .md5(calculateMD5(filePart))
  .storageType(storageType)
  .bizType(bizType)
  .uploadUserId(SecurityContextHolder.getUserId())
  .tenantId(TenantContext.getTenantId())
  .build();

fileRepository.save(fileEntity);
```

**Step 6**: 返回访问URL
```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "fileId": 10001,
    "fileName": "abc123.jpg",
    "originalName": "头像.jpg",
    "fileSize": 102400,
    "accessUrl": "https://cdn.example.com/files/2026/09/03/abc123.jpg"
  }
}
```

### 5.4 文件访问控制

**公开文件**: 直接访问URL
```
GET https://cdn.example.com/files/2026/09/03/abc123.jpg
```

**私有文件**: 需要Token认证
```
GET /admin-api/v1/files/{fileId}/download
Authorization: Bearer {accessToken}
```

**后端鉴权**:
```java
@GetMapping("/{fileId}/download")
public Mono<ResponseEntity<Resource>> download(@PathVariable Long fileId) {
  // 1. 查询文件元数据
  FileEntity file = fileRepository.findById(fileId).block();

  // 2. 权限校验
  Long currentUserId = SecurityContextHolder.getUserId();
  if (!hasPermission(currentUserId, file)) {
    throw new BusinessException("无权限访问该文件");
  }

  // 3. 返回文件流
  Resource resource = loadFileResource(file);
  return Mono.just(ResponseEntity.ok()
    .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file.getOriginalName() + "\"")
    .body(resource));
}
```

---

## 六、流程优化建议

### 6.1 登录流程优化

**优化点1**: 验证码防暴力破解
```java
// 登录失败3次后要求验证码
if (loginFailCount >= 3) {
  requireCaptcha = true;
}
```

**优化点2**: 短信验证码登录
```java
POST /admin-api/v1/auth/login-by-sms
{
  "phone": "13800138000",
  "code": "123456"
}
```

**优化点3**: 单点登录(SSO)
```java
// OAuth2授权码模式
GET /oauth/authorize
  ?client_id=xxx
  &redirect_uri=xxx
  &response_type=code
```

### 6.2 权限校验优化

**优化点1**: 权限缓存
```java
// 缓存用户权限1小时,减少数据库查询
@Cacheable(value = "user:permissions", key = "#userId", ttl = 3600)
```

**优化点2**: 权限预加载
```java
// 登录时一次性加载所有权限到Redis
redisTemplate.opsForSet().add("user:permissions:" + userId, permissions);
```

**优化点3**: 权限变更实时失效
```java
// 角色权限修改后,清除相关用户缓存
@CacheEvict(value = "user:permissions", allEntries = true)
public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
  // 更新逻辑
}
```

### 6.3 工作流优化

**优化点1**: 并行审批
```java
// BPMN并行网关,多人同时审批
<parallelGateway id="parallelGateway1" />
```

**优化点2**: 审批超时提醒
```java
// 定时任务扫描超时任务
@Scheduled(cron = "0 */30 * * * ?")
public void checkTimeout() {
  List<Task> timeoutTasks = taskRepository
    .findByStatusAndCreateTimeBefore("PENDING",
                                     LocalDateTime.now().minusDays(2));
  // 发送提醒
}
```

**优化点3**: 审批委托
```java
// 审批人设置委托人代为审批
POST /admin-api/v1/workflow/tasks/{taskId}/delegate
{
  "delegateUserId": 10002
}
```

### 6.4 文件上传优化

**优化点1**: 分片上传(大文件)
```javascript
// 前端分片上传
function uploadChunks(file) {
  const chunkSize = 2 * 1024 * 1024; // 2MB
  const chunks = Math.ceil(file.size / chunkSize);

  for (let i = 0; i < chunks; i++) {
    const chunk = file.slice(i * chunkSize, (i + 1) * chunkSize);
    uploadChunk(chunk, i, chunks);
  }
}
```

**优化点2**: 断点续传
```java
// 记录已上传分片
redisTemplate.opsForSet().add("upload:" + uploadId, chunkIndex);
```

**优化点3**: 文件秒传(MD5去重)
```java
// 上传前计算MD5,检查是否已存在
String md5 = calculateMD5(file);
FileEntity existFile = fileRepository.findByMd5(md5);
if (existFile != null) {
  // 秒传,直接返回已有文件
  return existFile;
}
```

---

## 七、附录

### 7.1 相关文档

- [系统架构设计文档](/.trae/documents/系统架构设计文档.md)
- [数据库设计文档](/.trae/documents/数据库设计文档.md)
- [配置文件完整性检查报告](/.trae/documents/配置文件完整性检查报告.md)

### 7.2 状态码说明

| 状态码 | 说明 | 适用场景 |
|--------|------|---------|
| 200 | 成功 | 请求成功 |
| 400 | 参数错误 | 请求参数不合法 |
| 401 | 未认证 | Token无效或已过期 |
| 403 | 无权限 | 无操作权限 |
| 404 | 不存在 | 资源不存在 |
| 500 | 服务器错误 | 服务器内部错误 |

### 7.3 业务错误码

| 错误码 | 说明 | 场景 |
|--------|------|------|
| `1001` | 用户不存在 | 登录时用户名不存在 |
| `1002` | 密码错误 | 登录时密码验证失败 |
| `1003` | 账号已禁用 | 用户状态为禁用 |
| `2001` | 无操作权限 | RBAC权限校验失败 |
| `2002` | 无数据权限 | 数据范围权限校验失败 |
| `3001` | 流程不存在 | 工作流定义不存在 |
| `3002` | 任务不存在 | 工作流任务不存在 |
| `4001` | 文件类型不支持 | 文件上传类型校验失败 |
| `4002` | 文件大小超限 | 文件上传大小校验失败 |

---

**文档维护者**: 架构团队
**审核状态**: 待人工审核
**下次更新**: 业务流程变更后同步更新
