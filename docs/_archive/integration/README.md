# 集成文档索引

本目录包含 Apex Scaffold 与第三方系统的集成相关文档。

**🔍 快速搜索**: [打开文档搜索工具](../doc-search.html) - 支持关键词搜索、分类过滤、实时预览

## 📋 目录概览

### OAuth2/PKCE 认证集成

集成相关文档位于 [`oauth2/`](oauth2/) 目录。

---

## 🔐 OAuth2/PKCE 认证集成

### 核心文档

| 文档名称 | 文件路径 | 说明 |
|---------|---------|------|
| **SSO OAuth2 实施计划** | [oauth2/sso-implementation-plan.md](oauth2/sso-implementation-plan.md) | SSO OAuth2 集成的完整实施计划 |
| **Keycloak 集成方案** | [oauth2/keycloak-integration.md](oauth2/keycloak-integration.md) | Keycloak 认证方式集成方案 |
| **OAuth2 集成指南** | [oauth2/integration-guide.md](oauth2/integration-guide.md) | OAuth2 集成的操作指南和最佳实践 |
| **OAuth2 实施报告** | [oauth2/implementation-report.md](oauth2/implementation-report.md) | OAuth2 完整实施报告和结果总结 |

### PKCE 相关文档

PKCE (Proof Key for Code Exchange) 相关文档位于 [`oauth2/pkce/`](oauth2/pkce/) 子目录。

| 文档名称 | 文件路径 | 说明 |
|---------|---------|------|
| **PKCE 验证计划** | [oauth2/pkce/verification-plan.md](oauth2/pkce/verification-plan.md) | PKCE 功能验证计划 |
| **PKCE 集成测试报告** | [oauth2/pkce/integration-test-report.md](oauth2/pkce/integration-test-report.md) | PKCE 集成测试执行报告 |
| **PKCE 单元测试报告** | [oauth2/pkce/unit-test-report.md](oauth2/pkce/unit-test-report.md) | PKCE 单元测试执行报告 |
| **PKCE 性能测试报告** | [oauth2/pkce/performance-test-report.md](oauth2/pkce/performance-test-report.md) | PKCE 性能测试报告和性能指标 |
| **PKCE 部署检查清单** | [oauth2/pkce/deployment-checklist.md](oauth2/pkce/deployment-checklist.md) | PKCE 部署前检查清单 |

### 历史归档

历史测试报告和开发记录位于 [`oauth2/archive/`](oauth2/archive/) 目录。

---

## 🎯 快速开始

### 场景1: 新增 OAuth2 集成

**推荐阅读顺序**:

1. [OAuth2 集成指南](oauth2/integration-guide.md) - 了解集成流程
2. [SSO OAuth2 实施计划](oauth2/sso-implementation-plan.md) - 参考实施计划
3. [Keycloak 集成方案](oauth2/keycloak-integration.md) - Keycloak 具体实现
4. [PKCE 部署检查清单](oauth2/pkce/deployment-checklist.md) - 部署前检查

### 场景2: PKCE 功能验证

**推荐阅读顺序**:

1. [PKCE 验证计划](oauth2/pkce/verification-plan.md) - 验证方案
2. [PKCE 单元测试报告](oauth2/pkce/unit-test-report.md) - 单元测试参考
3. [PKCE 集成测试报告](oauth2/pkce/integration-test-report.md) - 集成测试参考
4. [PKCE 性能测试报告](oauth2/pkce/performance-test-report.md) - 性能基准

### 场景3: OAuth2 问题排查

**推荐阅读顺序**:

1. [OAuth2 实施报告](oauth2/implementation-report.md) - 了解已知问题
2. [OAuth2 集成指南](oauth2/integration-guide.md) - 查看常见问题解决方案
3. [oauth2/archive/](oauth2/archive/) - 查看历史问题记录

---

## 📚 相关文档

- [系统架构文档](../../architecture/system-architecture.md) - 整体架构设计
- [安全防护体系](../../architecture/advanced-features/security-protection-system.md) - 安全机制详解
- [后端开发规范](../../04-development/backend-development-standards.md) - 开发规范
- [部署指南](../operations/deployment.md) - 部署说明

---

## 🔄 文档更新记录

| 日期 | 版本 | 更新内容 | 更新人 |
|------|------|---------|--------|
| 2026-09-09 | v1.0 | 创建集成文档索引,整合 OAuth2/PKCE 相关文档 | AI Agent |

---

**文档维护者**: 技术团队  
**审核状态**: 待审核  
**下次更新**: 新增集成功能后同步更新
