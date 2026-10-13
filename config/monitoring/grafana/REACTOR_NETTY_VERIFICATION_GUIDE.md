# Reactor Netty 指标验证指南

**验证时间**：2026-09-07  
**配置状态**：✅ 已完成（application.yml 已添加 `management.metrics.enable.reactor.netty: true`）

---

## ⚠️ 前置条件检查

在验证 Reactor Netty 指标前，请确保以下服务正常运行：

### 1. Podman 运行状态

```bash
podman ps
```

**预期结果**：能够看到容器列表，且 Podman 连接正常。

**如果失败（macOS）**：
```bash
# 查看已有虚拟机；尚未创建时先执行 podman machine init
podman machine list
podman machine start
podman ps
```

---

### 2. Redis 容器状态

```bash
podman-compose -f /Users/work/apex-scaffold/podman-compose.infra.yml ps redis
```

**预期结果**：
```
NAME                 STATUS
apex-infra-redis     Up X minutes (healthy)
```

**如果失败（容器未运行）**：
```bash
cd /Users/work/apex-scaffold
podman-compose -f podman-compose.infra.yml up -d redis
```

---

### 3. 应用启动状态

```bash
curl -s http://localhost:8081/actuator/health | jq .status
```

**预期结果**：`"UP"`

**如果失败（应用未运行）**：

#### 选项 A：前台运行（推荐，便于观察日志）

```bash
cd /Users/work/apex-scaffold
mvnd spring-boot:run -pl apex-interfaces
```

等待看到日志：
```
Started Application in X.XXX seconds
Netty started on port 8080
```

#### 选项 B：后台运行

```bash
cd /Users/work/apex-scaffold
nohup mvnd spring-boot:run -pl apex-interfaces > app.log 2>&1 &
```

监控启动日志：
```bash
tail -f app.log
```

---

## 🔍 Reactor Netty 指标验证

### 步骤 1：检查指标端点可用性

```bash
curl -s http://localhost:8081/actuator/prometheus | wc -l
```

**预期结果**：应该看到 100+ 行指标数据

---

### 步骤 2：验证 Reactor Netty 指标存在

```bash
curl -s http://localhost:8081/actuator/prometheus | grep "^reactor_netty" | head -20
```

**预期结果**（应该看到以下类型的指标）：

```
# EventLoop 指标
reactor_netty_eventloop_threads{...} 16
reactor_netty_eventloop_pending_tasks{...} 0

# 连接池指标
reactor_netty_connection_provider_active_connections{...} 0
reactor_netty_connection_provider_pending_connections{...} 0
reactor_netty_connection_provider_idle_connections{...} 2
reactor_netty_connection_provider_total_connections{...} 2

# ByteBuf 内存指标
reactor_netty_bytebuf_allocator_used_direct_memory{...} 1048576
reactor_netty_bytebuf_allocator_used_heap_memory{...} 0
```

---

### 步骤 3：统计 Reactor Netty 指标数量

```bash
curl -s http://localhost:8081/actuator/prometheus | grep "^reactor_netty" | wc -l
```

**预期结果**：应该看到 **10-20 个** Reactor Netty 指标

**如果结果为 0**：说明指标未启用，需要检查：
1. application.yml 配置是否正确
2. 应用是否重启以加载新配置
3. 是否有配置覆盖（如 application-dev.yml）

---

### 步骤 4：验证指标在 Prometheus 中可见

```bash
curl -s 'http://localhost:9090/api/v1/query?query=reactor_netty_eventloop_threads' | jq .
```

**预期结果**：
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "reactor_netty_eventloop_threads",
          "application": "apex-scaffold",
          "instance": "host.docker.internal:8081",
          "job": "apex-scaffold",
          "name": "reactor-http"
        },
        "value": [
          1725723600,
          "16"
        ]
      }
    ]
  }
}
```

**如果 result 数组为空**：
- Prometheus 可能还未采集到数据（等待 15 秒后重试）
- 检查 Prometheus 配置：`curl -s http://localhost:9090/api/v1/targets`

---

### 步骤 5：在 Grafana 中查看增强监控面板

1. **访问 Grafana**：http://localhost:3000
   - 用户名：`admin`
   - 密码：`admin`

2. **打开增强监控面板**：http://localhost:3000/d/apex-enhanced

3. **检查面板数据**：

   **✅ 应该有数据的面板（上半部分 - WebFlux 网关）**：
   - WebFlux EventLoop 线程
   - Reactor 待处理任务
   - WebFlux 活跃连接数
   - HTTP 服务器连接池详情
   - Netty ByteBuf 内存使用

   **✅ 应该有数据的面板（下半部分 - JVM）**：
   - JVM Heap 内存详情
   - JVM NonHeap 内存详情
   - GC 暂停时间分位数
   - GC 频率
   - JVM 线程状态
   - JVM 类加载
   - JVM 缓冲池

4. **生成测试流量**（可选，使增强监控数据更明显）：

```bash
# 连续发送 100 个请求
for i in {1..100}; do 
  curl -s http://localhost:8080/api/v1/auth/captcha > /dev/null
  sleep 0.1
done
```

然后刷新 Grafana 面板，应该能看到：
- EventLoop 待处理任务数量波动
- 活跃连接数增加
- ByteBuf 内存使用变化

---

## 📊 完整验证脚本

将以下脚本保存为 `verify_reactor_metrics.sh`：

```bash
#!/bin/bash
set -e

echo "=== Reactor Netty 指标验证脚本 ==="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. 检查应用健康状态
echo "1. 检查应用健康状态..."
if curl -s -f http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 应用运行正常${NC}"
else
    echo -e "${RED}✗ 应用未运行或不健康${NC}"
    echo "   请先启动应用：mvnd spring-boot:run -pl apex-interfaces"
    exit 1
fi

echo ""

# 2. 检查 Reactor Netty 指标
echo "2. 检查 Reactor Netty 指标..."
METRIC_COUNT=$(curl -s http://localhost:8081/actuator/prometheus | grep "^reactor_netty" | wc -l | tr -d ' ')

if [ "$METRIC_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ 找到 $METRIC_COUNT 个 Reactor Netty 指标${NC}"
    echo ""
    echo "指标示例："
    curl -s http://localhost:8081/actuator/prometheus | grep "^reactor_netty" | head -10
else
    echo -e "${RED}✗ 未找到 Reactor Netty 指标${NC}"
    echo "   可能原因："
    echo "   1. application.yml 中未启用：management.metrics.enable.reactor.netty: true"
    echo "   2. 应用未重启以加载新配置"
    exit 1
fi

echo ""

# 3. 检查 Prometheus 采集
echo "3. 检查 Prometheus 采集状态..."
PROM_RESULT=$(curl -s 'http://localhost:9090/api/v1/query?query=reactor_netty_eventloop_threads' | jq -r '.data.result | length')

if [ "$PROM_RESULT" -gt 0 ]; then
    echo -e "${GREEN}✓ Prometheus 已采集到 Reactor Netty 指标${NC}"
else
    echo -e "${YELLOW}⚠ Prometheus 尚未采集到指标（可能需要等待 15 秒）${NC}"
fi

echo ""

# 4. 检查关键指标值
echo "4. 关键指标值："
echo ""

echo "   EventLoop 线程数："
curl -s http://localhost:8081/actuator/prometheus | grep "reactor_netty_eventloop_threads{" | head -1

echo ""
echo "   活跃连接数："
curl -s http://localhost:8081/actuator/prometheus | grep "reactor_netty_connection_provider_active_connections{" | head -1

echo ""
echo "   ByteBuf Direct 内存："
curl -s http://localhost:8081/actuator/prometheus | grep "reactor_netty_bytebuf_allocator_used_direct_memory{" | head -1

echo ""

# 5. Grafana 访问提示
echo "5. Grafana 面板访问："
echo -e "   ${GREEN}http://localhost:3000/d/apex-enhanced${NC}"
echo "   用户名：admin / 密码：admin"

echo ""
echo "=== 验证完成 ==="
```

运行脚本：
```bash
chmod +x verify_reactor_metrics.sh
./verify_reactor_metrics.sh
```

---

## ❌ 常见问题排查

### 问题 1：应用无法启动

**症状**：`Connection refused: localhost/127.0.0.1:6379`

**原因**：Redis 未运行

**解决**：
```bash
podman-compose -f /Users/work/apex-scaffold/podman-compose.infra.yml up -d redis
```

---

### 问题 2：没有 Reactor Netty 指标

**症状**：`curl http://localhost:8081/actuator/prometheus | grep reactor_netty` 无输出

**排查步骤**：

1. 检查配置文件：
```bash
grep -A 3 "metrics:" /Users/work/apex-scaffold/apex-interfaces/src/main/resources/application.yml
```

应该看到：
```yaml
metrics:
  enable:
    reactor.netty: true
```

2. 确认应用已重启并加载新配置
3. 检查是否有 profile 覆盖：
```bash
grep -r "reactor.netty" /Users/work/apex-scaffold/apex-interfaces/src/main/resources/
```

---

### 问题 3：Grafana 面板显示 "No Data"

**原因**：
1. Prometheus 尚未采集数据（等待 15-30 秒）
2. 应用未生成流量（EventLoop 等指标需要有请求才会更新）

**解决**：

发送测试请求：
```bash
for i in {1..50}; do curl -s http://localhost:8080/api/v1/auth/captcha > /dev/null; done
```

刷新 Grafana 面板。

---

## 📈 预期指标范围

正常运行时，Reactor Netty 指标应该在以下范围：

| 指标 | 正常范围 | 说明 |
|------|---------|------|
| `reactor_netty_eventloop_threads` | 8-32 | 通常为 CPU 核心数 × 2 |
| `reactor_netty_eventloop_pending_tasks` | 0-100 | 待处理任务数，过高说明积压 |
| `reactor_netty_connection_provider_active_connections` | 0-500 | 活跃连接数 |
| `reactor_netty_connection_provider_idle_connections` | 1-50 | 空闲连接数 |
| `reactor_netty_bytebuf_allocator_used_direct_memory` | 0-100MB | Direct 内存使用 |
| `reactor_netty_bytebuf_allocator_used_heap_memory` | 0-50MB | Heap 内存使用 |

---

## ✅ 验证成功标志

当你看到以下所有项都正常时，说明 Reactor Netty 指标已成功启用：

- [ ] 应用健康检查返回 UP
- [ ] `curl http://localhost:8081/actuator/prometheus | grep reactor_netty | wc -l` > 10
- [ ] Prometheus 查询 `reactor_netty_eventloop_threads` 有结果
- [ ] Grafana 增强监控面板的 WebFlux 部分（上半部分 6 个面板）显示数据
- [ ] Grafana 增强监控面板的 JVM 部分（下半部分 7 个面板）显示数据

完成以上所有检查项后，Reactor Netty 增强监控已完全就绪！🎉

---

**文档维护者**：Apex Team  
**最后更新**：2026-09-07  
**相关文档**：
- [增强监控面板使用指南](./README-ENHANCED.md)
- [指标验证报告](./METRICS_VALIDATION_REPORT.md)
