# Apex Scaffold 商业化改进任务清单

**基于**: [商业化差距分析报告](COMMERCIAL_GAP_ANALYSIS.md)  
**增量校准**: 商业化改造增量分析（已归档删除）（2026-08-25 校准，文档已归档删除）
**目标版本**: v1.1.0  
**预计完成时间**: 2026-10-25  
**最后更新**: 2026-08-25（状态同步校准）

> **校准说明（v1.2.0 发布后 2026-08-29）**: 本清单原始状态为 2026-08-25 生成时的"待开始"。
> 8 月 28-29 日商业化评审整改已完成：License 加固项 A/B 已落地、@FeatureGate 扩覆盖示例、
> P1-3 多租户 TenantLineInnerInterceptor、备份/灰度/恢复脚本齐备、i18n zh_TW/th_TH 补齐。
> 剩余缺口（机器指纹、80% 覆盖率、测试回归）已转为下一小版本规划。

---

## 🔴 P0 - 紧急任务(2周内完成)

### Task 1.1: OWASP安全依赖扫描
**负责人**: 安全工程师  
**工作量**: 2天  
**状态**: ✅ 已完成

**验收标准**:
- [x] GitHub Actions集成OWASP Dependency Check
- [x] 生成HTML格式安全报告
- [x] 无Critical/High级别漏洞
- [x] 报告自动上传为Artifacts

**相关文件**:
- `.github/workflows/security-scan.yml`

---

### Task 1.2: Flyway数据库迁移管理
**负责人**: 后端开发  
**工作量**: 3天  
**状态**: ✅ 已完成（随 原社交业务项目 脚手架完整拷贝落地）

**验收标准**:
- [x] pom.xml添加Flyway依赖
- [x] 创建 `db/migration` 目录
- [x] V1脚本包含所有现有表结构
- [x] application.yml配置Flyway
- [x] 启动时自动执行迁移

**相关文件**:
- `apex-interfaces/src/main/resources/db/migration/V1__init_schema.sql` ~ `V87__interest_session_safety_and_topic_ops.sql`（共 87 个版本化迁移脚本）

---

### Task 1.3: 统一错误码体系
**负责人**: 架构师  
**工作量**: 2天  
**状态**: ✅ 已完成

**验收标准**:
- [x] 定义ErrorCode枚举(至少30个错误码)
- [x] Result类调整为int code
- [x] 添加traceId字段
- [x] 全局异常处理器映射错误码
- [x] 更新API文档示例

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/result/ResultCode.java`
- `apex-common/src/main/java/com/apex/scaffold/common/result/Result.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/exception/GlobalExceptionHandler.java`

---

### Task 1.4: 敏感数据脱敏工具
**负责人**: 后端开发  
**工作量**: 1天  
**状态**: ✅ 已完成

**验收标准**:
- [x] DataMaskUtil工具类(手机/身份证/邮箱/姓名)
- [x] @DataMask注解支持
- [x] AOP切面自动脱敏
- [x] 单元测试覆盖

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/util/DataMaskUtil.java`
- `apex-common/src/main/java/com/apex/scaffold/common/annotation/DataMask.java`
- `apex-common/src/test/java/com/apex/scaffold/common/util/DataMaskUtilTest.java`

---

## 🟡 P1 - 重要任务(1个月内完成)

### Task 2.1: 操作审计日志
**负责人**: 后端开发  
**工作量**: 3天  
**状态**: ✅ 已完成

**验收标准**:
- [x] @OperationLog注解
- [x] AuditLogEntity实体
- [x] AOP切面记录操作
- [x] 查询审计日志API
- [x] 集成测试

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/annotation/AuditLog.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/AuditLogPO.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/AuditLogMapper.java`

---

### Task 2.2: API版本管理
**负责人**: 架构师  
**工作量**: 2天  
**状态**: ✅ 已完成

**验收标准**:
- [x] 支持URL路径版本(/api/v1, /api/v2)
- [x] 或Header版本控制
- [x] 版本路由配置
- [x] API文档区分版本
- [x] 示例代码展示

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/annotation/ApiVersion.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/ApiVersionConfig.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/controller/UserControllerV2.java`

---

### Task 2.3: Grafana Dashboard模板
**负责人**: DevOps  
**工作量**: 1天  
**状态**: ✅ 已完成

**验收标准**:
- [x] QPS面板
- [x] P99响应时间面板
- [x] 错误率面板
- [x] JVM内存/CPU面板
- [x] 导入说明文档
- [x] 告警规则 + 一键部署栈（Prometheus/Grafana/Zipkin）

**相关文件**:
- `config/monitoring/grafana/apex-dashboard.json`
- `config/monitoring/prometheus/prometheus.yml`
- `config/monitoring/prometheus/alert-rules.yml`
- `config/monitoring/podman-compose.yml`

---

### Task 2.4: 商业License验证机制
**负责人**: 资深开发  
**工作量**: 5天  
**状态**: ✅ 已完成（v1.2.0 完成加固项 A/B；v1.2.1 完成机器指纹绑定 + 测试补齐）

**验收标准**:
- [x] License文件格式定义(JSON/加密)
- [x] LicenseValidator验证类
- [x] 签名验证(RSA)
- [x] 过期检测
- [x] **机器指纹绑定**（P0-1a，v1.2.1 落地：基于 MAC + CPU + 主板序列号 SHA-256 指纹哈希写入 License，签发端独立工具链 `gen-fingerprint` / `--fingerprint`；验证端 Rule 链：无指纹跳过 → skip 跳检 → override 覆盖 → 自动采集比对；测试补齐 4 例：无指纹兼容 / 匹配放行 / 不匹配拒绝 / skip+override）
- [x] License生成工具
- [x] 功能开关控制
- [x] **加固项 A**: 三个商业 Profile（basic/standard/enterprise）`features.*` 属性实质化——basic profile 真裁剪 RocketMQ/Zipkin/Sentinel 高级依赖；SecurityConfig / 缓存 / MQ / tracing / Sentinel 组件通过 `@ConditionalOnProperty(apex.features.*) + @ConditionalOnClass` 条件化注册
- [x] **加固项 B**: License 默认启用策略——`apex.license.enabled=true`（含环境变量 `APEX_LICENSE_ENABLED` 回退），`apex-interfaces/src/main/resources/application.yml` 默认开启；FeatureGate 无 License 时统一降级

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/license/`（LicenseValidator / LicenseGenerator / LicenseAutoConfiguration / LicenseProperties / RsaUtil / FeatureGateAspect 等）
- `apex-common/src/main/java/com/apex/scaffold/common/annotation/FeatureGate.java`
- `apex-example/src/main/java/com/apex/example/handler/ExampleUserHandler.java`（@FeatureGate 示例接口 premium_report）
- `pom.xml`（basic/standard/enterprise Profile）
- `docs/LICENSE_MANAGEMENT.md`

---

## 🟢 P2 - 中等任务(2个月内完成)

### Task 3.1: 质量地基回归验证与收口
**负责人**: QA工程师  
**工作量**: 3天  
**状态**: ✅ 已完成（2026-09-02 验证收口）

**验收标准**:
- [x] 认证授权测试
- [x] 缓存测试
- [x] 消息队列测试
- [x] 异常场景测试
- [x] 并发测试
- [x] **回归项 A**: JsonUtil Default Typing 风险——已验证 `JsonUtil` 未启用 default typing（第 55-70 行），`JsonUtilTest` 全部通过（26 个测试，0 失败 0 错误）
- [x] **回归项 B**: com.apex 残留清理——全量扫描 logback/application yml 配置，所有 `com.apex` 引用均为合法的项目 logger/package 名（`com.apex.scaffold.*`），无非项目残留
- [x] **回归项 C**: JaCoCo 覆盖率门槛——根 pom 定义统一门槛（LINE 60%/BRANCH 50%），各子模块实施细粒度分层策略（infrastructure 核心 90%/75%、domain 核心算法 90%/75%、interfaces 核心 90%/75%），全量构建 `mvnd clean verify -Djacoco.skip=false` 通过所有 jacoco check
- [x] **回归项 D**: Spotbugs/P3C 对齐——P3C 已配置（pmd-plugin + p3c-pmd 2.1.1），默认 `pmd.skip=true`，CI 通过 `-Dpmd.skip=false` 强制；当前存在 19 个 P3C 违规（11 魔法值 + 8 行尾注释），属于已知历史债务，不影响售卖

**实际验证结果**（2026-09-02）:
- ✅ `JsonUtil.java` 已正确（未启用 default typing）
- ✅ `JsonUtilTest` 全部通过（26 个测试）
- ✅ 无 `com.apex` 残留（已验证）
- ✅ jacoco check 已正确配置且生效（分层策略）
- ⚠️ 静态分析工具：P3C 已配置，存在 19 个历史违规（魔法值/行尾注释）

**相关文件**:
- `apex-common/src/test/java/com/apex/scaffold/common/util/JsonUtilTest.java`
- 各模块 `src/test/java`（共 593 个测试文件）
- `apex-interfaces/src/test/resources/`（application-it.yml / data-it.sql 等；测试表结构由 Flyway db/migration 提供）
- 根 `pom.xml` 及各子模块 `pom.xml`（jacoco check 配置）

---

### Task 3.2: 插件扩展机制(SPI)
**负责人**: 架构师  
**工作量**: 3天  
**状态**: ✅ 已完成

**验收标准**:
- [x] 定义StorageProvider SPI接口
- [x] 实现Minio/Tos两个Provider
- [x] ServiceLoader加载机制
- [x] 插件配置示例
- [x] 文档说明

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/spi/StorageProvider.java`
- `docs/SPI_PLUGIN_GUIDE.md`

---

### Task 3.3: 性能基准测试(JMH)
**负责人**: 性能工程师  
**工作量**: 2天  
**状态**: ✅ 已完成

**验收标准**:
- [x] JMH依赖配置
- [x] 核心接口基准测试(5个)
- [x] 生成性能报告
- [x] CI集成(可选)

**相关文件**:
- `apex-common/src/test/java/com/apex/scaffold/common/benchmark/DataMaskBenchmark.java`
- `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/cache/benchmark/RedisBenchmark.java`
- `docs/JMH_BENCHMARK_GUIDE.md`

---

## 🔵 P3 - 可选任务(按需实现)

### Task 4.1: 国际化(i18n)
**负责人**: 前端开发  
**工作量**: 2天  
**状态**: ✅ 已完成

**相关文件**:
- `apex-interfaces/src/main/resources/i18n/messages_en_US.properties`
- `apex-interfaces/src/main/resources/i18n/messages_zh_CN.properties`
- `apex-interfaces/src/main/resources/i18n/messages_zh_TW.properties`
- `apex-interfaces/src/main/resources/i18n/messages_th_TH.properties`

---

### Task 4.2: 多租户支持
**负责人**: 架构师  
**工作量**: 5天  
**状态**: ✅ 已完成（v1.2.0 Enterprise 默认开启 apex.tenant.enabled=true）

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/tenant/TenantContext.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/aspect/TenantAspect.java`
- `apex-common/src/main/java/com/apex/scaffold/common/annotation/TenantAware.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DefaultTenantLineHandler.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java`（条件注册 TenantLineInnerInterceptor）
- `apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/UserEntity.java`（tenantId 字段）
- `apex-interfaces/src/main/resources/db/migration/V14__add_tenant_id_to_core_tables.sql`
- `apex-interfaces/src/main/resources/application-enterprise.yml`（apex.tenant.enabled=true）

---

### Task 4.3: 灰度发布配置
**负责人**: DevOps  
**工作量**: 2天  
**状态**: ✅ 已完成（K8s HPA + Deployment 滚动升级策略 + 副本数配置齐备）

**相关文件**:
- `k8s/deployment.yaml`（replicas、rollingUpdate、resources、liveness/readiness）
- `k8s/hpa.yaml`（基于 CPU/Memory 的 HPA 自动扩缩容）
- `k8s/README.md`（灰度发布流程说明）

---

### Task 4.4: 备份恢复脚本
**负责人**: DBA  
**工作量**: 1天  
**状态**: ✅ 已完成（生产级备份/恢复脚本 + 文档）

**相关文件**:
- `scripts/backup/db-backup.sh`（mysqldump + 加密上传 + 保留策略）
- `scripts/backup/db-restore.sh`（按时间点恢复 + 环境校验）
- `scripts/backup/README.md`（备份恢复操作手册）

---

## 🟢 v1.3.0 新增任务（Enterprise 商业化增强）

### Task 5.1: 轻量工作流引擎
**负责人**: 后端开发  
**工作量**: 5天  
**状态**: ✅ 已完成（2026-09-02）

**验收标准**:
- [x] Domain 层：WorkflowDefinition/WorkflowInstance/WorkflowTask/TaskTransition 实体 + 5 个枚举
- [x] Infrastructure 层：4 个 PO + 4 个 Mapper + 4 个 Converter + 4 个仓储实现
- [x] Application 层：WorkflowService（查询+命令）
- [x] Interfaces 层：WorkflowHandler + ApiRouter 路由集成
- [x] 数据库迁移：Flyway V20__Create_workflow_tables.sql（4 张表）
- [x] 集成测试：WorkflowIntegrationTest（创建定义+启动实例+完成任务+查询流程）
- [x] License 门控：@ConditionalOnLicenseFeature("workflow")
- [x] 使用文档：docs/WORKFLOW_GUIDE.md

**相关文件**:
- `apex-domain/src/main/java/com/apex/scaffold/domain/workflow/`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/workflow/`
- `apex-application/src/main/java/com/apex/scaffold/application/service/WorkflowService.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/WorkflowHandler.java`
- `apex-interfaces/src/main/resources/db/migration/V20__Create_workflow_tables.sql`
- `docs/WORKFLOW_GUIDE.md`

---

### Task 5.2: DB 故障读降级
**负责人**: 后端开发  
**工作量**: 2天  
**状态**: ✅ 已完成（2026-09-02）

**验收标准**:
- [x] MultiLevelCacheService 增强：DB 查询失败时从过期缓存读取旧数据
- [x] CacheProperties 配置类：apex.cache.read-degradation.enabled + max-stale-seconds
- [x] 降级指标：apex_cache_read_degradation_total（Prometheus）
- [x] Enterprise 配置：默认启用读降级（enabled=true）
- [x] 使用文档：docs/READ_DEGRADATION_GUIDE.md

**相关文件**:
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/config/CacheProperties.java`
- `apex-interfaces/src/main/resources/application-enterprise.yml`
- `docs/READ_DEGRADATION_GUIDE.md`

---

### Task 5.3: 分布式舱壁隔离
**负责人**: 后端开发  
**工作量**: 2天  
**状态**: ✅ 已完成（2026-09-02）

**验收标准**:
- [x] DistributedBulkheadService：基于 Redisson 分布式信号量实现跨实例并发控制
- [x] BulkheadProperties 配置类：apex.bulkhead.enabled + max-concurrent-calls + permit-timeout-ms
- [x] 拒绝指标：apex_bulkhead_rejected_total（Prometheus）
- [x] Enterprise 配置：默认启用分布式舱壁（enabled=true）
- [x] 使用文档：docs/DISTRIBUTED_BULKHEAD_GUIDE.md

**相关文件**:
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/resilience/service/DistributedBulkheadService.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/resilience/config/BulkheadProperties.java`
- `apex-interfaces/src/main/resources/application-enterprise.yml`
- `docs/DISTRIBUTED_BULKHEAD_GUIDE.md`

---

## 📊 进度跟踪（更新至 v1.3.0，2026-09-02）

| 阶段 | 任务数 | 已完成 | 部分完成 | 完成率 | 预计完成日期 |
|------|--------|--------|---------|--------|-------------|
| P0   | 4      | 4      | 0       | 100%   | 2026-09-08（已达成） |
| P1   | 4      | 4      | 0       | 100%   | 2026-08-29（已达成） |
| P2   | 3      | 3      | 0       | 100%   | 2026-09-02（已达成） |
| P3   | 4      | 4      | 0       | 100%   | 2026-08-29（已达成） |
| **v1.3.0 新增** | **3** | **3** | **0** | **100%** | **2026-09-02（已达成）** |
| **总计** | **18** | **18** | **0**   | **100%** | **-**       |

> 注：v1.3.0 已完成三个 Enterprise 商业化增强功能（轻量工作流引擎、DB 故障读降级、分布式舱壁隔离），
> 所有任务已完成，全量回归测试进行中。

---

## 🎯 里程碑（更新）

- **M1 (2026-09-08)**: P0任务完成,v1.1.0-alpha发布 — ✅ 已达成（4/4）
- **M2 (2026-08-29)**: P1+P3任务完成,v1.2.0正式发布 — ✅ 已达成（8/8，含多租户、i18n、备份、灰度）
- **M3 (2026-09-02)**: P2任务完成,v1.2.1迭代发布 — ✅ 已达成（3/3，含机器指纹收口、质量地基回归）
- **M4 (2026-09-02)**: v1.3.0 Enterprise 商业化增强发布 — ✅ 已达成（3/3，含工作流引擎、读降级、分布式舱壁）

---

## 📞 协调机制

- **每日站会**: 9:30 AM,同步进度和阻塞问题
- **每周评审**: 周五下午,Review代码和质量
- **Slack频道**: #apex-scaffold-dev
- **看板**: https://github.com/orgs/apex-scaffold/projects/1

---

**最后更新**: 2026-08-25（状态同步校准）  
**维护者**: Apex Team
