# 变更与配置控制

> **版本**: v1.0
> **核对日期**: 2026-10-30
> **基线提交**: `b8e5733`
> **维护者**: Apex Team

本文档规定交付版本、数据库迁移、运行配置与接口契约的变更方式。目标是不让“已交付的东西”在客户不知情的情况下改变。

---

## 1. 版本控制

### 1.1 版本号来源

| 项 | 位置 | 说明 |
| --- | --- | --- |
| 项目版本 | 根 [pom.xml](../../../pom.xml) `<version>` | 当前 1.3.1，所有模块继承 |
| 构建档位标识 | `target/classes/apex-build.properties` | `build.profile.id` 写入档位 |
| Git 标签 | `git tag` | 最近标签为 `v1.1.4`，**落后于当前版本** |

> 当前版本 1.3.1 无对应 Git 标签。发布前应补打标签，否则无法从标签复现交付物。

### 1.2 版本号变更规则

遵循语义化版本：

| 变更类型 | 版本位 | 示例 |
| --- | --- | --- |
| 不兼容的接口/契约变更 | 主版本 | 2.0.0 |
| 向后兼容的功能新增 | 次版本 | 1.4.0 |
| 向后兼容的缺陷修复 | 修订号 | 1.3.2 |

一次版本变更需要同步修改：根 POM、[CHANGELOG.md](../../../CHANGELOG.md)、[README.md](../../../README.md) 徽章与版本表、本目录 [交付范围与基线](./delivery-scope.md)。

---

## 2. 数据库迁移控制

### 2.1 不可变规则

| 规则 | 说明 |
| --- | --- |
| 已执行迁移不可修改 | 修改会导致 Flyway checksum 不匹配，存量环境启动失败 |
| 只追加新版本 | 修正历史问题用新版本脚本，不改旧脚本 |
| 双库成对维护 | 每个版本同时提供 MySQL 与 PostgreSQL 脚本 |
| 版本链按域独立 | system、auth、workflow、file、office 各自编号，不能按全仓最大值编排 |
| 命名固定 | `V{版本号}__{描述}.sql`，双下划线 |

### 2.2 变更流程

```
需求确认
  → 读取所属域当前最大版本
  → 编写 MySQL + PostgreSQL 两份脚本
  → 更新对应实体/PO/仓储
  → 本地空库执行验证幂等
  → 运行 FlywayMigrationVersionTest
  → 在目标环境执行并记录
  → 提交
```

### 2.3 交付时的迁移核对

| 核对项 | 方法 |
| --- | --- |
| 双库版本集合一致 | 对比 `mysql/` 与 `postgresql/` 的版本号列表 |
| 版本连续 | `FlywayMigrationVersionTest` |
| 无未提交迁移 | `git status <模块>/src/main/resources/db/migration` 为空 |
| 目标库已执行到最新 | 查询 `flyway_schema_history` 的 `success=1` 最大版本 |

> 2026-10-30 曾登记 system V70～V72 未提交、违反“无未提交迁移”项（[风险登记册](./risk-register.md) R-01）；该批迁移已于 2026-10-31 随 `0e9dba36` 提交入库，R-01 已于 2026-11-14 复核关闭。本核对项当前满足，仍按上表在交付时逐项复查。

### 2.4 回滚边界

Flyway 社区版不支持自动回滚。交付约定：

1. **向前修复**为默认策略，新增版本修正数据或结构。
2. 破坏性变更（删列、改类型）必须先经“新增列 → 双写 → 回填 → 切读 → 删旧列”多版本过渡。
3. 数据库整体回滚依赖交付前的物理备份，见 [部署指南](../../05-operations/deployment.md) §13 与 [备份脚本](../../../scripts/backup/)。
4. 应用回滚不自动回滚数据库；旧版本应用必须能读取新结构（向后兼容）。

---

## 3. 配置变更控制

### 3.1 配置分层

| 层 | 位置 | 变更要求 |
| --- | --- | --- |
| 代码默认值 | `application.yml` 的 `${VAR:default}` | 随代码评审；默认值改动需评估所有部署 |
| 环境变量 | `.env` / 部署注入 | 交付时提供 `.env.example` 对照 |
| 配置中心 | Nacos `config/nacos/` | 变更需留记录，支持热更新的项单独说明 |
| 密钥 | 部署环境注入 | **禁止入库**，禁止写入日志 |

### 3.2 生产必改项

以下配置不得使用模板默认值直接上线：

| 项 | 要求 |
| --- | --- |
| `JWT_SECRET` | 满足最小长度，不使用示例值 |
| `DB_PASSWORD` / `REDIS_PASSWORD` | 强密码 |
| `APEX_DATA_ENCRYPTION_KEY` | 稳定保存，轮换需配套迁移 |
| `APEX_LICENSE_FILE` / 公钥 | 商业版必须配置有效授权 |
| `APEX_TRUSTED_PROXY_CIDRS` | 网关后方部署必须配置，否则限流按单一 IP 误伤 |
| CORS 允许来源 | 生产不得为通配 |

> 具体变量含义与校验行为见 [配置说明](../../05-operations/configuration.md) 与 [.env.example](../../../.env.example)。

### 3.3 配置变更影响评估

修改配置前确认：

1. 该配置是否被多个服务读取（如 `DB_HOST`、`REDIS_HOST`、`JWT_SECRET`）。
2. 是否有启动期校验会阻断启动（如密钥长度、License 模板值）。
3. 是否需要同步 `.env.example`、Nacos 配置与 [配置说明](../../05-operations/configuration.md)。
4. 是否影响已归档的部署记录。

---

## 4. 接口契约变更控制

### 4.1 契约分级

| 级别 | 范围 | 变更要求 |
| --- | --- | --- |
| 对外 API | `/api/v1/**`、`/api/v2/**`、`/admin-api/v1/**`、`/auth/v1/**` | 变更需版本化或保持向后兼容 |
| 内部服务 API | `/internal/v1/**` | 仅集群内直连，变更需同步调用方 |
| 跨服务契约 | [apex-api-contract](../../../apex-api-contract/src/main/java/com/apex/scaffold/contract/) | Feign 接口与事件 DTO，需双方同步发布 |
| 管理端契约 | 前端独立仓库 | 菜单组件字段与 API 需与前端对齐 |

### 4.2 不兼容变更的判定

以下变更视为不兼容：

- 删除或重命名字段、路径、参数
- 修改字段类型、必填性、枚举取值
- 修改响应包装结构（`Result`）
- 修改错误码语义
- 修改分页字段名（仓库存在多份 `PageResponse`，字段不完全一致）

### 4.3 变更检查

| 检查 | 工具/测试 |
| --- | --- |
| 路由与 SpringDoc 声明一致 | `RouterOpenApiCoverageTest` |
| 路由登记完整 | [API_ROUTES.md](../../03-api/API_ROUTES.md) 与 `check-doc-consistency.sh` |
| Feign 契约签名 | `FeignContractSignatureTest` |
| 事件契约 | `WorkflowEventContractTest` |
| OpenAPI 快照 | [docs/03-api/openapi/](../../03-api/openapi/) 归档对比 |

> OpenAPI 归档当前为 1.2.0（2026-10-01），晚于该日期的接口变更**没有**快照对比。交付前应重新导出。

---

## 5. 代码与依赖变更

| 项 | 规则 |
| --- | --- |
| 新增内部依赖 | 先确认目标模块 POM 的实际依赖方向，不机械套用“业务库都依赖 infrastructure” |
| 新增第三方依赖 | 评估 License、体积、是否可被 basic 档裁剪 |
| 共享模块改动 | `apex-common`、`apex-domain`、`apex-api-contract` 不得反向依赖具体业务库 |
| 循环依赖 | 禁止；共享 domain 只放跨域契约 |
| 已退役模块 | 不得重新引入 `apex-interfaces`、`apex-application`、`apex-auth-center` 作为运行入口 |

---

## 6. 提交与发布流程

### 6.1 提交规范

- 使用 Conventional Commits：`type(scope): 描述`。
- 按主题分组提交，一次提交只做一件事。
- 提交前检查暂存范围，不混入无关改动与构建产物。
- 不提交 `.env`、私钥、证书、真实配置。

### 6.2 发布流程

```
1. 确认工作区干净（git status 无未提交改动）
2. 确认 CHANGELOG 已更新
3. 打 Git 标签 v<版本>
4. 全量门禁 mvnd -T4 -B clean verify
5. 生成三档产物并校验
6. 生成 SHA-256 校验和
7. 按交付物清单核对包内容
8. 归档发布记录
```

### 6.3 发布记录字段

每次发布记录：版本号、Git 提交、构建时间、JDK/mvnd 版本、档位、测试统计、已知问题、SHA-256。

---

## 7. 变更审批

| 变更类型 | 审批 |
| --- | --- |
| 补丁版本（缺陷修复） | 技术负责人 |
| 次版本（功能新增） | 技术负责人 + 产品 |
| 主版本（不兼容变更） | 架构负责人 + 产品 |
| 数据库破坏性变更 | 架构负责人 + 运维 |
| 生产配置密钥轮换 | 运维负责人 |
| 交付范围增减 | 产品 + 技术负责人，并更新 [交付范围与基线](./delivery-scope.md) |

---

**维护者**: Apex Team
**下次核对**: 每个发布版本
