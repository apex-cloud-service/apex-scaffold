# Maven 分组父 POM 优化方案（最终整合版）

> 状态：**第一阶段已实施；完整回归与本地镜像验证结果见第 14 节**。
> 日期：2026-11-13。
> 基线：实施前项目版本 1.3.1；根 POM 直接聚合 36 个模块。
> 本轮范围：结构重组、文档 / 脚本 / 配置说明 / 测试 / 报告 / 路径 / 图表同步，以及实际验证。
> 实施原则：**相关内容必须与最终实现完全一致；实际变化的同步修改，未变化的核对验证，历史事实保留原貌。**

## 1. 结论与推荐

**可以增加父 POM，但应以“分组治理”而非“合并模块”为目标。** 推荐新增 6 个 `packaging=pom` 的分组父模块，复用现有 `apex-services-bom`，形成 7 个顶层管理组；全部现有模块继续保留在仓库根目录。其中新增 `apex-business-parent`，只管理项目专属业务代码、业务配置和资源所在的子模块，包括外部单体迁入及后续新开发业务；其余分组提供框架能力与运行支撑，所有通用框架实现均在其他模块完成，业务模块按需复用。

推荐方案的关键决策：

1. 根 `apex-scaffold` 继续作为总父 POM 和总聚合入口，保留 Spring Boot parent、统一版本、构建档位及质量门禁。
2. 新增共享、脚手架业务组件、starter、应用装配、工具和迁入业务 6 个分组父 POM；现有 `apex-services-bom` 增加运行模块聚合职责。
3. 根 `<modules>` 从 36 个直接条目收拢为 7 个分组条目，每个叶子模块只在一个分组中登记；迁入业务分组第一阶段先建立管理入口，不凭空创建具体业务子模块。
4. 不移动源码，不修改现有模块的 `groupId`、`artifactId`、JAR 名称、Java 包名或服务拓扑。
5. 第一阶段保留 `apex-api-contract`、`apex-integration`、`apex-boot-all` 对 `apex-services-bom` 的继承；它们的**聚合归属与继承归属允许不同**，避免引入非必要的依赖版本变化。
6. 第一阶段不顺带拆全局 BOM、升级依赖、引入 `${revision}` / Flatten 或统一所有实际依赖。
7. 模块结构变更必须同步所有受影响的有效文档、脚本、配置说明、测试断言及当前验收内容；架构图、UML、时序图、数据流程图、SVG 和图源 / 导出 / 引用必须纳入检查与闭环。
8. 日志、存储、License、classpath、测试资源和部署挂载路径以真实解析基准为准，不因为聚合图多一层就机械增加路径层级。
9. 同步升级模块注册表门禁和容器 POM 收集；补齐影响标准服务 prebuilt 验收的既有产物放行遗漏。完成完整验证后，才判断是否需要进一步抽取重复配置。

**收益与限制必须分开理解：**

| 项目 | 当前 | 推荐方案完成后 |
| --- | ---: | ---: |
| 根 POM 直接 `<module>` 条目 | 36 | 7 |
| 根聚合入口以下的 Maven 模块总数 | 36 | 42 |
| 含根 POM 的全量 reactor 项目数 | 37 | 43 |
| `packaging=pom` 项目数，含根 POM | 2 | 8 |
| JAR 模块数 | 35 | 35 |
| 现有叶子模块物理目录迁移 | 无 | 无 |
| 标准发布服务 JAR 集合 | 8 个 | 仍为 8 个 |

上述目标数以**只建立 `apex-business-parent`、尚未导入具体迁入业务子模块**为基准。后续新增 N 个业务项目后，递归模块数为 `42 + N`、含根 reactor 数为 `43 + N`；JAR / POM 数量按各新模块实际 packaging 计算，不能继续硬编码基线。迁入业务服务不会未经决策自动进入现有八服务发布集合。

因此，这不是减少实际 Maven 模块数量的方案，而是降低根级管理复杂度、明确配置归属的方案。**不承诺构建提速、产物缩小或依赖自动减少。** 如果目标是减少磁盘根目录数量，需要另做物理目录迁移；本方案不包含该工作。

## 2. 已核对的现状

### 2.1 当前继承与聚合是两种结构

根据根 [pom.xml](../../../pom.xml) 和 36 个模块 POM 的实际声明：

- 根 POM：`com.apex.scaffold:apex-scaffold:1.3.1`，`packaging=pom`，继承 Spring Boot `3.5.15`。
- 根 `<modules>` 直接包含 36 个模块，目前没有嵌套分组聚合。
- 23 个模块直接继承根 POM，**其中包含 `apex-services-bom` 本身**。
- 13 个模块继承 [apex-services-bom/pom.xml](../../../apex-services-bom/pom.xml)。
- `apex-services-bom` 当前没有 `<modules>`，没有直接 `<dependencies>`，没有自己的 `<build>`；主要增加依赖管理，再从根 POM 继承构建配置。
- 根 POM 没有直接 `<dependencies>`，不会仅因继承就给每个模块注入整套运行依赖。
- 版本目前使用显式 `1.3.1` 与 `${project.version}`，并非 `${revision}` 模式。

继承 `apex-services-bom` 的 13 个模块是：

| 类型 | 实际模块 |
| --- | --- |
| 标准发布运行模块，8 个 | `apex-gateway`、`apex-system-service`、`apex-auth-service`、`apex-workflow-service`、`apex-file-service`、`apex-office-service`、`apex-integration-service`、`apex-aggregation-service` |
| 可选运行模块，2 个 | `apex-job-service`、`apex-masking-service` |
| 其他模块，3 个 | `apex-api-contract`、`apex-integration`、`apex-boot-all` |

不能根据名字把 `apex-services-bom` 视为一个只被 `scope=import` 使用的纯 BOM，也不能根据旧注释中的“五服务”推断当前模块数。

### 2.2 公共配置已经有管理基础

根 POM 已统一管理：

- Java 21、编译参数、Lombok / MapStruct 注解处理器。
- Spring Boot / Spring Cloud 等版本属性及大量第三方、内部模块依赖版本。
- Surefire 单测过滤、Failsafe 的集成测试与 E2E 测试执行。
- Spotless、PMD/p3c、JaCoCo 的 `verify` 门禁与相关管理配置。
- `basic`、`standard`、`enterprise`，以及 `remote-it`、`stress-tests` 等构建 profile。

其中 `build/plugins` 与 `pluginManagement` 的执行语义不同。比如根 POM 中 Enforcer 是管理声明，**不能仅凭出现了插件配置就认定所有模块已经执行 Enforcer**。新增 parent 时不得把所有受管理插件无差别激活。

当前 `apex-services-bom` 又管理部分内部坐标、Spring Cloud Alibaba BOM 及服务相关依赖。它与根 POM 有重叠，但“存在重叠”不等于可以在本次结构调整中直接删除：需要先比较有效模型和依赖树。

### 2.3 当前真正的管理问题

1. 根聚合清单混排共享设施、业务库、运行服务、starter、工具和组合装配，新增模块缺少清晰的登记归属。
2. 多类 JAR 直接继承根 POM，分组级约束缺少明确承载点。
3. 部分服务和库重复声明测试、插件或依赖配置，但重复项不一定等价，不能机械抽取。
4. 模块注册表与根 `<modules>` 直接集合绑定，尚不支持嵌套聚合。
5. 容器构建显式复制各模块 POM，新增父模块必须同步，否则源码构建阶段无法完整解析 reactor。

## 3. 规范依据与设计约束

### 3.1 Maven 规则

| 概念 | 职责 | 本方案应用 |
| --- | --- | --- |
| `parent` | 继承属性、依赖管理和构建配置；一个项目只能有一个直接 parent | 分组父 POM 继承根 POM，叶子模块按配置兼容性迁移 |
| `<modules>` | 聚合构建项目，模块路径相对于声明该 `<modules>` 的 POM | 分组使用 `../apex-xxx` 聚合现有同级目录 |
| `dependencyManagement` | 管理版本、scope、exclusions 等默认值，不等于实际引入依赖 | 公共版本仍集中治理，不把全套依赖注入每个叶子 |
| `dependencies` | 实际引入依赖，父级声明可能被所有子项目继承 | 新分组父 POM 不声明业务运行依赖 |
| `pluginManagement` | 提供插件版本与配置；是否执行还取决于插件启用及生命周期绑定 | 不把 Boot repackage、Shade 等打包行为全局激活 |
| `build/plugins` | 声明插件及执行，可沿继承链生效 | 已有质量门禁继续保留，叶子打包行为不变 |
| BOM import | 导入依赖管理，不替代 parent 的插件与构建配置继承 | 不用 import BOM 冒充构建父 POM |
| reactor | 按 parent、真实依赖及其他已实例化关系排序 | 聚合分组不等于业务依赖，不依赖手工排列建立依赖关系 |

`dependencyManagement`、`pluginManagement` 本身不会创建 reactor 的实际依赖排序边。聚合树可以与继承树不同；这也是本方案保留三个继承例外的基础。

官方参考：

- [Introduction to the POM](https://maven.apache.org/guides/introduction/introduction-to-the-pom.html)。
- [Introduction to the Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)。
- [Guide to Working with Multiple Modules](https://maven.apache.org/guides/mini/guide-multiple-modules.html)。
- [Maven POM Reference](https://maven.apache.org/pom.html)。

### 3.2 本仓库约束优先

本方案遵守 [AGENTS.md](../../../AGENTS.md)，特别是：

- 固定 SDKMAN JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；执行构建前在当前 shell 加载初始化脚本。
- 不回退到系统 `mvn`，不改变 `~/.m2/repository`，不把仓库放到项目 `target/`。
- 保留 DDD 与物理依赖边界；parent 管理配置不代表允许跨域依赖。
- 不调整 system 的协议层位置，也不改变 integration 自含组件形态。
- 不复制、移动或改写已经执行的 Flyway 历史迁移。
- 不混淆 Maven 构建档位、Spring 运行 profile 和 Compose profile。
- 不改变 License 边界，不分发私钥或卖方签发工具。
- 不提交或推送 Git，除非用户另行明确要求。

## 4. 推荐结构

### 4.1 七个顶层分组

下列 6 个新模块名均为**拟新增**，不是当前已经存在的模块。

| 根级分组 | 操作 | 聚合叶子数 | 职责 |
| --- | --- | ---: | --- |
| `apex-shared-parent` | 新增 | 9 | common、共享 domain、契约、五个设施能力 JAR 和兼容 JAR |
| `apex-components-parent` | 新增 | 6 | 六个脚手架业务组件库 |
| `apex-starters-parent` | 新增 | 4 | 存储、授权、任务、分布式事务 starter |
| `apex-services-bom` | 复用并增加聚合 | 10 | 八个发布运行模块及 job / masking 可选运行模块 |
| `apex-applications-parent` | 新增 | 2 | 示例应用与组合装配 |
| `apex-tools-parent` | 新增 | 4 | 授权工具、生成器、离线部署工具、构建管理工具 |
| `apex-business-parent` | 新增 | 0，后续按项目业务增加 | 只管理项目业务库及业务专属配置 / 资源；通用框架实现与运行装配由其他分组提供 |

全部分组均为 `packaging=pom`；父模块自身没有 `src/main/java`、启动类、业务资源、服务端口或可执行 JAR。业务代码与配置保存在实际 JAR 子模块，而不是父 POM 源码目录。

### 4.2 完整聚合映射

```text
apex-scaffold                         总父 POM + 总聚合入口
├── apex-shared-parent                新增，9 个叶子
│   ├── apex-common
│   ├── apex-domain
│   ├── apex-api-contract
│   ├── apex-infrastructure-persistence
│   ├── apex-infrastructure-cache
│   ├── apex-infrastructure-messaging
│   ├── apex-infrastructure-remote
│   ├── apex-infrastructure-integration
│   └── apex-infrastructure
├── apex-components-parent            新增，6 个叶子
│   ├── apex-system
│   ├── apex-auth
│   ├── apex-workflow
│   ├── apex-file
│   ├── apex-office
│   └── apex-integration
├── apex-starters-parent              新增，4 个叶子
│   ├── apex-storage-spring-boot-starter
│   ├── apex-license-spring-boot-starter
│   ├── apex-job-spring-boot-starter
│   └── apex-seata-spring-boot-starter
├── apex-services-bom                 现有模块，聚合 10 个运行模块
│   ├── apex-gateway
│   ├── apex-system-service
│   ├── apex-auth-service
│   ├── apex-workflow-service
│   ├── apex-file-service
│   ├── apex-office-service
│   ├── apex-integration-service
│   ├── apex-aggregation-service
│   ├── apex-job-service
│   └── apex-masking-service
├── apex-applications-parent          新增，2 个叶子
│   ├── apex-example
│   └── apex-boot-all
├── apex-tools-parent                 新增，4 个叶子
│   ├── apex-license-tools
│   ├── apex-generator
│   ├── apex-tools-deployment
│   └── apex-tools-build
└── apex-business-parent              新增，迁入业务管理入口；第一阶段无子模块
```

这是**Maven 聚合关系图，不是物理目录树或生产依赖图**。所有现有 `apex-xxx/` 目录不动，新分组目录也位于仓库根。

不把 `apex-infrastructure` 改成父 POM：它目前是保留旧坐标的普通兼容 JAR，依赖五个能力 JAR。将它改为 `packaging=pom` 会改变已有消费契约。

### 4.3 第一阶段继承映射

| 叶子范围 | 第一阶段直接 parent | 说明 |
| --- | --- | --- |
| shared 组除 api-contract 外的 8 个模块 | `apex-shared-parent` | 新 parent 直接继承根 POM |
| `apex-api-contract` | 保持 `apex-services-bom` | 聚合在 shared 组，保留现有有效模型来源 |
| components 组除 integration 外的 5 个模块 | `apex-components-parent` | 新 parent 直接继承根 POM |
| `apex-integration` | 保持 `apex-services-bom` | 聚合在 components 组，保留已有服务 BOM 管理 |
| 4 个 starter | `apex-starters-parent` | 新 parent 直接继承根 POM |
| 10 个运行模块 | 保持 `apex-services-bom` | 继承和聚合归属一致 |
| `apex-example` | `apex-applications-parent` | 新 parent 直接继承根 POM；Boot 插件保留在叶子 |
| `apex-boot-all` | 保持 `apex-services-bom` | 聚合在 applications 组，保留薄 JAR 行为 |
| 4 个工具 | `apex-tools-parent` | 新 parent 直接继承根 POM；Shade 等留在实际使用者 |

共调整 **22 个既有叶子的直接 parent**；现有 13 个 `apex-services-bom` 子项目的直接 parent 不变。6 个新 parent 与 `apex-services-bom` 均直接继承根 POM。后续迁入业务库直接继承 `apex-business-parent`，不复用脚手架 components parent；独立运行模块的唯一直接 parent 按第 4.5 节规则确定。

不为了消除三个例外，直接让所有库继承 `apex-services-bom`：这会把服务 BOM 的依赖管理影响扩散到原本不受其管理的库与工具。

### 4.4 路径与版本示例

以下仅为方案片段，不是本轮实施内容。

根聚合清单：

```xml
<modules>
    <module>apex-shared-parent</module>
    <module>apex-components-parent</module>
    <module>apex-starters-parent</module>
    <module>apex-services-bom</module>
    <module>apex-applications-parent</module>
    <module>apex-tools-parent</module>
    <module>apex-business-parent</module>
</modules>
```

新分组父 POM 的基本形态，以 starters 为例：

```xml
<parent>
    <groupId>com.apex.scaffold</groupId>
    <artifactId>apex-scaffold</artifactId>
    <version>1.3.1</version>
    <relativePath>../pom.xml</relativePath>
</parent>
<artifactId>apex-starters-parent</artifactId>
<packaging>pom</packaging>
<modules>
    <module>../apex-storage-spring-boot-starter</module>
    <module>../apex-license-spring-boot-starter</module>
    <module>../apex-job-spring-boot-starter</module>
    <module>../apex-seata-spring-boot-starter</module>
</modules>
```

叶子指向同级新 parent：

```xml
<parent>
    <groupId>com.apex.scaffold</groupId>
    <artifactId>apex-starters-parent</artifactId>
    <version>1.3.1</version>
    <relativePath>../apex-starters-parent/pom.xml</relativePath>
</parent>
```

新增 POM 仍须包含正常的 `project`、`modelVersion` 等完整结构。版本沿用当前版本策略，分组不独立发版。不得因目录仍在根级而保留错误的默认 `../pom.xml` parent 路径。

### 4.5 项目业务专用父模块：`apex-business-parent`

#### 定位与边界

用户明确要求：**`apex-business-parent` 主要管理项目业务模块代码；其他分组承担框架与运行支撑，所有通用框架实现均通过其他模块提供。** 从外部单体迁入的代码只是业务来源之一，后续新开发的项目业务也归属该分组。

- `apex-components-parent` 管理现有六个脚手架能力库；它们在自身领域边界内向项目业务提供系统、认证、工作流、文件、办公、集成能力，不迁移到 business 组。
- `apex-business-parent` 只聚合项目业务库，例如订单、客户、库存等；这些名称只是示例，不代表当前已有模块。
- 父 POM 只包含继承、聚合、必要的依赖 / 插件管理约束；实际业务模型、规则、用例、领域端口、业务持久化适配、业务配置及测试保存在业务 JAR 子模块。
- 共享响应与异常、调度器、持久化底座、租户拦截器、缓存客户端、消息基础设施、HTTP 客户端、JWT 验签、日志框架、存储 SPI、License、XXL / Seata 等通用实现必须由相应共享模块、starter、能力库或运行模块提供，business 组只能复用，不能复制或重写一套。
- 独立服务启动、安全装配、通用运行配置、Logback、部署编排不聚合到 business 组；分别由 services、applications、tools 等支撑分组承担。业务接口和业务资源按其实际归属放置，不把“框架支撑分离”误解为所有业务适配也都必须移到共享模块。
- 第一阶段先新增空的 parent 管理入口，省略 `<modules>`，不制造不存在的业务模块引用、不导入未知单体源码、不新增运行服务或数据库。
- 具体项目、领域边界、业务模块清单、运行形态及数据迁移范围在取得原项目后形成清单，另行批准执行；本轮只补方案。

#### 业务与框架职责分配

| 内容 | 归属 | business 组的使用方式 |
| --- | --- | --- |
| 项目专属实体、规则、流程编排、领域端口、业务配置 | business 业务库 | 直接实现与测试，不上移为框架能力 |
| 业务表 Mapper / PO、仓储实现、业务事件映射、领域相关外部适配 | business 业务库的 infrastructure 层 | 实现本域端口，复用框架底座；不实现通用连接池、拦截器或 SDK 管理 |
| 共享工具、响应、异常、基础端口与调度器 | shared 组 | 按需依赖，不复制源码 |
| 数据源、通用持久化、缓存、消息、远程调用设施 | shared 组中的对应能力模块 | 通过现有契约或依赖复用，不将整套设施注入每个业务模块 |
| 系统管理、认证、工作流、文件、办公、对外集成能力 | components 组中的所属能力库 | 按真实边界使用稳定 API / 端口；跨独立服务使用契约与受控调用 |
| 存储、授权、任务、分布式事务自动装配 | starters 组 | 按需接入现有 starter，不在 business 重写 |
| 独立服务启动、安全链、框架装配、运行日志、业务 Router / Handler | services 组的运行模块 | 定向装配 business 库；业务协议调用业务用例，通用机制复用框架 |
| 组合应用装配 | applications 组 | 显式选择业务库，不自动装配全部项目业务 |
| 构建、生成、发布、部署与离线运维工具 | tools 组及现有 scripts | 不在 business 复制编排实现 |

**“所有框架实现通过其他模块提供”不等于业务库不能有 infrastructure 层。** 业务仓储 / Mapper 属于领域专属实现，应留在本业务库；数据源、MyBatis 插件、租户隔离底座、缓存和锁等通用框架实现留在支撑模块。新通用能力若现有框架不具备，应在合适的支撑模块中设计实现，再由业务依赖，不在 business 库隐式孵化第二套框架。

#### 建议物理布局

为维持本方案现有脚本 / 注册表 `root/artifactId` 约定，新增业务库和运行模块同样放在仓库根级，不移动任何已有目录：

```text
仓库根/
├── apex-business-parent/
│   └── pom.xml                         packaging=pom，不存放业务源码
├── apex-business-<域>/                  后续实际新增的业务 JAR
│   ├── pom.xml                         parent=apex-business-parent
│   └── src/
│       ├── main/
│       │   ├── java/<独立业务包>/
│       │   │   ├── domain/
│       │   │   ├── application/
│       │   │   └── infrastructure/
│       │   └── resources/              业务资源、Mapper、按归属放置的 i18n 等
│       └── test/                       业务测试、测试资源
└── apex-business-<域>-service/          仅选择新增独立服务时创建
    ├── pom.xml                         parent=apex-services-bom
    └── src/
        ├── main/
        │   ├── java/<独立启动与协议包>/  启动、安全、Router + Handler
        │   └── resources/              application/bootstrap/logback、独立域迁移
        └── test/                       接口、启动、权限、迁移与日志验证
```

这是**未来布局示例，不是第一阶段承诺创建的模块**。业务库依赖 common / domain / 所需设施或稳定 API；共享模块与现有脚手架业务库不得反向依赖迁入业务。避免多个原项目沿用相同根包造成组件、Mapper 或配置扫描冲突。

业务库聚合到 `apex-business-parent`，采用 `../apex-business-<域>`，直接继承 business parent；若创建独立运行模块，归入 `apex-services-bom` 的聚合与继承范围，**不在 business 组重复登记**。同一模块只聚合一次、只声明一个直接 parent，不能把 import BOM 当成完整构建继承。

如果只需业务库装配到指定现有应用，不创建独立 service；在选定应用显式依赖、定向扫描及配置导入，不能让所有服务或 `apex-boot-all` 自动装配项目业务。HTTP 协议层优先放运行模块，自含组件例外须另行裁决。运行模块虽然包含调用业务用例的 Router / Handler，其职责仍是协议与装配，不承载领域规则或复制框架实现。

第一阶段 services 组仍是现有 10 个运行模块、八个标准发布服务。后续增加业务运行模块时，services 组的实际子模块数量、路由、端口、镜像及发布范围同步调整；仍不得在 business 组聚合该运行模块。

#### 新 parent 的基础 POM 示例

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex.scaffold</groupId>
        <artifactId>apex-scaffold</artifactId>
        <version>1.3.1</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>apex-business-parent</artifactId>
    <packaging>pom</packaging>
    <name>Apex Project Business Parent</name>
    <description>项目业务库的聚合与构建管理入口，通用框架实现由其他模块提供</description>
</project>
```

后续业务库的 parent 使用 `../apex-business-parent/pom.xml`；没有实际业务子模块时，不声明示例 `<module>`。parent 登记为分组父 POM、`deliverable=none`，无端口 / mainClass / repackage；它不是待运行的业务应用。

#### 配置与资源迁移归属

| 原单体内容 | 迁入承载位置 | 要求 |
| --- | --- | --- |
| 业务实体、用例、Repository、Mapper / PO | 所属业务 JAR 的 DDD 层 | 分离端口 / 实现；禁止复制为共享领域或反向耦合脚手架业务 |
| application / bootstrap / 数据源 / 安全装配 | services / applications 组的指定运行模块 | 复用现有框架实现；不把原单体通用配置整份放入 business 库 classpath，不覆盖脚手架配置 |
| 原业务配置键 | 业务库可使用 `business.<域>.*` 等独立命名空间；原键兼容按清单决定 | 使用显式 `@ConfigurationProperties` / 按需导入，检查冲突、默认值及生产注入 |
| Logback、日志文件 / 轮转 | 运行模块及部署注入 | 不将旧 logback 放业务库使依赖者自动选中；重新核对 cwd、LOG_PATH、挂载与采集 |
| Mapper XML、模板、规则、i18n | 业务库专属资源路径 | 定向加载、命名隔离，不复用造成覆盖的通用资源名 |
| Flyway 与数据库脚本 | 新独立服务域默认放运行模块；装配现有域时按实际数据所有权归属 | 先盘点原 Flyway 历史 / 表与版本；既有库追加迁移，不复制 V1 覆盖已执行版本；MySQL / PostgreSQL 配对 |
| 测试、测试数据和模拟配置 | 各业务 / 运行模块 `src/test` | 隔离测试数据、容器和测试资源，不混入生产 profile |
| 文件存储 / License / 证书等路径 | 业务配置及运行部署 | 分清 classpath / 文件路径，配置注入；不提交真实凭据、私钥或环境文件 |

若最终选择业务自含组件形态，将资源与迁移放库内需单独明确自动装配、接口开关、扫描和迁移归属，不能默认照搬 integration 的所有行为。

#### 从单体迁入时的最小门禁

1. 盘点原项目模块 / 领域、框架、Servlet、同步 I/O、事务、数据权限、配置键、文件路径及数据库历史，划定业务与框架代码；只把项目业务迁入 business 库，原通用实现映射到现有支撑能力或在所属支撑模块补足，不整份复制原 parent POM。
2. 对 Spring MVC / Servlet / `javax.*` 等不兼容部分制定适配；新增 HTTP 采用 WebFlux Router + Handler，阻塞调用进入对应 `BlockingIoSchedulers`，JDBC 事务覆盖完整同步事务。
3. 保留并对齐接口、序列化、业务规则、权限、租户作用域、i18n、事件与失败路径；不能以“代码已放入模块”为迁移完成。增加业务 / 框架边界检查，验证 business 库没有复制通用安全、数据源、缓存、日志或调度框架实现，运行模块仅在支撑分组登记，框架共享模块不反向依赖项目业务。
4. 配置显式导入、扫描范围和资源路径可控，检测重复 Bean、Mapper、同名 application / logback / migration 及旧依赖冲突。
5. 数据迁移先在隔离环境验证兼容、回滚与历史链，不授权直接迁移生产数据库；不复制或泄露原项目真实密钥。
6. 对迁入业务的日志 / 文件路径重新核对，不能沿用“本仓原叶子没移动，因此路径都不变”的结论；原单体的 cwd、运行装配和容器目录可能不同。
7. 每新增模块同步 parent modules、注册表、递归 / COPY 清单、测试、有效文档和图表；默认 `deliverable=none` 仅适用库 / parent，运行 JAR 根据交付决策选择 optional 或 service-jar。
8. 新运行服务的端口、镜像、路由、监控、日志挂载与发布集合另行登记；不能使用当前八服务计数替代迁入服务的验收。

具体迁入数量尚未确定，第一阶段 42 / 43 的结构基线不包含这些后续业务子模块。本方案默认只建设管理入口；实际源码与配置迁移是扩展实施阶段，需要独立模块清单和授权。

## 5. 配置管理边界

### 5.1 第一阶段：结构调整与模型等价

第一阶段的新增 parent 尽量薄，只承载身份、描述、继承与聚合；**不新增实际依赖或新的插件执行**。

| 层级 | 应保留或承担 | 第一阶段禁止 |
| --- | --- | --- |
| 根 POM | 统一版本、编译、质量门禁、构建档位、已有依赖管理 | 为减少行数把配置复制到每个分组；导入自身后代 BOM |
| shared / components parent | 分组入口，后续同类配置治理位置 | 给所有子项目自动注入全部 infrastructure、数据库、Redis 或安全依赖 |
| starters parent | starter 分组与普通 JAR 管理位置 | Boot repackage、业务库依赖、全套 starter 相互依赖 |
| services-bom | 保留现有依赖管理，增加运行模块聚合 | 自动向网关、聚合服务、job、masking 注入 JDBC、Flyway、Seata、XXL |
| applications parent | 示例与装配分组 | 自动给 boot-all 激活 repackage |
| tools parent | 工具分组 | 给所有工具注入 Spring WebFlux 或统一激活 Shade |
| business parent | 只管理项目业务库，第一阶段无业务子模块；代码 / 配置归属见第 4.5 节 | 通用框架实现、运行模块聚合、父 POM 内业务源码、原单体运行配置复制及全业务自动装配 |
| 叶子模块 | 实际 dependencies、业务 profile、特有测试和打包行为 | 为“看起来统一”而删除不同的 exclusions、scope、optional、classifier |

保持原有插件 execution ID 与合并语义，不重命名执行块导致重复执行；保留 `combine.self`、`combine.children` 及模块级覆盖。

### 5.2 可选第二阶段：重复配置治理

在第一阶段验证通过之后，才考虑以下工作；**不包含在默认首轮执行范围内**：

- 提取同类且经有效模型证明等价的插件配置到对应 parent 的 `pluginManagement`。
- 消除根 POM 与 `apex-services-bom` 中真正等价的重复依赖管理。
- 在完整依赖管理基线对齐后，将三个继承例外迁移到各自分组 parent。
- 若外部消费者确实需要独立导入完整依赖矩阵，再设计不参与构建配置继承的纯平台 BOM。

此阶段需要单独列出差异和验证范围，不与目录分组捆绑执行。没有需要统一的配置时，保留薄 parent 比制造新的抽象更好。

### 5.3 必须保持的打包差异

- 八个标准运行模块仍产出可执行服务 JAR。
- `apex-job-service`、`apex-masking-service`、`apex-example` 仍按现有 optional 口径管理。
- `apex-boot-all` 仍为普通薄 JAR，不变更运行形态。
- 业务库、共享库、starter 不进入 Boot `BOOT-INF/classes`，不新增 repackage。
- gateway 的 `test-support` 等辅助 classifier 保留，发布 JAR 唯一性过滤继续有效。
- `apex-tools-deployment` 仍用 Shade 产出 `target/local-deployment.jar`，保留入口及资源合并。
- 新 parent 无可执行产物；`deliverable=none` 不表示“没有 Maven POM 构件”，parent 仍需供依赖模型解析。

## 6. 必须同步的构建与交付配套

### 6.1 模块注册表：继续扁平登记全部模块

实际注册表位置是 [apex-tools-build/src/main/resources/build-manifest.yaml](../../../apex-tools-build/src/main/resources/build-manifest.yaml)，不是仓库根下的同名文件。

推荐保留扁平结构及 `schema: 1`：

- 新增 6 个 parent 条目，分类为分组父 POM，`deliverable: none`，不登记端口或 mainClass。
- 现有 36 个条目保留，第一阶段登记总数变为 42，不含总根 POM；business parent 即使无子模块也必须登记。后续新增业务项目逐项登记并依据递归清单更新数量。
- 维持每条记录的 `  - artifactId:` 与字段四空格缩进，不改成嵌套 YAML。
- 更新头注：与“根直接 modules 一致”改为与“根递归聚合模块集合一致”。
- 不强制增加 `path` / `parent` 字段：本阶段仍是 `artifactId=根级目录名`，已有脚本可以继续按此定位产物。
- 发布服务仍是 `service-jar` 的 8 个条目；父 POM 不加入发布服务数组。

### 6.2 注册表测试：递归读聚合树，不扩大到继承树

[BuildManifestSyncTest.java](../../../apex-tools-build/src/test/java/com/apex/scaffold/tools/build/BuildManifestSyncTest.java) 的 `manifestModulesMatchRootPomDeclarations()` 当前仅读取根文件中的 `module` 文本并比较注册表 key。收拢根清单后必须修改，不能仅改断言预期为 7 项。

建议实现：

1. 从根 POM 出发，只读取该 POM 顶层直接 `<modules>` 的声明；本方案不在 profile 中声明 modules。
2. 相对于当前 POM 的目录解析路径，规整到真实路径，再读取目标 POM 的实际 artifactId。
3. 对目标项目继续递归读取其 `<modules>`，收集全部后代项目，排除起始总根 POM。
4. 对规范化路径与 GAV 检测重复和环，发现重复登记直接失败，而不是用 Set 静默去重。
5. 拒绝不存在的模块、指向仓库之外的路径及 artifactId / 根级目录命名不一致。
6. 比较第一阶段 42 个项目与注册表全量集合，包含空 business parent；后续依据实际递归清单比较，不在测试中永久锁死 42。
7. 新 parent 额外校验 `packaging=pom`、无直接业务依赖、无启动类或可执行打包插件。

递归的是**聚合边**，不是 `<parent>` 边。否则三个继承例外会造成错误回溯或重复收集。

继续保持现有交付口径、显式 mainClass 与 `release.sh SERVICES` 的双向校验。现有 `hasPlugin()` 连 `pluginManagement` 声明也计入；第一阶段因此不在新 parent 中声明 Boot / Shade。若第二阶段统一管理此类插件，必须同时升级“受管理”与“实际启用”的判定，不能放宽门禁掩盖误打包。

需要覆盖的新增测试场景：同级 `../` 模块、两层聚合、重复模块、聚合环、越界路径、缺失 POM、注册表缺项 / 多项、三个不同继承归属的叶子，以及 parent 被误设为可执行交付物。

### 6.3 编排入口和按模块构建

实际入口是 [apex-tools-build/bin/apex.sh](../../../apex-tools-build/bin/apex.sh)。保留其相对根目录计算与现有叶子构建参数。

- `build --module`、`test --module`、`package` 仍从总根执行 `-pl <叶子列表> -am`。
- 原有根级叶子路径不变，现有 `-pl apex-system-service -am` 等命令继续作为兼容目标。
- 不把 `-pl apex-services-bom -am` 宣称为“构建该组全部服务”。Maven 3 的聚合关系不等于所选 parent 的下游依赖；`-am` 主要补齐上游所需项目。
- 本阶段不新增 `--group` CLI。需要整组构建时，从根明确列出该组叶子并带 `-am`；以后可由聚合清单推导列表，但不另维护一份硬编码组清单。
- 不把 `-f 分组/pom.xml` 当作跨组依赖未安装时也一定可用的替代方案；推荐从总根构建。现有工具链版本对应的 `-pl` / `-am` 行为在实施时验证，不假定不同 Maven 版本语义完全一致。
- `status` 当前已将 `deliverable=none` 显示为“不产出可执行 JAR”。新增 parent 正确登记后应沿用该行为，只核对说明和显示结果，不为分组而强制重写逻辑。
- `release.sh SERVICES`、叶子 JAR 路径、根版本读取、脚本根目录计算及现有 CI `hashFiles('**/pom.xml')` 不因本次分组而改变；检查其行为保持一致，不做无依据的重构。
- 脚本、测试和配置注释中受影响的“直接根聚合”“直接根 parent”等说明必须改为新的事实；不把历史注释中的退役模块名当作当前路径。

### 6.4 容器 POM 收集与工具链

[Containerfile](../../../Containerfile) 在依赖预下载之前逐个 `COPY` 模块 POM。需要：

- 增加 6 个新 parent 的 POM COPY，包含无业务子模块的 `apex-business-parent`。
- 补齐当前未显式复制的 `apex-tools-build/pom.xml` 和 `apex-aggregation-service/pom.xml`；两者已经在根 reactor 中，预下载阶段也必须可见。
- 保持叶子路径和构建上下文不变；核对 `.containerignore` 未屏蔽新 parent。
- 为 POM COPY 清单增加与递归 reactor 的一致性检查，避免后续新增模块再次漏登记。

另一个已确认的现状是 Containerfile 的 `source` 分支直接调用系统 `mvn`，与本仓库固定 SDKMAN / mvnd 约束不一致。**不能把该分支描述为已经符合规范。** 第一阶段采用以下明确处理：

1. 构建验证和交付演练统一在宿主机加载固定环境后使用 mvnd。
2. 镜像验证采用现有 `prebuilt` 模式，消费宿主机验证后的产物，不运行 Containerfile 的 `mvn` 分支。
3. Containerfile 的 source 工具链整改单独列为可选后续事项；未整改之前，不将 source 模式记为验收通过。

前置显式 POM COPY 是源码模式在 `COPY . .` 之前解析 reactor 的必要条件。prebuilt 模式仍执行无条件 COPY，但不执行该阶段的 Maven 解析，随后完整 COPY 会带入未被忽略的 POM；不能将显式清单遗漏误述为 prebuilt 必然发生 Maven 解析失败。两种模式统一维护完整 POM 清单，避免遗漏。

另有已确认的既有验收阻断：[.containerignore](../../../.containerignore) 默认排除 `**/target`，现有产物放行清单缺少 `apex-aggregation-service` 和 `apex-example`。

- 默认实施范围补齐 aggregation 的目标 JAR 放行，因为它属于八个标准服务的 prebuilt 验收范围；与现有服务规则保持一致，不放行无关测试产物或敏感文件。
- 若演练 example 镜像，则同时补齐 example；只验收八个标准服务时，不强行扩大到示例镜像。
- 此遗漏不是新增 parent 引起的路径变化，但必须与分组适配分开标注、处置，不能让已知阻断残留却宣称镜像验收通过。
- 新 parent 不产服务 JAR，无需增加 target/JAR 放行；现有规则不排除其 POM，不需要仅因增加 parent 修改忽略配置。

`.gitignore`、`.zcodeignore` 已有通用 target 忽略规则，第一阶段只验证继续有效，不复制一组新的模块专属规则。

### 6.5 发布、外部消费与许可

[scripts/release/release.sh](../../../scripts/release/release.sh) 的实际 `SERVICES` 是八个标准运行模块，包含聚合服务。旧注释或测试说明中的“七个”不作为依据。

- 该服务数组、JAR 目录、版本读取和压缩包命名保持不变；新 parent 不加入 `SERVICES`。
- 部署工具继续按现有实现复制到发行包 `tools/local-deployment.jar`；不混淆“不是业务运行模块”与“完全不随包交付”。
- `apex-tools-build`、授权签发工具与 parent 本身不因分组而进入可执行交付清单。
- 仓库内 `install` 应包含新 parent POM，不能只安装 JAR 而留下缺失的 parent 链。
- 未来发布 Maven 构件时，必须同时发布根与新增 parent POM；只发布子 JAR/POM 会使外部消费者无法解析新的 parent。此处是发布要求，**不授权本轮向远程仓库 deploy**。
- 如未来生成剥离 `apex-license-tools` 的客户源码包，须同步处理 `apex-tools-parent` 中该模块的聚合声明，否则剥离后 reactor 会引用不存在的路径。不得为了保证构建，把卖方工具重新塞回客户源码包。

## 7. 全面同步与路径等价要求

### 7.1 “完全对齐”的强制定义

**不能只改 POM，也不能要求所有文件都改。** 全面对齐是：每份当前有效材料的事实、命令、行为和真实路径与最终实现一致，且有可核对的检查结果。

采用三种处理方式：

- **必须修改**：模块归属、parent、聚合、数量、模块遍历、构建 POM 清单等实际变化的内容。
- **必须核对并验证保持不变**：未变的业务调用、运行拓扑、服务配置、日志 / 存储路径、测试资源和部署挂载。
- **保留历史事实**：旧报告、日报、历史验收和变更记录不追溯改写；历史材料被用于当前指引时，需明确其历史属性，并让当前入口指向有效说明。

Maven 聚合树、parent 继承树、物理目录树、生产依赖图、运行部署图是五种不同关系。它们必须互相一致，但不要求树形结构相同。禁止为了“外观对齐”把路径改成并不存在的 `apex-components-parent/apex-system/`。

### 7.2 全面同步矩阵

| 对象 | 必须修改的内容 | 必须保持并验证的内容 | 验收方式 |
| --- | --- | --- | --- |
| POM | 新 parent、22 个叶子 parent / relativePath、七组 modules | GAV、实际 dependencies、插件执行、档位语义 | 递归清单、三档有效模型与完整构建 |
| 注册表 | 新增六条、分类、数量、递归口径及空 business parent | 扁平格式、八个发布服务、端口、mainClass | Java 门禁及 shell 消费结果 |
| 有效文档 | 模块分类、聚合 / 继承图、数量、维护步骤、受影响命令与注释 | 未变化的物理路径、业务边界及运行配置 | 事实逐条核对、链接与锚点检查 |
| 构建 / 发布脚本 | POM 收集配套；受影响说明与模块发现逻辑 | 根目录、版本读取、叶子 -pl、JAR 查找、SERVICES | 语法检查、目标选择与产物演练 |
| 配置文件 | 有关旧模块管理的注释若失效则更新；不制造新的运行键 | application/bootstrap、环境变量、classpath、端口、密钥契约 | 配置及 JAR 内资源对比 |
| 测试代码 | 递归模块检查及新 parent 约束，受影响断言 / 提示 | 业务测试、兄弟目录、workingDirectory、日志属性优先级 | 结构负面用例、现有测试与完整 verify |
| 当前测试汇总 / 验收材料 | 受影响模块范围、结构说明、执行命令、实际新结果 | 结果真实性，失败 / 错误 / 跳过分开 | 当前执行记录与报告相互核对 |
| 历史报告 | 不追溯改写当时结构或结果 | 原日期、数量和证据 | 与当前材料区分，不作为新结构已通过的证明 |
| 日志 / 存储 / License 路径 | 只有解析基准实际变化或发现相关缺陷才改 | 路径值、覆盖优先级、权限、挂载与采集链路 | 有效模型、临时目录探针、部署检查 |
| 架构 / UML / 时序 / 数据流 / SVG | 图中受影响的节点、边、标签、图注、链接；图源与导出同步 | 未变的业务、数据与部署关系 | 语义核对、渲染检查及引用检查 |
| CI / 忽略规则 | 补齐目标验收所需 aggregation 产物放行 | POM 缓存通配符、通用 target 忽略、既有流水线工作目录 | 清单检查、构建上下文与镜像验收 |

**覆盖检查是强制的，文件修改取决于真实影响。** 禁止用“路径没搬”跳过检查，也禁止为了证明检查过而制造无效 diff。

### 7.3 必须同步的当前文档入口

至少检查以下现有有效文档及其图表引用，实施时以实际引用链补全清单：

| 文档 | 同步重点 |
| --- | --- |
| [README](../../../README.md) | 根直接七组、第一阶段递归 42 模块及空 business 管理入口、物理目录与运行服务不变 |
| [AGENTS.md](../../../AGENTS.md) | 分组管理、继承关系、模块注册与验证约束；不改变 DDD / 安全规则 |
| [架构总览](../../02-architecture/architecture-overview.md) | 当前模块基座、继承 / 聚合关系及图文一致性 |
| [开发规范](../../04-development/development-standards.md) | 受影响的模块创建、parent、构建与验证步骤 |
| [构建工具使用说明](../../04-development/build-tools-usage.md) | 模块登记、递归门禁、计数口径、保持从总根构建 |
| [构建管理模块说明](../../../apex-tools-build/README.md) | 注册表与递归 reactor 一致；不再要求根直接 modules 与全部条目相等 |
| [交付范围](../delivery/delivery-scope.md) | 当前模块分类与构建 parent，不改变实际交付的八服务和部署工具 |
| [配置说明](../../05-operations/configuration.md) | 核对路径来源和现有配置，不将分组名称当运行配置 |
| [SVG 维护指南](../../04-development/svg-maintenance-guide.md) 与 [图表目录](../../diagrams/README.md) | 相关图源、生成与引用规范，更新失效的结构说明 |

模块级 README、构建脚本头注、POM 注释、HTML 图表说明和当前验收清单亦属于扫描范围。不是只更新上述文件即可自动证明全仓一致；通过引用与关键词查找找齐相关消费者，再逐项判断。

当前文档只能在对应结构实施时改为“已完成”。本方案保留基线与目标对照，避免在实施前宣称项目已是七组。历史材料中的 36、旧图编号、当时测试数量不得全仓替换。

### 7.4 路径解析基准清单

| 路径 / 属性 | 真实解析基准 | 第一阶段要求 |
| --- | --- | --- |
| `<modules><module>` | 声明该 module 的 POM 所在目录 | 分组正确使用 `../叶子目录` |
| parent `relativePath` | 叶子 POM 所在目录 | 指向实际 sibling parent |
| `${project.basedir}` | 当前构建项目物理目录 | 叶子位置不变，不能当 parent 目录 |
| `${project.build.directory}` | 当前项目有效构建配置 | 保留各叶子 target 与测试产物位置 |
| `${maven.multiModuleProjectDirectory}` | Maven/mvnd 启动器和 `.mvn` 根定位 | 总根调用保持；不是当前 modules 声明目录 |
| `${project.parent.basedir}` | 不应假设为仓库根；本仓现有相关配置未使用 | 不新增以直接 parent 推导仓库根的依赖 |
| JVM `user.dir` / 相对文件路径 | Java 或测试进程实际工作目录 | 不改 cd / workingDirectory；IDE 与直接 java -jar 单独判断 |
| `classpath:` / `classpath*:` | 类加载器中的资源位置 | 不加分组目录或 `../` 前缀 |
| Compose 相对挂载 | Compose 文件及项目目录基准 | 不移动 Compose，不改变 project-directory |
| 容器绝对路径 | 容器文件系统与挂载目标 | 保留 `/app/logs`、存储目录和权限 |
| Markdown / HTML / SVG 引用 | 文档位置、浏览器基准或图中实际链接规则 | 校验真实路径、大小写、锚点及跨文件引用 |

改用 `-f 分组/pom.xml` 不代表所有根属性必然变化，也不代表跨组依赖必然可见；若新增这种用法，要验证当前工具链的 reactor 和 `.mvn` 定位，不能继承“总根构建”的等价结论。

### 7.5 日志、存储、配置与资源的明确处置

#### Maven 测试日志

根 POM Surefire / Failsafe 使用：

```text
-Dapex.log.dir=${project.basedir}/../logs
-Dcsp.sentinel.log.dir=${project.build.directory}/sentinel-logs
```

现有叶子保持 `仓库根/apex-xxx`，所以业务测试日志仍位于根 `logs`，Sentinel 测试原生日志仍位于叶子 target。**不改成 `../../logs`，不写入分组 parent 目录。** 比较继承后的 argLine、execution 与系统属性，不能只看源 POM 文本。

system 测试 [logback-test.xml](../../../apex-system/src/test/resources/logback-test.xml) 使用 `apex.log.dir` 或 `user.dir/logs` 下的 `test`，与生产配置的 `LOG_PATH` 优先链不同，应分别验证。未注入属性的 IDE 测试行为不能当作 Maven fork 的默认结果。

#### 运行日志与采集

[app.sh](../../../scripts/deploy/service/app.sh) 从脚本位置定位仓库根并切换目录，shell `LOG_DIR` 取 `APEX_LOG_DIR`、`LOG_PATH` 或根 `logs/<service>`，再导出 `LOG_PATH`。`LOG_DIR` 不是 Java/Logback 自动识别的通用变量，PID / stdout 等也有自己的路径，不能混为一项。

system [LogDirectoryConfigurer](../../../apex-system/src/main/java/com/apex/scaffold/LogDirectoryConfigurer.java) 通过物理 `pom.xml` 与 `apex-system/pom.xml` 向上定位根，不读取聚合或 parent 链。system 与其他现有生产 Logback 的目录来源包含 `LOG_PATH`、`apex.log.dir`、`APEX_LOG_DIR`、`user.dir/logs`，保留各自实际优先级；不将全部来源概括为 Maven 自动注入。

[Compose](../../../podman-compose.yml)、[基础设施 Compose](../../../podman-compose.infra.yml)、[Filebeat](../../../config/monitoring/filebeat/filebeat.yml) 与 [Alloy](../../../config/monitoring/alloy-config.alloy) 的链路保持：

```text
服务容器 /app/logs
  → 宿主 logs/<原服务>
  → 采集容器 /var/log/apex-scaffold/**/*-json.log
```

七个管理组不产生七个运行日志目录，也不增加七个监控目标。GC、heapdump、SkyWalking、错误 / SQL / JSON 等日志位置、写权限与轮转策略均纳入核对；原有模块不存在日志路径变更时保留原值。此保持不变结论不适用于后续外部单体迁入业务，迁入项目须按第 4.5 节重新核对原 cwd、配置装配、路径与采集链路。

#### application、bootstrap 与 classpath

- `spring.application.name`、Nacos 标识、路由、端口、数据库归属、密钥键名不改成 parent 名称。
- 现有 application YAML 的日志块主要管理级别 / 格式；不能把 `logging.pattern.file` 当文件路径。本次扫描未发现需要因 parent 改写的 `logging.file.path` 目录声明，测试中的显式设置独立判断。
- Flyway、Mapper、basic schema、i18n、License classpath 资源及 `application*.yml` / `bootstrap.yml` / `logback*.xml` 保留原位置。
- [system POM](../../../apex-system/pom.xml) 对 `apex-build.properties` 的资源过滤保留；继续核对 `@build.profile.id@` 和 `@project.version@` 的结果，不扩大到所有 YAML/XML。
- parent 不承载运行资源，不把叶子 `src/main/resources` 迁入 parent，不因发现同名配置而顺带删除。

#### 存储、License 与其他文件

- `.env.example` 中相对 `STORAGE_LOCAL_BASE_PATH` 依赖 JVM cwd；配置和进程目录未变就不改值，容器仍以明确注入 / 挂载为准。
- License 分清 classpath、绝对文件与普通相对文件；公钥、证书、环境文件、脚本输出等按实际解析和挂载核对，不移动或重新生成密钥。
- PMD 相对排除文件等插件参数不能想当然认为相对 parent；有效模型与实际插件执行确认解析结果，不把配置复制到六个 parent。
- 工具测试的 `apex.root=${maven.multiModuleProjectDirectory}`、测试的 `../兄弟模块` 和叶子相对资源路径保留，但要检查 Surefire / Failsafe 的 `systemPropertyVariables`、argLine 与 workingDirectory 合并结果，确认属性实际传入 fork 和 cwd；不存在自动贯通到运行 JVM 的保证。

### 7.6 测试代码与报告同步

必须增加递归聚合、唯一归属、缺失 / 环 / 越界等结构测试，以及新增 parent 不引入运行依赖或可执行打包行为的检查。已有测试若直接断言根 modules、直接 parent 或固定 reactor 数量，应同步其断言和说明；不能通过删除或弱化业务测试换取通过。

现有物理 sibling 路径、资源路径和业务架构检查若仍正确，保持原值。当前模块清单、测试结果、覆盖率汇总的消费者都要核对，不能用固定反应堆计数推导测试数。

报告按性质处理：

1. 当前有效验收 / 交付材料的结构和范围同步新实现；新测试结果只能来自实际执行。
2. 历史报告和变更记录保留原日期、模块数、结果，不重算或将旧通过状态改称新结构通过。
3. 调整后记录命令、工具链、档位、reactor 范围、Failures / Errors / Skipped、报告位置与运行条件；Surefire / Failsafe / stress / remote-it 分开说明。
4. 有新增 parent 不代表有新增业务测试；POM-only 项目可能无报告，不能把“无测试”当服务失败，也不能把它记作某业务集成测试已通过。
5. 中间模型、日志、图渲染与构建证据放 `/tmp` 或原有受忽略产物位置；仅维护现有当前汇总和本方案必要的实施结果，不默认新建大量报告文件，不引用未入库证据为 Markdown 链接。

### 7.7 已发现的现状口径漂移及处置边界

图表盘点还发现以下与当前项目口径有关的既存不一致，实施时必须处理当前有效说明，而不能只新增六个 parent 后保留旧数量：

| 文件 / 材料 | 已确认现状 | 本次处置 |
| --- | --- | --- |
| [图表 README](../../diagrams/README.md) | 部分统计仍写 Maven 模块 31 | 更新当前统计为清楚区分七组、第一阶段 42 模块、43 reactor 项目及后续迁入业务的动态数量 |
| `95_module_dependency_graph.mmd` / `.svg` | 标称 35 模块，相关运行模块清单未包含 aggregation | 核对生产直接依赖，补齐当前应表达的模块；parent 用图例 / 独立继承图表示，不伪造生产依赖边 |
| [数据流架构说明](../../architecture/data-flow-architecture.md) | 仍有 reactor 共 30 模块的当前说明 | 更新模块数量和归属说明；业务流程本身按实际实现核对 |
| [产品解决方案 HTML](../../07-commercial/sales/Apex-Scaffold-产品解决方案文档.html) | 仍有 30 模块及未列 integration / aggregation 的现状图 | 同步有效产品说明及独立内嵌 Mermaid，不当作某 SVG 的自动导出源 |
| [构建使用说明](../../04-development/build-tools-usage.md) | 仍有旧 reactor 数量提示 | 改为有效计数与执行范围，不能据 grep 数量推导测试结果 |
| [交付交接清单](../delivery/handover-checklist.md) | 仍写六个服务 JAR、五个业务服务、五个库、五个域 | 更新为八个服务集合、六个业务域与实际数据库归属 |
| [验收方案](../delivery/acceptance-plan.md) | B-01 写六个服务 JAR，D-01 写五个库 | 更新为八个服务 JAR 与各业务域数据库口径 |
| [部署架构说明](../../architecture/deployment-architecture.md) | 演进说明写五业务服务 | 更新为网关 + 六业务服务 + 聚合服务拓扑 |
| [部署守护脚本](../../../scripts/deploy/service/app.sh) 与 [start-local.sh](../../../scripts/deploy/service/start-local.sh) | 服务清单注释缺 aggregation | 注释补齐；APEX_SERVICE_MODULE 映射本已支持 8098 |
| [拓扑装配脚本](../../../scripts/deploy/assemble-topology.sh) | split 拓扑仅构建六个业务服务，缺网关与聚合服务 | 补齐为网关 + 六业务服务 + 聚合服务共 8 个 |
| [逻辑架构详解](../../architecture/01-logical-architecture-detail.md) | 演进说明写全仓 31 个 Maven 模块 | 更新为七组 / 42 / 43 并指向 128 / 129 图 |
| `.github/workflows/ci-cd.yml` 的 GitHub Release 产物清单 | 只上传 7 个 JAR（缺 integration-service、aggregation-service） | **既有发布流水线漂移，非本次分组引入**；按方案范围不擅自改发布流水线，单独确认后处理 |

这些漂移属于当前对齐闭环中的既有问题，应与本次新增 parent 导致的变更分开记录；不得利用发现问题的机会改动无关业务代码、追溯重写历史报告或声称全部图已审计通过。

## 8. 架构图、UML、时序、数据流程与 SVG 同步

### 8.1 按表达关系判断影响

| 图类型 | 必须对齐的内容 | 第一阶段通常保持的内容 |
| --- | --- | --- |
| Maven 聚合 / 继承图 | 七组、全部模块归属、22 个 parent 改动、三个继承例外、空 business parent 与后续迁入规则、数量与图例 | 无 |
| 物理目录图 | 若同时描述聚合，需要分开标注；不得把聚合图冒充目录图 | 所有现有叶子仍在根级 |
| 生产依赖 / 组件架构图 | parent 用不同关系表示，不能画成 compile 依赖 | 实际业务和共享 JAR 依赖 |
| 部署架构图 | 核对原服务、镜像、端口和数据库，不把 parent 画成服务 | 运行拓扑、入口、数据库所有权 |
| UML 包图 / 组件图 | 涉及 Maven 构件或配置归属的部分同步 | Java 包、接口实现及业务组件关系 |
| UML 类图 | 类、接口、关联若真实变化才改 | 领域模型不因 parent 改变 |
| 构建 / 配置加载时序图 | 模块遍历、parent 解析、注册表校验、产物收集流程同步 | 运行配置加载若未改变 |
| 业务时序图 | 核对参与方及其模块标签 | 登录、审批、文件上传等业务调用顺序 |
| 数据流程 / ER 图 | 核对节点归属、配置 / 日志路径及说明 | 业务数据流、表结构和数据库边界 |
| SVG / PNG / HTML 展示图 | 受影响标签、连线、说明、路径、链接及正文引用 | 经核对仍有效的内容 |

至少区分 `聚合`、`继承`、`生产依赖`、`HTTP/事件调用` 等连线语义，使用不同图或明确图例。三个继承例外不能在图上被误画为已迁移到各组 parent。

### 8.2 图源—导出—引用闭环

实施必须盘点并关联：

- Markdown 内 Mermaid / PlantUML 等实际存在的图源。
- 独立 `.mmd`、其他实际存在的 UML / 绘图源文件。
- 导出的 `.svg` / `.png`，SVG 内嵌文字、标题、图例、链接。
- HTML 内嵌图、动态导航、图注与说明。
- 文档正文及图片引用，和实际生成 / 维护脚本。

**不得只改图源保留旧 SVG，也不得只换 SVG 留下旧图源 / 正文。** 有多种生成方式时遵循文件当前的维护规则，不能根据同名文件或修改时间就认定源与导出完全一致。

有图源则更新受影响图源并重导出；确实无图源的手工 SVG 依现有规范修改，不凭空假设存在 PlantUML 源。历史图保留原貌，但不能未经说明作为当前架构入口。

### 8.3 现有图表盘点入口

现有 [docs/diagrams](../../diagrams/) 与其 [README](../../diagrams/README.md) 已包含模块结构、依赖、UML、ER、流程和时序材料。例如以下文件存在：

- `93_overall_system_architecture.mmd` / `.svg`：整体架构。
- `94_deployment_topology.mmd` / `.svg`：部署拓扑。
- `95_module_dependency_graph.mmd` / `.svg`：模块依赖。
- `96_ddd_layered_dependency.mmd` / `.svg`：DDD 分层。
- `100_module_infrastructure_structure.mmd` / `.svg`、`123_module_system_structure.mmd` / `.svg`：模块结构。
- `107_uml_auth_center_domain.mmd` / `.svg`、`110b_uml_system_domain.mmd` / `.svg`：领域 UML。
- `115_flow_workflow_approval.mmd` / `.svg`、`118_flow_message_outbox.mmd` / `.svg`：业务 / 事件流程。
- `124_aggregation_todo_architecture.mmd` / `.svg`、`125_unified_todo_sequence.mmd` / `.svg`：聚合架构与统一待办时序。
- `sequence/`：其他时序图材料；[SVG 维护指南](../../04-development/svg-maintenance-guide.md) 与 [Mermaid 查看指南](../../04-development/mermaid-viewer-guide.md)：维护与查看入口。

这些是**已定位的盘点候选，不是全部错误清单，也不是已全部审查通过的证明**。其中 `93_overall_system_architecture.mmd` 已显式标为迁移前历史拓扑，保留其历史性质，不因旧模块名追溯重绘；`95_module_dependency_graph` 的当前数量及缺项则按第 7.7 节处理。文件名中可能有历史命名，是否改名依据实际内容与引用兼容性，不按名字批量重命名。

[系统架构文档](../../architecture/system-architecture.md) 还包含独立内嵌 Mermaid；产品 HTML 同样有独立图源，不能假定二者是已有 SVG 的生成来源。[batch-verify-svg.sh](../../../scripts/utils/batch-verify-svg.sh) 检查部分 SVG 格式而非模块语义，且会写报告，执行前检查输出范围、将证据留在临时位置，不把脚本“通过”当作源—导出—正文已一致。

第一阶段应在当前架构文档中补齐准确的目标聚合和继承表达；本方案第 4 节已有聚合图与继承表，可作为语义基准。实施时优先维护相关现有图；若现有生产依赖图不适合表达 Maven 聚合，采用独立清楚标注的图 / 表，不向运行图强塞父 POM 节点。

### 8.4 图表验收

- 全部相关候选有“需更新 / 已验证不变 / 历史保留”的结论，不能仅抽查一张后宣称全仓图文一致。
- 受影响节点、边、数量、路径、图例、图注与对应 POM / 源码 / 配置一致。
- 图源语法可解析，实际使用的导出 SVG 为有效 XML，引用不存在旧图残留或失效链接。
- 检查实际渲染后的截断、重叠、不可读标签、连线与布局；不能用 XML 合法替代可读性验收。
- 将图变更范围与语义检查结果记录在现有材料 / 实施总结，渲染证据不默认入库。
- `check-doc-links.sh` 检查 Markdown / HTML 本地路径和锚点，**不覆盖 SVG 内部链接、图内容正确性或 JavaScript 动态导航**；这些另外核对。

## 9. 不选择的方案

| 方案 | 判断 | 原因 |
| --- | --- | --- |
| 只新增 parent，根仍直接列出全部叶子 | 可作中间步骤，不作最终形态 | 能增加继承层，但不能解决根聚合清单混杂 |
| 按六个业务域分别建 parent，把库与 service 放在一起 | 本轮不推荐 | 共享配置仍是库 / 服务两类；域级聚合与不同继承仍需协调，额外 POM 更多 |
| 物理移动到 `shared/`、`services/` 等目录 | 本轮不实施 | 波及脚本、配置相对路径、测试扫描、容器 COPY、文档链接与发布路径，风险明显更高 |
| 合并 common、domain、设施或业务库 | 不实施 | 会削弱已建立的物理依赖和领域边界；不是 POM 治理问题 |
| 把所有模块改继承 services-bom | 不实施 | 会扩大服务相关依赖管理影响，工具与共享库无须承担服务基线 |
| 立即重命名 services-bom 为 services-parent | 不实施 | 父坐标变更与分组无关，增加兼容和发布成本；先纠正说明文字即可 |
| 一次性拆根 parent、平台 BOM、测试 BOM、运行父 POM | 暂缓 | 尚无必要的外部消费场景证明，容易把管理复杂度从根转移到过多层级 |

## 10. 确认后的实施顺序

以下均是未来实施步骤，**本轮不执行**。P0–P5 是默认第一阶段，第二阶段的 BOM / 重复配置治理另行确认。

### P0：冻结有效模型及关联材料基线

1. 检查工作区，保留用户已有改动；记录已存在的方案文件。
2. 加载固定构建环境；如 daemon 曾使用其他 JDK，先正确初始化再 `mvnd --stop`。
3. 记录当前 36 个模块的 parent、GAV、packaging、物理路径及生产依赖。
4. 保存各叶子 basic / standard / enterprise 的 effective POM、依赖树及关键插件执行配置，特别核对三个继承例外、gateway、system、starter 和工具。
5. 建立文档、脚本、配置、测试、当前报告、路径及图源—导出—引用清单，对每项标注“必须修改 / 验证不变 / 历史保留”，在现有方案附录或临时检查清单中维护，不默认另建报告集。
6. 记录日志 / 存储 / License / classpath / Compose / 测试 cwd 的解析基准及图表当前引用，单列第 6.4、7.7 节的既有问题。

基线模型、日志与渲染输出放 `/tmp` 或既有受忽略产物目录，不入库，不输出真实密钥或环境内容。

### P1：增加薄 parent 并迁移继承

1. 新增 6 个薄 parent，暂不填 `<modules>`。
2. 根暂保留现有叶子直聚合并登记新 parent，保证过渡状态可构建。
3. 同步注册表新增条目，迁移 22 个叶子 parent / relativePath。
4. 不改三个继承例外及 10 个运行模块的直接 parent；不添加运行依赖、资源或插件执行。
5. 比较有效模型与路径，确认除 parent 链及必要项目元信息外无非预期差异。

### P2：切换聚合与构建配套

1. 分组加入明确的 `../叶子目录` modules；services-bom 加入 10 个运行模块。business parent 第一阶段无实际子模块，省略 modules，但作为根聚合与注册表的一项参与构建。
2. 根收拢为七组，删除重复根级叶子登记；同步递归注册表门禁及结构负面测试。
3. 更新注册表头注、POM 说明、脚本中受影响的语义与错误信息；保持现有叶子构建及发布逻辑。
4. 补齐新 parent 和既有漏项的 Containerfile POM COPY，增加与递归 reactor 的清单一致检查。
5. 补齐 aggregation 的 prebuilt 产物放行；示例镜像仅在明确验收时同时处置 example。

聚合切换和递归检查是同一逻辑批次，不交付明知门禁失败的半完成状态。

### P3：同步有效文档与图表

1. 按第 7 节同步当前有效文档的模块分类、继承 / 聚合说明、数量、命令和维护流程。
2. 依据真实 POM / 源码 / 配置，逐项核对架构、UML、时序、数据流及 SVG；修改受影响部分，标明验证不变与历史保留。
3. 图源、配对展示文件、索引、图注、Markdown / HTML 引用同批更新，检查导出与渲染；不改业务时序来迎合 Maven 分组。
4. 处置第 7.7 节的当前口径漂移，不对历史报告做批量数字替换。

### P4：配置、路径与测试闭环

1. 比较全部叶子的资源过滤、argLine、根属性注入、workingDirectory、target 及档位差异。
2. 检查 JAR 中配置、Logback、Flyway、Mapper、i18n、License 资源与构建标识保持正确位置。
3. 验证日志目录 / 属性优先级、测试 cwd、存储相对路径、外部 License 以及挂载 / 采集链路。使用隔离临时输出或本地非生产环境，不启动生产服务或覆盖真实数据。
4. 修改确实受影响的测试断言、路径消费者与说明；不改变未变业务行为，也不降低 SLA、覆盖率或跳过标准。

### P5：完整验收与结果收口

1. 执行第 11 节门禁，核对全部 reactor、测试失败 / 错误 / 跳过、打包与八服务集合。
2. 演练批准范围内的本地 prebuilt 镜像；动态路径验证有缺失时明确列为未完成，不以静态核对充当启动或采集通过。
3. 将本次实际命令、环境、范围、结果及例外同步到现有当前汇总 / 验收材料；历史证据不改写。
4. 检查实际 diff、文档链接、SVG 内链接、图文一致性和证据位置，确保无无关代码 / 密钥 / 产物。
5. 逐项闭合“修改 / 验证不变 / 历史保留”清单，交付实施总结；不自动提交 Git 或推送。

结构、文档、图表、配置和验证必须全部收口后才能宣称优化完成，不能在 P2 构建成功时提前结束。

### 扩展阶段：具体单体业务迁入（不属于第一阶段默认执行）

第一阶段交付可用的 `apex-business-parent` 管理入口，**不等于外部业务已经迁移完成**。取得原项目、确认领域 / 模块 / 运行 / 数据清单后，按第 4.5 节逐域新增业务库和必要运行模块，导入并适配代码、配置、资源、测试及图文说明。

每批同步实际 modules、注册表、构建 COPY / 忽略、发布或装配选择以及日志路径；以真实迁入模块数更新 `42 + N` / `43 + N`，完成兼容、权限、租户、事务、双库迁移及路径验证后才标记该批迁入完成。没有原项目与迁移授权时，不创建样例业务代替真实需求，也不执行生产数据迁移。

## 11. 验证与验收标准

### 11.1 本轮方案文档验证

本轮只检查：现状事实、35 个既有叶子与现有 services-bom 的完整覆盖、数量计算、已定位关联文件、方案前后语义、相对链接、命令和空白。**不实施 parent，不执行 Java 回归，不改既有图，也不宣称全部运行配置和图表已验收。**

### 11.2 实施后的强制门禁

所有命令从总根目录执行，先在当前 shell 加载：

```bash
source scripts/build/init-build-env.sh
java -version
mvnd --version
```

| 验证 | 执行方式或检查点 | 通过标准 |
| --- | --- | --- |
| POM 模型与 reactor | `mvnd -T4 -B validate`，检查模型与排序 | 第一阶段 42 个后代 + 根，共 43 个项目，包含空 business parent；无重复 / 缺失 / parent 解析错误；未来迁入按实际数量计算 |
| 注册表与新增结构测试 | `mvnd -T4 -B -pl apex-tools-build -am test` | 递归注册表、路径 / 环 / 重复负面用例均通过 |
| 叶子按需构建 | 分别选择 system-service、integration-service、aggregation-service、部署工具等并带 `-am` | 仍能从总根补齐所需依赖；不误构建全部组内兄弟模块 |
| 有效模型等价 | 对全部叶子比较 profile 下的依赖坐标 / 版本 / scope / optional / exclusions、插件执行、资源过滤 | 第一阶段无非预期差异；parent 链变化明确列入白名单 |
| 三档产物 | 使用 mvnd 分别按 basic / standard / enterprise 打包，检查选定服务及 basic 依赖裁剪 | 原先排除的能力未被 parent 重新带入；JAR 名称及打包类型保持 |
| 格式 | `mvnd -T4 -B spotless:check` | 无格式失败，不进行全仓无关格式化 |
| 完整门禁 | `mvnd -T4 -B clean verify` | reactor 全部成功，Failures / Errors 为 0；如实列出环境依赖跳过 |
| 本地安装解析 | `mvnd -T4 -B -DskipTests install` 后核对父链 POM | 新 parent 安装完整；该命令不当作测试通过证明 |
| 编排与发布集合 | 检查 package / status，必要时在批准的本地路径演练发布组装 | 服务仍为 8 个；新 parent 不产生服务 JAR，辅助 classifier 正确排除 |
| 镜像路径 | 用固定工具链产物验证八标准服务 Containerfile prebuilt | 目标 JAR 在构建上下文可见、路径不变；aggregation 放行已补齐；不执行未整改的 source 分支 |
| 配置 / 资源 | 比较有效模型及实际 JAR 中 application/bootstrap/logback、迁移、Mapper、i18n、License、构建标识 | 无误过滤、资源丢失或路径变更；不能仅据源码路径判断打包已正确 |
| 日志 / 文件路径 | 本地临时探针或隔离环境检查测试 / 运行 cwd、属性覆盖、输出位置与存储 / License 读取 | 默认及显式路径按正确基准解析，无分组目录错写或权限变化；未执行场景不记通过 |
| 日志采集 | 核对生产端、Compose 挂载与 Filebeat / Alloy 路径，隔离环境验证可采集 | 文件可见、扫描模式一致；只完成静态检查时标明不等于采集成功 |
| 文档及当前报告 | 模块 / 继承 / 聚合 / 数量 / 命令 / 当前验证结果逐项核对 | 与新实现一致；历史报告保留当时事实，不冒充新结果 |
| 图源 / SVG / 引用 | 检查实际相关图源、配对导出、正文 / HTML / 索引及渲染 | 关系语义正确，三个继承例外准确，图文一致无旧标签残留；格式检查不替代视觉核对 |
| 脚本语法 | 对本次修改的 shell 脚本按实际 Bash / zsh 语法检查 | 不混用解释器，不改变原入口和工作目录，不执行外部发布 |
| 链接与差异 | `bash scripts/ci/check-doc-links.sh`、SVG 内链接补查、`git diff --check` | 相对路径、大小写、锚点有效；无无关源码 / 密钥 / 构建证据入库 |

完整验收不只看退出码，也看 reactor 全量范围和测试统计；缺少 TC、双库或其他外部环境导致的跳过不得记为通过。双库 Testcontainers 并发启动因资源竞争失败时，按 AGENTS.md 的低并行重跑规则复核，不弱化测试要求。

结构重组不要求部署生产业务服务或改写真实环境配置；动态验证使用本地临时或隔离环境，不能为验证路径触碰生产数据。发布组装会覆盖已有 `dist` 时，先查看目标并确认，不能无提示删除既有产物。

### 11.3 完成判定与例外口径

全部满足后才可标记第一阶段完成：

- [ ] 七组聚合、第一阶段 42 个后代模块、43 个全量项目与唯一归属正确。
- [ ] `apex-business-parent` 已登记、继承正确、无业务源码 / 运行资源 / 启动端口或虚构子模块；具体单体迁入单独计划，不误标已完成。
- [ ] 22 个叶子 parent 迁移正确；三个继承例外及所有 GAV / 业务依赖 / 打包行为符合方案。
- [ ] 注册表、递归门禁、构建 POM 收集与 prebuilt 产物放行同步。
- [ ] 全部相关有效文档、脚本、配置说明、测试断言、当前验收内容与新实现一致。
- [ ] 路径清单中每项已修改或已验证保持，日志 / 资源 / 存储 / License / 部署解析基准无误。
- [ ] 全部相关图源、SVG 等展示文件、索引、正文 / HTML 引用闭环；历史材料明确保留。
- [ ] 三档模型 / 产物及完整 `clean verify` 通过，计数与测试范围真实，环境跳过单列。
- [ ] 本地镜像与必要动态路径验证有实际结果，不将静态检查冒充运行通过。
- [ ] diff、文档与 SVG 链接检查完成，无密钥、真实配置、构建残渣或无关变更。

无法执行某动态场景时，注明前置条件、实际静态覆盖和剩余验证，不标“全部完成”；需要用户明确接受该例外后才能以限定范围交付。Containerfile source 工具链整改和第二阶段配置收敛是本方案明确排除项，不因此虚构其通过结果。

## 12. 风险与回退

| 风险 | 防护与处理 |
| --- | --- |
| parent 路径仍指根 POM，GAV 不匹配 | 显式 sibling relativePath，验证模型解析与本地安装 |
| 根和分组重复聚合同一叶子 | 递归检查同时验证真实路径、GAV 及唯一归属 |
| 子模块 profile 合并改变 basic 裁剪 | 三档 effective POM 与依赖 / 产物比较，不仅检查默认 standard |
| 新 parent 激活 repackage、Shade 或重复测试执行 | 第一阶段禁止新增此类执行；保留叶子差异和原 execution ID |
| 新 parent 丢失日志、PMD、测试根目录定位 | 保留叶子物理位置；仍比较 inherited 配置，不假定路径一定等价 |
| 容器预下载阶段缺 POM | 补齐全部 parent 和叶子 COPY，以递归 reactor 做一致性检查 |
| 外部消费缺新 parent POM | 安装 / 未来发布时完整提供父链，服务压缩包与 Maven 发布分开管理 |
| 分组 parent 被误当部署服务 | 注册表 none、无端口 / mainClass、部署清单不包含它们 |
| 聚合图被误当物理目录或依赖图 | 分图或图例区分，不伪造 parent 的生产依赖边 |
| 未改日志路径却发生资源过滤 / cwd 漂移 | 对比有效模型、JAR 内资源和实际进程路径，不只看原配置文本 |
| 文档源已更新但 SVG / HTML 仍旧 | 图源—展示—索引—正文引用同批闭环，检查实际渲染 |
| 当前文档混入历史统计，旧测试报告冒充新结果 | 现状 / 历史分开，实际执行范围与报告结果一致 |
| aggregation 产物被 ignore 导致镜像验收失败 | 补齐目标 JAR 放行并检查构建上下文，作为既有阻断单列 |

回退仅恢复本次新增 parent、叶子 parent 声明、modules、配套测试 / COPY / ignore 与对应文档图源 / SVG / 说明，不触碰业务数据、历史迁移或用户已有修改。结构与文档 / 图表按同一版本基线回退，避免恢复旧 POM 却保留新图。分批实施时按本次实际 diff 恢复，不使用全仓 `git reset --hard`。

## 13. 最终执行范围（已批准并实施）

用户已批准并按下列范围实施第一阶段；本节保留批准时的范围定义，实施结果见第 14 节。

- **采用七个分组及六个新 parent 名称，保留全部现有叶子目录、GAV 与业务 / 部署边界。**
- **实施 P0–P5 的结构等价与全面对齐方案，保留三个继承例外；新增 `apex-business-parent` 作为项目业务库的专用管理入口，覆盖外部迁入和新开发业务；通用框架实现、服务运行装配由其他分组提供，不在 business 重复实现。**
- 第一阶段只建立 business parent；实际迁入业务代码、配置与子模块需取得原项目并确认迁移清单后另行实施，不能把建立父 POM 等同迁入完成。
- 同步递归注册表、POM COPY、受影响脚本 / 配置说明 / 测试；补齐 aggregation 的既有 prebuilt 产物放行阻断。
- 全面同步当前有效文档及相关架构、UML、时序、数据流、SVG、图源 / 导出 / 引用；处置已定位的现状口径漂移，历史材料不追溯改写。
- 把日志、配置、存储、License、classpath、测试、构建及部署路径纳入强制等价检查，**不机械改动未变化的路径**。
- 完成三档比较、完整 `clean verify`、目标 prebuilt 镜像及必要本地路径验证；如有环境例外，明确报告并另行确认限定交付。
- 不实施第二阶段 BOM / 重复配置收敛，不整改 Containerfile source 工具链，不物理迁移目录，不部署生产、不发布远程 Maven 构件、不提交或推送 Git。
- example 镜像及其 ignore 补齐仅在明确选择示例镜像验收时实施，不自动扩大标准服务验收范围。

**上述范围已获用户批准并完成实施。** 若希望物理根目录也只保留几个文件夹，需要先调整本方案，不能在执行中顺带迁移。

## 14. 实施结果（2026-11-13）

### 14.1 实际结构

第一阶段已按本方案实施，`apex-business-parent` 保持空组：

| 分组 | 实际聚合模块数 | 实际叶子 |
| --- | ---: | --- |
| `apex-shared-parent` | 9 | common、domain、api-contract、五个 infrastructure 能力 JAR、compat infrastructure |
| `apex-components-parent` | 6 | system、auth、workflow、file、office、integration |
| `apex-starters-parent` | 4 | storage、license、job、seata starter |
| `apex-services-bom` | 10 | 八个标准服务 + job-service + masking-service |
| `apex-applications-parent` | 2 | example、boot-all |
| `apex-tools-parent` | 4 | license-tools、generator、tools-deployment、tools-build |
| `apex-business-parent` | 0 | 第一阶段无子模块 |

- 根 POM 直接聚合 **7 组**；递归后代 **42 个**；含根 reactor **43 项**；**35 个既有 JAR** + 8 个 POM。
- **22 个既有叶子**迁移直接 parent：shared 8、components 5、starters 4、applications 1、tools 4。
- **13 个 `apex-services-bom` 子项目继承保持**，其中三个例外 api-contract / integration / boot-all 的聚合归属分别为 shared / components / applications，且未改为 services 继承。
- 十个运行模块的聚合与继承归属均在 services。
- 六个新 parent 均为 `packaging=pom`，仅 `pom.xml`，无依赖、资源、源码目录、mainClass、Boot / Shade 打包执行；`apex-business-parent` 省略 `<modules>`。

### 14.2 实际执行的验证

| 验证项 | 实际结果 |
| --- | --- |
| 三档 effective POM 对比 | basic / standard / enterprise 全部为 43 项；原 37 项逐项比对 dependencies / dependencyManagement / properties / build / profiles 等，**无非预期差异** |
| 三档 dependency:tree 对比 | 原 37 项目模块树**逐模块一致**，无依赖变化 |
| 生产资源哈希对比 | 387 个原生产资源中仅 `build-manifest.yaml`（本次预期修改）变化 |
| `mvnd -B validate` | BUILD SUCCESS，43 项目，无重复 / 缺失 / parent 解析错误 |
| 构建管理门禁测试 | 67 项通过（BuildManifestSyncTest 9、MavenModuleInventoryTest 41、ContainerBuildContractTest 14、BuildPathContractTest 3），Failures / Errors 为 0 |
| 受影响服务快速回归 | 多模块 `test` BUILD SUCCESS；已有环境依赖跳过项保留 |
| `spotless:check` | BUILD SUCCESS（全仓） |
| 三档 package | basic / standard / enterprise 均 BUILD SUCCESS；八个服务均单 JAR fat jar，`apex-boot-all` 保持薄 JAR；basic 档 system-service 依赖数 375 < standard 396，符合裁剪 |
| 完整 `clean verify` | **BUILD SUCCESS，43/43 项目**，17:13；Surefire 5916 tests、Failsafe 401 tests，Failures 0 / Errors 0，跳过项为既有环境依赖用例 |
| 发布集合 | `release.sh SERVICES` 仍为 8 个服务；八服务目标目录各 1 个发布 JAR |
| `apex.sh status` | 正常识别七组 parent 为 `none` / 不产出可执行 JAR，未误标为服务 |
| 文档链接检查 | 3886 个链接、0 断裂（含新增 128 / 129 图） |
| `git diff --check` | 通过 |

### 14.3 未完成或受限项

- **图表视觉验收未完成**：视觉验收代理供应商连续不可用（多种 visual-judge 类型均报 provider-not-found），当前会话模型不支持读取 PNG。三份 SVG 已完成 XML 有效性、文字画布 / 卡片越界 0、文本包围盒重叠 0 的几何检查，并对渲染 PNG 做了 tesseract OCR 文字识别（关键分组名、模块名、数量均被正确识别）与 PIL 内容边界检测（四边余量 20–288 px，无裁切）。**仍无人以视觉方式确认版面观感**，不宣称视觉验收通过。
- Containerfile `source` 分支仍调用系统 `mvn`，按方案属于明确排除项，未整改、未验证通过。
- 未部署真实业务服务、未运行完整 Compose 应用栈（MySQL/Nacos/Redis 依赖未启动）、未验证 Filebeat / Alloy 真实采集链路，也未迁移生产数据。
- 第二阶段 BOM / 重复配置收敛未实施。
- 未提交 Git、未推送、未发布远程 Maven 构件。

### 14.4 容器验证结果（Podman）

按用户要求使用 Podman 工具链完成，不使用 OrbStack Docker：

| 验证项 | 实际结果 |
| --- | --- |
| 基础镜像 | `podman pull docker.io/library/eclipse-temurin:21-jre-alpine` 成功 |
| prebuilt 镜像构建 | `podman build --build-arg BUILD_MODE=prebuilt --build-arg SERVICE_MODULE=apex-aggregation-service` **成功**，镜像 290 MB；证明新增 6 个 parent POM 的前置 COPY 与 aggregation 产物放行在真实构建上下文生效 |
| 镜像内容 | `/app/app.jar` 79,876,101 字节；`/app/logs`、`/app/data/storage` 存在且属主 `appuser:appgroup`；`LOG_PATH=/app/logs`、`HEALTHCHECK_PORT=8098` 正确 |
| 容器运行 | `podman run` 启动成功：`Netty started on port 8098`、`Started AggregationServiceApplication`，`/actuator/health` 返回 `{"status":"UP"}` |
| Compose 编排 | `podman-compose -f podman-compose.yml config` 解析成功（17 服务，含 8 应用服务 + security-key-check）；临时校验栈 `up -d` 后容器 `Up`、端口 `18099→8098` 映射正常、健康检查 `UP`、宿主 `./verify-logs:/app/logs` 挂载生效 |
| 网关日志路径 | 本地以 `LOG_PATH` 启动 `apex-gateway-1.3.1.jar` 成功，目标目录生成 `apex-gateway.log` 与 `apex-gateway-json.log`，JSON 含 `service`/`serviceName` 字段，证明父 POM 重组未改变日志解析基准 |
| 清理 | 验证容器、临时镜像、临时挂载目录均已删除，仓库无残留 |

网关的独立 prebuilt 镜像构建未完成：基础镜像内 `apt-get` / `apk` 软件源在当前网络下反复超时。该限制属于网络环境，不影响上述 aggregation 镜像对 POM COPY 与产物放行的验证结论。

### 14.5 结论口径

结构重组、有效模型等价、依赖等价、三档打包、完整 Maven 门禁、prebuilt 镜像构建与运行、Compose 编排及日志路径均已实际通过。**仅图表人工视觉观感确认仍缺失**，已用 OCR 与几何检测提供程序化可读性证据；如需正式视觉签核，应在具备图像查看能力的环境复核 128 / 129 / 95 三张图。

## 15. 相关现有文件

- [仓库协作与构建约束](../../../AGENTS.md)。
- [根 POM](../../../pom.xml)。
- [现有服务父 POM](../../../apex-services-bom/pom.xml)。
- [模块注册表](../../../apex-tools-build/src/main/resources/build-manifest.yaml)。
- [注册表同步测试](../../../apex-tools-build/src/test/java/com/apex/scaffold/tools/build/BuildManifestSyncTest.java)。
- [统一构建编排入口](../../../apex-tools-build/bin/apex.sh)。
- [构建管理使用说明](../../04-development/build-tools-usage.md)。
- [构建初始化脚本](../../../scripts/build/init-build-env.sh)。
- [发布脚本](../../../scripts/release/release.sh)。
- [Containerfile](../../../Containerfile)。
