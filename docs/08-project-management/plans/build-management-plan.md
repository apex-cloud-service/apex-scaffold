# 构建发布管理模块方案(apex-tools-build)

> 状态:已评审通过,2026-11-06 实施。
> 目标:新增一个模块,对全部模块的**编译、构建、打包、部署**提供统一管理与单一入口,并以门禁测试锁住模块清单与交付口径,防止漂移。

## 1. 背景与可行性结论

**结论:可行**,但需接受一个 Maven 固有限制:reactor 内的模块无法编排"包含它自己的那次构建"(鸡生蛋)。因此"管理"能力拆成两层实现:

1. **reactor 内(受门禁保护)**:模块注册表 + 防漂移一致性测试,进入 `mvnd verify` 常规门禁;
2. **reactor 外(统一入口)**:`bin/apex.sh` 由人/CI 调用,编排现有 `mvnd`、`scripts/build/build.sh`、`scripts/release/release.sh`、`scripts/deploy/*` 等已验证执行器。

与现有 `apex-tools-deployment`(in-reactor 工具模块、reactor 外被脚本调用)完全同构,有先例可循。

**现状痛点(2026-11-05 评审确认)**:构建环境/构建/打包/部署散落在多个入口(`init-build-env.sh`、`build.sh`、`release.sh`、`start.sh`/`stop.sh`、`pull-infra-images.sh`、`local-deployment.sh`、`app.sh`),无统一生命周期入口;`security-key-check` 所需的 `tools/local-deployment.jar` 无自动补建编排;34 个模块清单、7 个服务交付 JAR 集合、各服务 mainClass/端口等事实无机器可校验的单一注册表,漂移只能靠人工核对。

## 2. 总体设计

### 2.1 新模块 `apex-tools-build`(第 35 号模块,parent=根 POM,packaging=jar)

```text
apex-tools-build/
├── pom.xml                                # 直接 parent 根 POM;零生产依赖
│                                          # test scope: junit-jupiter + snakeyaml(父 BOM 已管版本)
├── bin/apex.sh                            # 统一生命周期入口(zsh;set -euo pipefail;
│                                          #   复用 scripts/lib/common.sh 日志与校验函数)
├── src/main/resources/build-manifest.yaml # 35 模块注册表(单一事实来源)
├── src/test/java/com/apex/scaffold/tools/build/
│   └── BuildManifestSyncTest.java         # 防漂移一致性测试(进 verify 门禁)
└── README.md                              # 模块说明 + apex.sh 命令手册
```

### 2.2 build-manifest.yaml(模块注册表)

每个模块一条记录,字段:`artifactId`、`category`(共享基础/共享领域/共享设施/共享契约/服务父 POM/业务库/运行模块/服务增强/对象存储/授权模块/示例与工具/组合部署/运维工具/构建管理)、`deliverable`(`none`|`service-jar`|`optional`|`tool-jar`)、可选 `port`/`managementPort`(信息性)、可选 `mainClass`(仅登记 POM 显式声明者)。纯资源文件,不进任何服务的运行期装配。

条目顺序与根 POM `<modules>` 声明顺序一致,便于人工比对。

### 2.3 BuildManifestSyncTest(核心防漂移门禁,仿 `FlywayMigrationVersionTest` 模式)

- 根 `pom.xml` 的 `<modules>` 集合 == 注册表 artifactId 集合;
- `deliverable` 与打包插件双向一致:`none` ⇒ 无 spring-boot/shade 打包插件;`service-jar`/`optional` ⇒ 有 `spring-boot-maven-plugin`;`tool-jar` ⇒ 有 `maven-shade-plugin`(仅 `apex-tools-deployment`);
- POM 显式声明的 `<mainClass>` 与注册表登记一致(当前仅 `apex-system-service`、`apex-example` 显式声明,其余服务由 repackage 自动探测,不硬编码);
- `scripts/release/release.sh` 的 `SERVICES` 数组 == 注册表 `service-jar` 集合(网关 + 六个业务服务,共 7 个)——锁死"发布脚本 vs POM vs 注册表"三方一致。

测试为纯 JVM 断言(解析 POM/脚本/YAML),不执行 shell、不依赖本机环境,CI 可跑。

### 2.4 bin/apex.sh 子命令(全部委托现有执行器,不重写其逻辑)

| 子命令 | 行为 | 委托/说明 |
| --- | --- | --- |
| `env` | 加载并校验构建环境,打印 JDK/mvnd 版本 | source `scripts/build/init-build-env.sh` |
| `build [-P profile] [--module X..] [--with-tests]` | 整仓默认走 build.sh;`--module` 时 `mvnd clean package -pl <列表> -am` | 必带 `-am`(项目经验) |
| `test [--module X]` | `mvnd -pl X -am test`,缺省整仓 `mvnd -T4 -B test` | 快速验证 |
| `verify` | `mvnd -T4 -B clean verify` | 完整门禁 |
| `package [-P profile]` | 构建 7 个服务 fat jar 并逐一校验"有且仅有一个发布 JAR" | 模块列表来自注册表 `service-jar`,口径同 release.sh 阶段 1 |
| `release [basic\|standard\|enterprise] [版本] [--allow-dirty]` | 发布前置检查(工作区干净、版本与 POM 一致由 release.sh 校验)→ 执行 release.sh → 校验 `.zip` 与 `.zip.sha256` 生成 | 委托 `scripts/release/release.sh`,不改动它 |
| `image build [--module X\|--all] [--mode prebuilt\|source] [--engine podman\|docker] [--tag 引用]` | 构建服务镜像,注入 SERVICE_MODULE/BUILD_MODE/BUILD_VERSION(读根 POM);默认镜像名=模块 artifactId,`--tag` 指定完整引用 | 双引擎 |
| `image push --registry host[:port] [--module X\|--all] [--engine podman\|docker]` | 打 registry 前缀并推送 | k8s 远程交付需要 |
| `deploy java-jar <动作> [模块] [env] [档位]` | `precheck/start/stop/restart/status/logs/console/health`;动作透传 `scripts/deploy/service/app.sh`(APEX_SERVICE_MODULE 注入),precheck 校验可执行 JAR 存在,health 按注册表端口单模块 readiness | 运行时一 |
| `deploy podman-compose <precheck\|infra\|up\|down\|status\|health>` | precheck(守护可达/.env/工具 JAR 自动补建);up/down 委托 `start.sh`/`stop.sh`;infra 委托 `pull-infra-images.sh` | 运行时二 |
| `deploy docker-compose <precheck\|infra\|up\|down\|status\|health>` | 同一 compose 文件双引擎;`docker compose` 插件优先、回退 docker-compose 二进制;infra 用 `compose pull`;up 注入与 start.sh 相同的优雅停机治理变量 | 运行时三(新路径) |
| `deploy k8s <precheck\|apply\|status\|health\|down> [--canary] [--pdb] [--yes] [--allow-placeholder]` | fail-closed 编排 `k8s/`:集群可达性、**内嵌 Secret 占位值默认拒绝**(changeme/change-this 扫描)、apply 后 rollout status 等待、down 需 `--yes`(含 Secret 删除) | 运行时四(新路径) |
| `status` | 环境版本 + 注册表概览 + 各模块 target 产物存在性 | — |
| `help` | 子命令手册 | — |

## 3. 对现有文件的改动(最小化)

1. **根 `pom.xml`**:`<modules>` 末尾插入 `<module>apex-tools-build</module>`;顺手修正错位注释(原"运维工具模块…"注释悬在 `apex-boot-all` 上,移到 `apex-tools-deployment` 上方,并为 `apex-boot-all` 补回组合部署说明)。
2. **AGENTS.md**:模块总数 34→35(模块与物理边界开头句、分类表加"构建管理"行、依赖表加 `apex-tools-build | 无` 行)。
3. **delivery-scope.md**:模块清单 34→35,注明 `apex-tools-build` 与 `apex-tools-deployment` 同口径(不随交付包发布)。
4. **deliverables.md 事实对齐**:服务 JAR 计数"六个(网关+五个业务服务)"→"七个(网关+六个业务服务)",与 release.sh `SERVICES` 一致;"`.sha256` 需在发布流程外自行生成"的过时注更正(release.sh 阶段 3 已生成)。
5. **方案文档落盘**:本文件(`docs/08-project-management/plans/build-management-plan.md`)。

## 4. 明确边界(不做的事)

- **不搬移、不重写**任何现有脚本;build.sh/release.sh/app.sh 等保持原样作为底层执行器,`bin/apex.sh` 只做编排层;
- **部署管理 = java -jar / Podman Compose / Docker Compose / K8s 四种运行时**;不含 SSH 远程部署;K8s 为 `k8s/` 清单的 fail-closed 编排(内嵌 Secret 占位值默认拒绝部署、`down` 需 `--yes`),不替代密钥管理,Ingress 域名/TLS 与金丝雀镜像仍由部署者按 README 修改;
- **不改发布包内容**:release.sh 产物结构不动(仅被一致性测试读取);
- **不动 `.github/workflows/ci-cd.yml`**;
- 不引入 SSH 工具、不新增远程凭据管理;新模块不打进交付包。

## 5. 已知问题记录(本方案不修,留待后续)

- `.github/workflows/ci-cd.yml` 旧 release job 与 release.yml 双发布路径漂移;
- `apex-boot-all` 为无 repackage 薄 jar,`scripts/deploy/assemble-topology.sh` 对其 `java -jar` 存在矛盾(既有问题);
- R-05:workflow 默认端口 8083 与 system 管理端口 8083 冲突(Compose 用 8093 规避)。

## 6. 验证门禁

```bash
source scripts/build/init-build-env.sh
mvnd -pl :apex-tools-build -am test          # 新模块单测(必带 -am)
mvnd -T4 -B spotless:check                   # 含新模块格式门禁
./apex-tools-build/bin/apex.sh env           # 冒烟:环境校验
./apex-tools-build/bin/apex.sh status        # 冒烟:注册表渲染
./apex-tools-build/bin/apex.sh deploy precheck   # 冒烟:前置检查与工具 JAR 补建(不执行容器操作)
./apex-tools-build/bin/apex.sh build --module apex-common   # 真实构建冒烟
mvnd -T4 -B clean verify                     # 完整回归(~20 分钟,含新一致性测试)
git diff --check
```

预期:`BUILD SUCCESS`、`Failures: 0 / Errors: 0`。`deploy up/infra` 等真实容器操作需本机 podman 环境与 `.env` 密钥,不在静态验证范围,如实另行报告。

## 7. 验证记录(2026-11-06 实测)

| 验证项 | 结果 |
| --- | --- |
| `mvnd -pl :apex-tools-build -am test` | BUILD SUCCESS;Tests run: 4, Failures: 0, Errors: 0(注册表 vs 根 POM/打包插件/mainClass/release.sh SERVICES 四项一致性) |
| `mvnd -pl :apex-tools-build spotless:check` | 首查 1 个文件格式违规,`spotless:apply` 后复检通过 |
| `apex.sh help` / `env` / `status` | 通过;status 正确渲染 35 模块注册表、交付口径与 target 产物状态 |
| `apex.sh deploy precheck` | 通过;`tools/local-deployment.jar` 缺失时自动构建 `apex-tools-deployment` 并补齐(实测补建 3.0MB jar) |
| `apex.sh build --module :apex-common` | BUILD SUCCESS(-pl -am 路径实测) |
| `apex.sh release standard`(前置检查) | 正确拦截未提交工作区并退出码 1;未知命令退出码 1、help 退出码 0 |
| 文档链接门禁 `check-doc-links.sh` | 732 文件 / 3501 链接 / 断裂 0(修正 README 相对层级一处后) |
| `git diff --check` | 通过 |
| 完整回归 `mvnd -T4 -B clean verify` | 第 1 次:apex-integration 双库迁移 IT 因 postgres:15 镜像拉取停滞失败(网络原因,`Docker image pull has not made progress in 30s`),9 个排队模块被跳过,非本次变更引起;`podman pull postgres:15` 预热后第 2 次:**反应堆 35 模块 + 根聚合全部 SUCCESS,测试 6199,Failures 0 / Errors 0,Skipped 33(均为环境依赖型:Seata TC/MQTT/MQ broker/TLS/方言),失败项双库迁移 IT 本次 2/2 通过** |

| `apex.sh deploy java-jar precheck apex-system-service` | 通过;可执行 JAR 校验命中 |
| `apex.sh deploy podman-compose precheck` / `docker-compose precheck` | 双引擎均通过(守护可达、.env、工具 JAR);docker compose 插件 v5.5.0 识别正常 |
| `apex.sh deploy k8s precheck` | 本机无集群 → 集群不可达优雅报错;内嵌 Secret 占位扫描条件实测命中(4 处,若集群可达将 fail-closed);`down` 未加 `--yes` 退出码 1 |
| `apex.sh image build/push` 参数校验 | 缺 `--module` / 缺 `--registry` 均退出码 1 |
| `apex.sh image build --engine podman`(真实构建) | **成功**:prebuilt 模式端到端产出 `apex-file-service:latest` |
| `apex.sh image build --engine docker`(真实构建) | 路径执行到 registry 元数据解析后超时——本机 docker 上下文为 buildkit 容器驱动(日志 URL 含 build/podman/podman),即使基础镜像已 `docker pull` 成功仍强制联网解析,Docker Hub 网络停滞所致(与 postgres:15 同类环境问题,非脚本路径缺陷) |
| 完整回归第三轮(四运行时调整后) | `mvnd -T4 -B clean verify`:BUILD SUCCESS,反应堆 36 条目(根聚合+35 模块)全 SUCCESS,Failures 0 / Errors 0,Skipped 33(环境依赖型) |
| 注释规范化(第 8 节末条) | `scan-javadoc-issues.sh`:@author/@since 全仓 0 缺失,类级命中仅既有 4 个已核实误报(本模块 0 命中),方法级启发式本模块 0 命中;Spotless BUILD SUCCESS;`git diff --check` 通过 |
| 注释零行为变更证明 | apex.sh 剥离注释行后与原版 diff:仅函数定义移位(内容逐字一致)与空行增删;build-manifest.yaml 35 条记录行逐行一致;注释增强后 status/health/manifest_field 冒烟解析正常 |
| 完整回归第四轮(注释规范化后) | `mvnd -T4 -B clean verify`:BUILD SUCCESS,反应堆 36 条目全 SUCCESS,Failures 0 / Errors 0,Skipped 33(环境依赖型) |
| 使用说明文档 | `check-doc-links.sh`:733 文件 / 3533 链接 / 断裂 0;文档内全部子命令与 `apex.sh help` 逐一比对通过;端口表与注册表、`podman-compose.yml` 映射三方一致 |
| hostPort 修复 | `status` 渲染 `apex-workflow-service ... 端口 8083(宿主 8093)`;模块 4 项一致性测试仍全绿 |
| 完整回归第五轮(文档 + hostPort 后) | `mvnd -T4 -B clean verify`:BUILD SUCCESS,反应堆 36 条目全 SUCCESS,Failures 0 / Errors 0,Skipped 33(环境依赖型) |

**未验证的运行条件**(需真实容器编排、可用 K8s 集群、`.env` 密钥或长时间打包;命令路径已静态核对并复用既有脚本):

- `deploy java-jar start/stop`(真实进程启动,需真实 MySQL/Redis)
- `deploy podman-compose / docker-compose infra / up / down`(真实全栈容器编排)
- `image push`(真实推送);`deploy k8s apply`(本机无集群)
- `release` 完整产出(三档 zip 全量打包)与 `package` 全量(7 服务 fat jar 构建)

## 8. 调整记录

- **2026-11-06(用户要求):部署支持四种运行时。** 原 `deploy` 仅编排 Podman Compose;调整为 `deploy <运行时> <动作>`:
  - `java-jar`:新增,委托 `scripts/deploy/service/app.sh`(start/stop/restart/status/logs/console),precheck 校验模块可执行 JAR,health 为单模块 readiness;
  - `podman-compose`:原能力收编为运行时二(precheck/infra/up/down/status/health);
  - `docker-compose`:新增路径——经核对 `podman-compose.yml` 与 docker 兼容(仅 `network_mode: none`/`read_only: true`,均受支持;`dockerfile: Containerfile` 已显式指定),infra 拉镜像因 `pull-infra-images.sh` 为 podman 专属改用 `compose pull`,up 注入与 start.sh 相同的优雅停机治理变量;
  - `k8s`:新增,fail-closed 编排 `k8s/deployment.yaml`(+可选 `--pdb`/`--canary`):集群可达性检查、内嵌 Secret 占位值(changeme/change-this)默认拒绝部署(`--allow-placeholder` 显式豁免)、apply 后 `rollout status --timeout=300s`、`down` 需 `--yes`(会删除含 Secret)。
  - `image` 扩展:`--engine podman|docker`、`--tag 完整引用`、新增 `image push --registry`(k8s 远程集群交付需要)。
  - 镜像与 k8s 清单的衔接:清单使用 `apex-scaffold:latest`(单体聚合形态,SERVICE_MODULE=apex-example 构建),precheck 输出构建/推送命令提示。
- **2026-11-06(用户要求):注释规范化。** 四个文件按仓库注释规范补齐:
  - `BuildManifestSyncTest.java`:类级 Javadoc 扩展为"四方一致契约"完整说明(含业务背景与设计约束),类/常量/全部方法补 Javadoc,`@author Apex Team` + `@version/@since 1.3.2`(下一发布版口径)、句尾 `。`;代码行零变更;
  - `build-manifest.yaml`:头注扩展为消费方/字段语义/解析契约(awk 行式)/新增模块同步清单,并按分类增组注释;35 条记录行逐字不变;
  - `pom.xml`:parent/依赖/surefire 三处段落注释(零生产依赖、apex.root 注入口径);
  - `bin/apex.sh`:全部函数升级为"用途/参数/侧效/调用方"契约块,zsh 构造((f)/(N)/(j:,:))与 curl -w 语义加行内说明;`resolve_container_cli`/`image_ref` 移至 image 分节(定义均在入口分发前加载,语义无差),剥离注释后与原脚本代码逐行一致。
- **2026-11-06(用户要求):新增使用说明文档。** 落位 `docs/04-development/build-tools-usage.md`(构建规范同目录,并在 `docs/04-development/README.md` 索引登记),含:模块定位与解决的问题、首次环境准备(含 6 个 Compose 强制变量)、命令速查表、五条完整流程(日常开发/打包发布/镜像/四运行时部署/端到端上线)、注册表维护六步同步清单与格式契约、故障排查表(16 项现象→原因→处理)、设计边界与 5 条常见误解、文件清单与相关文档索引。
- **同轮修正的既有缺陷**:注册表原 `port` 语义混用了"容器内端口"与"Compose 宿主端口"——workflow-service 容器内 8083 在 Compose 中映射为 **8093**(规避与 system 管理端口 8083 冲突),导致 `deploy_health` 探测 8083 必然失败。已新增 `hostPort` 字段(仅 Compose 语义,缺省回退 managementPort→port),workflow 登记 `hostPort: 8093`;`deploy_health` 改为优先取 hostPort,`cmd_status` 并列展示"8083(宿主 8093)"。
