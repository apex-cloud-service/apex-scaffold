# 商业License验证机制使用指南

## 功能说明

商业License验证机制用于控制不同版本的功能访问权限,支持基础版/标准版/企业版的差异化授权。通过RSA签名确保License文件不可篡改。

## 架构设计

```
┌─────────────────┐
│ LicenseGenerator │ ← 生成License(仅服务商使用)
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
│ @FeatureGate     │ ← AOP自动拦截未授权功能
└─────────────────┘
```

## 快速开始

### 1. 生成密钥对(仅服务商)

```bash
# 方式一: 使用LicenseGenerator命令行工具
java -cp apex-common.jar com.apex.scaffold.common.license.LicenseGenerator \
  gen-keys private.key public.key

# 方式二: 代码生成
KeyPair keyPair = RsaUtil.generateKeyPair();
String privateKeyBase64 = RsaUtil.privateKeyToBase64(keyPair.getPrivate());
String publicKeyBase64 = RsaUtil.publicKeyToBase64(keyPair.getPublic());
```

**重要**: 私钥必须严格保密,仅保存在服务端!

### 2. 生成License(仅服务商)

```java
LicenseGenerator generator = LicenseGenerator.loadKeys("private.key", "public.key");

LicenseInfo license = generator.generateLicense(
    "示例客户",                    // 客户名称
    "customer@example.com",        // 客户邮箱
    LicenseVersion.ENTERPRISE,     // 授权版本
    LocalDateTime.now(),           // 生效时间
    LocalDateTime.now().plusYears(1), // 过期时间
    100,                           // 最大用户数
    50,                            // 最大并发数
    new String[]{                  // 启用的功能
        "audit_log", 
        "api_version", 
        "monitoring"
    }
);

generator.saveToFile(license, "customer-license.json");
```

生成的JSON示例:
```json
{
  "licenseId": "LIC-20260825130000-a1b2",
  "customerName": "示例客户",
  "customerEmail": "customer@example.com",
  "version": "ENTERPRISE",
  "validFrom": "2026-08-25T13:00:00",
  "validTo": "2027-08-25T13:00:00",
  "maxUsers": 100,
  "maxConcurrent": 50,
  "enabledFeatures": "[\"audit_log\",\"api_version\",\"monitoring\"]",
  "signature": "Base64编码的RSA签名...",
  "publicKeyFingerprint": "abc123def456..."
}
```

### 3. 客户端配置

在`application.yml`中配置:

```yaml
apex:
  license:
    enabled: true                          # 启用License验证
    file-path: classpath:license.json      # License文件路径
    public-key-file: /etc/apex/public.key  # 公钥文件路径
```

将`license.json`和`public.key`放到对应位置。

### 4. 使用@FeatureGate注解

```java
@RestController
@RequestMapping("/audit")
public class AuditLogController {

    @GetMapping("/logs")
    @FeatureGate(value = "audit_log", message = "审计日志功能需要标准版或企业版License")
    public Result<List<AuditLogVO>> listLogs() {
        // 只有授权客户才能访问此接口
        return Result.success(auditLogService.list());
    }
}
```

未授权访问时返回:
```json
{
  "code": 1007,
  "msg": "审计日志功能需要标准版或企业版License",
  "data": null
}
```

## 授权版本对比

| 功能 | 基础版 | 标准版 | 企业版 |
|------|--------|--------|--------|
| 核心CRUD | ✅ | ✅ | ✅ |
| 数据脱敏 | ✅ | ✅ | ✅ |
| 错误码体系 | ✅ | ✅ | ✅ |
| Flyway迁移 | ✅ | ✅ | ✅ |
| OWASP扫描 | ✅ | ✅ | ✅ |
| **操作审计日志** | ❌ | ✅ | ✅ |
| **API版本管理** | ❌ | ✅ | ✅ |
| **Grafana监控** | ❌ | ✅ | ✅ |
| **License验证** | ❌ | ✅ | ✅ |
| SPI插件扩展 | ❌ | ❌ | ✅ |
| 定制化开发 | ❌ | ❌ | ✅ |
| 技术支持 | 社区 | 邮件 | 专属顾问 |
| 价格 | 免费 | ¥9,800/年 | ¥29,800/年 |

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
// 生成License时指定自定义功能
String[] features = new String[]{
    "custom_report",      // 自定义报表
    "advanced_search",    // 高级搜索
    "batch_import"        // 批量导入
};

LicenseInfo license = generator.generateLicense(
    "客户A", "a@example.com", 
    LicenseVersion.STANDARD,
    LocalDateTime.now(), 
    LocalDateTime.now().plusYears(1),
    50, 20, features
);
```

### License续期

```java
// 重新生成License,延长有效期
LicenseInfo renewedLicense = generator.generateLicense(
    license.getCustomerName(),
    license.getCustomerEmail(),
    license.getVersion(),
    license.getValidFrom(),
    LocalDateTime.now().plusYears(1), // 新过期时间
    license.getMaxUsers(),
    license.getMaxConcurrent(),
    license.getEnabledFeatures().split(",")
);

generator.saveToFile(renewedLicense, "renewed-license.json");
```

## 安全建议

### 1. 私钥保护

- **绝不**将私钥交给客户
- 使用HSM(硬件安全模块)存储私钥
- 定期轮换密钥对(每年一次)
- 私钥文件权限设置为`600`(仅所有者可读写)

### 2. License文件保护

- 使用HTTPS分发License文件
- License文件包含客户标识,防止共享
- 定期检查License是否被篡改

### 3. 公钥分发

- 公钥可以公开,但应通过可信渠道分发
- 使用公钥指纹验证公钥完整性
- 避免中间人攻击

### 4. 运行时保护

- License验证失败时应立即终止应用
- 记录所有License相关操作到审计日志
- 实现心跳机制,定期向授权服务器报告

## 故障排查

### 问题1: License验证失败

**现象**: 启动时报错"License签名验证失败"

**原因**:
- License文件被篡改
- 公钥不匹配
- JSON格式错误

**解决**:
1. 检查License文件完整性(MD5校验)
2. 确认公钥与生成License时使用的密钥对匹配
3. 重新生成License

### 问题2: 功能未授权

**现象**: 访问接口返回"当前License未授权此功能"

**原因**:
- License中未包含该功能标识
- 功能标识拼写错误

**解决**:
1. 检查License JSON中的`enabledFeatures`字段
2. 确认`@FeatureGate`注解的value与功能列表一致
3. 联系服务商升级License

### 问题3: License已过期

**现象**: 启动时报错"License已过期"

**解决**:
1. 联系服务商续期
2. 临时方案: 修改系统时间(不推荐)
3. 升级到新版本获取新License

## API参考

### LicenseValidator

```java
// 加载License
validator.loadFromFile("license.json");
validator.loadFromJson(jsonString);

// 检查功能
boolean enabled = validator.isFeatureEnabled("audit_log");

// 检查限制
boolean allowed = validator.checkUserLimit(100);
boolean allowed = validator.checkConcurrentLimit(50);

// 获取信息
LicenseInfo info = validator.getLicenseInfo();
long remainingDays = info.getRemainingDays();
```

### LicenseGenerator

```java
// 生成密钥对
LicenseGenerator gen = LicenseGenerator.generateNewKeys("private.key", "public.key");

// 生成License
LicenseInfo license = gen.generateLicense(...);

// 保存文件
gen.saveToFile(license, "output.json");
```

## 注意事项

1. **性能影响**: License验证在启动时执行,运行时仅内存检查,无性能影响
2. **离线使用**: License文件一旦生成即可离线使用,无需联网验证
3. **多实例部署**: 每个实例都需要独立的License文件或共享同一文件
4. **测试环境**: 测试时可设置`apex.license.enabled=false`跳过验证
5. **法律合规**: License协议应符合当地法律法规,建议咨询法律专家

## TODO

- [ ] 实现在线激活机制(需联网)
- [ ] 支持License撤销列表(CRL)
- [ ] 添加License使用统计上报
- [ ] 实现灰度发布支持(部分功能试用)
- [ ] 集成区块链存证(防抵赖)
