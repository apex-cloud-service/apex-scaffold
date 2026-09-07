# Apex Scaffold 网络架构文档

> 文档版本: v1.0  
> 创建时间: 2026-09-04  
> 适用版本: apex-scaffold v2.5.0+  
> 网络模式: VPC + 安全分区隔离

> **配套架构图**:  
> - [网络架构拓扑图](../diagrams/14_network_architecture.svg)  
> - [部署架构图](../diagrams/13_deployment_architecture.svg)

---

## 一、网络架构概览

Apex Scaffold 采用**纵深防御**的网络安全架构，通过多层网络分区和防火墙隔离，确保系统的安全性和可用性。

### 1.1 网络分层模型

```
公网区 (Internet)
    ↓ [防火墙-1: 443 HTTPS]
DMZ区 (非军事化区)
    ↓ [防火墙-2: 8080 + IDS/IPS]
应用区 (Application)
    ↓ [防火墙-3: 3306/6379/9876/9000]
数据区 (Data)

运维管理区 (Management) ← [VPN隧道]
```

### 1.2 安全等级划分

| 区域 | 安全等级 | 访问控制 | 典型组件 |
|------|---------|---------|---------|
| **公网区** | 低 | 公开访问 | CDN、DNS、用户浏览器 |
| **DMZ区** | 中等 | 仅443端口 | WAF、负载均衡器、NAT网关、跳板机 |
| **应用区** | 高 | 内网白名单 | Apex应用实例、Nacos、Sentinel |
| **数据区** | 最高 | 应用区白名单 | MySQL、Redis、RocketMQ、MinIO |
| **运维管理区** | 高 | VPN + 2FA | Prometheus、Grafana、Kibana、Jenkins |

---

## 二、网络分区详解

### 2.1 公网区 (Internet Zone)

**用途**: 面向互联网用户的公开访问入口

**组件清单**:
- **CDN**: 火山云CDN，全球边缘节点分发静态资源
- **DNS**: GeoDNS智能解析，根据用户地理位置返回最近节点IP
- **用户终端**: Web浏览器、移动APP、API客户端

**访问特性**:
- ✅ 任何互联网用户可访问
- ✅ 支持HTTPS (TLS 1.2+)
- ✅ DDoS防护 (云厂商提供)

---

### 2.2 DMZ区 (Demilitarized Zone)

**用途**: 安全缓冲区，隔离公网与内网，提供边界防护

**CIDR**: `10.0.1.0/24`

#### 2.2.1 组件清单

**1. WAF (Web Application Firewall)**
- **端口**: 443 (HTTPS)
- **功能**:
  - SQL注入防护
  - XSS (跨站脚本) 过滤
  - CSRF (跨站请求伪造) 防护
  - CC攻击防护
  - 恶意Bot识别
- **规则**: OWASP Top 10规则集

**2. 负载均衡器 (ALB / Nginx)**
- **端口**: 443 (HTTPS)
- **功能**:
  - SSL/TLS终止 (卸载加密)
  - 流量分发 (轮询/权重/IP Hash)
  - 健康检查 (HTTP GET /actuator/health)
  - 会话保持 (Cookie Based)
  - 限流保护 (单IP 1000 req/s)
- **配置示例** (Nginx):
```nginx
upstream apex_backend {
    least_conn;
    server 10.0.2.10:8080 weight=1 max_fails=3 fail_timeout=30s;
    server 10.0.2.11:8080 weight=1 max_fails=3 fail_timeout=30s;
    server 10.0.2.12:8080 weight=1 max_fails=3 fail_timeout=30s;
}

server {
    listen 443 ssl http2;
    server_name api.apex.com;
    
    ssl_certificate /etc/nginx/ssl/apex.crt;
    ssl_certificate_key /etc/nginx/ssl/apex.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    
    location / {
        proxy_pass http://apex_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 超时设置
        proxy_connect_timeout 5s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        # 限流
        limit_req zone=api_limit burst=100 nodelay;
    }
}
```

**3. NAT网关**
- **功能**: 应用服务器出网访问 (调用外部API)
- **公网IP**: 1个弹性IP
- **带宽**: 100Mbps
- **日志**: 所有出网流量审计

**4. 跳板机 (Bastion Host)**
- **端口**: 22 (SSH)
- **访问控制**: VPN + 2FA (双因素认证)
- **审计**: 所有SSH会话录屏
- **权限**: 仅运维人员通过跳板机访问内网服务器

#### 2.2.2 防火墙规则 (FW-1: Internet → DMZ)

| 方向 | 协议 | 源 | 目标 | 端口 | 动作 | 说明 |
|------|------|-----|------|------|------|------|
| Inbound | TCP | 0.0.0.0/0 | WAF | 443 | ALLOW | HTTPS流量 |
| Inbound | TCP | 0.0.0.0/0 | WAF | 80 | ALLOW | HTTP重定向到HTTPS |
| Inbound | TCP | VPN IP池 | Bastion | 22 | ALLOW | VPN用户SSH |
| Inbound | ALL | 0.0.0.0/0 | * | * | DENY | 拒绝其他所有入站流量 |

---

### 2.3 应用区 (Application Zone)

**用途**: 运行业务应用和微服务治理组件

**CIDR**: `10.0.2.0/24`

#### 2.3.1 组件清单

**应用实例**:
- **Apex App-1**: `10.0.2.10:8080`
- **Apex App-2**: `10.0.2.11:8080`
- **Apex App-3**: `10.0.2.12:8080`

**服务治理**:
- **Nacos**: `10.0.2.20:8848` (配置中心 + 服务发现)
- **Sentinel Dashboard**: `10.0.2.21:8858` (流控熔断)
- **Service Mesh (可选)**: Istio Sidecar

#### 2.3.2 防火墙规则 (FW-2: DMZ → Application)

| 方向 | 协议 | 源 | 目标 | 端口 | 动作 | 说明 |
|------|------|-----|------|------|------|------|
| Inbound | TCP | LB (10.0.1.10) | Apex App | 8080 | ALLOW | 业务流量 |
| Inbound | TCP | Bastion (10.0.1.20) | All | 22 | ALLOW | 运维SSH |
| Inbound | ALL | DMZ | Apex App | * | DENY | 拒绝直接访问 |
| Outbound | TCP | Apex App | Data Zone | 3306,6379,9876,9000 | ALLOW | 数据访问 |
| Outbound | TCP | Apex App | 0.0.0.0/0 | 80,443 | ALLOW | 外部API调用 |

**IDS/IPS检测**:
- ✅ 端口扫描检测
- ✅ 异常流量模式识别
- ✅ 恶意Payload检测
- ✅ 暴力破解防护

---

### 2.4 数据区 (Data Zone)

**用途**: 存储核心业务数据和消息队列

**CIDR**: `10.0.3.0/24`

#### 2.4.1 组件清单

| 组件 | IP | 端口 | 访问控制 |
|------|-----|------|---------|
| **MySQL Master** | 10.0.3.10 | 3306 | 应用区白名单 + 密码认证 |
| **MySQL Slave-1** | 10.0.3.11 | 3306 | 应用区白名单 + 密码认证 |
| **MySQL Slave-2** | 10.0.3.12 | 3306 | 应用区白名单 + 密码认证 |
| **Redis Master-1** | 10.0.3.20 | 6379 | 应用区白名单 + 密码认证 |
| **Redis Master-2** | 10.0.3.21 | 6379 | 应用区白名单 + 密码认证 |
| **Redis Master-3** | 10.0.3.22 | 6379 | 应用区白名单 + 密码认证 |
| **RocketMQ NameServer** | 10.0.3.30 | 9876 | 应用区白名单 |
| **RocketMQ Broker** | 10.0.3.31 | 10911 | 应用区白名单 |
| **MinIO** | 10.0.3.40 | 9000 | 应用区白名单 + AK/SK |

#### 2.4.2 防火墙规则 (FW-3: Application → Data)

| 方向 | 协议 | 源 | 目标 | 端口 | 动作 | 说明 |
|------|------|-----|------|------|------|------|
| Inbound | TCP | Apex App (10.0.2.0/24) | MySQL | 3306 | ALLOW | 数据库访问 |
| Inbound | TCP | Apex App (10.0.2.0/24) | Redis | 6379 | ALLOW | 缓存访问 |
| Inbound | TCP | Apex App (10.0.2.0/24) | RocketMQ | 9876,10911 | ALLOW | 消息队列 |
| Inbound | TCP | Apex App (10.0.2.0/24) | MinIO | 9000 | ALLOW | 对象存储 |
| Inbound | TCP | Bastion (10.0.1.20) | All | 22 | ALLOW | 运维SSH |
| Inbound | ALL | * | Data Zone | * | DENY | 拒绝其他所有访问 |

**安全加固**:
- ❌ **禁止公网访问**: 所有数据库端口禁止公网暴露
- ✅ **IP白名单**: 仅允许应用区IP访问
- ✅ **密码策略**: 强密码 (16位+特殊字符)
- ✅ **加密传输**: MySQL开启SSL，Redis开启TLS (可选)
- ✅ **审计日志**: 所有数据库操作记录审计日志

---

### 2.5 运维管理区 (Management Zone)

**用途**: 监控、日志、CI/CD等运维工具

**CIDR**: `10.0.4.0/24`

#### 2.5.1 组件清单

| 组件 | IP | 端口 | 访问方式 |
|------|-----|------|---------|
| **VPN网关** | 10.0.4.1 | 1194 | IPSec / SSL VPN |
| **Prometheus** | 10.0.4.10 | 9090 | VPN访问 |
| **Grafana** | 10.0.4.11 | 3000 | VPN访问 |
| **Kibana** | 10.0.4.12 | 5601 | VPN访问 |
| **Jenkins** | 10.0.4.20 | 8080 | VPN访问 |
| **Harbor** | 10.0.4.21 | 443 | VPN访问 |

#### 2.5.2 VPN访问控制

**VPN类型**: IPSec / SSL VPN (推荐WireGuard)

**认证方式**:
- 第一层: VPN账号密码
- 第二层: Google Authenticator 2FA
- 第三层: 跳板机SSH密钥

**IP池**: `172.16.0.0/24`

**权限分级**:
- **运维工程师**: 全区域访问权限
- **开发工程师**: 仅访问测试/预发环境
- **第三方审计**: 只读权限

---

## 三、网络流量路径

### 3.1 用户请求流 (北向流量)

```
用户浏览器 (https://api.apex.com/admin-api/v1/users)
    ↓ DNS解析
CDN (静态资源直接返回)
    ↓ 动态请求转发
WAF (SQL注入/XSS过滤)
    ↓ 443 → 443
负载均衡器 (SSL终止 + 流量分发)
    ↓ 443 → 8080
Apex App-1 (业务处理)
    ↓ 3306
MySQL Slave (读操作)
    ↓ 6379
Redis (缓存查询)
    ↓ 响应链路返回
用户浏览器
```

### 3.2 写操作流 (南向流量)

```
Apex App-1 (接收写请求)
    ↓ 3306
MySQL Master (写入主库)
    ↓ Binlog复制
MySQL Slave-1/2 (异步复制)
    ↓ 6379
Redis (删除缓存)
    ↓ 9876
RocketMQ (发送异步消息)
```

### 3.3 运维访问流 (管理流量)

```
运维工程师电脑
    ↓ VPN拨号
VPN网关 (IPSec隧道)
    ↓ 172.16.0.x → 10.0.1.20
跳板机 (SSH堡垒机)
    ↓ SSH跳转
目标服务器 (应用区/数据区)
```

---

## 四、网络安全策略

### 4.1 纵深防御体系

**防御层级**:
1. **边界防护**: WAF + DDoS防护
2. **访问控制**: 三层防火墙 + IP白名单
3. **入侵检测**: IDS/IPS实时监控
4. **加密传输**: HTTPS + TLS 1.2+
5. **审计日志**: 所有网络流量记录

### 4.2 DDoS防护

**防护类型**:
- **SYN Flood**: SYN Cookie + 连接限速
- **UDP Flood**: 速率限制
- **HTTP Flood**: CC防护规则
- **DNS放大**: DNS请求速率限制

**防护能力**:
- 清洗中心带宽: 1Tbps
- 正常业务带宽: 10Gbps
- 攻击自动切换: < 30s

### 4.3 SSL/TLS配置

**证书类型**: EV SSL证书 (Extended Validation)

**TLS版本**: TLS 1.2 / TLS 1.3

**加密套件** (推荐):
```
TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
```

**HSTS配置**:
```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
```

### 4.4 IP白名单管理

**应用区访问数据区**:
```bash
# MySQL防火墙规则
iptables -A INPUT -p tcp -s 10.0.2.0/24 --dport 3306 -j ACCEPT
iptables -A INPUT -p tcp --dport 3306 -j DROP

# Redis防火墙规则
iptables -A INPUT -p tcp -s 10.0.2.0/24 --dport 6379 -j ACCEPT
iptables -A INPUT -p tcp --dport 6379 -j DROP
```

**运维访问**:
```bash
# 仅允许跳板机SSH
iptables -A INPUT -p tcp -s 10.0.1.20 --dport 22 -j ACCEPT
iptables -A INPUT -p tcp --dport 22 -j DROP
```

---

## 五、Kubernetes网络模型

### 5.1 CNI选择

**推荐**: Calico (BGP模式)

**特性**:
- ✅ 三层网络，无需Overlay隧道，性能最优
- ✅ 支持NetworkPolicy网络策略
- ✅ 支持IPv4/IPv6双栈
- ✅ 支持IPIP/VXLAN隧道 (跨子网)

### 5.2 NetworkPolicy示例

**限制Pod间通信**:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: apex-network-policy
  namespace: apex-prod
spec:
  podSelector:
    matchLabels:
      app: apex-scaffold
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: apex-prod
    - podSelector:
        matchLabels:
          app: nginx-ingress
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: apex-middleware
    ports:
    - protocol: TCP
      port: 8848  # Nacos
  - to:
    - podSelector:
        matchLabels:
          app: mysql
    ports:
    - protocol: TCP
      port: 3306
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
```

### 5.3 Service网络

**Service类型**:
- **ClusterIP**: 内部服务访问 (Nacos/MySQL/Redis)
- **NodePort**: 暴露到节点端口 (调试用)
- **LoadBalancer**: 云厂商LB (公网访问)
- **Headless**: 无ClusterIP (StatefulSet)

**DNS解析**:
- Pod → Service: `apex-svc.apex-prod.svc.cluster.local`
- 跨命名空间: `nacos-svc.apex-middleware.svc.cluster.local`

---

## 六、监控与日志

### 6.1 网络流量监控

**指标采集**:
- 流量统计: 带宽/pps/连接数
- 延迟监控: RTT/丢包率
- 异常检测: 扫描/DDoS/异常连接

**监控工具**:
- **Prometheus**: node_network_* 指标
- **Grafana**: 网络流量Dashboard
- **VPC Flow Logs**: 云厂商流量日志

### 6.2 防火墙日志

**记录内容**:
- 源IP/目标IP
- 源端口/目标端口
- 协议类型
- 动作 (ALLOW/DENY)
- 时间戳

**日志存储**:
- 保留期: 90天
- 存储介质: Elasticsearch
- 查询工具: Kibana

### 6.3 审计日志

**审计范围**:
- ✅ VPN拨号记录
- ✅ 跳板机SSH会话
- ✅ 防火墙规则变更
- ✅ 白名单IP变更
- ✅ SSL证书更新

---

## 七、故障排查

### 7.1 网络连通性测试

```bash
# 测试DNS解析
nslookup api.apex.com

# 测试端口连通性
telnet 10.0.3.10 3306
nc -zv 10.0.3.10 3306

# 路由追踪
traceroute api.apex.com

# 抓包分析
tcpdump -i eth0 port 3306 -w mysql.pcap
```

### 7.2 常见网络问题

**问题1: Pod无法访问外网**
- 检查NAT网关配置
- 检查Pod的DNS配置
- 检查防火墙Egress规则

**问题2: Service访问超时**
- 检查Service Endpoints是否正常
- 检查NetworkPolicy是否阻止
- 检查iptables规则

**问题3: 跨区域访问失败**
- 检查防火墙规则
- 检查IP白名单
- 检查路由表

---

## 八、最佳实践

### 8.1 网络规划

- ✅ **CIDR规划**: 预留足够IP地址，避免网段冲突
- ✅ **多可用区**: 应用分布在多个AZ，提升容灾能力
- ✅ **网络隔离**: 不同环境(dev/test/prod)使用独立VPC
- ✅ **最小权限**: 防火墙默认拒绝，仅开放必要端口

### 8.2 安全加固

- ✅ **关闭公网访问**: 数据库/缓存禁止公网暴露
- ✅ **定期审计**: 每月检查防火墙规则和白名单
- ✅ **漏洞扫描**: 定期扫描开放端口和服务
- ✅ **证书管理**: SSL证书自动续期，避免过期

### 8.3 性能优化

- ✅ **就近部署**: 应用与数据库部署在同一可用区
- ✅ **长连接复用**: 数据库连接池，避免频繁建连
- ✅ **内网通信**: 尽量走内网IP，避免公网绕行
- ✅ **带宽监控**: 及时发现带宽瓶颈并扩容

---

## 九、参考资料

### 9.1 内部文档

- [部署架构文档](deployment-architecture.md)
- [系统架构设计文档](system-architecture.md)
- [安全防护系统文档](../advanced-features/security-protection-system.md)

### 9.2 外部参考

- [AWS VPC网络架构](https://docs.aws.amazon.com/vpc/)
- [Kubernetes Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)

---

**文档维护者**: 架构团队  
**审核状态**: 待人工审核  
**下次更新**: 网络架构变更后同步更新
