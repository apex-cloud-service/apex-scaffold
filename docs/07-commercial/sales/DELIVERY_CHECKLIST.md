# Apex Scaffold 交付清单

**版本**: v1.0  
**适用**: 标准版 / 企业版源码交付  
**用途**: 交付前自检与验收核对

---

## 1. 交付物清单

| # | 交付物 | 说明 | 是否包含 |
|---|--------|------|---------|
| 1 | 源码包（8 模块 + generator） | DDD 五层 + 代码生成器 | ✅ |
| 2 | 配套前端工程 apex-admin-web | Vue3 管理后台（前后端分离） | ✅ |
| 3 | 部署资产 | Dockerfile / docker-compose.yml / docker-compose.infra.yml / k8s/（含金丝雀）/ scripts/ | ✅ |
| 4 | 文档集 docs/ | QUICK_START/ARCHITECTURE/CONFIGURATION/DEPLOYMENT/CODEGEN/EXAMPLE_GUIDE 等 27 篇 | ✅ |
| 5 | 监控资产 config/monitoring/ | Prometheus 抓取 + 10 条告警规则 + Grafana 大盘 | ✅ |
| 6 | 授权工具 | scripts/license/（私钥 + 发放脚本）+ LICENSE_MANAGEMENT.md | ✅（企业版） |
| 7 | 采购材料 docs/sales/ | 安全白皮书 / API 规范 / 性能报告 / 本清单 | ✅ |
| 8 | License 授权文件 | 按订单生成（企业版必含） | 按单 |

## 2. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+（推荐 17.0.12） | sdkman 统一 |
| Maven | mvnd 2.0+ / mvn 3.9+ | 构建 |
| MySQL | 8.0+ | 主数据源 |
| Redis | 6.2+ / 7.x | 缓存/会话/限流 |
| Node.js | ≥18（仅前端） | apex-admin-web 构建 |
| Nacos / RocketMQ / Sentinel | 可选 | 企业版能力，按需接入 |

## 3. 上线前 Checklist

- [ ] `mvn -o test` 全量测试绿（基线 ~1840 例）
- [ ] 三档构建冒烟：`-Pbasic/-Pstandard/-Penterprise` 产物可启动
- [ ] 生产环境变量全部替换（JWT_SECRET ≥32 字节 / DB / Redis 密码）
- [ ] License 授权文件已部署并验证商业功能可用
- [ ] 文件上传：默认关闭；开启时已配百度云审核
- [ ] Nacos 接入：`NACOS_*` 开关按需开启；Sentinel 动态规则按 config/sentinel 发布
- [ ] 备份策略已配置（scripts/backup + cron + 恢复演练）
- [ ] 监控接入：Prometheus 抓取 8081 + Grafana 大盘 + 告警通道
- [ ] 安全：OWASP 扫描报告存档；默认密码/密钥已全部替换
- [ ] 灰度预案：金丝雀（k8s/）或滚动更新策略就绪

## 4. 验收核对（客户侧）

- [ ] 快速开始：5 分钟跑通 doc.html + 健康检查 + 登录
- [ ] 示例模块：`/api/example/capabilities/*` 逐个演示通过
- [ ] 管理后台：用户/角色/菜单/字典/参数/任务/审计/在线用户可用
- [ ] 授权验证：基础版放行 + 商业功能按 License 拒绝/放行
- [ ] 文档与实现一致（以 docs/ 为准）

## 5. 服务等级（随版本）

| 版本 | 支持方式 | 响应时效 |
|------|---------|---------|
| 标准版 | 邮件工单 | 5 个工作日 |
| 企业版 | 专属客服 | 2 小时（工作时间） |

---
**交付人**: ______　**客户**: ______　**日期**: ______　**版本**: v1.2.0
