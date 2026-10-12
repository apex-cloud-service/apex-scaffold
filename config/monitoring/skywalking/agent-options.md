# ============================================================
# Apex Scaffold - SkyWalking Agent 配置说明（agent/应用侧）
# ============================================================
# 说明: 本仓库不提交 skywalking-agent.jar 二进制，构建/部署时按需下载；
#       此文件仅作为 JVM 接入参数的说明与标准化约定。
#
# 一、Agent 依赖下载（构建镜像 / 部署机器各执行一次）
#   SKYWALKING_VERSION=9.7.0
#   curl -L -o /opt/skywalking-agent.tar.gz \
#     https://archive.apache.org/dist/skywalking/${SKYWALKING_VERSION}/apache-skywalking-java-agent-${SKYWALKING_VERSION}.tgz
#   tar -xzf /opt/skywalking-agent.tar.gz -C /opt/   # 解压出 /opt/skywalking-agent/
#
# 二、JVM 接入参数（镜像内由 Dockerfile ENTRYPOINT 按 SW_AGENT_ENABLED 自动拼接）
#   -javaagent:/opt/skywalking-agent/skywalking-agent.jar
#   -Dskywalking.agent.service_name=${SW_AGENT_NAME}                # 默认取 spring.application.name
#   -Dskywalking.collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES}  # 默认 oap:11800
#
# 三、关键开关（可通过环境变量注入，作为 -D 系统属性）
#   skywalking.agent.sample_n_per_3_secs    每 3 秒采样上限（默认 -1 全量；高 QPS 建议 1000）
#   skywalking.agent.namespace              命名空间隔离（默认空）
#   skywalking.logging.dir                  agent 自身日志目录（默认 logs）
#
# 四、日志联动（无需编译期依赖）
#   agent 自动向 MDC 注入键 tid / SW_CTX；logback-spring.xml 的 JSON 输出改用 %X{tid}，
#   即可在同一 trace 下完成「日志 ↔ 链路」关联。
#
# 五、Docker 镜像内拷贝（Dockerfile 构建时执行）
#   COPY --from=skywalking-agent /opt/skywalking-agent /app/skywalking-agent
#   或运行时以 volume 挂载到 /app/skywalking-agent。
# ============================================================