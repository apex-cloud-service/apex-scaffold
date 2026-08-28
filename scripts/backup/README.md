# Apex Scaffold 备份/恢复工具

数据库与关键目录的备份/恢复脚本，支持宿主机直连与 Docker 容器两种部署场景（与 [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md) §13 对齐）。

## 文件清单

| 脚本 | 用途 |
|------|------|
| `db-backup.sh` | MySQL 全量备份（mysqldump，一致性快照）→ gzip → 轮转保留 N 份 → 可选上传 MinIO/S3 |
| `db-restore.sh` | 从备份文件恢复（自动识别 .sql/.sql.gz；恢复前默认先备份当前数据） |

## 快速开始

```bash
# 1. 备份（宿主机直连场景, 环境变量与 .env.example 对齐）
export DB_MASTER_PASSWORD=apex-root
./db-backup.sh                                    # 默认库 apex_data, 保留 7 份

# 2. 备份（Docker compose 场景）
./db-backup.sh --container apex-infra-mysql

# 3. 恢复（交互选择最近备份, 恢复前自动备份当前库）
./db-restore.sh --pass apex-root

# 4. 恢复（指定文件, 跳过恢复前备份）
./db-restore.sh --file ../backups/apex_data_20260828_120000.sql.gz --no-backup
```

## 环境变量参考（与 docker-compose.yml / .env.example 对齐）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_MASTER_HOST` | `127.0.0.1` | MySQL 地址 |
| `DB_MASTER_PORT` | `3306` | MySQL 端口 |
| `DB_MASTER_USERNAME` | `root` | 数据库账号 |
| `DB_MASTER_PASSWORD` | 必填 | 数据库密码 |
| `MYSQL_DATABASE` | `apex_data` | 目标库名 |
| `MYSQL_CONTAINER` | 空 | Docker 容器名（非空时走 `docker exec`） |
| `BACKUP_DIR` | `backups/` | 备份输出目录 |
| `BACKUP_KEEP` | `7` | 轮转保留份数（仅 backup） |
| `MC_ALIAS` / `MC_BUCKET` | 空 | MinIO/S3 上传目标（配合 `--upload`） |

## 定时备份（cron 示例）

```cron
# 每日 02:30 全量备份, 保留 14 份（容器场景）
30 2 * * * cd /opt/apex-scaffold/scripts/backup && \
  BACKUP_DIR=/data/backups ./db-backup.sh --container apex-infra-mysql --keep 14 >> /var/log/apex-backup.log 2>&1
```

## 恢复演练（强烈建议每季度执行一次）

1. 在**隔离环境**启动一份 MySQL（或临时库名）
2. 执行 `./db-restore.sh --db apex_data_drill --file <备份>`
3. 校验：`SELECT COUNT(*) FROM sys_menu;` 与迁移版本表 `flyway_schema_history` 行数应与生产一致
4. 启动应用指向演练库, 跑冒烟（`/api/v1/health`、登录）

## 完整备份策略（除 DB 外还需覆盖）

- Redis 数据卷（AOF 开启时）：`docker run --rm -v apex-scaffold_redis-data:/data -v $(pwd):/backup alpine tar czf /backup/redis-data.tar.gz -C /data .`
- 本地存储文件：`data/storage/`（文件上传目录）
- 授权与环境：`/etc/apex-scaffold/`（License 文件、环境变量）；**License 私钥丢失将无法发放新授权, 务必离线备份**

## 退出码约定

| 码 | 含义 |
|----|------|
| 0 | 成功 |
| 1 | 参数/环境错误 |
| 2 | 备份失败（含 gzip 校验失败） |
| 3 | 上传失败（本地备份保留） |
