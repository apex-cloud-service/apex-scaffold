#!/bin/bash
# scan-lombok-issues.sh
# 扫描项目中 Lombok 使用不规范的文件

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || exit
source "${SCRIPT_DIR}/../../lib/common.sh" || exit
setup_error_trap
PROJECT_ROOT=$(get_project_root)
cd -- "$PROJECT_ROOT"
JAVA_FILES=$(safe_find "apex-*/src/main/java" "*.java")
DTO_FILES=$(safe_find_custom "apex-application/src/main/java" -type f '(' -path '*/dto/*.java' -o -name '*Request.java' -o -name '*Response.java' ')')
DOMAIN_DTOS=$(safe_find "apex-domain/src/main/java" "*DTO.java")
DTO_FILES=$(printf '%s\n' "$DTO_FILES" "$DOMAIN_DTOS" | sort -u)
PO_FILES=$(safe_find_custom "apex-infrastructure/src/main/java" -path '*/po/*PO.java' -type f)

print_header "扫描 Lombok 使用问题..."

# 1. 查找手写 getter/setter 方法的类
echo "==> [1/5] 查找手写 getter/setter..."
while IFS= read -r file; do
    [[ -n "$file" && "$file" != */test/* ]] || continue
    MATCHES=$(safe_count 'public [A-Z].*get[A-Z].*(' "$file")
    if safe_gt "$MATCHES" 0; then
        printf '%s\n' "${file#./}"
    fi
done <<< "$JAVA_FILES" | sort -u > lombok-issues-getter.txt
GETTER_COUNT=$(safe_count '' lombok-issues-getter.txt)
echo "    发现 ${GETTER_COUNT} 个文件包含手写 getter"

# 2. 查找 DTO/Request/Response 类但没有 @Builder 的
echo "==> [2/5] 查找缺少 @Builder 的 DTO/Request/Response 类..."
while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    MATCHES=$(safe_count '@Builder' "$file")
    if safe_eq "$MATCHES" 0; then
        printf '%s\n' "${file#./}"
    fi
done <<< "$DTO_FILES" > lombok-issues-builder.txt
BUILDER_COUNT=$(safe_count '' lombok-issues-builder.txt)
echo "    发现 ${BUILDER_COUNT} 个 DTO 类缺少 @Builder"

# 3. 查找 Service 类有手写构造器但没有 @RequiredArgsConstructor 的
echo "==> [3/5] 查找 Service 类手写构造器..."
while IFS= read -r file; do
    [[ "$file" == *Service.java && "$file" != */test/* ]] || continue
    MATCHES=$(safe_count 'public.*Service[[:space:]]*(' "$file")
    ANNOTATIONS=$(safe_count '@RequiredArgsConstructor' "$file")
    if safe_gt "$MATCHES" 0 && safe_eq "$ANNOTATIONS" 0; then
        printf '%s\n' "${file#./}"
    fi
done <<< "$JAVA_FILES" > lombok-issues-constructor.txt
CONSTRUCTOR_COUNT=$(safe_count '' lombok-issues-constructor.txt)
echo "    发现 ${CONSTRUCTOR_COUNT} 个 Service 类有手写构造器"

# 4. 查找没有 @Slf4j 但有 Logger 字段的
echo "==> [4/5] 查找手写 Logger 字段..."
while IFS= read -r file; do
    [[ -n "$file" && "$file" != */test/* ]] || continue
    MATCHES=$(safe_count 'private.*Logger.*log' "$file")
    ANNOTATIONS=$(safe_count '@Slf4j' "$file")
    if safe_gt "$MATCHES" 0 && safe_eq "$ANNOTATIONS" 0; then
        printf '%s\n' "${file#./}"
    fi
done <<< "$JAVA_FILES" > lombok-issues-logger.txt
LOGGER_COUNT=$(safe_count '' lombok-issues-logger.txt)
echo "    发现 ${LOGGER_COUNT} 个文件有手写 Logger"

# 5. 查找 PO 类缺少构造器注解的
echo "==> [5/5] 查找 PO 类缺少构造器注解..."
while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    NO_ARGS=$(safe_count '@NoArgsConstructor' "$file")
    ALL_ARGS=$(safe_count '@AllArgsConstructor' "$file")
    if safe_eq "$NO_ARGS" 0 || safe_eq "$ALL_ARGS" 0; then
        printf '%s\n' "${file#./}"
    fi
done <<< "$PO_FILES" > lombok-issues-po-constructor.txt
PO_COUNT=$(safe_count '' lombok-issues-po-constructor.txt)
echo "    发现 ${PO_COUNT} 个 PO 类缺少构造器注解"

echo ""
echo "=========================================="
log_success "扫描完成！"
echo "=========================================="
echo ""
echo "问题文件统计："
echo "  - 手写 getter/setter:           ${GETTER_COUNT} 个"
echo "  - DTO 缺少 @Builder:            ${BUILDER_COUNT} 个"
echo "  - Service 手写构造器:           ${CONSTRUCTOR_COUNT} 个"
echo "  - 手写 Logger:                  ${LOGGER_COUNT} 个"
echo "  - PO 缺少构造器注解:            ${PO_COUNT} 个"
echo ""
TOTAL=$((GETTER_COUNT + BUILDER_COUNT + CONSTRUCTOR_COUNT + LOGGER_COUNT + PO_COUNT))
echo "  总计: ${TOTAL} 个问题文件"
echo ""
echo "详细结果已保存到："
echo "  - lombok-issues-getter.txt"
echo "  - lombok-issues-builder.txt"
echo "  - lombok-issues-constructor.txt"
echo "  - lombok-issues-logger.txt"
echo "  - lombok-issues-po-constructor.txt"
echo ""
