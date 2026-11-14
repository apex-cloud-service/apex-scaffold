# Apex Scaffold 脚本维护使用说明

**文档版本**: v1.0  
**最后更新**: 2026-09-20  
**维护团队**: Apex Team

---

## 📋 目录

- [1. 概述](#1-概述)
- [2. 定期维护脚本](#2-定期维护脚本)
- [3. 快速开始](#3-快速开始)
- [4. 配置定时任务](#4-配置定时任务)
- [5. 日志管理](#5-日志管理)
- [6. 故障排查](#6-故障排查)
- [7. 最佳实践](#7-最佳实践)
- [8. FAQ](#8-faq)

---

<a id="1-概述"></a>

## 1. 概述

### 1.1 目的

本文档提供 Apex Scaffold 项目维护脚本的完整使用指南，包括：
- 定期维护任务的执行方法
- 定时任务配置指南
- 日志管理和问题排查
- 最佳实践建议

### 1.2 维护脚本列表

| 脚本 | 频率 | 功能 | 位置 |
|-----|-----|------|------|
| `weekly-link-check.sh` | 每周 | 文档链接检查 | `scripts/ops/maintenance/` |
| `monthly-cleanup.sh` | 每月 | 清理临时文件 | `scripts/ops/maintenance/` |
| `quarterly-archive.sh` | 每季度 | 归档旧报告 | `scripts/ops/maintenance/` |

### 1.3 维护周期建议

```
每周     → 链接检查 + 修复
每月     → 清理备份、临时文件、空目录
每季度   → 归档 90 天前的报告
每年     → 清理归档目录，审查维护策略
```

---

<a id="2-定期维护脚本"></a>

## 2. 定期维护脚本

### 2.1 每周链接检查

#### 功能说明

自动检查所有文档的内部链接，确保链接有效性。

#### 使用方法

```bash
# 进入项目目录
cd /Users/work/apex-scaffold

# 执行检查
./scripts/ops/maintenance/weekly-link-check.sh
```

#### 输出文件

- **日志**: `logs/link-check-YYYYMMDD.log`
- **报告**: `docs/reports/link-check-YYYYMMDD.md`

#### 检查结果

```bash
# 查看最新日志
tail -50 logs/link-check-*.log | tail -50

# 查看检查报告
cat docs/reports/link-check-$(date +%Y%m%d).md
```

#### 修复断裂链接

如果检查发现断裂链接：

```bash
# 链接检查
./scripts/ci/check-doc-links.sh
```

#### 预期结果

- ✅ 所有活跃文档链接有效（0 个断裂）
- ✅ 归档/模板/计划文档的断裂链接已标记
- ✅ 链接有效率 > 95%

---

### 2.2 每月清理

#### 功能说明

清理项目中的临时文件、备份文件、空目录和大文件。

#### 使用方法

```bash
cd /Users/work/apex-scaffold
./scripts/ops/maintenance/monthly-cleanup.sh
```

#### 检查项目

| 项目 | 模式 | 说明 |
|-----|------|------|
| 备份文件 | 交互式 | `*.bak`, `*.backup`, `*~` |
| 重复文件 | 提示 | `*副本*`, `*copy*`, `*_2.*` |
| 空目录 | 交互式 | 递归检查所有目录 |
| 大文件 | 提示 | 大于 10MB 的文件 |
| 临时文件 | 自动 | `*.tmp`, `*.temp`, `.DS_Store` |

#### 输出文件

- **日志**: `logs/maintenance-YYYYMM.log`

#### 交互模式

脚本会在删除备份文件和空目录前询问确认：

```
发现 5 个备份文件:
-rw-r--r--  user  staff  1.2M  file.bak
...
是否删除这些备份文件? (y/N)
```

#### 注意事项

- ⚠️ 删除前请仔细确认
- ⚠️ 重要备份应提前移动到安全位置
- ⚠️ 首次运行建议手动执行，熟悉后再配置自动化

---

### 2.3 季度归档

#### 功能说明

自动归档 90 天以前的报告文件到 `docs/_archive/reports/`。

#### 使用方法

```bash
cd /Users/work/apex-scaffold
./scripts/ops/maintenance/quarterly-archive.sh
```

#### 归档规则

- 扫描 `docs/reports/` 目录
- 识别文件名中的日期（格式：`YYYY-MM-DD` 或 `YYYYMMDD`）
- 归档 90 天前的报告
- 保留重要报告和总结文档

#### 输出文件

- **日志**: `logs/quarterly-archive-YYYYMM.log`
- **报告**: `docs/reports/archive-summary-YYYYMMDD.md`

#### 归档后操作

```bash
# 查看归档目录
ls -lh docs/_archive/reports/

# 查看归档总结
cat docs/reports/archive-summary-$(date +%Y%m%d).md

# 查看归档大小
du -sh docs/_archive/
```

#### 定期清理

每年清理一次归档目录：

```bash
# 查看超过1年的归档
find docs/_archive/ -type f -mtime +365

# 手动删除（谨慎操作）
find docs/_archive/ -type f -mtime +365 -delete
```

---

<a id="3-快速开始"></a>

## 3. 快速开始

### 3.1 首次使用

#### 步骤 1: 检查环境

```bash
# 检查 bash 版本
bash --version  # 应该是 3.2+

# 检查 Java 版本（链接检查由 apex-tools-deployment 的 Java 工具承担）
java -version  # 应该是 21

# 检查脚本权限
ls -lh scripts/ops/maintenance/*.sh
```

#### 步骤 2: 手动测试

```bash
cd /Users/work/apex-scaffold

# 测试链接检查（推荐）
./scripts/ops/maintenance/weekly-link-check.sh

# 查看结果
cat docs/reports/link-check-$(date +%Y%m%d).md
```

#### 步骤 3: 配置定时任务

```bash
# 编辑 crontab
crontab -e

# 添加任务（见第 4 节）
```

### 3.2 日常维护

```bash
# 每周：检查链接报告
cat docs/reports/link-check-*.md | tail -50

# 每月：查看清理日志
tail -50 logs/maintenance-*.log

# 每季度：查看归档报告
cat docs/reports/archive-summary-*.md
```

---

<a id="4-配置定时任务"></a>

## 4. 配置定时任务

### 4.1 使用 cron（推荐）

#### 编辑 crontab

```bash
crontab -e
```

#### 添加任务

```cron
# Apex Scaffold 定期维护任务

# 每周一上午 9:00 - 链接检查
0 9 * * 1 cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/weekly-link-check.sh

# 每月 1 号上午 10:00 - 清理
0 10 1 * * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/monthly-cleanup.sh

# 每季度第一天上午 11:00 - 归档（1月、4月、7月、10月）
0 11 1 1,4,7,10 * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/quarterly-archive.sh
```

#### 查看已配置任务

```bash
crontab -l
```

#### 删除任务

```bash
# 编辑并删除对应行
crontab -e

# 或删除所有任务
crontab -r
```

### 4.2 Cron 时间格式

```
分钟 小时 日期 月份 星期
 │   │   │   │   │
 │   │   │   │   └─ 0-7 (0和7都表示周日)
 │   │   │   └───── 1-12
 │   │   └───────── 1-31
 │   └───────────── 0-23
 └───────────────── 0-59
```

#### 常用示例

| Cron 表达式 | 说明 |
|------------|------|
| `0 9 * * 1` | 每周一 9:00 |
| `0 10 1 * *` | 每月 1 号 10:00 |
| `0 11 1 1,4,7,10 *` | 每季度首日 11:00 |
| `*/30 * * * *` | 每 30 分钟 |
| `0 2 * * *` | 每天凌晨 2:00 |

### 4.3 使用 launchd（macOS 替代方案）

创建 `~/Library/LaunchAgents/com.apex.maintenance.weekly.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" 
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
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
launchctl load ~/Library/LaunchAgents/com.apex.maintenance.weekly.plist

# 查看状态
launchctl list | grep apex

# 卸载
launchctl unload ~/Library/LaunchAgents/com.apex.maintenance.weekly.plist
```

---

<a id="5-日志管理"></a>

## 5. 日志管理

### 5.1 日志位置

所有维护脚本的日志都存储在 `logs/` 目录：

```
logs/
├── link-check-20260920.log          # 链接检查日志
├── maintenance-202609.log            # 月度清理日志
├── quarterly-archive-202609.log     # 季度归档日志
└── launchd-*.log                     # launchd 日志（如使用）
```

### 5.2 日志格式

```
[时间戳] [级别] 消息
[2026-09-20 15:27:04] [INFO] 开始每周链接检查
[2026-09-20 15:27:05] [WARN] 链接检查发现问题
[2026-09-20 15:27:05] [INFO] 检查报告已生成
```

### 5.3 查看日志

```bash
# 查看最新的链接检查日志
tail -50 logs/link-check-*.log | tail -50

# 查看月度清理日志
tail -50 logs/maintenance-*.log | tail -50

# 查看季度归档日志
tail -50 logs/quarterly-archive-*.log | tail -50

# 实时监控日志
tail -f logs/*.log
```

### 5.4 日志清理

定期清理旧日志（建议保留 30-90 天）：

```bash
# 查看 30 天前的日志
find logs/ -name "*.log" -mtime +30

# 删除 30 天前的日志
find logs/ -name "*.log" -mtime +30 -delete

# 压缩 7 天前的日志
find logs/ -name "*.log" -mtime +7 -exec gzip {} \;
```

### 5.5 日志分析

```bash
# 统计错误数量
grep -c "\[ERROR\]" logs/*.log

# 查找所有警告
grep "\[WARN\]" logs/*.log

# 统计每日任务执行次数
grep "开始.*检查" logs/link-check-*.log | wc -l
```

---

<a id="6-故障排查"></a>

## 6. 故障排查

### 6.1 常见问题

#### 问题 1: 权限不足

**症状**:
```
bash: ./weekly-link-check.sh: Permission denied
```

**解决**:
```bash
chmod +x scripts/ops/maintenance/*.sh
```

#### 问题 2: 离线运维工具 JAR 未构建

**症状**:
```
离线运维工具 JAR 不存在: apex-tools-deployment/target/local-deployment.jar
```

**解决**:
```bash
source scripts/build/init-build-env.sh
mvnd -B -pl :apex-tools-deployment -am verify
```

#### 问题 3: 脚本路径错误

**症状**:
```
No such file or directory
```

**解决**:
```bash
# 确保从项目根目录执行
cd /Users/work/apex-scaffold
pwd  # 应该显示项目根目录

# 或使用绝对路径
/Users/work/apex-scaffold/scripts/ops/maintenance/weekly-link-check.sh
```

#### 问题 4: cron 任务未执行

**症状**: 定时任务没有按计划执行

**排查**:
```bash
# 1. 检查 cron 服务状态（Linux）
systemctl status cron

# 2. 检查 crontab 配置
crontab -l

# 3. 查看系统日志
# macOS
tail -f /var/log/system.log | grep cron

# Linux
tail -f /var/log/syslog | grep CRON

# 4. 确认脚本路径是绝对路径
# ✅ 正确
0 9 * * 1 cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/weekly-link-check.sh

# ❌ 错误（相对路径可能不工作）
0 9 * * 1 ./scripts/ops/maintenance/weekly-link-check.sh
```

#### 问题 5: 日志文件未生成

**症状**: 脚本执行后没有日志文件

**解决**:
```bash
# 1. 确保 logs 目录存在
mkdir -p /Users/work/apex-scaffold/logs

# 2. 检查目录权限
ls -ld /Users/work/apex-scaffold/logs

# 3. 手动测试
./scripts/ops/maintenance/weekly-link-check.sh
```

### 6.2 调试模式

#### 启用详细输出

```bash
# 使用 bash -x 查看执行过程
bash -x ./scripts/ops/maintenance/weekly-link-check.sh
```

#### 检查脚本语法

```bash
# 检查语法错误
bash -n ./scripts/ops/maintenance/weekly-link-check.sh
```

### 6.3 获取帮助

1. **查看文档**:
   - `scripts/ops/maintenance/README.md`
   - `scripts/README.md`

2. **检查日志**:
   - 查看最新的执行日志
   - 搜索 `[ERROR]` 和 `[WARN]` 标记

3. **联系团队**:
   - 提供完整的错误信息
   - 提供相关日志文件
   - 说明执行环境（操作系统、Shell 版本等）

---

<a id="7-最佳实践"></a>

## 7. 最佳实践

### 7.1 执行时间选择

| 任务 | 推荐时间 | 原因 |
|-----|---------|------|
| 链接检查 | 周一 9:00 | 周末可能有更新，周一检查最及时 |
| 月度清理 | 1 号 10:00 | 月初清理，避开月末高峰 |
| 季度归档 | 季度首日 11:00 | 统一归档时间，便于管理 |

**注意**: 避免在高负载时段（上班时间前、下班时间）执行。

### 7.2 监控建议

#### 创建监控脚本

```bash
#!/bin/bash
# scripts/ops/maintenance/check-status.sh

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

echo ""
echo "=== 链接有效率趋势 ==="
grep "有效率" logs/link-check-*.log | tail -5
```

#### 定期检查

```bash
# 每天检查一次任务执行状态
./scripts/ops/maintenance/check-status.sh
```

### 7.3 备份策略

#### 重要文件备份

```bash
# 备份配置
cp -r scripts/ops/maintenance/ ~/backups/maintenance-$(date +%Y%m%d)

# 备份日志
tar -czf ~/backups/logs-$(date +%Y%m%d).tar.gz logs/

# 备份报告
tar -czf ~/backups/reports-$(date +%Y%m%d).tar.gz docs/reports/
```

### 7.4 定期审查

| 频率 | 审查内容 |
|-----|---------|
| 每周 | 查看链接检查报告，修复断裂链接 |
| 每月 | 审查清理日志，确认没有误删 |
| 每季度 | 审查归档内容，清理不需要的文档 |
| 每年 | 评估维护策略，优化执行时间和频率 |

---

<a id="8-faq"></a>

## 8. FAQ

### Q1: 可以同时运行多个维护脚本吗？

**A**: 可以，但不推荐。建议错开执行时间，避免资源竞争。

```bash
# 推荐配置（时间错开）
0 9 * * 1 ... weekly-link-check.sh    # 9:00
0 10 1 * * ... monthly-cleanup.sh     # 10:00
0 11 1 1,4,7,10 * ... quarterly-archive.sh  # 11:00
```

### Q2: 脚本执行失败会发送通知吗？

**A**: 默认不会。如需通知，可以配置邮件或钉钉通知：

```bash
# cron 配置邮件通知
MAILTO=your-email@example.com
0 9 * * 1 cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/weekly-link-check.sh
```

### Q3: 可以修改归档天数吗？

**A**: 可以。编辑 `quarterly-archive.sh`，修改第 21 行：

```bash
# 默认 90 天
cutoff_date=$(date -v-90d +%Y%m%d ...)

# 改为 60 天
cutoff_date=$(date -v-60d +%Y%m%d ...)
```

### Q4: 如何禁用某个维护任务？

**A**: 编辑 crontab，注释掉对应行：

```bash
crontab -e

# 注释掉不需要的任务
# 0 10 1 * * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/monthly-cleanup.sh
```

### Q5: 脚本可以在其他项目中使用吗？

**A**: 可以，但需要修改项目路径。所有脚本开头都有 `PROJECT_ROOT` 变量定义。

### Q6: 如何查看历史执行记录？

**A**: 查看日志文件：

```bash
# 查看所有链接检查记录
ls -lt logs/link-check-*.log

# 统计执行次数
ls logs/link-check-*.log | wc -l
```

### Q7: 维护脚本会影响正在运行的服务吗？

**A**: 不会。所有维护脚本都是只读检查或文件清理，不会影响运行中的服务。

### Q8: 如何测试 cron 任务配置？

**A**: 使用短时间间隔测试：

```bash
# 临时配置（每分钟执行）
*/1 * * * * cd /Users/work/apex-scaffold && ./scripts/ops/maintenance/weekly-link-check.sh

# 观察几分钟后删除测试配置
```

---

## 📚 相关资源

### 文档

- [脚本索引](../../README.md) - 所有脚本总索引
- [维护脚本详细文档](../../scripts/ops/maintenance/README.md) - 维护脚本完整指南
- [文档结构规范](../08-project-management/DOCS_STRUCTURE_STANDARD.md) - 文档组织规范

### 工具

- [链接检查脚本](../../scripts/ci/check-doc-links.sh) - CI 中使用的检查脚本

---

## 📝 更新日志

| 版本 | 日期 | 更新内容 |
|-----|------|---------|
| v1.0 | 2026-09-20 | 初始版本，包含三个维护脚本的完整说明 |

---

## 📧 联系方式

如有问题或建议，请联系：
- **维护团队**: Apex Team
- **文档仓库**: `/Users/work/apex-scaffold`
- **相关文档**: `docs/` 目录

---

**© 2026 Apex Team · 让维护更简单** 🚀
