# 数据脱敏使用与运维

## 能力与边界

公共实现位于 [apex-common](../../apex-common/src/main/java/com/apex/scaffold/common/masking/JsonFieldMasker.java)，独立入口位于 [apex-masking-service](../../apex-masking-service/pom.xml)。两者共用 `DataMaskUtil`，支持姓名、手机号、邮箱、身份证格式、银行卡、地址和自定义首尾保留规则，也保留 IPv4、URL 和 GENERAL 类型。

脱敏用于限制展示与输出，不是加密、匿名化或证件真实性认证。规则仍保留部分可识别信息；调用方必须按用途配置最小保留范围。仅配置的精确字段名会脱敏，未配置字段原样保留，不会自动识别所有敏感内容。

| 类型 | 行为 |
| --- | --- |
| NAME | 修剪后保留首码点，单码点全遮盖 |
| PHONE | 去空白与连字符后匹配 `1` 开头的十一位数字，保留前三后四；非法值全遮盖 |
| EMAIL | 常规 ASCII 域名邮箱保留域名、本地部分首码点；单码点本地部分全遮盖，非法值全遮盖 |
| ID_CARD | 十五位数字或十八位末位允许 X，保留前三后四；不验证校验位，非法值全遮盖 |
| BANK_CARD | 去空白与连字符后九至十九位数字保留前四后四；不验证卡号真实性，非法值全遮盖 |
| ADDRESS | 保留前半码点，不解析行政区划 |
| CUSTOM | `customRule: "1,1"` 表示保留首尾各一码点；两段为 0–9999 的整数，无空格 |
| GENERAL | 四码点及以下全遮盖，否则保留前 30%、后 20%，向下取整 |
| IP_ADDRESS | 仅合法 IPv4 保留前两段；IPv6 等全遮盖 |
| URL | 合法 URI 遮盖查询和片段；含 userinfo 或无法解析 host 时全遮盖，路径仍保留 |

长度按 Unicode 码点计算，不拆开代理对；不按字素簇计算，组合字符可能被分别处理。null 和空字符串保持原值。首尾保留重叠时全遮盖。与旧实现相比，非法格式和短值不再返回原文，银行卡展示采用连续星号；调用方不应依赖旧星号数量或分组格式。

## 本地与 Jackson 使用

```java
DataMaskUtil.maskPhone("13800138000"); // 138****8000
DataMaskUtil.mask("𠮷野家東京", MaskType.CUSTOM, "1,1"); // 𠮷***京

class Contact {
    @DataMask(type = MaskType.PHONE)
    public String phone;

    @DataMask(type = MaskType.CUSTOM, customRule = "1,1")
    public String externalCode;
}
```

`@DataMask` 通过 Jackson 元注解接入 `DataMaskSerializer`。字段和 getter 均可使用，普通 `new ObjectMapper()`、项目 `JsonUtil` 和 Spring Jackson 输出均可复用，前提是没有禁用 Jackson 注解或覆盖序列化器。它仅改变序列化结果，不修改内存对象、不处理反序列化输入。只支持字符串属性；误标注非字符串和非法 CUSTOM 配置会拒绝序列化。null 保留为 null。

```java
Map<String, JsonFieldMasker.Rule> rules = Map.of(
    "phone", new JsonFieldMasker.Rule(MaskType.PHONE, null));
JsonNode masked = JsonFieldMasker.mask(input, rules);
```

JSON 实现返回深拷贝，递归匹配对象中的精确字段名，含数组内对象。选中字段为数字、对象、数组或布尔值时整个值替换为 `"***"`；null 保留。遍历深度超过 64 会拒绝处理。不支持 JSONPath、按路径区分同名字段或原地改写。

日志手机号 `SensitiveDataUtil.maskPhone` 复用统一规则；空白返回 `***`。既有 `fingerprint` 语义保持：UTF-8 SHA-256、前十二个十六进制字符、`sha256:` 前缀，空白为 `sha256:empty`。它不是认证凭据。`@DataMask` 不保证普通 `toString()`、直接日志插值或自定义反射日志清洗器安全，调用方仍须使用安全日志工具，禁止记录原始请求。

## 独立服务配置

服务采用 `apex-services-bom` parent，仅内部依赖 common，无数据库、Redis 或 Flyway。默认端口 8096，可通过 `MASKING_PORT` 修改。必需环境变量：

- `MASKING_JWT_ISSUER`：受信任 JWT issuer。
- `MASKING_JWT_JWK_SET_URI`：受信任签名公钥 JWKS 地址，生产使用 HTTPS。
- `MASKING_JWT_AUDIENCE`：本服务 audience。
- `MASKING_ALLOWED_TENANT`：默认 v1 允许访问的一个租户。

默认信任配置为空，不提供匿名业务模式。配置属性使用 Jakarta Validation 校验必填值、租户和字段集合；CUSTOM 在规则构造时校验。需要多个租户或版本时，部署完整外部 YAML，例如通过 `--spring.config.additional-location` 加载：

```yaml
apex:
  masking:
    issuer: https://identity.example
    jwk-set-uri: https://identity.example/.well-known/jwks.json
    audience: masking
    versions:
      v1:
        tenants: [tenant-a]
        fields:
          phone:
            type: PHONE
          code:
            type: CUSTOM
            custom-rule: "1,1"
      v2:
        tenants: [tenant-b]
        fields:
          email:
            type: EMAIL
```

这些地址和租户仅为配置结构示例。规则在启动时绑定并复制为不可变集合，通过配置发布与重启管理固定版本；没有数据库 CRUD、在线修改接口、热刷新或审批工作流。默认 v1 字段见 [application.yml](../../apex-masking-service/src/main/resources/application.yml)。Spring 配置映射可能合并版本和字段，发布时应核对最终配置，不能将覆盖文件误认为删除所有默认字段。

## API 与安全

| 接口 | 权限 | 说明 |
| --- | --- | --- |
| `POST /api/v1/masking/batch` | `masking:apply` scope | 按租户授权版本脱敏 |
| `GET /api/v1/masking/rules` | `masking:rules` scope | 仅列出当前租户可访问版本及字段规则，不输出租户授权名单 |

Spring Security 使用 `SCOPE_` authority 映射 scope。JWT 通过 JWKS 验证签名，并校验 issuer、audience 与默认时间约束。租户只读取已认证令牌的非空字符串 `tenant_id` claim；`X-Tenant-Id`、请求 JSON 或透传头不能覆盖它。未知版本或未授权租户执行请求返回 403。规则列表对没有授权版本的已认证租户返回空集合。

请求示例（发送时另附有效 `Authorization: Bearer ...`）：

```json
{"version":"v1","documents":[{"phone":"13800138000"}]}
```

成功响应使用 `Result`，code 为 0，data 包含 version 与 documents；示例手机号输出 `138****8000`。版本最长 64 字符，documents 为 1–100 项且不允许 null 项；未知顶层字段拒绝，以防客户端上传覆盖规则。内存解码上限 256KB，超限返回 413；校验或 JSON 解析错误返回固定业务错误，不回显原输入。安全链的 401/403 使用 Spring Security 协议响应，不承诺与业务 Result 完全相同。

Handler 不记录请求或异常原文，请求 DTO 的 `toString()` 固定为 redacted。网关、代理、APM 和客户端也应关闭请求体、响应体及完整令牌采集。仅 `/actuator/health` 匿名可读，健康细节关闭；其余路径需要认证。OpenAPI `/v3/api-docs` 也需要认证，使用函数式路由元数据和 bearerAuth schema。

本实现没有服务内速率限制或远程审计持久化；网关已包含脱敏路由，上线时配置 TLS、受信任 JWKS 网络访问、请求速率与并发限制，核对 token 的 scope 与 tenant_id 签发契约。服务处理原文但不持久化，未配置字段仍可能出现在响应中。

## 构建与验证

所有命令先在仓库根目录加载固定 JDK 与 mvnd：

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B -pl apex-common -am install
mvnd -T4 -B -f apex-masking-service/pom.xml verify
```

根 reactor 已注册 `apex-masking-service`，services BOM 统一管理 SpringDoc，Compose 提供可选 `masking` profile，网关包含 `/api/v1/masking/**` 路由。历史模块级验证如下；本次统一门禁及尚未验证的部署条件以交付报告为准。

2026-10-13 本轮实际验证：

- 首轮公共安全测试观察到原实现非法手机号泄漏、Unicode 截断和日志短值泄漏，随后实现修复；JSON 与服务测试先运行，因缺少实现失败后补实现。
- `mvnd -T4 -B -pl apex-common -am spotless:apply verify` 最终 BUILD SUCCESS：538 tests，Failures 0 / Errors 0 / Skipped 0；Spotless 与 JaCoCo 检查通过，PMD check 通过。p3c 仍输出内部 `maximum Iterations exceeded` 日志，不能据此声称静态分析穷尽全部路径。
- `mvnd -B -f apex-masking-service/pom.xml spotless:apply verify` BUILD SUCCESS：6 tests，Failures 0 / Errors 0 / Skipped 0；Spotless、PMD 和 JaCoCo 检查通过，并生成可执行 jar。
- 公共测试覆盖非法/短输入、Unicode、普通 ObjectMapper、getter、CUSTOM、null、非字符串拒绝、JSON 副本及非文本遮盖、fingerprint 精确值。
- 服务测试覆盖无认证、scope、租户头伪造、租户隔离、规则覆盖拒绝、未知版本、错误不回显、规则列表隔离和 256KB 上限；本机临时 JWKS + RSA 测试验证有效签名及错误签名、issuer、audience、过期拒绝。

上述为历史模块级验证，当时未运行全仓 `clean verify`；本次统一门禁见 [交付报告](../08-project-management/reports/service-enhancement-delivery-report.md)。仍未验证真实身份中心、生产 JWKS 轮换、生产 TLS、外部日志采集或网关端到端部署。Failsafe 生命周期执行成功，但这两个模块没有新增由 Failsafe 命名模式选中的 IT；接口测试在 Surefire 中运行。没有提供性能或并发容量保证。
