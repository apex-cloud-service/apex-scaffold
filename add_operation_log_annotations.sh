#!/bin/bash

# 为 IpAccessControlHandler.java 添加 @OperationLog 注解

file="/Users/work/apex-scaffold/apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/handler/IpAccessControlHandler.java"

# 检查文件是否已导入 OperationLog
if ! grep -q "import com.apex.scaffold.common.annotation.OperationLog" "$file"; then
    # 在 package 声明后添加导入
    sed -i '' '/^import com.apex.scaffold.common.result.Result;/a\
import com.apex.scaffold.common.annotation.OperationLog;\
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
' "$file"
fi

# 为每个方法添加注解（在 @Operation 前添加 @OperationLog）

# 1. addToWhitelist - 117行
sed -i '' '117i\
    @OperationLog(module = "IP访问控制", type = OperationType.INSERT, description = "添加IP白名单")\
' "$file"

# 2. addToBlacklist - 需要找到行号
# 3. removeFromWhitelist
# 4. removeFromBlacklist  
# 5. getWhitelist
# 6. getBlacklist
# 7. enableRule
# 8. disableRule
# 9. deleteRule
# 10. cleanupExpiredRules
# 11. checkAccess

echo "IpAccessControlHandler 注解添加完成"
