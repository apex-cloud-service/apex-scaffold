# 🚀 Grafana Dashboard 快速导入

只需 3 步，将缓存监控 Dashboard 导入到你的 Grafana！

## 📋 前置要求

- ✅ Grafana 7.0+ 正在运行
- ✅ Prometheus 已配置并采集应用指标
- ✅ 已有 Grafana 管理员权限或 API Key

## 🎯 最快方法（推荐）

### 使用自动化脚本

```bash
cd /Users/work/apex-scaffold

# 使用 API Key（推荐）
export GRAFANA_URL="http://localhost:3000"
export GRAFANA_API_KEY="your-api-key-here"
./scripts/ops/monitoring/grafana/import-dashboard.sh

# 或使用用户名密码
export GRAFANA_URL="http://localhost:3000"
export GRAFANA_USER="admin"
export GRAFANA_PASSWORD="admin"
./scripts/ops/monitoring/grafana/import-dashboard.sh
```

**完成！** 访问输出的 Dashboard URL 即可查看监控。

---

## 🖱️ UI 手动导入

如果你更喜欢可视化操作：

1. **登录 Grafana**: `http://localhost:3000`
2. **进入导入**: 左侧菜单 **+** → **Import**
3. **上传文件**: 选择 `docs/cache/grafana-cache-dashboard-optimized.json`
4. **选择数据源**: Prometheus
5. **导入**: 点击 **Import** 按钮

**完成！** Dashboard 自动打开。

---

## 📊 验证成功

导入后，你应该看到 **12 个监控面板**：

✅ 缓存命中率总览  
✅ 布隆过滤器容量  
✅ 缓存 QPS  
✅ P95 锁等待时间  
✅ TTL Jitter 统计  
✅ 缓存异常告警面板（新增）  
✅ 缓存穿透防护详情  
✅ 缓存击穿防护详情  
✅ 缓存请求流量分析  
✅ 分布式锁性能监控  
✅ 缓存预热状态  
✅ Redis 连接池监控  

---

## 🔧 后续配置（可选）

### 1. 配置告警通知

**Alerting** → **Contact points** → **New contact point**

**钉钉示例：**
```yaml
Name: dingtalk-cache-alerts
Type: DingDing
URL: https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
```

### 2. 调整告警阈值

根据实际业务调整：
- 缓存命中率: 当前 < 75%，建议范围 70-80%
- 锁等待时间: 当前 > 1000ms，建议范围 500-2000ms

### 3. 使用环境过滤器

顶部下拉框可以按环境过滤：
- All（默认，查看所有环境）
- dev / test / staging / prod

---

## ❓ 常见问题

### Dashboard 导入成功但无数据？

**检查 Prometheus 是否采集到指标：**
```bash
curl http://localhost:8080/actuator/prometheus | grep apex_cache
curl http://localhost:9090/api/v1/targets
```

**检查 Grafana 数据源：**
- Configuration → Data Sources → Prometheus
- 点击 **Save & Test**

### 告警不触发？

1. 检查告警规则状态：**Alerting** → **Alert rules**
2. 配置 Contact point 和 Notification policy
3. 使用 **Test rule** 功能测试

### 需要更详细的说明？

查看完整文档：
- [详细导入指南](./GRAFANA_IMPORT_GUIDE.md)
- [Grafana 配置指南](./GRAFANA_SETUP_GUIDE.md)
- [Dashboard 优化说明](./GRAFANA_DASHBOARD_OPTIMIZATION.md)

---

## 📦 文件清单

- **Dashboard 配置**: `docs/cache/grafana-cache-dashboard-optimized.json`
- **导入脚本**: `scripts/ops/monitoring/grafana/import-dashboard.sh`
- **详细文档**: `docs/cache/GRAFANA_*.md`

---

## 🎉 享受监控！

Dashboard 已经过生产环境验证，包含：
- ✅ 4 个内置告警规则
- ✅ 12 个优化的监控面板
- ✅ 环境过滤和应用重启标记
- ✅ 合理的阈值和配色方案

有问题？查看 [完整导入指南](./GRAFANA_IMPORT_GUIDE.md) 或提交 Issue。
