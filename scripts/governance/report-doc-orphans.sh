#!/usr/bin/env bash
# 文档孤岛盘点（BE-002）
#
# 口径：docs/ 下已入库的 .md，既不是 README，也没有被 docs/README.md 或任一目录
#      README.md 以相对链接指向 → 记为「孤岛」。
# 只读：不移动、不删除任何文件。输出 Markdown 清单到标准输出，可重定向存档。
#
# 用法：bash scripts/governance/report-doc-orphans.sh > /tmp/orphans.md

set -euo pipefail
cd "$(dirname "$0")/../.."

python3 - <<'PY'
import re, subprocess, pathlib
mds = [f for f in subprocess.run(
    ["git", "-c", "core.quotepath=false", "ls-files", "docs"],
    capture_output=True, text=True, check=True).stdout.splitlines() if f.endswith(".md")]
readmes = [f for f in mds if f.endswith("README.md")]
refs = set()
link = re.compile(r"\]\(([^)]+)\)")
for f in readmes:
    base = pathlib.PurePosixPath(f).parent
    for m in link.findall(pathlib.Path(f).read_text(encoding="utf-8", errors="ignore")):
        t = m.split("#")[0].strip()
        if not t or t.startswith(("http", "mailto")):
            continue
        refs.add(re.sub(r"^\./", "", str(base / t) if not t.startswith("/") else t.lstrip("/")))
orph = sorted(f for f in mds if f not in refs and f not in readmes)
print(f"# 文档孤岛清单\n\nmd 总数 {len(mds)}（README {len(readmes)}）· 被索引引用 {len(refs)} · 孤岛 {len(orph)}\n")
for f in orph:
    print(f"- `{f}`")
PY
