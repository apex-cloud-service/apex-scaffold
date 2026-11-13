#!/bin/bash
# batch-add-javadoc.sh
# 批量为类添加基础 JavaDoc 框架

set -e

PROJECT_ROOT="/Users/work/apex-scaffold"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "批量添加 JavaDoc 框架工具"
echo "=========================================="
echo ""
echo "⚠️  注意：此脚本仅添加基础框架，具体内容需要手工完善"
echo ""

# 备份文件
backup_file() {
    local file=$1
    cp "$file" "$file.bak"
}

# 获取类名
get_class_name() {
    local file=$1
    basename "$file" .java
}

# 添加类级 JavaDoc（DTO 类）
add_dto_javadoc() {
    local file=$1
    local class_name=$(get_class_name "$file")
    
    # 检查是否已有类级 JavaDoc
    if grep -B5 "^public class $class_name" "$file" | grep -q "/\*\*"; then
        echo "  跳过（已有 JavaDoc）: $file"
        return
    fi
    
    backup_file "$file"
    
    # 在 public class 前插入 JavaDoc
    local javadoc="/**\n * $class_name\n *\n * <p>TODO: 补充类的详细说明\n *\n * @author Apex Team\n * @version 1.0.0\n * @since 1.0.0\n */"
    
    sed -i '' "/^public class $class_name/i\\
$javadoc\\

" "$file"
    
    echo "  ✅ 已添加框架: $file (需手工完善)"
}

# 添加类级 JavaDoc（PO 类）
add_po_javadoc() {
    local file=$1
    local class_name=$(get_class_name "$file")
    
    # 检查是否已有类级 JavaDoc
    if grep -B5 "^public class $class_name" "$file" | grep -q "/\*\*"; then
        echo "  跳过（已有 JavaDoc）: $file"
        return
    fi
    
    backup_file "$file"
    
    # 在 public class 前插入 JavaDoc
    local javadoc="/**\n * $class_name - 持久化对象\n *\n * <p>对应数据库表：TODO: 补充表名\n *\n * @author Apex Team\n * @version 1.0.0\n * @since 1.0.0\n */"
    
    sed -i '' "/^public class $class_name/i\\
$javadoc\\

" "$file"
    
    echo "  ✅ 已添加框架: $file (需手工完善)"
}

# 添加类级 JavaDoc（Mapper 接口）
add_mapper_javadoc() {
    local file=$1
    local class_name=$(get_class_name "$file")
    
    # 检查是否已有类级 JavaDoc
    if grep -B5 "^public interface $class_name" "$file" | grep -q "/\*\*"; then
        echo "  跳过（已有 JavaDoc）: $file"
        return
    fi
    
    backup_file "$file"
    
    # 在 public interface 前插入 JavaDoc
    local javadoc="/**\n * $class_name\n *\n * <p>TODO: 补充 Mapper 的详细说明\n *\n * @author Apex Team\n * @version 1.0.0\n * @since 1.0.0\n */"
    
    sed -i '' "/^public interface $class_name/i\\
$javadoc\\

" "$file"
    
    echo "  ✅ 已添加框架: $file (需手工完善)"
}

# 添加类级 JavaDoc（Service 类）
add_service_javadoc() {
    local file=$1
    local class_name=$(get_class_name "$file")
    
    # 检查是否已有类级 JavaDoc
    if grep -B5 "^public class $class_name" "$file" | grep -q "/\*\*"; then
        echo "  跳过（已有 JavaDoc）: $file"
        return
    fi
    
    backup_file "$file"
    
    # 在 public class 前插入 JavaDoc
    local javadoc="/**\n * $class_name\n *\n * <p>TODO: 补充服务的详细说明\n *\n * @author Apex Team\n * @version 1.0.0\n * @since 1.0.0\n */"
    
    sed -i '' "/^public class $class_name/i\\
$javadoc\\

" "$file"
    
    echo "  ✅ 已添加框架: $file (需手工完善)"
}

echo "🔧 正在处理 DTO 类..."
find apex-system/src/main/java apex-auth/src/main/java apex-file/src/main/java apex-office/src/main/java apex-workflow/src/main/java apex-integration/src/main/java \( -name "*DTO.java" -o -name "*Request.java" -o -name "*Response.java" \) | while read file; do
    add_dto_javadoc "$file"
done

echo ""
echo "🔧 正在处理 PO 类..."
find apex-infrastructure-persistence/src/main/java -name "*PO.java" | while read file; do
    add_po_javadoc "$file"
done

echo ""
echo "🔧 正在处理 Mapper 接口..."
find apex-infrastructure-persistence/src/main/java -name "*Mapper.java" | while read file; do
    add_mapper_javadoc "$file"
done

echo ""
echo "🔧 正在处理 Service 类..."
find apex-system/src/main/java apex-auth/src/main/java apex-file/src/main/java apex-office/src/main/java apex-workflow/src/main/java apex-integration/src/main/java -name "*Service.java" | while read file; do
    add_service_javadoc "$file"
done

echo ""
echo "=========================================="
echo "✅ 批量添加 JavaDoc 框架完成！"
echo "=========================================="
echo ""
echo "⚠️  重要提示："
echo "1. 脚本只添加了基础框架，请手工完善以下内容："
echo "   - 类的详细说明"
echo "   - 主要功能列表"
echo "   - 使用示例"
echo "   - 注意事项"
echo "2. 参考示例文件："
echo "   - docs/04-development/development-standards.md"
echo "   - 各模块既有 Handler/Service 类的实际注释（如 apex-system interfaces/handler）"
echo "3. 完成后运行 'mvnd javadoc:javadoc' 检查生成效果"
echo "4. 如有问题，备份文件在 *.bak"
echo ""
