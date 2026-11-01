# 批次 C7 测试报告：业务库组件入口自动装配

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 C7（M3 组件化服务化）
- 执行日期：2026-11-01
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；下一批 C3（SPI 扩展点）/C8（事件通道）待用户验收后启动

## 1. 已交付变更

四个业务库新增组件入口，**引入依赖即完成装配**，不再要求消费方手工 `scanBasePackages`：

| 业务库 | 组件入口 | 装配注册文件 |
| --- | --- | --- |
| `apex-file` | `FileComponentAutoConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| `apex-workflow` | `WorkflowComponentAutoConfiguration` | 同上 |
| `apex-office` | `OfficeComponentAutoConfiguration` | 同上 |
| `apex-auth` | `AuthComponentAutoConfiguration` | 同上 |

统一结构（每个组件）：

```
@AutoConfiguration
@ConditionalOnProperty(prefix="apex.component.<name>", name="enabled", matchIfMissing=true)
@ConfigurationPropertiesScan("<component root>")
  ├─ <Name>CoreConfiguration      @ComponentScan(根包, 排除 interfaces 与 autoconfigure 自身)
  └─ <Name>InterfacesConfiguration @ConditionalOnProperty(interfaces-enabled, matchIfMissing=true)
                                    @ComponentScan(根包.interfaces)
```

设计要点：

- **组件总开关** `apex.component.<name>.enabled`（默认 true）
- **协议层子开关** `apex.component.<name>.interfaces-enabled`（默认 true）——关闭后仅装配领域/应用/基础设施，供组合部署（C9）时由其它组件承载 HTTP 入口
- **排除 autoconfigure 自身**：`@ComponentScan` 扫描组件根包时会把组件入口类本身扫进来，触发 Spring 的循环 `@Import` 检测（实测报 `A circular @Import has been detected`）——必须显式排除
- 配置属性扫描限定组件根包，避免跨组件误装配
- `apex-system` 未加组件入口：其 interfaces 层包名不在业务根包内（`com.apex.scaffold.interfaces`），且 D07（interfaces 迁移）尚未执行，留待 C4

## 2. 门禁结果

| 模块 | 结果 |
| --- | --- |
| `apex-file` / `apex-file-service` | **SUCCESS** |
| `apex-workflow` / `apex-workflow-service` | **SUCCESS** |
| `apex-office` / `apex-office-service` | **SUCCESS** |
| `apex-auth` / `apex-auth-service` | **SUCCESS** |
| 四 service `-am test` 整体 | **BUILD SUCCESS** |

## 3. 过程缺陷与修复记录

1. **循环 `@Import`**：组件入口以 `@ComponentScan` 扫根包时扫到自己 → 显式排除 `<root>.autoconfigure..*`。
2. **切片测试被组件装配污染**：`OfficeOpenApiContractTest` 的 `@EnableAutoConfiguration` 把组件入口扫入，导致其缺失 Mapper Bean → 在该测试的 `exclude` 中加入组件自动配置类（切片测试只 `@Import` Router + mock handler，不应加载组件全量）。
3. **`BlockingIoIsolationTest` 瞬时超时**（5s 阻塞读）：高并发 `-T4` 下出现，单跑 4/4 通过——按仓库既有口径（计时敏感测试负载超时可重跑）归类为瞬时，不放宽阈值。
4. **既有失败识别**：`apex-system` 的 `OpenApiEndpointsIntegrationTest`/`ApiCallLogIntegrationTest` 在 **HEAD 基线上即失败**（`JobMapper` 无 Bean，e17fe4c 引入，记忆已有记录）——已用 `git stash` 基线验证确认与本批无关，未纳入本批口径。
5. 报告内文档相对路径多退一级 → 修正。

## 4. 未验证项与边界

- **组件契约元数据**（方案 C7 要求的"端口/事件/配置/依赖/拔插步骤"清单）未产出——它与 C9 组合拓扑、D4 能力目录同源，宜在 C9 实施时统一生成，避免重复维护。
- **组件装配契约测试**（`ApplicationContextRunner`：开关关闭零 Bean、与 infrastructure 自动配置共存）未新增——列为 C9 批次项（与拓扑矩阵一并验证更经济）。
- **生产启动验证**：四 service 的测试全绿已覆盖上下文加载，但真实进程启动（含 Nacos/DB）未执行。
- **basic 档 JAR 裁剪实证**：C1 的 `optional` 改造尚未以"排除依赖构建 + 启动"实证（列为 C9 拓扑矩阵验证项）。
