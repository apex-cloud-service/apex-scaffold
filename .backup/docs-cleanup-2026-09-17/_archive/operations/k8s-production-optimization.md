# Kubernetes 生产环境配置优化指南

> **文档版本**: v1.0  
> **创建时间**: 2026-09-10  
> **适用版本**: apex-scaffold v1.2.0+  
> **优先级**: 🔴 高 (影响服务可用性)

---

## 一、概述

本文档基于 [实现一致性修正报告](../../.trae/documents/implementation-consistency-fix-report.md) 的发现,提供 Kubernetes 生产环境的配置优化建议,确保 Apex Governance 优雅关停机制正常工作。

### 1.1 当前配置状态

**仓库配置** (`k8s/deployment.yaml`):
```yaml
spec:
  # ❌ terminationGracePeriodSeconds 未配置 (使用 K8s 默认值 30s)
  containers:
  - name: apex-app
    lifecycle:
      preStop:
        exec:
          command: ["/bin/sh", "-c", "sleep 8"]
```

**问题分析**:
- Governance 总关停预算: 90 秒
- Kubernetes 默认宽限期: 30 秒
- **风险**: 应用未完成资源排空就被 SIGKILL 强制杀死

---

## 二、推荐配置

### 2.1 完整 Deployment 配置

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: apex-app
  namespace: default
spec:
  replicas: 3
  selector:
    matchLabels:
      app: apex-app
  template:
    metadata:
      labels:
        app: apex-app
    spec:
      # ✅ 关键配置 1: 设置宽限期为 120 秒
      terminationGracePeriodSeconds: 120
      
      containers:
      - name: apex-app
        image: apex-scaffold:1.2.0
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: management
          containerPort: 8081
          protocol: TCP
        
        # ✅ 关键配置 2: 保持 preStop 为 8 秒
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 8"]
        
        # ✅ 关键配置 3: Liveness Probe
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
            scheme: HTTP
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          successThreshold: 1
          failureThreshold: 3
        
        # ✅ 关键配置 4: Readiness Probe
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          successThreshold: 1
          failureThreshold: 2
        
        # ✅ 关键配置 5: Startup Probe (可选,用于慢启动应用)
        startupProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
            scheme: HTTP
          initialDelaySeconds: 0
          periodSeconds: 5
          timeoutSeconds: 3
          successThreshold: 1
          failureThreshold: 30  # 最多等待 150 秒启动
        
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod,enterprise"
        
        # Governance 配置
        - name: APEX_GOVERNANCE_ENABLED
          value: "true"
        - name: APEX_GOVERNANCE_DEPLOYMENT_MODE
          value: "kubernetes"  # 明确指定 Kubernetes 模式
        - name: APEX_GOVERNANCE_SHUTDOWN_TIMEOUT
          value: "90s"
        - name: APEX_GOVERNANCE_MQ_DRAIN_TIMEOUT
          value: "30s"
        - name: APEX_GOVERNANCE_FORCE_SHUTDOWN
          value: "true"
        
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
```

### 2.2 配置说明

| 配置项 | 推荐值 | 说明 | 影响 |
|--------|--------|------|------|
| `terminationGracePeriodSeconds` | **120** | Governance 总预算 90s + 30s 缓冲 | 🔴 必须 |
| `preStop sleep` | **8** | 给 K8s 端点控制器时间从 Service 移除 Pod | 🟡 推荐 |
| `livenessProbe.failureThreshold` | **3** | 连续失败 3 次才重启 (避免误杀) | 🟡 推荐 |
| `readinessProbe.periodSeconds` | **5** | 每 5 秒检查一次就绪状态 | 🟢 可选 |
| `DEPLOYMENT_MODE` | **kubernetes** | 明确指定模式 (避免自动检测失败) | 🟡 推荐 |

---

## 三、关停时序详解

### 3.1 完整时序图

```
用户/K8s 发起删除 Pod
         ↓
┌────────────────────────────────────────────────────────────┐
│ T=0s    Kubernetes 发送 SIGTERM 到容器                      │
│         同时开始 terminationGracePeriodSeconds 倒计时       │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T=0s    preStop Hook 执行 (sleep 8)                        │
│         给端点控制器时间从 Service 中移除 Pod               │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T=8s    应用开始处理 SIGTERM                                │
│         GracefulShutdownManager.stop() 触发                │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T=8s    ReadinessStateController 发布 REFUSING_TRAFFIC      │
│         Readiness Probe 开始返回失败                        │
│         K8s 停止将新流量路由到此 Pod                        │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T=8s    ResourceDrainRegistry 停止所有参与者接收新任务      │
│         - MQ 消费者停止拉取新消息                           │
│         - 死信队列处理器停止处理                            │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T=8-98s 资源排空阶段 (最多 90s)                             │
│         - 在途 MQ 消息处理完成 (预算 30s)                   │
│         - 其他资源参与者排空                                │
│         - 使用统一截止时间 (T=8s + 90s = T=98s)            │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T<=98s  所有资源排空完成                                    │
│         GracefulShutdownCoordinator 转入 STOPPED 状态       │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T<=98s  Spring Boot HTTP Graceful Shutdown                 │
│         等待正在处理的 HTTP 请求完成                        │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T<=100s Spring 容器关闭                                     │
│         Bean 生命周期回调执行:                              │
│         - 数据库连接池关闭                                  │
│         - 线程池关闭                                        │
│         - 其他资源释放                                      │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T<=105s JVM 进程退出 (exit code 0)                          │
└────────────────────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────────────────────┐
│ T=120s  terminationGracePeriodSeconds 到期                  │
│         如果进程还在运行,K8s 发送 SIGKILL 强制杀死          │
│         (正常情况下不会到达这里)                            │
└────────────────────────────────────────────────────────────┘
```

### 3.2 关键时间点

| 时间点 | 事件 | 说明 |
|--------|------|------|
| **T=0s** | SIGTERM + preStop | Kubernetes 开始优雅关停 |
| **T=8s** | 应用开始关停 | preStop 完成,应用收到 SIGTERM |
| **T=8-98s** | 资源排空 | Governance 协调所有资源排空 (90s 预算) |
| **T<=100s** | Spring 关闭 | Bean 生命周期回调完成 |
| **T<=105s** | 进程退出 | JVM 正常退出 |
| **T=120s** | SIGKILL 阈值 | 安全缓冲,正常不会到达 |

---

## 四、验证方法

### 4.1 验证配置生效

```bash
# 1. 应用配置
kubectl apply -f k8s/deployment.yaml

# 2. 验证 terminationGracePeriodSeconds
kubectl get pod apex-app-xxx -o jsonpath='{.spec.terminationGracePeriodSeconds}'
# 预期输出: 120

# 3. 验证 preStop 配置
kubectl get pod apex-app-xxx -o jsonpath='{.spec.containers[0].lifecycle.preStop}'
# 预期输出: {"exec":{"command":["/bin/sh","-c","sleep 8"]}}
```

### 4.2 模拟优雅关停测试

```bash
# 1. 开启日志监控
kubectl logs -f apex-app-xxx --tail=50 &

# 2. 删除 Pod 触发优雅关停
kubectl delete pod apex-app-xxx --grace-period=120

# 3. 观察日志输出 (参考下一节)

# 4. 验证关停时间
# 从 "Shutdown signal received" 到 Pod Terminated 应 < 110s
```

### 4.3 预期日志输出

**正常优雅关停日志**:
```
2026-09-10 10:00:00.000  INFO 1 --- [ionShutdownHook] c.a.s.i.c.GracefulShutdownManager        : Shutdown signal received
2026-09-10 10:00:00.005  INFO 1 --- [ionShutdownHook] c.a.s.i.g.l.GracefulShutdownCoordinator  : Transitioning to DRAINING state
2026-09-10 10:00:00.010  INFO 1 --- [ionShutdownHook] c.a.s.i.g.l.ReadinessStateController     : Refusing traffic (readiness=REFUSING_TRAFFIC)
2026-09-10 10:00:00.015  INFO 1 --- [ionShutdownHook] c.a.s.i.g.p.k.KubernetesGovernanceStrategy : Withdrawing from traffic (reason: SHUTDOWN)
2026-09-10 10:00:00.020  INFO 1 --- [ionShutdownHook] c.a.s.i.g.l.ResourceDrainRegistry        : Stopping all participants from accepting new tasks
2026-09-10 10:00:00.025  INFO 1 --- [ionShutdownHook] c.a.s.i.m.MqDrainParticipant             : Stopping MQ consumer and DLQ processor
2026-09-10 10:00:12.340  INFO 1 --- [ionShutdownHook] c.a.s.i.m.MqDrainParticipant             : All in-flight messages processed (12315ms elapsed)
2026-09-10 10:00:12.350  INFO 1 --- [ionShutdownHook] c.a.s.i.g.l.ResourceDrainRegistry        : All participants drained successfully
2026-09-10 10:00:12.355  INFO 1 --- [ionShutdownHook] c.a.s.i.g.l.GracefulShutdownCoordinator  : Graceful shutdown completed (state=STOPPED, duration=12350ms)
2026-09-10 10:00:12.500  INFO 1 --- [ionShutdownHook] o.s.b.w.e.netty.GracefulShutdown         : Commencing graceful shutdown. Waiting for active requests to complete
2026-09-10 10:00:13.200  INFO 1 --- [ionShutdownHook] o.s.b.w.e.netty.GracefulShutdown         : Graceful shutdown complete
2026-09-10 10:00:13.300  INFO 1 --- [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-09-10 10:00:13.450  INFO 1 --- [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
```

**关键指标**:
- Governance 关停耗时: ~12.35 秒 (远小于 90 秒预算)
- 总关停耗时: ~13.5 秒 (从 SIGTERM 到进程退出)
- 状态: ✅ STOPPED (正常完成)

---

## 五、故障排查

### 5.1 Pod 被强制杀死 (SIGKILL)

**症状**:
```bash
kubectl describe pod apex-app-xxx
# Events:
# Warning  Killing  pod/apex-app-xxx  Stopping container apex-app
# (超时未退出,被 SIGKILL 强制杀死)
```

**原因**: `terminationGracePeriodSeconds` 不足

**解决**: 增加到 120 秒
```yaml
spec:
  terminationGracePeriodSeconds: 120
```

### 5.2 关停时仍有请求 503

**症状**: 删除 Pod 后前端仍收到部分 503 错误

**原因**: `preStop` 等待时间不足,端点更新未传播完成

**解决**: 增加 `preStop` 等待时间
```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sh", "-c", "sleep 15"]  # 8s → 15s
```

### 5.3 MQ 消息丢失

**症状**: 关停时在途 MQ 消息未处理完成

**原因**: `MQ_DRAIN_TIMEOUT` 不足

**解决**: 增加 MQ 排空预算
```yaml
env:
- name: APEX_GOVERNANCE_MQ_DRAIN_TIMEOUT
  value: "60s"  # 30s → 60s
```

同时确保 `terminationGracePeriodSeconds >= MQ_DRAIN_TIMEOUT + 30s`

### 5.4 Readiness Probe 过早失败

**症状**: 启动时 Readiness 一直失败,Pod 无法就绪

**原因**: `initialDelaySeconds` 不足或应用启动慢

**解决**: 使用 Startup Probe 或增加 `initialDelaySeconds`
```yaml
startupProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  periodSeconds: 5
  failureThreshold: 30  # 最多等待 150 秒
```

---

## 六、生产环境 Checklist

### 6.1 配置检查清单

- [ ] `terminationGracePeriodSeconds` 设置为 **120**
- [ ] `preStop` 配置 `sleep 8` (或根据网络延迟调整到 10-15s)
- [ ] `livenessProbe` 使用 `/actuator/health/liveness` 端口 **8081**
- [ ] `readinessProbe` 使用 `/actuator/health/readiness` 端口 **8081**
- [ ] 环境变量 `APEX_GOVERNANCE_DEPLOYMENT_MODE=kubernetes`
- [ ] 环境变量 `APEX_GOVERNANCE_SHUTDOWN_TIMEOUT=90s`
- [ ] 环境变量 `APEX_GOVERNANCE_MQ_DRAIN_TIMEOUT=30s` (或根据实际消息处理时间调整)
- [ ] 资源 `requests` 和 `limits` 已配置
- [ ] 副本数 `replicas >= 2` (保证高可用)

### 6.2 发布前验证

- [ ] 在预发环境执行优雅关停测试
- [ ] 验证 Pod 删除后无 503 错误
- [ ] 验证 MQ 消息无丢失
- [ ] 验证关停日志完整输出
- [ ] 验证关停耗时 < 110 秒
- [ ] 压测场景下执行滚动更新测试

### 6.3 监控告警

建议配置以下告警:

```yaml
# Prometheus AlertRule 示例
groups:
- name: apex-app-graceful-shutdown
  rules:
  - alert: GracefulShutdownTimeout
    expr: |
      rate(apex_governance_shutdown_timeout_total[5m]) > 0
    for: 1m
    labels:
      severity: warning
    annotations:
      summary: "Apex 应用优雅关停超时"
      description: "Pod {{ $labels.pod }} 在过去5分钟内发生关停超时"

  - alert: PodKilled
    expr: |
      kube_pod_container_status_terminated_reason{reason="Error"} > 0
    labels:
      severity: critical
    annotations:
      summary: "Pod 被强制杀死"
      description: "Pod {{ $labels.pod }} 被 SIGKILL 强制终止"
```

---

## 七、参考资料

### 7.1 内部文档
- [部署运维文档 - Apex Governance 章节](deployment.md#25-apex-governance-优雅关停机制-)
- [实现一致性修正报告](../../.trae/documents/implementation-consistency-fix-report.md)
- [环境变量管理指南](environment-variables.md#48-服务治理配置)

### 7.2 Kubernetes 官方文档
- [Pod Lifecycle - Termination](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)
- [Configure Liveness, Readiness and Startup Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Container Lifecycle Hooks](https://kubernetes.io/docs/concepts/containers/container-lifecycle-hooks/)

### 7.3 Spring Boot 官方文档
- [Graceful Shutdown](https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.graceful-shutdown)
- [Application Availability](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application.application-availability)

---

## 八、变更历史

| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|----------|
| v1.0 | 2026-09-10 | Apex Team | 初始版本,基于实现一致性修正报告 |

---

**最后更新**: 2026-09-10  
**下次审查**: 2026-10-10  
**责任人**: 运维团队 + 后端团队
