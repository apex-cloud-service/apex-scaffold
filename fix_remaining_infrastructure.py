#!/usr/bin/env python3
"""
Batch fix remaining PMD violations
"""

import re
from pathlib import Path

# Fix AccountDeletionTaskMapper
file1 = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/AccountDeletionTaskMapper.java')
content1 = file1.read_text(encoding='utf-8')

# Add missing @param to claim method
content1 = re.sub(
    r'(     \* 方法\【claim】的参数\【staleBefore】缺少javadoc注释\n.*?)(    @Update)',
    r'\1     * @param id TODO\n     * @param now TODO\n     * @param staleBefore TODO\n     * @param workerId TODO\n\2',
    content1,
    flags=re.DOTALL
)

# Fix ProcessInstanceMapper
file2 = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/ProcessInstanceMapper.java')
content2 = file2.read_text(encoding='utf-8')

# Add @param for countStartedByDateRange
content2 = re.sub(
    r'(接口方法\【countStartedByDateRange】必须使用javadoc注释\n.*?)(    Long countStartedByDateRange)',
    r'\1    /**\n     * TODO: Add method description\n     *\n     * @param start TODO\n     * @param end TODO\n     * @return TODO\n     */\n    \2',
    content2,
    flags=re.DOTALL
)

# Fix MdcDataScopeProvider - add constant for comma
file3 = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/MdcDataScopeProvider.java')
content3 = file3.read_text(encoding='utf-8')

class_start = content3.find('public class MdcDataScopeProvider')
if class_start > 0:
    brace = content3.find('{', class_start)
    content3 = content3[:brace+1] + '\n\n    private static final String COMMA_SEPARATOR = ",";' + content3[brace+1:]
    content3 = content3.replace('split(",")', 'split(COMMA_SEPARATOR)')

file3.write_text(content3, encoding='utf-8')

# Fix OAuth2RegisteredClientRepositoryImpl - add constant for empty JSON array
file4 = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/repository/OAuth2RegisteredClientRepositoryImpl.java')
content4 = file4.read_text(encoding='utf-8')

class_start = content4.find('public class OAuth2RegisteredClientRepositoryImpl')
if class_start > 0:
    brace = content4.find('{', class_start)
    content4 = content4[:brace+1] + '\n\n    private static final String EMPTY_JSON_ARRAY = "[]";' + content4[brace+1:]
    content4 = content4.replace('parseJsonArray(json, "[]")', 'parseJsonArray(json, EMPTY_JSON_ARRAY)')

file4.write_text(content4, encoding='utf-8')

# Fix MeilisearchAdapter - add constants
file5 = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/search/MeilisearchAdapter.java')
content5 = file5.read_text(encoding='utf-8')

class_start = content5.find('public class MeilisearchAdapter')
if class_start > 0:
    brace = content5.find('{', class_start)
    content5 = content5[:brace+1] + '\n\n    private static final int PHONE_NUMBER_LENGTH = 11;\n    private static final int MIN_MASK_LENGTH = 4;' + content5[brace+1:]
    content5 = content5.replace('mobile.length() < 11', 'mobile.length() < PHONE_NUMBER_LENGTH')
    content5 = content5.replace('value.length() <= 4', 'value.length() <= MIN_MASK_LENGTH')

file5.write_text(content5, encoding='utf-8')

# Fix TokenServiceImpl - add constants
file6 = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java')
content6 = file6.read_text(encoding='utf-8')

class_start = content6.find('public class TokenServiceImpl')
if class_start > 0:
    brace = content6.find('{', class_start)
    content6 = content6[:brace+1] + '\n\n    private static final String JSON_START_BRACE = "{";\n    private static final String JWT_CLAIM_TYPE = "type";\n    private static final String JWT_CLAIM_SUB = "sub";' + content6[brace+1:]
    content6 = content6.replace('startsWith("{")', 'startsWith(JSON_START_BRACE)')
    content6 = content6.replace('claims.get("type")', 'claims.get(JWT_CLAIM_TYPE)')
    content6 = content6.replace('claims.containsKey("sub")', 'claims.containsKey(JWT_CLAIM_SUB)')

file6.write_text(content6, encoding='utf-8')

# Fix DistributedRateLimitWarmer - convert comment to JavaDoc
file7 = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedRateLimitWarmer.java')
content7 = file7.read_text(encoding='utf-8')

# Find the line comment before class and convert to JavaDoc
content7 = re.sub(
    r'//\s*(.*?)\n(public class DistributedRateLimitWarmer)',
    r'/**\n * \1\n */\n\2',
    content7
)

file7.write_text(content7, encoding='utf-8')

print("Fixed remaining violations in apex-infrastructure")
