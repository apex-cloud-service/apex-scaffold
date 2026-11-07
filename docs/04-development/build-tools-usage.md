# 构建发布管理模块使用说明（apex-tools-build）

> **适用版本**: 1.3.1 起（模块 `@since 1.3.2` 注释口径，随 1.3.1 代码基线交付）
> **模块位置**: `apex-tools-build/`
> **方案设计**: [构建发布管理模块方案](../08-project-management/plans/build-management-plan.md)
> **模块简介**: [apex-tools-build/README.md](../../apex-tools-build/README.md)

本文是 `apex-tools-build` 的完整操作手册：从环境准备到编译、测试、打包、发布，再到四种运行时的部署与故障排查，逐步给出可复制执行的命令与判定标准。命令均在**仓库根目录**执行。

---

## 目录

1. [模块是什么、解决什么问题](#1-模块是什么解决什么问题)
2. [前置条件与首次环境准备](#2-前置条件与首次环境准备)
3. [命令总览速查表](#3-命令总览速查表)
4. [流程一：日常开发（编译与测试）](#4-流程一日常开发编译与测试)
5. [流程二：打包与发布交付物](#5-流程二打包与发布交付物)
6. [流程三：镜像构建与推送](#6-流程三镜像构建与推送)
7. [流程四：部署（四种运行时）](#7-流程四部署四种运行时)
8. [流程五：完整发布上线（端到端）](#8-流程五完整发布上线端到端)
9. [模块注册表维护](#9-模块注册表维护)
10. [故障排查](#10-故障排查)
11. [设计边界与常见误解](#11-设计边界与常见误解)

---

## 1. 模块是什么、解决什么问题

### 1.1 组成

| 组成 | 路径 | 作用 |
| --- | --- | --- |
| 模块注册表 | `apex-tools-build/src/main/resources/build-manifest.yaml` | 全部模块的**单一事实来源**：分类、交付口径、端口、显式启动类 |
| 防漂移门禁 | `apex-tools-build/src/test/java/.../BuildManifestSyncTest.java` | 在 `verify` 阶段校验注册表与根 POM、各模块 POM、`release.sh` 四方一致 |
| 统一编排入口 | `apex-tools-build/bin/apex.sh` | 编译/构建/打包/发布/镜像/部署的命令行入口 |
| 模块 POM | `apex-tools-build/pom.xml` | 薄 jar，零生产依赖（仅 test scope 的 SnakeYAML/JUnit） |

### 1.2 它解决的问题

改造前，构建与部署能力分散在多个入口，且模块清单、交付口径只能靠人工核对：

| 改造前 | 改造后 |
| --- | --- |
| 需分别记住 `build.sh`、`release.sh`、`start.sh`、`stop.sh`、`pull-infra-images.sh`、`app.sh` 的用法 | 统一为 `apex.sh <子命令>`，底层仍调用这些脚本 |
| "6 个还是 7 个服务 JAR"曾出现文档与脚本口径漂移 | 注册表 + 门禁测试锁定为 7 个，改错即构建失败 |
| 部署前需手工构建 `tools/local-deployment.jar` | `deploy precheck` 自动补建 |
| 仅支持 Podman Compose | 支持 **java -jar / Podman Compose / Docker Compose / K8s** 四种运行时 |
| 无镜像构建/推送统一入口 | `image build` / `image push`，支持 podman/docker 双引擎 |

### 1.3 一个关键设计约束（务必先理解）

Maven reactor 内的模块**无法编排包含它自己的那次构建**。因此本模块分两层：

- **reactor 内**：注册表 + 一致性测试，随 `mvnd verify` 一起跑，受门禁保护；
- **reactor 外**：`bin/apex.sh` 由人/CI 调用，负责编排。

这也解释了为什么 `apex.sh` 不使用 `scripts/lib/common.sh` 的 `get_project_root`：本模块自身含 `pom.xml`，向上探测会提前命中模块目录，因此根目录由脚本位置（`SCRIPT_DIR/../..`）推算。

---

## 2. 前置条件与首次环境准备

### 2.1 硬性要求

| 项 | 要求 | 校验方式 |
| --- | --- | --- |
| JDK | SDKMAN `21.0.13.0+9-tem` | `apex.sh env` |
| 构建工具 | `mvnd 2.0.0-rc-3`（**禁止**回退系统 `mvn`） | `apex.sh env` |
| 本地仓库 | `~/.m2/repository`（**禁止** `-Dmaven.repo.local` 指向项目内目录） | — |
| Shell | `zsh`（脚本 shebang 为 zsh，macOS bash 3.2 不支持 SDKMAN 语法） | — |

版本定义见仓库根 [.sdkmanrc](../../.sdkmanrc)。

### 2.2 首次准备步骤

**第 1 步：确认 SDKMAN 与工具链**

```bash
./apex-tools-build/bin/apex.sh env
```

预期输出：

```text
✅ 已加载 sdkman 项目环境
✅ 构建环境检查通过
   Java: openjdk version "21.0.13" ...
   mvnd: Apache Maven Daemon (mvnd) 2.0.0-rc-3 ...
✓ JDK/mvnd 已按 .sdkmanrc 校验通过
```

若失败，先安装 SDKMAN 候选版本并 `sdk env`（详见 [.sdkmanrc](../../.sdkmanrc) 与 [开发规范](../04-development/development-standards.md)）。此前用过其他 JDK 时，先 `mvnd --stop` 清理 daemon 再重试。

**第 2 步：确认模块状态**

```bash
./apex-tools-build/bin/apex.sh status
```

输出包含 JDK/mvnd 版本、35 个模块的交付口径与端口、以及各模块 `target/` 产物是否已构建。首次运行全部显示"未构建"属正常。

**第 3 步（仅部署需要）：准备 `.env`**

```bash
cp .env.example .env
# 编辑 .env，至少填写以下 6 个 compose 强制变量（缺失会直接阻断容器编排）
```

| 变量 | 说明 |
| --- | --- |
| `APEX_DATA_ENCRYPTION_KEY` | 数据加密密钥 |
| `SECURITY_ENCRYPTION_KEY` | 安全模块加密密钥 |
| `CONFIG_ENCRYPTION_KEY` | 配置加密密钥 |
| `APEX_CONFIG_ENCRYPTION_KEY` | 配置加密密钥（Compose 变量名） |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符） |
| `APEX_INTERNAL_AUTH_SECRET` | 服务间内部认证密钥 |

> ⚠️ `.env` **不入版本库**。已用于持久数据的加密密钥必须稳定保存，轮换需配合迁移，不能每次启动随机重建。变量完整清单见 [配置说明](../05-operations/configuration.md) 与 [.env.example](../../.env.example)。

**第 4 步（仅容器部署需要）：确认容器引擎**

```bash
# Podman 路线
podman info && podman-compose version
# Docker 路线（二选一）
docker info && docker compose version
```

macOS 首次使用 Podman 需 `podman machine init && podman machine start`。

---

## 3. 命令总览速查表

```bash
./apex-tools-build/bin/apex.sh <子命令> [参数]
```

| 子命令 | 用途 | 典型用法 |
| --- | --- | --- |
| `env` | 校验并加载固定构建环境 | `apex.sh env` |
| `build` | 整仓或指定模块构建 | `apex.sh build -Pbasic` / `apex.sh build --module apex-common` |
| `test` | 快速测试（不含 Failsafe 门禁） | `apex.sh test --module apex-system` |
| `verify` | 完整回归门禁（约 20 分钟） | `apex.sh verify` |
| `package` | 构建 7 个发布服务 fat jar 并校验唯一性 | `apex.sh package -Penterprise` |
| `release` | 产出交付 zip + sha256 | `apex.sh release standard` |
| `image build` | 构建服务镜像（podman/docker） | `apex.sh image build --module apex-gateway` |
| `image push` | 打 registry 前缀并推送 | `apex.sh image push --module apex-gateway --registry harbor.example.com/apex` |
| `deploy java-jar` | java -jar 直部署 | `apex.sh deploy java-jar start apex-system-service dev basic` |
| `deploy podman-compose` | Podman Compose 编排 | `apex.sh deploy podman-compose up` |
| `deploy docker-compose` | Docker Compose 编排 | `apex.sh deploy docker-compose up` |
| `deploy k8s` | Kubernetes 清单编排 | `apex.sh deploy k8s apply --pdb` |
| `status` | 环境 + 注册表 + 产物概览 | `apex.sh status` |
| `help` | 命令手册 | `apex.sh help` |

**退出码约定**：成功 `0`；参数错误、前置检查失败、部署未就绪均为非零（可直接用于 CI 门禁）。

---

## 4. 流程一：日常开发（编译与测试）

### 4.1 整仓构建

```bash
./apex-tools-build/bin/apex.sh build              # 默认 standard 档
./apex-tools-build/bin/apex.sh build -Pbasic      # basic 档
./apex-tools-build/bin/apex.sh build -Penterprise # enterprise 档
```

行为：委托 `scripts/build/build.sh`，等价于 `mvnd -o clean package -P<档位> -DskipTests`（离线优先，失败自动回退在线）。

> **档位说明**：`basic` / `standard` / `enterprise` 是 **Maven 构建档位**，决定依赖裁剪与功能开关；它与 Spring 运行 profile（`dev`/`it`/`prod`）是两套配置，不能互相替代。详见 [部署指南 §3.1](../05-operations/deployment.md)。

### 4.2 按模块增量构建

```bash
./apex-tools-build/bin/apex.sh build --module apex-system-service
./apex-tools-build/bin/apex.sh build --module apex-system,apex-auth --with-tests
```

行为：`mvnd clean package -Pstandard -pl <模块列表> -am [-DskipTests]`。

> **`-am` 必带**：被依赖模块未安装到本地仓库时，仅 `-pl` 会编译失败或运行时 `NoSuchMethodError`。脚本已强制带上，请勿手工去掉。

### 4.3 测试

```bash
# 快速测试（单模块 + 其依赖链）
./apex-tools-build/bin/apex.sh test --module apex-tools-build

# 整仓快速测试
./apex-tools-build/bin/apex.sh test
```

> `test` **不包含** Failsafe 集成测试（`*IntegrationTest`、`*E2ETest`、`FlywayMigrationStartupTest`）与 Spotless/PMD/JaCoCo 门禁。需要完整门禁时用 `verify`。

### 4.4 完整回归门禁

```bash
./apex-tools-build/bin/apex.sh verify
```

等价于 `mvnd -T4 -B clean verify`，包含单元测试、Failsafe 集成测试、Spotless 格式、PMD/p3c 静态检查、JaCoCo 覆盖率（行 60% / 分支 50%）以及本模块的注册表一致性测试。耗时约 20 分钟，建议后台执行：

```bash
./apex-tools-build/bin/apex.sh verify > /tmp/verify.log 2>&1; echo "exit=$?"
```

**通过判定标准**（不能只看退出码）：

```bash
grep -c "SUCCESS \[" /tmp/verify.log                    # 反应堆条目数（35 模块 + 根聚合 = 36）
grep -E "BUILD (SUCCESS|FAILURE)" /tmp/verify.log
grep -cE "Tests run:.*Failures: [1-9]|Tests run:.*Errors: [1-9]" /tmp/verify.log   # 应为 0
```

> 约 33 个用例会因环境依赖被跳过（Seata TC、MQTT/EMQX、MQ broker、MySQL TLS、方言类），属正常，**不计为通过**。

### 4.5 提交前的最小检查

```bash
git diff --check                                        # 空白错误
source scripts/build/init-build-env.sh
mvnd -T4 -B -pl apex-tools-build spotless:check          # 本模块格式（约 2 秒）
```

---

## 5. 流程二：打包与发布交付物

### 5.1 只打包服务 JAR（不组装交付包）

```bash
./apex-tools-build/bin/apex.sh package -Pstandard
```

行为：

1. 从注册表读取 `service-jar` 集合（网关 + 六个业务服务，共 7 个），加 `apex-tools-deployment`；
2. `mvnd clean package -pl <列表> -am -DskipTests`；
3. 逐一校验每个服务 `target/` 下"**有且仅有一个**发布 JAR"（排除 `-sources`/`-javadoc`/`-test-support`/`-tests` 辅助产物），不满足即失败。

预期输出：7 行 `✓ apex-xxx-service: apex-xxx-service-1.3.1.jar`。

### 5.2 产出完整交付包

```bash
./apex-tools-build/bin/apex.sh release standard
./apex-tools-build/bin/apex.sh release enterprise 1.3.1
```

行为：

1. **前置检查**：工作区必须干净（`git status --porcelain` 为空），否则拒绝发布；如需临时豁免用 `--allow-dirty`（不推荐）；
2. 委托 `scripts/release/release.sh`：构建 7 个服务 JAR + 部署工具 → 组装 `dist/apex-scaffold-<版本>-<档位>/` → 压缩并生成校验和；
3. **收尾校验**：`dist/apex-scaffold-<版本>-<档位>.zip` 与 `.zip.sha256` 必须同时存在。

预期产物：

```text
dist/
├── apex-scaffold-1.3.1-standard.zip
└── apex-scaffold-1.3.1-standard.zip.sha256
```

**包内内容**：7 个服务 JAR、`LICENSE`/`LICENSE-COMMERCIAL.md`、`docs/`（Markdown + 图片）、`config/`（已剔除 `private.key`）、`podman-compose.yml`、`podman-compose.infra.yml`、`Containerfile`、`k8s/`、`scripts/{build,deploy,lib}`、`tools/local-deployment.jar`。

**包内禁止项**（`release.sh` 自检，命中即拒绝打包）：`config/license/private.key`、`scripts/license/`、`apex-license-tools`。

**校验和验证**：

```bash
cd dist && shasum -a 256 -c apex-scaffold-1.3.1-standard.zip.sha256
```

> 版本号必须与根 `pom.xml` 的 `<version>` 一致（`release.sh` 内部强校验），包名与内部 JAR 版本不得漂移。发布前的完整检查项见 [交付物清单 §5](../08-project-management/delivery/deliverables.md)。

---

## 6. 流程三：镜像构建与推送

### 6.1 构建镜像

```bash
# 单模块（默认 prebuilt 模式，复用宿主已构建的 fat jar）
./apex-tools-build/bin/apex.sh image build --module apex-gateway

# 全部发布服务
./apex-tools-build/bin/apex.sh image build --all

# 指定引擎与镜像引用
./apex-tools-build/bin/apex.sh image build --module apex-gateway --engine docker --tag apex-gateway:v1.3.1

# 容器内全量编译（无需宿主先 package，但更慢）
./apex-tools-build/bin/apex.sh image build --module apex-gateway --mode source
```

| 参数 | 取值 | 说明 |
| --- | --- | --- |
| `--module` | 模块 artifactId | 目标模块 |
| `--all` | — | 注册表 `service-jar` 全集 |
| `--mode` | `prebuilt`（默认）/ `source` | prebuilt 复用 `target/` 下的 fat jar；source 在容器内用 Maven 全量编译 |
| `--engine` | `podman`（默认）/ `docker` | 容器引擎 |
| `--tag` | 镜像引用 | 缺省为 `<模块>:${TAG:-latest}`（与 `podman-compose.yml` 的 `image:` 命名一致） |

> **prebuilt 模式前提**：先执行 `apex.sh package`（或 `build`），否则容器构建上下文缺少 fat jar 会失败。

### 6.2 K8s 单体镜像

K8s 清单使用 `apex-scaffold:latest`（单体聚合形态，对应 `apex-example` 模块）：

```bash
./apex-tools-build/bin/apex.sh image build --module apex-example --tag apex-scaffold
```

### 6.3 推送到镜像仓库

远程集群节点无法读取本地引擎的镜像存储，必须先推送：

```bash
./apex-tools-build/bin/apex.sh image push \
  --module apex-example --tag apex-scaffold \
  --registry harbor.example.com/apex

# 批量推送全部发布服务
./apex-tools-build/bin/apex.sh image push --all --registry harbor.example.com/apex

# 也可用环境变量提供 registry
export APEX_IMAGE_REGISTRY=harbor.example.com/apex
./apex-tools-build/bin/apex.sh image push --module apex-gateway
```

推送后需把 `k8s/deployment.yaml` 的 `image:` 改为推送到仓库的完整引用（见 [k8s/README.md](../../k8s/README.md)）。

---

## 7. 流程四：部署（四种运行时）

`deploy` 必须显式选择运行时：`apex.sh deploy <运行时> <动作> [参数]`。**没有缺省运行时**，避免误操作真实环境。

```bash
./apex-tools-build/bin/apex.sh deploy            # 无参数时打印用法（退出码 1）
```

### 7.1 运行时一：java -jar

适用：单机/虚拟机直接部署单个服务，无需容器；由 `scripts/deploy/service/app.sh` 承载守护、优雅停机与 readiness 轮询。

**第 1 步：前置检查**

```bash
./apex-tools-build/bin/apex.sh deploy java-jar precheck apex-system-service
```

校验 `java` 可用 + 模块 `target/` 下存在可执行 JAR（排除辅助 classifier）。缺失时先 `apex.sh build --module <模块>`。

**第 2 步：启动**

```bash
# 语法：deploy java-jar <动作> [模块] [环境] [档位]
./apex-tools-build/bin/apex.sh deploy java-jar start apex-system-service dev basic
```

实际激活的 Spring profile 为 `<环境>,<档位>`（如 `dev,basic`）。模块缺省为 `apex-gateway`。

**第 3 步：确认状态**

```bash
./apex-tools-build/bin/apex.sh deploy java-jar status apex-system-service
./apex-tools-build/bin/apex.sh deploy java-jar health apex-system-service   # 单模块 readiness
./apex-tools-build/bin/apex.sh deploy java-jar logs apex-system-service
```

**第 4 步：停止/重启**

```bash
./apex-tools-build/bin/apex.sh deploy java-jar restart apex-system-service prod enterprise
./apex-tools-build/bin/apex.sh deploy java-jar stop apex-system-service
```

前台调试（Ctrl+C 停止）：`deploy java-jar console apex-gateway dev basic`。

**业务变量必须注入**（否则启动即失败）：`DB_MASTER_URL/USERNAME/PASSWORD`、`DB_SLAVE_*`（prod）、`REDIS_HOST/PORT/PASSWORD`（prod 强制非空）。可用 `APEX_ENV_FILE=/path/to/env` 指定变量文件，脚本会在计算默认值**之前** source 它。

**默认端口**：

| 模块 | 业务端口 | 管理端口 |
| --- | --- | --- |
| apex-gateway | 8080 | 8080 |
| apex-auth-service | 8081 | 8081 |
| apex-system-service | 8082 | **8083** |
| apex-workflow-service | 8093 | 8093 |
| apex-office-service | 8084 | 8084 |
| apex-file-service | 8085 | 8085 |
| apex-integration-service | 8094 | **8097** |
| apex-job-service | 8095 | 8095 |
| apex-masking-service | 8096 | 8096 |

> ⚠️ **同机冲突**：workflow 业务端口 8083 与 system 管理端口 8083 冲突。同机部署时用 `SERVER_PORT`/`WORKFLOW_PORT` 调整（Compose 已用 8093 规避）。

### 7.2 运行时二：Podman Compose

**第 1 步：前置检查**

```bash
./apex-tools-build/bin/apex.sh deploy podman-compose precheck
```

检查：podman 守护可达、根目录 `.env` 存在、`tools/local-deployment.jar` 存在（缺失时**自动**执行 `mvnd -pl apex-tools-deployment -am package -DskipTests` 并复制到 `tools/`）。

**第 2 步：启动基础设施**

```bash
./apex-tools-build/bin/apex.sh deploy podman-compose infra
```

行为：`pull-infra-images.sh` 拉取 9 个基础设施镜像（失败重试 4 次）→ `podman-compose -f podman-compose.infra.yml up -d`。

> 基础设施栈创建 `apex-observability` 外部网络，主栈依赖该网络，**必须先启动基础设施**。

**第 3 步：启动主栈**

```bash
./apex-tools-build/bin/apex.sh deploy podman-compose up
```

委托 `scripts/deploy/start.sh`（`podman-compose -f podman-compose.yml up -d --build`），并注入优雅停机治理变量（`APEX_GOVERNANCE_SHUTDOWN_TIMEOUT=90s`、`APEX_STOP_GRACE_PERIOD=105s`）。参数透传，如启用可选 profile：

```bash
./apex-tools-build/bin/apex.sh deploy podman-compose up --profile rocketmq
```

可用 profile：`masking`、`xxl-job`、`seata`、`keycloak`、`rocketmq`。

**第 4 步：确认状态与健康**

```bash
./apex-tools-build/bin/apex.sh deploy podman-compose status
./apex-tools-build/bin/apex.sh deploy podman-compose health
```

`health` 依次探测 7 个发布服务的 `/actuator/health/readiness`，任一未就绪即退出码 1。**端口取注册表**：workflow 探测宿主 8093（容器内 8083 映射而来），system 探测 8083，integration 探测 8097。

**第 5 步：停止**

```bash
./apex-tools-build/bin/apex.sh deploy podman-compose down
```

委托 `scripts/deploy/stop.sh`（`down --timeout 105`，覆盖 90 秒优雅停机预算）。

### 7.3 运行时三：Docker Compose

与 Podman Compose **使用同一份 compose 文件**（已核对兼容：仅用到 `network_mode: none` 与 `read_only: true`，Docker 均支持；服务已显式声明 `dockerfile: Containerfile`）。命令形式完全对称：

```bash
./apex-tools-build/bin/apex.sh deploy docker-compose precheck
./apex-tools-build/bin/apex.sh deploy docker-compose infra
./apex-tools-build/bin/apex.sh deploy docker-compose up
./apex-tools-build/bin/apex.sh deploy docker-compose status
./apex-tools-build/bin/apex.sh deploy docker-compose health
./apex-tools-build/bin/apex.sh deploy docker-compose down
```

**与 Podman 路线的差异**（脚本已内置，无需手工处理）：

| 项 | Podman | Docker |
| --- | --- | --- |
| Compose 命令解析 | `podman-compose` | `docker compose`（v2 插件）优先，回退 `docker-compose` |
| 基础设施拉镜像 | `pull-infra-images.sh`（podman 专属） | `docker compose -f podman-compose.infra.yml pull` |
| `up`/`down` 实现 | 委托 `start.sh`/`stop.sh` | 脚本内注入相同的优雅停机治理变量后直接执行 compose |

### 7.4 运行时四：Kubernetes

清单位于 `k8s/`：`deployment.yaml`（Namespace/Secret/Deployment/Service/Ingress/HPA）、`canary-deployment.yaml`（金丝雀）、`pod-disruption-budget.yaml`（PDB）。详见 [k8s/README.md](../../k8s/README.md)。

**第 1 步：前置检查（fail-closed）**

```bash
./apex-tools-build/bin/apex.sh deploy k8s precheck
```

检查项：

1. `kubectl` 存在；
2. 集群可达（`kubectl get ns --request-timeout=10s`）；
3. `k8s/deployment.yaml` 存在；
4. **Secret 占位值扫描**：清单中若仍含 `changeme` / `change-this-to-a-secure`，**默认拒绝部署**。

**第 2 步：修改 Secret 与域名（必须人工完成）**

编辑 `k8s/deployment.yaml`：

- `Secret.stringData` 中 `DB_MASTER_PASSWORD`、`DB_SLAVE_PASSWORD`、`REDIS_PASSWORD`、`JWT_SECRET` 必改；
- `Ingress` 的 `host` 改为实际域名，`tls.secretName` 指向真实证书；
- 集群外数据库/Redis 时，把 `REDIS_HOST` 等改为实际 Service/DNS。

> 仅演示环境可临时用 `--allow-placeholder` 豁免占位检查，**严禁用于生产**。

**第 3 步：构建并推送镜像**

```bash
./apex-tools-build/bin/apex.sh image build --module apex-example --tag apex-scaffold
./apex-tools-build/bin/apex.sh image push --module apex-example --tag apex-scaffold --registry <仓库地址>
```

然后把 `deployment.yaml` 的 `image: apex-scaffold:latest` 改为推送后的完整引用。

**第 4 步：部署**

```bash
./apex-tools-build/bin/apex.sh deploy k8s apply            # 基础清单
./apex-tools-build/bin/apex.sh deploy k8s apply --pdb      # 同时应用 PDB
./apex-tools-build/bin/apex.sh deploy k8s apply --canary   # 同时应用金丝雀（先改 canary 镜像）
```

`apply` 会依次应用清单，并执行 `kubectl rollout status deployment/apex-scaffold --timeout=300s` 等待滚动更新完成。

**第 5 步：确认状态**

```bash
./apex-tools-build/bin/apex.sh deploy k8s status   # deploy/pods/svc/ingress/hpa
./apex-tools-build/bin/apex.sh deploy k8s health   # rollout status + pods
```

**第 6 步：下线（高风险，需显式确认）**

```bash
./apex-tools-build/bin/apex.sh deploy k8s down --yes
```

> ⚠️ `down` 会删除已应用的对象，**包括 `apex-secrets`**。不加 `--yes` 会被拒绝（退出码 1）。按需追加 `--pdb`/`--canary` 一并删除。

**灰度发布流程**（10% → 50% → 100%）见 [k8s/README.md](../../k8s/README.md)。

---

## 8. 流程五：完整发布上线（端到端）

以「standard 档 → Docker Compose 部署到预发环境」为例：

```bash
# ① 环境就绪
./apex-tools-build/bin/apex.sh env

# ② 完整门禁（必须先通过，约 20 分钟）
./apex-tools-build/bin/apex.sh verify

# ③ 提交并确认工作区干净（release 会强校验）
git status --porcelain      # 应为空

# ④ 产出交付包 + 校验和
./apex-tools-build/bin/apex.sh release standard
cd dist && shasum -a 256 -c apex-scaffold-1.3.1-standard.zip.sha256 && cd ..

# ⑤ 镜像构建与推送
./apex-tools-build/bin/apex.sh image build --all --engine docker --tag ""   # 或逐个 --module
./apex-tools-build/bin/apex.sh image push --all --engine docker --registry harbor.example.com/apex

# ⑥ 部署前置检查
./apex-tools-build/bin/apex.sh deploy docker-compose precheck

# ⑦ 基础设施 → 主栈
./apex-tools-build/bin/apex.sh deploy docker-compose infra
./apex-tools-build/bin/apex.sh deploy docker-compose up

# ⑧ 健康验证（失败即退出码 1，可直接作为发布门禁）
./apex-tools-build/bin/apex.sh deploy docker-compose health

# ⑨ 查看状态
./apex-tools-build/bin/apex.sh deploy docker-compose status
```

K8s 路线把 ⑥⑦⑧ 替换为 `deploy k8s precheck → apply → health`。

**回滚要点**：

| 运行时 | 回滚方式 |
| --- | --- |
| java -jar | 用上一版本 JAR 重新 `deploy java-jar restart` |
| Compose | `git checkout` 上一版本 compose/镜像 tag 后 `up -d` |
| K8s | `kubectl -n apex-scaffold rollout undo deployment/apex-scaffold` |

---

## 9. 模块注册表维护

注册表 `build-manifest.yaml` 是单一事实来源，**新增/删除/改名模块时必须同步六处**，否则门禁失败或口径漂移：

1. 根 `pom.xml` 的 `<modules>`；
2. `apex-tools-build/src/main/resources/build-manifest.yaml`（保持与根 POM 相同的声明顺序）；
3. 模块自身 `pom.xml` 的打包插件与显式 `mainClass`（如适用）；
4. `scripts/release/release.sh` 的 `SERVICES` 数组（仅发布服务集合变化时）；
5. `AGENTS.md` 的模块表与依赖表；
6. `docs/08-project-management/delivery/delivery-scope.md` §2（交付口径变化时）。

### 9.1 字段说明

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `artifactId` | 是 | 模块坐标，必须与根 POM `<modules>` 一致 |
| `category` | 是 | 职责分类（口径同 `AGENTS.md`） |
| `deliverable` | 是 | `none` / `service-jar` / `optional` / `tool-jar`，与打包插件双向绑定 |
| `port` | 否 | 容器内/进程内默认业务端口 |
| `managementPort` | 否 | 默认管理端口（缺省同 `port`） |
| `hostPort` | 否 | **仅 Compose**：宿主侧映射端口（缺省同 `managementPort`）；当前仅 workflow 登记 8093 |
| `mainClass` | 否 | 仅登记 POM **显式声明**的启动类（当前仅 system-service、example） |

### 9.2 格式契约（重要）

`bin/apex.sh` 用 **awk 行式解析**该文件，必须保持：

```yaml
modules:
  - artifactId: <id>        # 记录行：两空格缩进 + "- "
    category: <分类>         # 字段行：四空格缩进
    deliverable: <口径>
```

- 注释行（`#` 开头）对 snakeyaml 与 awk 均不可见，可自由增删；
- **修改缩进/键名/列表语法不会触发 Java 测试失败，但会静默破坏 shell 解析**——调整结构时必须同步 `apex.sh` 的 `manifest_service_modules` / `manifest_field`。

### 9.3 验证修改

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B -pl apex-tools-build -am test     # 四方一致性（4 个测试）
./apex-tools-build/bin/apex.sh status          # 渲染验证
```

---

## 10. 故障排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `command not found: mvnd` | 未加载 SDKMAN 环境 | `./apex-tools-build/bin/apex.sh env` 或 `source scripts/build/init-build-env.sh` |
| `JDK 版本不符合要求` | `java/current` 链接漂移 | `sdk env`；必要时 `mvnd --stop` 后重试 |
| `BUILD FAILURE` 且报 `mainClass 在 POM 与注册表之间不一致` | 改了 POM 未同步注册表 | 按 §9 同步，或改回 POM |
| `BUILD FAILURE` 且报 `根 POM modules 与注册表不一致` | 新增模块只改了 POM | 同步注册表条目 |
| `BUILD FAILURE` 且报 `SERVICES 数组与注册表 service-jar 集合不一致` | 改了 `release.sh` 服务集合 | 同步注册表 `deliverable` |
| `release` 报"工作区存在未提交变更" | 发布基线不干净 | 提交后重试；临时豁免 `--allow-dirty`（不推荐） |
| `release` 报版本不一致 | 显式版本 ≠ POM 版本 | 省略版本号（自动读 POM）或对齐 POM |
| `deploy ... precheck` 报"缺少根目录 .env" | 未准备环境变量 | `cp .env.example .env` 并填 6 个强制变量 |
| `deploy ... precheck` 报"podman 守护不可达" | Podman 虚拟机未启动 | `podman machine start` |
| `deploy k8s precheck` 报"集群不可达" | kubeconfig 未配置/集群未起 | 检查 `kubectl config current-context` |
| `deploy k8s precheck` 报"Secret 仍为占位值" | 清单未改 | 按 §7.4 第 2 步修改，或演示环境加 `--allow-placeholder` |
| `deploy k8s down` 被拒绝 | 缺少确认 | 追加 `--yes` |
| `deploy ... health` 报 workflow 8093 未就绪 | workflow 未启动或端口被改 | 检查 `WORKFLOW_PORT` 与容器状态 |
| `image build` 失败且提示缺少 JAR | prebuilt 模式下未先打包 | 先执行 `apex.sh package`，或用 `--mode source` |
| `image build` 在 `FROM ...` 处超时 | 基础镜像拉取网络问题 | 预先 `podman pull`/`docker pull` 基础镜像后重试 |
| `deploy java-jar start` 立即退出 | 业务变量缺失（DB/Redis/JWT） | 检查 `APEX_ENV_FILE` 与必需变量 |

**通用排查顺序**：

```bash
./apex-tools-build/bin/apex.sh status     # 环境与产物
./apex-tools-build/bin/apex.sh deploy <运行时> precheck   # 部署前置
# 查看日志
./apex-tools-build/bin/apex.sh deploy java-jar logs <模块>
podman-compose -f podman-compose.yml logs -f apex-gateway
kubectl -n apex-scaffold logs -l app=apex-scaffold --tail=100
```

---

## 11. 设计边界与常见误解

### 11.1 明确不做的事

| 边界 | 说明 |
| --- | --- |
| 不搬移/不重写现有脚本 | `build.sh`、`release.sh`、`app.sh`、`start.sh`、`stop.sh` 保持原样作为底层执行器 |
| 不含 SSH 远程部署 | 所有部署都是本机/本地集群执行；远程主机需自行分发产物 |
| K8s 不替代密钥管理 | Secret 修改、Ingress 域名/TLS 仍由部署者按 `k8s/README.md` 维护 |
| 不进交付发布包 | 与 `apex-tools-deployment` 同口径，交付包内不含本模块 |
| 不做镜像仓库认证 | `image push` 只执行 `push`，登录（`podman login`/`docker login`）需提前完成 |

### 11.2 常见误解

**❌ "`apex.sh build` 通过了就等于代码没问题"**
→ `build` 默认 `-DskipTests`。发布前必须 `apex.sh verify`（完整门禁）。

**❌ "Maven 档位 `standard` 就是运行环境"**
→ 档位（`basic`/`standard`/`enterprise`）与 Spring profile（`dev`/`it`/`prod`）是两套独立配置，部署时两者都要指定。

**❌ "改了注册表就生效了"**
→ 注册表在 reactor 内受门禁保护，改完必须跑 `apex.sh test --module apex-tools-build` 验证一致性；`status` 可验证渲染。

**❌ "`deploy` 会自己选运行时"**
→ 必须显式指定 `java-jar`/`podman-compose`/`docker-compose`/`k8s`，无参数直接退出码 1。

**❌ "K8s 清单可以直接 apply 到生产"**
→ `deployment.yaml` 内嵌 Secret 为占位值，`precheck` 会 fail-closed 拒绝；必须先改 Secret 与域名。

---

## 附录 A：文件清单

```text
apex-tools-build/
├── pom.xml                                      # 薄 jar，零生产依赖
├── README.md                                    # 模块简介与命令手册
├── bin/
│   └── apex.sh                                  # 统一编排入口（zsh）
└── src/
    ├── main/resources/
    │   └── build-manifest.yaml                  # 模块注册表（单一事实来源）
    └── test/java/com/apex/scaffold/tools/build/
        └── BuildManifestSyncTest.java           # 四方一致性门禁
```

## 附录 B：相关文档

| 主题 | 文档 |
| --- | --- |
| 方案设计与决策记录 | [build-management-plan.md](../08-project-management/plans/build-management-plan.md) |
| 部署指南（手工流程、systemd、K8s 细节） | [deployment.md](../05-operations/deployment.md) |
| 配置项与环境变量 | [configuration.md](../05-operations/configuration.md) |
| 开发规范（构建、注释、JavaDoc） | [development-standards.md](../04-development/development-standards.md) |
| 交付物清单与校验 | [deliverables.md](../08-project-management/delivery/deliverables.md) |
| 交付范围与模块口径 | [delivery-scope.md](../08-project-management/delivery/delivery-scope.md) |
| K8s 部署资产与灰度流程 | [k8s/README.md](../../k8s/README.md) |

---

**维护者**: Apex Team
**最后核对**: 2026-11-06（对应 `apex-tools-build` 四运行时改造与注释规范化完成时点）
