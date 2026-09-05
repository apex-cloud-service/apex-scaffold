# 03 技术架构全景图 - 架构决策记录 (ADR)

> 文档版本: v1.1  
> 最后更新: 2026-09-05  
> 适用版本: apex-scaffold v1.2.0+

## 架构图

![Apex Scaffold 技术架构全景图](../diagrams/03_technical_architecture.svg)

---

## ADR-009: 采用 Spring WebFlux 响应式技术栈

**状态**: ✅ 已采纳  
**决策日期**: 2024-Q3  
**决策人**: 架构团队

### 背景

系统需要支持高并发场景,同时希望更高效地利用服务器资源,降低硬件成本。

### 决策

采用 Spring WebFlux + Project Reactor 响应式技术栈:
- WebFlux 作为 Web 层框架
- Netty 作为底层服务器
- Reactor 作为响应式流实现
- 所有 I/O 操作返回 `Mono<T>` 或 `Flux<T>`

### 理由

**优势**:
- ✅ 高并发场景下资源利用率更高 (固定线程池处理更多请求)
- ✅ 背压支持: Flux/Mono 自动处理背压,防止内存溢出
- ✅ 函数式编程: 链式调用更清晰,组合更灵活
- ✅ 非阻塞 I/O: 减少线程等待时间

**取舍**:
- 放弃了 Spring MVC 的简单易用性
- 选择了 WebFlux 的高性能和资源效率

**对比 Spring MVC**:

| 维度 | Spring MVC | Spring WebFlux |
|------|------------|----------------|
| 线程模型 | 每请求一线程 | 固定线程池 |
| 适用场景 | I/O 密集型 | 高并发场景 |
| 学习曲线 | 低 | 中高 |
| 生态成熟度 | 非常成熟 | 成熟 |
| 最大并发 | 受线程数限制 | 受内存限制 |

### 后果

- ✅ 相同硬件资源下,支持更高并发 (测试数据需实测)
- ✅ 资源利用率提升: 线程等待时间减少
- ⚠️ 学习成本增加: 需要理解响应式编程思维
- ⚠️ 调试难度增加: 异步调用栈不直观

### 验证方式

- 压测对比: 相同硬件下 WebFlux vs MVC 并发能力
- 资源监控: CPU 利用率、线程数、响应时间
- 团队反馈: 开发体验、调试效率

### 重新评估条件

- 团队反馈响应式编程学习成本过高
- 性能提升不明显 (< 20%)
- 调试和排查问题效率明显下降

---

## ADR-010: 采用 MyBatis-Plus 作为 ORM 框架

**状态**: ✅ 已采纳  
**决策日期**: 2024-Q3  
**决策人**: 架构团队

### 背景

需要选择合适的 ORM 框架,平衡开发效率和灵活性。

### 决策

采用 MyBatis-Plus 作为 ORM 框架:
- MyBatis-Plus 作为增强版 MyBatis
- LambdaQueryWrapper 构建查询条件
- 代码生成器快速生成 Mapper/Service
- 通过 `subscribeOn(Schedulers.boundedElastic())` 适配响应式

### 理由

**优势**:
- ✅ Lambda 风格: 类型安全的查询条件构建
- ✅ CRUD 自动生成: BaseMapper 提供基础方法
- ✅ 代码生成: 快速生成 Mapper/Entity/Service
- ✅ 逻辑删除: 软删除自动处理
- ✅ 多租户: TenantLineInnerInterceptor 行级隔离
- ✅ SQL 灵活: 复杂查询可直接写 XML

**对比其他方案**:

| 方案 | 优势 | 劣势 | 决策 |
|------|------|------|------|
| **MyBatis-Plus** | Lambda 查询、代码生成、多租户 | 阻塞 I/O | ✅ 采纳 |
| JPA/Hibernate | 标准规范、缓存完善 | 复杂查询困难 | ❌ 不采纳 |
| jOOQ | 类型安全、SQL DSL | 学习成本高 | ❌ 不采纳 |
| Spring Data R2DBC | 原生响应式 | 生态不成熟 | ❌ 不采纳 |

**取舍**:
- 放弃了 R2DBC 的原生响应式能力
- 选择了 MyBatis-Plus 的生态成熟度和开发效率
- 通过线程池切换适配响应式 (技术债)

### 后果

- ✅ 开发效率提升: Lambda 查询 + 代码生成
- ✅ 多租户支持: 行级隔离开箱即用
- ⚠️ 阻塞 I/O: 需要手动切换线程池
- ⚠️ 技术债: JDBC 阻塞特性与 WebFlux 不完全契合

### 验证方式

- 代码审查: 确保所有 Mapper 调用都切换了线程池
- 性能测试: 验证线程切换开销可接受
- 开发反馈: 查询构建和代码生成效率

### 迁移路径

如果未来 R2DBC 生态成熟,可迁移至原生响应式:
```java
// 当前方案 (MyBatis-Plus + 线程切换)
Mono.fromCallable(() -> mapper.selectList(null))
    .subscribeOn(Schedulers.boundedElastic())

// 未来方案 (R2DBC 原生响应式)
databaseClient.sql("SELECT * FROM user")
    .fetch()
    .all()
```

---

## ADR-011: 采用 Vue 3 + TypeScript + Vite 前端技术栈

**状态**: ✅ 已采纳  
**决策日期**: 2024-Q3  
**决策人**: 架构团队

### 背景

前端需要支持复杂的后台管理界面,要求开发效率高、类型安全、构建速度快。

### 决策

采用 Vue 3 + TypeScript + Vite 技术栈:
- Vue 3.5 Composition API
- TypeScript 5.7 strict 模式
- Vite 6 作为构建工具
- Element Plus 2.9 作为 UI 组件库
- Pinia 2.3 作为状态管理

### 理由

**Vue 3 优势**:
- ✅ Composition API: 逻辑复用更清晰
- ✅ `<script setup>`: 减少样板代码
- ✅ 性能提升: 相比 Vue 2 渲染速度提升 1.5-2x
- ✅ TypeScript 支持: 更好的类型推导

**TypeScript 优势**:
- ✅ 类型安全: 编译时错误检查
- ✅ IDE 提示: 更好的开发体验
- ✅ 重构友好: 类型变更自动提示

**Vite 优势**:
- ✅ 冷启动快: < 1s (相比 Webpack 10-30s)
- ✅ HMR 快: < 100ms (相比 Webpack 1-5s)
- ✅ 生产构建: Rolldown 引擎,速度快

**对比其他方案**:

| 方案 | 优势 | 劣势 | 决策 |
|------|------|------|------|
| **Vue 3 + Vite** | 开发体验好、构建快 | 生态相对 React 小 | ✅ 采纳 |
| React + Next.js | 生态最大、SSR 成熟 | 学习曲线陡峭 | ❌ 不采纳 |
| Angular | 完整方案、企业级 | 过于重量级 | ❌ 不采纳 |
| Svelte | 编译时优化、性能好 | 生态不成熟 | ❌ 不采纳 |

**取舍**:
- 放弃了 React 的生态规模
- 选择了 Vue 3 的开发体验和学习曲线

### 后果

- ✅ 开发效率高: HMR < 100ms,类型提示完善
- ✅ 代码质量: TypeScript strict 模式减少运行时错误
- ✅ 构建速度快: 生产构建时间缩短 50-70%
- ⚠️ 生态相对小: 部分库需要自己封装

### 验证方式

- 构建速度: 冷启动 < 2s, HMR < 200ms
- 类型覆盖率: > 95%
- 开发反馈: 开发体验评分

---

## ADR-012: 采用 Caffeine + Redis 二级缓存架构

**状态**: ✅ 已采纳  
**决策日期**: 2024-Q3  
**决策人**: 架构团队

### 背景

系统需要缓存热点数据,降低数据库压力,同时支持分布式部署。

### 决策

采用 Caffeine (L1 堆内) + Redis (L2 分布式) 二级缓存:
- L1 Caffeine: 堆内缓存,最大 10000 条,TTL 5 分钟
- L2 Redis: 分布式缓存,TTL 可配置
- 更新策略: Cache-Aside Pattern (先删缓存再更新 DB)

### 理由

**Caffeine 优势**:
- ✅ 极低延迟: < 1ms (相比 Redis < 5ms)
- ✅ 高命中率: W-TinyLFU 算法
- ✅ 零网络开销: 本地堆内

**Redis 优势**:
- ✅ 分布式共享: 多实例间共享缓存
- ✅ 持久化: AOF + RDB 双重保障
- ✅ 过期策略: 自动清理过期数据

**对比单级缓存**:

| 方案 | 延迟 | 命中率 | 分布式 | 决策 |
|------|------|--------|--------|------|
| 仅 Caffeine | < 1ms | 中 | ❌ | ❌ 不采纳 |
| 仅 Redis | < 5ms | 高 | ✅ | ❌ 不采纳 |
| **Caffeine + Redis** | < 1ms | 高 | ✅ | ✅ 采纳 |

**取舍**:
- 放弃了"单级缓存"的简单性
- 选择了"二级缓存"的性能和分布式能力

### 后果

- ✅ 性能提升: 热点数据延迟 < 1ms
- ✅ 数据库压力降低: 缓存命中率 > 80%
- ⚠️ 一致性问题: Cache-Aside 模式可能短暂不一致
- ⚠️ 复杂度增加: 需要管理两级缓存

### 验证方式

- 缓存命中率: Caffeine > 60%, Redis > 80%
- 数据库 QPS: 降低 > 70%
- P99 延迟: 降低 > 50%

### 一致性保障

**强一致性场景** (不适用缓存):
- 金融交易、库存扣减等

**最终一致性场景** (可用缓存):
- 用户信息、部门树、字典数据等

**更新策略**:
```java
// 写操作: 先删缓存,再更新 DB
@CacheEvict(value = "users", key = "#id")
public Mono<Void> updateUser(Long id, UserDTO dto) {
    return userRepository.update(id, dto);
}

// 读操作: 先查缓存,再查 DB,再写缓存
@Cacheable(value = "users", key = "#id")
public Mono<UserVO> getUser(Long id) {
    return userRepository.findById(id);
}
```

---

## ADR-013: 采用 Maven Profile 管理多版本能力

**状态**: ✅ 已采纳  
**决策日期**: 2024-Q4  
**决策人**: 架构团队

### 背景

系统需要支持不同客户的不同能力需求,避免所有客户都承担完整依赖成本。

### 决策

通过 Maven Profile 管理三个版本:
- **basic**: 核心功能 (Spring Boot + MySQL + Caffeine)
- **standard**: 标准版 (basic + Redis + MinIO + 监控)
- **enterprise**: 企业版 (standard + RocketMQ + Nacos + Sentinel)

### 理由

**优势**:
- ✅ 按需加载: 避免不必要的依赖
- ✅ 成本分级: 不同版本对应不同价格
- ✅ 灵活组合: 客户可按需选择

**取舍**:
- 放弃了"统一依赖"的简单性
- 选择了"灵活配置"的商业价值

### 后果

- ✅ 依赖精简: basic 版依赖减少 40%
- ✅ 启动速度: basic 版启动时间减少 30%
- ⚠️ 测试成本: 需要测试三个版本
- ⚠️ 文档维护: 需要明确各版本能力差异

### 验证方式

- 依赖分析: `mvn dependency:tree -P basic/standard/enterprise`
- 启动时间: 分别测量三个版本启动时间
- 功能验证: 确保各版本功能正常

---

## ADR-014: 端口分离与管理端点保护

**状态**: ✅ 已采纳  
**决策日期**: 2024-Q4  
**决策人**: 架构团队

### 背景

生产环境需要保护 `/actuator/*` 管理端点,防止外部访问泄露系统信息。

### 决策

采用端口分离策略:
- 业务端口: `8080` (对外)
- 管理端口: `8081` (仅内网)

配置:
```yaml
server:
  port: 8080

management:
  server:
    port: 8081
    address: 127.0.0.1  # Compose 默认
    # address: 0.0.0.0  # K8s 改为允许 Service 访问
```

### 理由

**优势**:
- ✅ 安全隔离: 管理端点不对外暴露
- ✅ 监控独立: Prometheus 通过内网抓取
- ✅ 性能隔离: 管理流量不影响业务

**取舍**:
- 放弃了"单端口"的简单性
- 选择了"安全优先"

### 后果

- ✅ 安全性提升: 外部无法访问敏感端点
- ⚠️ 配置复杂度: K8s Service 需要暴露两个端口

---

## ADR-015: 响应式与阻塞 I/O 隔离策略

**状态**: ✅ 已采纳  
**决策日期**: 2024-Q3  
**决策人**: 架构团队

### 背景

WebFlux 响应式框架与 MyBatis-Plus 阻塞 I/O 需要明确的隔离策略。

### 决策

所有阻塞 I/O 操作必须通过 `subscribeOn(Schedulers.boundedElastic())` 切换线程池:

```java
@Override
public Flux<DeptEntity> findAll() {
    return Mono.fromCallable(() -> deptMapper.selectList(null))
        .flatMapIterable(Function.identity())
        .subscribeOn(Schedulers.boundedElastic());  // 切换到阻塞线程池
}
```

### 理由

**优势**:
- ✅ 防止阻塞 Netty 线程: 保护事件循环
- ✅ 明确边界: 阻塞操作与响应式操作隔离
- ✅ ArchUnit 验证: 通过测试固化规则

**取舍**:
- 放弃了"完全响应式"的理想状态
- 选择了"生态成熟度"优先

### 后果

- ✅ Netty 线程不被阻塞: 保证响应式性能
- ⚠️ 线程切换开销: 每次调用增加 < 1ms 开销
- ⚠️ 代码约定: 需要团队遵守规范

### 验证方式

- ArchUnit 测试: 检查所有 Mapper 调用是否切换线程
- 性能测试: 验证线程切换开销可接受
- Code Review: 人工审查关键路径

---

## 重新评估条件

以下情况需要重新评估技术选型:

### 触发条件

1. **响应式编程成本过高**:
   - 团队反馈学习成本 > 3 个月
   - 调试效率明显下降
   - 可考虑回退到 Spring MVC

2. **R2DBC 生态成熟**:
   - MyBatis-Plus 支持 R2DBC
   - 或 R2DBC 生态完善
   - 可迁移至原生响应式

3. **前端技术栈重大变化**:
   - Vue 4 发布
   - 或 React 优势明显扩大
   - 重新评估技术选型

4. **缓存架构优化**:
   - 一致性问题频繁
   - 或性能提升不明显
   - 考虑引入 Canal 等工具

### 重新评估内容

- **是否继续使用 WebFlux**: 评估性能提升 vs 学习成本
- **是否迁移到 R2DBC**: 评估生态成熟度
- **是否优化缓存一致性**: 评估 Canal 等工具

---

## 相关文档

- **详细说明**: [03 技术架构全景图 - 详细说明](03-technical-architecture-detail.md)
- **快速参考**: [03 技术架构全景图 - 快速参考](03-technical-architecture-quick-ref.md)
- **商业价值**: [03 技术架构全景图 - 商业价值](03-technical-architecture-business-value.md)
- **逻辑架构决策**: [01 DDD 四层逻辑架构 - 架构决策](01-logical-architecture-decision.md)
- **部署架构决策**: [02 网络物理部署架构 - 架构决策](02-physical-architecture-decision.md)

---

**下一步**: 阅读 [商业价值文档](03-technical-architecture-business-value.md) 了解技术选型的业务价值。
