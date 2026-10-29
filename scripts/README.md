# Apex Scaffold Scripts 脚本索引

本目录包含 Apex Scaffold 项目的所有自动化脚本，按功能分类组织。

**目录结构版本**: v2.0 (2026-09-20 重组)  
**组织原则**: 按功能分类，路径简短，职责单一

---

## 📁 目录结构总览

```
scripts/
├── dev/              # 开发辅助工具
├── test/             # 测试相关
├── deploy/           # 部署相关
├── ops/              # 运维相关
├── data/             # 数据相关
├── ci/               # CI/CD
├── utils/            # 通用工具
├── bench/            # 性能测试
├── init/             # 初始化
├── keycloak/         # Keycloak
├── license/          # 授权
├── office/           # Office 集成
├── performance/      # 性能测试
└── archive/          # 历史一次性脚本归档
```

> **兼容入口说明**：`scripts/` 根目录与 `scripts/deploy/` 下保留的部分同名脚本（如 `batch-add-javadoc.sh`、`deploy-monitoring.sh`）是历史路径兼容转发壳，实际实现集中在对应分类目录；待调用方全部迁移后可移除（见《工作区管理规范》与 jdk21-unified-auth 计划文档的兼容入口约定）。

---

## 🛠️ dev/ - 开发辅助工具

开发过程中使用的代码生成、格式化和检查工具。

### codegen/ - 代码生成

**用途**: DDD 代码生成器，根据 JSON 配置生成完整的领域模型、应用服务和接口代码。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `generate.sh` | DDD 代码生成 | 新增业务模块时快速生成样板代码 |

**示例**:
```bash
cd scripts/dev/codegen
./generate.sh examples/product.json
```

**输出**: Domain、Application、Infrastructure、Interfaces 四层完整代码

---

### format/ - 代码格式化

**用途**: 批量添加或修复代码注解，提升代码规范性。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `batch-add-javadoc.sh` | 批量添加 JavaDoc 注释 | 补充缺失的类和方法注释 |
| `batch-add-lombok.sh` | 批量添加 Lombok 注解 | 消除手写的 getter/setter/equals 等样板代码 |

**示例**:
```bash
# 扫描并添加缺失的 JavaDoc
./scripts/dev/format/batch-add-javadoc.sh apex-application

# 为 DTO/PO/VO 添加 Lombok 注解
./scripts/dev/format/batch-add-lombok.sh apex-infrastructure
```

**注意**: 运行前建议先提交现有改动，运行后检查 git diff 确认修改正确。

---

### check/ - 代码检查

**用途**: 扫描代码问题，确保符合项目规范。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `scan-javadoc-issues.sh` | 扫描 JavaDoc 问题 | 提交前检查注释完整性 |
| `scan-lombok-issues.sh` | 扫描 Lombok 使用问题 | 检查是否有手写样板代码 |
| `check-javadoc.sh`（`check_javadoc.sh`） | JavaDoc 质量检查 | 识别缺少 JavaDoc 的类和方法 |
| `check-handler-javadoc.sh` | Handler JavaDoc 检查 | 检查接口层注释完整性 |
| `check-springdoc-annotations.sh` | 检查 SpringDoc 注解 | 确保 API 文档注解完整 |
| `check-flyway-timestamp-usage.sh` | 检查时间戳用法 | 校验迁移脚本时间字段规范 |
| `check-port-conflicts.sh` | 检查端口冲突 | 启动前检查端口占用 |
| `owasp-check.sh` | OWASP 依赖漏洞扫描 | 运行 dependency-check 检查依赖漏洞 |

**SpringDoc 检查配套文档**：[README-check-springdoc.md](dev/check/README-check-springdoc.md)（完整用法）、[QUICKREF-springdoc-check.md](dev/check/QUICKREF-springdoc-check.md)（速查表）。

**示例**:
```bash
# 扫描整个项目的 JavaDoc 问题
./scripts/dev/check/scan-javadoc-issues.sh

# 检查 apex-interfaces 模块的 SpringDoc 注解
./scripts/dev/check/check-springdoc-annotations.sh apex-interfaces

# 检查 8080 端口是否被占用
./scripts/dev/check/check-port-conflicts.sh 8080
```

**输出**: 问题列表和修复建议

---

## 🧪 test/ - 测试相关

测试执行、JVM 诊断和测试报告生成。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `jvm-diagnostics-test.sh` | JVM 诊断测试 | 排查内存泄漏、线程死锁等 JVM 问题 |
| `generate-test-report.sh` | 生成测试报告 | CI/CD 中生成格式化的测试报告 |
| `test-sentinel-hot-reload.sh` | Sentinel 热更新测试 | 验证 Sentinel 规则文件监听 |
| `chaos-fault-injection.sh` | 混沌故障注入 | 验证服务容错与降级行为 |

**示例**:
```bash
# 运行 JVM 诊断测试
./scripts/test/jvm-diagnostics-test.sh

# 生成测试覆盖率报告
./scripts/test/generate-test-report.sh
```

---

## 🚀 deploy/ - 部署相关

应用部署、服务管理和 Podman 相关脚本。

### podman/ - Podman 部署

**用途**: Podman 镜像管理和监控栈部署（实际实现所在，`scripts/deploy/` 下的同名脚本为兼容转发入口）。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `pull-infra-images.sh` | 拉取基础设施镜像 | 初始化开发环境时批量拉取 MySQL、Redis、RocketMQ 等镜像（带重试） |
| `deploy-monitoring.sh` | 部署监控服务 | 部署 Prometheus、Grafana 监控栈（macOS 需先 `podman machine start`） |

**示例**:
```bash
# 拉取所有基础设施镜像
./scripts/deploy/podman/pull-infra-images.sh

# 部署监控服务
./scripts/deploy/podman/deploy-monitoring.sh
```

---

### service/ - 服务管理

**用途**: 应用启动、停止和状态管理。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `start.sh` | 启动应用 | 生产环境启动服务 |
| `stop.sh` | 停止应用 | 生产环境停止服务 |
| `app.sh` | 应用管理脚本 | 启动/停止/重启/状态查询 |

**示例**:
```bash
# 启动应用（后台运行）
./scripts/deploy/service/start.sh

# 停止应用
./scripts/deploy/service/stop.sh

# 查看应用状态
./scripts/deploy/service/app.sh status

# 重启应用
./scripts/deploy/service/app.sh restart
```

**配置**: 修改脚本中的 JAR_PATH、APP_NAME 等变量

---

### 其他部署脚本

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `apex-scaffold.service` | Systemd 服务文件 | 配置为系统服务，开机自启 |

**安装 Systemd 服务**:
```bash
# 复制服务文件
sudo cp scripts/deploy/apex-scaffold.service /etc/systemd/system/

# 重载 systemd
sudo systemctl daemon-reload

# 启用服务
sudo systemctl enable apex-scaffold

# 启动服务
sudo systemctl start apex-scaffold
```

---

## 🔧 ops/ - 运维相关

生产环境运维、备份恢复、监控和诊断工具。

### backup/ - 备份恢复

**用途**: 数据库备份和恢复。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `db-backup.sh` | 数据库备份 | 定时备份生产数据库 |
| `db-restore.sh` | 数据库恢复 | 从备份文件恢复数据库 |

**示例**:
```bash
# 备份数据库
./scripts/ops/backup/db-backup.sh

# 从备份恢复
./scripts/ops/backup/db-restore.sh backup-2026-09-20.sql
```

**配置**: 在脚本中配置数据库连接信息和备份路径

---

### monitoring/ - 监控运维

**用途**: 启动和配置监控服务。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `start-monitoring.sh` | 启动监控服务 | 启动 Prometheus + Grafana 监控栈 |
| `verify-reactor-metrics.sh` | 验证 Reactor Netty 指标 | 应用启动后验证响应式指标暴露（默认 localhost:8081） |

**Grafana 配置脚本**:

| 脚本 | 功能 |
|-----|------|
| `grafana/check-environment.sh` | 检查 Grafana 环境 |
| `grafana/configure-datasource.sh` | 配置数据源 |
| `grafana/import-dashboard.sh` | 导入仪表板 |
| `grafana/quick-check.sh` | 快速健康检查 |

**示例**:
```bash
# 启动监控服务
./scripts/ops/monitoring/start-monitoring.sh

# 检查 Grafana 环境
./scripts/ops/monitoring/grafana/check-environment.sh

# 配置 Prometheus 数据源
./scripts/ops/monitoring/grafana/configure-datasource.sh

# 导入 JVM 监控仪表板
./scripts/ops/monitoring/grafana/import-dashboard.sh jvm-dashboard.json
```

---

### diagnostics/ - 诊断工具

**用途**: JVM 和应用诊断。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `jvm-diagnostics.sh` | JVM 诊断 | 排查线程死锁、内存泄漏、CPU 飙升等问题 |

**示例**:
```bash
# 诊断 JVM 问题
./scripts/ops/diagnostics/jvm-diagnostics.sh <PID>
```

**输出**: 线程 dump、堆 dump、GC 日志分析

---

### maintenance/ - 定期维护

**用途**: 自动化定期维护任务。

| 脚本 | 频率 | 功能 |
|-----|-----|------|
| `weekly-link-check.sh` | 每周 | 检查文档链接有效性 |
| `monthly-cleanup.sh` | 每月 | 清理备份文件、临时文件、空目录 |
| `quarterly-archive.sh` | 每季度 | 归档90天前的旧报告 |

**示例**:
```bash
# 每周链接检查
./scripts/ops/maintenance/weekly-link-check.sh

# 每月清理
./scripts/ops/maintenance/monthly-cleanup.sh

# 季度归档
./scripts/ops/maintenance/quarterly-archive.sh
```

**配置 cron 定时任务**:
```cron
# 每周一上午9点检查链接
0 9 * * 1 cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/weekly-link-check.sh

# 每月1号上午10点清理
0 10 1 * * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/monthly-cleanup.sh

# 每季度第一天上午11点归档
0 11 1 1,4,7,10 * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/quarterly-archive.sh
```

**详细文档**: 参见 [maintenance/README.md](ops/maintenance/README.md)

---

## 📊 data/ - 数据相关

数据初始化、数据源配置和测试数据。

### datasource/ - 数据源

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `init-slave.sql` | 初始化从库 | 配置主从复制 |
| `setup-slave.sh` | 设置从库脚本 | 自动化配置主从复制 |

### 工作流测试数据

| 文件 | 功能 |
|-----|------|
| `init_workflow_test_data.sql` | 工作流测试数据 v1 |
| `init_workflow_test_data_v2.sql` | 工作流测试数据 v2 |

**示例**:
```bash
# 初始化工作流测试数据
mysql -u root -p apex_scaffold < scripts/data/init_workflow_test_data_v2.sql
```

---

## 🔄 ci/ - CI/CD

持续集成和文档检查脚本。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `check-doc-consistency.sh` | 文档一致性检查 | CI 中检查文档与代码是否一致 |
| `check-doc-links.sh` | 文档链接检查 | CI 中检查文档中的链接是否有效 |

**示例**:
```bash
# 检查文档一致性
./scripts/ci/check-doc-consistency.sh

# 检查所有文档链接
./scripts/ci/check-doc-links.sh
```

**集成到 CI**:
```yaml
# .github/workflows/ci.yml
- name: Check Documentation
  run: |
    ./scripts/ci/check-doc-consistency.sh
    ./scripts/ci/check-doc-links.sh
```

---

## 🛠️ utils/ - 通用工具

跨场景使用的通用工具脚本。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `export-api-docs.sh` | 导出 API 文档 | 从 Swagger/SpringDoc 导出 OpenAPI 规范 |
| `extract-env-vars.sh` | 提取环境变量 | 从配置文件提取需要的环境变量列表 |
| `batch-verify-svg.sh` | 批量验证 SVG | 检查 SVG 文件是否有效 |
| `verify-svg-quality.sh` | SVG 质量验证 | 检查 `docs/diagrams/` 图表的 CSS 残留、XML 格式与配色 |
| `check-commercialization-readiness.sh` | 商业化就绪度检查 | 检查项目是否达到商业化售卖标准 |
| `local-deployment.sh` | 本地部署辅助 | 本地环境部署操作 |
| `start-doc-search.sh` | 启动文档搜索 | 本地启动静态服务访问 `docs/04-development/doc-search.html` |
| `token-v42-recover.sh` | 令牌迁移恢复 | token v42 敏感字段迁移的恢复工具 |
| `ConfigEncryptionTool.java` | 配置加密工具 | 配置文件敏感项加解密 |

**示例**:
```bash
# 导出 OpenAPI 文档
./scripts/utils/export-api-docs.sh http://localhost:8080/v3/api-docs > openapi.json

# 提取环境变量
./scripts/utils/extract-env-vars.sh application-prod.yml

# 验证所有 SVG 文件
./scripts/utils/batch-verify-svg.sh docs/diagrams/
```

---

## ⚡ bench/ - 性能基准测试

性能测试脚本。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `bench.sh` | 性能基准测试 | 测试系统吞吐量、延迟等性能指标 |
| `bench-vt.sh` | 虚拟线程 A/B 压测 | 对比平台线程与虚拟线程模式下阻塞 I/O 池的吞吐与延迟 |

**示例**:
```bash
# 运行性能测试
./scripts/bench/bench.sh

# 对比平台线程与虚拟线程模式（自动启停 system-service）
./scripts/bench/bench-vt.sh
```

---

## 🎬 init/ - 初始化

项目初始化脚本。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `new-project.sh` | 创建新项目 | 基于 Apex Scaffold 创建新项目 |

**示例**:
```bash
# 创建新项目
./scripts/init/new-project.sh my-new-project
```

---

## 🔐 keycloak/ - Keycloak

Keycloak 认证服务器配置。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `init-realm.sh` | 初始化 Realm | 创建 Keycloak Realm 和客户端配置 |

**示例**:
```bash
# 初始化 Keycloak Realm
./scripts/keycloak/init-realm.sh
```

---

## 🔑 license/ - 授权

授权文件生成和管理。

| 脚本 | 功能 | 使用场景 |
|-----|------|---------|
| `generate-license.sh` | 生成授权文件 | 为商业版生成授权证书 |

**示例**:
```bash
# 生成授权文件
./scripts/license/generate-license.sh --customer "Acme Inc" --expire "2027-12-31"
```

---

## 📄 office/ - Office 集成

Office 文档处理相关脚本。

| 脚本 | 功能 |
|-----|------|
| `smoke-test.sh` | Office 服务冒烟测试 |
| `validate-office-config.sh` | 校验 Office 配置 |
| `generate-test-report.sh` | 生成 Office 集成测试报告 |
| `generate-message-infrastructure.sh` | 历史站内信代码生成工具（兼容入口，实际实现已归档至 `archive/office/`） |

**示例**:
```bash
# 校验 Office 配置
./scripts/office/validate-office-config.sh

# 冒烟测试
./scripts/office/smoke-test.sh http://localhost:8080
```

---

## 🗄️ archive/ - 历史脚本归档

已完成使命的一次性脚本，按来源分类保留；执行前请先核对脚本头部说明与原始上下文。

| 子目录 | 内容 |
|-----|------|
| `frontend/` | 旧前端一次性工具（TS 错误修复、注释补充、版本号更新） |
| `office/` | 一次性 Office 代码生成工具（消息基础设施、Phase 2 代码框架） |
| `svg/` | SVG 图表一次性治理工具（图标批量升级/回滚、旧图清理、显示验证） |
| `microservices/` | 微服务化迁移一次性工具（Phase 5/6 骨架生成、off_* 数据同步与对账守护） |

---

## 📈 performance/ - 性能测试

JMeter 性能测试脚本。

| 文件/目录 | 功能 |
|---------|------|
| `jmeter/api-load-test.jmx` | JMeter 测试计划 |
| `run-load-test.sh` | 运行负载测试 |

**示例**:
```bash
# 运行负载测试
./scripts/performance/run-load-test.sh

# 使用 JMeter GUI 编辑测试计划
jmeter -t scripts/performance/jmeter/api-load-test.jmx
```

---

## 📋 使用指南

### 环境准备

所有脚本运行前需要确保环境正确：

```bash
# 1. 加载项目环境
cd /Users/work/apex-scaffold
sdk env

# 2. 验证 Java 和 mvnd 版本
java -version   # 应该是 21.0.13.0+9-tem
mvnd --version  # 应该是 2.0.0-rc-3
```

### 脚本权限

首次使用需要添加执行权限：

```bash
# 为所有脚本添加执行权限
find scripts -name "*.sh" -type f -exec chmod +x {} \;
```

### 常用工作流

#### 开发工作流

```bash
# 1. 生成新业务模块
./scripts/dev/codegen/generate.sh examples/order.json

# 2. 检查代码规范
./scripts/dev/check/scan-javadoc-issues.sh
./scripts/dev/check/scan-lombok-issues.sh
./scripts/dev/check/check-springdoc-annotations.sh

# 3. 格式化代码（如需要）
./scripts/dev/format/batch-add-javadoc.sh apex-application
./scripts/dev/format/batch-add-lombok.sh apex-infrastructure

# 4. 运行测试
mvnd clean test -P basic

# 5. 提交代码
git add .
git commit -m "feat(order): add order management module"
```

#### 部署工作流

```bash
# 1. 拉取基础设施镜像
./scripts/deploy/podman/pull-infra-images.sh

# 2. 部署监控服务
./scripts/deploy/podman/deploy-monitoring.sh

# 3. 启动应用
./scripts/deploy/service/start.sh

# 4. 检查应用状态
./scripts/deploy/service/app.sh status

# 5. 查看日志
tail -f logs/apex-scaffold.log
```

#### 运维工作流

```bash
# 1. 备份数据库
./scripts/ops/backup/db-backup.sh

# 2. 启动监控
./scripts/ops/monitoring/start-monitoring.sh

# 3. 诊断问题（如需要）
./scripts/ops/diagnostics/jvm-diagnostics.sh <PID>

# 4. 导出 API 文档
./scripts/utils/export-api-docs.sh http://localhost:8080/v3/api-docs > openapi.json
```

---

## 🔧 配置说明

### 通用配置变量

多数脚本支持通过环境变量或脚本内变量配置：

| 变量 | 说明 | 默认值 |
|-----|------|-------|
| `PROJECT_ROOT` | 项目根目录 | `/Users/work/apex-scaffold` |
| `APP_NAME` | 应用名称 | `apex-scaffold` |
| `APP_PORT` | 应用端口 | `8080` |
| `JAVA_HOME` | Java 路径 | 由 SDKMAN 管理 |
| `MAVEN_OPTS` | Maven 选项 | `-Xmx2g -XX:MaxMetaspaceSize=512m` |

### 脚本配置文件

部分脚本支持独立配置文件：

- `scripts/deploy/service/app.conf` - 应用服务配置
- `scripts/ops/backup/backup.conf` - 备份配置
- `scripts/ops/monitoring/monitoring.conf` - 监控配置

---

## 📝 最佳实践

### 1. 脚本命名

- 使用 kebab-case 命名（如 `check-port-conflicts.sh`）
- 动词开头，清晰描述功能（如 `generate-`, `check-`, `deploy-`）
- 避免缩写，除非是通用约定（如 `jvm`、`api`）

### 2. 脚本结构

```bash
#!/usr/bin/env bash
set -euo pipefail  # 严格模式

# 脚本说明
# 用途：...
# 示例：./script.sh [options]

# 常量定义
readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly SCRIPT_NAME="$(basename "$0")"

# 函数定义
function main() {
    # 主逻辑
}

# 入口
main "$@"
```

### 3. 错误处理

```bash
# 检查命令是否存在
if ! command -v mvnd &> /dev/null; then
    echo "Error: mvnd not found. Please install via SDKMAN."
    exit 1
fi

# 检查文件是否存在
if [[ ! -f "pom.xml" ]]; then
    echo "Error: pom.xml not found. Run from project root."
    exit 1
fi
```

### 4. 日志输出

```bash
# 使用颜色区分不同级别
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly NC='\033[0m' # No Color

function log_info() {
    echo -e "${GREEN}[INFO]${NC} $*"
}

function log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

function log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}
```

---

## 🐛 故障排查

### 常见问题

#### 1. 权限不足

```bash
# 症状
bash: ./script.sh: Permission denied

# 解决
chmod +x scripts/path/to/script.sh
```

#### 2. 找不到命令

```bash
# 症状
command not found: mvnd

# 解决
cd /Users/work/apex-scaffold
sdk env
```

#### 3. 脚本路径错误

```bash
# 症状
No such file or directory: /some/path

# 解决
# 确保从项目根目录运行，或使用绝对路径
cd /Users/work/apex-scaffold
./scripts/path/to/script.sh
```

#### 4. 环境变量未设置

```bash
# 症状
Error: JAVA_HOME not set

# 解决
sdk env  # 或手动设置 export JAVA_HOME=...
```

---

## 📚 相关文档

- [AGENTS.md](../AGENTS.md) - 编码规范和验证约定
- [docs/04-development/](../docs/04-development/README.md) - 开发规范文档
- [docs/05-operations/](../docs/05-operations/README.md) - 运维部署文档
- [docs/06-testing/](../docs/06-testing/README.md) - 测试文档

---

## 🔄 版本历史

### v2.1 (2026-10-29)

- ✨ SVG 图表工具归位：`verify-svg-quality.sh` 迁入 `utils/`，一次性图表工具归档至 `archive/svg/`
- ✨ `deploy/podman/` 成为部署脚本正身，`scripts/deploy/` 根级同名脚本转为兼容转发入口
- ✨ 根目录实体脚本归位：`check_javadoc.sh`、`owasp-check.sh` 及 SpringDoc 检查文档迁入 `dev/check/`，`test-sentinel-hot-reload.sh` 迁入 `test/`，`verify-reactor-metrics.sh` 迁入 `ops/monitoring/`，`check-commercialization-readiness.sh` 迁入 `utils/`
- ✨ `scripts/microservices/` 迁移一次性工具归档至 `archive/microservices/`
- ✨ `scripts/office/generate-phase2-code.sh` 归档至 `archive/office/`
- 📝 更新所有受影响脚本路径引用（文档、POM 注释与检查脚本）

### v2.0 (2026-09-20)

- ✨ 重组目录结构，按功能分类
- ✨ 新增 `dev/format/` 和 `dev/check/` 分类
- ✨ 新增 `ops/monitoring/` 和 `ops/backup/` 分类
- ✨ 新增容器部署和 `deploy/service/` 分类（当前容器入口为 `deploy/podman/`）
- ✨ 新增 `utils/` 通用工具分类
- 🗑️ 删除 6 个临时脚本（PMD、JavaDoc 清理等）
- 📝 更新所有脚本路径引用

### v1.0 (2026-09-01)

- 🎉 初始版本
- 基础脚本集合

---

## 📮 反馈与贡献

如果您发现脚本问题或有改进建议，请：

1. 在项目中创建 Issue
2. 提交 Pull Request
3. 联系项目维护团队

---

**维护者**: Apex Team  
**最后更新**: 2026-10-29
**文档版本**: v2.1

© 2026 Apex Team · 让脚本管理更简单 🚀
