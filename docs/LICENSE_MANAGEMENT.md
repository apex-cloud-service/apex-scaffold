# 商业 License 验证机制使用指南

## 功能说明

商业 License 验证机制用于控制不同版本的功能访问权限，支持基础版/标准版/企业版的差异化授权。通过 RSA 签名确保 License 文件不可篡改。

**v2.0 机制要点**：
- **默认启用**：`apex.license.enabled` 缺省为 `true`，License 校验不可被跳过（显式关闭将导致商业功能无法被授权）。
- **开箱即用**：随包内置基础版 License 模板与官方公钥，下载后基础功能立即可用。
- **容错降级**：未配置 License / License 无效 / 已过期时，应用**正常启动**，基础功能放行，商业功能（`@FeatureGate` 标注）拒绝，并输出明确的 WARN 日志 —— fork 用户无法绕过商业功能授权。

## 架构设计

```
┌─────────────────┐
│ LicenseGenerator │ ← 生成License(仅服务商使用, 持有私钥)
└────────┬────────┘
         │ 生成 .json + RSA签名
         ↓
┌─────────────────┐
│  License File    │ ← 分发给客户
└────────┬────────┘
         │ 加载并验证
         ↓
┌─────────────────┐
│ LicenseValidator │ ← 验证签名/有效期/功能权限
└────────┬────────┘
         │ 检查结果
         ↓
┌─────────────────┐
│ @FeatureGate     │ ← AOP自动拦截未授权功能(降级模式同样生效)
└─────────────────┘
```

## 快速开始

### 1. 服务商：生成密钥对（仅一次）

```bash
# 使用 LicenseGenerator 命令行工具
java -cp "<apex-common.jar>:<依赖>" com.apex.scaffold.common.license.LicenseGenerator \
  gen-keys config/license/private.key config/license/public.key
```

**重要**：私钥必须严格保密，仅保存在服务端，**禁止提交到代码仓库**（已加入 .gitignore）。

### 2. 服务商：发放 License（每次购买）

推荐使用官方发放脚本：

```bash
# 发放标准版一年授权
./scripts/license/generate-license.sh STANDARD "示例公司" 365

# 发放企业版一年授权
./scripts/license/generate-license.sh ENTERPRISE "示例公司" 365

# 发放自定义功能集
./scripts/license/generate-license.sh STANDARD "示例公司" 365 jwt_auth,rbac,redis_cache
```

脚本会自动使用各版本默认功能集（与 [LICENSE-COMMERCIAL.md](../LICENSE-COMMERCIAL.md) 版本对照一致），产物输出到 `dist/license/`。

也可以使用 CLI 手动生成：

```bash
java -cp "<apex-common.jar>:<依赖>" com.apex.scaffold.common.license.LicenseGenerator \
  gen-license config/license/private.key config/license/public.key customer-license.json \
  STANDARD "示例公司" 365
```

生成的功能清单（默认）：

| 版本 | 默认功能集 |
|------|-----------|
| BASIC | jwt_auth, unified_response, parameter_validation, webflux, mybatis_plus |
| STANDARD | 基础版 + rbac, redis_cache, monitoring, wechat_sdk, object_storage, email_sms, excel_io |
| ENTERPRISE | 标准版 + rocketmq, tracing, sentinel, resilience4j, distributed_lock, multi_datasource, flyway, k8s |

### 3. 客户端：配置并激活

随包默认配置已开箱可用（基础版）：

```yaml
apex:
  license:
    enabled: ${APEX_LICENSE_ENABLED:true}       # 默认开启
    file-path: ${APEX_LICENSE_FILE:classpath:license/license-basic.json}
    public-key-file: ${APEX_LICENSE_PUBLIC_KEY_FILE:classpath:license/public.key}
```

**激活商业版**：将服务商发放的 License 文件放置到应用可访问路径，通过环境变量覆盖：

```bash
export APEX_LICENSE_FILE=/opt/apex/customer-license.json
java -jar apex-interfaces.jar
```

重启后日志输出 `License 验证通过: 客户=xxx, 版本=STANDARD` 即激活成功。

### 4. 使用 @FeatureGate 注解标记商业功能

```java
@GetMapping("/logs")
@FeatureGate(value = "audit_log", message = "审计日志功能需要标准版或企业版License")
public Result<List<AuditLogVO>> listLogs() {
    // 只有授权客户才能访问此接口
    return Result.success(auditLogService.list());
}
```

未授权访问时返回：

```json
{
  "code": 1007,
  "msg": "审计日志功能需要标准版或企业版License",
  "data": null
}
```

## 授权版本对比

| 功能 | 基础版 | 标准版 | 企业版 |
|------|:---:|:---:|:---:|
| JWT 认证 / 统一响应 / 参数校验 | ✅ | ✅ | ✅ |
| WebFlux / MyBatis-Plus | ✅ | ✅ | ✅ |
| RBAC 权限管理 | - | ✅ | ✅ |
| Redis 多级缓存 | - | ✅ | ✅ |
| Prometheus 监控 | - | ✅ | ✅ |
| 对象存储 / 短信邮件 | - | ✅ | ✅ |
| RocketMQ 消息队列 | - | - | ✅ |
| 链路追踪（Zipkin） | - | - | ✅ |
| Sentinel / Resilience4j | - | - | ✅ |
| 分布式锁 / 多数据源 | - | - | ✅ |
| K8s 部署脚本 | - | - | ✅ |
| 技术支持 | 社区 | 邮件 5 工作日 | 专属客服 2 小时 |
| 价格 | 免费 | ¥9,999/年 | ¥49,999/年 |

## 高级用法

### 动态检查用户数限制

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final LicenseValidator licenseValidator;

    public Result<Void> createUser(UserDTO user) {
        int currentCount = userMapper.selectCount(null);
        if (!licenseValidator.checkUserLimit(currentCount + 1)) {
            return Result.error(ErrorCode.BUSINESS_RULE_VIOLATION,
                "已达到最大用户数限制: " + licenseValidator.getLicenseInfo().getMaxUsers());
        }
        userMapper.insert(user);
        return Result.success();
    }
}
```

### 自定义功能列表

```java
String[] features = new String[]{"custom_report", "advanced_search", "batch_import"};
LicenseInfo license = generator.generateLicense(
    "客户A", "a@example.com",
    LicenseVersion.STANDARD,
    LocalDateTime.now(),
    LocalDateTime.now().plusYears(1),
    50, 20, features
);
```

### License 续期

```java
LicenseInfo renewedLicense = generator.generateLicense(
    license.getCustomerName(),
    license.getCustomerEmail(),
    license.getVersion(),
    license.getValidFrom(),
    LocalDateTime.now().plusYears(1),
    license.getMaxUsers(),
    license.getMaxConcurrent(),
    license.getEnabledFeatures().split(",")
);
generator.saveToFile(renewedLicense, "renewed-license.json");
```

## 安全建议

1. **私钥保护**：绝不将私钥交给客户；使用 HSM 存储；定期轮换密钥对（每年一次）；私钥文件权限设为 `600`。
2. **License 文件保护**：使用 HTTPS 分发；License 包含客户标识防止共享；定期检查是否被篡改。
3. **公钥分发**：公钥可公开，但应通过可信渠道分发；使用公钥指纹验证完整性。
4. **运行时保护**：开启审计日志记录 License 相关操作；商业功能一律通过 `@FeatureGate` 标注，保证降级模式下同样受控。

## 故障排查

| 现象 | 原因 | 解决 |
|------|------|------|
| 启动日志 WARN "License 加载或验证失败...进入降级模式" | 未配置 License 或 License 无效 | 基础功能不受影响；如需商业功能，联系服务商获取正确 License |
| 访问商业接口返回"当前License未授权此功能" | License 功能集不含该标识，或未激活商业 License | 检查 `enabledFeatures`，确认 `@FeatureGate` value 一致，或升级 License |
| 启动日志 "License已过期" | License 超过有效期 | 联系服务商续期 |
| 篡改 License 后访问商业功能被拒 | 签名验证失败 | 重新从服务商获取 License |

## 注意事项

1. **性能**：License 验证在启动时执行，运行时仅内存检查，无性能影响。
2. **离线**：License 文件生成后即可离线使用，无需联网。
3. **多实例**：每个实例可共享同一 License 文件。
4. **测试环境**：测试时可将 `apex.license.enabled` 设为 `false` 跳过验证（商业功能将不可用）。
5. **降级 ≠ 绕过**：即使关闭 License 校验或移除 License 文件，`@FeatureGate` 标注的商业功能仍会拒绝访问。

## 工具链

| 工具 | 用途 | 持有方 |
|------|------|--------|
| `LicenseGenerator`（CLI） | 生成密钥对 / 发放 License | 服务商 |
| `scripts/license/generate-license.sh` | 一键发放 License | 服务商 |
| `LicenseValidator` | 应用内验证（随包自动装配） | 客户 |
| `@FeatureGate` + Aspect | 商业功能门禁 | 开发者 |

## TODO

- [ ] 实现在线激活机制（需联网）
- [ ] 支持 License 撤销列表（CRL）
- [ ] 添加 License 使用统计上报
- [ ] 实现灰度试用支持（部分功能限时试用）
