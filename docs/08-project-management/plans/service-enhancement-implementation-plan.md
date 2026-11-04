# 服务增强统一集成实施计划

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

日期：2026-10-14。适用版本：1.3.1。实施结果见 [交付报告](../reports/service-enhancement-delivery-report.md)。本计划记录本次已授权范围与验收要求，不代表所有运行场景已验收。

## 需求追踪与验收

| 编号 | 需求 | 实现位置 | 验收条件 |
| --- | --- | --- | --- |
| R1 | 注册四个增强模块，24模块统一构建 | 根 pom.xml | reactor 包含四模块，完整 verify 成功 |
| R2 | XXL 3.1.1、Seata 2.2.0、内部 starter 与 SpringDoc 版本收敛 | 根 POM、services BOM、四新模块 POM | 不保留独立硬编码 SpringDoc 版本 |
| R3 | 保留 system 数据归属，提供任务代理及有限 XXL 接管 | job-service、job starter、system、workflow | 路由白名单、可信租户、Publisher 完成及失败传播测试通过 |
| R4 | 共享脱敏与独立受保护服务 | common、masking-service | 非法短值与 Unicode 安全、JWT/scope/租户隔离测试通过 |
| R5 | 默认关闭的多模式分布式事务（`apex.seata.mode` 选择 AT/TCC/SAGA/XA，缺省 AT） | seata starter、五服务、双数据库迁移、SAGA 建表脚本 | 默认关闭与按模式装配单测通过；真实 TC 四模式端到端测试独立验收 |
| R6 | 部署入口与可选中心 | Containerfile、Compose、app.sh、.env.example、gateway | shell 语法、Compose 渲染通过，真实运行另行记录 |
| R7 | 架构和专业文档 | AGENTS、README、配置及三运维文档 | 链接、事实、空白检查通过 |

## 架构与所有权

网关将九个任务管理路径转至8095任务代理，代理固定调用 system。任务实体、数据库、日志和权限仍归 system；代理只转发 Authorization、Content-Type，不信任客户端租户头，也不接受请求指定上游。

五业务 service 引入两个 starter，默认不启动 XXL 执行器或 Seata TM/RM。system 中只有部署指定的一个正数任务 ID 与可信租户可被 XXL 接管，其余动态 cron 继续本地执行。workflow 仅接管固定超时扫描入口。XXL cron、中心任务及停用状态人工同步。

common 承担脱敏工具、Jackson 序列化及 JSON 深拷贝规则；masking-service 仅依赖 common，无数据库。策略按启动配置发布，无 CRUD、在线审批或热刷新。JWT 签名、issuer、audience、scope 与 tenant_id 构成服务访问边界。

Seata 通过 `apex.seata.mode` 选择 AT（缺省）/TCC/SAGA/XA 四种模式，共用同一 XID 传播与 Outbox 防护。AT/SAGA/XA 要求同步单 JDBC 数据源，拒绝动态数据源和 Publisher 回调，整个同步数据库事务在 jdbc 专用池执行；AT 需 undo_log，SAGA 需状态机存储三表，XA 依赖数据库 XA 能力，TCC 由扫描器代理 `@LocalTCC` 动作且不包装数据源。XID 通过受保护内部入口、精确 origin 和独立传播密钥传递，网关剥离外部传播头。现有 Outbox 在全局事务内被拒绝，不能声称全局事务与消息原子一致。

## 实施顺序

1. 阅读各模块 POM、配置、实现与测试，保留已有变更；先完成根注册、统一版本和服务父 BOM。
2. 核对函数式路由、安全、调度接管与默认禁用；补充代理转发、失败响应及启用配置拒绝路径测试。
3. 配对追加各业务域 undo_log 迁移，不改历史版本；核对网关、容器构建、端口、中心外部挂载及卷声明。
4. 加载固定 Java 17.0.12-oracle、mvnd 2.0.0-rc-3，执行 `mvnd -T4 -B clean verify`；由失败证据修复 PMD、覆盖率等问题，不降低门槛或 SLA。
5. 校验 Compose、shell、文档链接及 diff；将实际测试统计、跳过项、外部条件写入交付报告。

## 接口与配置

任务接口：GET `/admin-api/v1/jobs`、`/page`、`/{id}`、`/{id}/logs`；POST 根路径、`/{id}/run`；PUT `/{id}`、`/{id}/status`；DELETE `/{id}`。缺少有效格式 Bearer 返回401，非法ID返回400，连接失败返回502，实际权限仍由 system 验证。

脱敏接口：POST `/api/v1/masking/batch` 需 `masking:apply`；GET `/api/v1/masking/rules` 需 `masking:rules`。批次1–100项，解码上限256KB；租户由签名 token 读取。详细规则与示例见 [脱敏运维](../../05-operations/data-masking.md)。

| 组件 | 端口/配置 | 默认状态 |
| --- | --- | --- |
| job-service | APEX_JOB_PORT=8095、APEX_JOB_SYSTEM_URL | 固定 system 代理 |
| masking-service | MASKING_PORT=8096、四项 MASKING_JWT/租户变量 | Compose masking profile 可选 |
| XXL 中心 | 8097、XXL_ADMIN_CONFIG_FILE | xxl-job profile 可选 |
| Seata TC | RPC8091、控制台7091、SEATA_SERVER_CONFIG_FILE | seata profile 可选 |
| 五业务执行器 | apex.job.executor.* | enabled=false |
| 五业务 AT | APEX_SEATA_ENABLED、TX_GROUP、PROPAGATION_SECRET | enabled=false |

## 迁移与部署验收

system V61、auth V57、workflow V2、file V3、office V3 均追加 MySQL/PostgreSQL `create_seata_undo_log`（仅 AT 模式需要），真实数据库执行需分别验收。SAGA 模式另需执行随 starter 发布的 `sql/saga/{mysql,postgresql}/seata_state_machine.sql` 存储三表。TC 官方表、XXL 官方表必须独立初始化，不能以业务 undo_log 代替。

先准备外部 apex-observability 网络、镜像、真实机密、中心只读配置文件、身份中心、数据库与 TLS；再启动中心和业务服务。Compose profile 只启动中心，不开启业务执行器或全局事务。业务开关需通过配置中心或明确环境注入，不能依赖 .env 自动传入 Java。

XXL 切换前停用相应中心任务、核对单任务租户与白名单，再启动执行器并配置中心；回退时先停中心任务再恢复本地接管开关，避免重叠。Seata 关闭前停止新事务并等待在途事务终结，保留 TC/RM；AT 下保留 undo_log，不删除待恢复记录。

真实 AT 验收使用专用 TC、两个独立测试库及 `APEX_SEATA_IT=true`，验证提交与故障回滚；同 JVM 两个 HTTP 分支不能替代五服务生产 HTTPS 联调。TCC 动作代理、SAGA 状态机执行与 XA 两阶段提交需在具备真实 TC 的环境另行验收。详细命令见 [Seata 运维](../../05-operations/seata-integration.md)。

## 风险与完成判据

完整 verify 成功只证明默认门禁范围；跳过真实 AT 不算 AT 通过，Compose 渲染不算部署成功，401路由存活探针不证明 system 上游、数据库或权限链就绪。上线还须验证 XXL 通信、JWT/JWKS轮换、双数据库迁移、TC故障恢复、生产TLS、日志采集和并发容量。

本次变更完整文件清单、测试实况与未验证项目统一列于交付报告；未提交 Git，也不提供未经测试的性能承诺。
