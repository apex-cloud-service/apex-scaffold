# Apex Scaffold 文档与脚本目录结构规范

**版本**: v1.0  
**发布日期**: 2026-09-17  
**适用范围**: 所有项目文档和脚本

---

## 一、设计原则

### 1.1 核心原则
- **用户导向**: 按用户角色和使用场景组织,而非按内容类型
- **路径简短**: 最多3级目录深度,路径易记忆
- **职责单一**: 每个目录只承担一种职责,避免交叉
- **版本控制**: 只保留当前有效文档,过时内容归档或删除
- **可发现性**: 每个目录都有README.md说明用途和内容索引

### 1.2 禁止反模式
- ❌ 禁止按时间组织(如reports/2026-09/)
- ❌ 禁止按作者组织(如reports/kiro/)
- ❌ 禁止按状态组织(如drafts/、final/、completed/)
- ❌ 禁止重复内容(同一信息只保留一份)
- ❌ 禁止临时文件进入版本控制

---

## 二、docs/ 目录结构

```
docs/
├── README.md                        # 文档总索引,快速导航
├── 00-getting-started/              # 快速入门(新人必读)
│   ├── README.md
│   ├── quickstart.md                # 5分钟快速开始
│   ├── onboarding-guide.md          # 新人入职指南
│   └── examples-summary.md          # 示例代码总览
│
├── 01-guides/                       # 使用指南(按功能分类)
│   ├── README.md
│   ├── authentication/              # 认证授权指南
│   │   ├── oauth2-integration.md
│   │   ├── keycloak-integration.md
│   │   └── sso-implementation.md
│   ├── cache/                       # 缓存使用指南
│   │   ├── multi-level-cache.md
│   │   └── redis-watchdog.md
│   ├── data/                        # 数据相关指南
│   │   ├── data-scope-usage.md
│   │   ├── tenant-isolation.md
│   │   └── datasource-switch.md
│   ├── messaging/                   # 消息队列指南
│   │   └── rocketmq-usage.md
│   ├── resilience/                  # 容错弹性指南
│   │   ├── rate-limit-algorithms.md
│   │   ├── circuit-breaker.md
│   │   ├── auto-retry.md
│   │   └── distributed-bulkhead.md
│   ├── workflow/                    # 工作流指南
│   │   ├── workflow-engine.md
│   │   ├── workflow-designer.md
│   │   └── workflow-monitor.md
│   └── troubleshooting.md           # 故障排查指南
│
├── 02-architecture/                 # 架构设计(技术决策)
│   ├── README.md
│   ├── architecture-overview.md     # 架构总览
│   ├── architecture-evolution.md    # 架构演进历史
│   ├── ddd-design/                  # DDD设计
│   │   ├── logical-architecture.md  # 逻辑架构(五层)
│   │   ├── domain-model.md          # 领域模型
│   │   └── bounded-context.md       # 限界上下文
│   ├── infrastructure/              # 基础设施
│   │   ├── database-design.md
│   │   ├── cache-architecture.md
│   │   ├── mq-architecture.md
│   │   └── network-architecture.md
│   └── technology-stack/            # 技术栈
│       ├── backend-tech-stack.md
│       ├── frontend-tech-stack.md
│       └── tech-selection-reasons.md
│
├── 03-api/                          # API文档
│   ├── README.md
│   ├── api-standards.md             # API规范
│   ├── api-version-management.md    # 版本管理
│   └── openapi-annotations.md       # OpenAPI注解规范
│
├── 04-development/                  # 开发规范
│   ├── README.md
│   ├── backend/                     # 后端开发
│   │   ├── backend-standards.md
│   │   ├── webflux-reactive.md
│   │   ├── mybatis-plus-guide.md
│   │   └── flyway-migration.md
│   ├── frontend/                    # 前端开发
│   │   ├── frontend-standards.md
│   │   ├── vue3-guide.md
│   │   └── component-guide.md
│   ├── code-quality/                # 代码质量
│   │   ├── javadoc-guide.md
│   │   ├── lombok-guide.md
│   │   ├── spotless-format.md
│   │   └── pmd-check.md
│   └── tools/                       # 开发工具
│       ├── mvnd-build.md
│       ├── codegen-usage.md
│       └── mermaid-viewer.md
│
├── 05-operations/                   # 运维部署
│   ├── README.md
│   ├── deployment/                  # 部署
│   │   ├── deployment-guide.md
│   │   ├── docker-compose.md
│   │   └── kubernetes-deploy.md
│   ├── configuration/               # 配置
│   │   ├── configuration-guide.md
│   │   └── environment-vars.md
│   ├── monitoring/                  # 监控
│   │   ├── prometheus-grafana.md
│   │   ├── loki-logging.md
│   │   └── alert-rules.md
│   └── security/                    # 安全
│       ├── security-checklist.md
│       └── security-protections.md
│
├── 06-testing/                      # 测试
│   ├── README.md
│   ├── test-strategy.md             # 测试策略
│   ├── integration-test.md          # 集成测试
│   └── performance-test.md          # 性能测试
│
├── 07-commercial/                   # 商业化
│   ├── README.md
│   ├── versions.md                  # 版本对比(基础版/标准版/企业版)
│   ├── license-management.md        # 授权管理
│   └── sales/                       # 销售资料
│       ├── api-specification.md
│       ├── security-whitepaper.md
│       ├── performance-report.md
│       └── delivery-checklist.md
│
├── diagrams/                        # 架构图表(82张SVG)
│   ├── README.md                    # 图表索引
│   ├── 01_logical_architecture.svg  # 01-69号现有图表
│   ├── ...
│   ├── 82_domain_event_outbox.svg   # 70-82号新增图表
│   ├── sequence/                    # Mermaid时序图源文件
│   │   └── *.mmd
│   └── _templates/                  # SVG模板
│       └── design-system-v3.svg
│
└── reports/                         # 技术报告(只保留有长期价值的)
    ├── README.md
    ├── svg-code-alignment-audit-report.md          # 2026-09-17审计报告
    └── svg-update-completion-report-2026-09-17.md  # 2026-09-17更新报告
```

---

## 三、scripts/ 目录结构

```
scripts/
├── README.md                        # 脚本索引和使用说明
│
├── build/                           # 构建相关
│   ├── init-build-env.sh            # 初始化构建环境(SDKMAN)
│   ├── build.sh                     # 完整构建
│   └── clean.sh                     # 清理构建产物
│
├── dev/                             # 开发辅助
│   ├── codegen/                     # 代码生成
│   │   ├── generate.sh
│   │   └── examples/
│   │       └── product.json
│   ├── format/                      # 代码格式化
│   │   ├── batch-add-javadoc.sh
│   │   ├── batch-add-lombok.sh
│   │   └── spotless-apply.sh
│   └── check/                       # 代码检查
│       ├── scan-javadoc-issues.sh
│       ├── scan-lombok-issues.sh
│       └── check-port-conflicts.sh
│
├── test/                            # 测试相关
│   ├── run-unit-tests.sh
│   ├── run-integration-tests.sh
│   ├── jvm-diagnostics-test.sh
│   └── generate-test-report.sh
│
├── deploy/                          # 部署相关
│   ├── docker/                      # Docker部署
│   │   ├── pull-infra-images.sh
│   │   └── deploy-monitoring.sh
│   ├── k8s/                         # Kubernetes部署
│   │   └── deploy-k8s.sh
│   └── service/                     # 服务管理
│       ├── app.sh                   # 应用启动/停止/状态
│       ├── start.sh
│       └── stop.sh
│
├── ops/                             # 运维相关
│   ├── backup/                      # 备份恢复
│   │   ├── db-backup.sh
│   │   └── db-restore.sh
│   ├── monitoring/                  # 监控
│   │   ├── start-monitoring.sh
│   │   └── grafana/
│   │       ├── check-environment.sh
│   │       ├── configure-datasource.sh
│   │       ├── import-dashboard.sh
│   │       └── quick-check.sh
│   └── diagnostics/                 # 诊断
│       └── jvm-diagnostics.sh
│
├── data/                            # 数据相关
│   ├── init-workflow-test-data.sql
│   └── datasource/
│       ├── setup-slave.sh
│       └── init-slave.sql
│
├── ci/                              # CI/CD相关
│   ├── check-doc-consistency.sh
│   └── check-doc-links.sh
│
└── utils/                           # 通用工具
    ├── export-api-docs.sh
    ├── extract-env-vars.sh
    └── verify-svg-quality.sh
```

---

## 四、清理规则

### 4.1 必须删除的文件类型
1. **临时文件**: `*.tmp`, `*.bak`, `*.backup`, `*.old`
2. **重复报告**: 同一主题的多个版本报告,只保留最新或最完整的
3. **过时报告**: 6个月前的交付报告、进度报告、实施报告
4. **示例代码**: Postman collection、测试脚本等已迁移到测试框架的
5. **草稿文档**: 标题包含"草稿"、"TODO"、"WIP"的未完成文档
6. **归档内容**: `_archive/` 下超过1年且无参考价值的内容

### 4.2 保留规则
1. **技术决策**: 架构决策记录(ADR)永久保留
2. **审计报告**: 最近一次的审计报告保留1年
3. **测试报告**: 最近3次的完整测试报告
4. **配置示例**: `.env.example`、`application.yml.example`等
5. **商业文档**: 销售资料、白皮书等

### 4.3 归档规则
如果不确定是否删除,考虑归档:
```
docs/_archive/YYYY-MM/
└── archived-file.md
```
归档文件保留6个月,之后自动删除。

---

## 五、维护流程

### 5.1 新增文档
1. 确定文档类别和目标受众
2. 放入对应目录,命名使用小写+短横线(kebab-case)
3. 在目录的README.md中添加索引
4. 在docs/README.md中更新总索引(如果是重要文档)

### 5.2 更新文档
1. 直接更新原文件,不创建v2、final等版本
2. 在文档头部更新"最后更新日期"
3. 重大变更在文档底部添加"变更历史"章节

### 5.3 删除文档
1. 评估是否有引用(使用grep检查)
2. 如果有引用,先更新引用或提供替代文档
3. 删除前考虑是否需要归档
4. 从所有README索引中移除

### 5.4 定期审查
- **每月**: 清理临时文件和重复文件
- **每季度**: 审查reports/目录,归档过时报告
- **每年**: 清理_archive/目录,删除超过1年的归档文件

---

## 六、文档编写规范

### 6.1 文件命名
- 使用小写字母
- 单词用短横线连接: `oauth2-integration.md`
- 避免缩写,除非是公认的技术术语
- 不使用日期、版本号作为文件名的一部分

### 6.2 目录命名
- 使用小写字母
- 单词用短横线连接: `getting-started/`
- 添加序号前缀便于排序: `01-guides/`, `02-architecture/`

### 6.3 文档结构
每个文档应包含:
```markdown
# 文档标题

> 版本: v1.0 | 最后更新: 2026-09-17 | 维护者: 架构团队

简短的文档说明(1-2句话)

## 目录
(自动生成或手动维护)

## 正文
...

## 参考资料
- 相关文档链接
- 外部资源链接

---

**维护者**: 团队名称  
**联系方式**: 联系方式或讨论渠道
```

---

## 七、迁移检查清单

从旧结构迁移到新结构时:

- [ ] 备份当前docs/和scripts/目录
- [ ] 创建新的目录结构
- [ ] 迁移有效文档到新位置
- [ ] 更新所有内部链接和引用
- [ ] 删除冗余和过时文件
- [ ] 更新README索引
- [ ] 验证所有链接有效性
- [ ] 提交变更并通知团队

---

**文档版本**: v1.0  
**发布日期**: 2026-09-17  
**下次审核**: 2027-03-17 (6个月后)

© 2026 Apex Team · 保持文档整洁,提升团队效率
