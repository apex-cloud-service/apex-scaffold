#!/bin/bash
# batch-add-lombok.sh
# 批量为 DTO/PO 类添加 Lombok 注解

set -e

PROJECT_ROOT="/Users/work/apex-scaffold"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "批量添加 Lombok 注解工具"
echo "=========================================="
echo ""

# 备份文件
backup_file() {
    local file=$1
    cp "$file" "$file.bak"
}

# 为 DTO 类添加 @Builder 注解
add_builder_to_dto() {
    local file=$1
    
    # 检查是否已有 @Builder
    if grep -q "@Builder" "$file"; then
        echo "  跳过（已有 @Builder）: $file"
        return
    fi
    
    # 检查是否有 @Data
    if ! grep -q "@Data" "$file"; then
        echo "  跳过（没有 @Data）: $file"
        return
    fi
    
    backup_file "$file"
    
    # 1. 添加 import
    if ! grep -q "import lombok.Builder;" "$file"; then
        sed -i '' '/import lombok.Data;/a\
import lombok.Builder;
' "$file"
    fi
    
    if ! grep -q "import lombok.NoArgsConstructor;" "$file"; then
        sed -i '' '/import lombok.Builder;/a\
import lombok.NoArgsConstructor;
' "$file"
    fi
    
    if ! grep -q "import lombok.AllArgsConstructor;" "$file"; then
        sed -i '' '/import lombok.NoArgsConstructor;/a\
import lombok.AllArgsConstructor;
' "$file"
    fi
    
    # 2. 在 @Data 后添加注解
    sed -i '' '/@Data$/a\
@Builder\
@NoArgsConstructor\
@AllArgsConstructor
' "$file"
    
    echo "  ✅ 已添加: $file"
}

# 为 PO 类添加构造器注解
add_constructors_to_po() {
    local file=$1
    
    # 检查是否已有构造器注解
    if grep -q "@NoArgsConstructor" "$file" && grep -q "@AllArgsConstructor" "$file"; then
        echo "  跳过（已有构造器注解）: $file"
        return
    fi
    
    # 检查是否有 @Data
    if ! grep -q "@Data" "$file"; then
        echo "  跳过（没有 @Data）: $file"
        return
    fi
    
    backup_file "$file"
    
    # 1. 添加 import
    if ! grep -q "import lombok.Builder;" "$file"; then
        sed -i '' '/import lombok.Data;/a\
import lombok.Builder;
' "$file"
    fi
    
    if ! grep -q "import lombok.NoArgsConstructor;" "$file"; then
        sed -i '' '/import lombok.Builder;/a\
import lombok.NoArgsConstructor;
' "$file"
    fi
    
    if ! grep -q "import lombok.AllArgsConstructor;" "$file"; then
        sed -i '' '/import lombok.NoArgsConstructor;/a\
import lombok.AllArgsConstructor;
' "$file"
    fi
    
    # 2. 在 @Data 后添加注解
    if ! grep -q "@Builder" "$file"; then
        sed -i '' '/@Data$/a\
@Builder
' "$file"
    fi
    
    if ! grep -q "@NoArgsConstructor" "$file"; then
        sed -i '' '/@Data$/a\
@NoArgsConstructor
' "$file"
    fi
    
    if ! grep -q "@AllArgsConstructor" "$file"; then
        sed -i '' '/@NoArgsConstructor$/a\
@AllArgsConstructor
' "$file"
    fi
    
    echo "  ✅ 已添加: $file"
}

echo "🔧 正在处理 DTO 类..."
find apex-system/src/main/java apex-auth/src/main/java apex-file/src/main/java apex-office/src/main/java apex-workflow/src/main/java apex-integration/src/main/java \( -name "*DTO.java" -o -name "*Request.java" -o -name "*Response.java" \) | while read file; do
    add_builder_to_dto "$file"
done

echo ""
echo "🔧 正在处理 PO 类..."
find apex-infrastructure-persistence/src/main/java -name "*PO.java" | while read file; do
    add_constructors_to_po "$file"
done

echo ""
echo "=========================================="
echo "✅ 批量添加完成！"
echo "=========================================="
echo ""
echo "提示："
echo "1. 请使用 'mvnd clean compile -DskipTests' 验证"
echo "2. 如有问题，备份文件在 *.bak"
echo "3. 验证通过后可删除备份：find . -name '*.bak' -delete"
echo ""
