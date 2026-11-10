# Apex Scaffold 备份与恢复工具

本目录提供 MySQL、PostgreSQL 数据库的备份、恢复和隔离演练脚本，支持宿主机直连与 Podman 容器两种执行场景。部署与运维边界参见 [部署文档](../../docs/05-operations/deployment.md) 和 [运维手册](../../docs/05-operations/oncall-runbook.md)。

## 文件清单

| 脚本 | 用途 |
| --- | --- |
| `db-backup.sh` | MySQL 一致性全量备份：`mysqldump` → gzip → 完整性校验 → 轮转保留 → 可选上传 MinIO/S3 |
| `db-restore.sh` | MySQL 恢复：支持 `.sql`、`.sql.gz`；默认先备份当前库，再覆盖目标库 |
| `pg-backup.sh` | PostgreSQL 一致性全量备份：`pg_dump -Fc` → `pg_restore --list` 校验 → 轮转保留 → 可选上传 MinIO/S3 |
| `pg-restore.sh` | PostgreSQL 恢复：仅支持 `.dump`；默认先备份当前库，再覆盖目标库 |
| `backup-restore-drill.sh` | 在名称包含 `_drill` 的隔离库中执行恢复并校验关键表和 Flyway 版本，退出时删除演练库 |

所有脚本都使用严格的 Bash 模式（`set -euo pipefail`），并通过临时凭据文件传递密码，避免密码出现在进程参数中。

## 快速开始

### MySQL

```bash
# 备份。密码从环境变量读取，备份目录默认是仓库根目录下的 backups/
export DB_MASTER_PASSWORD='***'
./db-backup.sh --yes

# 指定备份目录、保留份数和容器
BACKUP_DIR=/data/backups ./db-backup.sh \
  --container apex-infra-mysql --keep 14 --yes

# 从指定文件恢复；恢复前默认先备份当前库
./db-restore.sh \
  --file /data/backups/apex_system_20261110_020000.sql.gz --yes

# 演练环境已确认有可用备份时，可跳过恢复前备份
./db-restore.sh \
  --file /data/backups/apex_system_20261110_020000.sql.gz --no-backup --yes
```

### PostgreSQL

```bash
# 备份。密码从 PGPASSWORD 读取；生成 .dump 文件
export PGPASSWORD='***'
./pg-backup.sh --yes

# 从 .dump 文件恢复；恢复前默认先备份当前库
./pg-restore.sh \
  --file /data/backups/apex_system_20261110_020000.dump --yes
```

### 隔离恢复演练

```bash
# MySQL：脚本会创建 <源库>_drill_<时间戳>，恢复并校验后删除
./backup-restore-drill.sh \
  --engine mysql \
  --file /data/backups/apex_system_20261110_020000.sql.gz \
  --yes

# PostgreSQL：同样要求显式 --yes
./backup-restore-drill.sh \
  --engine postgresql \
  --file /data/backups/apex_system_20261110_020000.dump \
  --password-file /secure/pg.pass \
  --yes
```

`backup-restore-drill.sh` 默认校验 `sys_menu`、`sys_user` 与 `flyway_schema_history` 可查询；可通过 `--expected-flyway-version <version>` 要求演练库的 Flyway 最新版本等于指定值。演练库名必须包含 `_drill`，且不得等于源库；脚本无论成功失败都通过 EXIT trap 尝试删除演练库和临时凭据文件。

## 密码与确认约束

- MySQL 密码来源：`DB_MASTER_PASSWORD` 或 `--password-file <file>`；不提供 `--pass`/`--password` 参数。
- PostgreSQL 密码来源：`PGPASSWORD` 或 `--password-file <file>`；不提供 `--pass`/`--password` 参数。
- 密码文件必须是普通文件，权限必须为 `400` 或 `600`；脚本只读取首行。
- 备份轮转删除在非交互环境必须显式传入 `--yes`，否则脚本保留过期文件并跳过删除。
- MySQL/PostgreSQL 恢复会覆盖目标库对象，非交互环境必须显式传入 `--yes`。
- 恢复前默认先备份当前库；只有确认已有可用备份且处于演练场景时，才使用 `--no-backup`。
- 恢复要求目标库已存在；脚本不会自动创建目标库。
- MySQL 恢复仅接受 `.sql` 或 `.sql.gz`；PostgreSQL 恢复仅接受 `pg_dump -Fc` 生成的 `.dump`。
- 恢复演练必须显式传入 `--yes`，演练库名必须包含 `_drill`，且不得等于源库。

## 环境变量参考

### MySQL

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_MASTER_HOST` | `127.0.0.1` | MySQL 地址 |
| `DB_MASTER_PORT` | `3306` | MySQL 端口 |
| `DB_MASTER_USERNAME` | `root` | MySQL 账号 |
| `DB_MASTER_PASSWORD` | 空 | MySQL 密码；与 `--password-file` 二选一 |
| `MYSQL_DATABASE` | `apex_system` | 默认数据库名 |
| `MYSQL_CONTAINER` | 空 | Podman 容器名；非空时通过 `podman exec` 执行 |
| `BACKUP_DIR` | `backups/` | 备份输出目录 |
| `BACKUP_KEEP` | `7` | 备份轮转保留份数（仅备份脚本） |
| `MC_ALIAS` / `MC_BUCKET` | 空 | MinIO/S3 目标；使用 `--upload` 时必须同时设置 |

### PostgreSQL

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PGHOST` | `127.0.0.1` | PostgreSQL 地址 |
| `PGPORT` | `5432` | PostgreSQL 端口 |
| `PGUSER` | `postgres` | PostgreSQL 账号 |
| `PGPASSWORD` | 空 | PostgreSQL 密码；与 `--password-file` 二选一 |
| `PGDATABASE` | `apex_system` | 默认数据库名 |
| `POSTGRES_CONTAINER` | 空 | Podman 容器名；非空时通过 `podman exec` 执行 |
| `POSTGRES_CONTAINER_HOST` | `127.0.0.1` | 容器内 PostgreSQL 地址 |
| `POSTGRES_CONTAINER_PORT` | `5432` | 容器内 PostgreSQL 端口 |
| `BACKUP_DIR` | `backups/` | 备份输出目录 |
| `BACKUP_KEEP` | `7` | 备份轮转保留份数（仅备份脚本） |
| `MC_ALIAS` / `MC_BUCKET` | 空 | MinIO/S3 目标；使用 `--upload` 时必须同时设置 |

所有连接参数均可用对应的命令行选项覆盖；完整选项以脚本 `--help` 输出为准。

## 定时备份示例

```cron
# 每日 02:30 执行 MySQL 全量备份，保留 14 份（容器场景）
30 2 * * * cd /opt/apex-scaffold/scripts/backup && \
  BACKUP_DIR=/data/backups ./db-backup.sh \
  --password-file /run/secrets/apex-db-password \
  --container apex-infra-mysql --keep 14 --yes >> /var/log/apex-backup.log 2>&1
```

> 上例中的密码只通过 `--password-file` 传入。请确保密钥文件权限为 `400` 或 `600`，且不会被写入日志或版本库。

## 退出码

### 备份脚本（`db-backup.sh`、`pg-backup.sh`）

| 码 | 含义 |
| --- | --- |
| `0` | 成功 |
| `1` | 参数或环境错误 |
| `2` | 备份失败，包括导出失败或备份文件完整性校验失败 |
| `3` | 对象存储上传失败或上传环境不可用；本地备份保留 |

### 恢复脚本（`db-restore.sh`、`pg-restore.sh`）

| 码 | 含义 |
| --- | --- |
| `0` | 成功 |
| `1` | 参数或环境错误，包括目标库不存在、备份文件扩展名不支持 |
| `2` | 恢复失败，包括备份文件损坏、导入失败或恢复前备份失败 |

### 演练脚本（`backup-restore-drill.sh`）

| 码 | 含义 |
| --- | --- |
| `0` | 演练执行且全部校验通过 |
| `1` | 参数错误 |
| `2` | 恢复后数据校验失败 |
| `3` | 客户端缺失、数据库不可连接或环境不可用（SKIP） |

## 恢复演练建议

1. 在隔离环境或独立实例中执行演练，不在生产库上直接验证恢复流程。
2. 使用 `backup-restore-drill.sh` 创建 `_drill` 演练库，避免手工误连生产库。
3. 校验 `sys_menu`、`sys_user`、`flyway_schema_history`，并按发布基线传入 `--expected-flyway-version`。
4. 恢复完成后启动指向演练库的应用实例，执行健康检查、登录和关键业务冒烟。
5. 记录演练时间、备份文件、源库、Flyway 版本、校验结果和异常处理，作为恢复能力证据。

## 完整备份范围

除数据库外，生产备份策略还应覆盖：

- Redis 数据卷（启用 AOF 时）；
- 本地上传文件目录 `data/storage/`；
- 授权文件、环境变量模板与部署密钥；
- License 私钥必须离线保存；私钥丢失将无法签发新授权。
