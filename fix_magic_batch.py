#!/usr/bin/env python3
"""
Batch fix common magic constants and other violations
"""

from pathlib import Path
import re

def add_constants_to_class(file_path, constants_dict):
    """Add constants after class declaration"""
    content = file_path.read_text(encoding='utf-8')
    
    # Find class declaration
    class_match = re.search(r'(public\s+(?:class|interface)\s+\w+[^{]*\{)', content)
    if not class_match:
        return False
    
    insert_pos = class_match.end()
    
    # Build constants block
    constants_lines = []
    for name, (type_str, value) in constants_dict.items():
        constants_lines.append(f'    private static final {type_str} {name} = {value};')
    
    if not constants_lines:
        return False
    
    constants_block = '\n\n' + '\n'.join(constants_lines) + '\n'
    
    # Insert constants
    content = content[:insert_pos] + constants_block + content[insert_pos:]
    
    # Replace magic values with constants
    for name, (_, value) in constants_dict.items():
        # Be careful with regex escaping
        if value.startswith('"'):
            # String constant - exact match with quotes
            content = re.sub(rf'(?<![A-Z_]){re.escape(value)}(?![A-Z_])', name, content)
        else:
            # Numeric constant
            content = re.sub(rf'(?<!\w){re.escape(value)}(?!\w)', name, content)
    
    file_path.write_text(content, encoding='utf-8')
    return True

# Fix common HTTP status codes in moderation adapters
http_constants = {
    'HTTP_STATUS_OK': ('int', '200'),
    'HTTP_STATUS_REDIRECT_THRESHOLD': ('int', '300'),
}

files_with_http = [
    'apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/moderation/BaiduImageModerationAdapter.java',
    'apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/moderation/BaiduTextModerationAdapter.java',
]

for f in files_with_http:
    path = Path('/Users/work/apex-scaffold') / f
    if path.exists():
        add_constants_to_class(path, http_constants)
        print(f"Fixed HTTP constants in {f}")

# Fix conclusion type constant
conclusion_constants = {
    'CONCLUSION_TYPE_FAILED': ('int', '4'),
}

conclusion_files = [
    'apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/moderation/BaiduImageModerationAdapter.java',
    'apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/moderation/BaiduTextModerationAdapter.java',
]

for f in conclusion_files:
    path = Path('/Users/work/apex-scaffold') / f
    if path.exists():
        content = path.read_text(encoding='utf-8')
        # Only replace in specific context
        content = re.sub(r'conclusionType\s*==\s*4', 'conclusionType == CONCLUSION_TYPE_FAILED', content)
        path.write_text(content, encoding='utf-8')

# Fix probability constant
prob_constants = {
    'JSON_FIELD_PROBABILITY': ('String', '"probability"'),
}

for f in conclusion_files:
    path = Path('/Users/work/apex-scaffold') / f
    if path.exists():
        add_constants_to_class(path, prob_constants)
        print(f"Fixed probability constant in {f}")

# Fix DLQ constants
dlq_constants = {
    'EXCEPTION_TIMEOUT': ('String', '"TimeoutException"'),
    'REASON_TIMEOUT': ('String', '"timeout"'),
    'EXCEPTION_CONNECT': ('String', '"ConnectException"'),
    'REASON_NETWORK': ('String', '"network"'),
    'REASON_JSON': ('String', '"JSON"'),
    'REASON_PARSE': ('String', '"parse"'),
    'REASON_BUSINESS': ('String', '"Business"'),
}

dlq_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/dlq/DeadLetterQueueProcessor.java')
if dlq_file.exists():
    add_constants_to_class(dlq_file, dlq_constants)
    print(f"Fixed DLQ constants")

# Fix image preprocessor constants
img_constants = {
    'MIN_DIMENSION_DIVISOR': ('int', '8'),
    'COMPRESSION_QUALITY': ('float', '0.08F'),
}

img_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/moderation/BaiduImagePreprocessor.java')
if img_file.exists():
    add_constants_to_class(img_file, img_constants)
    print(f"Fixed image preprocessor constants")

# Fix ChuanglanFlashClient null string
null_constants = {
    'NULL_STRING': ('String', '"null"'),
}

chuanglan_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanFlashClient.java')
if chuanglan_file.exists():
    add_constants_to_class(chuanglan_file, null_constants)
    print(f"Fixed null string constant")

# Fix DomainEventOutboxDispatcher timeout
timeout_constants = {
    'SHUTDOWN_TIMEOUT_SECONDS': ('int', '30'),
}

event_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/event/DomainEventOutboxDispatcher.java')
if event_file.exists():
    add_constants_to_class(event_file, timeout_constants)
    print(f"Fixed timeout constant")

# Fix UserMessagePayload regex
regex_constants = {
    'MESSAGE_ID_PATTERN': ('String', '"[A-Za-z0-9._-]{1,64}"'),
}

payload_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/listener/UserMessagePayload.java')
if payload_file.exists():
    add_constants_to_class(payload_file, regex_constants)
    print(f"Fixed message ID pattern")

# Fix MqLagMonitor max consumers
max_consumer_constants = {
    'MAX_CONSUMER_COUNT': ('int', '32'),
}

lag_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/monitor/MqLagMonitor.java')
if lag_file.exists():
    add_constants_to_class(lag_file, max_consumer_constants)
    print(f"Fixed max consumer constant")

# Fix office client
office_constants = {
    'ZERO_STRING': ('String', '"0"'),
}

office_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/client/AbstractOfficeClient.java')
if office_file.exists():
    add_constants_to_class(office_file, office_constants)
    print(f"Fixed office client constant")

# Fix OfficeRepositoryImpl page size
page_constants = {
    'DEFAULT_PAGE_SIZE': ('int', '100'),
}

repo_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/persistence/OfficeRepositoryImpl.java')
if repo_file.exists():
    add_constants_to_class(repo_file, page_constants)
    print(f"Fixed page size constant")

# Fix OfficeMessageConverter constants
office_msg_constants = {
    'RECIPIENT_TYPE_USER': ('String', '"USER"'),
    'RECIPIENT_TYPE_GROUP': ('String', '"GROUP"'),
}

office_msg_files = [
    'apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/persistence/converter/OfficeMessageConverter.java',
    'apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter/OfficeMessageConverter.java',
]

for f in office_msg_files:
    path = Path('/Users/work/apex-scaffold') / f
    if path.exists():
        add_constants_to_class(path, office_msg_constants)
        print(f"Fixed office message constants in {f}")

# Fix HmacOfficeSignatureVerifier
hmac_constants = {
    'HEX_RADIX': ('int', '2'),
}

hmac_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/security/HmacOfficeSignatureVerifier.java')
if hmac_file.exists():
    add_constants_to_class(hmac_file, hmac_constants)
    print(f"Fixed HMAC constant")

# Fix OfficeMessageServiceImpl
office_svc_constants = {
    'STATUS_FAILED': ('String', '"FAILED"'),
}

office_svc_file = Path('/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/service/OfficeMessageServiceImpl.java')
if office_svc_file.exists():
    add_constants_to_class(office_svc_file, office_svc_constants)
    print(f"Fixed office service constant")

print("\nAll common magic constants fixed!")
