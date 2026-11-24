# apex-tools-build — 构建发布管理模块

对全部模块的**编译、构建、打包、发布、镜像、部署**提供统一管理与单一入口。方案见
[构建发布管理模块方案](../../docs/08-project-management/plans/build-management-plan.md)，
完整操作手册见 [构建发布管理模块使用说明](../../docs/04-development/build-tools-usage.md)。

## 组成

| 内容 | 说明 |
| --- | --- |
| `src/main/resources/build-manifest.yaml` | 全部递归模块的扁平注册表（单一事实来源）：第一阶段 42 项，不含总根 POM；分类、交付口径、端口、mainClass |
| `src/test/java/.../BuildManifestSyncTest.java` | 防漂移门禁：注册表 vs 根 POM 递归 `<modules>` 集合 vs 各模块打包插件/mainClass vs `release.sh SERVICES` |
| `bin/apex.sh` | 生命周期统一编排入口（只做编排，构建/发布/部署能力由 `scripts/` 既有脚本与 mvnd 承载） |

模块为薄 jar：零生产依赖（仅 test scope 的 snakeyaml/JUnit），直接继承 `apex-tools-parent`，经它继承根构建配置；不参与服务运行期装配，不进交付发布包。

## Maven 分组与计数

根直接聚合 shared/components/starters/services/applications/tools/business 七组；递归模块 44 个，含根 reactor 45 个，其中 8 个 POM、37 个 JAR；第二阶段（2026-11-14）起叶子目录嵌套于分组父目录下。完整分组、22 个叶子 parent 调整、第二阶段物理归位与保留的 13 个 services-bom 子继承见 [README 分组说明](../../README.md#maven-分组与父链)。api-contract / integration / boot-all 的聚合归属分别在 shared / components / applications，直接 parent 仍是 services-bom。

注册表保持 `schema: 1` 扁平格式，登记全部递归后代（含七个分组 parent 与 business 组业务库、不含总根）。递归沿 `<modules>` 聚合边，不沿 `<parent>` 继承边；第二阶段起叶子物理位于 `<分组父>/<artifactId>`（两级布局由 `MavenModuleInventory` 门禁锁定）。parent 记录为 `deliverable=none`，没有端口、mainClass 或可执行 JAR，仍是模型解析所需的 POM 构件。后续新增模块依实际递归清单登记，不永久硬编码 42 / 43。

business 第一阶段无子模块，只为项目业务 JAR 库预留管理入口；框架实现复用其他支撑分组。新独立运行模块应聚合并继承 services-bom，不能在 business 重复登记或未经决策进入八服务发布集合。

## apex.sh 命令手册

```bash
./apex-tools-build/bin/apex.sh <子命令> [参数]
```

| 子命令 | 说明 |
| --- | --- |
| `env` | 校验并加载固定构建环境（SDKMAN + JDK 21.0.13.0+9-tem + mvnd 2.0.0-rc-3） |
| `build [-P basic\|standard\|enterprise] [--module m1,m2] [--with-tests]` | 整仓构建委托 `scripts/build/build.sh`；`--module` 时 `mvnd clean package -pl <列表> -am`（默认跳过测试） |
| `test [--module m]` | 快速测试：指定模块为 `mvnd -pl m -am test`，否则整仓 `mvnd -T4 -B test` |
| `verify` | 完整回归门禁 `mvnd -T4 -B clean verify` |
| `package [-P profile]` | 构建 8 个发布服务 fat jar（+ 部署工具）并逐一校验"有且仅有一个发布 JAR" |
| `release [basic\|standard\|enterprise] [版本] [--allow-dirty]` | 前置检查（工作区干净）→ 委托 `scripts/release/release.sh` → 校验 `.zip`/`.zip.sha256` 生成 |
| `image build [--module m\|--all] [--mode prebuilt\|source] [--engine podman] [--tag 引用]` | 使用 Podman 构建服务镜像，默认镜像名=模块 artifactId；`--tag` 指定完整引用（如 `apex-scaffold`） |
| `image push --registry host[:port] [--module m\|--all] [--engine podman] [--tag 引用]` | 使用 Podman 打 registry 前缀并推送（k8s 远程集群交付需要；可走 `APEX_IMAGE_REGISTRY`） |
| `deploy java-jar <动作> [模块] [dev\|it\|prod] [档位]` | `precheck/start/stop/restart/status/logs/console/health`，委托 `scripts/deploy/service/app.sh`；模块默认 apex-gateway |
| `deploy podman-compose <precheck\|infra\|up\|down\|status\|health> [透传]` | 前置检查（podman/.env/工具 JAR 自动补建）；up/down 委托 `start.sh`/`stop.sh` |
| `deploy k8s <precheck\|apply\|check-canary\|status\|health\|down> [--canary] [--pdb] [--yes] [--allow-placeholder]` | fail-closed 编排 `k8s/` 清单：旧稳定 selector 拒绝直接迁移；灰度先校验 stable/canary EndpointSlice 和 Pod UID 再创建 0% Ingress；`down` 删除含 Secret 需 `--yes` |
| `status` | 环境版本 + 注册表概览 + 各模块 target 产物存在性 |
| `help` | 命令手册 |

所有命令从仓库总根执行；直接调用 mvnd 前先在当前 shell `source scripts/build/init-build-env.sh`（`apex.sh` 自身会加载环境，子进程 `env` 不切换父 shell）。按模块构建继续使用 `-pl <叶子列表> -am`，不新增 `--group`；选 `apex-services-bom -am` 不代表构建整组服务，整组需明确列出叶子。

镜像验收采用宿主固定工具链构建后的 `prebuilt` 模式；`source` 分支仍调用系统 `mvn`，不符合当前构建约束，未整改前不得作为本阶段通过项。parent 不改变叶子 target/JAR 路径。

典型链路：

```bash
apex.sh env                 # 环境就绪
apex.sh build               # 整仓构建
apex.sh verify              # 完整门禁
apex.sh release standard    # 产出 dist/apex-scaffold-<v>-standard.zip(.sha256)

# 四种部署运行时（按需选一）
apex.sh deploy java-jar precheck apex-system-service   # java -jar 直部署
apex.sh deploy java-jar start apex-system-service dev basic
apex.sh deploy podman-compose precheck && apex.sh deploy podman-compose infra && apex.sh deploy podman-compose up
apex.sh deploy k8s precheck && apex.sh deploy k8s apply --pdb    # 先脱占位 Secret；旧拓扑按 k8s/README 分阶段迁移
apex.sh deploy k8s apply --canary                             # 发布隔离预览，完成后权重仍为 0
apex.sh deploy k8s check-canary                               # 重验端点身份和 Ingress 后再人工放量
apex.sh deploy health       # 全部发布服务 readiness（compose/java-jar 均适用）
```

## 注册表维护义务

新增/删除/改名模块，或调整交付口径、打包插件、mainClass、端口时，必须同步：

1. 本模块 `build-manifest.yaml`（包括无子模块的 parent）；
2. 所属分组 POM 的 `<modules>` 与模块 parent / `relativePath`，新增顶层组时才改根 `<modules>`；
3. `Containerfile` 的显式 POM 收集清单及相关结构测试；
4. `scripts/release/release.sh` 的 `SERVICES` 数组（服务集变化时）；
5. `AGENTS.md` 模块表与依赖表，以及受影响的当前开发和交付说明。

聚合路径相对声明 POM 解析，模块目录 = 仓库根/<分组父>/<artifactId>（两级布局由门禁锁定，注册表 artifactId 与目录名一致）。递归门禁拒绝重复聚合、环、越界、缺失 POM 与注册表缺项/多项，并核对 parent 不能误设可执行交付物。不一致时 `BuildManifestSyncTest` 失败；运行 `test` 只验证快速测试，完整验收仍需 `verify`。

安装或未来发布 Maven 构件时须包含根与所需分组 parent POM，否则外部消费者无法解析继承链；此要求不授权远程发布。客户源码包剥离卖方 `apex-license-tools` 时，须同步 tools parent 的聚合清单，不能重新打包卖方工具来满足构建。

## 设计边界

- 不搬移、不重写现有脚本；本模块只做编排与事实登记；
- 部署管理 = **java -jar / Podman Compose / K8s 三种运行时**；不含 SSH 远程部署；
  K8s 为 `k8s/` 清单的 fail-closed 编排（内嵌 Secret 占位值默认拒绝部署、`down` 需 `--yes`），不替代密钥管理；
- 不进交付发布包；`apex-tools-deployment` 则按既有发布脚本以 `tools/local-deployment.jar` 随包交付，不作为业务运行服务。
