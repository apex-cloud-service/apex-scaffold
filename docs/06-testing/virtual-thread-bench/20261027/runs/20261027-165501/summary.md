# 虚拟线程压测报告

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 时间：2026-10-27 16:57:57 CST
- 目标：`http://127.0.0.1:8082/api/v1/health`
- 压测参数：wrk -t4 -c64 -d30s --latency（采样点 15s）
- 压测池：`event,cache`
- 并发上限：基准 `BENCH_CONCURRENCY=16`，可用 `BENCH_CONCURRENCY_<POOL>` 覆盖单个池
- 保护开关：`BENCH_SAMPLE_GUARD=true`（true 时关闭 IP 限流与 Sentinel 系统规则）
- JAR：`/Users/work/apex-scaffold/apex-system-service/target/apex-system-service-1.3.1.jar`

## 吞吐与延迟

| 模式 | Requests/sec | 平均延迟(ms) | p50(ms) | p90(ms) | p99(ms) | 非 2xx |
| --- | --- | --- | --- | --- | --- | --- |
| baseline | 214.84 | 329.80 | 288.93 | 703.77 | 1180.00 | 0 |
| virtual | 201.52 | 338.85 | 316.73 | 693.67 | 1060.00 | 0 |

## 阻塞 I/O 池指标（压测中途采样，15s 附近）

| 模式 | 池 | max_threads | queue_capacity | saturation | 采样窗口完成数 | 压测窗口内启动的 `apex.<pool>-vt-*` 数（JFR） |
| --- | --- | --- | --- | --- | --- | --- |
| baseline | event | 16.0 | 2048.0 | 0.3125 | n/a | 0 |
| baseline | cache | 16.0 | 512.0 | 0.0 | n/a | 0 |
| virtual | event | 16.0 | 2048.0 | 0.0 | 2090 | 6087 |
| virtual | cache | 16.0 | 512.0 | 0.0 | 41 | 58 |

> 「采样窗口完成数」= 压测开始时到采样点的 `completed_tasks` 增量；平台线程模式下 Reactor 调度器不暴露该计数，显示 `n/a`。
> 「启动的虚拟线程数」= 该轮 JFR 记录到的 `jdk.VirtualThreadStart` 事件数（仅统计压测开始之后、名称前缀匹配该池的事件）；平台线程模式为 0。

## 结论

- 吞吐：214.84 → 201.52 req/s（-6.2%）
- p99：1180.00 ms → 1060.00 ms（-10.2%）

## 判定虚拟线程是否生效

1. 该池在压测窗口内启动了虚拟线程（`jdk.VirtualThreadStart` 事件数 > 0，线程名 `apex.<pool>-vt-*`），即该池已切换到虚拟线程执行器；平台线程模式只有 `apex.<pool>-N`，事件数为 0。
   `jcmd Thread.dump_to_file -format=text` 的文本格式不含虚拟线程栈（仅有总数汇总），不能用来数虚拟线程。
2. `apex_blocking_io_max_threads` 在虚拟线程模式等于配置的 `max-concurrency`，平台线程模式等于 `threads`。
3. 用 `-Djdk.tracePinnedThreads=full`（`BENCH_TRACE_PINNED=true`）可在服务日志观察 `synchronized` 阻塞导致的载体线程钉住。

## 产物

- `results.tsv`：逐池结果明细
- `wrk-<mode>.txt`：wrk 原始输出
- `prom-<mode>-pre.txt` / `prom-<mode>-mid.txt`：压测前与压测中的 Prometheus 快照
- `vt-start-<mode>.jfr`：该轮 JFR 录制，含 `jdk.VirtualThreadStart` 事件
- `threads-<mode>.txt`：平台线程 Dump（不含虚拟线程栈）
- `jvm-args-<mode>.txt` / `service-<mode>.log`：启动参数与启动日志
