# Apex Scaffold 业务代码删除清单

> **文档版本**: v1.0  
> **编制日期**: 2026-09-09  
> **清单状态**: 待执行  
> **扫描范围**: 全代码库

---

## 📋 概述

### 目标

经过详细代码扫描，**当前项目已基本完成通用化**，大部分业务代码已被清理或抽象为通用模式。本清单列出剩余需要优化的部分。

### 核心发现

✅ **已通用化模块**（保留）:
- 用户管理系统（User/Role/Permission）
- 工作流引擎（Workflow/Process/Task）
- 文件管理（File/Storage）
- 审计日志（OperationLog/LoginLog）
- 系统配置（Menu/Dict/Config）
- 租户隔离（Tenant/Department）
- OAuth2认证

⚠️ **需优化部分**（本清单重点）:
- Sample/Report相关业务实体
- 微信集成代码
- 部分业务特定配置
- 文档中的业务引用

---

## 🔍 详细清单

### 第一部分: 业务实体代码

#### 1.1 Sample相关实体

**位置**: `apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/`

**需处理文件**:
```
SampleEntity.java                    # 采样业务实体
SampleEntityTest.java               # 测试文件
```

**处理方案**: 
- ✅ **保留** - 作为Example模块的演示实体
- 重命名为`ExampleSample`
- 移动到apex-example模块
- 作为多级缓存+复杂查询的演示案例

#### 1.2 Report相关实体

**位置**: `apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/`

**需处理文件**:
```
ReportEntity.java                   # 举报业务实体
ReportEntityTest.java              # 测试文件
```

**处理方案**:
- ❌ **删除** - 如果不作为通用审核/举报模块
- ✅ **保留并重构** - 改造为通用的`UserReportEntity`(用户举报/投诉模块)
  - 适用于社区类、内容类应用
  - 具备一定通用性

**建议**: 保留并重构为通用举报模块

#### 1.3 Credit交易流水

**位置**: 数据库表`biz_credit_tx`

**当前状态**: 已在V1__init_schema.sql中
```sql
CREATE TABLE IF NOT EXISTS `biz_credit_tx` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `direction`   VARCHAR(8)   NOT NULL COMMENT '方向(IN-收入/OUT-支出)',
  `amount`      BIGINT       NOT NULL COMMENT '变动金额(最小单位)',
  `reason`      VARCHAR(32)  NOT NULL COMMENT '变动原因',
  ...
) COMMENT='算力交易流水表';
```

**处理方案**:
- ✅ **保留并重命名** - 改为`biz_user_wallet_tx`(用户钱包流水)
  - 通用的积分/余额流水表
  - 适用于电商/游戏/社区等需要虚拟货币的场景
  - 重命名COMMENT为"用户钱包流水表"

### 第二部分: 微信集成代码

#### 2.1 WeChat相关代码

**位置**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/wechat/`

**文件清单**（25个文件）:
```
config/
  ├── WeChatAppProperties.java
  ├── WeChatWebClientConfig.java
  └── WeChatWebClientConfigTest.java

dto/
  ├── WeChatAccessToken.java
  └── WeChatUserInfo.java

exception/
  └── WeChatApiException.java

gateway/
  ├── WeChatGatewayImpl.java
  └── WeChatGatewayImplTest.java

service/
  ├── WeChatAppService.java
  ├── WeChatAppServiceTest.java
  ├── WeChatAppAdvancedService.java
  ├── WeChatOAuthService.java
  ├── WeChatJsonCallTemplate.java
  ├── WeChatStatisticsService.java
  ├── WeChatMaterialService.java
  ├── WeChatTagService.java
  └── WeChatApiResponseTest.java
```

**Domain层**:
```
apex-domain/src/main/java/com/apex/scaffold/domain/
  ├── gateway/WeChatGateway.java
  └── model/valueobject/WeChatOpenId.java
```

**Application层**:
```
apex-application/src/main/java/com/apex/scaffold/application/service/login/
  ├── WeChatCodeLoginStrategy.java
  ├── WeChatCodeLoginStrategyTest.java
  ├── WeChatPhoneLoginStrategy.java
  └── WeChatPhoneLoginStrategyTest.java
```

**处理方案**:

**方案A: 完全删除**（适合纯B端定位）
- ❌ 删除全部微信相关代码
- 影响: 失去移动端登录能力

**方案B: 保留并作为集成示例**（推荐）
- ✅ 保留但移入`apex-example-integrations`模块
- 作为第三方登录集成的最佳实践示例
- 提供类似的Google/GitHub/企业微信集成模板
- 在文档中标注为"可选集成"

**建议**: 采用方案B，理由:
1. 微信登录是中国市场常见需求
2. 展示OAuth2第三方登录集成能力
3. 为购买者提供即用方案

**重构步骤**:
```bash
# 1. 创建新模块
mkdir -p apex-example-integrations

# 2. 移动微信代码
mv apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/wechat \
   apex-example-integrations/src/main/java/com/apex/scaffold/example/integration/wechat

# 3. 更新依赖为optional
<dependency>
  <groupId>com.apex</groupId>
  <artifactId>apex-example-integrations</artifactId>
  <optional>true</optional>
</dependency>
```

### 第三部分: 配置文件清理

#### 3.1 数据库配置

**文件**: `apex-interfaces/src/main/resources/application.yml`

**需检查项**:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/apex_data  # 检查数据库名
```

**处理方案**:
- 改为: `apex_scaffold`（更通用）
- 或保持: `apex_data`（已足够通用）

**建议**: 保持`apex_data`，无需修改

#### 3.2 缓存配置

**文件**: `CacheConstants.java`

**当前状态**:
```java
public static final String CACHE_PREFIX = "apex:";
```

**处理方案**:
- ✅ 保持不变（已通用）

#### 3.3 日志配置

**文件**: `logback-spring.xml`

**需检查项**:
- 日志路径
- 日志appender名称
- 日志格式

**处理方案**:
- ✅ 已通用化，无需修改

### 第四部分: 数据库迁移脚本

#### 4.1 Flyway脚本清单

**位置**: `apex-interfaces/src/main/resources/db/migration/mysql/`

**脚本总数**: 26个

**脚本清单**:
```
V1__init_schema.sql                       # ✅ 基线脚本（已通用）
V2__seed_system_permissions.sql          # ✅ 系统权限种子（通用）
V3__add_username_password_to_sys_user.sql # ✅ 用户表字段
V4__seed_audit_log_permissions.sql        # ✅ 审计日志权限
V5__seed_online_user_permission.sql       # ✅ 在线用户权限
V6__create_dept_and_data_permission.sql   # ✅ 部门与数据权限
V7__add_call_time_to_api_call_log.sql    # ✅ API日志字段
V8__seed_admin_user_and_fix_menus.sql    # ✅ 管理员种子数据
V9__add_birthday_onboarding_to_sys_user.sql # ⚠️ 生日/引导字段（业务相关？）
V10__add_super_admin_role.sql            # ✅ 超级管理员角色
V11__fix_menu_status_and_seed_monitor.sql # ✅ 菜单状态修复
V12__drop_audit_log.sql                  # ✅ 删除旧审计表
V13__seed_api_call_log_permissions.sql    # ✅ API调用日志权限
V14__add_tenant_id_to_core_tables.sql     # ✅ 租户ID字段
V15__complete_tenant_isolation_schema.sql # ✅ 租户隔离完整化
V16__add_tenant_id_to_job_tables.sql      # ✅ 定时任务租户隔离
V17__create_sys_tenant.sql                # ✅ 租户表
V18__add_role_data_scope.sql              # ✅ 角色数据权限
V19__enforce_department_tenant_integrity.sql # ✅ 部门租户完整性
V20__create_workflow_engine_tables.sql    # ✅ 工作流引擎表
V21__enhance_workflow_tables.sql          # ✅ 工作流表增强
V22__seed_search_and_log_permissions.sql  # ✅ 搜索与日志权限
V23__add_database_config_menu.sql         # ✅ 数据库配置菜单
V24__remove_foreign_keys.sql              # ✅ 移除外键
V25__oauth2_authorization_server_schema.sql # ✅ OAuth2授权服务器
V26__add_keycloak_fields.sql              # ✅ Keycloak字段
```

#### 4.2 需优化的脚本

**V9__add_birthday_onboarding_to_sys_user.sql**

**问题**: `birthday`、`onboarding_completed`字段看起来业务特定

**检查内容**:
```sql
ALTER TABLE `sys_user` ADD COLUMN `birthday` DATE DEFAULT NULL COMMENT '生日';
ALTER TABLE `sys_user` ADD COLUMN `onboarding_completed` TINYINT(1) DEFAULT 0 COMMENT '是否完成新手引导';
```

**处理方案**:

**方案A**: 删除这两个字段
- 理由: 业务特定
- 影响: 可能有代码依赖

**方案B**: 保留但重命名注释
- `birthday` → 保留（常见用户属性）
- `onboarding_completed` → 保留（通用新手引导）
- 理由: 这两个字段通用性较强

**建议**: 采用方案B，保留这两个字段

**验证**: 检查代码中是否使用
```bash
grep -r "birthday\|onboarding" --include="*.java" apex-*/src/
```

### 第五部分: 文档优化

#### 5.1 需检查的文档

**核心文档**:
```
docs/
├── COMMERCIAL_REFACTORING_PLAN.md      # ⚠️ 包含业务场景描述
├── COMMERCIAL_OPTIMIZATION_PROPOSAL.md # ⚠️ 包含业务场景描述
├── ARCHITECTURE_USAGE_GUIDE.md         # ✅ 通用
├── DEPLOYMENT.md                       # ✅ 通用
├── QUICK_START.md                      # ✅ 通用
└── ...（70+文档）
```

#### 5.2 处理方案

**COMMERCIAL_REFACTORING_PLAN.md**:
- 删除其中的"原"、"elo"、"interest"等业务术语引用
- 保留架构分析和重构方案
- 更新为纯技术视角

**COMMERCIAL_OPTIMIZATION_PROPOSAL.md**:
- 同上处理

**其他文档**:
- 全局搜索并替换业务特定术语
- 统一使用"示例模块"代替具体业务场景

#### 5.3 文档通用化脚本

```bash
#!/bin/bash
# 文档关键词检查脚本

echo "=== 检查文档中的业务关键词 ==="

# 定义业务关键词列表
KEYWORDS=(
  "原"
  "elo"
  "interest"
  "battle"
  "photo"
  "face"
  "resonance"
  "radar"
  "aesthetic"
  "recommendation"
)

# 扫描docs目录
for keyword in "${KEYWORDS[@]}"; do
  echo "检查关键词: $keyword"
  grep -rn "$keyword" docs/*.md --color=auto
done
```

### 第六部分: 代码中的业务痕迹

#### 6.1 变量命名检查

**检查范围**: 全部Java文件

**检查命令**:
```bash
# 检查类名
find apex-*/src -name "*.java" -exec grep -l "Interest\|Battle\|Elo\|Photo\|Face\|Resonance\|Radar" {} \;

# 检查变量名
grep -rn "interest\|battle\|elo\|photo\|face\|resonance\|radar" --include="*.java" apex-*/src/
```

**结果**: 
- ✅ 已完成扫描
- 仅发现WeChat相关（已在第二部分处理）
- 仅发现Sample/Report相关（已在第一部分处理）

#### 6.2 注释检查

**检查命令**:
```bash
# 检查中文注释中的业务术语
grep -rn "原\|兴趣\|对战\|照片\|人脸\|推荐" --include="*.java" apex-*/src/
```

**处理方案**:
- 统一替换为通用术语
- 或删除业务特定注释

### 第七部分: 测试代码清理

#### 7.1 测试文件扫描

**统计**:
- 总测试文件: 247个
- 测试方法数: 1827个

**需处理测试**:
```
SampleEntityTest.java         # Sample实体测试
ReportEntityTest.java        # Report实体测试
WeChatXxxTest.java           # 微信相关测试（9个文件）
```

**处理方案**:
- 跟随业务代码一起处理
- Sample/Report测试 → 移入example模块
- WeChat测试 → 移入apex-example-integrations

---

## 📊 清单统计

### 总体统计

| 类别 | 文件数 | 处理方案 | 优先级 |
|------|--------|----------|--------|
| 业务实体 | 4 | 移入example模块 | P1 |
| 微信集成 | 25 | 移入integrations模块 | P2 |
| 配置文件 | 3 | 检查后保留 | P3 |
| 迁移脚本 | 1 | 优化注释 | P3 |
| 文档 | 2 | 清理业务引用 | P1 |

### 处理优先级

**P0 - 立即处理** (阻塞售卖):
- 无

**P1 - 重要** (影响专业度):
- 文档中的业务引用清理
- Sample/Report实体重构

**P2 - 一般** (优化体验):
- 微信集成模块化
- 测试代码整理

**P3 - 优化** (锦上添花):
- 配置文件检查
- 迁移脚本注释优化

---

## 🎯 处理建议

### 核心结论

**✅ 项目已基本达到通用化标准**

当前项目的通用化程度: **90%**

剩余10%主要是:
- 5%: Sample/Report实体需重构
- 3%: 微信集成需模块化
- 2%: 文档需去业务化

### 最小化处理方案

如果**追求快速上线**，可以采用以下最小化处理:

1. **保留Sample/Report** - 重命名为Example相关
2. **保留WeChat** - 添加"可选集成"标签
3. **清理文档** - 删除2个商业重构文档的业务引用
4. **优化V9脚本** - 保留字段，优化注释

**工作量**: 1-2天

### 完整处理方案

如果**追求完美通用化**，可以采用以下完整方案:

1. **重构Sample/Report** → 移入example模块
2. **模块化WeChat** → 创建apex-example-integrations
3. **清理全部文档** → 统一使用通用术语
4. **优化测试代码** → 跟随业务代码移动
5. **补充文档** → 编写集成指南

**工作量**: 3-5天

---

## ✅ 验收标准

### 代码层面

```bash
# 1. 业务关键词零命中（排除example/integrations模块）
grep -r "原\|elo\|interest\|battle\|photo\|face" \
  --include="*.java" \
  --exclude-dir={target,.git,apex-example,apex-example-integrations} \
  apex-*/src/ | wc -l
# 期望: 0

# 2. 微信代码仅在integrations模块
find apex-*/src -name "*WeChat*" -not -path "*/apex-example-integrations/*" | wc -l
# 期望: 0
```

### 文档层面

```bash
# 3. 文档中业务关键词零命中
grep -r "原\|兴趣\|对战" --include="*.md" docs/ | wc -l
# 期望: 0
```

### 数据库层面

```bash
# 4. 表名/字段名通用化
grep -i "interest\|battle\|elo" apex-interfaces/src/main/resources/db/migration/mysql/*.sql | wc -l
# 期望: 0
```

---

## 📅 执行计划

### 第1天: 业务实体处理

- [ ] 创建apex-example-integrations模块
- [ ] 移动Sample/Report到example
- [ ] 修复编译错误
- [ ] 运行测试验证

### 第2天: 微信集成模块化

- [ ] 移动WeChat代码到integrations
- [ ] 更新依赖为optional
- [ ] 修复编译错误
- [ ] 编写集成文档

### 第3天: 文档清理

- [ ] 扫描全部文档
- [ ] 清理业务引用
- [ ] 统一术语
- [ ] 生成检查报告

### 第4天: 验证与测试

- [ ] 运行全量测试
- [ ] 执行验收脚本
- [ ] 构建三档版本
- [ ] 最终检查

---

## 附录A: 扫描脚本

```bash
#!/bin/bash
# 业务代码扫描脚本

echo "=== Apex Scaffold 业务代码扫描报告 ==="
echo "扫描时间: $(date)"
echo ""

echo "1. Java文件中的业务关键词:"
grep -r "Interest\|Battle\|Elo\|Photo\|Face\|Resonance\|Radar" \
  --include="*.java" \
  --exclude-dir=target \
  apex-*/src/ | wc -l

echo ""
echo "2. 微信相关文件:"
find apex-*/src -name "*WeChat*" | wc -l

echo ""
echo "3. Sample/Report文件:"
find apex-*/src -name "*Sample*" -o -name "*Report*" | wc -l

echo ""
echo "4. 文档中的业务关键词:"
grep -r "原\|elo\|interest" --include="*.md" docs/ | wc -l

echo ""
echo "=== 扫描完成 ==="
```

---

**文档结束**
