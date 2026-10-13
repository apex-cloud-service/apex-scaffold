# Lambda 表达式最佳实践速查手册

**适用范围**: Apex Scaffold 项目  
**Java 版本**: 17.0.12  
**创建日期**: 2026-09-10

---

## 目录

1. [基本原则](#基本原则)
2. [方法引用优先](#方法引用优先)
3. [Stream API 最佳实践](#stream-api-最佳实践)
4. [Optional 链式操作](#optional-链式操作)
5. [响应式编程（Reactor）](#响应式编程reactor)
6. [常见反模式](#常见反模式)
7. [性能考量](#性能考量)

---

## 基本原则

### 原则 1: 保持简洁

Lambda 表达式应该简短且易读，超过 3 行建议提取为独立方法。

```java
// ❌ 不推荐：Lambda 过长
users.stream()
    .filter(user -> {
        if (user.getAge() < 18) return false;
        if (user.getStatus() != UserStatus.ACTIVE) return false;
        if (user.getDeptId() == null) return false;
        LocalDateTime lastLogin = user.getLastLoginTime();
        return lastLogin != null && lastLogin.isAfter(LocalDateTime.now().minusDays(30));
    })
    .collect(Collectors.toList());

// ✅ 推荐：提取为方法
users.stream()
    .filter(this::isActiveRecentUser)
    .toList();

private boolean isActiveRecentUser(User user) {
    return user.getAge() >= 18
        && user.getStatus() == UserStatus.ACTIVE
        && user.getDeptId() != null
        && isRecentLogin(user.getLastLoginTime());
}

private boolean isRecentLogin(LocalDateTime lastLogin) {
    return lastLogin != null 
        && lastLogin.isAfter(LocalDateTime.now().minusDays(30));
}
```

### 原则 2: 避免副作用

Lambda 表达式应该是纯函数，不应修改外部状态。

```java
// ❌ 不推荐：修改外部变量
List<String> errors = new ArrayList<>();
users.forEach(user -> {
    if (!isValid(user)) {
        errors.add("Invalid user: " + user.getId()); // 副作用
    }
});

// ✅ 推荐：使用 Stream 返回新集合
List<String> errors = users.stream()
    .filter(user -> !isValid(user))
    .map(user -> "Invalid user: " + user.getId())
    .toList();
```

### 原则 3: 类型推断

编译器能推断的类型无需显式声明。

```java
// ❌ 不推荐：显式类型声明
users.stream()
    .map((User user) -> user.getName())
    .collect(Collectors.toList());

// ✅ 推荐：类型推断
users.stream()
    .map(user -> user.getName())
    .toList();

// ✅ 更好：方法引用
users.stream()
    .map(User::getName)
    .toList();
```

---

## 方法引用优先

### 静态方法引用

```java
// ❌ 不推荐
list.stream().map(s -> Integer.parseInt(s))

// ✅ 推荐
list.stream().map(Integer::parseInt)
```

### 实例方法引用

```java
// ❌ 不推荐
users.stream().map(user -> user.getName())

// ✅ 推荐
users.stream().map(User::getName)
```

### 构造器引用

```java
// ❌ 不推荐
Stream.of(1, 2, 3).map(i -> new User(i))

// ✅ 推荐
Stream.of(1, 2, 3).map(User::new)
```

### 项目实际案例

**案例 1: Repository 转换**

```java
// apex-infrastructure/persistence/repository/UserRepositoryImpl.java

// ✅ 推荐：方法引用
@Override
public Mono<User> findById(Long id) {
    return Mono.fromCallable(() -> userMapper.selectById(id))
        .subscribeOn(BlockingIoSchedulers.jdbc())
        .map(userPoConverter::toEntity);  // 方法引用
}
```

**案例 2: 批量转换**

```java
// apex-application/service/UserService.java

// ✅ 推荐：方法引用链
public Mono<List<UserDTO>> listUsers() {
    return userRepository.findAll()
        .map(users -> users.stream()
            .map(userAssembler::toDTO)  // 方法引用
            .toList());
}
```

---

## Stream API 最佳实践

### 使用 toList() 替代 collect(Collectors.toList())

**Java 16+ 提供了更简洁的写法**

```java
// ❌ 旧写法
List<String> names = users.stream()
    .map(User::getName)
    .collect(Collectors.toList());

// ✅ 新写法（Java 16+）
List<String> names = users.stream()
    .map(User::getName)
    .toList();
```

### filter + map 链式操作

```java
// ✅ 项目实际案例：CacheServiceImpl.getBatch
return Mono.fromCallable(() -> keys.stream()
    .filter(key -> key != null)              // 过滤 null 键
    .map(key -> {
        try {
            String value = getBucketValue(key);
            return value == null ? null : deserialize(value, clazz);
        } catch (Exception e) {
            log.warn("Failed to get cache for key: {}", key, e);
            return null;
        }
    })
    .filter(value -> value != null)          // 过滤 null 值
    .toList())
    .subscribeOn(BlockingIoSchedulers.cache());
```

### Collectors 常用收集器

#### groupingBy 分组

```java
// 按部门分组统计用户数
Map<String, Long> userCountByDept = users.stream()
    .collect(Collectors.groupingBy(
        User::getDeptName,
        Collectors.counting()
    ));

// 按状态分组获取用户列表
Map<UserStatus, List<User>> usersByStatus = users.stream()
    .collect(Collectors.groupingBy(User::getStatus));
```

#### partitioningBy 分区

```java
// 按条件分为两组
Map<Boolean, List<User>> partitioned = users.stream()
    .collect(Collectors.partitioningBy(user -> user.getAge() >= 18));

List<User> adults = partitioned.get(true);
List<User> minors = partitioned.get(false);
```

#### joining 拼接

```java
// 拼接用户名（逗号分隔）
String names = users.stream()
    .map(User::getName)
    .collect(Collectors.joining(", "));

// 带前后缀
String userList = users.stream()
    .map(User::getName)
    .collect(Collectors.joining(", ", "[", "]"));
// 结果: [Alice, Bob, Charlie]
```

### 项目实际案例

**案例 1: 配置验证批处理**

```java
// apex-common/config/ApexBlockingIoProperties.java

@PostConstruct
public void validate() {
    // ✅ 优秀实践：Map.entry + Stream.of + forEach
    Stream.of(
        Map.entry("jdbc", jdbc),
        Map.entry("cache", cache),
        Map.entry("storage", storage),
        Map.entry("event", event),
        Map.entry("remote", remote),
        Map.entry("general", general))
    .forEach(e -> validatePool(e.getKey(), e.getValue()));
}
```

**案例 2: 策略自动注册**

```java
// apex-application/service/login/LoginStrategyProvider.java

@PostConstruct
public void init() {
    // ✅ 优秀实践：forEach 自动注册
    strategies.forEach(strategy -> 
        strategyMap.put(strategy.getLoginType(), strategy));
}
```

---

## Optional 链式操作

### 基本用法

```java
// ❌ 不推荐：传统 null 检查
public String getUserEmail(User user) {
    if (user != null) {
        UserProfile profile = user.getProfile();
        if (profile != null) {
            return profile.getEmail();
        }
    }
    return "unknown@example.com";
}

// ✅ 推荐：Optional 链式操作
public String getUserEmail(User user) {
    return Optional.ofNullable(user)
        .map(User::getProfile)
        .map(UserProfile::getEmail)
        .orElse("unknown@example.com");
}
```

### 响应式版本（Mono）

```java
// ✅ 项目实际案例：响应式 Optional
public Mono<String> getUserEmail(Long userId) {
    return userRepository.findById(userId)
        .map(User::getProfile)
        .map(UserProfile::getEmail)
        .defaultIfEmpty("unknown@example.com");
}
```

### Optional 常用方法

#### map vs flatMap

```java
// map：一对一转换
Optional<String> name = Optional.of(user).map(User::getName);

// flatMap：一对 Optional
Optional<String> email = Optional.of(user)
    .flatMap(u -> Optional.ofNullable(u.getProfile()))
    .flatMap(p -> Optional.ofNullable(p.getEmail()));
```

#### orElse vs orElseGet vs orElseThrow

```java
// orElse：总是执行（即使有值）
String name = Optional.ofNullable(user)
    .map(User::getName)
    .orElse(getDefaultName());  // getDefaultName() 总是被调用

// orElseGet：惰性求值（推荐）
String name = Optional.ofNullable(user)
    .map(User::getName)
    .orElseGet(this::getDefaultName);  // 仅在无值时调用

// orElseThrow：无值时抛异常
User user = Optional.ofNullable(userRepository.findById(id))
    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));
```

#### ifPresent vs ifPresentOrElse

```java
// ifPresent：有值时执行
Optional.ofNullable(user).ifPresent(u -> log.info("Found user: {}", u.getName()));

// ifPresentOrElse：有值/无值分别处理（Java 9+）
Optional.ofNullable(user).ifPresentOrElse(
    u -> log.info("Found user: {}", u.getName()),
    () -> log.warn("User not found")
);
```

---

## 响应式编程（Reactor）

### Mono 常用操作

#### map：一对一转换

```java
// ✅ 项目实际案例
@Override
public Mono<UserDTO> getUserById(Long id) {
    return userRepository.findById(id)
        .map(userAssembler::toDTO);
}
```

#### flatMap：一对 Mono（嵌套异步）

```java
// ✅ 用户 + 角色信息
public Mono<UserWithRoles> getUserWithRoles(Long userId) {
    return userRepository.findById(userId)
        .flatMap(user -> roleRepository.findByUserId(user.getId())
            .map(roles -> new UserWithRoles(user, roles)));
}
```

#### zipWith：合并两个 Mono

```java
// ✅ 并行查询并合并
public Mono<UserDetail> getUserDetail(Long userId) {
    Mono<User> userMono = userRepository.findById(userId);
    Mono<UserProfile> profileMono = profileRepository.findByUserId(userId);
    
    return userMono.zipWith(profileMono)
        .map(tuple -> new UserDetail(tuple.getT1(), tuple.getT2()));
}
```

#### switchIfEmpty：空值降级

```java
// ✅ 项目实际案例
public Mono<DeptDTO> get(Long id) {
    return deptRepository.findById(id)
        .map(this::toDto)
        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND)));
}
```

#### defaultIfEmpty：提供默认值

```java
public Mono<String> getUserEmail(Long userId) {
    return userRepository.findById(userId)
        .map(User::getEmail)
        .defaultIfEmpty("unknown@example.com");
}
```

### Flux 常用操作

#### filter：过滤

```java
public Flux<User> getActiveUsers() {
    return userRepository.findAll()
        .flatMapMany(Flux::fromIterable)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE);
}
```

#### collectList：收集为 List

```java
public Mono<List<UserDTO>> listUsers() {
    return userRepository.findAll()
        .flatMapMany(Flux::fromIterable)
        .map(userAssembler::toDTO)
        .collectList();
}
```

#### concatMap vs flatMap

```java
// concatMap：保持顺序（串行）
Flux.just(1, 2, 3)
    .concatMap(i -> processInOrder(i));

// flatMap：并行（不保证顺序）
Flux.just(1, 2, 3)
    .flatMap(i -> processInParallel(i));
```

### 阻塞 IO 隔离（重要！）

```java
// ✅ 项目标准模式：JDBC 操作必须调度到专用线程池
@Override
public Mono<User> findById(Long id) {
    return Mono.fromCallable(() -> userMapper.selectById(id))
        .subscribeOn(BlockingIoSchedulers.jdbc())  // 关键：隔离到 JDBC 线程池
        .map(userPoConverter::toEntity);
}

// ✅ Redis 操作同理
@Override
public Mono<Boolean> set(String key, String value, Duration ttl) {
    return Mono.fromCallable(() -> {
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(value, ttl);
            return true;
        })
        .subscribeOn(BlockingIoSchedulers.cache());  // 隔离到 Cache 线程池
}
```

**可用线程池**:
- `BlockingIoSchedulers.jdbc()` - MyBatis 查询
- `BlockingIoSchedulers.cache()` - Redis 同步操作
- `BlockingIoSchedulers.storage()` - 文件/对象存储
- `BlockingIoSchedulers.event()` - 领域事件发布
- `BlockingIoSchedulers.remote()` - 同步 HTTP 客户端
- `BlockingIoSchedulers.general()` - 通用阻塞任务

---

## 常见反模式

### 反模式 1: forEach 副作用

```java
// ❌ 错误：修改外部集合
List<String> result = new ArrayList<>();
users.forEach(user -> {
    if (user.getAge() >= 18) {
        result.add(user.getName());  // 副作用！
    }
});

// ✅ 正确：使用 Stream
List<String> result = users.stream()
    .filter(user -> user.getAge() >= 18)
    .map(User::getName)
    .toList();
```

### 反模式 2: peek 修改元素

```java
// ❌ 错误：peek 不应修改元素
users.stream()
    .peek(user -> user.setStatus(UserStatus.ACTIVE))  // 不推荐
    .collect(Collectors.toList());

// ✅ 正确：使用 map
users.stream()
    .map(user -> {
        user.setStatus(UserStatus.ACTIVE);
        return user;
    })
    .toList();
```

### 反模式 3: 过早终止操作

```java
// ❌ 错误：findFirst 可能遗漏错误
users.stream()
    .map(this::processUser)  // 假设可能抛异常
    .findFirst();  // 只处理第一个，其他元素被忽略

// ✅ 正确：根据需求选择
// 情况 1：需要处理所有元素
users.stream()
    .map(this::processUser)
    .toList();  // 全部处理

// 情况 2：只需要第一个，但要快速失败
users.stream()
    .limit(1)  // 明确限制
    .map(this::processUser)
    .findFirst();
```

### 反模式 4: 迭代中修改集合

```java
// ❌ 错误：迭代中直接删除
for (Map.Entry<String, LocalRateLimiter> entry : INSTANCES.entrySet()) {
    if (shouldRemove(entry.getValue())) {
        INSTANCES.remove(entry.getKey());  // ConcurrentModificationException！
    }
}

// ✅ 正确：先筛选再删除
List<String> keysToRemove = INSTANCES.entrySet().stream()
    .filter(entry -> shouldRemove(entry.getValue()))
    .map(Map.Entry::getKey)
    .toList();

keysToRemove.forEach(INSTANCES::remove);
```

### 反模式 5: 在响应式链中阻塞

```java
// ❌ 错误：在 event-loop 线程阻塞
@Override
public Mono<User> findById(Long id) {
    User user = userMapper.selectById(id);  // 阻塞调用！
    return Mono.just(user);
}

// ✅ 正确：调度到专用线程池
@Override
public Mono<User> findById(Long id) {
    return Mono.fromCallable(() -> userMapper.selectById(id))
        .subscribeOn(BlockingIoSchedulers.jdbc());
}
```

---

## 性能考量

### 何时使用并行 Stream

```java
// 并行 Stream 适用场景：
// 1. 数据量大（> 1000 条）
// 2. 每个元素处理耗时（> 1ms）
// 3. CPU 密集型任务

// ✅ 适合并行
List<ComplexReport> reports = largeDataset.parallelStream()
    .map(this::generateComplexReport)  // 每条耗时 10ms
    .toList();

// ❌ 不适合并行（overhead 大于收益）
List<String> names = users.parallelStream()
    .map(User::getName)  // 简单属性访问
    .toList();
```

### 避免不必要的装箱拆箱

```java
// ❌ 性能差：装箱拆箱
int sum = numbers.stream()
    .map(i -> i * 2)  // Integer → int → Integer
    .reduce(0, Integer::sum);

// ✅ 性能好：使用原始类型流
int sum = numbers.stream()
    .mapToInt(Integer::intValue)
    .map(i -> i * 2)
    .sum();
```

### 懒加载 vs 立即求值

```java
// Stream 操作分为中间操作（懒加载）和终止操作（立即求值）

// 中间操作（不会立即执行）：
// filter, map, flatMap, peek, sorted, distinct, limit

// 终止操作（触发执行）：
// forEach, collect, toList, count, reduce, findFirst, anyMatch

// ✅ 利用懒加载优化性能
long count = users.stream()
    .filter(user -> user.getAge() >= 18)  // 懒加载
    .map(User::getName)                    // 懒加载
    .limit(100)                            // 懒加载，限制处理数量
    .count();                              // 终止操作，触发执行
```

---

## 项目代码索引

**优秀实践参考**:
- 配置验证批处理: [ApexBlockingIoProperties.java](../../../apex-common/src/main/java/com/apex/scaffold/common/config/ApexBlockingIoProperties.java#L194-L213)
- 策略自动注册: [LoginStrategyProvider.java](../../../apex-system/src/main/java/com/apex/scaffold/application/service/login/LoginStrategyProvider.java)
- 响应式链式操作: [CacheServiceImpl.java](../../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheServiceImpl.java#L142-L167)
- 阻塞 IO 隔离: [SubscriptionRepositoryImpl.java](../../../apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/repository/SubscriptionRepositoryImpl.java)

**待优化代码**:
- 限流器清理: [LocalRateLimiter.java#L246-L260](../../../apex-common/src/main/java/com/apex/scaffold/common/util/ratelimit/LocalRateLimiter.java#L246-L260)
- 部门树构建: [DeptService.java#L137-L151](../../../apex-system/src/main/java/com/apex/scaffold/application/service/DeptService.java)

---

## 快速参考卡片

### Stream 操作速查

| 操作 | 类型 | 用途 | 示例 |
|------|------|------|------|
| `filter` | 中间 | 过滤 | `.filter(user -> user.getAge() >= 18)` |
| `map` | 中间 | 一对一转换 | `.map(User::getName)` |
| `flatMap` | 中间 | 一对多展开 | `.flatMap(dept -> dept.getUsers().stream())` |
| `distinct` | 中间 | 去重 | `.distinct()` |
| `sorted` | 中间 | 排序 | `.sorted(Comparator.comparing(User::getAge))` |
| `limit` | 中间 | 限制数量 | `.limit(10)` |
| `skip` | 中间 | 跳过前 N 个 | `.skip(5)` |
| `peek` | 中间 | 调试/日志 | `.peek(u -> log.info("Processing: {}", u))` |
| `collect` | 终止 | 收集为集合 | `.collect(Collectors.toList())` |
| `toList` | 终止 | 收集为 List（Java 16+）| `.toList()` |
| `forEach` | 终止 | 遍历执行 | `.forEach(System.out::println)` |
| `reduce` | 终止 | 归约聚合 | `.reduce(0, Integer::sum)` |
| `count` | 终止 | 计数 | `.count()` |
| `anyMatch` | 终止 | 任一匹配 | `.anyMatch(user -> user.getAge() >= 18)` |
| `allMatch` | 终止 | 全部匹配 | `.allMatch(user -> user.getAge() >= 18)` |
| `findFirst` | 终止 | 查找第一个 | `.findFirst()` |

### Collectors 速查

| 收集器 | 用途 | 示例 |
|--------|------|------|
| `toList()` | 转为 List | `.collect(Collectors.toList())` |
| `toSet()` | 转为 Set | `.collect(Collectors.toSet())` |
| `toMap()` | 转为 Map | `.collect(Collectors.toMap(User::getId, u -> u))` |
| `joining()` | 字符串拼接 | `.collect(Collectors.joining(", "))` |
| `groupingBy()` | 分组 | `.collect(Collectors.groupingBy(User::getDept))` |
| `partitioningBy()` | 分区 | `.collect(Collectors.partitioningBy(u -> u.getAge() >= 18))` |
| `counting()` | 计数 | `.collect(Collectors.counting())` |
| `summingInt()` | 求和 | `.collect(Collectors.summingInt(User::getAge))` |
| `averagingInt()` | 平均值 | `.collect(Collectors.averagingInt(User::getAge))` |

### Reactor 操作速查

| 操作 | 用途 | 示例 |
|------|------|------|
| `map` | 同步转换 | `.map(User::getName)` |
| `flatMap` | 异步展开 | `.flatMap(id -> userRepo.findById(id))` |
| `filter` | 条件过滤 | `.filter(user -> user.getAge() >= 18)` |
| `zipWith` | 合并两个流 | `userMono.zipWith(profileMono)` |
| `switchIfEmpty` | 空值降级 | `.switchIfEmpty(Mono.error(...))` |
| `defaultIfEmpty` | 默认值 | `.defaultIfEmpty("unknown")` |
| `subscribeOn` | 指定执行线程池 | `.subscribeOn(Schedulers.boundedElastic())` |
| `publishOn` | 切换下游线程池 | `.publishOn(Schedulers.parallel())` |
| `timeout` | 超时控制 | `.timeout(Duration.ofSeconds(5))` |
| `retry` | 重试 | `.retry(3)` |
| `onErrorResume` | 异常降级 | `.onErrorResume(e -> Mono.just(fallback))` |

---

**最后更新**: 2026-09-10  
**维护者**: Apex Team  
**反馈渠道**: #apex-scaffold-dev
