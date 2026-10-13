# Nacos 配置分层模板（config/nacos/）

本目录是微服务化后 Nacos 配置中心的**模板仓库**，对应迁移方案
[§12.1 Nacos 配置分层](../../docs/microservice-migration-plan.md) 与
执行计划 Phase 1.4。模板本身不参与应用启动，仅作为发布到 Nacos 的
Data ID 内容基线，便于评审、版本管理与一键发布。

> ⚠️ **密钥禁令**：本目录内所有文件**禁止**出现任何明文密钥（JWT 私钥、
> 数据库口令、Redis 口令等）。敏感值一律通过环境变量 / KMS 注入，
> 模板中仅保留 `${ENV_VAR:default}` 占位符。评审与 CI 会校验此约定。

---

## 一、命名空间（namespace）与分组（group）

| 环境 | namespace | 说明 |
|------|-----------|------|
| 本地开发 | `public` | 默认公共空间，对应 `.env.example` 的 `NACOS_NAMESPACE` 留空 |
| 开发联调 | `dev` | `NACOS_NAMESPACE=dev` |
| 生产 | `prod` | `NACOS_NAMESPACE=prod` |

- 配置分组统一 `DEFAULT_GROUP`（Sentinel 规则组为 `SENTINEL_GROUP`）。
- 命名空间与分组均由 `bootstrap.yml` 的
  `spring.cloud.nacos.config.namespace/group` 决定，支持环境变量覆盖。

---

## 二、Data ID 清单

> 约定：`{service}` = `spring.application.name`，即 Nacos 注册名，也是网关
> `lb://{service}` 的解析目标。服务名一律使用 `apex-xxx-service` 形式。

### 2.1 共享配置（多服务共同引用）

| Data ID | 引用服务 | 内容 | 模板文件 |
|---------|----------|------|----------|
| `apex-common.yaml` | 全部微服务 | Result/i18n 开关、日志级别基线、JWKs 地址、网关公共头约定 | [apex-common.yaml](./apex-common.yaml) |
| `apex-datasource-dev.yaml` | 全部有库服务（dev） | dev 环境数据源连接（无密钥，口令走环境变量） | [apex-datasource-dev.yaml](./apex-datasource-dev.yaml) |
| `apex-datasource-prod.yaml` | 全部有库服务（prod） | prod 环境数据源连接（无密钥，口令走环境变量） | [apex-datasource-prod.yaml](./apex-datasource-prod.yaml) |

### 2.2 服务私有配置（每服务一个）

| Data ID | 服务 | 模板文件 |
|---------|------|----------|
| `apex-gateway.yaml` | `apex-gateway` | [apex-gateway.yaml](./apex-gateway.yaml) |
| `apex-auth-service.yaml` | `apex-auth-service` | 见下方「发布说明」按需创建 |
| `apex-system-service.yaml` | `apex-system-service` | 见下方「发布说明」按需创建 |
| `apex-workflow-service.yaml` | `apex-workflow-service` | 见下方「发布说明」按需创建 |
| `apex-office-service.yaml` | `apex-office-service` | 见下方「发布说明」按需创建 |
| `apex-file-service.yaml` | `apex-file-service` | 见下方「发布说明」按需创建 |

> 💡 服务私有的 `apex-{service}.yaml` 模板**按 Phase 需要增量新增**：本
> Phase（1.4）先落地 `apex-gateway.yaml` 与两个共享模板；auth/system/
> workflow/office/file 各自的服务私有配置分别在 Phase 2–6 引入并补充到本表。

### 2.3 Sentinel 规则（可选，组 `SENTINEL_GROUP`）

| Data ID | 服务 | 模板文件 | 引用配置 |
|---------|------|----------|----------|
| `{service}-flow-rules` | 各微服务 | [sentinel/](./sentinel/) | `apex.sentinel.nacos.flow-data-id` |
| `{service}-degrade-rules` | 各微服务 | [sentinel/](./sentinel/) | `apex.sentinel.nacos.degrade-data-id` |

> ⚠️ Sentinel 规则推送为**整体替换**语义：发布时必须提交**完整规则集**，
> 不能只发增量。现有单体规则见 [`config/sentinel/`](../sentinel/README.md)。

---

## 三、加载顺序与优先级

```text
bootstrap.yml
  -> Nacos 远端配置（apex-common.yaml -> apex-datasource-{env}.yaml -> apex-{service}.yaml）
  -> application.yml
  -> application-{profile}.yml
```

- **优先级**：环境变量 > Nacos 远端配置 > 本地文件默认值。
- 若暂未部署 Nacos，通过 `NACOS_CONFIG_ENABLED=false` 关闭配置中心，
  应用回退到本地 `application.yml`，行为与单体一致（零影响）。

---

## 四、发布方式

### 4.1 Nacos 控制台

配置管理 → 配置列表 → 选择对应 namespace → 新建配置，填写：

- **Data ID**：见上表（如 `apex-gateway.yaml`）
- **Group**：`DEFAULT_GROUP`（Sentinel 规则为 `SENTINEL_GROUP`）
- **配置格式**：`YAML`（Sentinel 规则为 `JSON`）
- **配置内容**：对应模板文件内容

### 4.2 CLI（curl）

```bash
# 发布共享配置 apex-common.yaml（本地 infra 栈 127.0.0.1:18848）
curl -X POST "http://127.0.0.1:18848/nacos/v1/cs/configs" \
  --data-urlencode "dataId=apex-common.yaml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content@config/nacos/apex-common.yaml"

# 发布网关私有配置 apex-gateway.yaml
curl -X POST "http://127.0.0.1:18848/nacos/v1/cs/configs" \
  --data-urlencode "dataId=apex-gateway.yaml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content@config/nacos/apex-gateway.yaml"
```

> 💡 `--data-urlencode "content@<file>"` 从文件读取内容，避免 shell 转义问题。

---

## 五、本地联调（无 Nacos）

1. 保持 `.env.example` 默认：`NACOS_CONFIG_ENABLED=false`、
   `NACOS_DISCOVERY_ENABLED=false`、`NACOS_REGISTER_ENABLED=false`。
2. 各服务使用本地 `application.yml` 的默认值即可启动。
3. 接入本地 infra Nacos 栈（`127.0.0.1:18848`）时，将上述开关置 `true`
   并注入 `NACOS_SERVER_ADDR` / `NACOS_NAMESPACE`。

---

## 六、关联文档

- 迁移方案：[`docs/microservice-migration-plan.md`](../../docs/microservice-migration-plan.md) §12
- 配置项详解：[`docs/05-operations/configuration.md`](../../docs/05-operations/configuration.md)
- 环境变量清单：[`docs/05-operations/configuration-inventory.md`](../../docs/05-operations/configuration-inventory.md)
- Sentinel 规则模板：[`config/sentinel/README.md`](../sentinel/README.md)
