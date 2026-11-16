#!/usr/bin/env python3
"""活文档指针自检（两仓通用）

检查 1：受控 Markdown 中指向 AI 私有目录的引用（正文应在 docs/）
检查 2：Markdown 相对链接可达性（历史归档整体豁免）

豁免规则（按优先级）：
1. 路径以 docs/_archive/ 开头（历史归档，按规范 §3.6 不改正文）
2. 文件列在 scripts/governance/doc-refs-allowlist.txt 中
3. 该行显式标注「原文未入库」（已交代缺失，不再算问题）

用法：python3 scripts/governance/check-doc-refs.py <repo-root> [strict]
退出码：默认 0；strict=1 且有问题时 1
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ARCHIVE_PREFIX = "docs/_archive/"
PRIV_PATTERN = re.compile(r"\.workbuddy/(knowledge|reports)/|\.zcode/plans/|\.trae/documents/")
LINK_PATTERN = re.compile(r"\]\(([^)#]+\.md)(#[^)]*)?\)")
MISSING_MARK = "原文未入库"


def git_md_files(root: Path):
    out = subprocess.run(
        ["git", "-c", "core.quotepath=false", "ls-files", "*.md"],
        cwd=root, capture_output=True, text=True,
    ).stdout
    return [f for f in out.splitlines() if f]


def load_allowlist(root: Path):
    f = root / "scripts/governance/doc-refs-allowlist.txt"
    if not f.exists():
        return set()
    return {
        line.strip()
        for line in f.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    }


def main() -> int:
    root = Path(sys.argv[1]).resolve()
    strict = len(sys.argv) > 2 and sys.argv[2] == "1"
    allow = load_allowlist(root)
    files = git_md_files(root)
    problems = []

    print("== 1. 私有目录路径残留（受控 Markdown） ==")
    priv = 0
    for rel in files:
        if rel.startswith(ARCHIVE_PREFIX) or rel in allow:
            continue
        path = root / rel
        if not path.exists():
            continue
        try:
            lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
        except OSError:
            continue
        for no, line in enumerate(lines, 1):
            if not PRIV_PATTERN.search(line):
                continue
            if MISSING_MARK in line:
                continue
            print(f"  [WARN] {rel}:{no} 指向私有目录（正文应在 docs/）")
            priv += 1
    if priv == 0:
        print("  [OK]   无私有目录路径残留")
    problems.extend(["priv"] * priv)

    print("== 2. Markdown 相对链接可达性 ==")
    link = 0
    for rel in files:
        if rel.startswith(ARCHIVE_PREFIX) or rel in allow:
            continue
        path = root / rel
        if not path.exists():
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for m in LINK_PATTERN.finditer(text):
            target = m.group(1)
            if target.startswith(("http", "/")):
                continue
            if not (path.parent / target).exists():
                line_no = text[: m.start()].count("\n") + 1
                print(f"  [WARN] 断链 {rel}:{line_no} -> {target}")
                link += 1
    if link == 0:
        print("  [OK]   无断链")
    problems.extend(["link"] * link)

    print(f"== 结果：{len(problems)} 项 ==")
    if strict and problems:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
