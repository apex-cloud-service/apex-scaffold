#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/src"
cat > "$TMP_DIR/src/Documented.java" <<'JAVA'
/**
 * Example declaration:
 * <pre>
 * public class Example {}
 * </pre>
 */
public class Documented {}
JAVA
cat > "$TMP_DIR/src/Undocumented.java" <<'JAVA'
public class Undocumented {}
JAVA
cat > "$TMP_DIR/src/Annotated.java" <<'JAVA'
/**
 * A documented type with an annotation between its doc and declaration.
 */
@SuppressWarnings("unused")
public class Annotated {}
JAVA
cat > "$TMP_DIR/src/DocumentedRecord.java" <<'JAVA'
/**
 * A documented record.
 */
public record DocumentedRecord(String value) {}
JAVA
cat > "$TMP_DIR/src/DocumentedAnnotation.java" <<'JAVA'
/**
 * A documented annotation type.
 */
public @interface DocumentedAnnotation {}
JAVA
cat > "$TMP_DIR/src/CommentExamples.java" <<'JAVA'
// public class LineExample {}
/* public class BlockExample {} */
/**
 * Contains comment examples that are not declarations.
 */
public class CommentExamples {}
JAVA

set +e
(cd "$TMP_DIR" && bash "$ROOT_DIR/scripts/dev/check/check_javadoc.sh" "$TMP_DIR/src") > "$TMP_DIR/output.txt" 2>&1
status=$?
set -e

if [[ $status -eq 0 ]]; then
    printf 'Expected scan to fail because Undocumented.java has no class JavaDoc\n' >&2
    exit 1
fi

if ! grep -q '缺少JavaDoc: 1' "$TMP_DIR/output.txt"; then
    printf 'Expected exactly one missing class JavaDoc; scanner output was:\n' >&2
    cat "$TMP_DIR/output.txt" >&2
    exit 1
fi

if grep -q 'Documented.java' "$TMP_DIR/output.txt"; then
    printf 'Documented.java was incorrectly reported as missing JavaDoc\n' >&2
    cat "$TMP_DIR/output.txt" >&2
    exit 1
fi

if ! grep -q 'Undocumented.java' "$TMP_DIR/output.txt"; then
    printf 'Undocumented.java was not reported as missing class JavaDoc\n' >&2
    cat "$TMP_DIR/output.txt" >&2
    exit 1
fi

OUTPUT_DIR="$TMP_DIR/scan-output"
mkdir -p "$OUTPUT_DIR"
(cd "$ROOT_DIR" && JAVADOC_OUTPUT_DIR="$OUTPUT_DIR" JAVADOC_SCAN_PATH="$TMP_DIR/src" bash scripts/dev/check/scan-javadoc-issues.sh) > "$TMP_DIR/scan-output.txt" 2>&1

if grep -q 'Documented.java' "$OUTPUT_DIR/javadoc-issues-class.txt"; then
    printf 'Documented.java was incorrectly reported by scan-javadoc-issues.sh\n' >&2
    exit 1
fi

if ! grep -q 'Undocumented.java' "$OUTPUT_DIR/javadoc-issues-class.txt"; then
    printf 'Undocumented.java was not reported by scan-javadoc-issues.sh\n' >&2
    exit 1
fi

printf 'PASS: check_javadoc ignores comment examples and identifies real missing docs\n'
