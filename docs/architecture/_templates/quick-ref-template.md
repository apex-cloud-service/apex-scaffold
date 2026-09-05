# [图表标题] - 快速参考卡

> **Quick Reference Card** · 打印此页作为快速参考  
> **版本**: v2.0 · **更新**: 2026-09-04

---

## 📌 核心要点 (Top 5)

### 1️⃣ 要点一

[一句话说明最重要的概念或配置]

### 2️⃣ 要点二

[一句话说明第二重要的内容]

### 3️⃣ 要点三

[一句话说明第三重要的内容]

### 4️⃣ 要点四

[一句话说明第四重要的内容]

### 5️⃣ 要点五

[一句话说明第五重要的内容]

---

## 🎯 关键配置

```yaml
# 最小可用配置
component:
  port: 8080
  timeout: 30s
  pool-size: 50
  retry: 3
```

```bash
# 环境变量
export COMPONENT_PORT=8080
export COMPONENT_TIMEOUT=30s
```

---

## ⚡ 命令速查

```bash
# 启动服务
./startup.sh

# 查看状态
systemctl status apex-scaffold

# 查看日志
tail -f logs/application.log

# 健康检查
curl http://localhost:8080/actuator/health

# 性能指标
curl http://localhost:8080/actuator/metrics

# 重启服务
systemctl restart apex-scaffold
```

---

## 📊 关键指标

| 指标 | 正常范围 | 告警阈值 |
|------|---------|---------|
| **QPS** | 5000-10000 | > 15000 |
| **延迟 P95** | < 100ms | > 500ms |
| **错误率** | < 0.1% | > 1% |
| **CPU** | < 60% | > 80% |
| **内存** | < 70% | > 85% |
| **连接池** | < 80% | > 90% |

---

## 🔧 故障排查速查

### 问题1: 服务启动失败
```bash
# 检查端口占用
lsof -i :8080
# 查看启动日志
tail -100 logs/startup.log
```

### 问题2: 响应延迟高
```bash
# 查看线程状态
jstack <pid> | grep -A 10 BLOCKED
# 查看GC情况
jstat -gcutil <pid> 1000
```

### 问题3: 内存溢出
```bash
# 生成堆转储
jmap -dump:format=b,file=heap.bin <pid>
# 分析内存使用
jmap -heap <pid>
```

---

## 📞 紧急联系

- **技术支持**: tech-support@apex-scaffold.com
- **值班电话**: +86 138-xxxx-xxxx
- **告警群**: 钉钉/企业微信 - Apex运维群
- **文档中心**: https://docs.apex-scaffold.com

---

## 🔗 快速链接

- 📖 [详细文档](./xxx-detail.md)
- 🎯 [技术决策文档](./xxx-decision.md)
- 💼 [商业价值说明](./xxx-business-value.md)
- 📊 [Grafana监控面板](http://grafana.apex-scaffold.com/d/xxx)
- 🔍 [Kibana日志查询](http://kibana.apex-scaffold.com/app/xxx)

---

**💡 提示**: 建议打印此页并贴在工位，方便快速查阅关键信息。

---

*© 2026 Apex Scaffold · v2.0 · Apache 2.0 License*
