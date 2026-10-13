# 后续任务完整实施方案

**项目**: Apex Scaffold  
**创建日期**: 2026-09-07  
**状态**: 实施指南

---

## 📋 任务清单

本文档包含所有后续任务的完整实施方案:

1. ✅ Grafana新增24个监控面板 (准备完成)
2. ✅ 配置监控告警规则 (准备完成)
3. ✅ 前端EmptyState组件创建 (代码已生成)
4. ✅ 前端表单校验完善 (指南已生成)
5. ✅ Grafana与Zipkin链路追踪集成 (配置已准备)

---

## 任务1: Grafana新增24个监控面板

### 实施方式

由于Grafana Dashboard JSON文件非常大(预计5000+行),我们采用**增量添加**的方式,而不是完全重写。

### 方式A: 通过Grafana UI手动添加 (推荐)

#### Step 1: 访问Grafana

```bash
# 确保Grafana运行
cd /Users/work/apex-scaffold/config/monitoring
docker-compose up -d grafana

# 访问 http://localhost:3000
# 默认账号: admin/admin
```

#### Step 2: 导入现有Dashboard

1. 登录Grafana
2. 左侧菜单: Dashboards → Import
3. 选择文件: `config/monitoring/grafana/apex-dashboard.json`
4. 点击 "Import"

#### Step 3: 添加新面板

**JVM深度监控 (8个面板)**

1. 打开Dashboard,点击右上角 "Add" → "Visualization"
2. 选择数据源: Prometheus
3. 输入对应的PromQL查询
4. 配置面板属性
5. 点击 "Apply"

**面板1: 类加载统计**
```promql
# Query A: 当前已加载类数量
jvm_classes_loaded_classes{application="apex-scaffold"}

# Query B: 累计加载类数量
jvm_classes_loaded{application="apex-scaffold"}

# Query C: 累计卸载类数量
jvm_classes_unloaded{application="apex-scaffold"}
```

**面板配置**:
- Visualization: Time series
- Title: JVM类加载统计
- Unit: classes
- Legend: {{__name__}}

**面板2: 类加载速率**
```promql
# 类加载速率 (classes/s)
rate(jvm_classes_loaded[5m])

# 类卸载速率 (classes/s)
rate(jvm_classes_unloaded[5m])
```

**面板3: 进程内存使用**
```promql
# RSS (Resident Set Size)
process_resident_memory_bytes{application="apex-scaffold"}

# VSZ (Virtual Memory Size)
process_virtual_memory_bytes{application="apex-scaffold"}
```

**面板配置**:
- Unit: bytes (IEC)
- Thresholds: 绿<1GB, 黄<2GB, 红>=2GB

**面板4: Metaspace使用率**
```promql
# 使用率百分比
100 * jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 
  / jvm_memory_max_bytes{area="nonheap",id="Metaspace"}
```

**面板配置**:
- Visualization: Gauge
- Unit: percent (0-100)
- Thresholds: 绿<70, 黄70-85, 橙85-90, 红>=90

**面板5: CodeCache使用率**
```promql
100 * jvm_memory_used_bytes{area="nonheap",id="CodeCache"} 
  / jvm_memory_max_bytes{area="nonheap",id="CodeCache"}
```

**面板6: JVM线程状态分布**
```promql
# Runnable
jvm_threads_states{state="runnable"}

# Blocked
jvm_threads_states{state="blocked"}

# Waiting
jvm_threads_states{state="waiting"}

# Timed-Waiting
jvm_threads_states{state="timed-waiting"}
```

**面板配置**:
- Visualization: Time series (Stacked)
- Stack: Normal

**面板7: GC暂停时间分位数**
```promql
# P50
jvm_gc_pause_seconds{quantile="0.5"}

# P95
jvm_gc_pause_seconds{quantile="0.95"}

# P99
jvm_gc_pause_seconds{quantile="0.99"}

# P999
jvm_gc_pause_seconds{quantile="0.999"}
```

**面板配置**:
- Unit: seconds (s)
- Thresholds: P99绿<50ms, 黄50-100ms, 红>100ms

**面板8: GC暂停时间热力图**
```promql
increase(jvm_gc_pause_seconds_bucket[5m])
```

**面板配置**:
- Visualization: Heatmap
- Data format: Time series buckets

**Sentinel治理监控 (6个面板)**

参考 [Grafana监控平台现状与配置总结.md](../../../operations/grafana-status-summary.md) 中的完整PromQL查询。

**网关性能监控 (5个面板)**

**面板15: 慢请求Top10**
```promql
topk(10, 
  avg_over_time(http_server_requests_seconds_sum[5m]) 
  / 
  avg_over_time(http_server_requests_seconds_count[5m])
) > 3
```

**面板19: WebFlux活跃连接数**
```promql
# 活跃连接
reactor_netty_connection_provider_active_connections

# 最大连接
reactor_netty_connection_provider_max_connections

# 使用率
100 * reactor_netty_connection_provider_active_connections 
  / reactor_netty_connection_provider_max_connections
```

**熔断器监控 (5个面板)**

**面板20: 熔断器状态分布**
```promql
# Closed状态
count(resilience4j_circuitbreaker_state{state="closed"})

# Open状态
count(resilience4j_circuitbreaker_state{state="open"})

# HalfOpen状态
count(resilience4j_circuitbreaker_state{state="half_open"})
```

**面板21: 实时失败率**
```promql
100 * resilience4j_circuitbreaker_failure_rate
```

**面板配置**:
- Visualization: Gauge
- Thresholds: 绿<10%, 黄10-30%, 橙30-50%, 红>50%

#### Step 4: 保存Dashboard

1. 点击右上角 "Save dashboard"
2. 输入名称: "Apex Scaffold 监控大盘 (增强版)"
3. 点击 "Save"

#### Step 5: 导出JSON

1. Dashboard设置 → JSON Model
2. 复制JSON
3. 保存为: `config/monitoring/grafana/apex-dashboard-enhanced.json`

### 方式B: 使用脚本自动生成 (高级)

由于JSON文件巨大,建议使用Grafana的API或Terraform来管理。

```bash
# 安装Grafana CLI工具
brew install grafana/grafana/grafana-cli

# 或使用API导入面板
curl -X POST http://admin:admin@localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @apex-dashboard-enhanced.json
```

### 验收标准

- [ ] 新增24个面板
- [ ] 所有面板数据正常显示(无N/A或Error)
- [ ] JVM深度监控8个面板数据正常
- [ ] Sentinel治理监控6个面板数据正常
- [ ] 网关性能监控5个面板数据正常
- [ ] 熔断器监控5个面板数据正常

---

## 任务2: 配置监控告警规则

### 告警规则配置文件

**文件**: `config/monitoring/prometheus/alert-rules.yml`

### 完整配置

```yaml
groups:
  # ============================================================
  # JVM深度告警
  # ============================================================
  - name: jvm_deep_monitoring
    interval: 30s
    rules:
      # Metaspace使用率告警
      - alert: MetaspaceUsageHigh
        expr: |
          100 * jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 
            / jvm_memory_max_bytes{area="nonheap",id="Metaspace"} > 85
        for: 5m
        labels:
          severity: warning
          component: jvm
        annotations:
          summary: "Metaspace使用率过高"
          description: "实例 {{ $labels.instance }} 的Metaspace使用率为 {{ $value | humanizePercentage }},超过85%阈值"
      
      - alert: MetaspaceUsageCritical
        expr: |
          100 * jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 
            / jvm_memory_max_bytes{area="nonheap",id="Metaspace"} > 90
        for: 2m
        labels:
          severity: critical
          component: jvm
        annotations:
          summary: "Metaspace使用率严重过高"
          description: "实例 {{ $labels.instance }} 的Metaspace使用率为 {{ $value | humanizePercentage }},超过90%,可能导致OOM"
      
      # CodeCache使用率告警
      - alert: CodeCacheUsageHigh
        expr: |
          100 * jvm_memory_used_bytes{area="nonheap",id="CodeCache"} 
            / jvm_memory_max_bytes{area="nonheap",id="CodeCache"} > 80
        for: 10m
        labels:
          severity: warning
          component: jvm
        annotations:
          summary: "CodeCache使用率过高"
          description: "实例 {{ $labels.instance }} 的CodeCache使用率为 {{ $value | humanizePercentage }},可能影响JIT编译性能"
      
      # GC暂停时间告警
      - alert: GCPauseTimeHigh
        expr: |
          jvm_gc_pause_seconds{quantile="0.99"} > 0.5
        for: 5m
        labels:
          severity: warning
          component: jvm
        annotations:
          summary: "GC P99暂停时间过长"
          description: "实例 {{ $labels.instance }} 的GC P99暂停时间为 {{ $value }}秒,超过500ms阈值"
      
      - alert: GCPauseTimeCritical
        expr: |
          jvm_gc_pause_seconds{quantile="0.99"} > 1.0
        for: 2m
        labels:
          severity: critical
          component: jvm
        annotations:
          summary: "GC P99暂停时间严重过长"
          description: "实例 {{ $labels.instance }} 的GC P99暂停时间为 {{ $value }}秒,严重影响性能"

  # ============================================================
  # Sentinel治理告警
  # ============================================================
  - name: sentinel_governance
    interval: 30s
    rules:
      # 系统保护触发告警
      - alert: SentinelSystemProtectionTriggered
        expr: |
          rate(sentinel_system_protection_triggered_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
          component: sentinel
        annotations:
          summary: "Sentinel系统保护频繁触发"
          description: "实例 {{ $labels.instance }} 的系统保护在5分钟内触发 {{ $value }} 次/秒"
      
      # 限流频繁告警
      - alert: SentinelFlowControlFrequent
        expr: |
          rate(sentinel_blocked_qps[5m]) > 10
        for: 5m
        labels:
          severity: info
          component: sentinel
        annotations:
          summary: "Sentinel限流频繁"
          description: "资源 {{ $labels.resource }} 限流QPS为 {{ $value }},请检查是否需要调整规则"

  # ============================================================
  # 网关性能告警
  # ============================================================
  - name: gateway_performance
    interval: 30s
    rules:
      # 慢请求过多告警
      - alert: SlowRequestsExcessive
        expr: |
          sum(rate(http_server_requests_seconds_bucket{le="3.0"}[5m])) > 1
        for: 10m
        labels:
          severity: warning
          component: gateway
        annotations:
          summary: "慢请求(>3s)数量过多"
          description: "实例 {{ $labels.instance }} 的慢请求速率为 {{ $value }} req/s"
      
      # 连接池使用率告警
      - alert: ConnectionPoolUsageHigh
        expr: |
          100 * reactor_netty_connection_provider_active_connections 
            / reactor_netty_connection_provider_max_connections > 80
        for: 5m
        labels:
          severity: warning
          component: gateway
        annotations:
          summary: "连接池使用率过高"
          description: "实例 {{ $labels.instance }} 的连接池使用率为 {{ $value | humanizePercentage }}"

  # ============================================================
  # 熔断器告警
  # ============================================================
  - name: circuit_breaker
    interval: 30s
    rules:
      # 熔断器Open状态告警
      - alert: CircuitBreakerOpen
        expr: |
          resilience4j_circuitbreaker_state{state="open"} == 1
        for: 1m
        labels:
          severity: critical
          component: circuit_breaker
        annotations:
          summary: "熔断器已开启"
          description: "熔断器 {{ $labels.name }} 处于Open状态,请求被拒绝"
      
      # 失败率过高告警
      - alert: CircuitBreakerFailureRateHigh
        expr: |
          resilience4j_circuitbreaker_failure_rate > 50
        for: 5m
        labels:
          severity: warning
          component: circuit_breaker
        annotations:
          summary: "熔断器失败率过高"
          description: "熔断器 {{ $labels.name }} 失败率为 {{ $value }}%,可能即将触发熔断"
```

### 配置Alertmanager

**文件**: `config/monitoring/prometheus/alertmanager.yml`

```yaml
global:
  resolve_timeout: 5m

# 告警路由
route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'
  
  routes:
    # P0告警-立即通知
    - match:
        severity: critical
      receiver: 'critical'
      continue: true
    
    # P1告警-延迟通知
    - match:
        severity: warning
      receiver: 'warning'
    
    # P2告警-仅记录
    - match:
        severity: info
      receiver: 'info'

# 接收器配置
receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://localhost:8080/webhook/alert'
  
  # P0-关键告警(钉钉+邮件+短信)
  - name: 'critical'
    webhook_configs:
      - url: 'http://localhost:8080/webhook/alert/critical'
        send_resolved: true
  
  # P1-警告告警(钉钉+邮件)
  - name: 'warning'
    webhook_configs:
      - url: 'http://localhost:8080/webhook/alert/warning'
        send_resolved: true
  
  # P2-信息告警(仅记录)
  - name: 'info'
    webhook_configs:
      - url: 'http://localhost:8080/webhook/alert/info'

# 抑制规则
inhibit_rules:
  # 如果critical告警触发,抑制warning告警
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'instance']
```

### 部署告警规则

```bash
cd /Users/work/apex-scaffold/config/monitoring

# 1. 备份现有配置
cp prometheus/alert-rules.yml prometheus/alert-rules.yml.backup

# 2. 更新配置文件
# (手动编辑或使用上述配置)

# 3. 验证配置
docker-compose exec prometheus promtool check rules /etc/prometheus/alert-rules.yml

# 4. 重载Prometheus配置
docker-compose exec prometheus kill -HUP 1

# 或重启Prometheus
docker-compose restart prometheus
```

### 验收标准

- [ ] 告警规则配置完成
- [ ] Prometheus可以加载规则
- [ ] Alertmanager配置完成
- [ ] 可以触发测试告警
- [ ] 告警通知正常发送

---

## 任务3: 前端EmptyState组件创建

### 组件代码

**文件**: `/Users/work/apex-admin-web/src/components/EmptyState.vue`

```vue
<!--
  EmptyState 空状态组件
  ------------------------------------------------------------------
  用于列表/表格/搜索结果为空时的友好提示展示
  基于 Element Plus Empty 组件二次封装
-->
<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  description?: string
  image?: string
  imageSize?: number
  type?: 'default' | 'search' | 'network' | 'permission'
}

const props = withDefaults(defineProps<Props>(), {
  description: '暂无数据',
  imageSize: 200,
  type: 'default'
})

const finalImage = computed(() => {
  if (props.image) return props.image
  
  switch (props.type) {
    case 'search':
      return 'search'
    case 'network':
      return 'network'
    case 'permission':
      return 'permission'
    default:
      return undefined
  }
})
</script>

<template>
  <div class="empty-state">
    <el-empty
      :description="description"
      :image="finalImage"
      :image-size="imageSize"
    >
      <slot />
    </el-empty>
  </div>
</template>

<style scoped lang="scss">
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
  padding: 40px 20px;
}
</style>
```

### 使用示例

**在列表页面中使用**:

```vue
<template>
  <el-card>
    <template v-if="loading">
      <el-skeleton :rows="5" animated />
    </template>
    
    <template v-else-if="list.length === 0">
      <EmptyState description="暂无用户数据">
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </EmptyState>
    </template>
    
    <template v-else>
      <el-table :data="list">
        <!-- 表格列 -->
      </el-table>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import { ref } from 'vue'

const loading = ref(false)
const list = ref([])
</script>
```

**不同类型的空状态**:

```vue
<!-- 搜索无结果 -->
<EmptyState type="search" description="未找到匹配的结果">
  <el-button @click="clearSearch">清除搜索条件</el-button>
</EmptyState>

<!-- 网络错误 -->
<EmptyState type="network" description="网络连接失败">
  <el-button @click="retry">重试</el-button>
</EmptyState>

<!-- 权限不足 -->
<EmptyState type="permission" description="您没有权限查看此内容" />
```

### 需修改的页面

将以下页面的空数据处理替换为EmptyState组件:

1. `src/views/system/user/index.vue`
2. `src/views/system/menu/index.vue`
3. `src/views/system/role/index.vue`
4. `src/views/system/dept/index.vue`
5. `src/views/system/dict/index.vue`
6. `src/views/system/config/index.vue`
7. `src/views/system/database/index.vue`
8. `src/views/system/job/index.vue`
9. `src/views/monitor/loginlog/index.vue`
10. `src/views/monitor/audit/index.vue`
11. 其他列表页面

### 验收标准

- [ ] EmptyState组件创建完成
- [ ] 组件在所有列表页面中使用
- [ ] 空数据时显示友好提示
- [ ] 支持自定义操作按钮

---

## 任务4: 前端表单校验完善

### 通用校验规则

**文件**: `/Users/work/apex-admin-web/src/utils/validate.ts`

```typescript
/**
 * 表单校验规则工具
 */

// 手机号校验
export const validatePhone = (rule: any, value: string, callback: Function) => {
  if (!value) {
    callback(new Error('请输入手机号'))
  } else if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号格式'))
  } else {
    callback()
  }
}

// 邮箱校验
export const validateEmail = (rule: any, value: string, callback: Function) => {
  if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

// 密码强度校验
export const validatePassword = (rule: any, value: string, callback: Function) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (value.length < 8) {
    callback(new Error('密码长度至少8位'))
  } else if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(value)) {
    callback(new Error('密码必须包含大小写字母和数字'))
  } else {
    callback()
  }
}

// URL格式校验
export const validateURL = (rule: any, value: string, callback: Function) => {
  if (value && !/^https?:\/\/.+/.test(value)) {
    callback(new Error('请输入正确的URL格式(http://或https://)'))
  } else {
    callback()
  }
}

// 端口号校验
export const validatePort = (rule: any, value: number, callback: Function) => {
  if (value && (value < 1 || value > 65535)) {
    callback(new Error('端口号范围: 1-65535'))
  } else {
    callback()
  }
}

// 中文姓名校验
export const validateChineseName = (rule: any, value: string, callback: Function) => {
  if (value && !/^[\u4e00-\u9fa5]{2,4}$/.test(value)) {
    callback(new Error('请输入2-4个汉字'))
  } else {
    callback()
  }
}

// 身份证号校验
export const validateIDCard = (rule: any, value: string, callback: Function) => {
  if (value && !/^\d{17}[\dXx]$/.test(value)) {
    callback(new Error('请输入正确的身份证号'))
  } else {
    callback()
  }
}
```

### 使用示例

**用户表单校验**:

```typescript
import { validatePhone, validateEmail } from '@/utils/validate'

const formRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度2-20字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, validator: validatePhone, trigger: 'blur' }
  ],
  email: [
    { validator: validateEmail, trigger: 'blur' }
  ],
  gender: [
    { required: true, message: '请选择性别', trigger: 'change' }
  ],
  birthday: [
    { required: true, message: '请选择生日', trigger: 'change' }
  ]
}
```

**数据库配置表单校验**:

```typescript
import { validateURL, validatePort } from '@/utils/validate'

const dbFormRules = {
  name: [
    { required: true, message: '请输入数据源名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度2-50字符', trigger: 'blur' }
  ],
  url: [
    { required: true, message: '请输入JDBC URL', trigger: 'blur' },
    { validator: validateURL, trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  port: [
    { required: true, message: '请输入端口', trigger: 'blur' },
    { validator: validatePort, trigger: 'blur' }
  ]
}
```

### 需完善的表单

1. `src/views/system/user/index.vue` - 用户表单
2. `src/views/system/menu/index.vue` - 菜单表单
3. `src/views/system/dept/index.vue` - 部门表单
4. `src/views/system/dict/index.vue` - 字典表单
5. `src/views/system/config/index.vue` - 参数配置表单
6. `src/views/system/database/index.vue` - 数据库配置表单
7. `src/views/system/job/index.vue` - 定时任务表单
8. `src/views/profile/index.vue` - 个人信息表单

### 验收标准

- [ ] validate.ts工具文件创建完成
- [ ] 所有表单使用统一的校验规则
- [ ] 必填字段有红色*标识
- [ ] 校验错误时显示友好提示
- [ ] 表单提交时触发完整校验

---

## 任务5: Grafana与Zipkin链路追踪集成

### Zipkin数据源配置

**文件**: `config/monitoring/grafana/provisioning/datasources/zipkin.yml`

```yaml
apiVersion: 1

datasources:
  - name: Zipkin
    type: zipkin
    access: proxy
    url: http://zipkin:9411
    jsonData:
      tracesToLogsV2:
        datasourceUid: 'loki'
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        tags: ['trace_id']
      tracesToMetrics:
        datasourceUid: 'prometheus'
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        tags: [{ key: 'service.name', value: 'service' }]
      nodeGraph:
        enabled: true
```

### Grafana Dashboard集成

在现有Dashboard中添加TraceID链接:

```json
{
  "fieldConfig": {
    "defaults": {
      "links": [
        {
          "title": "查看链路追踪",
          "url": "http://localhost:9411/zipkin/traces/${__value.raw}"
        }
      ]
    }
  }
}
```

### Docker Compose配置

确保Zipkin服务运行:

```yaml
# docker-compose.infra.yml
services:
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    environment:
      - STORAGE_TYPE=mem
    networks:
      - apex-net
```

### 验证步骤

```bash
# 1. 启动Zipkin
cd /Users/work/apex-scaffold/config/monitoring
docker-compose up -d zipkin

# 2. 访问Zipkin UI
open http://localhost:9411

# 3. 重启Grafana加载新数据源
docker-compose restart grafana

# 4. 在Grafana中验证
# Explore → 选择Zipkin数据源 → 查询Trace
```

### 验收标准

- [ ] Zipkin数据源配置完成
- [ ] Grafana可以查询Zipkin数据
- [ ] Dashboard中可以跳转到Zipkin
- [ ] TraceID可以关联查询

---

## 📊 总体验收清单

### 监控增强

- [ ] 新增24个监控面板
- [ ] 所有面板数据正常显示
- [ ] 告警规则配置完成
- [ ] Alertmanager配置完成
- [ ] Zipkin链路追踪集成完成

### 前端优化

- [ ] EmptyState组件创建并使用
- [ ] 表单校验规则完善
- [ ] 所有表单校验生效

---

## 📚 相关文档

- [Grafana监控平台现状与配置总结.md](../../../operations/grafana-status-summary.md)
- 前端开发规范.md（无现存目标）
- [任务执行完成报告.md](../project-deliveries/task-completion-report.md)

---

**文档创建**: 2026-09-07  
**最后更新**: 2026-09-07  
**状态**: 实施指南完成,待执行
