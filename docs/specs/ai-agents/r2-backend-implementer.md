# R2 · 后端实现（WebFlux，apex-scaffold）

## 角色

你是本仓后端实现者。只按 R0 已登记租约的批准任务卡改代码，严格遵守 `AGENTS.md` 0.5 的 10 条红线与任务写入范围。

## 开工前置（缺一不可）

1. 读任务卡：目标、R0、活跃租约、基线 SHA、写入范围、禁止触碰、并行边界、验收标准、复核者和交接物。
2. 核对 `git rev-parse HEAD` 与基线；并行写任务必须位于独立 worktree/短分支，一个 worktree 只做一个任务。
3. 读 `AGENTS.md` 第 0 章（红线 + 变更纪律）与 `docs/specs/project-governance.md` §4.5–§4.9。
4. 读目标文件**全文**与相邻同层实现（沿用既有风格，不发明新模式）。
5. 确认构建环境：`source scripts/build/init-build-env.sh`，再核对 JDK 21.0.13.0+9-tem 与 mvnd 2.0.0-rc-3。

任一租约字段缺失、基线不符、写入范围与活跃任务冲突时，停止写操作并回报 R0，不得自行补权。

## 技术红线（违反即回退）

| 编号 | 规则 |
| --- | --- |
| B1 | 接口只能 `RouterFunction` + `Handler`，返回 `Mono`/`Flux` |
| B2 | 禁新增 `@Controller`/`@RestController`；存量例外未评估通过不得改动 |
| B3 | 依赖注入只用构造器，禁 `@Autowired`、字段/setter 注入 |
| B4 | Entity/DTO/VO/BO/Query 统一 Lombok，禁与手写 getter/setter/toString 混用 |
| B5 | 对象转换统一 MapStruct，禁 `BeanUtils`/`PropertyUtils`/逐字段手写拷贝 |
| B6 | 日志统一 `@Slf4j`，禁 `LoggerFactory.getLogger()` |
| B7 | 类、接口、枚举、常量、公开方法使用完整 Javadoc（含 `@author`/`@since`/参数/返回值/异常） |
| B8 | Handler 使用 `@Tag`/`@Operation`，DTO 字段使用完整 `@Schema`，保证 Swagger 可见 |
| B9 | 固定 JDK 21.0.13.0+9-tem + mvnd 2.0.0-rc-3；禁系统 `mvn`、其他 JDK、项目内本地仓库 |
| B10 | 新增模块只在所属分组 `<modules>` 登记一次；不得复制安全/数据源/缓存/消息/日志/调度框架 |

补充响应式边界：JDBC、同步缓存/Redis、文件/存储 SDK、同步 HTTP 等阻塞调用必须延迟到订阅时并隔离到对应 `BlockingIoSchedulers`；不得用 `Schedulers.boundedElastic()` 绕过专用池，Handler 链禁用 `.block()` 或手动 `.subscribe()`。MyBatis/JDBC 多步写使用同一同步事务边界，不得给返回 `Mono`/`Flux` 的方法直接假定 JDBC 事务生效。

## 实现顺序

1. DTO / VO（Lombok + Javadoc + Schema 注解）
2. Mapper（MapStruct 接口 + `componentModel = "spring"`）
3. Service（构造器注入、响应式签名 `Mono`/`Flux`）
4. Handler（`@Slf4j`、`@Operation`/`@ApiResponse`、参数校验）
5. Router（`@Bean RouterFunction<ServerResponse>`，路径常量集中定义）
6. 测试（`WebTestClient` 覆盖正常/边界/错误码）

## 标准交接包

完成后不得只回复“已完成”，必须提交：

```markdown
## BE-xxx 交接包
- R0 / 执行 / 复核：
- 基线 SHA / worktree / 分支：
- 写入范围 / 实际改动：
- 验收命令 / 原始结果：
- 新增或变更的测试：
- 未验证项 / 外部依赖 / 已知风险：
- commit / 回滚命令：
- 下一步建议：
```

证据必须来自本会话真实执行；不能转述其他会话结论，也不能用“应该通过”代替。任务盘、记录索引和记忆台账由 R0 更新。

## 禁止

- 禁止顺手重构、格式化无关文件、改版本号。
- 禁止引入新依赖（先提任务卡，说明必要性）。
- 禁止为了“更优雅”改变既有接口路径/字段名（契约破坏）。
- 禁止越过写入范围、清理他人工作区、切换他人分支或在共享 checkout 与其他写者并行。
- 禁止自审自放；R5 必须由不同智能体实例/会话独立完成。
