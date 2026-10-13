# ============================================================
# Apex Scaffold - Nacos Sentinel 规则模板目录
# ============================================================
# 【作用】
#   每个微服务一份「限流 flow / 熔断 degrade」JSON 模板，发布到 Nacos
#   （组 SENTINEL_GROUP，Data ID 见下表），供各服务 `apex.sentinel.nacos`
#   热更新。发布为整文件，推送为整体替换语义。
#
# 【命名约定】
#   Data ID = {service}-flow-rules / {service}-degrade-rules
#   其中 {service} = spring.application.name（apex-xxx-service）。
#
# 【密钥禁令】
#   规则均为资源阈值，无任何密钥；如需按来源限定可改 limitApp。
#
# @author Apex Team
# ============================================================

# 各服务规则模板（随 Phase 引入使用）：
#   apex-auth-service-flow-rules.json      — 认证服务限流
#   apex-auth-service-degrade-rules.json   — 认证服务熔断
#   apex-system-service-flow-rules.json    — 系统服务限流
#   apex-system-service-degrade-rules.json — 系统服务熔断
#   apex-workflow-service-flow-rules.json  — 工作流服务限流
#   apex-workflow-service-degrade-rules.json — 工作流服务熔断
#   apex-office-service-flow-rules.json    — 办公服务限流
#   apex-office-service-degrade-rules.json — 办公服务熔断
#   apex-file-service-flow-rules.json      — 文件服务限流
#   apex-file-service-degrade-rules.json   — 文件服务熔断
#
# 说明：
#   - 通用字段与资源写法见 config/sentinel/README.md。
#   - 资源名使用运行时路径变量归一化形式，例如 /api/v1/user/{userId}。
#   - 阈值按各服务实测容量调优；下面模板给出可用的初始基线。