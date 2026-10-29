# 阻塞 I/O 虚拟线程压测报告（20261027）

## 结论摘要

- 目标接口 `/api/v1/health` 在 `event`、`cache` 两个阻塞 I/O 池上分别以平台线程与虚拟线程运行。第一轮用 wrk `-c200` 完成 5 轮对比，**每轮均出现 socket timeout，数据不足以支撑结论**；随后按「降低连接数 + 多轮取中位数」重测，补充 1 轮 `-c32` 与 5 个有效 `-c64` 样本。
- **虚拟线程开关确实生效**：虚拟线程模式下 `apex_blocking_io_max_threads` 等于配置的 `max-concurrency`（16.0），JFR `jdk.VirtualThreadStart` 记录到 `apex.event-vt-*` / `apex.cache-vt-*` 线程启动（c=64 各轮 event 池 5822–8859 条），平台线程模式该事件恒为 0。
- **c=64 重测取得方向性正收益**：5 个有效样本的逐轮吞吐变化为 +12.9%、+18.2%、+29.9%、-6.2%、+47.7%，中位数 **+18.2%**；按中位数汇总，吞吐 187.27 → 211.43 req/s（+12.9%），平均延迟 373.69 → 337.17 ms（-9.8%），p99 1320 → 1080 ms（-18.2%）。timeout 从第一轮每轮数百个降到每轮 0–28 个（相对约 5 700–6 000 次请求 < 0.5%）：虚拟线程侧 4 轮为 0，平台线程侧 2 轮为 0，仅 `165501` 双侧均为 0。
- 但样本量仍不足以下结论：c=64 的 baseline 自身在 150.80–214.84 req/s 间波动（相对中位约 ±19%），5 个样本中仍有 1 个为负向（-6.2%），且仅单机、无预热轮、无置信区间。**「虚拟线程在短阻塞接口上有约 13–18% 的中位吞吐收益」目前只能作为方向性判断，不足以支持调整默认配置。**
- **完整回归门禁已通过**：`mvnd -T4 -B clean verify` 结果为 `BUILD SUCCESS`（31 个聚合模块全部 SUCCESS，20:07 min），Surefire 5057 个用例、Failsafe 389 个用例，`Failures: 0 / Errors: 0`，Spotless、PMD(p3c)、JaCoCo 随 verify 一并通过。
- 注意：该次 `clean verify` 的根 `clean` 清空了 `target/`，第一轮遗留的 `vt-start-*.jfr`、`threads-*.txt`、`prom-*.txt`、`service-*.log` 已不可恢复；`results.tsv` 与 `wrk-*.txt` 在构建前已归档到本目录 `runs/`，重测最后一轮（`20261027-191439`）的完整产物也已归档到 `runs/20261027-191439/`。

## 被测对象与环境

| 项目 | 值 |
| --- | --- |
| 被测服务 | `apex-system-service-1.3.1.jar`（Spring Boot 3.5.15 / Reactor 3.7.19 / WebFlux Netty） |
| JDK | `21.0.13.0+9-tem` |
| 接口 | `GET /api/v1/health`（业务端口 8082） |
| 指标端点 | `http://127.0.0.1:8083/actuator/prometheus` |
| 压测工具 | wrk 4.2，`-t4 -d30s --latency`，连接数见各轮次 |
| 依赖 | 容器 `apex-bench-mysql`（mysql:9.0）、`apex-bench-redis`（redis:7-alpine） |
| 压测脚本 | [scripts/bench/bench-vt.sh](../../../../scripts/bench/bench-vt.sh)，`compare` 模式 |

### 两种模式的 JVM 参数差异

平台线程（baseline）：

```text
-Dapex.blocking-io.event.threads=16 -Dapex.blocking-io.cache.threads=16
```

虚拟线程（virtual）：

```text
-Dapex.blocking-io.event.virtual-threads=true -Dapex.blocking-io.event.max-concurrency=16
-Dapex.blocking-io.cache.virtual-threads=true -Dapex.blocking-io.cache.max-concurrency=16
```

两组并发上限均为 16，仅执行器类型不同，其余参数完全一致（详见本目录 `jvm-args-baseline.txt`、`jvm-args-virtual.txt`）。开关只通过 JVM 参数注入，未修改任何配置文件。

此外，两轮都通过 `BENCH_JAVA_OPTS` 注入与线程模型无关的本地化启动参数（关闭 R2DBC 自动装配、提供本地加密密钥、指定本地邮件主机、开启 Flyway）；缺少其中 R2DBC 排除项时 system-service 会因找不到 R2DBC 连接 URL 启动失败，复现时必须一并传入。

### 复现命令

第一轮（`-c200`，受超时影响，结果仅作参考）：

```bash
BENCH_JAVA_OPTS="-Dspring.flyway.enabled=true -Dspring.datasource.dynamic.p6spy=false \
-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration,org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration \
-Dapex.security.data-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
-Dsecurity.config.encryption.key=0123456789abcdef0123456789abcdef \
-Dsecurity.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
-Dspring.mail.host=localhost" \
KEYCLOAK_ENABLED=false BENCH_DURATION=30s BENCH_CONNECTIONS=200 BENCH_THREADS=4 \
BENCH_POOLS="event,cache" BENCH_CONCURRENCY=16 BENCH_STARTUP_TIMEOUT=300 \
./scripts/bench/bench-vt.sh
```

重测轮次仅把 `BENCH_CONNECTIONS` 改为 `32` 或 `64`，其余保持不变。

## 测试数据

### 第一轮：`-c200`（每轮均超时，结果仅作参考）

`completed_in_window` 指压测窗口内该池 `completed_tasks` 的增量（由 pre/mid 两次 Prometheus 快照相减），`VT_STARTED` 指 JFR `jdk.VirtualThreadStart` 中线程名以 `apex.<pool>-vt-` 开头的事件数。

| 轮次 | 模式 | req/s | 平均(ms) | p50 | p90 | p99 | 非2xx | max_threads | completed_in_window | VT_STARTED | timeout |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 150716 | baseline event | 159.31 | 838.71 | 750.20 | 1700.00 | 1960.00 | 0 | 16.0 | n/a | 0 | 有 |
| 150716 | virtual event | 183.44 | 902.45 | 858.01 | 1740.00 | 1970.00 | 0 | 16.0 | 1645 | 0 | 有 |
| 151356 | baseline event | 219.88 | 777.77 | 761.76 | 1450.00 | 1920.00 | 0 | 16.0 | n/a | 0 | 有 |
| 151356 | virtual event | 159.94 | 872.68 | 802.53 | 1690.00 | 1950.00 | 0 | 16.0 | n/a | 4809 | 有 |
| 152036 | baseline event | 173.23 | 928.50 | 913.82 | 1600.00 | 1930.00 | 0 | 16.0 | n/a | 0 | 有 |
| 152036 | virtual event | 168.21 | 745.97 | 682.24 | 1460.00 | 1950.00 | 0 | 16.0 | 1634 | 5060 | 有 |
| 153537 | baseline event | 186.90 | 774.42 | 591.20 | 1720.00 | 1960.00 | 0 | 16.0 | n/a | 0 | 有 |
| 153537 | virtual event | 194.21 | 759.45 | 692.21 | 1510.00 | 1920.00 | 0 | 16.0 | n/a | 5856 | 有 |
| 154300 | baseline event | 231.85 | 788.00 | 798.03 | 1340.00 | 1720.00 | 0 | 16.0 | n/a | 0 | 有 |
| 154300 | virtual event | 262.39 | 732.69 | 694.88 | 1420.00 | 1900.00 | 0 | 16.0 | 2741 | 7911 | 有 |

吞吐变化为 +15.1%、-27.3%、-2.9%、+3.9%、+13.2%，正负交替；10 次采样全部出现 `Socket errors: ... timeout`（171–924 个）。该轮数据已被后续重测取代。

`cache` 池同轮次指标见各 `runs/<时间戳>/results.tsv`；其 `VT_STARTED` 恒小于 `event` 池，符合 `/api/v1/health` 主要经过 `event` 池的预期。

`completed_in_window` 为 `n/a` 的两轮（151356、153537）是脚本缺陷导致：`wait_metrics_ready()` 只判断指标行存在、未判断值是否为 `NaN`，服务刚就绪时 pre 快照取到 `NaN`，窗口增量无法计算。该缺陷已修复，修复后的轮次均正常产出。

### 第二轮：降低连接数重测

重测目的是把压测负载移出服务端的超时区。判断依据是 wrk 输出：`Socket errors:` 行只在实际发生错误时打印，该行缺失即表示该轮 timeout 为 0。

#### `-c32`（160332，仍有残留超时，仅作对照）

| 模式 | req/s | 平均(ms) | p50 | p90 | p99 | 非2xx | timeout |
| --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 109.14 | 339.16 | 253.44 | 764.96 | 1610.00 | 0 | 27 |
| virtual | 114.22 | 327.52 | 233.44 | 743.43 | 1530.00 | 0 | 19 |

吞吐 +4.7%，p99 -5.0%。两模式仍有 27/19 个 timeout，未达到「timeout 为 0」的判定条件，故不作为结论依据，继续降到 `-c64`。

#### `-c64`（5 个有效样本 + 1 个剔除样本）

| 轮次 | 模式 | req/s | 平均(ms) | p50 | p90 | p99 | 非2xx | timeout | VT_STARTED(event) | completed_in_window(event/cache) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 161003 | baseline | 187.27 | 378.76 | 327.45 | 823.69 | 1320.00 | 0 | 3 | 0 | n/a |
| 161003 | virtual | 211.43 | 328.21 | 302.20 | 694.30 | 1080.00 | 0 | **0** | 6383 | 2002 / 88 |
| 161612 | baseline | 77.91 | 559.57 | 378.96 | 1420.00 | 1930.00 | 0 | **243**（污染，剔除） | 0 | n/a |
| 161612 | virtual | 192.80 | 385.40 | 310.83 | 877.54 | 1420.00 | 0 | **0** | 5822 | 2205 / 23 |
| 162059 | baseline | 183.90 | 373.69 | 334.48 | 767.50 | 1200.00 | 0 | **0** | 0 | n/a |
| 162059 | virtual | 217.30 | 337.17 | 266.61 | 767.09 | 1400.00 | 0 | 25 | 6561 | 2222 / 17 |
| 164847 | baseline | 150.80 | 459.21 | 372.17 | 1030.00 | 1560.00 | 0 | 28 | 0 | n/a |
| 164847 | virtual | 195.94 | 351.72 | 317.18 | 715.25 | 1200.00 | 0 | **0** | 5921 | 2580 / 19 |
| 165501 | baseline | 214.84 | 329.80 | 288.93 | 703.77 | 1180.00 | 0 | **0** | 0 | n/a |
| 165501 | virtual | 201.52 | 338.85 | 316.73 | 693.67 | 1060.00 | 0 | **0** | 6087 | 2090 / 41 |
| 191439 | baseline | 199.27 | 354.12 | 296.36 | 759.91 | 1460.00 | 0 | 25 | 0 | n/a |
| 191439 | virtual | 294.41 | 265.63 | 212.54 | 628.72 | 1080.00 | 0 | **0** | 8859 | 3530 / 12 |

`161612` 的 baseline 轮与本仓库的 `mvnd -T4 -B clean verify` 并发执行，编译与 Testcontainers 抢占了 CPU，baseline 只有 77.91 req/s 且 timeout 达 243 个，属样本污染，**已从统计中剔除**；同一轮次的 virtual 数据（192.80 req/s、timeout 0）保留展示但不参与配对计算。

#### 中位数汇总（5 个有效 c=64 样本）

| 指标 | baseline 中位 | virtual 中位 | 变化 |
| --- | --- | --- | --- |
| req/s | 187.27 | 211.43 | +12.9% |
| 平均延迟 | 373.69 ms | 337.17 ms | -9.8% |
| p50 | 327.45 ms | 302.20 ms | -7.7% |
| p90 | 767.50 ms | 694.30 ms | -9.5% |
| p99 | 1320.00 ms | 1080.00 ms | -18.2% |
| timeout 合计 | 56 | 25 | — |

逐轮配对变化：+12.9%、+18.2%、+29.9%、-6.2%、+47.7%，中位 **+18.2%**。五个样本中 4 个为正、1 个为负；baseline 自身波动 150.80–214.84（+42.5%），virtual 自身波动 195.94–294.41（+50.2%）——两侧轮间波动同量级，是配对差异波动的主要来源。最后一轮 `191439` 的 +47.7% 明显高于其余样本，属单点极值，未单独作为结论依据。

`cache` 池在 c=64 各轮的 `VT_STARTED` 为 113 / 50 / 28 / 45 / 58 / 34，`completed_in_window` 为 88 / 23 / 17 / 19 / 41 / 12（按 `161003`、`161612`、`162059`、`164847`、`165501`、`191439` 顺序，含已剔除的 `161612`），量级远小于 `event` 池，与 `/api/v1/health` 主要由 `event` 池承载阻塞任务一致。各轮 `max_threads` 均为 16.0，`queue_capacity` 分别为 2048.0（event）/ 512.0（cache）。

## 虚拟线程生效证据

1. **池上限**：虚拟线程模式 `apex_blocking_io_max_threads` = 16.0，等于 `max-concurrency`；平台线程模式同值为 16.0，等于 `threads`。
2. **JFR 线程启动事件**：`jdk.VirtualThreadStart` 中匹配 `apex.<pool>-vt-` 前缀的事件，虚拟线程模式每轮均大于 0（5 个有效 c=64 样本的 event 池为 5822–8859 条），平台线程模式恒为 0。
   - `settings=profile` 默认不采集该事件，脚本显式传入 `jdk.VirtualThreadStart#enabled=true`，否则计数恒为 0。
3. **任务完成量**：虚拟线程模式 c=64 的 `completed_tasks` 在窗口内有真实增量（event 2002–3530，cache 12–88），说明池确实在执行任务，而非仅创建线程。
4. **平台线程模式 `completed_in_window` 恒为 `n/a`**：`BlockingIoSchedulers` 的平台模式快照从 Reactor 调度器读取 `estimateRemainingTaskCapacity` 等属性，不提供累计完成数，属设计行为，不是采集失败。

补充说明：`jcmd Thread.dump_to_file -format=text` 的文本 Dump 只输出虚拟线程数量汇总、不含其栈帧，因此本报告不采用 Dump 作为虚拟线程证据；`threads-<mode>.txt` 仅用于观察平台线程。

## 完整回归门禁结果

`BlockingIoSchedulers`（平台快照读取方式）与 `MetricsConfig`（`NaN` 过滤）有 Java 改动，故补跑完整门禁。

命令与结果：

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B clean verify
```

| 项目 | 结果 |
| --- | --- |
| 反应堆 | 31 个聚合模块（含根 POM、BOM 与重定向模块共 32 个日志单元）全部 `SUCCESS` |
| 构建结果 | `BUILD SUCCESS`，退出码 0 |
| 总耗时 | 20:07 min（Wall Clock），完成于 2026-10-27T16:22:35+08:00 |
| Surefire | 5057 个用例，`Failures: 0 / Errors: 0`，跳过 10（28 个模块产出汇总） |
| Failsafe | 389 个用例，`Failures: 0 / Errors: 0`，跳过 18（10 个模块产出汇总） |
| Spotless / PMD(p3c) / JaCoCo | 均绑定 verify 执行（`spotless:check`、`pmd:check (p3c-pmd-check)`、`jacoco:check`），随 `BUILD SUCCESS` 一并通过 |

耗时较长的模块为 `apex-auth-service`（06:42 min，含真实 Redis/HTTPS 的授权集成测试）、`apex-office-service`（02:46 min）、`apex-auth`（02:19 min）、`apex-tools-deployment`（01:36 min）。本次未出现计时敏感用例的负载型超时。跳过的 18 个 Failsafe 用例为仓库既有显式跳过项（如缺少真实 TC/双库环境的场景），不计为通过。

需要注意：该次 `clean` 删除了根 `target/`，第一轮压测的 JFR、线程 Dump、Prometheus 快照与服务日志随之丢失，仅 `results.tsv` 与 `wrk-*.txt` 因提前归档而保留。

## 有效性限制

- **超时已大幅缓解但未彻底消除**：第一轮 10 次采样全部超时（171–924 个）；重测 c=32 为 27/19，c=64 五个有效样本 baseline 合计 56 个、virtual 合计 25 个（baseline 的 56 个分布在 `161003`/`164847`/`191439` 三轮，virtual 的 25 个全部出现在 `162059`），两侧都有出现且都集中在个别轮次，说明剩余超时更接近负载抖动的随机噪声，而非某一模式的固有行为。
- **样本量与波动**：c=64 仅 5 个有效样本，每轮 30s、单次采样、无预热轮，未计算置信区间。baseline 的极差达 42.5%，个别样本（164847 的 150.80 req/s、timeout 28）明显偏低，而 `191439` 的 virtual 侧 294.41 req/s 又明显偏高，说明本机作为压测机的稳定性有限。中位 +18.2%（逐轮配对）与 +12.9%（中位值汇总）的两种口径都为正，但不足以排除噪声。
- **单机共置**：wrk、被测服务、MySQL/Redis 容器同机运行，CPU 争用直接影响结果；161612 的 baseline 轮已实证这一点。
- **覆盖范围**：仅 `event`、`cache` 两个池；`jdbc`、`storage`、`remote`、`general` 未经过该接口，未参与本次对比。
- **接口代表性**：`/api/v1/health` 的阻塞任务很短，虚拟线程的主要收益场景是长时间阻塞（慢 SQL、远程调用），该接口不能代表收益上限。
- **`saturation` 语义**：平台线程模式 `saturation` 由 `estimateBusy()/capacity` 推导，虚拟线程模式为 `active/max-concurrency`，两者口径不同，不可直接横向比较。
- **未执行**：pinning（`synchronized` 钉住载体线程）追踪、长时运行稳定性观察、真实阻塞接口压测。

## 结论

1. 虚拟线程开关在本项目中的注入与观测链路可用、可验证，`apex.blocking-io.<pool>.virtual-threads` 的行为符合设计；`BlockingIoSchedulers` 与 `MetricsConfig` 的改动通过完整回归门禁。
2. 第一轮 `-c200` 的数据因普遍 socket timeout 不可用；把连接数降到 `-c64` 后，虚拟线程表现出方向性正收益：**中位吞吐 +12.9%（中位值汇总）至 +18.2%（逐轮配对中位），中位平均延迟 -9.8%，中位 p99 -18.2%，timeout 总量上虚拟线程更低（25 对 56）且集中在个别轮次**。
3. 但收益幅度与噪声幅度同量级（5 个样本中仍有 1 个为 -6.2%，且两侧轮间波动均为 40%+），且仅覆盖短阻塞的健康检查接口。**因此仍不建议基于本报告调整任何默认配置**；默认维持平台线程，虚拟线程继续按池显式开启，是否放开需先补真实阻塞接口与 pinning 验证。
4. 与第一轮相比，本轮结论的可靠性提升来自「连接数下调 + 多轮中位数」两点，而非新增证据类型；若要进一步提升置信度，应改为固定负载发生器、增加预热轮与样本数（≥ 10 轮），并避免与构建任务并行。

## 产物清单

| 文件 | 说明 |
| --- | --- |
| [summary.md](./summary.md) | 第一轮最终一轮（`-c200`，`154300`）由脚本生成的对比摘要 |
| [jvm-args-baseline.txt](./jvm-args-baseline.txt) | 平台线程模式完整启动命令 |
| [jvm-args-virtual.txt](./jvm-args-virtual.txt) | 虚拟线程模式完整启动命令 |
| [runs/](./runs/) | 12 个轮次的 `results.tsv` 与 `wrk-baseline.txt`、`wrk-virtual.txt`；`20261027-191439` 另含 JFR、Prometheus 快照、线程 Dump 与服务日志 |

`runs/` 下按时间戳分目录：第一轮 5 个（`20261027-150716`、`151356`、`152036`、`153537`、`154300`），重测 7 个（`160332` c=32；`161003`、`161612`、`162059`、`164847`、`165501`、`191439` c=64，其中 `161612` 已剔除）。

第一轮的 `vt-start-*.jfr`、`threads-*.txt`、`prom-*.txt`、`service-*.log` 已随 `clean verify` 的根 `clean` 删除，不可恢复；重测轮次中仅 `20261027-191439` 的同类产物已归档到 `runs/20261027-191439/`，其余重测轮次只保留 `results.tsv` 与 `wrk-*.txt`。

## 未确认项与全量测试必要性

评估口径：**能否影响默认配置决策**、**是否存在正确性风险**、**失败代价是否高于测试成本**。

### 必须补充（影响可用性判断）

| 项 | 原因 | 状态 |
| --- | --- | --- |
| 降低连接数重测 | 第一轮每轮都有 socket timeout，数据不足以支撑任何结论 | **已完成**：1 轮 `-c32` + 5 个有效 `-c64` 样本，逐轮配对中位吞吐 +18.2%（中位值汇总 +12.9%），见上文 |
| 完整回归门禁 | `BlockingIoSchedulers`、`MetricsConfig` 有 Java 改动，此前未跑 `clean verify` | **已完成**：`mvnd -T4 -B clean verify` → `BUILD SUCCESS`，31 个聚合模块，20:07 min，Surefire 5057 / Failsafe 389 全通过 |

### 建议补充（决定虚拟线程能否用于生产）

| 项 | 原因 | 建议 |
| --- | --- | --- |
| 真实阻塞场景压测 | 健康检查接口无法体现虚拟线程收益上限 | 选一条有慢 SQL 或远程调用的业务接口，对比 `jdbc` / `remote` 池 |
| pinning 追踪 | 若存在 `synchronized` 包裹阻塞 I/O，虚拟线程会被钉住，收益归零甚至退化 | 用 `BENCH_TRACE_PINNED=true`（`-Djdk.tracePinnedThreads=full`）跑一轮，检查日志 |
| `max-concurrency` 饱和行为 | 需要确认超限时的拒绝/排队与错误语义 | 将 `max-concurrency` 调到远低于并发的值，观察 `rejected`、`saturation` 与响应码 |
| `BlockingIoSchedulers` 平台快照反射断言 | 已有 `BlockingIoSchedulersTest` 只断言默认 `maxThreads` / `queueCapacity` 与 `saturation` 的数学计算；真实 `activeTasks` / `queued` 走 `estimateBusy` 反射，失效时静默降级为 0，现有用例拦不住（本次已踩坑） | 补一条「向池内提交任务后 `activeTasks > 0`」的断言 |
| 提高压测置信度 | 5 个样本、baseline 波动 42.5%，中位差异与噪声同量级 | 固定负载发生器、加预热轮、样本数 ≥ 10，且不与构建并行 |

### 暂不需要全量执行

| 项 | 原因 |
| --- | --- |
| 其余四个服务（auth/workflow/file/office）同类压测 | 执行器实现由 `apex-common` 统一提供，已由 system-service 覆盖；各自业务接口差异留到有明确需求时再做 |
| 配置文件中 `apex.blocking-io.*.virtual-threads` 的绑定验证 | 与 JVM 参数走同一个 `@ConfigurationProperties` 绑定，语义一致，重复验证收益低 |
| `storage` / `general` 池压测 | 当前无对应热点接口，测了也无法驱动配置决策 |
| 长时间（分钟级）稳定性与内存观察 | 虚拟线程为短生命周期任务线程，30s 已能暴露创建/回收问题；确有上线需求时再做 10 分钟以上的长稳 |

### 总结

两项阻塞性缺口（数据失真、门禁未跑）已在本轮补齐，虚拟线程在短阻塞接口上的表现由「无法判定」变为「方向性正收益但幅度接近噪声」。**仍不需要**对全部未确认项做全量测试：推进上线前补「真实阻塞接口 + pinning + 饱和行为 + 快照反射断言 + 提高样本量」即可，其余项目前不具备投入产出比。
