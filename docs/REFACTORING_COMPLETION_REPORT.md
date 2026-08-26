# Apex Scaffold v2.0 通用化重构完成报告

> **编制日期**: 2026-08-26
> **执行依据**: [COMMERCIAL_REFACTORING_PLAN.md](COMMERCIAL_REFACTORING_PLAN.md)
> **完成范围**: 阶段 A（工程基线修复与品牌统一）+ 阶段 B（去业务化核心重构）

---

## 一、总体结论

**阶段 A + B 全部完成，项目已具备对外售卖的基本条件**：

| 验收门禁 | 状态 |
|---|---|
| 全库 grep `bias`/`Bias`/`BIAS` 零命中（排除 .git） | ✅ 339 文件 → 0 |
| `mvn test-compile` 全模块编译通过 | ✅ |
| 单元测试全绿 | ✅ 1,786 个测试（5 模块） |
| 数据库迁移为干净基线 | ✅ 87 个旧脚本 → V1（27 张通用表） |
| git 版本管理 | ✅ main 分支，2 个基线提交 |
| 管理后台无业务残留 | ✅ 品牌改造为 Apex Scaffold |
| 缓存/指标前缀统一 | ✅ `apex:` / `apex.api.*` |

---

## 二、阶段 A 完成明细（工程基线修复与品牌统一）

### A1. git 初始化与工程规范
- ✅ `.gitignore`（target/logs/密钥/License 文件不入库）
- ✅ `.gitattributes`（统一 LF 换行）
- ✅ `git init -b main`，基线提交 `chore: init scaffold v1.0.0 baseline`

### A2. 清理构建产物与遗留脚本
- ✅ 删除全部 5 个模块 `target/` 目录
- ✅ 删除拷贝脚本（copy_*.py、fix-author.sh）
- ✅ 删除内部修复报告 3 篇、rebel.xml
- ✅ 清理 231 个文件中的 `@author anqli` 残留

### A3. JsonUtil 反序列化缺陷
- ✅ 早前已修复（移除全局 default typing）

### A4-A5. 通用代码与配置 bias 消除
- ✅ `CacheConstants` 前缀 `bias:` → `apex:`
- ✅ `ApiCallLogService` 指标 `bias.api.call` → `apex.api.call`
- ✅ 删除 `BiasAlgorithmProperties`、`EyesightProperties` 业务配置类
- ✅ 5 个配置文件 `com.bias` → `com.apex.scaffold`
- ✅ 配置前缀统一：`bias.security/sentinel/scheduling/blocking-io/degrade` → `apex.*`
- ✅ i18n、admin 静态资源、pom.xml、bootstrap.yml、spy.properties 全部清理

---

## 三、阶段 B 完成明细（去业务化核心重构）

### B1. apex-domain 业务剥离（408 → 96 个文件）
- ✅ 删除 `interest/` 全目录（100+ 文件，含 governance/channel/session/reveal/connection/universe/antiabuse）
- ✅ 删除 battle/elo/vote/photo 域、face 分析域、resonance/radar/chat 域
- ✅ 删除 eyesight/future-partner/personal-bias/aesthetic/recommendation 域
- ✅ 删除业务 enums/events/exceptions/gateways/services/repositories/entities/valueobjects

### B2. apex-application 业务剥离（398 → 77 个文件）
- ✅ 删除约 200 个业务 DTO（interest/frequency、Battle/Chat/Resonance/PersonalBias 等）
- ✅ 删除业务 service/listener/config（PhotoBatch、SingleRecommendation、Interest 等）
- ✅ 重写 `AdminUserManagementService`（去除高风险用户处置/偏好模型导出等业务方法）
- ✅ 重写 `ProfileService`（去除 interest 图谱集成，保留通用资料管理）
- ✅ 重建 `UserDefaultsFactory` 及 4 个通用 DTO（SendSms/WxPhone/WechatBindPhone/MobileValidate）

### B3. apex-interfaces 业务剥离（50 → 8 个 Handler）
- ✅ 删除约 40 个业务 Handler（AdminContentModeration、Chat、Explore、FaceAnalysis、VotePhoto 等）
- ✅ 重写 `ApiRouter`（仅注册 auth/user/file/health/RBAC/admin 路由）
- ✅ 重写 `OpenApiDocumentationCustomizer`、`RouterOpenApiConfig`
- ✅ 重写 `FileHandler`（保留通用上传下载，去除照片业务端点）
- ✅ admin 后台品牌改造（BIAS → Apex Scaffold，`bias.admin.*` → `apex.admin.*`）
- ✅ 删除 `db/interest/` 种子数据、`interfaces/tool/` 业务工具

### B4. apex-infrastructure 业务剥离
- ✅ 删除 `FaceAnalysisClient`、PersonalBias/Photo/Vote/Interest 等 PO/Mapper/Repository
- ✅ 重写 `AccountDeletionCleanupMapper`（仅保留 11 个通用表清理方法）
- ✅ 重建 `BusinessMetricsService`（通用 Micrometer 实现，仅 recordCreditChange）

### B5. 数据库迁移重建
- ✅ 删除全部 87 个旧迁移脚本（含 `bias_data` 库名、`@bias_*` 变量、personal_bias 表）
- ✅ 新建 `V1__init_schema.sql`：27 张通用表（RBAC 六件套 + 用户/文件/审计/订阅/信用/举报/黑名单等）
- ✅ 数据库名统一为 `apex_data`，迁移脚本不包含 CREATE DATABASE/USE（由 JDBC URL 提供）
- ✅ `schema-it.sql` 重建为 27 张通用表（H2 兼容），`data-it.sql` 保留通用种子数据
- ✅ `application-prod.yml` 去除业务配置节（vote-photo/recommendation/algorithm），通用配置并入 `apex:` 节

### B6. 文档 bias 清理
- ✅ 删除内部分析文档 `COMMERCIAL_INCREMENTAL_ANALYSIS.md`
- ✅ 重写 `apex-domain/README.md`、`apex-infrastructure/README.md`（零业务残留）
- ✅ 清理重构方案文档中的原项目引用
- ✅ 更新 `CHANGELOG.md`

---

## 四、最终项目结构

```
apex-scaffold/
├── apex-common/            # 58 文件：工具/注解/Result/异常/SPI/License/缓存常量
├── apex-domain/            # 96 文件：通用领域实体/仓储接口/服务端口/事件
├── apex-application/       # 77 文件：认证/用户/RBAC/文件用例 + 通用 DTO
├── apex-infrastructure/    # 206 文件：持久化/缓存/存储/短信/消息/审核实现
├── apex-interfaces/        # 88 文件：8 个通用 Handler + 安全/路由/i18n/admin
├── apex-example/           # 8 文件：纯净示例模块
├── docs/                   # 商业化文档体系
└── db/migration/V1__init_schema.sql  # 27 张通用表干净基线
```

---

## 五、测试验证

| 模块 | 测试数 | 结果 |
|---|---|---|
| apex-common | 438 | ✅ 全绿 |
| apex-domain | 434 | ✅ 全绿 |
| apex-infrastructure | 335 | ✅ 全绿 |
| apex-application | 263 | ✅ 全绿 |
| apex-interfaces | 316 | ✅ 全绿 |
| **合计** | **1,786** | ✅ |

---

## 六、后续路线图

| 阶段 | 内容 | 优先级 |
|---|---|---|
| **C** | 通用系统管理能力（菜单/字典/参数）、管理后台完善、apex-example 增强、备份脚本 | P1-P2 |
| **D** | Maven Profile 实质化（basic/standard/enterprise 差异化）、License 强制启用、双轨许可证 | P1 |
| **E** | 文档体系重构、交付物校验、营销物料、安全复核、v1.1.0 发布 | P2 |

**建议下一步**: 优先执行阶段 D（Profile 差异化 + License 保护）——直接关系商业收入；随后阶段 C（管理后台与示例增强）提升竞争力；最后阶段 E 发布。

---

**编制**: Apex Team
