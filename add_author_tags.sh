#!/bin/bash

# 批量为 Java 文件添加 @author 标签

# 查找所有需要添加 @author 的 Java 文件
find apex-application/src/main/java -name "*.java" -type f | while read -r file; do
    # 检查文件是否已有 @author 标签
    if grep -q "@author" "$file"; then
        continue
    fi
    
    # 检查文件是否有类级别的 Javadoc
    if ! grep -q "/\*\*" "$file"; then
        continue
    fi
    
    # 使用 sed 在类 Javadoc 的最后一个 */ 之前添加 @author
    # 查找第一个 public class/interface/enum 之前的最后一个 */
    sed -i '' '/^public \(class\|interface\|enum\)/,/^public \(class\|interface\|enum\)/{
        /\*\//{ 
            s|\*/| *\n * @author Apex Team\n */|
            :a
            n
            ba
        }
    }' "$file" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo "Added @author to: $file"
    fi
done

echo "Done!"
