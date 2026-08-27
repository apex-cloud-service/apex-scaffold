# Apex Scaffold 商业化改进任务清单

**基于**: [商业化差距分析报告](COMMERCIAL_GAP_ANALYSIS.md)  
**增量校准**: 商业化改造增量分析（已归档删除）（2026-08-25 校准，文档已归档删除）
**目标版本**: v1.1.0  
**预计完成时间**: 2026-10-25  
**最后更新**: 2026-08-25（状态同步校准）

> **校准说明**: 本清单原始状态为 2026-08-25 生成时的"待开始"。同日后续工作已实际完成大部分任务（License 套件落地、@FeatureGate 示例、测试代码完整拷入等），本次按当前代码状态统一校准。证据与新增缺口详见 商业化改造增量分析（已归档删除）。

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
- [x] @AuditLog注解
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
- `config/monitoring/docker-compose.yml`

---

### Task 2.4: 商业License验证机制
**负责人**: 资深开发  
**工作量**: 5天  
**状态**: ⚠️ 部分完成（核心代码已落地；商业化加固待办见下）

**验收标准**:
- [x] License文件格式定义(JSON/加密)
- [x] LicenseValidator验证类
- [x] 签名验证(RSA)
- [x] 过期检测
- [x] 机器指纹绑定
- [x] License生成工具
- [x] 功能开关控制
- [ ] **加固项 A**: 三个商业 Profile（basic/standard/enterprise）的 `features.*` 属性实质化——当前为"空声明"，未驱动任何依赖裁剪或代码开关，三种版本编译产物相同
- [ ] **加固项 B**: License 默认启用策略——当前 `apex.license.enabled` 无默认配置，License 验证器默认不初始化，fork 用户可绕过授权；需补充默认公钥/license 模板并明确 FeatureGate 未启用时的行为

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/license/`（LicenseValidator / LicenseGenerator / LicenseAutoConfiguration / LicenseProperties / RsaUtil / FeatureGateAspect 等）
- `apex-common/src/main/java/com/apex/scaffold/common/annotation/FeatureGate.java`
- `apex-example/src/main/java/com/apex/example/handler/ExampleUserHandler.java`（@FeatureGate 示例接口 premium_report）
- `pom.xml`（basic/standard/enterprise Profile）
- `docs/LICENSE_MANAGEMENT.md`

---

## 🟢 P2 - 中等任务(2个月内完成)

### Task 3.1: 集成测试补充
**负责人**: QA工程师  
**工作量**: 3天  
**状态**: ⚠️ 部分完成（593 个测试文件已从 原社交业务项目 完整拷入，但存在失败需修复回归）

**验收标准**:
- [x] 认证授权测试
- [x] 缓存测试
- [x] 消息队列测试
- [x] 异常场景测试
- [x] 并发测试
- [ ] 覆盖率>80%
- [ ] **回归项 A**: 修复 `mvn test` 失败——JsonUtil 默认类型激活破坏标准 JSON（6 失败 + 3 错误，详见增量分析缺陷 1）
- [ ] **回归项 B**: 清理 5 个配置文件 `com.apex` 残留（logback-spring.xml / application-prod.yml / application-performance.yml / application-it.yml / logback-test.xml）
- [ ] **回归项 C**: 修复后全量重跑单元测试并继续集成测试（failsafe/verify）回归

**相关文件**:
- `apex-common/src/test/java/com/apex/scaffold/common/util/JsonUtilTest.java`
- 各模块 `src/test/java`（共 593 个测试文件）
- `apex-interfaces/src/test/resources/`（application-it.yml / schema-it.sql / data-it.sql 等）

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
**状态**: ⏳ 待开始（仍为空白）

**相关文件**:
- `apex-common/src/main/java/com/apex/scaffold/common/tenant/TenantContext.java` (新建)
- MyBatis拦截器自动注入tenant_id

---

### Task 4.3: 灰度发布配置
**负责人**: DevOps  
**工作量**: 2天  
**状态**: ⏳ 待开始（仍为空白）

**相关文件**:
- `k8s/canary-deployment.yaml` (新建)

---

### Task 4.4: 备份恢复脚本
**负责人**: DBA  
**工作量**: 1天  
**状态**: ⏳ 待开始（仍为空白）

**相关文件**:
- `scripts/backup/db-backup.sh` (新建)
- `scripts/backup/db-restore.sh` (新建)

---

## 📊 进度跟踪

| 阶段 | 任务数 | 已完成 | 部分完成 | 完成率 | 预计完成日期 |
|------|--------|--------|---------|--------|-------------|
| P0   | 4      | 4      | 0       | 100%   | 2026-09-08（已达成） |
| P1   | 4      | 3      | 1       | 75%    | 2026-09-25 |
| P2   | 3      | 2      | 1       | 67%    | 2026-10-25 |
| P3   | 4      | 1      | 0       | 25%    | 按需        |
| **总计** | **15** | **10** | **2**   | **67%** | **-**       |

> 注：2 个"部分完成"任务（Task 2.4 License 加固、Task 3.1 测试回归）涉及 P0 缺陷修复，建议优先处理。

---

## 🎯 里程碑

- **M1 (2026-09-08)**: P0任务完成,v1.1.0-alpha发布 — ✅ 已达成（4/4）
- **M2 (2026-09-25)**: P1任务完成,v1.1.0-beta发布 — ⏳ 剩余 Task 2.4 加固项
- **M3 (2026-10-25)**: P2任务完成,v1.1.0正式发布 — ⏳ 剩余 Task 3.1 回归

---

## 📞 协调机制

- **每日站会**: 9:30 AM,同步进度和阻塞问题
- **每周评审**: 周五下午,Review代码和质量
- **Slack频道**: #apex-scaffold-dev
- **看板**: https://github.com/orgs/apex-scaffold/projects/1

---

**最后更新**: 2026-08-25（状态同步校准）  
**维护者**: Apex Team
