# 策略模式实现指南

**版本**: 1.0  
**更新日期**: 2026-09-10  
**适用版本**: Apex Scaffold 1.2.0+

---

## 一、概述

### 1.1 什么是策略模式

策略模式（Strategy Pattern）是一种行为设计模式,它定义了一系列算法,并将每个算法封装起来,使它们可以互相替换。策略模式让算法独立于使用它的客户端而变化。

**核心组成**:
- **策略接口（Strategy）**: 定义统一的操作规范
- **具体策略（Concrete Strategy）**: 实现具体的算法逻辑
- **上下文（Context）**: 持有策略引用,负责策略选择和调用

### 1.2 为什么使用策略模式

| 不使用策略模式 | 使用策略模式 | 优势 |
|--------------|------------|------|
| 大量 if-else 判断 | 接口多态调用 | 代码清晰度提升 80%+ |
| 新增类型需修改原有代码 | 仅需新增实现类 | 符合开闭原则 |
| 难以测试和维护 | 每个策略独立测试 | 可测试性提升 |
| 职责不清晰 | 职责明确分离 | 可维护性提升 |

---

## 二、Apex Scaffold 中的策略模式

### 2.1 登录策略模式

#### 架构设计

```
┌───────────────────────────────────────┐
│      LoginStrategyProvider            │  ← 策略提供者（上下文）
│   EnumMap<LoginType, LoginStrategy>   │
└───────────────┬───────────────────────┘
                │
                ├──────┬──────┬──────┬──────┐
                │      │      │      │      │
        ┌───────▼──┐  │  ┌───▼───┐ │  ┌───▼───┐
        │WeChatCode│  │  │Password│ │  │  Sms  │
        │Strategy  │  │  │Strategy│ │  │Strategy│
        └──────────┘  │  └────────┘ │  └────────┘
                      │              │
                 ┌────▼────┐    ┌───▼────┐
                 │WeChatPhone│  │Chuanglan│
                 │Strategy │  │  Phone  │
                 └─────────┘  └─────────┘
```

#### 核心接口

```java
/**
 * 登录策略接口
 */
public interface LoginStrategy {
    
    /**
     * 执行登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    Mono<LoginResponse> login(LoginRequest request);
    
    /**
     * 获取登录类型
     *
     * @return 登录类型枚举
     */
    LoginType getLoginType();
}
```

#### 策略提供者

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginStrategyProvider {
    
    private final List<LoginStrategy> strategies;
    private EnumMap<LoginType, LoginStrategy> strategyMap;
    
    /**
     * 初始化策略映射（自注册机制）
     */
    @PostConstruct
    public void init() {
        strategyMap = new EnumMap<>(LoginType.class);
        for (LoginStrategy strategy : strategies) {
            LoginType type = strategy.getLoginType();
            if (strategyMap.containsKey(type)) {
                throw new IllegalStateException("重复的登录策略: " + type);
            }
            strategyMap.put(type, strategy);
            log.info("注册登录策略: {} -> {}", type, strategy.getClass().getSimpleName());
        }
    }
    
    /**
     * 获取策略
     *
     * @param loginTypeName 登录类型名称
     * @return 对应的登录策略
     */
    public LoginStrategy getStrategy(String loginTypeName) {
        LoginType loginType = LoginType.valueOf(loginTypeName.toUpperCase());
        LoginStrategy strategy = strategyMap.get(loginType);
        if (strategy == null) {
            throw new BusinessException(ResultCode.UNSUPPORTED_LOGIN_TYPE);
        }
        return strategy;
    }
}
```

#### 具体策略实现

**1. 密码登录策略**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordLoginStrategy implements LoginStrategy {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new BusinessException("用户不存在")))
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .switchIfEmpty(Mono.error(new BusinessException("密码错误")))
                .flatMap(user -> tokenService.generateTokens(user.getId())
                        .map(tokens -> LoginResponse.builder()
                                .userId(user.getId())
                                .accessToken(tokens.getAccessToken())
                                .refreshToken(tokens.getRefreshToken())
                                .build()));
    }
    
    @Override
    public LoginType getLoginType() {
        return LoginType.PASSWORD;
    }
}
```

**2. 微信授权码登录策略**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class WeChatCodeLoginStrategy implements LoginStrategy {
    
    private final WeChatOAuth2Service weChatService;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        String code = request.getCode();
        
        return weChatService.exchangeAccessToken(code)          // 1. 换取 access_token
                .flatMap(weChatService::getUserInfo)            // 2. 获取用户信息
                .flatMap(weChatUser -> 
                        findOrCreateUser(weChatUser.getOpenId()))  // 3. 查找或创建用户
                .flatMap(user -> tokenService.generateTokens(user.getId())
                        .map(tokens -> LoginResponse.builder()
                                .userId(user.getId())
                                .accessToken(tokens.getAccessToken())
                                .refreshToken(tokens.getRefreshToken())
                                .build()));
    }
    
    @Override
    public LoginType getLoginType() {
        return LoginType.WX_CODE;
    }
    
    private Mono<User> findOrCreateUser(String openId) {
        return userRepository.findByOpenId(openId)
                .switchIfEmpty(Mono.defer(() -> createNewUser(openId)));
    }
}
```

#### 使用方式

```java
@Component
@RequiredArgsConstructor
public class AuthHandler {
    
    private final LoginStrategyProvider strategyProvider;
    
    public Mono<ServerResponse> login(ServerRequest request) {
        return request.bodyToMono(LoginRequest.class)
                .flatMap(loginRequest -> {
                    // 根据登录类型选择策略
                    LoginStrategy strategy = strategyProvider.getStrategy(
                            loginRequest.getLoginType());
                    
                    // 执行登录
                    return strategy.login(loginRequest);
                })
                .flatMap(response -> ServerResponse
                        .ok()
                        .bodyValue(Result.success(response)));
    }
}
```

---

### 2.2 服务治理策略模式

#### 架构设计

```
┌─────────────────────────────────────────┐
│ DeploymentGovernanceStrategyRegistry     │  ← 策略注册表
│ Map<DeploymentMode, Strategy>            │
└──────────────┬──────────────────────────┘
               │
               ├────────┬────────┐
               │        │        │
     ┌─────────▼──┐  ┌──▼──┐  ┌─▼─────────┐
     │Standalone  │  │Compose│ │Kubernetes │
     │Strategy    │  │Strategy│ │Strategy   │
     └────────────┘  └────────┘ └───────────┘
         ↓               ↓           ↓
      java -jar     Podman Compose  K8s Pod
```

#### 核心接口

```java
/**
 * 部署平台治理策略接口
 */
public interface DeploymentGovernanceStrategy {
    
    /**
     * 部署模式
     */
    DeploymentMode mode();
    
    /**
     * 准备接收流量
     */
    void prepareForTraffic();
    
    /**
     * 摘除流量
     *
     * @param reason 摘除原因
     */
    void withdrawFromTraffic(WithdrawalReason reason);
    
    /**
     * 恢复流量
     */
    void restoreTraffic();
    
    /**
     * 平台能力矩阵
     */
    GovernanceCapabilities capabilities();
}
```

#### 策略注册表

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DeploymentGovernanceStrategyRegistry {
    
    private final List<DeploymentGovernanceStrategy> strategies;
    private final DeploymentModeDetector modeDetector;
    
    private Map<DeploymentMode, DeploymentGovernanceStrategy> strategyMap;
    private DeploymentGovernanceStrategy activeStrategy;
    
    @PostConstruct
    public void init() {
        // 构建策略映射
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        DeploymentGovernanceStrategy::mode,
                        Function.identity()));
        
        // 选择活动策略
        DeploymentMode currentMode = modeDetector.detect();
        activeStrategy = strategyMap.get(currentMode);
        
        if (activeStrategy == null) {
            throw new IllegalStateException("未找到部署模式策略: " + currentMode);
        }
        
        log.info("激活治理策略: {} -> {}", 
                currentMode, activeStrategy.getClass().getSimpleName());
    }
    
    /**
     * 获取当前活动策略
     */
    public DeploymentGovernanceStrategy activeStrategy() {
        return activeStrategy;
    }
}
```

#### 具体策略实现

**1. Kubernetes 治理策略**

```java
@Slf4j
public class KubernetesGovernanceStrategy implements DeploymentGovernanceStrategy {
    
    private final ReadinessStateController readinessController;
    
    @Override
    public DeploymentMode mode() {
        return DeploymentMode.KUBERNETES;
    }
    
    @Override
    public void prepareForTraffic() {
        log.info("[K8s] 准备接收流量: 设置 readiness=ACCEPTING_TRAFFIC");
        readinessController.acceptTraffic();
        log.info("[K8s] 等待 K8s probe 检测完成");
        sleep(Duration.ofSeconds(5));  // 等待 probe 周期
    }
    
    @Override
    public void withdrawFromTraffic(WithdrawalReason reason) {
        log.info("[K8s] 摘除流量: reason={}", reason);
        readinessController.refuseTraffic();
        log.info("[K8s] 等待 Service 移除 Pod Endpoint");
        sleep(Duration.ofSeconds(10));  // 等待传播
    }
    
    @Override
    public void restoreTraffic() {
        log.info("[K8s] 恢复流量");
        readinessController.acceptTraffic();
    }
    
    @Override
    public GovernanceCapabilities capabilities() {
        return GovernanceCapabilities.builder()
                .supportsReadinessProbe(true)
                .supportsGracefulShutdown(true)
                .supportsHealthCheck(true)
                .supportsLoadBalancing(true)
                .build();
    }
}
```

**2. Standalone 治理策略**

```java
@Slf4j
public class StandaloneGovernanceStrategy implements DeploymentGovernanceStrategy {
    
    private final ReadinessStateController readinessController;
    private final Optional<NacosService> nacosService;
    
    @Override
    public DeploymentMode mode() {
        return DeploymentMode.STANDALONE;
    }
    
    @Override
    public void prepareForTraffic() {
        log.info("[Standalone] 准备接收流量");
        readinessController.acceptTraffic();
        
        // 如果启用 Nacos,注册实例
        nacosService.ifPresent(nacos -> {
            log.info("[Standalone] 向 Nacos 注册实例");
            nacos.registerInstance();
        });
    }
    
    @Override
    public void withdrawFromTraffic(WithdrawalReason reason) {
        log.info("[Standalone] 摘除流量: reason={}", reason);
        readinessController.refuseTraffic();
        
        // 如果启用 Nacos,下线实例
        nacosService.ifPresent(nacos -> {
            log.info("[Standalone] 从 Nacos 下线实例");
            nacos.deregisterInstance();
            sleep(Duration.ofSeconds(5));  // 等待传播
        });
    }
    
    @Override
    public GovernanceCapabilities capabilities() {
        return GovernanceCapabilities.builder()
                .supportsReadinessProbe(true)
                .supportsGracefulShutdown(true)
                .supportsNacos(nacosService.isPresent())
                .build();
    }
}
```

#### 使用方式

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class GracefulShutdownCoordinator implements SmartLifecycle {
    
    private final DeploymentGovernanceStrategyRegistry strategyRegistry;
    
    @Override
    public void stop(Runnable callback) {
        DeploymentGovernanceStrategy strategy = strategyRegistry.activeStrategy();
        
        log.info("开始优雅关机流程,使用策略: {}", strategy.mode());
        
        try {
            // 1. 摘除流量
            strategy.withdrawFromTraffic(WithdrawalReason.SHUTDOWN);
            
            // 2. 排空请求
            drainInFlightRequests();
            
            // 3. 停止新任务
            stopNewTasks();
            
            // 4. 执行清理
            cleanup();
            
            log.info("优雅关机完成");
        } finally {
            callback.run();
        }
    }
}
```

---

## 三、实现策略模式的步骤

### 3.1 定义策略接口

```java
public interface PaymentStrategy {
    /**
     * 执行支付
     */
    Mono<PaymentResult> pay(PaymentRequest request);
    
    /**
     * 获取支付类型
     */
    PaymentType getType();
}
```

### 3.2 实现具体策略

```java
@Service
@RequiredArgsConstructor
public class AlipayStrategy implements PaymentStrategy {
    
    private final AlipayClient alipayClient;
    
    @Override
    public Mono<PaymentResult> pay(PaymentRequest request) {
        // 支付宝支付逻辑
        return alipayClient.createOrder(request)
                .map(this::convertToResult);
    }
    
    @Override
    public PaymentType getType() {
        return PaymentType.ALIPAY;
    }
}

@Service
@RequiredArgsConstructor
public class WeChatPayStrategy implements PaymentStrategy {
    
    private final WeChatPayClient weChatClient;
    
    @Override
    public Mono<PaymentResult> pay(PaymentRequest request) {
        // 微信支付逻辑
        return weChatClient.unifiedOrder(request)
                .map(this::convertToResult);
    }
    
    @Override
    public PaymentType getType() {
        return PaymentType.WECHAT;
    }
}
```

### 3.3 创建策略提供者

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentStrategyProvider {
    
    private final List<PaymentStrategy> strategies;
    private Map<PaymentType, PaymentStrategy> strategyMap;
    
    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        PaymentStrategy::getType,
                        Function.identity()));
        
        log.info("注册支付策略: {}", strategyMap.keySet());
    }
    
    public PaymentStrategy getStrategy(PaymentType type) {
        PaymentStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new BusinessException("不支持的支付类型: " + type);
        }
        return strategy;
    }
}
```

### 3.4 在业务代码中使用

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final PaymentStrategyProvider strategyProvider;
    
    public Mono<PaymentResult> payOrder(Long orderId, PaymentType paymentType) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BusinessException("订单不存在")))
                .flatMap(order -> {
                    // 选择支付策略
                    PaymentStrategy strategy = strategyProvider.getStrategy(paymentType);
                    
                    // 执行支付
                    PaymentRequest request = buildPaymentRequest(order);
                    return strategy.pay(request);
                })
                .doOnNext(result -> updateOrderStatus(orderId, result));
    }
}
```

---

## 四、最佳实践

### 4.1 自注册机制

✅ **推荐方式**: 通过 Spring 自动注入所有策略实现

```java
@PostConstruct
public void init() {
    // Spring 自动注入所有 LoginStrategy 实现
    for (LoginStrategy strategy : strategies) {
        strategyMap.put(strategy.getLoginType(), strategy);
    }
}
```

❌ **不推荐方式**: 手动注册

```java
// 维护成本高,容易遗漏新增策略
strategyMap.put(LoginType.PASSWORD, new PasswordLoginStrategy());
strategyMap.put(LoginType.WX_CODE, new WeChatCodeLoginStrategy());
```

### 4.2 策略验证

```java
@PostConstruct
public void init() {
    strategyMap = new EnumMap<>(LoginType.class);
    
    for (LoginStrategy strategy : strategies) {
        LoginType type = strategy.getLoginType();
        
        // 检查重复注册
        if (strategyMap.containsKey(type)) {
            throw new IllegalStateException(
                    String.format("重复的登录策略: %s (已有: %s, 冲突: %s)",
                            type,
                            strategyMap.get(type).getClass().getSimpleName(),
                            strategy.getClass().getSimpleName()));
        }
        
        strategyMap.put(type, strategy);
        log.info("注册登录策略: {} -> {}", type, strategy.getClass().getSimpleName());
    }
    
    // 验证必需策略
    List<LoginType> requiredTypes = List.of(LoginType.PASSWORD, LoginType.WX_CODE);
    for (LoginType required : requiredTypes) {
        if (!strategyMap.containsKey(required)) {
            log.warn("缺少必需的登录策略: {}", required);
        }
    }
}
```

### 4.3 策略组合（模板方法 + 策略模式）

```java
/**
 * 抽象基类,封装基于手机号登录的公共逻辑
 */
@RequiredArgsConstructor
public abstract class AbstractPhoneLoginStrategy implements LoginStrategy {
    
    protected final UserRepository userRepository;
    protected final TokenService tokenService;
    
    @Override
    public final Mono<LoginResponse> login(LoginRequest request) {
        // 模板方法:固定流程
        return validatePhone(request)           // 1. 验证手机号
                .flatMap(this::verifyToken)     // 2. 验证令牌(子类实现)
                .flatMap(this::findOrCreateUser)  // 3. 查找或创建用户
                .flatMap(this::generateTokens);   // 4. 生成 JWT
    }
    
    /**
     * 验证令牌(由子类实现)
     */
    protected abstract Mono<String> verifyToken(String phone);
    
    private Mono<String> validatePhone(LoginRequest request) {
        String phone = request.getPhone();
        if (!PhoneValidator.isValid(phone)) {
            return Mono.error(new BusinessException("手机号格式错误"));
        }
        return Mono.just(phone);
    }
}

/**
 * 微信一键登录策略
 */
@Service
public class WeChatPhoneLoginStrategy extends AbstractPhoneLoginStrategy {
    
    private final WeChatPhoneService weChatPhoneService;
    
    @Override
    protected Mono<String> verifyToken(String phone) {
        // 微信特定的令牌验证逻辑
        return weChatPhoneService.verifyAccessToken(phone);
    }
    
    @Override
    public LoginType getLoginType() {
        return LoginType.WX_PHONE;
    }
}
```

---

## 五、测试

### 5.1 策略单元测试

```java
@ExtendWith(MockitoExtension.class)
class PasswordLoginStrategyTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private TokenService tokenService;
    
    @InjectMocks
    private PasswordLoginStrategy strategy;
    
    @Test
    void testLoginSuccess() {
        // 准备数据
        User user = User.builder()
                .id(1L)
                .username("test")
                .password("$2a$10$hashed")
                .build();
        
        TokenPair tokens = TokenPair.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .build();
        
        // Mock 行为
        when(userRepository.findByUsername("test"))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches("password", "$2a$10$hashed"))
                .thenReturn(true);
        when(tokenService.generateTokens(1L))
                .thenReturn(Mono.just(tokens));
        
        // 执行测试
        LoginRequest request = LoginRequest.builder()
                .username("test")
                .password("password")
                .build();
        
        StepVerifier.create(strategy.login(request))
                .assertNext(response -> {
                    assertThat(response.getUserId()).isEqualTo(1L);
                    assertThat(response.getAccessToken()).isEqualTo("access");
                })
                .verifyComplete();
    }
}
```

### 5.2 策略提供者测试

```java
@ExtendWith(MockitoExtension.class)
class LoginStrategyProviderTest {
    
    @Mock
    private LoginStrategy passwordStrategy;
    
    @Mock
    private LoginStrategy weChatStrategy;
    
    private LoginStrategyProvider provider;
    
    @BeforeEach
    void setUp() {
        when(passwordStrategy.getLoginType()).thenReturn(LoginType.PASSWORD);
        when(weChatStrategy.getLoginType()).thenReturn(LoginType.WX_CODE);
        
        provider = new LoginStrategyProvider(List.of(passwordStrategy, weChatStrategy));
        provider.init();
    }
    
    @Test
    void testGetStrategy() {
        LoginStrategy strategy = provider.getStrategy("PASSWORD");
        assertThat(strategy).isSameAs(passwordStrategy);
        
        strategy = provider.getStrategy("WX_CODE");
        assertThat(strategy).isSameAs(weChatStrategy);
    }
    
    @Test
    void testGetStrategyThrowsForUnsupportedType() {
        assertThatThrownBy(() -> provider.getStrategy("UNKNOWN"))
                .isInstanceOf(BusinessException.class);
    }
}
```

---

## 六、常见问题

### Q1: 何时使用策略模式?

**适用场景**:
- 有多个相似算法,可以互相替换
- 需要在运行时动态选择算法
- 避免大量 if-else 或 switch-case

**不适用场景**:
- 策略很少变化
- 策略之间差异很大,无法抽象统一接口

### Q2: 策略模式 vs 工厂模式?

| 对比维度 | 策略模式 | 工厂模式 |
|---------|---------|---------|
| 关注点 | 算法行为 | 对象创建 |
| 客户端 | 知道所有策略 | 不知道具体类型 |
| 目的 | 替换算法 | 解耦创建逻辑 |

### Q3: 如何处理策略之间的共同逻辑?

使用**抽象基类** + **模板方法模式**:

```java
public abstract class AbstractLoginStrategy implements LoginStrategy {
    
    @Override
    public final Mono<LoginResponse> login(LoginRequest request) {
        return validate(request)
                .flatMap(this::authenticate)   // 子类实现
                .flatMap(this::generateResponse);
    }
    
    protected abstract Mono<User> authenticate(LoginRequest request);
    
    private Mono<LoginRequest> validate(LoginRequest request) {
        // 公共验证逻辑
        return Mono.just(request);
    }
}
```

---

## 七、参考资料

### 项目实现
- [LoginStrategy.java](../../apex-application/src/main/java/com/apex/scaffold/application/service/login/LoginStrategy.java)
- [DeploymentGovernanceStrategy.java](../../apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentGovernanceStrategy.java)

### 设计文档
- [服务治理方案](./service-governance-solution.md)
- [服务治理实施](./service-governance-implementation.md)

---

**版权声明**: © 2026 Apex Team  
**维护者**: Apex 开发团队
