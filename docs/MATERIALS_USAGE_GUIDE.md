# Apex Scaffold 架构素材库使用指南

> **版本**: v1.0  
> **更新日期**: 2026-09-05  
> **素材总览**: 29 张 SVG 图表 + 12 份核心文档 + 15 个架构决策记录

---

## 📦 素材库概览

### 完整资产清单

| 资产类型 | 数量 | 位置 | 用途 |
|---------|------|------|------|
| **SVG 架构图** | 29 张 | `docs/diagrams/*.svg` | 技术交流、PPT 制作、文档配图 |
| **核心架构文档** | 12 份 | `docs/architecture/0[1-3]-*.md` | 详细说明、快速参考、决策记录、商业价值 |
| **架构决策记录** | 15 个 | ADR-001 ~ ADR-015 | 技术评审、决策追溯 |
| **高级特性文档** | 4 份 | `docs/architecture/advanced-features/*.md` | 缓存、安全、Sentinel、重试 |
| **架构索引** | 1 份 | `docs/ARCHITECTURE_INDEX.md` | 快速导航、场景推荐 |

---

## 🎯 快速定制指南

### 场景 1: 客户商业提案 PPT（20-30 分钟）

**推荐结构**（15-20 页）：

1. **封面** - 项目名称 + 核心定位
2. **项目概览** - 四大核心价值（高性能、高可用、低成本、可观测）
3. **技术架构全景** - 使用 `03_technical_architecture.svg`
4. **DDD 四层架构** - 使用 `01_logical_architecture.svg`
5. **部署架构** - 使用 `02_physical_architecture.svg`
6. **核心特性 1** - 多级缓存架构（`09_multi_level_cache_architecture.svg`）
7. **核心特性 2** - 安全防护体系（`29_security_threat_model.svg`）
8. **核心特性 3** - 容灾与高可用（`26_disaster_recovery_flow.svg`）
9. **商业价值 1** - 开发效率提升（来自 `01-logical-architecture-business-value.md`）
10. **商业价值 2** - 成本优化（来自 `02-physical-architecture-business-value.md`）
11. **商业价值 3** - ROI 分析（首年 419%，第二年 2256%）
12. **技术指标** - 性能、可用性、成本对比
13. **实施路线图** - 5 个阶段演进
14. **成功案例** - 3 个典型场景
15. **Q&A** - 常见问题解答

**素材来源**：
- **图表**: `01`, `02`, `03`, `10`, `26`, `29` 号 SVG
- **商业价值数据**: `0[1-3]-*-business-value.md`
- **ROI 分析**: `01-logical-architecture-business-value.md` 第 8 节

---

### 场景 2: 技术分享 PPT（30-40 分钟）

**推荐结构**（25-30 页）：

1. **封面 + 议程**
2. **架构演进历史** - 从单体到 DDD
3. **DDD 四层架构详解** - 使用 `01_logical_architecture.svg` + detail 文档
4. **技术栈全景** - 使用 `03`, `19`, `22` 号图
5. **响应式编程** - WebFlux 核心概念
6. **数据库设计** - 使用 `08_database_er_diagram.svg`
7. **多级缓存实现** - 使用 `10` 号图 + 代码示例
8. **Sentinel 流控熔断** - 使用 `09` 号图 + 配置说明
9. **安全防护实践** - 使用 `12`, `29` 号图
10. **容灾切换流程** - 使用 `26` 号图
11. **CI/CD 流水线** - 使用 `27` 号图
12. **监控告警体系** - 使用 `17`, `24`, `25` 号图
13. **架构决策回顾** - ADR-001 ~ ADR-015 精选
14. **踩坑与最佳实践**
15. **Q&A**

**素材来源**：
- **图表**: 全部 29 张 SVG
- **技术细节**: `0[1-3]-*-detail.md`
- **架构决策**: `0[1-3]-*-decision.md`

---

### 场景 3: 架构评审文档（1 小时）

**推荐内容**：

1. **架构概述** - 使用 `ARCHITECTURE_INDEX.md` 前置部分
2. **逻辑架构** - `01-logical-architecture-detail.md` 完整版
3. **部署架构** - `02-physical-architecture-detail.md` 完整版
4. **技术架构** - `03-technical-architecture-detail.md` 完整版
5. **架构决策记录** - 15 个 ADR 完整列表
6. **风险与缓解** - 来自各 business-value 文档
7. **未来演进计划**

**素材来源**：
- **主文档**: 3 份 detail.md（各 15-21KB）
- **决策记录**: 3 份 decision.md
- **商业价值**: 3 份 business-value.md

---

### 场景 4: 新人培训材料（30 分钟）

**推荐内容**（快速参考卡片）：

1. **快速上手** - `QUICK_START.md`
2. **架构速查** - `01-logical-architecture-quick-ref.md`
3. **部署速查** - `02-physical-architecture-quick-ref.md`
4. **技术栈速查** - `03-technical-architecture-quick-ref.md`
5. **开发检查清单** - 来自 quick-ref 文档
6. **常见问题** - FAQ 汇总

**素材来源**：
- **快速参考**: 3 份 quick-ref.md（各 10-11KB）
- **架构索引**: `ARCHITECTURE_INDEX.md` 场景推荐部分

---

## 🛠️ 定制技巧

### 1. SVG 图表使用

**嵌入 PPT / Keynote**：
```bash
# 方法 1: 直接拖拽 SVG 文件（推荐）
# 方法 2: 在浏览器中打开 SVG，截图粘贴
# 方法 3: 使用 rsvg-convert 转换为 PNG
rsvg-convert -w 1920 docs/diagrams/01_logical_architecture.svg > output.png
```

**嵌入 Markdown / 文档**：
```markdown
![DDD 四层逻辑架构](diagrams/01_logical_architecture.svg)
```

**Web 页面使用**：
```html
<img src="diagrams/01_logical_architecture.svg" alt="架构图" style="max-width: 100%;">
```

### 2. 数据提取

**ROI 数据**（来自 `01-logical-architecture-business-value.md`）：
- 首年 ROI: **419%**
- 第二年 ROI: **2256%**
- 开发效率提升: **40-60%**
- 代码质量提升: **25-40%**
- 故障定位效率: **8-24 倍**
- 服务器成本降低: **60%**

**性能指标**（来自各技术文档）：
- P95 响应时间: **< 50ms**
- P99 响应时间: **< 100ms**
- 吞吐量: **> 5000 QPS**
- 可用性: **99.9%+**
- RTO (恢复时间): **< 5min**
- RPO (数据丢失): **< 1min**

**成本优化**（来自 `02-physical-architecture-business-value.md`）：
- TCO 降低: **30-50%**
- 服务器成本: **降低 60%**
- 运维人力: **降低 40-50%**
- 故障损失: **降低 75-90%**

### 3. 快速组合模板

#### 模板 A: 5 分钟电梯演讲
1. 封面（项目定位）
2. 四大核心价值
3. 技术架构全景图（`03` 号）
4. ROI 数据页
5. 联系方式

#### 模板 B: 15 分钟技术概览
1. 封面
2. 项目概览
3. DDD 四层架构（`01` 号）
4. 部署架构（`02` 号）
5. 技术栈（`03` 号）
6. 核心特性（选 2-3 个）
7. 商业价值
8. Q&A

#### 模板 C: 30 分钟完整方案
1. 封面 + 议程
2. 背景与挑战
3. 解决方案（架构设计）
4. 核心特性详解（5-6 个）
5. 技术指标与对比
6. 商业价值与 ROI
7. 实施路线图
8. 成功案例
9. Q&A

---

## 📋 素材清单速查

### 核心架构图（必用）
- `01_logical_architecture.svg` - DDD 四层逻辑架构 ⭐
- `02_physical_architecture.svg` - 网络物理部署架构 ⭐
- `03_technical_architecture.svg` - 技术架构全景图 ⭐

### 高级特性图（推荐）
- `09_multi_level_cache_architecture.svg` - 多级缓存架构 ⭐
- `26_disaster_recovery_flow.svg` - 容灾切换流程 ⭐
- `27_cicd_pipeline_detail.svg` - CI/CD 流水线 ⭐
- `28_api_gateway_detail.svg` - API 网关架构 ⭐
- `29_security_threat_model.svg` - 安全威胁模型 ⭐

### 业务流程图（按需）
- `04_login_authentication_flow.svg` - JWT 登录认证
- `05_permission_check_flow.svg` - RBAC 权限校验
- `06_workflow_approval_flow.svg` - 三级审批工作流
- `07_file_upload_flow.svg` - 文件分片上传

### 基础设施图（按需）
- `08_database_er_diagram.svg` - 数据库 ER 图
- `10_rate_limit_circuit_breaker.svg` - Sentinel 流控熔断
- `11_data_permission_filter.svg` - 自动重试策略
- `12_security_protection_system.svg` - 安全防护体系

### 部署运维图（按需）
- `13_deployment_architecture.svg` - K8s 部署架构
- `14_network_architecture.svg` - 网络架构
- `15_data_flow_architecture.svg` - 数据流转
- `16_mq_architecture.svg` - RocketMQ 架构
- `17_monitoring_architecture.svg` - 监控体系
- `18_microservice_governance.svg` - 微服务治理

### 技术栈图（按需）
- `19_backend_technology_stack.svg` - 后端技术栈
- `20_infrastructure_components.svg` - 基础设施组件
- `21_technology_ecosystem.svg` - 技术生态全景
- `22_frontend_technology_stack.svg` - 前端技术栈
- `23_frontend_backend_interaction.svg` - 前后端交互

### 监控大盘图（按需）
- `24_grafana_dashboard_architecture.svg` - Grafana 监控面板
- `25_prometheus_monitoring_architecture.svg` - Prometheus 监控

---

## 🎨 品牌与设计规范

### 颜色方案
- **主色调**: `#6366f1` (Indigo) - 专业、科技
- **成功色**: `#10b981` (Green) - 性能、优化
- **警告色**: `#f59e0b` (Amber) - 注意、重点
- **错误色**: `#ef4444` (Red) - 风险、问题
- **中性色**: `#64748b` (Slate) - 文本、说明

### 字体建议
- **中文**: PingFang SC / Microsoft YaHei / Source Han Sans
- **英文**: Inter / SF Pro / Segoe UI
- **代码**: JetBrains Mono / Fira Code / Consolas

### 尺寸规范
- **PPT 标准**: 16:9 (1920×1080)
- **打印文档**: A4 (210×297mm)
- **SVG 原始尺寸**: 各图表不同，建议保持宽高比

---

## ✅ 使用检查清单

### 商业提案准备
- [ ] 确认目标受众（技术 vs 商务）
- [ ] 选择合适的图表（3-8 张）
- [ ] 提取关键数据指标
- [ ] 准备 ROI 计算说明
- [ ] 准备成功案例或参考
- [ ] 预留 Q&A 时间

### 技术分享准备
- [ ] 确认分享深度（概览 vs 深入）
- [ ] 准备代码示例
- [ ] 准备架构决策说明
- [ ] 准备踩坑与最佳实践
- [ ] 预留现场演示时间
- [ ] 准备答疑材料

### 文档输出准备
- [ ] 确认文档格式（PDF / Word / Markdown）
- [ ] 检查图表清晰度
- [ ] 检查链接有效性
- [ ] 添加目录和索引
- [ ] 添加版本号和更新日期
- [ ] 导出前预览检查

---

## 🔗 相关资源

- **架构索引**: `docs/ARCHITECTURE_INDEX.md`
- **快速开始**: `docs/QUICK_START.md`
- **源码仓库**: https://github.com/your-org/apex-scaffold
- **在线文档**: https://docs.your-domain.com/apex-scaffold

---

## 📝 更新日志

### v1.0 (2026-09-05)
- ✅ 完成全部 29 张 SVG 图表
- ✅ 完成 12 份核心架构配套文档
- ✅ 完成 15 个架构决策记录 (ADR)
- ✅ 完成架构索引和使用指南
- ✅ 全部图表通过质量验证

---

**结论**: 现有素材库已完全满足各类商业化场景需求，可根据具体场景快速定制。建议优先使用现有高质量素材，而非制作固定的商业化材料。
