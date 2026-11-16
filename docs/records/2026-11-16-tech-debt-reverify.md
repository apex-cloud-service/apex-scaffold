# 技术债卡复核（BE-001 / BE-017）与 mvnd 守护进程故障处置

> 日期：2026-11-16 · 分支 `dev` · 触发原因：上一轮总结把 `BE-001`、`BE-017` 记为「本轮未动」，与任务盘状态不符；本次逐条复核取证并更正口径。

## 1. BE-001（`.mmd` 补导 `.svg`）：已完成，复核通过

| 复核项 | 命令 | 结果 |
| --- | --- | --- |
| 源文件数 | `find docs -name "*.mmd" \| wc -l` | 68 |
| 导出件数 | `find docs -name "*.svg" \| wc -l` | 247（含图标类 SVG，非全部来自 `.mmd`） |
| 配对缺口 | `python3` 逐文件判断 `${f%.mmd}.svg` 是否存在 | **缺失 0** |
| 治理自检 | `bash scripts/governance/verify-docs.sh` 第 6 项 | 无「缺同名 SVG」告警 |

结论：**68/68 配对完成，BE-001 已闭合**，无需再补导出；任务盘状态正确（已完成）。

## 2. BE-017（`apex-common` 手写 Logger → `@Slf4j`）：已完成，复核通过

| 复核项 | 命令 | 结果 |
| --- | --- | --- |
| 主代码手写 Logger 字段 | `grep -rn "private static final Logger\|LoggerFactory.getLogger" **/src/main/java` | **0 处** |
| `@Slf4j` 覆盖 | `grep -rl "@Slf4j" --include=*.java .` | 482 个文件 |
| 仅存的手写用法 | `grep -rn "LoggerFactory.getLogger" --include=*.java .` | 全部在 `src/test/java`：测试用 logback `ListAppender` 抓取日志事件做断言（必须拿到 `ch.qos.logback.classic.Logger` 再 `addAppender`），**改 `@Slf4j` 反而做不到** |

结论：**主代码已 100% 注解化，BE-017 已闭合**；测试代码中的 `LoggerFactory.getLogger` 属测试装置，不是「手写日志器」，保持原样并在本文存档理由。

## 3. 模块验证（apex-common）

```bash
mvn -pl :apex-common -am test -DskipITs
```

实测结果：`BUILD SUCCESS`（26.272s，`apex-common` 22.301s），jacoco `All coverage checks have been met.`，91 个类的 bundle 分析通过。

## 4. mvnd 守护进程故障与处置（环境记录）

| 项 | 事实 |
| --- | --- |
| 现象 | `mvnd -pl :apex-common -am verify` 反复输出 `[ERROR] aktStatus is NULL: maximum Iterations exceeded, abort 0`（数十次），构建未执行 |
| 尝试 | `mvnd --stop` 后重试、`MVND_NO_DAEMON=1`、`mvn -o`（离线缺 `junit:junit:3.8.1`，pmd 插件无法解析）均未绕过 |
| 可用回退 | **`mvn`（sdkman `maven/current`，3.10.0 + JDK 21.0.13）工作正常**；离线模式不可用于 `verify`（pmd 依赖未缓存），需联网 |
| 结论 | mvnd 2.0.0-rc-3 守护进程在本机当前状态异常，属工具环境问题，不是项目问题；验证改用 `mvn -pl <module> -am test|verify`，并把 `aktStatus is NULL` 作为已知故障记录 |

> 后续若需恢复 mvnd：`pkill -f mvnd-daemon` → 删除 `~/.m2/mvnd` 下的 daemon 日志/锁 → `mvnd --status` 观察；恢复前请一律用 `mvn`。

## 5. 口径更正

- 上一轮总结的「BE-001、BE-017 仍属技术债，本轮未动」**不成立**：两卡在 2026-11-16 治理批次中已完成，本次为复核而非新改动。
- 本次**未改任何代码**，只新增本记录。
