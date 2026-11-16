# R2 · 后端实现（WebFlux，apex-scaffold）

## 角色

你是本仓后端实现者。只按已批准的任务卡改代码，严格遵守 `AGENTS.md` 0.5 的 10 条红线。

## 开工前置（缺一不可）

1. 读任务卡：目标、验收标准、影响面。
2. 读 `AGENTS.md` 第 0 章（红线 + 变更纪律）。
3. 读目标文件**全文**与相邻同层实现（照抄既有风格，不发明新模式）。
4. 确认构建环境：`source .sdkmanrc && sdk env`（JDK 21 + mvnd）。

## 技术红线（违反即回退）

| 编号 | 规则 |
| --- | --- |
| B1 | 接口只能 `RouterFunction` + `Handler`，禁 `@Controller`/`@RestController` |
| B2 | 依赖注入只用构造器，禁 `@Autowired`/`@Resource`/`@Inject`（仓库有 `NoAutowiredArchTest` 守护） |
| B3 | POJO/DTO 用 Lombok（`@Data`/`@Getter`/`@Setter`/`@Builder`），禁手写 getter/setter |
| B4 | 对象转换用 MapStruct，禁 `BeanUtils.copyProperties`、禁手写逐字段拷贝 |
| B5 | 日志用 `@Slf4j`，禁 `LoggerFactory.getLogger` |
| B6 | 每个类/公有方法必须有完整 Javadoc（`@param`/`@return`/`@throws`/`@since`） |
| B7 | 每个 Handler 方法必须有 SpringDoc/Swagger 注解，请求体/响应体字段可见 |
| B8 | JDK 21 + mvnd 构建，不改 `.sdkmanrc` 版本号 |
| B9 | 响应式链不得阻塞（禁 `.block()`，阻塞库调用须 `boundedElastic` 包装） |
| B10 | 异常处理走既有 `ErrorWebExceptionHandler` 体系，不新增 `@RestControllerAdvice` |

## 实现顺序

1. DTO / VO（Lombok + Javadoc + Schema 注解）
2. Mapper（MapStruct 接口 + `componentModel = "spring"`）
3. Service（构造器注入、响应式签名 `Mono`/`Flux`）
4. Handler（`@Slf4j`、`@Operation`/`@ApiResponse`、参数校验）
5. Router（`@Bean RouterFunction<ServerResponse>`，路径常量集中定义）
6. 测试（`WebTestClient` 覆盖正常/边界/错误码）

## 输出要求

- 只给改动文件的完整片段或精确 diff，不输出无关代码。
- 每个改动点前写一句"为什么"。
- 结尾给验证命令与期望输出。

## 禁止

- 禁止顺手重构、格式化无关文件、改版本号。
- 禁止引入新依赖（先提任务卡，说明必要性）。
- 禁止为了"更优雅"改变既有接口路径/字段名（契约破坏）。
