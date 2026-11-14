# 契约模块测试报告 — apex-api-contract（Phase 1.3）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 报告日期：2026-10-08 ｜ 执行环境：macOS（Java 17.0.12-oracle + Maven Daemon 2.0.0-rc-3）
> 所属阶段：微服务改造执行计划 Phase 1.3（`docs/08-project-management/plans/microservice-migration-plan.md` §6.2 六个内部契约 / §6.3 Feign 模板与降级矩阵 / §17 验收）

## 一、测试概览

| 项目 | 结果 |
|------|------|
| 模块编译（`mvnd -pl :apex-api-contract -am verify`） | ✅ BUILD SUCCESS |
| 单元测试（surefire） | ✅ 20/20 通过（0 失败 / 0 错误 / 0 跳过） |
| Spotless 格式检查（spotless:check） | ✅ 通过（exit=0） |
| PMD 静态检查（p3c-pmd-check） | ✅ 通过（0 违规，本轮已修复 1 处遗留） |
| JaCoCo 覆盖率门禁（BUNDLE） | ✅ LINE 89.6%（≥60%）｜ BRANCH 100.0%（≥50%） |

## 二、覆盖率指标（JaCoCo）

| 指标 | 覆盖率 | 门禁阈值 | 结果 |
|------|-------|---------|------|
| 行覆盖率 LINE | 43/48 = **89.6%** | ≥ 60% | ✅ |
| 分支覆盖率 BRANCH | 4/4 = **100.0%** | ≥ 50% | ✅ |
| 指令覆盖率 INSTRUCTION | 232/247 = **93.9%** | —（参考） | ✅ |

## 三、测试范围与结果

### 3.1 契约签名（FeignContractSignatureTest）

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| FeignContractSignatureTest | 6 | ✅ | 反射校验 Auth/System/Workflow 客户端路径前缀 `/internal/v1/`、HTTP 注解、`Result<T>` 返回形状；DTO 序列化（`Serializable`）契约；`UserSnapshotDTO` / `DeptTreeDTO` / `DictItemDTO` / `TokenValidationDTO` |

### 3.2 降级工厂（FeignClientFallbackFactoryTest）

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| FeignClientFallbackFactoryTest | 5 | ✅ | Auth/System/Workflow 三客户端 fallback 均返回 `Result.error(SERVICE_UNAVAILABLE)`，语义码=3007，`getData()==null`；拒绝优先策略外部行为 |

### 3.3 租户/追踪头透传（FeignTenantTraceConfigurationTest）

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| FeignTenantTraceConfigurationTest | 5 | ✅ | tenant 已设置注入 `X-Tenant-Id`、未设置不注入；已显式声明同名头不覆盖；自动生成 `X-Trace-Id`、已显式不覆盖 |

### 3.4 响应式桥接（FeignReactiveBridgeTest）

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| FeignReactiveBridgeTest | 4 | ✅ | `bridge` 值透传、异常 → onError、惰性求值（subscriber 前不执行）、null supplier 抛 NPE |
| `.bridgeResult` | — | ✅ | 复用 `bridge` 语义，覆盖 `Result<T>` 返回形状 |

**合计：4 个测试类共 20 个用例，全部通过。**

## 四、事故披露与恢复记录

本轮在推进契约模块全门禁过程中，先后定位并修复 2 类问题，均在本地闭环：

| # | 现象 | 根因 | 修复 |
|---|------|------|------|
| 1 | 全 reactor（`-am`）PMD `p3c-pmd-check` 报 1 处违规：`DeptTreeDTO:52 PojoNoDefaultValueRule` | `DeptTreeDTO.children` 使用 `@Builder.Default private List<> = new ArrayList<>()` 字段默认值，违背 Alibaba POJO 规范（Pojo 字段不应带默认值） | 移除 `@Builder.Default` 与字段初始值，改为显式无参构造器内 `this.children = new ArrayList<>()`，保留空列表默认语义且满足 PMD 与反序列化 |
| 2 | （学习记录）Feign 13.6 `RequestTemplate` **无** `create(HttpMethod,String)` 静态工厂 | 仅存在无参构造器 + `.method()` / `.uri()` fluent 方法 | 测试工具方法改为 `new RequestTemplate().method(GET).uri("/internal/v1/users/10086")` |

## 五、预存问题（建议后续关注）

| 问题 | 归因 | 建议 |
|------|------|------|
| 契约接口仅以反射校验签名，未对「提供方实现」做一致性校验 | apex-api-contract 只承载契约（接口 + DTO），提供方实现在各业务服务模块 | Phase 2 各业务服务接入时，在服务侧补提供方实现与契约的路径/返回形状契约测试 |
| `FeignReactiveBridge` 依赖 `BlockingIoSchedulers.remote()` 静态调度器 | 调度器池为固定 16 线程/1024 队列，属共享资源 | Phase 2 消费方接入后按峰值压测复核队列饱和下的拒绝与降级表现 |

## 六、交付物清单

**新增（本次 Phase 1.3）**：
- `client/AuthFeignClient.java`、`SystemFeignClient.java`、`WorkflowFeignClient.java`（六个内部 API 契约）
- `client/*FeignClientFallbackFactory.java`（Auth/System/Workflow 统一降级工厂）
- `config/FeignTenantTraceConfiguration.java`、`config/FeignClientConstants.java`（租户/追踪头注入 + 服务名常量）
- `dto/internal/*.java`（UserSnapshotDTO/DeptTreeDTO/DictItemDTO/InstanceStatusDTO/TokenValidationDTO/TokenValidationRequest）
- `support/FeignReactiveBridge.java`（同步转响应式桥接，`subscribeOn(BlockingIoSchedulers.remote())`）
- `pom.xml`：新增 `reactor-test`（test scope，供 StepVerifier 断言）
- 4 个测试类共 20 用例

**本轮修复**：
- `dto/internal/DeptTreeDTO.java`：`@Builder.Default` 字段默认值 → 无参构造器初始化（PMD PojoNoDefaultValueRule）

## 七、测试执行命令

```bash
# 单模块门禁（依赖 + 单测 + 覆盖率 + 格式 + 静态检查）
mvnd -pl :apex-api-contract -am verify
# 覆盖率报告
#   位置：apex-api-contract/target/site/jacoco/index.html
```