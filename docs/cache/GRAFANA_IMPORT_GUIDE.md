# Grafana Dashboard 导入操作指南

本文档提供将缓存监控 Dashboard 导入到现有 Grafana 平台的详细步骤。

## 📋 导入前准备

### 1. 确认 Grafana 版本

```bash
# 检查 Grafana 版本
curl http://localhost:3000/api/health

# 或登录 Grafana 查看左下角版本信息
```

**要求：Grafana 7.0 及以上**

### 2. 确认 Prometheus 数据源

登录 Grafana → **Configuration** → **Data Sources**

检查是否已配置 Prometheus 数据源：
- ✅ 已配置：记录数据源名称（如 `Prometheus`）
- ❌ 未配置：先配置 Prometheus 数据源

**配置 Prometheus 数据源：**

```yaml
Name: Prometheus
Type: Prometheus
URL: http://localhost:9090
Access: Server (Default)
```

保存并测试连接。

### 3. 准备认证信息

选择以下一种认证方式：

**方式 1：API Key（推荐）**
1. 登录 Grafana
2. **Configuration** → **API Keys**
3. **Add API key**
   - Name: `dashboard-import`
   - Role: `Editor` 或 `Admin`
   - Time to live: 根据需要设置
4. 复制生成的 API Key

**方式 2：用户名密码**
- 默认：`admin / admin`
- 或使用你的 Grafana 账号密码

---

## 🚀 导入方法

### 方法 1：自动化脚本导入（推荐）

使用我们提供的自动化脚本快速导入。

#### 步骤 1：赋予执行权限

```bash
cd /Users/work/apex-scaffold
chmod +x scripts/ops/monitoring/grafana/import-dashboard.sh
```

#### 步骤 2：使用 API Key 导入

```bash
export GRAFANA_URL="http://localhost:3000"
export GRAFANA_API_KEY="your-api-key-here"

./scripts/ops/monitoring/grafana/import-dashboard.sh
```

#### 步骤 3：或使用用户名密码导入

```bash
export GRAFANA_URL="http://localhost:3000"
export GRAFANA_USER="admin"
export GRAFANA_PASSWORD="admin"

./scripts/ops/monitoring/grafana/import-dashboard.sh
```

#### 预期输出

```
=== Grafana Dashboard 导入工具 ===

Dashboard 文件: /Users/work/apex-scaffold/docs/cache/grafana-cache-dashboard-optimized.json
Grafana 地址: http://localhost:3000

检查 Grafana 连接...
✓ Grafana 连接成功

使用 API Key 认证
读取 Dashboard 配置...
正在导入 Dashboard...
✓ Dashboard 导入成功！

Dashboard 地址: http://localhost:3000/d/xxx/apex-scaffold-cache-monitoring-v2
Dashboard ID: 123

=== 导入完成 ===
后续步骤：
  1. 访问 Dashboard URL 查看监控面板
  2. 配置 Prometheus 数据源（如果尚未配置）
  3. 配置告警通知渠道（钉钉/企业微信/Slack）
  4. 根据实际需要调整告警阈值
```

---

### 方法 2：Grafana UI 手动导入

最直观的方法，适合初次使用。

#### 步骤 1：登录 Grafana

访问：`http://localhost:3000`（或你的 Grafana 地址）

#### 步骤 2：进入导入页面

1. 点击左侧菜单 **+**（或 **Dashboards**）
2. 选择 **Import**

#### 步骤 3：上传 Dashboard 文件

**选项 A：上传文件**
1. 点击 **Upload JSON file**
2. 选择文件：
   ```
   /Users/work/apex-scaffold/docs/cache/grafana-cache-dashboard-optimized.json
   ```
3. 点击 **Open**

**选项 B：粘贴 JSON**
1. 点击 **Or paste JSON**
2. 打开文件并复制全部内容：
   ```bash
   cat /Users/work/apex-scaffold/docs/cache/grafana-cache-dashboard-optimized.json
   ```
3. 粘贴到文本框

#### 步骤 4：配置数据源

1. 在导入页面，找到 **Prometheus** 下拉框
2. 选择你的 Prometheus 数据源
3. 点击 **Import**

#### 步骤 5：验证导入

Dashboard 会自动打开，你应该看到 12 个监控面板。

---

### 方法 3：cURL 命令导入

适合在服务器上通过命令行操作。

#### 使用 API Key

```bash
# 设置变量
GRAFANA_URL="http://localhost:3000"
GRAFANA_API_KEY="your-api-key-here"
DASHBOARD_FILE="/Users/work/apex-scaffold/docs/cache/grafana-cache-dashboard-optimized.json"

# 读取 Dashboard JSON
DASHBOARD_JSON=$(cat "$DASHBOARD_FILE")

# 包装成 API 格式
IMPORT_JSON=$(cat <<EOF
{
  "dashboard": $(echo "$DASHBOARD_JSON" | jq '.dashboard'),
  "overwrite": true,
  "inputs": [],
  "folderId": 0
}
EOF
)

# 导入
curl -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRAFANA_API_KEY" \
  -d "$IMPORT_JSON" \
  "${GRAFANA_URL}/api/dashboards/db"
```

#### 使用用户名密码

```bash
GRAFANA_URL="http://localhost:3000"
GRAFANA_USER="admin"
GRAFANA_PASSWORD="admin"
DASHBOARD_FILE="/Users/work/apex-scaffold/docs/cache/grafana-cache-dashboard-optimized.json"

# 读取并包装 JSON
DASHBOARD_JSON=$(cat "$DASHBOARD_FILE")
IMPORT_JSON=$(cat <<EOF
{
  "dashboard": $(echo "$DASHBOARD_JSON" | jq '.dashboard'),
  "overwrite": true,
  "inputs": [],
  "folderId": 0
}
EOF
)

# 导入（使用 Basic Auth）
curl -X POST \
  -H "Content-Type: application/json" \
  -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
  -d "$IMPORT_JSON" \
  "${GRAFANA_URL}/api/dashboards/db"
```

---

### 方法 4：Podman Compose Provisioning

适合容器化部署，支持自动导入。

#### 步骤 1：创建 Provisioning 配置

在现有 `config/monitoring/grafana/provisioning/dashboards/` 目录下配置 `apex-cache.yaml`：

```yaml
apiVersion: 1

providers:
  - name: 'Apex Cache Monitoring'
    orgId: 1
    folder: 'Apex Scaffold'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards/apex-cache
```

#### 步骤 2：更新 config/monitoring/podman-compose.yml（挂载路径相对此文件）

```yaml
version: '3.8'

services:
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      # Dashboard 配置文件
      - ../../docs/cache/grafana-cache-dashboard-optimized.json:/etc/grafana/provisioning/dashboards/apex-cache/dashboard.json:ro
      # Provisioning 配置
      - ./grafana/provisioning/dashboards:/etc/grafana/provisioning/dashboards:ro
      # 数据持久化
      - grafana-data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_INSTALL_PLUGINS=
    depends_on:
      - prometheus

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'

volumes:
  grafana-data:
  prometheus-data:
```

#### 步骤 3：启动服务（项目根目录）

```bash
podman-compose -f config/monitoring/podman-compose.yml up -d
```

Dashboard 会在 Grafana 启动时自动导入。

---

### 方法 5：Kubernetes ConfigMap

适合 K8s 环境部署。

#### 步骤 1：创建 ConfigMap

```bash
kubectl create configmap grafana-dashboard-cache \
  --from-file=/Users/work/apex-scaffold/docs/cache/grafana-cache-dashboard-optimized.json \
  -n monitoring
```

#### 步骤 2：创建 Dashboard Provider ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboard-provider
  namespace: monitoring
data:
  apex-cache.yaml: |
    apiVersion: 1
    providers:
      - name: 'Apex Cache'
        orgId: 1
        folder: 'Apex Scaffold'
        type: file
        disableDeletion: false
        updateIntervalSeconds: 30
        options:
          path: /etc/grafana/provisioning/dashboards/apex-cache
```

```bash
kubectl apply -f grafana-dashboard-provider-configmap.yaml
```

#### 步骤 3：更新 Grafana Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: monitoring
spec:
  template:
    spec:
      containers:
      - name: grafana
        image: grafana/grafana:latest
        volumeMounts:
        - name: dashboard-cache
          mountPath: /etc/grafana/provisioning/dashboards/apex-cache
        - name: dashboard-provider
          mountPath: /etc/grafana/provisioning/dashboards
      volumes:
      - name: dashboard-cache
        configMap:
          name: grafana-dashboard-cache
      - name: dashboard-provider
        configMap:
          name: grafana-dashboard-provider
```

```bash
kubectl apply -f grafana-deployment.yaml
kubectl rollout restart deployment/grafana -n monitoring
```

---

## ✅ 验证导入成功

### 1. 检查 Dashboard 列表

登录 Grafana → **Dashboards** → **Manage**

查找：**Apex Scaffold 缓存监控 v2**

### 2. 访问 Dashboard

点击 Dashboard 名称，应该看到 12 个面板：

- ✅ 📊 缓存命中率总览
- ✅ 💾 布隆过滤器容量
- ✅ 📈 缓存 QPS
- ✅ ⏱️ P95 锁等待时间
- ✅ 🔄 TTL Jitter 统计
- ✅ ⚠️ 缓存异常告警面板
- ✅ 🛡️ 缓存穿透防护详情
- ✅ 🔒 缓存击穿防护详情
- ✅ 📊 缓存请求流量分析
- ✅ ⏱️ 分布式锁性能监控
- ✅ 🔥 缓存预热状态
- ✅ 🔌 Redis 连接池监控

### 3. 检查环境变量

顶部应该有一个 `environment` 下拉框，可以选择：
- All（默认）
- dev
- test
- staging
- prod

### 4. 检查告警规则

点击面板标题 → **Edit** → **Alert**

应该有 4 个告警规则已配置：
- 缓存命中率告警
- 锁等待时间过长
- 缓存异常综合告警
- 缓存预热失败告警

---

## 🔧 导入后配置

### 1. 选择数据源

如果导入时未选择数据源，可以：

1. 进入 Dashboard 设置（右上角 ⚙️）
2. **Variables** → `environment`
3. 选择 Prometheus 数据源
4. **Save dashboard**

### 2. 配置告警通知

**步骤 1：创建 Contact Point**

1. **Alerting** → **Contact points**
2. **New contact point**

**钉钉示例：**
```yaml
Name: dingtalk-cache-alerts
Type: DingDing
Webhook URL: https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
Message: {{ template "default.message" . }}
```

**步骤 2：配置 Notification Policy**

1. **Alerting** → **Notification policies**
2. 编辑 Default policy
3. 选择刚创建的 Contact point
4. **Save**

### 3. 调整告警阈值（可选）

根据实际业务情况调整：

**缓存命中率：**
- 当前: < 75%
- 建议范围: 70-80%
- 修改位置: Panel 1 → Edit → Alert → Conditions

**锁等待时间：**
- 当前: > 1000ms
- 建议范围: 500-2000ms
- 修改位置: Panel 4 → Edit → Alert → Conditions

### 4. 设置刷新间隔

右上角时间选择器旁边：
- 默认: 30s
- 可选: 10s, 1m, 5m
- 生产环境建议: 30s 或 1m

---

## ❌ 常见问题排查

### 问题 1：导入失败 - 401 Unauthorized

**原因：** 认证信息错误

**解决：**
```bash
# 检查 API Key 是否有效
curl -H "Authorization: Bearer YOUR_API_KEY" \
  http://localhost:3000/api/dashboards/home

# 或检查用户名密码
curl -u admin:admin \
  http://localhost:3000/api/dashboards/home
```

### 问题 2：导入成功但无数据

**原因：** Prometheus 未采集到指标或数据源配置错误

**解决：**

1. **检查 Prometheus 采集：**
   ```bash
   curl http://localhost:9090/api/v1/targets
   ```

2. **检查应用指标暴露：**
   ```bash
   curl http://localhost:8080/actuator/prometheus | grep apex_cache
   ```

3. **检查 Grafana 数据源：**
   - Configuration → Data Sources → Prometheus
   - 点击 **Save & Test**

### 问题 3：告警不触发

**检查清单：**
- [ ] Prometheus Alertmanager 是否配置
- [ ] Contact point 是否正确配置
- [ ] Notification policy 是否启用
- [ ] 告警条件是否满足

**测试告警：**
1. Dashboard → 编辑面板 → Alert
2. 点击 **Test rule**

### 问题 4：环境变量下拉框为空

**原因：** Prometheus 中没有 `environment` 标签

**解决：**

确保应用配置中有环境标签：

```yaml
# application.yml
management:
  metrics:
    tags:
      application: apex-scaffold
      environment: ${spring.profiles.active}
```

或修改 Dashboard 变量查询：

```promql
label_values(up{job="apex-scaffold"}, instance)
```

---

## 📖 相关文档

- [Grafana 配置指南](./GRAFANA_SETUP_GUIDE.md)
- [Dashboard 优化说明](./GRAFANA_DASHBOARD_OPTIMIZATION.md)
- [监控告警配置](./CACHE_MONITORING_ALERTS.md)
- [缓存使用指南](./CACHE_USAGE_GUIDE.md)

---

## 💡 推荐配置

**生产环境：**
- 使用方法 1（自动化脚本）或方法 4（Podman Compose Provisioning）
- 配置告警通知到钉钉或企业微信
- 设置刷新间隔为 30s 或 1m
- 启用环境变量过滤器

**测试环境：**
- 使用方法 2（UI 手动导入）
- 可选配置告警通知
- 刷新间隔可以设置为 10s

---

需要帮助？
- 查看脚本日志排查问题
- 参考 Grafana 官方文档
- 提交 Issue 到项目仓库
