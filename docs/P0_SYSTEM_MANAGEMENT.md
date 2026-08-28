# P0 阶段实施说明：系统管理能力落地

> 版本：v1.1.3（2026-08-27）
> 模块：菜单管理 / 字典管理 / 参数配置 / 定时任务 / 登录安全（图形验证码 + 登录日志）/ 管理后台升级

## 一、实施总览

本阶段依据《脚手架评审与改进指南》中 **P0 优先级** 的四项缺口，补齐了通用后端脚手架
"让客户 30 分钟跑通完整管理系统"的产品化能力，全部能力基于 Spring WebFlux 函数式端点
（RouterFunction）实现，不引入任何 servlet / Spring MVC 依赖。

| P0 项 | 能力 | 状态 |
|---|---|---|
| P0-1 | 系统管理三件套：菜单 / 字典 / 参数 | ✅ |
| P0-2 | 定时任务管理（动态注册 / 启停 / 手动触发 / 执行日志） | ✅ |
| P0-3 | 管理后台升级（系统管理控制台，闭环"建角色→配菜单→建用户→看日志"） | ✅ |
| P0-4 | 登录安全（图形验证码 + 登录日志，等保 2.0 审计要求） | ✅ |

## 二、数据库变更（Flyway 基线 V1__init_schema.sql）

数据库迁移统一由 Flyway 管理（`classpath:db/migration`），本次在既有干净基线上
**追加 7 张通用表**，不破坏既有迁移版本：

| 表名 | 用途 | 关联实体 |
|---|---|---|
| `sys_menu` | 菜单树与按钮权限定义（目录/菜单/按钮三级） | MenuEntity / MenuPO |
| `sys_dict_type` | 字典类型定义（编码全局唯一） | DictTypeEntity / DictTypePO |
| `sys_dict_data` | 字典数据项（按类型归属，默认项唯一） | DictDataEntity / DictDataPO |
| `sys_config` | 系统参数键值对（内置/用户两种类型） | ConfigEntity / ConfigPO |
| `sys_job` | 定时任务定义（Cron + 调用目标 + 并发策略） | JobEntity / JobPO |
| `sys_job_log` | 定时任务执行日志（状态/异常/耗时快照） | JobLogEntity / JobLogPO |
| `sys_login_log` | 登录尝试日志（账号/IP/结果/时间，审计用） | LoginLogEntity / LoginLogPO |

同步维护 Flyway 迁移 `db/migration`（MySQL 版 V1-V6，各环境共用），保持表结构与生产一致。

## 三、API 清单

### 3.1 菜单管理 `/admin-api/v1/menus`（权限：`system:menu:*`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/menus` | 查询菜单树（扁平数据按父子组装） |
| GET | `/menus/{menuId}` | 查询菜单详情 |
| POST | `/menus` | 创建菜单（按钮类型必须携带 perms） |
| PUT | `/menus/{menuId}` | 更新菜单 |
| DELETE | `/menus/{menuId}` | 删除菜单（含子菜单拒绝删除） |
| PUT | `/menus/{menuId}/status` | 启用 / 停用菜单 |

### 3.2 字典管理 `/admin-api/v1/dict`（权限：`system:dict:*`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/dict/types` | 查询字典类型列表 |
| GET/POST/PUT/DELETE | `/dict/types/{id}` | 字典类型 CRUD |
| PUT | `/dict/types/{id}/status` | 启用 / 停用字典类型 |
| GET | `/dict/data?dictType=xx` | 按类型查询启用数据项（业务读取用） |
| GET/POST/PUT/DELETE | `/dict/data/{id}` | 字典数据 CRUD |
| PUT | `/dict/data/{id}/status` | 启用 / 停用字典数据 |

### 3.3 参数配置 `/admin-api/v1/configs`（权限：`system:config:*`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/configs` | 查询全部参数配置 |
| GET/POST/PUT/DELETE | `/configs/{id}` | 参数 CRUD（内置参数禁止删除） |
| PUT | `/configs/{id}/status` | 启用 / 停用参数 |

### 3.4 定时任务 `/admin-api/v1/jobs`（权限：`system:job:*`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/jobs` | 查询任务列表 |
| GET/POST/PUT/DELETE | `/jobs/{id}` | 任务 CRUD（创建/更新后自动注册/重注册调度） |
| PUT | `/jobs/{id}/status` | 启用（注册调度）/ 停用（取消调度） |
| POST | `/jobs/{id}/run` | 立即执行一次（不改变调度状态） |
| GET | `/jobs/{id}/logs?limit=20` | 查询最近执行日志 |

### 3.5 登录安全

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/auth/captcha` | 获取图形验证码（ID + Base64 PNG） |
| POST | `/api/v1/auth/captcha/verify` | 校验验证码（一次性消费） |
| GET | `/admin-api/v1/login-logs`（权限 `system:login-log:list`） | 分页查询登录日志（按账号/状态筛选） |

登录接口（`POST /api/v1/auth/login`）新增可选字段 `captchaId` / `captchaCode`：
携带时服务端强制校验图形验证码，校验失败返回 `CAPTCHA_INVALID`（4741）；
未携带（APP 客户端未开启验证码）时不影响原有登录流程。

## 四、技术要点

### 4.1 定时任务动态调度（无 Quartz 依赖）

- `CronExpression`：自研轻量 Cron 解析器，支持 Quartz 风格 6/7 段表达式
  （秒-周[年]），覆盖 `*`、`?`、列表、范围、步长与 JAN-DEC/SUN-SAT 名称简写，
  提供 `next(LocalDateTime)` 计算下次触发时间。
- `JobScheduler`：基于 Reactor 的递归调度链（`Mono.delay → 执行 → 记录日志 → 递归`），
  每任务独立可取消；支持"禁止并发"互斥；`@PreDestroy` 统一释放调度资源。
- `JobInvoker`：反射解析 `beanName.methodName("arg", 1)` 调用目标，自动适配
  Mono/Flux 返回值并阻塞等待完成。
- `JobInitializer`：应用启动后自动恢复全部启用任务的调度。

### 4.2 图形验证码（无第三方库）

- `CaptchaService` 使用 `java.awt` 原生绘制 PNG（干扰线 + 噪点 + 旋转字符），
  Base64 输出；答案存 Redis（`apex:captcha:*`，TTL 可配），校验后立即删除（防重放）。
- 配置项：`apex.captcha.enabled/length/ttl`（见 `application.yml`）。

### 4.3 登录日志审计

- 登录成功/失败均落库 `sys_login_log`（失败计数与审计并行，日志写入失败不阻塞登录）。
- 管理后台按账号模糊、状态筛选分页回溯，支撑异常登录排查。

### 4.4 WebFlux 化调整（本次一并完成）

- 删除历史遗留的 MVC 注解式死代码 `UserControllerV1/V2`（`@RestController` 空实现）。
- 管理后台短入口 `/admin` 由注解式 `@Controller` 改为函数式重定向路由
  （`ServerResponse.temporaryRedirect`）。
- `GlobalExceptionHandler` 移除 Spring MVC 专用的 `MethodArgumentNotValidException`
  处理器（WebFlux 下由 `WebExchangeBindException` 覆盖），函数式端点异常由其提供的
  `WebExceptionHandler` bean 统一兜底。
- 全工程依赖仅 `spring-boot-starter-webflux`，无 `spring-webmvc` / servlet API。

## 五、权限点规划（配合 sys_permission 数据初始化）

建议在部署时向 `sys_permission` 预置以下权限码，并分配给后台管理员角色：

```
system:menu:list/query/add/edit/remove
system:dict:list/query/add/edit/remove
system:config:list/query/add/edit/remove
system:job:list/query/add/edit/remove/run
system:login-log:list
staff:manage / staff:session:revoke（用户管理，已有）
```

## 六、测试与验证

- 新增单元测试 62 个：MenuServiceTest(10)、DictServiceTest(9)、ConfigServiceTest(9)、
  CronExpressionTest(14)、JobServiceTest(9)、CaptchaServiceTest(6)、LoginLogServiceTest(3)、
  异常/前端契约测试更新 2 个。
- 全量测试保持全绿，编译通过（mvnd + sdkman 默认 JDK 17）。
