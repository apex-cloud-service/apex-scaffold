# Apex Scaffold 安全白皮书

**版本**: v1.0  
**适用版本**: Apex Scaffold v1.2.0（Java 17 / Spring Boot 3.5 / WebFlux）  
**定位**: 面向企业采购安全评审的说明材料；所有内容基于代码库实际实现，可审计验证。

---

## 1. 概述

Apex Scaffold 采用**纵深防御**策略构建安全体系，覆盖认证、授权、传输、存储、
审计、依赖供应链六个层面，并按等级保护 2.0（GB/T 22239）通用要求自评设计。

## 2. 认证与身份

| 能力 | 实现 | 说明 |
|------|------|------|
| 登录方式 | 账号密码（BCrypt 哈希比对）+ 图形验证码 | `AuthService` / `CaptchaService` |
| Token | JWT（HS256/RS256 可配，默认 HS256 ≥32 字节密钥）+ Refresh Token | 无状态、双 Token 模型 |
| 设备绑定 | `deviceId` 参与刷新校验 | 防 Token 跨设备盗用 |
| 会话失效 | 登出/强退即时撤销（Redis 黑名单） | `revokeSessions` |
| 验证码 | 图形验证码（Redis 一次性消费 + 防重放） | 登录时可选强制 |
| 暴力破解防护 | 失败计数 + IP/账号封禁升级 | `LoginProtectionProperties`，可配置阈值与窗口 |

## 3. 授权与访问控制

| 能力 | 实现 |
|------|------|
| 模型 | RBAC（用户-角色-权限）+ 菜单/按钮级权限码 |
| 接口鉴权 | `@RequirePermission` 切面（声明式）+ `RequirePermissionAspect` |
| 数据权限 | 部门数据权限（`@DataScope`），按数据范围过滤 |
| 幂等 | `@IdempotentApi`（Redis SETNX，防重复提交副作用） |

## 4. 传输安全

- HTTPS 全链路推荐（Compose/K8s 提供 TLS 入口示例）
- 敏感请求体（登录密码）建议 TLS 承载；Token 走 `Authorization: Bearer` 头，不落 URL
- JWT 密钥、DB/Redis 密码全部环境变量注入，禁止硬编码；prod 启动强制校验（JWT ≥32 字节等）

## 5. 存储与数据安全

- 数据库密码 `DB_MASTER_PASSWORD` / Redis `REDIS_PASSWORD` 环境变量注入，不进代码/镜像层
- 敏感数据脱敏工具 `DataMaskUtil`（手机号/身份证/邮箱/姓名），支持日志与响应脱敏
- 逻辑删除（`deleted` 标记）统一，物理删除需显式操作

## 6. 输入与输出安全

| 防护 | 实现 |
|------|------|
| SQL 注入 | MyBatis-Plus 参数化 + `InputValidationFilter` 关键字检测（SQL 语法位置判定，防误报） |
| XSS | 管理后台统一 HTML 转义（`esc`），前后端分离工程内置转义 |
| 参数校验 | Jakarta Validation（注解式，统一错误码/消息） |
| 重放攻击 | `@AntiReplay`（时间戳+nonce，Redis 窗口去重） |
| SSRF/路径穿越 | 文件存储抽象层校验对象名与存储桶边界 |

## 7. 限流与抗滥用

- Sentinel 四维限流：接口（43 条限流规则）、IP（ZSet + Lua 滑窗 + 封禁升级）、用户、热点参数
- 登录/短信等敏感接口专属低阈值（20 QPS 防刷）
- 系统保护：CPU 使用率 + 入口 QPS 兜底
- 规则支持 Nacos 动态下发（生产热更新，见 `apex.sentinel.nacos`）

## 8. 审计与追溯

- 操作审计 `@OperationLog` → `sys_operation_log`（操作人/模块/动作/请求摘要/IP/耗时）
- 登录日志 `sys_login_log`（成功/失败/IP/原因）
- 全链路 `traceId` 贯穿请求（日志、响应头、异常响应），支持问题定位
- 审计查询接口受 `system:audit-log:list` 权限保护

## 9. 依赖与供应链安全

- CI 集成 **OWASP Dependency-Check**（`.github/workflows/security-scan.yml`），Critical/High 漏洞门禁
- 依赖版本统一由父 POM `dependencyManagement` 管理；已知兼容性缺陷（如 commons-io 版本缺口）已修复并有回归测试
- Java 17 LTS + Spring Boot 3.5 官方支持版本

## 10. 等保 2.0 自评对照

| 等保要求 | 本产品对应 |
|---------|-----------|
| 身份鉴别 | 双因子可选（密码+验证码）、登录失败锁定 |
| 访问控制 | RBAC + 数据权限 + 最小权限原则 |
| 安全审计 | 操作/登录双审计日志、只读审计查询 |
| 入侵防范 | 限流/防重放/SQL 注入防护/输入校验 |
| 数据完整性 | Token 签名、Flyway 迁移校验（checksum） |
| 数据备份恢复 | `scripts/backup/`（dump+轮转+演练流程） |
| 集中管控 | Prometheus 指标 + 告警规则 + 健康探针 |

## 11. 已知边界与建议

- 默认示例密钥/密码仅用于本地开发，**生产必须通过环境变量替换**（DEPLOYMENT.md §14 安全清单）
- 文件上传默认关闭（`FILE_UPLOAD_ENABLED=false`）；开启时要求启用百度云图片审核联动
- 建议部署在隔离网络，数据库/Redis 不对外暴露
