# apex-tools-build — 构建发布管理模块

对全部模块的**编译、构建、打包、发布、镜像、部署**提供统一管理与单一入口。方案见
[构建发布管理模块方案](../docs/08-project-management/plans/build-management-plan.md)，
完整操作手册见 [构建发布管理模块使用说明](../docs/04-development/build-tools-usage.md)。

## 组成

| 内容 | 说明 |
| --- | --- |
| `src/main/resources/build-manifest.yaml` | 35 模块注册表（单一事实来源）：分类、交付口径、端口、mainClass |
| `src/test/java/.../BuildManifestSyncTest.java` | 防漂移门禁：注册表 vs 根 POM `<modules>` vs 各模块打包插件/mainClass vs `release.sh SERVICES` |
| `bin/apex.sh` | 生命周期统一编排入口（只做编排，构建/发布/部署能力由 `scripts/` 既有脚本与 mvnd 承载） |

模块为薄 jar：零生产依赖（仅 test scope 的 snakeyaml/JUnit），不参与服务运行期装配，不进交付发布包。

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
| `package [-P profile]` | 构建 7 个发布服务 fat jar（+ 部署工具）并逐一校验"有且仅有一个发布 JAR" |
| `release [basic\|standard\|enterprise] [版本] [--allow-dirty]` | 前置检查（工作区干净）→ 委托 `scripts/release/release.sh` → 校验 `.zip`/`.zip.sha256` 生成 |
| `image build [--module m\|--all] [--mode prebuilt\|source] [--engine podman\|docker] [--tag 引用]` | 构建服务镜像，默认镜像名=模块 artifactId；`--tag` 指定完整引用（如 `apex-scaffold`） |
| `image push --registry host[:port] [--module m\|--all] [--engine podman\|docker] [--tag 引用]` | 打 registry 前缀并推送（k8s 远程集群交付需要；可走 `APEX_IMAGE_REGISTRY`） |
| `deploy java-jar <动作> [模块] [dev\|it\|prod] [档位]` | `precheck/start/stop/restart/status/logs/console/health`，委托 `scripts/deploy/service/app.sh`；模块默认 apex-gateway |
| `deploy podman-compose <precheck\|infra\|up\|down\|status\|health> [透传]` | 前置检查（podman/.env/工具 JAR 自动补建）；up/down 委托 `start.sh`/`stop.sh` |
| `deploy docker-compose <precheck\|infra\|up\|down\|status\|health> [透传]` | 同一 compose 文件双引擎；基础设施镜像用 `compose pull`；up 注入与 start.sh 相同的优雅停机治理变量 |
| `deploy k8s <precheck\|apply\|status\|health\|down> [--canary] [--pdb] [--yes] [--allow-placeholder]` | fail-closed 编排 `k8s/` 清单：Secret 占位值默认拒绝部署；`down` 删除含 Secret 需 `--yes`；`apply` 后等待 rollout status |
| `status` | 环境版本 + 注册表概览 + 各模块 target 产物存在性 |
| `help` | 命令手册 |

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
apex.sh deploy docker-compose precheck && apex.sh deploy docker-compose infra && apex.sh deploy docker-compose up
apex.sh deploy k8s precheck && apex.sh deploy k8s apply --pdb    # 先脱占位 Secret
apex.sh deploy health       # 全部发布服务 readiness（compose/java-jar 均适用）
```

## 注册表维护义务

新增/删除/改名模块，或调整交付口径、打包插件、mainClass、端口时，必须同步：

1. 本模块 `build-manifest.yaml`；
2. 根 `pom.xml` `<modules>`；
3. `scripts/release/release.sh` 的 `SERVICES` 数组（服务集变化时）；
4. `AGENTS.md` 模块表与依赖表。

不一致时 `BuildManifestSyncTest` 在 verify 门禁失败。

## 设计边界

- 不搬移、不重写现有脚本；本模块只做编排与事实登记；
- 部署管理 = **java -jar / Podman Compose / Docker Compose / K8s 四种运行时**；不含 SSH 远程部署；
  K8s 为 `k8s/` 清单的 fail-closed 编排（内嵌 Secret 占位值默认拒绝部署、`down` 需 `--yes`），不替代密钥管理；
- 不进交付发布包（与 `apex-tools-deployment` 同口径）。
