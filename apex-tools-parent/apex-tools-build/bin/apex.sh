#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 生命周期统一编排入口（apex-tools-build）
# ============================================================
# 用法: ./apex-tools-parent/apex-tools-build/bin/apex.sh <子命令> [参数]
# 子命令: env | build | test | verify | package | release | image | deploy | status | help
#
# 设计约定:
#   - 本脚本只做编排：构建/发布/部署能力由 scripts/ 既有脚本与 mvnd 承载，不维护第二套逻辑；
#     既有脚本（build.sh/release.sh/app.sh/start.sh/stop.sh）保持原样作为底层执行器
#   - 部署支持四种运行时：java-jar（委托 app.sh）、podman-compose（委托 start.sh/stop.sh）、
#     docker-compose（同一 compose 文件，新路径）、k8s（fail-closed 编排 k8s/ 清单）；
#     运行时以显式参数选择：deploy <运行时> <动作> [参数]
#   - 模块清单/交付口径/端口以 build-manifest.yaml 为准（BuildManifestSyncTest 在 verify
#     门禁中锁定其与根 POM、各模块 POM、release.sh SERVICES 的四方一致）
#   - 新增 Bash 脚本规范：复用 scripts/lib/common.sh 的日志/校验函数，kebab-case，
#     失败显式退出（set -euo pipefail），敏感值不写日志
#   - 不使用 common.sh 的 get_project_root：本模块所在分组父目录自身含 pom.xml，向上探测
#     会提前命中；根目录以仓库根独有的 .mvn/ 构建配置目录为标记向上探测，与嵌套层级解耦
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，与 build.sh/release.sh 一致
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
# 第二阶段起本脚本位于 apex-tools-parent/apex-tools-build/bin/，固定层数与 pom.xml 探测
# 都会被分组父目录提前命中；以仓库根独有的 .mvn/ 为标记向上探测。
PROJECT_ROOT=""
_root_probe="${SCRIPT_DIR}"
while [[ "${_root_probe}" != "/" ]]; do
  if [[ -d "${_root_probe}/.mvn" ]]; then
    PROJECT_ROOT="${_root_probe}"
    break
  fi
  _root_probe="$(dirname "${_root_probe}")"
done
if [[ -z "${PROJECT_ROOT}" ]]; then
  print -u2 "[ERROR] 无法定位仓库根（向上未找到 .mvn/ 标记）"
  exit 1
fi
# common.sh 在 zsh 下 source 安全：BASH_SOURCE 仅出现在本脚本不调用的函数内部（get_script_dir/get_project_root）
source "${PROJECT_ROOT}/scripts/lib/common.sh"

MANIFEST="${PROJECT_ROOT}/apex-tools-parent/apex-tools-build/src/main/resources/build-manifest.yaml"
BUILD_ENV="${PROJECT_ROOT}/scripts/build/init-build-env.sh"
TOOL_JAR="${PROJECT_ROOT}/tools/local-deployment.jar"
COMPOSE_APP="${PROJECT_ROOT}/podman-compose.yml"
COMPOSE_INFRA="${PROJECT_ROOT}/podman-compose.infra.yml"
K8S_DIR="${PROJECT_ROOT}/k8s"
K8S_NAMESPACE="apex-scaffold"

# 启动前置：注册表与环境脚本必须存在（缺失即失败，不静默降级）
require_file "${MANIFEST}" "模块注册表 build-manifest.yaml"
require_file "${BUILD_ENV}" "构建环境初始化脚本 init-build-env.sh"

cd "${PROJECT_ROOT}"

# ============================================================
# 内部工具
# ============================================================

# ------------------------------------------------------------
# 函数: init_build_env
# 用途: 在当前 shell 加载固定构建环境（SDKMAN + .sdkmanrc）。
#       init-build-env.sh 内部强校验 JDK 21.0.13.0+9-tem 与 mvnd 2.0.0-rc-3，
#       不满足即退出，保证所有 mvnd 子命令都在受控工具链下执行。
# 侧效: 修改当前 shell 的 JAVA_HOME/PATH；校验失败直接终止脚本。
# 调用方: cmd_env/cmd_build/cmd_test/cmd_verify/cmd_package、precheck_common
# ------------------------------------------------------------
init_build_env() {
  source "${BUILD_ENV}"
}

# ------------------------------------------------------------
# 函数: pom_version
# 用途: 读取发布版本（根 POM <version>）。
# 实现: 与 release.sh 同一 awk 口径——定位 artifactId=apex-scaffold 后的 version，
#       跳过 <parent> 中 Spring Boot 的版本，避免误读。
# 返回: 版本字符串（stdout）
# 调用方: cmd_release（省略版本号时）/ image_build / image_push
# ------------------------------------------------------------
pom_version() {
  awk '/<artifactId>apex-scaffold<\/artifactId>/{getline; if ($0 ~ /<version>/) {gsub(/.*<version>|<\/version>.*/, ""); print; exit}}' \
    "${PROJECT_ROOT}/pom.xml"
}

# ------------------------------------------------------------
# 函数: validate_profile <profile>
# 用途: 校验构建档位取值；非法值直接终止（不静默回退默认档）。
# 取值: basic | standard | enterprise（与根 POM Maven profile 对应）
# ------------------------------------------------------------
validate_profile() {
  case "$1" in
    basic|standard|enterprise) ;;
    *) log_error "不支持的构建档位: $1（仅 basic/standard/enterprise）"; exit 1 ;;
  esac
}

# ------------------------------------------------------------
# 函数: manifest_service_modules
# 用途: 从注册表提取交付口径为 service-jar 的模块列表（即发布服务集合，
#       与 release.sh SERVICES 数组等价，集合一致性由 BuildManifestSyncTest 锁定）。
# 输出: 每行一个 artifactId（stdout）。
# 实现: awk 状态机按行解析——"- artifactId:" 起始记录，随后 deliverable 行触发输出；
#       格式契约见 build-manifest.yaml 头注（缩进/键名不得变更）。
# 调用方: cmd_package / image_build / image_push / deploy_health
# ------------------------------------------------------------
manifest_service_modules() {
  awk '
    /^  - artifactId:/ {
      id = $0
      sub(/^  - artifactId:[[:space:]]*/, "", id)
      sub(/[[:space:]]+$/, "", id)
    }
    /^    deliverable:[[:space:]]*service-jar/ { print id }
  ' "${MANIFEST}"
}

# ------------------------------------------------------------
# 函数: manifest_field <artifactId> <字段名>
# 用途: 读取注册表中指定模块的指定字段（port/managementPort/deliverable 等）。
# 输出: 字段值（stdout）；字段不存在时输出空串，调用方自行决定缺省语义。
# 实现: awk 状态机——先按记录行命中 artifactId，再在该记录内匹配首个字段行；
#       动态正则仅拼接字段名（字母，无正则元字符），无注入风险。
# 调用方: jar_health / deploy_health / cmd_status（间接，经内联 awk）
# ------------------------------------------------------------
manifest_field() {
  awk -v id="$1" -v field="$2" '
    /^  - artifactId:/ {
      cur = $0
      sub(/^  - artifactId:[[:space:]]*/, "", cur)
      sub(/[[:space:]]+$/, "", cur)
      hit = (cur == id)
    }
    hit && $0 ~ "^    " field ":" {
      v = $0
      sub("^    " field ":[[:space:]]*", "", v)
      sub(/[[:space:]]+$/, "", v)
      print v
      exit
    }
  ' "${MANIFEST}"
}

# ------------------------------------------------------------
# 函数: mvnd_offline_first <mvnd 参数...>
# 用途: 离线优先执行 mvnd（-o），失败自动回退在线——首次构建或本地仓库缺依赖时
#       仍可完成，同时日常构建不因网络而变慢。MAVEN_OPTS 统一 1G 堆。
# 侧效: 执行 mvnd；两段都失败时按 mvnd 退出码终止（set -e）。
# 调用方: cmd_build / cmd_package / precheck_common
# ------------------------------------------------------------
mvnd_offline_first() {
  log_info "mvnd $*"
  MAVEN_OPTS="-Xmx1024m" mvnd -o "$@" || MAVEN_OPTS="-Xmx1024m" mvnd "$@"
}

# ------------------------------------------------------------
# 函数: colonize_ids <逗号分隔 artifactId 列表>
# 用途: 把裸 artifactId 列表转为 :artifactId 形式——mvnd -pl 的非冒号值按"相对路径"
#       解析，第二阶段叶子嵌套到分组父目录后裸路径不再命中；冒号形式按坐标匹配，
#       与物理目录层级解耦（已含冒号的项原样保留，幂等）。
# 调用方: cmd_build / cmd_test / cmd_package
# ------------------------------------------------------------
colonize_ids() {
  local -a parts out
  parts=("${(@s:,,:)1}")
  local p
  out=()
  for p in "${parts[@]}"; do
    if [[ "${p}" == :* ]]; then
      out+=("${p}")
    else
      out+=(":${p}")
    fi
  done
  print -r -- "${(j:,:)out}"
}

# ============================================================
# 子命令: env —— 构建环境校验
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_env
# 用途: 校验并展示固定构建环境（JDK/mvnd 版本与 .sdkmanrc 一致性）。
#       任何构建/测试动作前建议先执行，快速暴露工具链漂移。
# ------------------------------------------------------------
cmd_env() {
  print_header "Apex Scaffold 构建环境"
  init_build_env
  log_success "JDK/mvnd 已按 .sdkmanrc 校验通过"
  log_info "JDK: $(java -version 2>&1 | sed -n '1p')"
  log_info "mvnd: $(mvnd --version | sed -n '1p')"
}

# ============================================================
# 子命令: build / test / verify —— 编译与测试门禁
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_build [-P 档位] [--module a,b] [--with-tests]
# 用途: 整仓构建（无 --module 时 exec scripts/build/build.sh，保持其既有行为）；
#       指定 --module 时按模块构建：mvnd clean package -pl :<列表> -am（冒号按坐标匹配）。
# 约定: -am 必带——被依赖模块未安装到本地仓库时，仅 -pl 会编译失败（项目既有教训）；
#       默认 -DskipTests，--with-tests 显式开启测试。
# ------------------------------------------------------------
cmd_build() {
  local profile="-Pstandard" modules="" with_tests=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -P*) profile="$1"; shift ;;
      --module) modules="$2"; shift 2 ;;
      --module=*) modules="${1#--module=}"; shift ;;
      --with-tests) with_tests=1; shift ;;
      *) log_error "未知参数: $1"; exit 1 ;;
    esac
  done
  validate_profile "${profile#-P}"
  if [[ -z "${modules}" ]]; then
    exec "${PROJECT_ROOT}/scripts/build/build.sh" "${profile}"
  fi
  init_build_env
  local -a args
  args=(clean package "${profile}" -pl "$(colonize_ids "${modules}")" -am)
  if [[ ${with_tests} -eq 0 ]]; then
    args+=(-DskipTests)
  fi
  # -am 必带：被依赖模块未安装到本地仓库时，仅 -pl 会失败
  mvnd_offline_first "${args[@]}"
}

# ------------------------------------------------------------
# 函数: cmd_test [--module m]
# 用途: 快速测试（不含 Failsafe 集成门禁）。
#       指定模块时 mvnd -pl :m -am test（冒号按坐标匹配，连带其上游依赖模块）；
#       缺省整仓 mvnd -T4 -B test。完整门禁请用 verify 子命令。
# ------------------------------------------------------------
cmd_test() {
  local module=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --module) module="$2"; shift 2 ;;
      --module=*) module="${1#--module=}"; shift ;;
      *) log_error "未知参数: $1"; exit 1 ;;
    esac
  done
  init_build_env
  if [[ -n "${module}" ]]; then
    log_info "模块测试（-am 连带依赖模块）"
    MAVEN_OPTS="-Xmx1024m" mvnd -T4 -B -pl "$(colonize_ids "${module}")" -am test
  else
    log_info "整仓测试"
    MAVEN_OPTS="-Xmx1024m" mvnd -T4 -B test
  fi
}

# ------------------------------------------------------------
# 函数: cmd_verify
# 用途: 完整回归门禁 mvnd -T4 -B clean verify——包含单元测试、Failsafe 集成测试
#       （*IntegrationTest/*E2ETest/FlywayMigrationStartupTest）、Spotless、PMD/p3c、
#       JaCoCo 覆盖率门禁与本模块的注册表一致性测试，耗时约 20 分钟。
# ------------------------------------------------------------
cmd_verify() {
  init_build_env
  log_info "完整回归门禁（含 Spotless/PMD/JaCoCo/Failsafe，约 20 分钟）"
  MAVEN_OPTS="-Xmx1024m" mvnd -T4 -B clean verify
}

# ============================================================
# 子命令: package —— 发布 JAR 构建与唯一性校验
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_package [-P 档位]
# 用途: 构建 8 个发布服务 fat jar + 部署工具（apex-tools-deployment），
#       并逐一校验每个服务 target 下"有且仅有一个发布 JAR"。
# 口径: 模块列表取注册表 service-jar 集合；构建参数与 release.sh 阶段 1 一致
#       （-am 连带依赖、-DskipTests）；唯一性校验排除 -sources/-javadoc/
#       -test-support/-tests 辅助产物（apex-gateway 会额外产出 test-support classifier）。
# 与 release 的关系: package 只产 JAR 不组装 dist；完整交付包走 release 子命令。
# ------------------------------------------------------------
cmd_package() {
  local profile="-Pstandard"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -P*) profile="$1"; shift ;;
      *) log_error "未知参数: $1"; exit 1 ;;
    esac
  done
  validate_profile "${profile#-P}"
  init_build_env
  local -a services
  services=("${(f)$(manifest_service_modules)}")
  local service_list="$(colonize_ids "${(j:,:)services},apex-tools-deployment")"
  # 口径与 release.sh 阶段 1 一致：发布服务 + 部署工具（-pl 冒号按坐标匹配），连带依赖，跳过测试
  mvnd_offline_first clean package "${profile}" -pl "${service_list}" -am -DskipTests
  # 逐一校验"有且仅有一个发布 JAR"（排除 sources/javadoc/test-support/tests 辅助产物）
  local svc f svc_dir
  for svc in "${services[@]}"; do
    svc_dir="$(apex_module_dir "${svc}")" || { log_error "无法定位模块目录: ${svc}"; exit 1; }
    local -a jars
    jars=()
    for f in "${svc_dir}"/target/"${svc}"-*.jar(N); do
      case "${f}" in
        *-sources.jar|*-javadoc.jar|*-test-support.jar|*-tests.jar) continue ;;
      esac
      jars+=("${f}")
    done
    if (( ${#jars[@]} != 1 )); then
      log_error "${svc} 应有且仅有一个发布 JAR，实际 ${#jars[@]} 个"
      exit 1
    fi
    log_success "${svc}: ${jars[1]}"
  done
}

# ============================================================
# 子命令: release —— 交付包产出
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_release [basic|standard|enterprise] [版本] [--allow-dirty]
# 用途: 发布前置检查后委托 scripts/release/release.sh 产出 dist/*.zip(.sha256)。
# 前置: 工作区必须干净（发布基线可复现）；--allow-dirty 显式豁免（不推荐）；
#       版本与根 POM 的一致性由 release.sh 内部强校验，本函数不重复实现。
# 收尾: 校验 zip 与 zip.sha256 均已生成（release.sh 阶段 3 产物）。
# ------------------------------------------------------------
cmd_release() {
  local profile="${1:-standard}"
  if [[ $# -gt 0 ]]; then
    shift
  fi
  local allow_dirty=0 version=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --allow-dirty) allow_dirty=1; shift ;;
      *) version="$1"; shift ;;
    esac
  done
  validate_profile "${profile}"
  if [[ ${allow_dirty} -eq 0 && -n "$(git status --porcelain)" ]]; then
    log_error "工作区存在未提交变更；发布基线必须可复现。先提交或清理，或显式传入 --allow-dirty 豁免（不推荐）"
    exit 1
  fi
  local -a release_args
  release_args=("${profile}")
  if [[ -n "${version}" ]]; then
    release_args+=("${version}")
  fi
  log_info "委托 scripts/release/release.sh 产出 ${profile} 发布包"
  "${PROJECT_ROOT}/scripts/release/release.sh" "${release_args[@]}"
  local v="${version}"
  if [[ -z "${v}" ]]; then
    v="$(pom_version)"
  fi
  require_file "${PROJECT_ROOT}/dist/apex-scaffold-${v}-${profile}.zip" "发布包"
  require_file "${PROJECT_ROOT}/dist/apex-scaffold-${v}-${profile}.zip.sha256" "发布包校验和"
  log_success "发布包与校验和齐备: dist/apex-scaffold-${v}-${profile}.zip"
}

# ============================================================
# 子命令: image —— 服务镜像构建与推送（podman/docker 双引擎）
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_image build|push [参数]
# 用途: image 子命令分发。
#   build: 构建服务镜像（详见 image_build）
#   push:  打 registry 前缀并推送（详见 image_push，k8s 远程集群交付需要）
# ------------------------------------------------------------
cmd_image() {
  local sub="${1:-}"
  case "${sub}" in
    build) shift; image_build "$@" ;;
    push) shift; image_push "$@" ;;
    *)
      log_error "用法: apex.sh image <build|push> [参数]"
      exit 1
      ;;
  esac
}

# ------------------------------------------------------------
# 函数: resolve_container_cli <podman|docker>
# 用途: 解析容器引擎到全局数组 CONTAINER_CMD（后续统一 "${CONTAINER_CMD[@]}" 调用），
#       缺失即失败并提示安装（不静默回退引擎）。
# 侧效: 写全局 CONTAINER_CMD。
# ------------------------------------------------------------
CONTAINER_CMD=()
resolve_container_cli() {
  case "$1" in
    podman)
      require_command podman "Podman"
      CONTAINER_CMD=(podman)
      ;;
    docker)
      require_command docker "Docker"
      CONTAINER_CMD=(docker)
      ;;
    *)
      log_error "不支持的容器引擎: $1（仅 podman/docker）"
      exit 1
      ;;
  esac
}

# ------------------------------------------------------------
# 函数: image_ref <模块名> [--tag 覆盖值]
# 用途: 计算目标镜像引用。
# 规则: --tag 优先（含 ":" 视为完整引用；不含则追加 ${TAG:-latest} 版本段）；
#       未指定时镜像名 = 模块 artifactId + ":${TAG:-latest}"，与 podman-compose.yml
#       的 image: 命名一致（如 apex-gateway:latest）。
# 特例: k8s 清单使用 apex-scaffold:latest（单体聚合形态，SERVICE_MODULE=apex-example），
#       经 --tag apex-scaffold 指定。
# 输出: 完整镜像引用（stdout）
# ------------------------------------------------------------
image_ref() {
  local module="$1" tag_override="$2"
  if [[ -n "${tag_override}" ]]; then
    case "${tag_override}" in
      *:*) printf '%s\n' "${tag_override}" ;;
      *) printf '%s\n' "${tag_override}:${TAG:-latest}" ;;
    esac
  else
    printf '%s\n' "${module}:${TAG:-latest}"
  fi
}

# ------------------------------------------------------------
# 函数: image_build [--module m|--all] [--mode prebuilt|source]
#                   [--engine podman|docker] [--tag 引用]
# 用途: 经根目录 Containerfile 构建服务镜像（一镜像一服务）。
# 注入: SERVICE_MODULE（目标模块）、MODULE_PATH（仓库根相对的模块物理目录，第二阶段
#       嵌套布局）、BUILD_MODE（prebuilt 复用宿主已构建 fat jar / source 容器内全量编译）、
#       BUILD_VERSION（读根 POM）。
# 约定: --all 目标为注册表 service-jar 全集；默认引擎 podman。
# ------------------------------------------------------------
image_build() {
  local module="" mode="prebuilt" use_all=0 engine="podman" tag_override=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --module) module="$2"; shift 2 ;;
      --module=*) module="${1#--module=}"; shift ;;
      --all) use_all=1; shift ;;
      --mode) mode="$2"; shift 2 ;;
      --mode=*) mode="${1#--mode=}"; shift ;;
      --engine) engine="$2"; shift 2 ;;
      --engine=*) engine="${1#--engine=}"; shift ;;
      --tag) tag_override="$2"; shift 2 ;;
      --tag=*) tag_override="${1#--tag=}"; shift ;;
      *) log_error "未知参数: $1"; exit 1 ;;
    esac
  done
  case "${mode}" in
    prebuilt|source) ;;
    *) log_error "不支持的镜像构建模式: ${mode}（仅 prebuilt/source）"; exit 1 ;;
  esac
  resolve_container_cli "${engine}"
  local -a targets
  targets=()
  if [[ ${use_all} -eq 1 ]]; then
    targets=("${(f)$(manifest_service_modules)}")
  elif [[ -n "${module}" ]]; then
    targets=("${module}")
  fi
  if (( ${#targets[@]} == 0 )); then
    log_error "需指定 --module <模块名> 或 --all"
    exit 1
  fi
  local version="$(pom_version)" m ref m_dir
  for m in "${targets[@]}"; do
    m_dir="$(apex_module_dir "${m}")" || { log_error "无法定位模块目录: ${m}"; exit 1; }
    ref="$(image_ref "${m}" "${tag_override}")"
    log_info "构建镜像 ${ref}（${engine}，模式 ${mode}，版本 ${version}）"
    "${CONTAINER_CMD[@]}" build -f "${PROJECT_ROOT}/Containerfile" \
      --build-arg "SERVICE_MODULE=${m}" \
      --build-arg "MODULE_PATH=${m_dir#"${PROJECT_ROOT}"/}" \
      --build-arg "BUILD_MODE=${mode}" \
      --build-arg "BUILD_VERSION=${version}" \
      -t "${ref}" \
      "${PROJECT_ROOT}"
  done
}

# ------------------------------------------------------------
# 函数: image_push --registry <host[:port]> [--module m|--all]
#                   [--engine podman|docker] [--tag 引用]
# 用途: 为本地镜像打 registry 前缀并推送——k8s 远程集群交付的前置步骤
#       （节点无法访问本地引擎的镜像存储）。
# 约定: registry 可经 --registry 或环境变量 APEX_IMAGE_REGISTRY 提供；
#       引用已含 "/"（自带 registry 前缀）时不再拼接。
# ------------------------------------------------------------
image_push() {
  local module="" use_all=0 engine="podman" tag_override="" registry="${APEX_IMAGE_REGISTRY:-}"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --module) module="$2"; shift 2 ;;
      --module=*) module="${1#--module=}"; shift ;;
      --all) use_all=1; shift ;;
      --engine) engine="$2"; shift 2 ;;
      --engine=*) engine="${1#--engine=}"; shift ;;
      --tag) tag_override="$2"; shift 2 ;;
      --tag=*) tag_override="${1#--tag=}"; shift ;;
      --registry) registry="$2"; shift 2 ;;
      --registry=*) registry="${1#--registry=}"; shift ;;
      *) log_error "未知参数: $1"; exit 1 ;;
    esac
  done
  if [[ -z "${registry}" ]]; then
    log_error "push 需 --registry <host[:port]>（或环境变量 APEX_IMAGE_REGISTRY）"
    exit 1
  fi
  resolve_container_cli "${engine}"
  local -a targets
  targets=()
  if [[ ${use_all} -eq 1 ]]; then
    targets=("${(f)$(manifest_service_modules)}")
  elif [[ -n "${module}" ]]; then
    targets=("${module}")
  fi
  if (( ${#targets[@]} == 0 )); then
    log_error "需指定 --module <模块名> 或 --all"
    exit 1
  fi
  local m local_ref full
  for m in "${targets[@]}"; do
    local_ref="$(image_ref "${m}" "${tag_override}")"
    case "${local_ref}" in
      */*) full="${local_ref}" ;;  # 引用已含 registry 前缀，不再拼接
      *) full="${registry}/${local_ref}" ;;
    esac
    if [[ "${full}" != "${local_ref}" ]]; then
      "${CONTAINER_CMD[@]}" tag "${local_ref}" "${full}"
    fi
    log_info "推送镜像 ${full}"
    "${CONTAINER_CMD[@]}" push "${full}"
  done
}

# ============================================================
# 子命令: deploy —— 四运行时部署编排
#   java-jar / podman-compose / docker-compose / k8s，详见 deploy_usage
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_deploy <运行时> <动作> [参数]
# 用途: deploy 子命令分发；运行时必须显式选择（无缺省运行时，避免误操作真实环境）。
# 分发: java-jar → deploy_jar；podman-compose/docker-compose → deploy_compose podman|docker；
#       k8s → deploy_k8s；未知运行时打印用法并以非零码退出。
# ------------------------------------------------------------
cmd_deploy() {
  local runtime="${1:-}"
  if [[ -z "${runtime}" ]]; then
    deploy_usage
    exit 1
  fi
  shift
  case "${runtime}" in
    java-jar) deploy_jar "$@" ;;
    podman-compose) deploy_compose podman "$@" ;;
    docker-compose) deploy_compose docker "$@" ;;
    k8s) deploy_k8s "$@" ;;
    *)
      log_error "未知部署运行时: ${runtime}（支持 java-jar / podman-compose / docker-compose / k8s）"
      deploy_usage
      exit 1
      ;;
  esac
}

# ------------------------------------------------------------
# 函数: deploy_usage
# 用途: 打印四运行时的动作清单与参数格式（deploy 无参数/未知运行时时输出）。
# ------------------------------------------------------------
deploy_usage() {
  cat <<'EOF'
部署运行时用法: ./apex-tools-parent/apex-tools-build/bin/apex.sh deploy <运行时> <动作> [参数]

  java-jar        precheck|start|stop|restart|status|logs|console|health
                  格式: deploy java-jar <动作> [模块] [dev|it|prod] [basic|standard|enterprise]
                  （委托 scripts/deploy/service/app.sh；模块默认 apex-gateway）
  podman-compose  precheck|infra|up|down|status|health [参数透传]
  docker-compose  precheck|infra|up|down|status|health [参数透传]
                  （同一 compose 文件双引擎；docker 基础设施镜像用 compose pull）
  k8s             precheck|apply|status|health|down [--canary] [--pdb] [--yes] [--allow-placeholder]
                  （Secret 占位值 fail-closed；down 删除含 Secret，需 --yes）
EOF
}

# ------------------------------------------------------------
# 函数: precheck_common
# 用途: Compose 两引擎共用的部署前置：根目录 .env 存在性（Compose 以 ${VAR:?}
#       强制注入密钥，缺失即拒绝启动）+ tools/local-deployment.jar 存在性。
# 自动补建: 工具 JAR 缺失时执行 mvnd -pl :apex-tools-deployment -am package -DskipTests
#       并复制到 tools/——修复"security-key-check 依赖 JAR 无编排"的既有缺口，
#       首次 Compose 部署不再需要手工构建。
# ------------------------------------------------------------
precheck_common() {
  if [[ ! -f "${PROJECT_ROOT}/.env" ]]; then
    log_error "缺少根目录 .env（Compose 以 \${VAR:?} 强制注入密钥）。执行: cp .env.example .env 并填写全部强制变量"
    exit 1
  fi
  log_success ".env 存在"
  if [[ ! -f "${TOOL_JAR}" ]]; then
    log_warn "缺少 ${TOOL_JAR}：自动构建 apex-tools-deployment 并补齐（Compose security-key-check 依赖）"
    init_build_env
    mvnd_offline_first clean package -pl :apex-tools-deployment -am -DskipTests
    mkdir -p "${PROJECT_ROOT}/tools"
    cp "$(apex_module_dir apex-tools-deployment)/target/local-deployment.jar" "${TOOL_JAR}"
  fi
  require_file "${TOOL_JAR}" "部署校验工具 local-deployment.jar"
}

# ------------------------------------------------------------
# 函数: deploy_jar <动作> [模块] [dev|it|prod] [basic|standard|enterprise]
# 用途: 运行时一——java -jar 直部署，全部动作透传 scripts/deploy/service/app.sh
#       （start/stop/restart/status/logs/console；守护、优雅停机、readiness 轮询
#       均由 app.sh 承载，本函数只注入 APEX_SERVICE_MODULE 选模块）。
# precheck: 校验 java 可用 + 模块可执行 JAR 存在（排除辅助 classifier，
#           与发布口径一致），缺失时提示 build --module。
# health:   jar_health 单模块 readiness（java -jar 模式只起单个服务，
#           不适用"8 服务全量 readiness"的 deploy_health）。
# 模块缺省: apex-gateway（与 app.sh 的默认一致）。
# ------------------------------------------------------------
deploy_jar() {
  local action="${1:-}"
  if [[ -z "${action}" ]]; then
    deploy_usage
    exit 1
  fi
  shift
  case "${action}" in
    precheck)
      local module="${1:-apex-gateway}"
      print_header "部署前置检查（java -jar，模块 ${module}）"
      require_command java "Java"
      local module_dir f
      module_dir="$(apex_module_dir "${module}")" || { log_error "无法定位模块目录: ${module}"; exit 1; }
      local -a release_jars
      release_jars=()
      for f in "${module_dir}"/target/"${module}"-*.jar(N); do
        case "${f}" in
          *-sources.jar|*-javadoc.jar|*-test-support.jar|*-tests.jar) continue ;;
        esac
        release_jars+=("${f}")
      done
      if (( ${#release_jars[@]} == 0 )); then
        log_error "未找到 ${module} 的可执行 JAR；先执行: apex.sh build --module ${module}"
        exit 1
      fi
      log_success "可执行 JAR: ${release_jars[1]}"
      log_success "前置通过；启动: apex.sh deploy java-jar start ${module} dev basic"
      ;;
    health)
      jar_health "${1:-apex-gateway}"
      ;;
    start|stop|restart|status|logs|console)
      local module="${1:-apex-gateway}"
      if [[ $# -gt 0 ]]; then
        shift
      fi
      log_info "委托 scripts/deploy/service/app.sh（模块 ${module}，动作 ${action}，参数透传）"
      export APEX_SERVICE_MODULE="${module}"
      exec bash "${PROJECT_ROOT}/scripts/deploy/service/app.sh" "${action}" "$@"
      ;;
    *)
      log_error "未知 java-jar 动作: ${action}（支持 precheck/start/stop/restart/status/logs/console/health）"
      exit 1
      ;;
  esac
}

# ------------------------------------------------------------
# 函数: jar_health <模块>
# 用途: 单模块 readiness 探测（java -jar 运行时专用）。
# 端口: 业务端口默认即管理端口；system/integration 用注册表 managementPort。
# 输出: HTTP 200 视为就绪；否则输出实际状态码（000=连接失败）并退出码 1。
# ------------------------------------------------------------
jar_health() {
  local module="$1"
  local port mport code
  port="$(manifest_field "${module}" port)"
  if [[ -z "${port}" ]]; then
    log_error "模块 ${module} 未在注册表登记端口（非可运行模块？）"
    exit 1
  fi
  mport="$(manifest_field "${module}" managementPort)"
  if [[ -z "${mport}" ]]; then
    mport="${port}"
  fi
  # curl 失败（连接拒绝/超时）时 -w 仍会输出 000，故用 || true 吞退出码、防空串拼接
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:${mport}/actuator/health/readiness" 2>/dev/null || true)"
  if [[ "${code}" == "200" ]]; then
    log_success "${module}: 端口 ${mport} 就绪"
  else
    log_failure "${module}: 端口 ${mport} 未就绪（HTTP ${code:-000}）"
    exit 1
  fi
}

# ------------------------------------------------------------
# 函数: resolve_compose_cli <podman|docker>
# 用途: 解析 Compose 引擎到全局数组 COMPOSE_CMD。
#   podman: podman-compose 单命令；
#   docker: docker compose v2 插件优先，回退 docker-compose v1 独立二进制。
# 兼容性: podman-compose.yml 与 docker compose 兼容（仅 network_mode:none/read_only，
#   均受支持；服务已显式 dockerfile: Containerfile）。
# 侧效: 写全局 COMPOSE_CMD。
# ------------------------------------------------------------
COMPOSE_CMD=()
resolve_compose_cli() {
  case "$1" in
    podman)
      require_command podman "Podman"
      require_command podman-compose "podman-compose"
      COMPOSE_CMD=(podman-compose)
      ;;
    docker)
      require_command docker "Docker"
      if docker compose version >/dev/null 2>&1; then
        COMPOSE_CMD=(docker compose)
      elif command -v docker-compose >/dev/null 2>&1; then
        COMPOSE_CMD=(docker-compose)
      else
        log_error "未找到 docker compose 插件或 docker-compose 独立二进制"
        exit 1
      fi
      ;;
    *)
      log_error "不支持的 Compose 引擎: $1"
      exit 1
      ;;
  esac
}

# ------------------------------------------------------------
# 函数: deploy_compose <podman|docker> <precheck|infra|up|down|status|health> [透传]
# 用途: 运行时二/三——同一 compose 文件的双引擎编排。
# precheck: 守护进程可达性（podman info / docker info）+ precheck_common。
# infra:    拉取基础设施镜像并启动 podman-compose.infra.yml；
#           docker 引擎因 pull-infra-images.sh 为 podman 专属改用 compose pull。
# up/down:  podman 引擎委托既有 start.sh/stop.sh（内置优雅停机治理变量，行为不变）；
#           docker 引擎在脚本内注入与 start.sh/stop.sh 相同的治理变量后执行
#           compose up -d --build / compose down（新路径，无既有脚本可委托）。
# health:   deploy_health 7 服务全量 readiness（compose 发布了主机端口，两引擎通用）。
# ------------------------------------------------------------
deploy_compose() {
  local engine="$1"
  shift
  local action="${1:-}"
  if [[ -z "${action}" ]]; then
    deploy_usage
    exit 1
  fi
  shift
  resolve_compose_cli "${engine}"
  case "${action}" in
    precheck)
      print_header "部署前置检查（${engine}-compose）"
      if [[ "${engine}" == "podman" ]]; then
        podman info >/dev/null 2>&1 || { log_error "podman 守护不可达（先 podman machine start）"; exit 1; }
      else
        docker info >/dev/null 2>&1 || { log_error "docker 守护不可达（检查 Docker Desktop/daemon）"; exit 1; }
      fi
      log_success "容器守护进程可达"
      precheck_common
      log_success "前置检查通过；可继续: apex.sh deploy ${engine}-compose infra → apex.sh deploy ${engine}-compose up"
      ;;
    infra)
      if [[ "${engine}" == "podman" ]]; then
        log_info "拉取基础设施镜像（pull-infra-images.sh，失败自动重试）"
        bash "${PROJECT_ROOT}/scripts/deploy/podman/pull-infra-images.sh"
      else
        log_info "拉取基础设施镜像（compose pull）"
        "${COMPOSE_CMD[@]}" -f "${COMPOSE_INFRA}" pull
      fi
      log_info "启动基础设施栈（podman-compose.infra.yml）"
      "${COMPOSE_CMD[@]}" -f "${COMPOSE_INFRA}" up -d
      log_success "基础设施栈（${engine}）已启动；主栈执行: apex.sh deploy ${engine}-compose up"
      ;;
    up|down|status)
      if [[ "${engine}" == "podman" ]]; then
        case "${action}" in
          up)
            log_info "委托 scripts/deploy/start.sh（参数透传）"
            exec "${PROJECT_ROOT}/scripts/deploy/start.sh" "$@"
            ;;
          down)
            log_info "委托 scripts/deploy/stop.sh（参数透传）"
            exec "${PROJECT_ROOT}/scripts/deploy/stop.sh" "$@"
            ;;
          status) "${COMPOSE_CMD[@]}" -f "${COMPOSE_APP}" ps ;;
        esac
      else
        # 与 start.sh/stop.sh 相同的优雅停机治理变量
        export APEX_GOVERNANCE_DEPLOYMENT_MODE="${APEX_GOVERNANCE_DEPLOYMENT_MODE:-compose}"
        export APEX_GOVERNANCE_SHUTDOWN_TIMEOUT="${APEX_GOVERNANCE_SHUTDOWN_TIMEOUT:-90s}"
        export APEX_STOP_GRACE_PERIOD="${APEX_STOP_GRACE_PERIOD:-105s}"
        case "${action}" in
          up) "${COMPOSE_CMD[@]}" -f "${COMPOSE_APP}" up -d --build "$@" ;;
          down) "${COMPOSE_CMD[@]}" -f "${COMPOSE_APP}" down "$@" ;;
          status) "${COMPOSE_CMD[@]}" -f "${COMPOSE_APP}" ps ;;
        esac
      fi
      ;;
    health) deploy_health ;;
    *)
      log_error "未知 ${engine}-compose 动作: ${action}（支持 precheck/infra/up/down/status/health）"
      exit 1
      ;;
  esac
}

# ------------------------------------------------------------
# 函数: deploy_k8s <precheck|apply|status|health|down> [--canary] [--pdb]
#                  [--yes] [--allow-placeholder]
# 用途: 运行时四——k8s/ 静态清单的 fail-closed 编排。
# 清单: deployment.yaml（Namespace/Secret/Deployment/Service/Ingress/HPA，单体形态，
#       镜像 apex-scaffold:latest）；--pdb 追加 PodDisruptionBudget；
#       --canary 追加金丝雀清单（镜像需部署者先按 k8s/README 修改）。
# fail-closed 三条线:
#   1. precheck 强校验集群可达（kubectl get ns 超时即失败）；
#   2. precheck 扫描 deployment.yaml 内嵌 Secret 的占位值（changeme/change-this），
#      默认拒绝部署——防止把模板密钥打进集群；--allow-placeholder 显式豁免（仅限演示）；
#   3. down 需 --yes 确认（会删除 Namespace 内对象，含 apex-secrets）。
# apply: 依 deployment.yaml → [--pdb] → [--canary] 顺序应用，
#        最后 rollout status --timeout=300s 等待滚动更新完成。
# 边界: 不替代密钥管理（Secret 修改/Ingress 域名/TLS 仍由部署者按 k8s/README 维护），
#       不做 SSH 远程、不做镜像推送自动化（见 image push）。
# ------------------------------------------------------------
deploy_k8s() {
  local action="${1:-}"
  if [[ -z "${action}" ]]; then
    deploy_usage
    exit 1
  fi
  shift
  local canary=0 with_pdb=0 confirmed=0 allow_placeholder=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --canary) canary=1; shift ;;
      --pdb) with_pdb=1; shift ;;
      --yes) confirmed=1; shift ;;
      --allow-placeholder) allow_placeholder=1; shift ;;
      *) log_error "未知参数: $1"; exit 1 ;;
    esac
  done
  require_command kubectl "kubectl"
  case "${action}" in
    precheck) k8s_precheck "${allow_placeholder}" ;;
    apply)
      k8s_precheck "${allow_placeholder}"
      log_info "应用基础清单（Namespace/Secret/Deployment/Service/Ingress/HPA）"
      kubectl apply -f "${K8S_DIR}/deployment.yaml"
      if [[ ${with_pdb} -eq 1 ]]; then
        log_info "应用 PodDisruptionBudget"
        kubectl apply -f "${K8S_DIR}/pod-disruption-budget.yaml"
      fi
      if [[ ${canary} -eq 1 ]]; then
        log_info "应用金丝雀清单（先确认 canary-deployment.yaml 的镜像已更新）"
        kubectl apply -f "${K8S_DIR}/canary-deployment.yaml"
      fi
      log_info "等待滚动更新完成（超时 300s）"
      kubectl -n "${K8S_NAMESPACE}" rollout status deployment/apex-scaffold --timeout=300s
      log_success "K8s 部署完成；状态: apex.sh deploy k8s status"
      ;;
    status)
      kubectl -n "${K8S_NAMESPACE}" get deploy,pods,svc,ingress,hpa -o wide
      ;;
    health)
      kubectl -n "${K8S_NAMESPACE}" rollout status deployment/apex-scaffold --timeout=60s
      kubectl -n "${K8S_NAMESPACE}" get pods
      ;;
    down)
      if [[ ${confirmed} -ne 1 ]]; then
        log_error "k8s down 将删除 ${K8S_NAMESPACE} 命名空间内已应用对象（含 apex-secrets）；确认请加 --yes"
        exit 1
      fi
      kubectl delete -f "${K8S_DIR}/deployment.yaml" --ignore-not-found
      if [[ ${with_pdb} -eq 1 ]]; then
        kubectl delete -f "${K8S_DIR}/pod-disruption-budget.yaml" --ignore-not-found
      fi
      if [[ ${canary} -eq 1 ]]; then
        kubectl delete -f "${K8S_DIR}/canary-deployment.yaml" --ignore-not-found
      fi
      log_success "K8s 资源已删除"
      ;;
    *)
      log_error "未知 k8s 动作: ${action}（支持 precheck/apply/status/health/down）"
      exit 1
      ;;
  esac
}

# ------------------------------------------------------------
# 函数: k8s_precheck <allow_placeholder:0|1>
# 用途: k8s 部署前置检查（apply 前自动执行，也可单独运行）。
# 检查项: kubectl 存在 → 集群可达 → 清单存在 → Secret 占位扫描。
# 提示项: 镜像获取路径（apex-scaffold:latest 的构建/推送命令），
#         远程集群节点无法直接读取本地引擎镜像存储。
# ------------------------------------------------------------
k8s_precheck() {
  local allow_placeholder="$1"
  print_header "K8s 部署前置检查"
  if ! kubectl get ns --request-timeout=10s >/dev/null 2>&1; then
    log_error "Kubernetes 集群不可达（kubectl get ns 超时/失败）；检查 kubeconfig"
    exit 1
  fi
  log_success "集群可达"
  require_file "${K8S_DIR}/deployment.yaml" "K8s 部署清单"
  # Secret 占位值 fail-closed：防止把模板密钥打进集群
  if grep -qE "changeme|change-this-to-a-secure" "${K8S_DIR}/deployment.yaml"; then
    if [[ "${allow_placeholder}" != "1" ]]; then
      log_error "deployment.yaml 内嵌 Secret 仍为占位值（DB_MASTER_PASSWORD/DB_SLAVE_PASSWORD/REDIS_PASSWORD/JWT_SECRET）"
      log_error "先编辑 ${K8S_DIR}/deployment.yaml 的 Secret 段，或用 --allow-placeholder 显式豁免（仅限演示环境）"
      exit 1
    fi
    log_warn "Secret 仍为占位值（--allow-placeholder 豁免；严禁用于生产）"
  else
    log_success "Secret 已脱占位"
  fi
  log_info "镜像: 清单使用 apex-scaffold:latest（单体聚合形态，SERVICE_MODULE=apex-example 构建）；确保节点可获取："
  log_info "  apex.sh image build --module apex-example --tag apex-scaffold --engine <podman|docker>"
  log_info "  远程集群需先推送: apex.sh image push --module apex-example --tag apex-scaffold --registry <host[:port]>"
  log_success "K8s 前置检查通过；部署: apex.sh deploy k8s apply [--pdb] [--canary]"
}

# ------------------------------------------------------------
# 函数: deploy_health
# 用途: 8 个发布服务（注册表 service-jar 全集）的 readiness 聚合探测。
# 适配: compose 双引擎（发布主机端口）与整仓检查通用；java -jar 模式请用
#       jar_health 单模块探测。
# 端口: 优先取注册表 hostPort（Compose 宿主侧映射），缺省回退 managementPort，
#       再回退 port。2026-11-07 起 workflow 业务端口为 8093（与 system 管理端口
#       8083 不再冲突），注册表暂无 hostPort 登记，探测走 port；
#       如某服务宿主映射与容器端口不同，在注册表登记 hostPort 即可。
#       optional 模块（job/masking）不在 service-jar 集合内，不做默认探测
#       （其 readiness 路径特殊，见 app.sh）。
# 退出: 任一服务未就绪 → 退出码 1（CI 可直接作为部署后门禁）。
# ------------------------------------------------------------
deploy_health() {
  # 探测端口优先级：hostPort（Compose 宿主映射）> managementPort > port
  local failed=0 svc port mport hport code
  for svc in $(manifest_service_modules); do
    port="$(manifest_field "${svc}" port)"
    mport="$(manifest_field "${svc}" managementPort)"
    hport="$(manifest_field "${svc}" hostPort)"
    if [[ -z "${mport}" ]]; then
      mport="${port}"
    fi
    if [[ -z "${hport}" ]]; then
      hport="${mport}"
    fi
    # curl 失败时 -w 仍输出 000，用 || true 吞退出码，避免空串/重复拼接
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:${hport}/actuator/health/readiness" 2>/dev/null || true)"
    if [[ "${code}" == "200" ]]; then
      log_success "${svc}: 端口 ${hport} 就绪"
    else
      log_failure "${svc}: 端口 ${hport} 未就绪（HTTP ${code:-000}）"
      failed=1
    fi
  done
  if [[ ${failed} -eq 1 ]]; then
    exit 1
  fi
  log_success "全部发布服务 readiness 通过"
}

# ============================================================
# 子命令: status —— 注册表与产物概览
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_status
# 用途: 展示构建环境版本 + 模块注册表概览（分类/交付口径/端口）+
#       各模块 target 产物存在性（交付口径非 none 的模块计数可执行 JAR）。
# 只读: 不执行任何构建；java/mvnd 不可用时输出告警而非失败（环境未初始化是常态）。
# ------------------------------------------------------------
cmd_status() {
  print_header "Apex Scaffold 构建状态"
  if command -v java >/dev/null 2>&1; then
    log_info "JDK: $(java -version 2>&1 | sed -n '1p')"
  else
    log_warn "java 不可用：先执行 apex.sh env（加载 init-build-env.sh）"
  fi
  if command -v mvnd >/dev/null 2>&1; then
    log_info "mvnd: $(mvnd --version | sed -n '1p')"
  else
    log_warn "mvnd 不可用：先执行 apex.sh env"
  fi
  print_section "模块注册表（交付口径 → target 产物）"
  local line id deliverable port host_port id_dir
  local -a jars
  # awk 输出 "artifactId|deliverable|port|hostPort" 管道分隔行；zsh (N) glob 缺省不报错，
  # 无产物时数组为空，据此渲染"未构建"
  while IFS='|' read -r id deliverable port host_port; do
    case "${deliverable}" in
      none)
        printf '  %-40s %-12s 端口 %-6s 不产出可执行 JAR\n' "${id}" "${deliverable}" "${port}"
        ;;
      *)
        # 端口展示：容器内端口，Compose 宿主映射不同时并列显示（注册表 hostPort）
        if [[ -n "${host_port}" && "${host_port}" != "-" ]]; then
          port="${port}(宿主 ${host_port})"
        fi
        id_dir="$(apex_module_dir "${id}" 2>/dev/null || true)"
        jars=()
        if [[ -n "${id_dir}" ]]; then
          jars=("${id_dir}"/target/"${id}"-*.jar(N))
        fi
        if (( ${#jars[@]} > 0 )); then
          printf '  %-40s %-12s 端口 %-6s 已构建 %d 个 JAR\n' "${id}" "${deliverable}" "${port}" "${#jars[@]}"
        else
          printf '  %-40s %-12s 端口 %-6s \033[33m未构建\033[0m\n' "${id}" "${deliverable}" "${port}"
        fi
        ;;
    esac
  done < <(awk '
    function flush() { if (id != "") print id "|" deliverable "|" port "|" host_port }
    /^  - artifactId:/ {
      flush()
      id = $0
      sub(/^  - artifactId:[[:space:]]*/, "", id)
      sub(/[[:space:]]+$/, "", id)
      deliverable = ""
      port = "-"
      host_port = "-"
    }
    /^    deliverable:/ { deliverable = $0; sub(/^    deliverable:[[:space:]]*/, "", deliverable) }
    /^    port:/       { port = $0; sub(/^    port:[[:space:]]*/, "", port) }
    /^    hostPort:/   { host_port = $0; sub(/^    hostPort:[[:space:]]*/, "", host_port) }
    END { flush() }
  ' "${MANIFEST}")
}

# ============================================================
# 子命令: help —— 命令手册
# ============================================================

# ------------------------------------------------------------
# 函数: cmd_help
# 用途: 输出全部子命令与参数格式（help/--help/-h 或未知命令时）。
# ------------------------------------------------------------
cmd_help() {
  cat <<'EOF'
Apex Scaffold 生命周期统一编排入口（apex-tools-build）

用法: ./apex-tools-parent/apex-tools-build/bin/apex.sh <子命令> [参数]

子命令:
  env                     校验并加载固定构建环境（SDKMAN + JDK + mvnd）
  build [-P 档位] [--module a,b] [--with-tests]
                          整仓构建（委托 build.sh）；--module 时 mvnd -pl 列表 -am
  test [--module m]       快速测试（默认整仓 mvnd -T4 -B test）
  verify                  完整回归门禁 mvnd -T4 -B clean verify
  package [-P 档位]       构建 8 个发布服务 fat jar 并逐一校验唯一性
  release [档位] [版本] [--allow-dirty]
                          前置检查后委托 release.sh 产出 dist/*.zip(.sha256)
  image build [--module m|--all] [--mode prebuilt|source] [--engine podman|docker] [--tag 引用]
                          构建服务镜像（默认镜像名=模块 artifactId；--tag 指定完整引用）
  image push --registry <host[:port]> [--module m|--all] [--engine podman|docker] [--tag 引用]
                          打 registry 前缀并推送（k8s 远程集群交付需要）
  deploy java-jar <动作> [模块] [dev|it|prod] [档位]
                          动作: precheck|start|stop|restart|status|logs|console|health
                          （委托 scripts/deploy/service/app.sh；模块默认 apex-gateway）
  deploy podman-compose <precheck|infra|up|down|status|health> [透传]
  deploy docker-compose <precheck|infra|up|down|status|health> [透传]
                          同一 compose 文件双引擎；up 透传 compose profile（如 --profile rocketmq）
  deploy k8s <precheck|apply|status|health|down> [--canary] [--pdb] [--yes] [--allow-placeholder]
                          fail-closed 编排 k8s/ 清单；Secret 占位值默认拒绝部署
  status                  环境版本 + 模块注册表/产物概览
  help                    本帮助

档位: basic | standard | enterprise（默认 standard）
部署运行时: java-jar | podman-compose | docker-compose | k8s
EOF
}

# ============================================================
# 入口分发
# ============================================================

# 说明: 未知命令先报错再打帮助（退出码 1）；help/--help/-h 视为合法命令（退出码 0）
ACTION="${1:-help}"
if [[ $# -gt 0 ]]; then
  shift
fi
case "${ACTION}" in
  env) cmd_env ;;
  build) cmd_build "$@" ;;
  test) cmd_test "$@" ;;
  verify) cmd_verify ;;
  package) cmd_package "$@" ;;
  release) cmd_release "$@" ;;
  image) cmd_image "$@" ;;
  deploy) cmd_deploy "$@" ;;
  status) cmd_status ;;
  help|--help|-h) cmd_help ;;
  *)
    log_error "未知命令: ${ACTION}"
    cmd_help
    exit 1
    ;;
esac
