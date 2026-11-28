# Sentinel 规则 JSON 模板（Nacos 动态下发）

启用 `apex.sentinel.nacos.enabled=true` 后，将以下文件内容分别发布到 Nacos 配置中心：

| 文件 | Nacos DataId | 说明 |
|------|-------------|------|
| `apex-sentinel-flow-rules.json` | `apex-sentinel-flow-rules` | 限流规则（FlowRule JSON 数组） |
| `apex-sentinel-degrade-rules.json` | `apex-sentinel-degrade-rules` | 熔断规则（DegradeRule JSON 数组） |

- 配置分组：`SENTINEL_GROUP`（可用 `SENTINEL_NACOS_GROUP` 环境变量覆盖）
- 命名空间：默认公共空间（用 `NACOS_NAMESPACE` 指定）
- 发布后约 30 秒内热生效，无需重启应用
- ⚠️ Nacos 推送会**整体替换**对应规则集合，请维护完整规则集
  （模板已按 `SentinelConfig` 当前实现同步：6 条限流规则、2 条降级规则，BE-071/BE-072 清理后；异常比例与慢调用比例规则可针对同一资源同时存在）

## 发布方式（Nacos 控制台或 CLI）

```bash
# 方式一：Nacos 控制台 → 配置管理 → 新建配置
#   Data ID: apex-sentinel-flow-rules / apex-sentinel-degrade-rules
#   Group: SENTINEL_GROUP
#   格式: JSON

# 方式二：curl 发布（本地 infra 栈 127.0.0.1:18848）
curl -X POST "http://127.0.0.1:18848/nacos/v1/cs/configs" \
  -d "dataId=apex-sentinel-flow-rules&group=SENTINEL_GROUP&content=$(cat apex-sentinel-flow-rules.json)"
```

## 字段说明

### FlowRule（限流）
| 字段 | 说明 |
|------|------|
| `resource` | 资源名（接口路径，路径变量需归一化如 `/api/v1/user/{userId}`） |
| `grade` | 1=QPS 限流 |
| `count` | QPS 阈值 |
| `limitApp` | 生效来源（`default` = 所有调用方） |
| `controlBehavior` | 0=直接拒绝 1=Warm Up 2=匀速排队 |

### DegradeRule（熔断）
| 字段 | 说明 |
|------|------|
| `grade` | 0=异常比例 1=异常数 2=慢调用比例 |
| `count` | 阈值（异常比例 0.0~1.0；RT 模式为毫秒） |
| `timeWindow` | 熔断持续秒数 |
| `minRequestAmount` | 触发熔断的最小请求数 |
| `slowRatioThreshold` | 慢调用比例阈值（grade=2 时使用） |
