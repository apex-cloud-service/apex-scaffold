# JavaDoc 补充待办清单

**生成时间**: 2026-09-12 13:44:56  
**扫描路径**: apex-common/src/main/java  
**总体覆盖率**: 12.9%

---

## 统计数据

| 指标 | 数量 | 覆盖率 |
|------|------|--------|
| 类级JavaDoc | 31 个类, 54 个缺失 | -74.2% |
| 方法级JavaDoc | 339 个方法, 0 个缺失 | 100.0% |

---

## 待补充文件清单

### 类级JavaDoc缺失

- [ ] `com/apex/scaffold/common/dto/PageResponse.java`
- [ ] `com/apex/scaffold/common/dto/TokenStoreEntry.java`
- [ ] `com/apex/scaffold/common/dto/PageRequest.java`
- [ ] `com/apex/scaffold/common/result/ResultCode.java`
- [ ] `com/apex/scaffold/common/result/Result.java`
- [ ] `com/apex/scaffold/common/context/DataScopeProvider.java`
- [ ] `com/apex/scaffold/common/context/DataScopeConfig.java`
- [ ] `com/apex/scaffold/common/context/DataScopeContext.java`
- [ ] `com/apex/scaffold/common/context/DataScopeInfo.java`
- [ ] `com/apex/scaffold/common/context/ReactiveDataScopeProvider.java`
- [ ] `com/apex/scaffold/common/util/JwtUtil.java`
- [ ] `com/apex/scaffold/common/util/PasswordUtil.java`
- [ ] `com/apex/scaffold/common/util/HttpStatusUtil.java`
- [ ] `com/apex/scaffold/common/util/ratelimit/LocalRateLimiter.java`
- [ ] `com/apex/scaffold/common/util/DateUtil.java`
- [ ] `com/apex/scaffold/common/util/id/SnowflakeIdGenerator.java`
- [ ] `com/apex/scaffold/common/util/RequestUtil.java`
- [ ] `com/apex/scaffold/common/util/TraceIdUtil.java`
- [ ] `com/apex/scaffold/common/util/FileUrlUtil.java`
- [ ] `com/apex/scaffold/common/config/JwtProperties.java`
- [ ] `com/apex/scaffold/common/config/ApexBlockingIoProperties.java`
- [ ] `com/apex/scaffold/common/config/RedisKeyPrefix.java`
- [ ] `com/apex/scaffold/common/config/RedissonProperties.java`
- [ ] `com/apex/scaffold/common/enums/UserStatus.java`
- [ ] `com/apex/scaffold/common/enums/DataScopeType.java`
- [ ] `com/apex/scaffold/common/enums/Gender.java`
- [ ] `com/apex/scaffold/common/scheduler/BlockingIoSchedulers.java`
- [ ] `com/apex/scaffold/common/annotation/OperationLog.java`
- [ ] `com/apex/scaffold/common/annotation/RequireRole.java`
- [ ] `com/apex/scaffold/common/annotation/IdempotentApi.java`
- [ ] `com/apex/scaffold/common/annotation/OptimisticLockRetry.java`
- [ ] `com/apex/scaffold/common/annotation/MaskType.java`
- [ ] `com/apex/scaffold/common/annotation/DataMask.java`
- [ ] `com/apex/scaffold/common/annotation/ApiVersion.java`
- [ ] `com/apex/scaffold/common/annotation/RequirePermission.java`
- [ ] `com/apex/scaffold/common/annotation/HotSpotRateLimit.java`
- [ ] `com/apex/scaffold/common/annotation/UserRateLimit.java`
- [ ] `com/apex/scaffold/common/annotation/RateLimiter.java`
- [ ] `com/apex/scaffold/common/annotation/ConcurrencyControl.java`
- [ ] `com/apex/scaffold/common/annotation/DataScope.java`
- [ ] `com/apex/scaffold/common/annotation/TenantAware.java`
- [ ] `com/apex/scaffold/common/annotation/Logical.java`
- [ ] `com/apex/scaffold/common/annotation/AggregateRoot.java`
- [ ] `com/apex/scaffold/common/annotation/AutoRetry.java`
- [ ] `com/apex/scaffold/common/annotation/FeatureGate.java`
- [ ] `com/apex/scaffold/common/annotation/AntiReplay.java`
- [ ] `com/apex/scaffold/common/annotation/DataSource.java`
- [ ] `com/apex/scaffold/common/spi/StorageProvider.java`
- [ ] `com/apex/scaffold/common/governance/DrainResult.java`
- [ ] `com/apex/scaffold/common/governance/DrainParticipant.java`
- [ ] `com/apex/scaffold/common/error/ErrorCode.java`
- [ ] `com/apex/scaffold/common/exception/AlertEvent.java`
- [ ] `com/apex/scaffold/common/exception/BaseException.java`
- [ ] `com/apex/scaffold/common/exception/BusinessException.java`

### 方法级JavaDoc缺失 (部分列表)

无缺失

---

## 执行建议

### 按优先级补充

**P0 - 核心接口层** (Handler):
```bash
find apex-interfaces/src/main/java -name "*Handler.java"
```

**P1 - 应用服务层** (Service):
```bash
find apex-application/src/main/java -name "*Service.java"
```

**P2 - 领域层** (Domain Model, Repository):
```bash
find apex-domain/src/main/java -name "*.java"
```

### 使用IDE快捷键

- **IDEA**: 光标放在类/方法上,按 `Ctrl+Shift+D` (Windows/Linux) 或 `⌘⇧D` (Mac)
- **Eclipse**: `Alt+Shift+J`
- **VSCode**: 安装 "Java Extension Pack",使用 `/** + Enter`

### 参考文档

- [JavaDoc编写指南](../docs/JAVADOC_GUIDE.md)
- [代码规范](../docs/standards/)

---

**生成工具**: check_javadoc.sh  
**报告文件**: javadoc_todo_list_20260912_134456.md
