# SVG图表与文档代码对齐方案

> **创建时间**: 2026-09-05  
> **目标**: 确保所有SVG图表和技术文档与项目实际代码实现完全一致  
> **范围**: 29张SVG图表 + 70+篇技术文档

---

## 📋 执行摘要

基于对Apex Scaffold项目的深度调研，发现部分SVG图表和文档内容与实际代码实现存在差异。本方案将系统性地审查和更新所有架构图表及配套文档，确保100%准确反映当前项目实现状态。

---

## 🔍 当前状态分析

### 项目实际实现情况（v1.2.0）

**核心技术栈**：
- **后端**: Java 17 + Spring Boot 3.5.15 + Spring WebFlux 6.x
- **持久化**: MyBatis-Plus 3.5.10.1 + MySQL 8.4 + Druid 1.2.25
- **缓存**: Redis 7.x (Redisson 3.36.0) + Caffeine 3.1.8
- **搜索**: Meilisearch 0.17.1（轻量级全文搜索）
- **消息队列**: RocketMQ 5.x（可选，企业版）
- **微服务治理**: Nacos 2.x + Sentinel 1.8.x（可选）
- **监控**: Prometheus + Grafana (66个面板) + Zipkin + Loki + Alloy
- **前端**: Vue 3.5.13 + Vite 6.0.7 + TypeScript 5.x + Element Plus 2.x + Pinia 2.3

**架构模式**：
- DDD四层架构（接口层、应用层、领域层、基础设施层）
- 响应式编程（Project Reactor + WebFlux）
- 函数式路由（RouterFunction）
- 二级缓存（L1 Caffeine + L2 Redis）
- 读写分离（主从复制）

**部署方案**：
- Docker Compose（默认）
- Kubernetes（可选）
- Profile分级：basic / standard（默认）/ enterprise

**数据库**：
- 43张核心表
- 22个Flyway迁移脚本

### 已知的不一致问题

#### 1. 技术版本不一致

**问题文件**: `03_technical_architecture.svg`、`19_backend_technology_stack.svg`、`22_frontend_technology_stack.svg`

**不一致点**：
- 图表显示的版本号可能与pom.xml、package.json实际版本不符
- 某些依赖库可能已更新但图表未同步

**影响**: 新人参考文档时可能使用错误的版本

#### 2. 技术组件缺失或冗余

**问题文件**: `03_technical_architecture.svg`、`21_technology_ecosystem.svg`

**潜在问题**：
- Meilisearch（全文搜索）是否在图表中体现
- Loki + Alloy（日志聚合）是否在监控架构中体现
- Volcengine TOS（火山云对象存储）是否体现
- 创蓝云短信、百度AI审核等三方集成是否体现

#### 3. 架构层次不准确

**问题文件**: `01_logical_architecture.svg`

**潜在问题**：
- 四层职责是否准确反映代码模块划分
- apex-common、apex-generator、apex-example等模块是否体现
- RouterFunction函数式路由是否体现（非传统Controller）

#### 4. 业务流程过时

**问题文件**: `04_login_auth_flow.svg`、`05_permission_check_flow.svg`、`06_workflow_approval_flow.svg`

**潜在问题**：
- JWT双Token机制（Access + Refresh）是否准确
- RBAC权限模型（5级数据范围）是否准确
- 工作流引擎实现细节是否准确

#### 5. 部署架构不完整

**问题文件**: `13_deployment_architecture.svg`、`02_physical_architecture.svg`

**潜在问题**：
- Docker Compose默认栈vs K8s可选部署是否区分
- Profile分级（basic/standard/enterprise）是否体现
- 端口分离（8080业务端口 + 8081管理端口）是否体现

#### 6. 监控架构细节缺失

**问题文件**: `17_monitoring_architecture.svg`、`24_grafana_dashboard_architecture.svg`、`25_prometheus_monitoring_architecture.svg`

**潜在问题**：
- Loki + Alloy日志聚合方案是否体现（替代传统ELK）
- 66个Grafana面板的11个分组是否准确
- Actuator端点配置是否准确

#### 7. 数据库表结构过时

**问题文件**: `08_database_er_diagram.svg`

**潜在问题**：
- 是否包含所有43张核心表
- 多租户字段（tenant_id）是否体现
- Flyway迁移历史是否反映最新表结构

#### 8. 文档描述不准确

**问题文件**: 多个配套Markdown文档

**潜在问题**：
- 版本号不一致
- 配置参数过时
- 最佳实践不适用当前版本

---

## 🎯 对齐目标

### 准确性目标

1. **版本一致性**: 所有技术组件版本号与pom.xml、package.json 100%一致
2. **组件完整性**: 所有实际使用的技术组件都在图表中体现
3. **架构准确性**: 架构层次、模块划分、调用关系与代码实现完全一致
4. **流程准确性**: 业务流程图与实际代码逻辑完全一致
5. **配置准确性**: 配置参数、端口号、环境变量与实际配置完全一致

### 质量目标

1. **可验证性**: 每个技术声明都可通过代码验证
2. **可维护性**: 建立文档更新机制，代码变更时同步更新文档
3. **可读性**: 图表清晰、文档结构合理、易于理解
4. **完整性**: 覆盖所有核心功能模块和技术组件

---

## 📝 详细执行计划

### Phase 1: 代码实现深度审查（只读）

**目标**: 建立完整的"实际情况清单"，作为对齐的基准

#### 1.1 技术栈审查

**任务**：
- [ ] 读取并解析 `pom.xml`，提取所有依赖及版本号
- [ ] 读取并解析前端 `package.json`（如果有），提取所有依赖及版本号
- [ ] 读取 `application.yml` / `application.properties`，提取配置参数
- [ ] 读取 `docker-compose.yml`，提取服务组件和端口配置
- [ ] 生成"技术栈清单.md"（包含所有依赖及版本）

**产出**：
- `reports/tech-stack-inventory.md` - 技术栈完整清单
- `reports/version-comparison.md` - 版本对比报告

#### 1.2 架构模块审查

**任务**：
- [ ] 分析 `apex-interfaces`、`apex-application`、`apex-domain`、`apex-infrastructure` 四层模块结构
- [ ] 分析 `apex-common`、`apex-generator`、`apex-example` 等辅助模块
- [ ] 审查RouterFunction路由配置 vs 传统Controller
- [ ] 审查依赖关系（哪些模块依赖哪些模块）
- [ ] 生成"架构模块清单.md"

**产出**：
- `reports/module-structure-inventory.md` - 模块结构清单

#### 1.3 数据库结构审查

**任务**：
- [ ] 读取所有Flyway迁移脚本（V1__至V22__）
- [ ] 提取完整表结构（43张表）
- [ ] 分析表关系（主外键、索引）
- [ ] 提取多租户字段、数据权限字段
- [ ] 生成"数据库结构清单.md"

**产出**：
- `reports/database-schema-inventory.md` - 数据库结构清单

#### 1.4 业务流程审查

**任务**：
- [ ] 审查JWT认证流程实现代码
- [ ] 审查RBAC权限校验实现代码
- [ ] 审查工作流引擎实现代码（如果有）
- [ ] 审查文件上传实现代码
- [ ] 生成"业务流程清单.md"

**产出**：
- `reports/business-flow-inventory.md` - 业务流程清单

#### 1.5 部署配置审查

**任务**：
- [ ] 审查Docker Compose配置
- [ ] 审查Kubernetes配置（如果有）
- [ ] 审查Profile配置（basic/standard/enterprise）
- [ ] 审查端口配置、网络配置
- [ ] 生成"部署配置清单.md"

**产出**：
- `reports/deployment-config-inventory.md` - 部署配置清单

#### 1.6 监控配置审查

**任务**：
- [ ] 审查Prometheus配置
- [ ] 审查Grafana面板配置（如果有）
- [ ] 审查Loki/Alloy日志配置
- [ ] 审查Actuator端点配置
- [ ] 生成"监控配置清单.md"

**产出**：
- `reports/monitoring-config-inventory.md` - 监控配置清单

---

### Phase 2: 差异分析与对比

**目标**: 对比图表/文档与实际代码，生成差异清单

#### 2.1 SVG图表差异分析

**任务**：
- [ ] 逐个审查29张SVG图表
- [ ] 对比图表内容与Phase 1的清单
- [ ] 标记不一致的地方（版本号、组件名称、架构关系等）
- [ ] 生成"SVG图表差异报告.md"

**产出**：
- `reports/svg-diagrams-gap-analysis.md` - SVG差异报告

**审查清单**：

| 序号 | 文件名 | 审查重点 | 预估差异程度 |
|------|--------|---------|------------|
| 01 | 01_logical_architecture.svg | 四层模块划分、RouterFunction | 中 |
| 02 | 02_physical_architecture.svg | Docker Compose配置、端口 | 低 |
| 03 | 03_technical_architecture.svg | 技术栈版本号、组件名称 | 高 |
| 04 | 04_login_auth_flow.svg | JWT双Token流程 | 中 |
| 05 | 05_permission_check_flow.svg | RBAC权限模型、数据权限 | 中 |
| 06 | 06_workflow_approval_flow.svg | 工作流实现细节 | 中 |
| 07 | 07_file_upload_flow.svg | 文件上传流程 | 低 |
| 08 | 08_database_er_diagram.svg | 43张表、多租户字段 | 高 |
| 09 | 09_sentinel_rate_limit_circuit_breaker.svg | Sentinel配置 | 低 |
| 10 | 10_multi_level_cache_architecture.svg | Caffeine + Redis配置 | 低 |
| 11 | 11_auto_retry_strategy_flow.svg | 重试策略实现 | 低 |
| 12 | 12_security_protection_system.svg | 安全机制 | 中 |
| 13 | 13_deployment_architecture.svg | K8s/Docker部署 | 中 |
| 14 | 14_network_architecture.svg | 网络拓扑 | 低 |
| 15 | 15_data_flow_architecture.svg | 数据流向 | 中 |
| 16 | 16_mq_architecture.svg | RocketMQ配置 | 中 |
| 17 | 17_monitoring_architecture.svg | Prometheus/Grafana/Loki | 中 |
| 18 | 18_microservice_governance.svg | Nacos/Sentinel治理 | 中 |
| 19 | 19_backend_technology_stack.svg | 后端技术栈版本 | 高 |
| 20 | 20_infrastructure_components.svg | 基础设施组件 | 中 |
| 21 | 21_technology_ecosystem.svg | 60+技术组件全景 | 高 |
| 22 | 22_frontend_technology_stack.svg | 前端技术栈版本 | 高 |
| 23 | 23_frontend_backend_interaction.svg | 前后端交互流程 | 中 |
| 24 | 24_grafana_dashboard_architecture.svg | 66个面板分组 | 中 |
| 25 | 25_prometheus_monitoring_architecture.svg | 监控指标 | 中 |
| 26 | 26_disaster_recovery_flow.svg | 容灾流程 | 低 |
| 27 | 27_cicd_pipeline_detail.svg | CI/CD流水线 | 低 |
| 28 | 28_api_gateway_detail.svg | API网关 | 低 |
| 29 | 29_security_threat_model.svg | 安全威胁模型 | 低 |

#### 2.2 文档差异分析

**任务**：
- [ ] 审查70+篇技术文档
- [ ] 对比文档内容与Phase 1的清单
- [ ] 标记不一致的地方
- [ ] 生成"文档差异报告.md"

**产出**：
- `reports/docs-gap-analysis.md` - 文档差异报告

**重点审查文档**：
- 架构设计文档（12篇）
- 技术栈文档（3篇）
- 业务流程文档（1篇）
- 部署运维文档（5篇）
- 开发规范文档（3篇）

---

### Phase 3: 更新SVG图表

**目标**: 根据差异报告更新所有SVG图表

#### 3.1 高优先级图表更新

**顺序**：按差异程度和重要性排序

1. **03_technical_architecture.svg** - 技术栈全景
   - 更新所有技术组件版本号
   - 添加缺失组件（Meilisearch、Loki、Alloy、Volcengine TOS等）
   - 验证所有组件名称准确

2. **08_database_er_diagram.svg** - 数据库ER图
   - 确保包含所有43张表
   - 添加多租户字段标注
   - 更新表关系

3. **19_backend_technology_stack.svg** - 后端技术栈
   - 更新所有版本号
   - 确保6层架构准确

4. **21_technology_ecosystem.svg** - 技术生态全景
   - 更新60+技术组件
   - 确保分类准确

5. **22_frontend_technology_stack.svg** - 前端技术栈
   - 更新所有版本号
   - 确保30+依赖准确

#### 3.2 中优先级图表更新

6. **01_logical_architecture.svg** - DDD四层架构
   - 更新模块划分
   - 标注RouterFunction路由方式

7-17. 其他中优先级图表（见上表）

#### 3.3 低优先级图表更新

18-29. 低优先级图表（见上表）

---

### Phase 4: 更新技术文档

**目标**: 根据差异报告更新所有技术文档

#### 4.1 架构文档更新

**文件列表**：
- `architecture/system-architecture.md`
- `architecture/01-logical-architecture-detail.md`
- `architecture/01-logical-architecture-quick-ref.md`
- `architecture/02-physical-architecture-detail.md`
- `architecture/03-technical-architecture-detail.md`
- 等12篇架构文档

**更新内容**：
- 版本号
- 配置参数
- 架构说明
- 技术选型理由

#### 4.2 技术栈文档更新

**文件列表**：
- `architecture/backend-technology-stack.md`
- `architecture/frontend-technology-stack.md`
- `architecture/infrastructure-components.md`

**更新内容**：
- 完整技术清单
- 版本号
- 依赖关系

#### 4.3 业务流程文档更新

**文件列表**：
- `architecture/business-flows.md`

**更新内容**：
- JWT认证流程
- RBAC权限流程
- 工作流流程
- 文件上传流程

#### 4.4 部署运维文档更新

**文件列表**：
- `architecture/deployment-architecture.md`
- `operations/deployment.md`
- `operations/configuration.md`
- `operations/monitoring.md`
- `operations/monitoring-dashboard-configuration.md`

**更新内容**：
- 部署配置
- 环境变量
- 端口配置
- 监控配置

#### 4.5 开发规范文档更新

**文件列表**：
- `standards/backend-development-standards.md`
- `standards/frontend-development-standards.md`
- `api/route-mapping.md`

**更新内容**：
- 开发规范
- API路由

---

### Phase 5: 同步更新索引文档

**目标**: 更新ARCHITECTURE_INDEX.md，确保索引准确

**任务**：
- [ ] 更新文档版本号
- [ ] 更新图表清单
- [ ] 更新文档清单
- [ ] 更新最后更新时间
- [ ] 添加本次更新记录

---

### Phase 6: 质量验证

**目标**: 确保所有更新准确无误

#### 6.1 自动化验证

**任务**：
- [ ] XML格式验证（xmllint所有SVG）
- [ ] 版本号验证（与pom.xml、package.json对比）
- [ ] 链接验证（文档内部链接）
- [ ] 图表引用验证（文档引用的图表是否存在）

**脚本**：
- `scripts/verify-svg-format.sh` - SVG格式验证
- `scripts/verify-versions.sh` - 版本号验证
- `scripts/verify-links.sh` - 链接验证

#### 6.2 人工审查

**任务**：
- [ ] 抽查10张图表内容准确性
- [ ] 抽查10篇文档内容准确性
- [ ] 交叉验证图表与文档一致性

#### 6.3 集成测试

**任务**：
- [ ] 在浏览器中打开所有SVG，确保正常显示
- [ ] 在Markdown阅读器中打开所有文档，确保格式正确
- [ ] 验证所有超链接可点击

---

## 📊 资源评估

### 工作量评估

| 阶段 | 任务数 | 预估时间 | 复杂度 |
|------|--------|---------|--------|
| Phase 1: 代码审查 | 6大项 | 2-3小时 | 中 |
| Phase 2: 差异分析 | 2大项 | 1-2小时 | 低 |
| Phase 3: 更新SVG | 29个文件 | 4-6小时 | 高 |
| Phase 4: 更新文档 | 70+个文件 | 6-8小时 | 高 |
| Phase 5: 同步索引 | 1个文件 | 0.5小时 | 低 |
| Phase 6: 质量验证 | 3大项 | 1-2小时 | 中 |
| **总计** | - | **14-21小时** | - |

### 人员需求

- **技术文档工程师**: 1人（主要）
- **架构师**: 1人（审查）
- **后端开发**: 1人（技术验证）
- **前端开发**: 1人（前端部分验证）

### 工具需求

- **文本编辑器**: VS Code / IntelliJ IDEA
- **SVG编辑器**: VS Code + SVG插件
- **验证工具**: xmllint、shellcheck、markdownlint
- **版本控制**: Git

---

## 🎯 成功标准

### 必须达成（Must Have）

1. ✅ 所有技术组件版本号与代码100%一致
2. ✅ 所有SVG图表XML格式验证通过
3. ✅ 所有文档Markdown格式验证通过
4. ✅ 关键架构图（01、03、08、19、22）准确性100%
5. ✅ 索引文档完整更新

### 应该达成（Should Have）

1. ✅ 所有29张SVG图表内容准确
2. ✅ 所有70+篇文档内容准确
3. ✅ 建立文档更新机制（代码变更触发文档更新）
4. ✅ 建立自动化验证脚本

### 可以达成（Could Have）

1. ⭐ 添加图表版本号标注
2. ⭐ 添加文档最后审查日期
3. ⭐ 建立CI/CD自动验证流程
4. ⭐ 生成PDF版本文档

---

## 🚀 执行建议

### 分批执行方案

考虑到工作量较大，建议分3批执行：

**第一批（高优先级）**：
- Phase 1: 代码审查
- Phase 2: 差异分析
- Phase 3.1: 更新5张高优先级SVG
- Phase 4.1-4.2: 更新架构和技术栈文档
- Phase 6: 质量验证（第一批）

**第二批（中优先级）**：
- Phase 3.2: 更新12张中优先级SVG
- Phase 4.3-4.4: 更新业务和部署文档

**第三批（低优先级）**：
- Phase 3.3: 更新12张低优先级SVG
- Phase 4.5: 更新开发规范文档
- Phase 5: 同步索引文档
- Phase 6: 最终质量验证

### 风险控制

**风险1**: 代码实现复杂，难以快速理解
- **缓解**: 先审查配置文件和README，建立整体认知
- **备选**: 请项目核心开发者协助审查

**风险2**: 差异过多，工作量超预期
- **缓解**: 优先处理高优先级图表和文档
- **备选**: 采用分批执行方案

**风险3**: 更新后图表可读性下降
- **缓解**: 保持图表简洁，不过度展示细节
- **备选**: 创建详细版和简化版两套图表

---

## 📝 附录

### A. 技术栈清单模板

```markdown
# 技术栈清单

## 后端技术栈

| 技术 | 版本 | 用途 | 配置文件 |
|------|------|------|---------|
| Java | 17 | 语言 | pom.xml |
| Spring Boot | 3.5.15 | 框架 | pom.xml |
| ... | ... | ... | ... |

## 前端技术栈

| 技术 | 版本 | 用途 | 配置文件 |
|------|------|------|---------|
| Vue | 3.5.13 | 框架 | package.json |
| ... | ... | ... | ... |
```

### B. 差异报告模板

```markdown
# SVG图表差异报告

## 03_technical_architecture.svg

### 发现的差异

1. **版本号不一致**
   - 图表显示: Spring Boot 3.4.0
   - 实际版本: Spring Boot 3.5.15
   - 影响: 用户可能使用错误版本

2. **缺失组件**
   - 图表缺失: Meilisearch 0.17.1
   - 实际使用: 是
   - 影响: 用户不知道项目使用全文搜索

### 修复建议

- [ ] 更新Spring Boot版本号为3.5.15
- [ ] 添加Meilisearch组件
```

### C. 验证脚本示例

```bash
#!/bin/bash
# verify-svg-format.sh

echo "验证SVG格式..."
for file in docs/diagrams/*.svg; do
  if xmllint --noout "$file" 2>&1; then
    echo "✅ $file"
  else
    echo "❌ $file - 格式错误"
  fi
done
```

---

**方案制定者**: TRAE AI Assistant  
**审核状态**: 待人工审核  
**预计开始时间**: 2026-09-05  
**预计完成时间**: 2026-09-12
