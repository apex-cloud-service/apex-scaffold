# 问题排查指南

[查看故障排查决策树](diagrams/47_troubleshooting_decision_tree.svg)

本文档汇总了使用 Apex Scaffold 时遇到的常见问题及解决方案。

## 🚀 启动问题

### Q1: 应用启动失败,报错"Port 8080 already in use"

**原因**: 端口被其他进程占用

**解决方案**:

```bash
# macOS/Linux: 查找占用端口的进程
lsof -i :8080

# 杀死进程
kill -9 <PID>

# 或者修改应用端口
# application.yml
server:
  port: 8081
```

### Q2: MySQL连接失败 "Communications link failure"

**原因**: MySQL未启动或配置错误

**解决方案**:

```bash
# 检查MySQL是否运行（本地基础设施由 docker-compose.infra.yml 管理）
docker compose -f docker-compose.infra.yml ps mysql

# 如果未运行,启动MySQL
docker compose -f docker-compose.infra.yml up -d mysql

# 等待MySQL完全启动(约30秒)
sleep 30

# 测试连接（默认 root / apex-root）
mysql -h localhost -P 3306 -u root -papex-root
```

检查配置文件（应用实际为 dynamic-datasource 结构，见 application-dev.yml）:
```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
          username: root
          password: apex-root  # 与 docker-compose.infra.yml 的 MYSQL_ROOT_PASSWORD 一致
```

### Q3: Redis连接失败（Lettuce/Redisson 无法建立连接）

**解决方案**:

```bash
# 检查Redis状态（本地基础设施由 docker-compose.infra.yml 管理）
docker compose -f docker-compose.infra.yml ps redis

# 测试Redis连接（infra 栈容器名为 apex-infra-redis）
docker exec -it apex-infra-redis redis-cli ping
# 应返回: PONG
```

### Q4: Maven编译失败 "Could not resolve dependencies"

**原因**: 网络问题或Maven仓库配置错误

**解决方案**:

```bash
# 清理本地缓存
mvnd dependency:purge-local-repository

# 重新下载依赖
mvnd clean install -U

# 如果使用国内网络,配置阿里云镜像
# ~/.m2/settings.xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

## 🔐 认证授权问题

### Q5: JWT Token验证失败 "Invalid JWT signature"

**原因**: JWT密钥不一致

**解决方案**:

1. 检查环境变量:
```bash
echo $JWT_SECRET
```

2. 确保生成Token和验证Token使用相同的密钥:
```yaml
jwt:
  secret: ${JWT_SECRET:your-consistent-secret-key}
```

3. 重启应用使配置生效

### Q6: 接口返回403 Forbidden

**原因**: 权限不足或Token过期

**解决方案**:

1. 检查Token是否过期:
```bash
# 解码JWT查看过期时间
echo "YOUR_TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq '.exp'
```

2. 检查用户角色权限:
```sql
-- 查询用户角色
SELECT u.username, r.role_name 
FROM user u
JOIN user_role ur ON u.id = ur.user_id
JOIN role r ON ur.role_id = r.id
WHERE u.username = 'testuser';
```

3. 检查接口权限配置:
```java
@RequirePermission("user:view")  // 确保用户有此权限
public Mono<ServerResponse> getUser(ServerRequest request) { ... }
```

### Q7: CORS跨域错误

**解决方案**:

```yaml
# application.yml
spring:
  webflux:
    cors:
      allowed-origins: "*"  # 生产环境应指定具体域名
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: "*"
      allow-credentials: true
      max-age: 3600
```

## 💾 数据库问题

### Q8: MyBatis-Plus插入数据后ID为null

**原因**: 主键策略配置错误

**解决方案**:

```java
// 实体类
@TableName("user")
public class UserEntity extends BaseEntity {
    
    @TableId(type = IdType.ASSIGN_ID)  // 使用雪花算法
    private Long id;
    
    // 或使用自增
    // @TableId(type = IdType.AUTO)
}
```

检查全局配置:
```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: ASSIGN_ID  # 或 AUTO/INPUT
```

### Q9: SQL执行慢,如何优化?

**解决方案**:

1. 开启SQL日志:
```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

2. 使用EXPLAIN分析:
```sql
EXPLAIN SELECT * FROM user WHERE username = 'testuser';
```

3. 添加索引:
```sql
CREATE INDEX idx_username ON user(username);
```

4. 避免N+1查询:
```java
// ❌ N+1查询
List<Order> orders = orderRepository.findAll();
orders.forEach(order -> {
    User user = userRepository.findById(order.getUserId());
});

// ✅ 批量查询
List<Long> userIds = orders.stream()
    .map(Order::getUserId)
    .distinct()
    .collect(Collectors.toList());
Map<Long, User> userMap = userRepository.findByIdIn(userIds)
    .stream()
    .collect(Collectors.toMap(User::getId, u -> u));
```

## ⚡ 缓存问题

### Q10: 缓存不生效,每次都查数据库

**原因**: 缓存配置错误或Key冲突

**解决方案**:

1. 检查缓存是否启用:
```yaml
cache:
  multi-level:
    enabled: true
```

2. 检查@Cacheable注解:
```java
@Cacheable(value = "user", key = "#id", unless = "#result == null")
public Mono<UserEntity> findById(Long id) {
    return userRepository.findById(id);
}
```

3. 清除缓存测试:
```bash
# Redis中查看缓存Key
docker exec -it apex-infra-redis redis-cli keys "user:*"

# 删除特定缓存
docker exec -it apex-infra-redis redis-cli del "user::1"
```

### Q11: 缓存穿透导致数据库压力大

**解决方案**:

启用布隆过滤器:
```yaml
cache:
  bloom-filter:
    enabled: true
    expected-insertions: 1000000
    false-positive-probability: 0.01
```

或在代码层处理:
```java
public Mono<UserEntity> findById(Long id) {
    // 先查缓存
    return cacheService.get("user", id)
        .switchIfEmpty(Mono.defer(() -> 
            // 缓存未命中,查数据库
            userRepository.findById(id)
                .doOnNext(user -> {
                    if (user != null) {
                        // 写入缓存
                        cacheService.put("user", id, user);
                    } else {
                        // 防止缓存穿透:缓存空值
                        cacheService.put("user", id, NULL_PLACEHOLDER, 60);
                    }
                })
        ));
}
```

## 📨 消息队列问题

### Q12: RocketMQ消息发送失败 "No route info of this topic"

**原因**: Topic未创建或NameServer地址错误

**解决方案**:

```bash
# 检查NameServer是否运行（本地基础设施由 docker-compose.infra.yml 管理）
docker compose -f docker-compose.infra.yml ps rocketmq-namesrv

# 创建Topic
docker exec -it <rocketmq-broker-container> sh
mqadmin updateTopic -n localhost:9876 -t your-topic -c DefaultCluster
```

检查配置:
```yaml
rocketmq:
  name-server: localhost:9876  # 确保地址正确
  producer:
    group: apex-producer-group
```

### Q13: 消息重复消费

**解决方案**:

实现幂等消费:
```java
@Component
@RocketMQMessageListener(topic = "order-topic", consumerGroup = "order-consumer")
public class OrderConsumer implements RocketMQListener<OrderEvent> {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    @Transactional
    public void onMessage(OrderEvent event) {
        String key = "order:processed:" + event.getOrderNo();
        
        // 幂等检查
        Boolean isAbsent = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        
        if (Boolean.FALSE.equals(isAbsent)) {
            log.warn("订单已处理,跳过: {}", event.getOrderNo());
            return;
        }
        
        // 处理业务逻辑
        processOrder(event);
    }
}
```

## 📊 监控问题

### Q14: Prometheus无法采集指标

**解决方案**:

1. 检查Actuator端点是否暴露:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

2. 访问指标端点测试:
```bash
curl http://localhost:8080/actuator/prometheus
```

3. 检查Prometheus配置:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'apex-scaffold'
    static_configs:
      - targets: ['localhost:8080']
```

### Q15: Grafana看板无数据

**解决方案**:

1. 检查数据源配置:
   - URL: `http://localhost:9090` (Prometheus地址)
   - 点击"Save & Test"确认连接成功

2. 检查Prometheus是否正常采集:
```bash
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job=="apex-scaffold")'
```

3. 重启Grafana:
```bash
docker compose -f docker-compose.infra.yml restart grafana
```

## 🐳 Docker问题

### Q16: Docker容器启动后立即退出

**解决方案**:

```bash
# 查看容器日志
docker compose -f docker-compose.infra.yml logs apex-scaffold

# 常见原因:
# 1. 端口冲突: 修改 docker-compose.yml / docker-compose.infra.yml 中的 ports 映射
# 2. 内存不足: 增加Docker资源限制
# 3. 配置文件错误: 检查application.yml
```

### Q17: Docker构建慢

**解决方案**:

使用多阶段构建和优化Dockerfile:
```dockerfile
# 使用国内Maven镜像
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B  # 预下载依赖
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

构建时使用缓存:
```bash
docker build --no-cache -t apex-scaffold:latest .
```

## 🔍 调试技巧

### 启用详细日志

```yaml
# application-dev.yml
logging:
  level:
    root: DEBUG
    com.apex: DEBUG
    org.springframework.web: DEBUG
    org.mybatis: DEBUG
```

### 远程调试

```bash
# 启动时添加JVM参数
mvnd spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

# IDEA中配置Remote JVM Debug
# Host: localhost
# Port: 5005
```

### 性能分析

```bash
# 启用JFR(Java Flight Recorder)
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr -jar app.jar

# 使用JDK Mission Control分析
jmc recording.jfr
```

## 📞 获取帮助

如果以上方案无法解决你的问题:

1. 📖 查阅 [完整文档](../README.md)
2. 🐛 搜索 [GitHub Issues](https://github.com/your-org/apex-scaffold/issues)
3. 💬 在 [Discussions](https://github.com/your-org/apex-scaffold/discussions) 提问
4. 📧 邮件联系: your-email@example.com

**提交Issue时请提供**:
- 完整的错误日志
- 复现步骤
- 环境信息(JDK版本、OS、Docker版本)
- 相关配置文件内容

---

**持续更新中...** 欢迎补充更多常见问题!
