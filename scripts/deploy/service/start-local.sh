#!/bin/bash

# 启动脚本 - 使用 java -jar 方式运行应用

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${PROJECT_ROOT}"

# 设置环境变量（与 Docker Compose 配置保持一致）
export SPRING_PROFILES_ACTIVE="prod,enterprise"

# 数据库配置（指向 Docker Compose 中的 MySQL）
export DB_MASTER_URL="jdbc:mysql://localhost:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
export DB_MASTER_USERNAME="root"
export DB_MASTER_PASSWORD="apex-root"
export DB_SLAVE_URL="jdbc:mysql://localhost:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
export DB_SLAVE_USERNAME="root"
export DB_SLAVE_PASSWORD="apex-root"

# Redis 配置
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD="apex-redis-password"
export REDIS_DATABASE="0"

# RocketMQ 配置
export ROCKETMQ_NAMESERVER="localhost:9876"
export ROCKETMQ_NAMESRV_ADDR="localhost:9876"
export ROCKETMQ_PRODUCER_GROUP="apex-producer-group"

# JWT 密钥
export JWT_ACCESS_SECRET="ThisIsAVeryLongAccessTokenSecretKeyForHS256MustBeAtLeast32BytesLong"
export JWT_REFRESH_SECRET="ThisIsAVeryLongRefreshTokenSecretKeyForHS256MustBeAtLeast32BytesLong"

# JVM 参数
export JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=./logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

# 启动应用
echo "启动 Apex Scaffold 应用..."
cd "${PROJECT_ROOT}"
java $JAVA_OPTS -jar apex-interfaces/target/apex-interfaces-1.2.0-executable.jar
