# Apex Scaffold 部署运维文档

> 文档版本: v1.0  
> 创建时间: 2026-09-03  
> 适用版本: apex-scaffold v2.5.0+

---

## 一、部署概览

### 1.1 部署方式

| 方式 | 适用场景 | 难度 | 推荐度 |
|------|---------|------|--------|
| **Docker Compose** | 开发/测试环境,单机部署 | ⭐ | ⭐⭐⭐⭐⭐ |
| **Kubernetes** | 生产环境,集群部署 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **裸机部署** | 传统部署方式 | ⭐⭐ | ⭐⭐⭐ |
| **云平台** | 阿里云/AWS/腾讯云 | ⭐⭐ | ⭐⭐⭐⭐ |

### 1.2 服务器资源要求

**最小配置** (开发/测试):
- CPU: 4核
- 内存: 8GB
- 磁盘: 50GB
- 网络: 10Mbps

**推荐配置** (生产环境):
- CPU: 8核
- 内存: 16GB
- 磁盘: 200GB (SSD)
- 网络: 100Mbps

**集群配置** (高可用):
- 应用服务器: 3台 (8核16G)
- MySQL主从: 1主2从 (16核32G)
- Redis Cluster: 6节点 (8核16G)
- 负载均衡: 2台 (4核8G)

---

## 二、Docker Compose 部署

### 2.1 前置准备

**安装Docker & Docker Compose**:

```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | sh
sudo apt-get install docker-compose-plugin

# CentOS/RHEL
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 验证安装
docker --version
docker compose version
```

### 2.2 docker-compose.yml

创建 `docker-compose.yml`:

```yaml
version: '3.8'

services:
  # MySQL 8.0
  mysql:
    image: mysql:8.0
    container_name: apex-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root123456}
      MYSQL_DATABASE: apex_data
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./docker/mysql/conf.d:/etc/mysql/conf.d
    command: 
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-authentication-plugin=mysql_native_password
    networks:
      - apex-network

  # Redis 7.0
  redis:
    image: redis:7.0-alpine
    container_name: apex-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:-redis123456}
    networks:
      - apex-network

  # Nacos 2.x
  nacos:
    image: nacos/nacos-server:v2.3.0
    container_name: apex-nacos
    restart: always
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: mysql
      MYSQL_SERVICE_HOST: mysql
      MYSQL_SERVICE_DB_NAME: nacos_config
      MYSQL_SERVICE_USER: root
      MYSQL_SERVICE_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root123456}
      NACOS_AUTH_ENABLE: true
      NACOS_AUTH_TOKEN: ${NACOS_AUTH_TOKEN:-SecretKey012345678901234567890123456789012345678901234567890123456789}
      NACOS_AUTH_IDENTITY_KEY: nacos
      NACOS_AUTH_IDENTITY_VALUE: nacos
    ports:
      - "8848:8848"
      - "9848:9848"
    volumes:
      - nacos-logs:/home/nacos/logs
    depends_on:
      - mysql
    networks:
      - apex-network

  # Apex Scaffold Application
  apex-scaffold:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: apex-scaffold
    restart: always
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: mysql
      DB_PORT: 3306
      DB_NAME: apex_data
      DB_USERNAME: root
      DB_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root123456}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123456}
      NACOS_SERVER: nacos:8848
      JWT_SECRET: ${JWT_SECRET:-your-production-secret-key-change-me}
      TZ: Asia/Shanghai
    ports:
      - "8080:8080"
      - "8081:8081"
    volumes:
      - app-logs:/app/logs
      - app-upload:/app/upload
    depends_on:
      - mysql
      - redis
      - nacos
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - apex-network

  # Prometheus
  prometheus:
    image: prom/prometheus:v2.45.0
    container_name: apex-prometheus
    restart: always
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
    networks:
      - apex-network

  # Grafana
  grafana:
    image: grafana/grafana:10.0.0
    container_name: apex-grafana
    restart: always
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
      GF_INSTALL_PLUGINS: ''
    ports:
      - "3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./docker/grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - prometheus
    networks:
      - apex-network

  # Zipkin
  zipkin:
    image: openzipkin/zipkin:2.24
    container_name: apex-zipkin
    restart: always
    ports:
      - "9411:9411"
    environment:
      STORAGE_TYPE: mysql
      MYSQL_HOST: mysql
      MYSQL_TCP_PORT: 3306
      MYSQL_DB: zipkin
      MYSQL_USER: root
      MYSQL_PASS: ${MYSQL_ROOT_PASSWORD:-root123456}
    depends_on:
      - mysql
    networks:
      - apex-network

volumes:
  mysql-data:
  redis-data:
  nacos-logs:
  app-logs:
  app-upload:
  prometheus-data:
  grafana-data:

networks:
  apex-network:
    driver: bridge
```

### 2.3 环境变量配置

创建 `.env` 文件:

```bash
# 数据库密码
MYSQL_ROOT_PASSWORD=your_mysql_password

# Redis密码
REDIS_PASSWORD=your_redis_password

# JWT密钥
JWT_SECRET=your_jwt_secret_key_at_least_32_characters

# Nacos认证Token
NACOS_AUTH_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789

# Grafana密码
GRAFANA_PASSWORD=your_grafana_password
```

### 2.4 启动服务

```bash
# 1. 构建并启动所有服务
docker compose up -d

# 2. 查看日志
docker compose logs -f apex-scaffold

# 3. 查看服务状态
docker compose ps

# 4. 停止服务
docker compose down

# 5. 停止并删除数据卷
docker compose down -v
```

### 2.5 健康检查

```bash
# 检查应用健康状态
curl http://localhost:8081/actuator/health

# 检查MySQL连接
docker exec -it apex-mysql mysql -uroot -p

# 检查Redis连接
docker exec -it apex-redis redis-cli -a redis123456 ping

# 检查Nacos
curl http://localhost:8848/nacos
```

---

## 三、Kubernetes 部署

### 3.1 命名空间

创建 `k8s/namespace.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: apex-scaffold
```

### 3.2 ConfigMap

创建 `k8s/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: apex-config
  namespace: apex-scaffold
data:
  application.yml: |
    spring:
      profiles:
        active: prod
      datasource:
        url: jdbc:mysql://mysql-service:3306/apex_data
        username: root
        password: ${DB_PASSWORD}
      data:
        redis:
          host: redis-service
          port: 6379
          password: ${REDIS_PASSWORD}
```

### 3.3 Secret

创建 `k8s/secret.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: apex-secret
  namespace: apex-scaffold
type: Opaque
stringData:
  db-password: "your_mysql_password"
  redis-password: "your_redis_password"
  jwt-secret: "your_jwt_secret_key"
```

### 3.4 MySQL StatefulSet

创建 `k8s/mysql-statefulset.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
  namespace: apex-scaffold
spec:
  ports:
  - port: 3306
  selector:
    app: mysql
  clusterIP: None
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mysql
  namespace: apex-scaffold
spec:
  serviceName: mysql-service
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: mysql:8.0
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_ROOT_PASSWORD
          valueFrom:
            secretKeyRef:
              name: apex-secret
              key: db-password
        - name: MYSQL_DATABASE
          value: "apex_data"
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
  volumeClaimTemplates:
  - metadata:
      name: mysql-storage
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 20Gi
```

### 3.5 Apex Scaffold Deployment

创建 `k8s/apex-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: apex-scaffold
  namespace: apex-scaffold
spec:
  replicas: 3
  selector:
    matchLabels:
      app: apex-scaffold
  template:
    metadata:
      labels:
        app: apex-scaffold
    spec:
      containers:
      - name: apex-scaffold
        image: your-registry/apex-scaffold:2.5.0
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 8081
          name: management
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: apex-secret
              key: db-password
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: apex-secret
              key: redis-password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: apex-secret
              key: jwt-secret
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "2000m"
            memory: "2Gi"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
        volumeMounts:
        - name: logs
          mountPath: /app/logs
        - name: upload
          mountPath: /app/upload
      volumes:
      - name: logs
        emptyDir: {}
      - name: upload
        persistentVolumeClaim:
          claimName: apex-upload-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: apex-service
  namespace: apex-scaffold
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  - port: 8081
    targetPort: 8081
    name: management
  selector:
    app: apex-scaffold
```

### 3.6 Ingress

创建 `k8s/ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: apex-ingress
  namespace: apex-scaffold
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - api.example.com
    secretName: apex-tls
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: apex-service
            port:
              number: 8080
```

### 3.7 HPA (水平自动扩缩容)

创建 `k8s/hpa.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: apex-hpa
  namespace: apex-scaffold
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: apex-scaffold
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### 3.8 部署执行

```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 创建Secret和ConfigMap
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml

# 3. 部署MySQL
kubectl apply -f k8s/mysql-statefulset.yaml

# 4. 部署Redis
kubectl apply -f k8s/redis-statefulset.yaml

# 5. 部署应用
kubectl apply -f k8s/apex-deployment.yaml

# 6. 配置Ingress
kubectl apply -f k8s/ingress.yaml

# 7. 配置HPA
kubectl apply -f k8s/hpa.yaml

# 8. 查看部署状态
kubectl get pods -n apex-scaffold
kubectl get svc -n apex-scaffold
kubectl logs -f deployment/apex-scaffold -n apex-scaffold
```

---

## 四、裸机部署

### 4.1 安装JDK 17

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# CentOS/RHEL
sudo yum install java-17-openjdk java-17-openjdk-devel

# 验证安装
java -version
```

### 4.2 安装MySQL 8.0

```bash
# Ubuntu/Debian
sudo apt install mysql-server-8.0

# CentOS/RHEL
sudo yum install mysql-server

# 启动MySQL
sudo systemctl start mysqld
sudo systemctl enable mysqld

# 初始化数据库
mysql -u root -p
CREATE DATABASE apex_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'apex'@'%' IDENTIFIED BY 'apex123456';
GRANT ALL PRIVILEGES ON apex_data.* TO 'apex'@'%';
FLUSH PRIVILEGES;
```

### 4.3 安装Redis 7.0

```bash
# Ubuntu/Debian
sudo apt install redis-server

# CentOS/RHEL
sudo yum install redis

# 配置Redis密码
sudo vi /etc/redis/redis.conf
# 添加: requirepass your_password

# 启动Redis
sudo systemctl start redis
sudo systemctl enable redis
```

### 4.4 部署应用

```bash
# 1. 上传JAR包
scp target/apex-scaffold-2.5.0.jar user@server:/opt/apex/

# 2. 创建配置文件
cat > /opt/apex/application-prod.yml <<EOF
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/apex_data
    username: apex
    password: apex123456
  data:
    redis:
      host: localhost
      port: 6379
      password: your_password
EOF

# 3. 创建启动脚本
cat > /opt/apex/start.sh <<'EOF'
#!/bin/bash
cd /opt/apex
nohup java -server \
  -Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.profiles.active=prod \
  -jar apex-scaffold-2.5.0.jar \
  > logs/app.log 2>&1 &
echo $! > app.pid
EOF

chmod +x /opt/apex/start.sh

# 4. 创建systemd服务
sudo cat > /etc/systemd/system/apex-scaffold.service <<EOF
[Unit]
Description=Apex Scaffold Application
After=network.target

[Service]
Type=simple
User=apex
WorkingDirectory=/opt/apex
ExecStart=/usr/bin/java -jar /opt/apex/apex-scaffold-2.5.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 5. 启动服务
sudo systemctl daemon-reload
sudo systemctl start apex-scaffold
sudo systemctl enable apex-scaffold
sudo systemctl status apex-scaffold
```

---

## 五、监控告警配置

### 5.1 Prometheus 配置

创建 `docker/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'apex-scaffold'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['apex-scaffold:8081']
        labels:
          application: 'apex-scaffold'
          environment: 'prod'

  - job_name: 'mysql'
    static_configs:
      - targets: ['mysql-exporter:9104']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']
```

### 5.2 Grafana 配置

**数据源配置** (`docker/grafana/provisioning/datasources/prometheus.yml`):

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

**导入Dashboard**:

1. 访问 Grafana: http://localhost:3000
2. 登录 (admin/admin)
3. 导入Dashboard JSON (`.trae/documents/Grafana监控面板增强方案.md`)

### 5.3 告警规则

创建 `docker/prometheus/rules/alerts.yml`:

```yaml
groups:
  - name: apex_alerts
    interval: 30s
    rules:
      # 应用宕机告警
      - alert: ApexScaffoldDown
        expr: up{job="apex-scaffold"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Apex Scaffold应用宕机"
          description: "{{ $labels.instance }} 已宕机超过1分钟"

      # CPU使用率告警
      - alert: HighCPUUsage
        expr: process_cpu_usage{job="apex-scaffold"} > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "CPU使用率过高"
          description: "{{ $labels.instance }} CPU使用率 {{ $value | humanizePercentage }}"

      # 内存使用率告警
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{job="apex-scaffold",area="heap"} / jvm_memory_max_bytes{job="apex-scaffold",area="heap"} > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM堆内存使用率过高"
          description: "{{ $labels.instance }} 堆内存使用率 {{ $value | humanizePercentage }}"

      # API错误率告警
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{job="apex-scaffold",status=~"5.."}[5m]) / rate(http_server_requests_seconds_count{job="apex-scaffold"}[5m]) > 0.01
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "API错误率过高"
          description: "{{ $labels.instance }} 错误率 {{ $value | humanizePercentage }}"

      # 响应时间告警
      - alert: SlowResponse
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="apex-scaffold"}[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "API响应时间过慢"
          description: "{{ $labels.instance }} P95响应时间 {{ $value }}s"
```

---

## 六、运维操作

### 6.1 日志管理

**查看实时日志**:
```bash
# Docker
docker logs -f apex-scaffold

# Kubernetes
kubectl logs -f deployment/apex-scaffold -n apex-scaffold

# 裸机
tail -f /opt/apex/logs/app.log
```

**日志轮转配置** (`logback-spring.xml`):

```xml
<configuration>
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/apex-scaffold.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>logs/apex-scaffold.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
      <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
        <maxFileSize>100MB</maxFileSize>
      </timeBasedFileNamingAndTriggeringPolicy>
      <maxHistory>30</maxHistory>
      <totalSizeCap>10GB</totalSizeCap>
    </rollingPolicy>
  </appender>
</configuration>
```

### 6.2 数据库备份

**MySQL自动备份脚本**:

```bash
#!/bin/bash
# /opt/scripts/backup-mysql.sh

BACKUP_DIR="/backup/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
DB_NAME="apex_data"
DB_USER="root"
DB_PASS="your_password"

# 创建备份目录
mkdir -p $BACKUP_DIR

# 全量备份
mysqldump -u$DB_USER -p$DB_PASS \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  $DB_NAME | gzip > $BACKUP_DIR/${DB_NAME}_${DATE}.sql.gz

# 删除30天前的备份
find $BACKUP_DIR -name "*.sql.gz" -mtime +30 -delete

echo "Backup completed: ${DB_NAME}_${DATE}.sql.gz"
```

**Crontab定时任务**:

```bash
# 每天凌晨2点执行备份
0 2 * * * /opt/scripts/backup-mysql.sh >> /var/log/mysql-backup.log 2>&1
```

### 6.3 应用升级

**蓝绿部署**:

```bash
# 1. 部署新版本到备用环境
docker compose -f docker-compose.blue.yml up -d

# 2. 健康检查
curl http://blue-host:8080/actuator/health

# 3. 切换流量 (修改Nginx配置)
upstream apex_backend {
    server blue-host:8080;  # 新版本
    # server green-host:8080;  # 旧版本
}

# 4. 重载Nginx
nginx -s reload

# 5. 验证无误后,停止旧版本
docker compose -f docker-compose.green.yml down
```

**滚动更新** (Kubernetes):

```bash
# 更新镜像
kubectl set image deployment/apex-scaffold \
  apex-scaffold=your-registry/apex-scaffold:2.6.0 \
  -n apex-scaffold

# 查看滚动更新状态
kubectl rollout status deployment/apex-scaffold -n apex-scaffold

# 回滚到上一版本
kubectl rollout undo deployment/apex-scaffold -n apex-scaffold
```

### 6.4 性能调优

**JVM参数调优**:

```bash
java -server \
  -Xms4g -Xmx4g \              # 堆内存4G
  -Xmn2g \                     # 新生代2G
  -XX:MetaspaceSize=256m \     # 元空间初始大小
  -XX:MaxMetaspaceSize=512m \  # 元空间最大大小
  -XX:+UseG1GC \               # 使用G1垃圾回收器
  -XX:MaxGCPauseMillis=200 \   # GC最大停顿时间200ms
  -XX:G1HeapRegionSize=16m \   # G1区域大小
  -XX:+ParallelRefProcEnabled \  # 并行处理Reference
  -XX:+HeapDumpOnOutOfMemoryError \  # OOM时dump堆
  -XX:HeapDumpPath=/logs/heapdump.hprof \
  -XX:+PrintGCDetails \        # 打印GC详情
  -XX:+PrintGCDateStamps \     # 打印GC时间戳
  -Xloggc:/logs/gc.log \       # GC日志路径
  -jar apex-scaffold-2.5.0.jar
```

**MySQL参数调优** (`/etc/mysql/conf.d/custom.cnf`):

```ini
[mysqld]
# InnoDB配置
innodb_buffer_pool_size = 8G
innodb_log_file_size = 512M
innodb_flush_log_at_trx_commit = 2
innodb_flush_method = O_DIRECT

# 连接配置
max_connections = 1000
max_connect_errors = 10000

# 查询缓存
query_cache_type = 0
query_cache_size = 0

# 慢查询日志
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 1
```

**Redis参数调优** (`/etc/redis/redis.conf`):

```conf
# 内存配置
maxmemory 4gb
maxmemory-policy allkeys-lru

# 持久化
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec

# 网络
tcp-backlog 511
timeout 300
tcp-keepalive 300
```

---

## 七、故障排查

### 7.1 常见问题

**问题1: 应用启动失败**

排查步骤:
```bash
# 1. 查看日志
docker logs apex-scaffold

# 2. 检查端口占用
lsof -i:8080

# 3. 检查数据库连接
mysql -h localhost -u apex -p

# 4. 检查Redis连接
redis-cli -h localhost -p 6379 -a password ping
```

**问题2: 接口响应慢**

排查步骤:
```bash
# 1. 查看JVM堆内存
curl http://localhost:8081/actuator/metrics/jvm.memory.used

# 2. 查看数据库慢查询
mysql> SHOW FULL PROCESSLIST;
mysql> SELECT * FROM mysql.slow_log ORDER BY query_time DESC LIMIT 10;

# 3. 查看Redis慢查询
redis-cli slowlog get 10

# 4. 分析线程dump
jstack <pid> > thread-dump.txt
```

**问题3: 内存泄漏**

排查步骤:
```bash
# 1. 生成堆dump
jmap -dump:format=b,file=heapdump.hprof <pid>

# 2. 使用MAT分析
# 下载Eclipse Memory Analyzer Tool
# 导入heapdump.hprof文件分析

# 3. 查看对象占用
jmap -histo:live <pid> | head -n 20
```

### 7.2 应急预案

**数据库宕机**:
```bash
# 1. 快速切换到从库
# 修改应用配置指向从库
spring.datasource.url=jdbc:mysql://slave-host:3306/apex_data

# 2. 重启应用
docker restart apex-scaffold

# 3. 修复主库后切回
```

**Redis宕机**:
```bash
# 1. 应用降级(关闭缓存)
# 临时配置
spring.cache.type=none

# 2. 重启Redis
docker restart apex-redis

# 3. 恢复配置
```

**流量激增**:
```bash
# 1. 快速扩容 (Kubernetes)
kubectl scale deployment apex-scaffold --replicas=10 -n apex-scaffold

# 2. 开启限流
# Sentinel Dashboard配置QPS限流规则

# 3. 开启熔断
# 配置Resilience4j熔断器
```

---

## 八、安全加固

### 8.1 网络安全

**防火墙配置**:
```bash
# 只允许必要端口
sudo ufw allow 8080/tcp   # 应用端口
sudo ufw allow 22/tcp     # SSH
sudo ufw deny 3306/tcp    # 禁止外部访问MySQL
sudo ufw deny 6379/tcp    # 禁止外部访问Redis
sudo ufw enable
```

**HTTPS配置** (Nginx):
```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;
    
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    
    location / {
        proxy_pass http://apex-backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 8.2 数据安全

**数据库加密**:
```sql
-- 启用透明数据加密(TDE)
ALTER INSTANCE ROTATE INNODB MASTER KEY;
```

**敏感配置加密**:
```yaml
# 使用Jasypt加密敏感配置
spring:
  datasource:
    password: ENC(encrypted_password)

# 启动时提供解密密钥
java -Djasypt.encryptor.password=secret_key -jar app.jar
```

---

## 九、附录

### 9.1 端口清单

| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| Apex Scaffold | 8080 | HTTP | 业务接口 |
| Apex Scaffold | 8081 | HTTP | 管理端口(Actuator) |
| MySQL | 3306 | TCP | 数据库 |
| Redis | 6379 | TCP | 缓存 |
| Nacos | 8848 | HTTP | 配置中心 |
| Prometheus | 9090 | HTTP | 指标采集 |
| Grafana | 3000 | HTTP | 监控大盘 |
| Zipkin | 9411 | HTTP | 链路追踪 |

### 9.2 相关文档

- [系统架构设计文档](./系统架构设计文档.md)
- [数据库设计文档](./数据库设计文档.md)
- [业务流程文档](./业务流程文档.md)

---

**文档维护者**: TRAE AI Assistant  
**审核状态**: 待人工审核  
**下次更新**: 部署方式变更后同步更新
