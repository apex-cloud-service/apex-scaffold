# SkyWalking Agent 配置说明

## Agent 下载与镜像接入

本仓库不提交 Agent 二进制。Containerfile 在 builder 阶段下载 9.7.0，下载、解压或 JAR 非空校验失败会中止构建。

```bash
SKYWALKING_VERSION=9.7.0
curl -fsSL -o /opt/skywalking-agent.tar.gz \
  "https://archive.apache.org/dist/skywalking/java-agent/${SKYWALKING_VERSION}/apache-skywalking-java-agent-${SKYWALKING_VERSION}.tgz"
tar -xzf /opt/skywalking-agent.tar.gz -C /opt/
test -s /opt/skywalking-agent/skywalking-agent.jar
```

镜像使用 `COPY --from=builder /opt/skywalking-agent /app/skywalking-agent`。入口默认启用 `-javaagent:/app/skywalking-agent/skywalking-agent.jar`；启用时 JAR 不可读、缺失或为空会退出 1。可通过 `SW_AGENT_JAR` 指定路径，通过 `SW_AGENT_ENABLED=false` 显式关闭。

- `SW_AGENT_NAME`：镜像默认取构建参数 `SERVICE_MODULE`。
- `SW_AGENT_COLLECTOR_BACKEND_SERVICES`：默认 `skywalking-oap:11800`，可通过环境变量覆盖。
- `SW_LOGGING_DIR`：镜像默认 `/app/logs/skywalking`，位于已授权给 appuser 的日志目录，避免 Agent 默认安装目录不可写。
- JSON 日志使用 Toolkit `TraceIdJsonProvider` 输出 `TID`，Filebeat 将其转换为 `tid`；业务 `traceId` 独立保留，不依赖 Agent 自动向 MDC 注入 `tid`。
- 其他 JVM 系统属性可通过 `JAVA_OPTS` 设置，例如 `-Dskywalking.agent.sample_n_per_3_secs=1000`、`-Dskywalking.agent.namespace=dev`、`-Dskywalking.logging.dir=/app/logs/skywalking`。覆盖 `JAVA_OPTS` 时需保留所需 JVM 调优参数。

## Compose 网络与 OAP 存储

`podman-compose.infra.yml` 创建 `apex-observability` 网络，OAP 与 `podman-compose.yml` 的六个应用服务均接入该网络，应用 Compose 以 external 引用它。先启动观测服务，再启动应用栈；使用独立项目名避免两个 Compose 相互覆盖服务：

```bash
podman-compose -p apex-infra -f podman-compose.infra.yml up -d skywalking-oap skywalking-ui elasticsearch kibana filebeat
podman-compose -p apex-app -f podman-compose.yml up -d
```

以上为部署示例。应用启动前需配置安全密钥，并准备 `logs/<service>` 子目录，确保镜像非 root 用户 `appuser` 有写权限。bind mount 会遮蔽镜像内目录权限，不能仅依赖 Containerfile 的 chown。不要同时启动两个 Compose 的全部中间件，已有 MySQL、Redis 等默认端口会冲突。

`Containerfile.quick` 不包含 Agent，也不会处理 `SW_AGENT_ENABLED`；需要自动探针时使用 `Containerfile` 构建，不能仅对 quick 镜像设置该环境变量。2026-10-13 检查的本地 `localhost/apex-scaffold:latest` 入口与 quick 一致，预期 Agent 路径不能加载；这不构成当前完整 Containerfile 的构建验收。

Alloy 的 `env` 标签由 `APEX_LOG_ENV` 提供，应用 Compose 默认 `prod`；开发环境需显式设置 `APEX_LOG_ENV=dev`，并与 `SPRING_PROFILES_ACTIVE` 对齐。独立运行 Alloy 也需传入该变量。`app.sh` 仅在 start/restart/console 检查 Agent 和应用 JAR；产物移除后仍可使用 logs/status/stop 排查和管理已有进程。

OAP 使用官方 9.7.0 镜像内完整 `application.yml`，不挂载本目录的说明文件。通过官方环境变量设置：

- `SW_STORAGE=h2`。
- `SW_STORAGE_H2_URL=jdbc:h2:file:/skywalking/data/skywalking;DATABASE_TO_UPPER=FALSE`，由 `skywalking-data` 卷持久化。
- `SW_CORE_RECORD_DATA_TTL` 与 `SW_CORE_METRICS_DATA_TTL` 默认均为 7 天，可覆盖。

UI 默认访问 `http://localhost:18080`，通过 `SKYWALKING_UI_PORT` 修改宿主机端口，避免与网关 8080 冲突。

官方配置基线：https://github.com/apache/skywalking/blob/v9.7.0/oap-server/server-starter/src/main/resources/application.yml
