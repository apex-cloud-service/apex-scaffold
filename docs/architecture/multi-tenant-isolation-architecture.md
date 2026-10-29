# 多租户数据隔离架构文档

**文档版本**: v1.0  
**创建日期**: 2026-09-12  
**维护团队**: Apex 架构团队

---

## 📋 概述

Apex Scaffold 多租户系统采用**共享数据库、共享Schema、租户字段隔离**模式,通过在数据表中增加`tenant_id`字段实现租户间数据隔离。本文档详细说明多租户架构的设计原理、实现方式和最佳实践。

---

## 🏗️ 架构设计

### 多租户数据隔离架构图

下图展示了从请求入口到数据库层的完整租户隔离机制:

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'primaryColor':'#4f46e5'}}}%%
graph TB
    subgraph Request["请求入口"]
        Client[客户端请求]
        Header[X-Tenant-Id Header]
    end
    
    subgraph Filter["过滤器层"]
        TenantFilter[TenantWebFilter<br/>优先级: -100]
        TenantContext[TenantContext<br/>ThreadLocal存储]
    end
    
    subgraph Service["应用服务层"]
        UserService[UserService]
        OrderService[OrderService]
    end
    
    subgraph Data["数据访问层"]
        TenantInterceptor[TenantLineInnerInterceptor<br/>SQL拦截器]
        DataScope[DataScopeInterceptor<br/>数据权限]
        OptimisticLock[OptimisticLockerInterceptor<br/>乐观锁]
    end
    
    subgraph Database["数据库层"]
        MySQL[(MySQL 9.0<br/>tenant_id字段)]
    end
    
    subgraph Cache["缓存层"]
        RedisKey[Redis Key<br/>apex:tenant:{tenantId}:*]
    end
    
    subgraph MQ["消息队列层"]
        IdempotentKey[幂等Key<br/>tenant作用域]
    end
    
    Client -->|携带租户标识| Header
    Header --> TenantFilter
    TenantFilter -->|提取并验证| TenantContext
    TenantContext --> UserService
    TenantContext --> OrderService
    
    UserService --> TenantInterceptor
    OrderService --> TenantInterceptor
    
    TenantInterceptor -->|拦截顺序: 1| DataScope
    DataScope -->|拦截顺序: 2| OptimisticLock
    OptimisticLock --> MySQL
    
    TenantContext -.->|租户隔离| RedisKey
    TenantContext -.->|租户作用域| IdempotentKey
```

详细架构源文件: [72_multi_tenant_isolation_architecture.mmd](../diagrams/sequence/72_multi_tenant_isolation_architecture.mmd)

---

## 🔑 核心组件

### 1. TenantWebFilter (过滤器)

**位置**: `apex-system/src/main/java/com/apex/scaffold/interfaces/filter/TenantWebFilter.java`

**职责**:
- 从HTTP请求头提取`X-Tenant-Id`
- 验证租户ID合法性
- 设置`TenantContext`上下文
- 优先级设置为`-100`,确保最先执行

**代码示例**:
```java
@Component
@Order(-100)
public class TenantWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (tenantId == null) {
            return Mono.error(new BusinessException(ResultCode.TENANT_ID_REQUIRED));
        }
        TenantContext.setTenantId(tenantId);
        return chain.filter(exchange)
            .doFinally(signalType -> TenantContext.clear());
    }
}
```

### 2. TenantContext (上下文)

**位置**: `apex-common/src/main/java/com/apex/scaffold/common/context/TenantContext.java`

**职责**:
- 使用`ThreadLocal`存储当前租户ID
- 提供租户ID的设置和获取方法
- 在请求结束时自动清理

**代码示例**:
```java
public class TenantContext {
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    
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

### 3. TenantLineInnerInterceptor (SQL拦截器)

**位置**: `apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java`

**职责**:
- 拦截所有SQL语句
- 自动在WHERE子句添加`tenant_id = ?`条件
- 自动在INSERT语句添加`tenant_id`字段
- 拦截顺序: 多租户 → 数据权限 → 乐观锁

**配置示例**:
```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    
    // 1. 多租户拦截器 (最高优先级)
    TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
    tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
        @Override
        public Expression getTenantId() {
            return new StringValue(TenantContext.getTenantId());
        }
        
        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }
    });
    interceptor.addInnerInterceptor(tenantInterceptor);
    
    // 2. 数据权限拦截器
    interceptor.addInnerInterceptor(new DataScopeInterceptor());
    
    // 3. 乐观锁拦截器
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    
    return interceptor;
}
```

---

## 📊 数据库设计

### 表结构规范

所有需要租户隔离的表必须包含`tenant_id`字段:

```sql
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` varchar(64) NOT NULL COMMENT '租户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_username` (`username`),
  KEY `idx_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';
```

**索引设计原则**:
1. `tenant_id`字段**必须**建立索引
2. 联合查询条件中,`tenant_id`应放在索引第一位
3. 示例: `KEY idx_tenant_status (tenant_id, status)`

---

## 🔒 缓存隔离

### Redis Key 规范

所有Redis Key必须包含租户ID:

```
格式: apex:tenant:{tenantId}:{业务模块}:{具体标识}

示例:
apex:tenant:tenant_001:user:123456
apex:tenant:tenant_001:captcha:login_code
apex:tenant:tenant_001:token:access_token_xyz
```

**实现示例**:
```java
public class CacheKeyUtil {
    public static String buildKey(String module, String identifier) {
        String tenantId = TenantContext.getTenantId();
        return String.format("apex:tenant:%s:%s:%s", tenantId, module, identifier);
    }
}
```

---

## 📮 消息队列隔离

### 幂等Key租户作用域

消息幂等Key必须包含租户ID:

```java
public class IdempotentKeyGenerator {
    public String generateKey(String messageId) {
        String tenantId = TenantContext.getTenantId();
        return String.format("idempotent:tenant:%s:msg:%s", tenantId, messageId);
    }
}
```

---

## ⚠️ 注意事项

### 1. 安全防护

**防止租户ID伪造**:
```java
// ❌ 错误: 直接从请求参数获取
String tenantId = request.getParameter("tenantId");

// ✅ 正确: 只从TenantContext获取
String tenantId = TenantContext.getTenantId();
```

**JWT Token中包含租户ID**:
```java
public Claims parseToken(String token) {
    Claims claims = Jwts.parser()
        .setSigningKey(secret)
        .parseClaimsJws(token)
        .getBody();
    
    // 验证token中的租户ID与请求头一致
    String tokenTenantId = claims.get("tenantId", String.class);
    String requestTenantId = TenantContext.getTenantId();
    
    if (!Objects.equals(tokenTenantId, requestTenantId)) {
        throw new BusinessException(ResultCode.TENANT_ID_MISMATCH);
    }
    
    return claims;
}
```

### 2. 表级别控制

某些表不需要租户隔离(如字典表、系统配置表),需要在拦截器中排除:

```java
tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
    @Override
    public boolean ignoreTable(String tableName) {
        // 排除不需要租户隔离的表
        return Arrays.asList(
            "sys_dict_type",
            "sys_dict_data",
            "sys_config",
            "sys_tenant"
        ).contains(tableName);
    }
});
```

### 3. 跨租户查询

管理员需要查询所有租户数据时,使用`@TenantIgnore`注解:

```java
@TenantIgnore
@Select("SELECT * FROM sys_user")
List<User> listAllUsers();
```

---

## 🧪 测试验证

### 单元测试

```java
@Test
void testTenantIsolation() {
    // 设置租户A
    TenantContext.setTenantId("tenant_a");
    User userA = userMapper.selectById(1L);
    assertNotNull(userA);
    
    // 切换到租户B
    TenantContext.setTenantId("tenant_b");
    User userB = userMapper.selectById(1L);
    assertNull(userB); // 租户B无法查询到租户A的数据
    
    TenantContext.clear();
}
```

### 集成测试

```java
@SpringBootTest
class TenantIntegrationTest {
    @Test
    void testMultiTenantSqlInterception() {
        // 模拟HTTP请求
        webTestClient.post()
            .uri("/api/v1/users")
            .header("X-Tenant-Id", "tenant_001")
            .bodyValue(createUserRequest)
            .exchange()
            .expectStatus().isOk();
        
        // 验证SQL日志包含tenant_id条件
        // 应输出: INSERT INTO sys_user (..., tenant_id) VALUES (..., 'tenant_001')
    }
}
```

---

## 📚 参考资料

- [AGENTS.md](../../AGENTS.md) - 多租户开发规范
- [TenantIsolationArchTest.java](../../apex-infrastructure-persistence/src/test/java/com/apex/scaffold/arch/TenantIsolationArchTest.java) - 架构约束测试
- Flyway迁移 V14~V19 - 租户隔离相关迁移

---

**文档维护**: Apex Team  
**最后更新**: 2026-09-12
