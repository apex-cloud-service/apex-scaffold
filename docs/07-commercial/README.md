# 07. 商业化文档

本目录包含商业版本、授权管理和销售支持相关文档。

---

## 📚 核心文档

### [授权管理](./license-management.md)

授权系统使用和管理指南

- 授权系统架构
- 授权文件生成
- 授权验证流程
- 授权更新和续期
- 常见问题处理

### [商业版本](COMMERCIAL_VERSIONS.md)

商业版本功能对比和说明

- Basic 版本（开源）
- Standard 版本（标准商业版）
- Enterprise 版本（企业版）
- 功能对比表
- 升级路径

---

## 💼 销售资料

销售支持文档位于 [sales/](./sales/) 目录：

### [产品设计指导文档](./sales/Apex-Scaffold-产品设计指导文档.md)

角色画像、能力地图、模块设计指引与设计规范（DOCX/HTML/PDF 同源多格式随附）

### [产品解决方案文档](./sales/Apex-Scaffold-产品解决方案文档.html)

面向客户的整体解决方案介绍（DOCX/PDF 同源多格式随附）

### [API 规范](./sales/API_SPECIFICATION.md)

API 接口规范和设计标准

- RESTful API 设计规范
- 接口文档标准
- 版本管理策略
- 安全规范

### [安全白皮书](./sales/SECURITY_WHITEPAPER.md)

系统安全体系和防护措施

- 认证与授权
- 数据加密
- 网络安全
- 安全审计
- 合规认证

### [性能报告](./sales/PERFORMANCE_REPORT.md)

性能测试报告和优化建议

- 性能测试方法
- 测试结果数据
- 性能指标分析
- 优化建议

### [交付清单](./sales/DELIVERY_CHECKLIST.md)

项目交付检查清单

- 交付物清单
- 验收标准
- 部署验证
- 培训材料

---

## 📋 商业化规划

### 发行准备

- [代码清理清单](./code-cleanup-checklist.md) - 发行前代码审查
- [脚手架重构计划](./scaffold-refactoring-plan.md) - 架构优化
- [增强提案](./enhancement-proposal.md) - 功能增强规划

### 销售执行

- [销售执行计划](./sales-execution-plan.md) - 市场推广策略

---

## 🔐 授权体系

### 授权档位

| 档位 | 价格 | 适用场景 | 核心功能 |
|------|------|---------|---------|
| Basic | 免费 | 学习、开源项目 | 核心功能 |
| Standard | 商业 | 中小企业 | + MySQL/Redis/缓存 |
| Enterprise | 商业 | 大型企业 | + 微服务/消息队列 |

### 授权验证

系统启动时自动验证授权文件：

- 授权文件路径：`config/license.dat`
- 验证失败时降级到 Basic 档位
- 授权到期前 30 天提醒

---

## 🚀 快速链接

- [返回文档中心](../README.md)
- [部署指南](../05-operations/deployment.md)
- [配置管理](../05-operations/configuration.md)

---

**最后更新**: 2026-09-12
