#!/bin/bash

###############################################################################
# check-doc-links.sh - 扫描 docs/ 目录 Markdown 内部链接的有效性
# 用法：./scripts/ci/check-doc-links.sh  无参数；依赖 python3 与 scripts/lib/common.sh
# 作者：Apex Team    创建：2026-09-13
###############################################################################
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCS_DIR="$PROJECT_ROOT/docs"

require_command python3 || exit 1
require_directory "$DOCS_DIR" || exit 1
SCAN_DIR=$(mktemp -d "${TMPDIR:-/tmp}/doc-links.XXXXXX")
trap 'rm -rf "$SCAN_DIR"' EXIT
safe_find_custom "$DOCS_DIR" -name '*.md' -type f -print0 > "$SCAN_DIR/files"
log_info "开始扫描 docs 目录中的内部链接..."

python3 - "$SCAN_DIR/files" <<'PY'
from pathlib import Path
import bisect
import html
import os
import re
import sys
from urllib.parse import unquote, urlsplit


def mask_code(text):
    fence = None
    lines = []
    for line in text.splitlines(keepends=True):
        match = re.match(r"^ {0,3}(`{3,}|~{3,})", line)
        if fence:
            if match and match[1][0] == fence[0] and len(match[1]) >= len(fence) and not line[match.end():].strip():
                fence = None
            lines.append(re.sub(r"[^\n]", " ", line))
        elif match:
            fence = match[1]
            lines.append(re.sub(r"[^\n]", " ", line))
        else:
            lines.append(line)
    text = "".join(lines)
    text = re.sub(r"<!--.*?-->", lambda m: re.sub(r"[^\n]", " ", m[0]), text, flags=re.S)
    return re.sub(r"(`+)(?!`)(.*?)(?<!`)\1(?!`)",
                  lambda m: re.sub(r"[^\n]", " ", m[0]), text, flags=re.S)


def destination(text, start):
    pos = start
    while pos < len(text) and text[pos].isspace():
        pos += 1
    if pos == len(text):
        return None
    if text[pos] == "<":
        match = re.match(r"<((?:\\.|[^<>\n])*)>", text[pos:])
        if not match:
            return None
        return match[1], pos + match.end()
    start = pos
    depth = 0
    while pos < len(text):
        char = text[pos]
        if char == "\\" and pos + 1 < len(text):
            pos += 2
            continue
        if char.isspace() or (char == ")" and depth == 0):
            break
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        pos += 1
    if depth:
        return None
    return text[start:pos], pos


def label_key(label):
    return " ".join(label.split()).casefold()


def links(text):
    text = mask_code(text)
    references = {}
    for match in re.finditer(r"^ {0,3}\[([^\]\n]+)\]:[ \t]*", text, re.M):
        value = destination(text, match.end())
        if value:
            references.setdefault(label_key(match[1]), value[0])
    text = re.sub(r"^ {0,3}\[[^\]\n]+\]:[^\n]*", lambda m: " " * len(m[0]), text, flags=re.M)
    pattern = re.compile(r"(?<!\\)\[((?:\\.|[^\[\]\\]|\[[^\]\n]*\])*)\]")
    pos = 0
    while match := pattern.search(text, pos):
        end = match.end()
        pos = end
        if text[end:end + 1] == "(":
            value = destination(text, end + 1)
            if value:
                path, tail = value
                suffix = re.match(r"\s*(?:\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|\((?:\\.|[^)\\])*\))?\s*\)", text[tail:])
                if suffix:
                    pos = tail + suffix.end()
                    yield match.start(), path
        else:
            ref = re.match(r"\[([^\]\n]*)\]", text[end:])
            key = label_key(ref[1] or match[1]) if ref else label_key(match[1])
            if ref:
                pos = end + ref.end()
            if key in references:
                yield match.start(), references[key]


def target_path(source, destination):
    value = html.unescape(re.sub(r"\\([!\"#$%&'()*+,\-./:;<=>?@\[\]\\^_`{|}~ ])", r"\1", destination))
    if not value or value.startswith(("#", "//")):
        return None
    uri = urlsplit(value)
    if uri.scheme and uri.scheme.lower() != "file":
        return None
    if uri.scheme.lower() == "file" and uri.netloc and uri.netloc.lower() != "localhost":
        return Path("//" + uri.netloc + unquote(uri.path))
    path = unquote(uri.path)
    if not path:
        return None
    return Path(os.path.abspath(source.parent / path))


total = 0
broken = 0
for name in sorted(Path(sys.argv[1]).read_bytes().split(b"\0")):
    if not name:
        continue
    source = Path(os.fsdecode(name))
    text = source.read_text(encoding="utf-8")
    newlines = [m.start() for m in re.finditer("\n", text)]
    for offset, value in links(text):
        target = target_path(source, value)
        if target is None:
            continue
        total += 1
        if not target.exists():
            broken += 1
            line = bisect.bisect_left(newlines, offset) + 1
            print(f"坏链接: {source}:{line}: {value} -> {target}")
print("扫描完成")
print(f"   总链接数: {total}")
print(f"   断裂链接: {broken}")
sys.exit(1 if broken else 0)
PY
