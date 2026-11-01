# 批次 C9 测试报告：组合部署拓扑（apex-boot-all）

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 C9（M3 组件化服务化）
- 执行日期：2026-11-01
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；M3 剩余 C4（interfaces 层迁移，D07）

## 1. 已交付变更

### 新增 `apex-boot-all` 组合部署包

- 单进程装配全部业务组件（`apex-system` + `apex-auth` + `apex-workflow` + `apex-file` + `apex-office`），用于 basic / 示例 / 演示档
- 启动类 `BootAllApplication` **不使用 `scanBasePackages` 全根扫描**——组件全部经 C7 的 `AutoConfiguration` 入口自动装配，这本身即是对"组件化 + 自动装配"能力的验证
- 事件通道：组合拓扑下 `apex.events.channel=auto` 自动退化为进程内派发（C8），无需业务代码改动
- 已注册进根 POM reactor（31 → 32 模块）
- 企业部署仍为五服务拆分拓扑，二者并存

### 装配完整性测试（`BootAllAssemblyTest`，4 用例）

| 用例 | 验证点 |
| --- | --- |
| 组件入口类可见 | auth/workflow/file/office 四个 `*ComponentAutoConfiguration` 在组合包类路径可加载 |
| 组件核心类可见 | 四个上下文的代表性应用服务类可加载 |
| 自动装配清单包含组件入口 | 汇总类路径上全部 `AutoConfiguration.imports`，断言声明了四个组件入口 |
| 组件包内存在 Spring 组件 | 扫描 auth/workflow 根包，断言存在 `@Service`/`@Component`/`@Repository`（装配前提） |

## 2. 结果

- `BootAllAssemblyTest`：**4/4 通过**。
- `apex-boot-all -am test`：**BUILD SUCCESS**。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links **0 断裂**。

## 3. 未验证项与边界

- **组合拓扑的完整启动冒烟未执行**：需 H2 + 本地存储 + 无 MQ 环境下 `java -jar` 启动（依赖 basic-schema 与各组件配置属性合并），当前只验证了装配前提（类可见性 + 装配清单 + 组件扫描）。**这是 C9 的核心待验证项**，建议在部署环境完成。
- **拓扑矩阵未覆盖**：方案 C9 要求验证 ①五服务拆分（现状，已由各 service 测试覆盖）②all-in-one ③system+file 合并等任意子集。本批只交付 all-in-one 形态与装配测试，未做多拓扑契约矩阵。
- **配置属性合并风险**：五个组件在同一进程共享 `application.yml`，各组件配置前缀不同（`apex.component.*`、`storage.*`、`file.*`、`office.*`、`workflow.*`）理论不冲突，但未经真实启动验证。
- **`apex-system` 未纳入组件入口**（其 interfaces 层包名在业务根包外），all-in-one 下 system 仍靠自身配置装配——待 C4（interfaces 迁移）后统一。
- **端口/路由冲突**：五组 Router 在同一进程注册，路径前缀不同（/api/v1、/workflow、/file、/office 等）预期无冲突，未实测。
