#!/bin/bash
# 为 OAuth2 相关类添加 @SuppressWarnings 注解

cd /Users/work/apex-scaffold/apex-application/src/main/java

# 定义需要处理的类
classes=(
    "com/apex/scaffold/application/assembler/OAuth2ClientAssembler.java"
    "com/apex/scaffold/application/dto/OAuth2ClientRequest.java"
    "com/apex/scaffold/application/dto/OAuth2ClientResponse.java"
    "com/apex/scaffold/application/dto/OAuth2ConsentRequest.java"
    "com/apex/scaffold/application/dto/OAuth2ConsentResponse.java"
    "com/apex/scaffold/application/service/OAuth2AuthorizationServiceImpl.java"
    "com/apex/scaffold/application/service/OAuth2ClientManagementService.java"
    "com/apex/scaffold/application/service/OAuth2ClientSecretRotationService.java"
    "com/apex/scaffold/application/service/impl/OAuth2ClientSecretRotationServiceImpl.java"
)

for file in "${classes[@]}"; do
    if [ -f "$file" ]; then
        echo "Processing $file"
        # 在类声明前添加 @SuppressWarnings 注解
        # 查找 public class/interface 声明，在它之前插入注解
        sed -i '' '/^public \(class\|interface\|enum\|record\) /i\
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
' "$file"
    else
        echo "File not found: $file"
    fi
done

echo "Done!"
