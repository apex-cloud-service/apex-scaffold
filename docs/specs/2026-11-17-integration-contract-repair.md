# 2026-11-17 前后端联调后端修复方案（BE-036）

## 1. 背景

本会话先前进行真实本地联调并修改若干后端文件，但未先登记任务/方案。2026-11-17 用户明确授权 ZCode 为此联调项主控，补登记 BE-036 / FE-026。本方案对既有工作作补记并约束后续验证，**不虚构事前审批**；本项须独立复核，不触碰其他任务和私有工具目录。

## 2. 目标

- 四个权限快照客户端安全地解码认证中心 `{tenantId,userId,permissions,roles}` 与增量未知字段；只忽略非消费字段，不放宽 tenant/user/permission/code 校验或错误拒绝。
- 有效套餐 menuIds null/空集合按既有契约允许全量菜单；受限菜单依旧补父链，拒绝范围仍为空。
- 审批仓储在可信 Reactor Context 解析租户，在实际 JDBC callable 中安装/恢复 ThreadLocal，保持无租户 fail-closed。核查这次改动的所有仓储方法，避免只修待办查询而留下 ID/写操作隔离缺口。

## 3. 影响面（模块、接口、数据）

`apex-office` 的 `ApprovalProcessRepositoryImpl`、`apex-system` 的 `MenuService`、`apex-{office,integration,workflow,knowledge}-service` 的权限客户端及回归测试。接口路径/响应主协议不改变；Snapshot 是消费投影的增量兼容。构建不升级依赖。真实联调用 Podman 基础设施 + 当前 Java JAR；生产环境未变更。

## 4. 兼容性

- 复用 `TenantContext.callWithTenant` 与现有 JDBC 调度器；优先可信 Reactor 租户，仅兼容既有显式 ThreadLocal 调用，不由 HTTP query/body 选择租户。
- 无租户必须拒绝并且 mapper 不执行；显式租户/实体租户与可信租户不符必须拒绝。成功、异常和连续跨租户请求均恢复线程上下文。
- menuIds 空集合全量仅在有效 scope 非 denied 时适用；features 空集合仍是未订阅。
- 不为联调打开鉴权绕过、扩大生产账号权限或延长 token TTL。先前本地数据修补另记清单和验证边界，不伪装为产品迁移。

## 5. 回滚方案

未提交工作树仅回退本项差异，保留其他改动。以后提交用 `git revert`。本地端口冲突绕开用户进程，环境继续 `podman-compose`；不删数据卷。先前修改本机信任库、本地授权/套餐等须在记录中披露，未保存原快照不自动覆盖式恢复。

## 6. 验收标准

1. 实际失败复现：严格 ObjectMapper 解码含 roles/新字段；菜单 null/空/受限/拒绝；审批 JDBC Reactor-only 租户与无上下文/错配/异常恢复。
2. 按根 AGENTS 使用固定 JDK 21.0.13.0+9-tem + `scripts/build/init-build-env.sh` + mvnd；真实受影响模块测试、格式/质量检查及 JAR 重建实际退出成功，不以 `tail` 或 echo 伪报成功。
3. 真实 HTTP 读取回归与前端 FE-026 协同；实际对端数据非空闭环、第三方外部操作未验证时不得声称全量验收。
4. 记录/接口说明/台账/索引同步，独立 reviewer 输出缺陷结论后再更新任务状态。
