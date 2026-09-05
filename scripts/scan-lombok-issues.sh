#!/bin/bash
# scan-lombok-issues.sh
# 扫描项目中 Lombok 使用不规范的文件

set -e

PROJECT_ROOT="/Users/work/apex-scaffold"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "扫描 Lombok 使用问题..."
echo "=========================================="
echo ""

# 清理旧的扫描结果
rm -f lombok-issues-*.txt

# 1. 查找手写 getter/setter 方法的类
echo "==> [1/5] 查找手写 getter/setter..."
find apex-*/src/main/java -name "*.java" -type f \
    -exec grep -l "public [A-Z].*get[A-Z].*(" {} \; 2>/dev/null | \
    grep -v "/test/" | sort | uniq > lombok-issues-getter.txt || true
GETTER_COUNT=$(wc -l < lombok-issues-getter.txt | tr -d ' ')
echo "    发现 ${GETTER_COUNT} 个文件包含手写 getter"

# 2. 查找 DTO/Request/Response 类但没有 @Builder 的
echo "==> [2/5] 查找缺少 @Builder 的 DTO/Request/Response 类..."
{
    find apex-application/src/main/java -path "*/dto/*.java" -type f
    find apex-application/src/main/java -name "*Request.java" -type f
    find apex-application/src/main/java -name "*Response.java" -type f
    find apex-domain/src/main/java -name "*DTO.java" -type f
} 2>/dev/null | sort | uniq | while read file; do
    if [ -f "$file" ] && ! grep -q "@Builder" "$file"; then
        echo "$file"
    fi
done > lombok-issues-builder.txt || true
BUILDER_COUNT=$(wc -l < lombok-issues-builder.txt | tr -d ' ')
echo "    发现 ${BUILDER_COUNT} 个 DTO 类缺少 @Builder"

# 3. 查找 Service 类有手写构造器但没有 @RequiredArgsConstructor 的
echo "==> [3/5] 查找 Service 类手写构造器..."
find apex-*/src/main/java -name "*Service.java" -type f 2>/dev/null | \
    grep -v "/test/" | while read file; do
    if grep -q "public.*Service\s*(" "$file" && ! grep -q "@RequiredArgsConstructor" "$file"; then
        echo "$file"
    fi
done > lombok-issues-constructor.txt || true
CONSTRUCTOR_COUNT=$(wc -l < lombok-issues-constructor.txt | tr -d ' ')
echo "    发现 ${CONSTRUCTOR_COUNT} 个 Service 类有手写构造器"

# 4. 查找没有 @Slf4j 但有 Logger 字段的
echo "==> [4/5] 查找手写 Logger 字段..."
find apex-*/src/main/java -name "*.java" -type f 2>/dev/null | \
    grep -v "/test/" | while read file; do
    if grep -q "private.*Logger.*log" "$file" && ! grep -q "@Slf4j" "$file"; then
        echo "$file"
    fi
done > lombok-issues-logger.txt || true
LOGGER_COUNT=$(wc -l < lombok-issues-logger.txt | tr -d ' ')
echo "    发现 ${LOGGER_COUNT} 个文件有手写 Logger"

# 5. 查找 PO 类缺少构造器注解的
echo "==> [5/5] 查找 PO 类缺少构造器注解..."
find apex-infrastructure/src/main/java -path "*/po/*PO.java" -type f 2>/dev/null | while read file; do
    if ! grep -q "@NoArgsConstructor" "$file" || ! grep -q "@AllArgsConstructor" "$file"; then
        echo "$file"
    fi
done > lombok-issues-po-constructor.txt || true
PO_COUNT=$(wc -l < lombok-issues-po-constructor.txt | tr -d ' ')
echo "    发现 ${PO_COUNT} 个 PO 类缺少构造器注解"

echo ""
echo "=========================================="
echo "扫描完成！"
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
