# 定期维护脚本

本目录包含项目定期维护的自动化脚本。

## 📋 脚本列表

| 脚本 | 频率 | 功能 | 执行时间建议 |
|-----|-----|------|------------|
| `weekly-link-check.sh` | 每周 | 检查文档链接有效性 | 每周一上午 |
| `monthly-cleanup.sh` | 每月 | 清理备份文件、临时文件、空目录 | 每月1号 |
| `quarterly-archive.sh` | 每季度 | 归档过时报告和文档 | 每季度第一天 |

---

## 🔧 使用方法

### 1. 每周链接检查

**用途**: 自动检查所有文档的内部链接，确保链接有效性。

```bash
./scripts/ops/maintenance/weekly-link-check.sh
```

**输出**:
- 日志文件: `logs/link-check-YYYYMMDD.log`
- 检查报告: `docs/reports/link-check-YYYYMMDD.md`

**自动检查** (可选):
```bash
./scripts/ci/check-doc-links.sh
```

---

### 2. 每月清理

**用途**: 清理临时文件、备份文件、空目录和重复文件。

```bash
./scripts/ops/maintenance/monthly-cleanup.sh
```

**检查项目**:
- ✅ 备份文件 (*.bak, *.backup, *~)
- ✅ 重复文件 (*副本*, *copy*, *_2.*)
- ✅ 空目录
- ✅ 大文件 (>10MB)
- ✅ 临时文件 (*.tmp, *.temp, .DS_Store)

**输出**:
- 日志文件: `logs/maintenance-YYYYMM.log`

**交互模式**:
- 发现备份文件和空目录时会询问是否删除
- 其他问题会显示警告，需手动处理

---

### 3. 季度归档

**用途**: 归档 90 天以上的旧报告到 `docs/_archive/`。

```bash
./scripts/ops/maintenance/quarterly-archive.sh
```

**功能**:
- 自动归档 90 天前的报告文件
- 统计归档目录大小和文件数量
- 生成归档总结报告

**输出**:
- 日志文件: `logs/quarterly-archive-YYYYMM.log`
- 归档报告: `docs/reports/archive-summary-YYYYMMDD.md`

---

## 📅 定时任务配置

### 方式一：使用 cron (推荐)

编辑 crontab:
```bash
crontab -e
```

添加以下任务:
```cron
# 每周一上午9点检查链接
0 9 * * 1 cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/weekly-link-check.sh

# 每月1号上午10点清理
0 10 1 * * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/monthly-cleanup.sh

# 每季度第一天上午11点归档
0 11 1 1,4,7,10 * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/quarterly-archive.sh
```

查看已配置的任务:
```bash
crontab -l
```

---

### 方式二：使用 launchd (macOS)

创建 plist 文件 `~/Library/LaunchAgents/com.apex.maintenance.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.apex.maintenance.weekly</string>
    <key>ProgramArguments</key>
    <array>
        <string>/Users/work/apex-scaffold/scripts/ops/maintenance/weekly-link-check.sh</string>
    </array>
    <key>StartCalendarInterval</key>
    <dict>
        <key>Weekday</key>
        <integer>1</integer>
        <key>Hour</key>
        <integer>9</integer>
        <key>Minute</key>
        <integer>0</integer>
    </dict>
    <key>StandardOutPath</key>
    <string>/Users/work/apex-scaffold/logs/launchd-weekly.log</string>
    <key>StandardErrorPath</key>
    <string>/Users/work/apex-scaffold/logs/launchd-weekly-error.log</string>
</dict>
</plist>
```

加载任务:
```bash
launchctl load ~/Library/LaunchAgents/com.apex.maintenance.plist
```

---

## 📊 日志管理

### 日志位置

所有维护脚本的日志都存储在 `logs/` 目录:

```
logs/
├── link-check-20260920.log          # 链接检查日志
├── maintenance-202609.log            # 月度清理日志
└── quarterly-archive-202609.log     # 季度归档日志
```

### 日志清理

清理 30 天前的日志:
```bash
find logs/ -name "*.log" -mtime +30 -delete
```

---

## 🔍 故障排查

### 1. 权限问题

```bash
# 添加执行权限
chmod +x scripts/ops/maintenance/*.sh
```

### 2. Python 环境问题

链接检查需要 Python 3:
```bash
# 检查 Python 版本
python3 --version

# 如果不存在，安装 Python 3
brew install python3
```

### 3. 路径问题

确保脚本从项目根目录执行，或使用绝对路径:
```bash
cd /Users/work/apex-scaffold
./scripts/ops/maintenance/weekly-link-check.sh
```

---

## 📝 自定义配置

### 修改归档天数

编辑 `quarterly-archive.sh`，修改第 21 行:
```bash
# 默认 90 天
cutoff_date=$(date -v-90d +%Y%m%d 2>/dev/null || date -d '90 days ago' +%Y%m%d)

# 改为 60 天
cutoff_date=$(date -v-60d +%Y%m%d 2>/dev/null || date -d '60 days ago' +%Y%m%d)
```

### 修改大文件阈值

编辑 `monthly-cleanup.sh`，修改第 76 行:
```bash
# 默认 10MB
large_files=$(find "$PROJECT_ROOT" -type f -size +10M ...)

# 改为 50MB
large_files=$(find "$PROJECT_ROOT" -type f -size +50M ...)
```

---

## ✅ 最佳实践

### 1. 定期检查日志

```bash
# 查看最新的链接检查日志
tail -50 logs/link-check-*.log | tail -50

# 查看月度清理日志
tail -50 logs/maintenance-*.log | tail -50
```

### 2. 手动测试

首次配置定时任务前，先手动运行测试:
```bash
# 测试链接检查
./scripts/ops/maintenance/weekly-link-check.sh

# 测试月度清理（干运行）
./scripts/ops/maintenance/monthly-cleanup.sh
```

### 3. 监控执行状态

创建简单的监控脚本:
```bash
#!/bin/bash
# check-maintenance-status.sh

echo "=== 维护任务执行状态 ==="
echo ""

echo "最近的链接检查:"
ls -lt logs/link-check-*.log 2>/dev/null | head -1

echo ""
echo "最近的月度清理:"
ls -lt logs/maintenance-*.log 2>/dev/null | head -1

echo ""
echo "最近的季度归档:"
ls -lt logs/quarterly-archive-*.log 2>/dev/null | head -1
```

---

## 📚 相关文档

- [DOCS_STRUCTURE_STANDARD.md](../../../docs/DOCS_STRUCTURE_STANDARD.md) - 文档结构规范
- [scripts/README.md](../../README.md) - 脚本索引
- [scripts/ci/check-doc-links.sh](../../ci/check-doc-links.sh) - 链接检查脚本

---

## 🆘 获取帮助

如有问题或建议，请：
1. 查看脚本日志文件
2. 检查脚本执行权限
3. 确认 Python 3 已安装
4. 联系项目维护团队

---

**维护者**: Apex Team  
**最后更新**: 2026-09-20  
**版本**: v1.0

© 2026 Apex Team · 让维护更简单 🚀
