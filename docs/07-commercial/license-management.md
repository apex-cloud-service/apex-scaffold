# 商业 License 验证机制使用指南

## 功能说明

商业 License 验证机制用于控制不同版本的功能访问权限，支持基础版/标准版/企业版的差异化授权。通过 RSA 签名确保 License 文件不可篡改。

**v2.0 机制要点**：
- **默认启用**：`apex.license.enabled` 缺省为 `true`，License 校验不可被跳过（显式关闭将导致商业功能无法被授权）。
- **开箱即用**：随包内置基础版 License 模板与官方公钥，下载后基础功能立即可用。
- **容错降级**：未配置 License / License 无效 / 已过期 / 机器指纹不匹配时，应用**正常启动**，基础功能放行，商业功能（`@FeatureGate` 标注）拒绝，并输出明确的 WARN 日志 —— fork 用户无法绕过商业功能授权。
- **机器绑定**（v1.2.1 新增）：签发时写入目标机器的 SHA-256 指纹，运行时采集本机指纹比对，防止 License 被复制到未授权机器。容器/K8s 部署支持手动覆盖指纹。

## 架构设计

> **配套架构图**: [License 验证流程](../diagrams/65_license_validation_flow.svg)

```
┌─────────────────────┐
│   LicenseGenerator    │ ← 生成License(仅服务商使用, 持有私钥)
│  + MachineFingerprint │    客户先运行 gen-fingerprint 采集指纹 → 卖方签发
└──────────┬──────────┘
           │ 生成 .json + RSA签名(含机器指纹字段)
           ↓
┌─────────────────────┐
│    License File       │ ← 分发给客户(绑定到指定机器)
└──────────┬──────────┘
           │ 加载并验证
           ↓
┌─────────────────────┐
│   LicenseValidator    │ ← 验证签名 → 公钥指纹 → 机器指纹 → 有效期
│  + FingerprintCollector│    采集本机指纹并比对,不匹配→降级模式
└──────────┬──────────┘
           │ 检查结果
           ↓
┌─────────────────────┐
│    @FeatureGate      │ ← AOP自动拦截未授权功能(降级模式同样生效)
└─────────────────────┘
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

### 2.1 机器指纹绑定（v1.2.1 新增，推荐启用）

机器指纹绑定防止 License 被复制到未授权机器运行。签发流程：

**步骤 1：客户采集机器指纹**

```bash
# 客户在目标机器上运行
java -cp "<apex-license-tools.jar>:<依赖>" com.apex.scaffold.license.tools.LicenseGenerator gen-fingerprint

# 输出示例：
# 机器指纹（SHA-256）:
# a1b2c3d4e5f6...（64 字符）
# 请将此指纹发给卖方用于 License 签发。
```

指纹采集维度：MAC 地址 + CPU 标识 + 主板序列号，三维度拼接后 SHA-256 哈希。容器内主板维度可能不可用，会自动降级为 MAC + CPU。

**步骤 2：卖方签发绑定指纹的 License**

```bash
# 使用官方脚本
./scripts/license/generate-license.sh ENTERPRISE "示例公司" 365 --fingerprint a1b2c3d4e5f6...

# 或使用 CLI
java -cp "<apex-license-tools.jar>:<依赖>" com.apex.scaffold.license.tools.LicenseGenerator \
  gen-license config/license/private.key config/license/public.key customer-license.json \
  ENTERPRISE "示例公司" 365 --fingerprint a1b2c3d4e5f6...
```

指纹字段参与 RSA 签名，篡改 License 中的指纹会导致签名验证失败。

**步骤 3：License 未绑定指纹时的行为**

旧版 License（无 `machineFingerprint` 字段）仍可正常验证，仅输出 WARN 日志提示建议绑定指纹。

### 2.2 容器/K8s 部署的机器指纹覆盖

Docker 容器内 MAC 地址每次重建可能变化，K8s Pod 中硬件信息不可用。此时通过配置手动指定指纹：

```yaml
apex:
  license:
    machine-fingerprint-override: "${APEX_LICENSE_FINGERPRINT}"  # 由部署方约定
```

```bash
# K8s Deployment 示例
env:
  - name: APEX_LICENSE_FINGERPRINT
    valueFrom:
      fieldRef:
        fieldPath: spec.nodeName  # 用节点名作为指纹源
```

部署方需确保签发时和运行时使用相同的指纹值。

**跳过验证（仅测试用）**：

```yaml
apex:
  license:
    machine-fingerprint-skip: true  # 生产环境不应启用
```

### 2.3 机器指纹验证规则

LicenseValidator 加载 License 时，验证链顺序为：

```
签名验证 → 公钥指纹 → 机器指纹 → 有效期 → 运行期重检
```

机器指纹验证（`validateMachineFingerprint`）的判定优先级：

| 优先级 | 条件 | 行为 |
|--------|------|------|
| 1 | License 中 `machineFingerprint` 为 null/空 | **跳过**，WARN 提示"建议绑定指纹"（向后兼容旧版 License） |
| 2 | `apex.license.machine-fingerprint-skip=true` | **跳过**，WARN 提示"生产不应启用" |
| 3 | `apex.license.machine-fingerprint-override` 非空 | 用 override 值与 License 指纹比对 |
| 4 | 以上均不满足 | 自动采集本机指纹（MAC + CPU + 主板 → SHA-256）与 License 指纹比对 |
| - | 比对不匹配 | 抛 `LicenseException("机器指纹不匹配")` → 进入降级模式 |
| - | 比对匹配 | 放行，继续验证有效期 |

### 2.4 License JSON 字段格式

```json
{
  "licenseId": "LIC-20260829143022-3a8f",
  "customerName": "示例公司",
  "customerEmail": "customer@example.com",
  "version": "ENTERPRISE",
  "validFrom": "2026-08-29T14:30:22",
  "validTo": "2027-08-29T14:30:22",
  "maxUsers": -1,
  "maxConcurrent": -1,
  "enabledFeatures": "[\"jwt_auth\",\"rbac\",\"redis_cache\",\"rocketmq\"]",
  "publicKeyFingerprint": "a1b2c3d4e5f6...",
  "machineFingerprint": "9f8e7d6c5b4a...(64字符SHA-256)",
  "signature": "Base64编码的RSA-SHA256签名"
}
```

| 字段 | 类型 | 说明 | 参与签名 |
|------|------|------|:---:|
| `licenseId` | String | License 唯一 ID | ✅ |
| `customerName` | String | 客户名称 | ✅ |
| `customerEmail` | String | 客户邮箱 | ✅ |
| `version` | Enum | 授权版本 BASIC/STANDARD/ENTERPRISE | ✅ |
| `validFrom` | DateTime | 生效时间 | ✅ |
| `validTo` | DateTime | 过期时间（null=永久） | ✅ |
| `maxUsers` | int | 最大用户数（-1=无限制） | ✅ |
| `maxConcurrent` | int | 最大并发数（-1=无限制） | ✅ |
| `enabledFeatures` | String | 启用功能 JSON 数组 | ✅ |
| `publicKeyFingerprint` | String | 公钥 SHA-256 指纹 | ✅ |
| `machineFingerprint` | String | 机器 SHA-256 指纹（v1.2.1 新增，null=不绑定） | ✅ |
| `signature` | String | RSA-SHA256 签名（覆盖除自身外所有字段） | - |

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
// machineFingerprint 可为 null（不绑定机器）或 SHA-256 哈希
String machineFingerprint = MachineFingerprintCollector.collect();
LicenseInfo license = generator.generateLicense(
    "客户A", "a@example.com",
    LicenseVersion.STANDARD,
    LocalDateTime.now(),
    LocalDateTime.now().plusYears(1),
    50, 20, features,
    machineFingerprint  // v1.2.1 新增参数
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
    license.getEnabledFeatures().split(","),
    license.getMachineFingerprint()  // 续期时保持原机器绑定
);
generator.saveToFile(renewedLicense, "renewed-license.json");
```

## 安全建议

1. **私钥保护**：绝不将私钥交给客户；使用 HSM 存储；定期轮换密钥对（每年一次）；私钥文件权限设为 `600`。
2. **License 文件保护**：使用 HTTPS 分发；License 包含客户标识防止共享；定期检查是否被篡改。
3. **机器指纹绑定**：签发商业版 License 时**务必绑定机器指纹**，防止 License 被复制到其他机器运行；容器部署使用 `machine-fingerprint-override` 指定稳定标识。
4. **公钥分发**：公钥可公开，但应通过可信渠道分发；使用公钥指纹验证完整性。
5. **运行时保护**：开启审计日志记录 License 相关操作；商业功能一律通过 `@FeatureGate` 标注，保证降级模式下同样受控。
6. **生产禁用 skip**：`apex.license.machine-fingerprint-skip=true` 仅用于测试环境，生产环境启用会导致机器绑定完全失效。

## 故障排查

| 现象 | 原因 | 解决 |
|------|------|------|
| 启动日志 WARN "License 加载或验证失败...进入降级模式" | 未配置 License 或 License 无效 | 基础功能不受影响；如需商业功能，联系服务商获取正确 License |
| 访问商业接口返回"当前License未授权此功能" | License 功能集不含该标识，或未激活商业 License | 检查 `enabledFeatures`，确认 `@FeatureGate` value 一致，或升级 License |
| 启动日志 "License已过期" | License 超过有效期 | 联系服务商续期 |
| 篡改 License 后访问商业功能被拒 | 签名验证失败 | 重新从服务商获取 License |
| 启动日志 "机器指纹不匹配" | License 绑定的指纹与当前机器不一致 | 联系服务商在目标机器上重新采集指纹并签发新 License；或检查是否误用其他机器的 License |
| 启动日志 WARN "License 未包含机器指纹" | 旧版 License 无 `machineFingerprint` 字段 | 建议升级为带指纹绑定的 License；当前仍可正常使用，无安全降级 |
| 容器重建后 License 失效 | Docker 容器 MAC 地址变化导致指纹改变 | 使用 `apex.license.machine-fingerprint-override` 指定稳定指纹值 |

## 注意事项

1. **性能**：License 验证在启动时执行，运行时仅内存检查，无性能影响。
2. **离线**：License 文件生成后即可离线使用，无需联网。
3. **多实例**：每个实例可共享同一 License 文件。若绑定了机器指纹，则所有实例需部署在同一台物理机（或使用相同的 `machine-fingerprint-override`）。
4. **测试环境**：测试时可将 `apex.license.enabled` 设为 `false` 跳过验证（商业功能将不可用）；也可设 `apex.license.machine-fingerprint-skip=true` 单独跳过机器指纹验证。
5. **降级 ≠ 绕过**：即使关闭 License 校验或移除 License 文件，`@FeatureGate` 标注的商业功能仍会拒绝访问。
6. **机器指纹向后兼容**：不带 `machineFingerprint` 字段的旧版 License 可正常加载验证，仅输出 WARN 提示建议升级。
7. **容器指纹策略**：Docker/K8s 环境下推荐使用 `machine-fingerprint-override` 配置稳定标识，而非依赖容器内自动采集。

## 工具链

| 工具 | 用途 | 持有方 |
|------|------|--------|
| `LicenseGenerator gen-keys`（CLI） | 生成 RSA 密钥对 | 服务商 |
| `LicenseGenerator gen-fingerprint`（CLI） | 采集当前机器指纹（SHA-256） | 客户 |
| `LicenseGenerator gen-license`（CLI） | 发放 License（支持 `--fingerprint` 绑定） | 服务商 |
| `scripts/license/generate-license.sh` | 一键发放 License（支持 `--fingerprint`） | 服务商 |
| `LicenseValidator`（随包自动装配） | 应用内验证：签名 → 公钥指纹 → 机器指纹 → 有效期 | 客户 |
| `MachineFingerprintCollector` | 采集 MAC + CPU + 主板序列号并哈希 | 客户（运行时自动调用） |
| `@FeatureGate` + Aspect | 商业功能门禁 | 开发者 |

## TODO

- [ ] 实现在线激活机制（需联网）
- [ ] 支持 License 撤销列表（CRL）
- [ ] 添加 License 使用统计上报
- [ ] 实现灰度试用支持（部分功能限时试用）
