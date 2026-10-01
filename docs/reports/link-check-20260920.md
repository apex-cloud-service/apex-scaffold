# 文档链接检查报告

**检查时间**: 2026-09-20 15:27:05  
**状态**: FAILED

## 检查结果

详见日志文件: `logs/link-check-20260920.log`

## 修复建议

如果发现断裂链接，可使用以下工具修复：

```bash
# 自动修复工具
python3 scripts/utils/fix-doc-links.py

# 手动检查
./scripts/ci/check-doc-links.sh
```

---

*此报告由每周链接检查脚本自动生成*
