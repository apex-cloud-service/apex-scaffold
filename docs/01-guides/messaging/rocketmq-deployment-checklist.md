# RocketMQ 消息自动处理功能上线部署检查清单

**项目**: Apex Scaffold RocketMQ Auto Handler  
**版本**: 3.1.0  
**检查日期**: 2026-09-24  
**负责人**: _____________  
**检查人**: _____________

---

## 📋 检查清单使用说明

- ✅ 表示已完成并验证通过
- ❌ 表示未完成或验证失败
- ⚠️ 表示需要注意或有条件通过
- 🔄 表示进行中

**检查原则**:
1. 所有 **必须项** 必须全部 ✅ 才能上线
2. **推荐项** 至少完成 80% 才能上线
3. **可选项** 根据实际情况决定
4. 每项检查需要 2 人确认

---

## Part 1: 部署前检查（Pre-Deployment）

### 1.1 代码完整性检查 【必须】

| 检查项 | 状态 | 检查人 | 备注 |
|-------|------|--------|------|
| MqLagMonitor.java 已提交到代码仓库 | ☐ | | |
| DeadLetterQueueProcessor.java 已提交 | ☐ | | |
| MqAlertManager.java 已提交 | ☐ | | |
| AlertNotificationSender.java 已提交 | ☐ | | |
| MqAutoHandlerConfig.java 已提交 | ☐ | | |
| 代码已通过 Code Review | ☐ | | |
| 代码已合并到 main/master 分支 | ☐ | | |
| 版本号已更新为 3.1.0 | ☐ | | |

**检查命令**:
```bash
# 检查文件是否存在
ls -la apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/monitor/MqLagMonitor.java
ls -la apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/dlq/DeadLetterQueueProcessor.java
ls -la apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/alert/MqAlertManager.java
ls -la apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/alert/AlertNotificationSender.java
ls -la apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/config/MqAutoHandlerConfig.java

# 检查 Git 状态
git status
git log --oneline -5
```

---

### 1.2 配置文件检查 【必须】

| 检查项 | 状态 | 检查人 | 备注 |
|-------|------|--------|------|
| application.yml 配置已添加 | ☐ | | 行号: 725-768 |
| application-prod.yml 配置已添加 | ☐ | | 行号: 382-413 |
| 配置语法正确（无 YAML 语法错误） | ☐ | | |
| 默认值合理（threshold/cooldown） | ☐ | | |
| 环境变量占位符正确 | ☐ | | |
| 生产环境通知已启用 | ☐ | | enabled: true |
| 开发环境通知已关闭 | ☐ | | enabled: false |

**检查命令**:
```bash
# 验证 YAML 语法
cat apex-system/src/main/resources/application.yml | grep -A 50 "apex:"
cat apex-system/src/main/resources/application-prod.yml | grep -A 30 "apex:"

# 检查配置完整性
grep "auto-handler:" apex-system/src/main/resources/application.yml
grep "notification:" apex-system/src/main/resources/application-prod.yml
```

---

### 1.3 依赖检查 【必须】

| 检查项 | 状态 | 检查人 | 备注 |
|-------|------|--------|------|
| RocketMQ Spring Boot Starter 已配置 | ☐ | | 版本: 2.2.3+ |
| Micrometer 已配置 | ☐ | | 用于指标采集 |
| Redisson 已配置 | ☐ | | 用于幂等性 |
| Spring Boot Actuator 已配置 | ☐ | | 用于健康检查 |
| 所有依赖版本兼容 | ☐ | | |
| Maven/Gradle 构建成功 | ☐ | | |

**检查命令**:
```bash
# 检查依赖
./mvnw dependency:tree | grep rocketmq
./mvnw dependency:tree | grep micrometer
./mvnw dependency:tree | grep redisson

# 构建项目
./mvnw clean package -DskipTests
```

---

### 1.4 环境变量准备 【必须】

#### 开发/测试环境

| 环境变量 | 是否配置 | 示例值 | 检查人 |
|---------|---------|--------|--------|
| ROCKETMQ_NAMESERVER | ☐ | 127.0.0.1:9876 | |
| DINGTALK_WEBHOOK_URL | ☐ | （可选） | |
| WECHAT_WEBHOOK_URL | ☐ | （可选） | |

#### 生产环境

| 环境变量 | 是否配置 | 示例值 | 检查人 |
|---------|---------|--------|--------|
| ROCKETMQ_NAMESERVER | ☐ | 192.168.1.10:9876;192.168.1.11:9876 | |
| DINGTALK_WEBHOOK_URL | ☐ | https://oapi.dingtalk.com/robot/send?access_token=xxx | |
| WECHAT_WEBHOOK_URL | ☐ | https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx | |

**检查命令**:
```bash
# 开发环境
echo $ROCKETMQ_NAMESERVER

# 生产环境（在生产服务器上执行）
env | grep ROCKETMQ
env | grep WEBHOOK
```

---

### 1.5 基础设施就绪检查 【必须】

| 检查项 | 状态 | 检查人 | 备注 |
|-------|------|--------|------|
| RocketMQ NameServer 运行正常 | ☐ | | |
| RocketMQ Broker 运行正常 | ☐ | | |
| Redis 运行正常 | ☐ | | 用于幂等性 |
| Prometheus 运行正常 | ☐ | | 用于监控 |
| Grafana 运行正常 | ☐ | | 用于可视化 |
| 网络连通性测试通过 | ☐ | | |

**检查命令**:
```bash
# RocketMQ 连接测试
telnet 192.168.1.10 9876

# Redis 连接测试
redis-cli -h 192.168.1.20 ping

# Prometheus 健康检查
curl http://prometheus:9090/-/healthy

# Grafana 健康检查
curl http://grafana:3000/api/health
```

---

### 1.6 告警通知测试 【必须】

| 检查项 | 状态 | 检查人 | 备注 |
|-------|------|--------|------|
| 钉钉 Webhook 测试成功 | ☐ | | |
| 企业微信 Webhook 测试成功 | ☐ | | |
| 告警消息格式正确 | ☐ | | Markdown 格式 |
| 告警接收人确认 | ☐ | | |

**测试脚本**:
```bash
# 测试钉钉 Webhook
curl -X POST "${DINGTALK_WEBHOOK_URL}" \
  -H 'Content-Type: application/json' \
  -d '{
    "msgtype": "markdown",
    "markdown": {
      "title": "RocketMQ 测试告警",
      "text": "### 测试告警\n这是一条测试告警消息"
    }
  }'

# 测试企业微信 Webhook
curl -X POST "${WECHAT_WEBHOOK_URL}" \
  -H 'Content-Type: application/json' \
  -d '{
    "msgtype": "markdown",
    "markdown": {
      "content": "### RocketMQ 测试告警\n这是一条测试告警消息"
    }
  }'
```

---

### 1.7 文档准备 【推荐】

| 文档 | 是否完成 | 负责人 | 备注 |
|------|---------|--------|------|
| 使用指南文档 | ☐ | | rocketmq-auto-handler-guide.md |
| 优化总结文档 | ☐ | | rocketmq-auto-handler-summary.md |
| 配置验证文档 | ☐ | | rocketmq-config-verification.md |
| 上线部署检查清单（本文档） | ☐ | | |
| 运维操作手册 | ☐ | | |
| 故障排查手册 | ☐ | | |

---

## Part 2: 部署执行检查（Deployment）

### 2.1 构建部署包 【必须】

| 检查项 | 状态 | 执行人 | 备注 |
|-------|------|--------|------|
| 代码最新版本已拉取 | ☐ | | git pull |
| Maven/Gradle 清理构建 | ☐ | | clean package |
| 单元测试全部通过 | ☐ | | |
| 部署包文件完整 | ☐ | | .jar 文件 |
| 部署包 MD5 校验通过 | ☐ | | |
| 部署包已上传到目标服务器 | ☐ | | |

**执行命令**:
```bash
# 构建部署包
./mvnw clean package -Pprod

# 生成 MD5
md5sum apex-system-service/target/apex-system-service-*.jar > apex-system-service.jar.md5

# 上传到服务器
scp apex-components-parent/apex-system/target/apex-interfaces-3.1.0.jar user@prod-server:/opt/apex/
scp apex-interfaces-3.1.0.jar.md5 user@prod-server:/opt/apex/

# 在服务器上验证 MD5
ssh user@prod-server "cd /opt/apex && md5sum -c apex-interfaces-3.1.0.jar.md5"
```

---

### 2.2 灰度发布准备 【必须】

| 检查项 | 状态 | 执行人 | 备注 |
|-------|------|--------|------|
| 灰度发布计划已制定 | ☐ | | 5% → 20% → 50% → 100% |
| 流量切换方案已确认 | ☐ | | 负载均衡配置 |
| 回滚方案已准备 | ☐ | | |
| 回滚脚本已测试 | ☐ | | |
| 数据备份已完成 | ☐ | | Redis / MySQL |

**灰度发布计划**:
```
阶段 1: 5% 流量（1-2 天）
- 选择 1 台服务器部署新版本
- 监控关键指标
- 验证自动处理功能

阶段 2: 20% 流量（2-3 天）
- 扩展到 2-3 台服务器
- 继续监控
- 收集性能数据

阶段 3: 50% 流量（3-5 天）
- 扩展到 50% 服务器
- 验证稳定性
- 评估优化效果

阶段 4: 100% 流量
- 全量发布
- 持续监控 7 天
```

---

### 2.3 服务启动检查 【必须】

| 检查项 | 状态 | 执行人 | 备注 |
|-------|------|--------|------|
| 旧版本服务正常停止 | ☐ | | graceful shutdown |
| 新版本服务成功启动 | ☐ | | |
| 启动日志无 ERROR | ☐ | | |
| 健康检查通过 | ☐ | | /actuator/health |
| 消费者注册成功 | ☐ | | 查看日志 |
| 监控器注册成功 | ☐ | | 查看日志 |
| 死信处理器注册成功 | ☐ | | 查看日志 |

**检查命令**:
```bash
# 停止旧版本（优雅关闭）
kill -15 $(cat /opt/apex/apex.pid)
# 等待 30 秒
sleep 30

# 启动新版本
cd /opt/apex
nohup java -jar \
  -Dspring.profiles.active=prod \
  -Xms2g -Xmx2g \
  apex-interfaces-3.1.0.jar > apex.log 2>&1 &
echo $! > apex.pid

# 等待启动完成（约 30-60 秒）
tail -f apex.log | grep "Started ApexApplication"

# 健康检查
curl http://localhost:8081/actuator/health

# 检查启动日志
grep "已注册消费者到监控器" apex.log
grep "已注册死信队列消费者" apex.log
grep "已注册消费者到自动处理器" apex.log
```

---

## Part 3: 部署后验证（Post-Deployment）

### 3.1 功能验证 【必须】

| 功能 | 验证方法 | 状态 | 验证人 | 备注 |
|-----|---------|------|--------|------|
| 消息积压监控 | 查看日志和指标 | ☐ | | |
| 自动扩容触发 | 模拟积压 | ☐ | | |
| 死信队列消费 | 发送失败消息 | ☐ | | |
| 死信自动重试 | 观察重试日志 | ☐ | | |
| 告警触发 | 达到阈值 | ☐ | | |
| 告警通知 | 接收钉钉/企业微信 | ☐ | | |
| 告警冷却 | 验证不重复告警 | ☐ | | |

**验证步骤**:

#### 3.1.1 消息积压监控验证
```bash
# 1. 查看监控日志
tail -f apex.log | grep "消息积压"

# 2. 查看 Prometheus 指标
curl http://localhost:8081/actuator/prometheus | grep mq_consumer_lag

# 3. 查看 Grafana 面板
# 访问: http://grafana:3000/d/rocketmq-dashboard
```

#### 3.1.2 自动扩容验证
```bash
# 1. 发送大量消息（60000 条）
for i in {1..60000}; do
  # 发送消息的代码
done

# 2. 暂停消费

# 3. 观察日志
tail -f apex.log | grep "自动扩容"

# 预期输出:
# ERROR 【严重告警】消费者 xxx 消息严重积压: 60000 条
# INFO  自动扩容消费者 xxx: 线程数 [20-64] -> [40-128]
# INFO  启用批量消费模式: xxx (批量大小: 32)
```

#### 3.1.3 死信队列验证
```bash
# 1. 发送消息并模拟失败

# 2. 观察死信队列日志
tail -f apex.log | grep "死信"

# 预期输出:
# WARN  收到死信消息: topic=xxx, msgId=xxx
# WARN  死信原因分析: msgId=xxx, reason=NETWORK_ERROR
# INFO  尝试重新处理死信消息: msgId=xxx
# INFO  死信消息重试成功: msgId=xxx
```

#### 3.1.4 告警通知验证
```bash
# 查看告警日志
tail -f apex.log | grep "告警"

# 检查钉钉群是否收到消息
# 检查企业微信群是否收到消息

# 预期: 收到 Markdown 格式的告警消息
```

---

### 3.2 性能验证 【必须】

| 指标 | 期望值 | 实际值 | 状态 | 验证人 |
|-----|--------|--------|------|--------|
| 消息发送延迟（P99） | ≤ 20ms | | ☐ | |
| 消息消费延迟（P99） | ≤ 50ms | | ☐ | |
| 消费吞吐量 | ≥ 2000 msg/s | | ☐ | |
| 积压处理速度 | ≥ 2000 msg/s | | ☐ | |
| CPU 占用率 | ≤ 60% | | ☐ | |
| 内存占用 | ≤ 2GB | | ☐ | |
| 网络带宽 | ≤ 100Mbps | | ☐ | |

**性能测试命令**:
```bash
# 查看 Prometheus 指标
curl http://localhost:8081/actuator/prometheus | grep -E "mq_producer|mq_consumer"

# 查看系统资源
top -p $(cat /opt/apex/apex.pid)
free -h
```

---

### 3.3 监控验证 【必须】

| 监控项 | 验证方法 | 状态 | 验证人 | 备注 |
|-------|---------|------|--------|------|
| Prometheus 指标采集 | 访问 /actuator/prometheus | ☐ | | |
| Grafana 面板显示 | 访问 RocketMQ Dashboard | ☐ | | |
| 告警规则生效 | 查看 Prometheus Alerts | ☐ | | |
| 日志正常输出 | 检查日志文件 | ☐ | | |
| 链路追踪正常 | 查看 Zipkin | ☐ | | |

**验证步骤**:
```bash
# 1. 验证 Prometheus 指标
curl http://localhost:8081/actuator/prometheus | grep mq_consumer_lag
curl http://localhost:8081/actuator/prometheus | grep mq_alert

# 2. 访问 Grafana
open http://grafana:3000/d/rocketmq-dashboard

# 3. 检查告警规则
curl http://prometheus:9090/api/v1/alerts | jq

# 4. 检查日志
tail -100 /opt/apex/apex.log

# 5. 检查 Zipkin 追踪
open http://zipkin:9411
```

---

### 3.4 故障场景测试 【推荐】

| 故障场景 | 测试方法 | 期望行为 | 状态 | 测试人 |
|---------|---------|---------|------|--------|
| RocketMQ NameServer 故障 | 停止 NameServer | 自动重连 | ☐ | |
| Redis 故障 | 停止 Redis | 幂等性降级 | ☐ | |
| 网络抖动 | 模拟网络延迟 | 自动重试 | ☐ | |
| 消费积压 | 发送大量消息 | 自动扩容 | ☐ | |
| 死信消息 | 模拟消费失败 | 自动处理 | ☐ | |
| 告警风暴 | 频繁触发告警 | 冷却抑制 | ☐ | |

**测试脚本**:
```bash
# 测试 1: RocketMQ 故障
systemctl stop rocketmq-namesrv
# 观察日志，应该看到重连尝试
# 恢复 NameServer
systemctl start rocketmq-namesrv

# 测试 2: Redis 故障
systemctl stop redis
# 观察日志，应该看到降级到本地幂等
# 恢复 Redis
systemctl start redis

# 测试 3: 消费积压
# 发送 60000 条消息
# 观察自动扩容日志

# 测试 4: 告警风暴
# 多次触发积压告警
# 验证冷却期内不重复告警
```

---

## Part 4: 持续监控（Continuous Monitoring）

### 4.1 关键指标监控（首日）【必须】

| 时间点 | 检查项 | 状态 | 检查人 | 备注 |
|-------|--------|------|--------|------|
| 启动后 1 小时 | 错误率 < 1% | ☐ | | |
| 启动后 2 小时 | 积压量 < 10000 | ☐ | | |
| 启动后 4 小时 | 性能正常 | ☐ | | |
| 启动后 8 小时 | 无告警 | ☐ | | |
| 启动后 24 小时 | 系统稳定 | ☐ | | |

### 4.2 日常监控（7 天）【必须】

| 监控内容 | 频率 | 负责人 | 备注 |
|---------|------|--------|------|
| 查看 Grafana 总览面板 | 每天 2 次 | | 早晚各一次 |
| 检查活跃告警 | 每天 2 次 | | |
| 查看消息积压趋势 | 每天 1 次 | | |
| 检查死信队列数量 | 每天 1 次 | | |
| 分析慢消费情况 | 每周 1 次 | | |
| 查看自动扩容记录 | 每周 1 次 | | |
| 评估优化效果 | 第 7 天 | | 完整评估 |

---

## Part 5: 回滚检查（Rollback - 仅故障时）

### 5.1 回滚触发条件

| 触发条件 | 阈值 | 说明 |
|---------|------|------|
| 错误率 | > 10% | 相比基线 |
| 性能下降 | > 20% | 相比基线 |
| 消息丢失 | > 0 | 任何消息丢失 |
| 严重告警 | 持续 > 10 分钟 | 无法自动恢复 |
| 服务崩溃 | 连续 3 次重启失败 | |

### 5.2 回滚执行步骤

| 步骤 | 操作 | 预计时间 | 执行人 | 状态 |
|-----|------|---------|--------|------|
| 1 | 通知相关人员 | 1 分钟 | | ☐ |
| 2 | 停止新版本服务 | 30 秒 | | ☐ |
| 3 | 恢复配置文件 | 1 分钟 | | ☐ |
| 4 | 启动旧版本服务 | 1 分钟 | | ☐ |
| 5 | 验证服务正常 | 2 分钟 | | ☐ |
| 6 | 恢复流量 | 30 秒 | | ☐ |
| 7 | 确认系统稳定 | 5 分钟 | | ☐ |

**回滚脚本**:
```bash
#!/bin/bash
# rollback.sh

echo "开始回滚..."

# 1. 停止新版本
kill -15 $(cat /opt/apex/apex.pid)
sleep 10

# 2. 恢复配置
cp /opt/apex/backup/application-prod.yml.bak \
   /opt/apex/config/application-prod.yml

# 3. 启动旧版本
cd /opt/apex/backup
nohup java -jar \
  -Dspring.profiles.active=prod \
  -Xms2g -Xmx2g \
  apex-interfaces-3.0.0.jar > apex.log 2>&1 &
echo $! > /opt/apex/apex.pid

# 4. 等待启动
sleep 30

# 5. 健康检查
curl http://localhost:8081/actuator/health

echo "回滚完成"
```

---

## Part 6: 上线批准（Sign-off）

### 6.1 各阶段批准

| 阶段 | 批准人 | 签名 | 日期 | 备注 |
|-----|--------|------|------|------|
| 部署前检查完成 | 技术负责人 | | | |
| 灰度发布 5% | 运维负责人 | | | |
| 灰度发布 20% | 运维负责人 | | | |
| 灰度发布 50% | 技术负责人 | | | |
| 全量发布 | 项目经理 | | | |
| 验收完成 | 产品负责人 | | | |

### 6.2 最终批准

**项目状态**: ☐ 通过 / ☐ 不通过

**项目经理签名**: _________________  
**日期**: _________________

**技术负责人签名**: _________________  
**日期**: _________________

---

## 附录 A: 关键指标基线

| 指标 | 当前基线 | 优化目标 | 实际值 |
|-----|---------|---------|--------|
| 消息发送延迟（P99） | 30ms | 15ms | |
| 消息消费延迟（P99） | 100ms | 50ms | |
| 消费吞吐量 | 500 msg/s | 2000 msg/s | |
| 人工干预次数 | 10 次/天 | 1 次/天 | |
| 故障恢复时间 | 5-10 分钟 | 30-60 秒 | |

---

## 附录 B: 紧急联系人

| 角色 | 姓名 | 手机 | 邮箱 | 备注 |
|-----|------|------|------|------|
| 项目经理 | | | | |
| 技术负责人 | | | | |
| 开发工程师 | | | | |
| 运维工程师 | | | | |
| DBA | | | | |
| 值班电话 | | | | 7×24 |

---

## 附录 C: 相关文档

1. [RocketMQ 消息自动处理使用指南](rocketmq-auto-handler-guide.md)
2. [RocketMQ 消息自动处理优化总结](rocketmq-auto-handler-summary.md)
3. [RocketMQ 配置验证报告](rocketmq-config-verification.md)
4. [RocketMQ 生产优化报告](rocketmq-production-optimization.md)
5. [Apex Scaffold 最终优化报告](../../reports/FINAL-OPTIMIZATION-REPORT.md)

---

## 附录 D: 检查清单总结

**必须项统计**:
- 部署前检查: ____ / ____ 项完成
- 部署执行检查: ____ / ____ 项完成
- 部署后验证: ____ / ____ 项完成

**推荐项统计**:
- 推荐项: ____ / ____ 项完成

**整体完成度**: _____%

**上线建议**: ☐ 可以上线 / ☐ 需要整改 / ☐ 暂缓上线

---

**检查清单版本**: v1.0  
**最后更新**: 2026-09-24  
**文档状态**: ✅ 最终版本
