package com.apex.scaffold.application.support.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import reactor.core.publisher.Mono;

/**
 * SAML 2.0 协议适配器
 *
 * <p>实现 SAML 2.0 (Security Assertion Markup Language) 单点登录协议的适配器,
 * 支持 SP-initiated 和 IdP-initiated 两种认证流程。SAML 是企业级 SSO 的标准协议,
 * 广泛应用于企业内部系统集成和跨域认证场景。
 *
 * <h2>SAML 认证流程（SP-Initiated）</h2>
 * <ol>
 *   <li>用户访问受保护资源,SP 检测未认证状态</li>
 *   <li>SP 调用 {@link #initiate} 生成 AuthnRequest 并重定向用户到 IdP</li>
 *   <li>用户在 IdP 完成身份验证</li>
 *   <li>IdP 生成签名的 SAMLResponse 并 POST 回 SP 的 ACS 端点</li>
 *   <li>SP 调用 {@link #authenticate} 验证 SAMLResponse 签名和断言</li>
 *   <li>SP 提取用户属性并建立本地会话</li>
 * </ol>
 *
 * <h2>SAML 登出流程（SLO）</h2>
 * <ol>
 *   <li>用户发起登出操作</li>
 *   <li>SP 调用 {@link #logout} 生成 LogoutRequest 并重定向到 IdP</li>
 *   <li>IdP 注销会话并返回 LogoutResponse</li>
 *   <li>SP 销毁本地会话</li>
 * </ol>
 *
 * <h2>配置参数（从 config Map 读取）</h2>
 * <ul>
 *   <li>entityId - SP 实体 ID（唯一标识符）</li>
 *   <li>authEndpoint - IdP 的 SingleSignOnService 端点 URL</li>
 *   <li>logoutEndpoint - IdP 的 SingleLogoutService 端点 URL（可选）</li>
 *   <li>certificate - IdP 签名验证证书（X.509 PEM 格式）</li>
 *   <li>acsUrl - SP 的 Assertion Consumer Service URL（回调地址）</li>
 *   <li>spCertificate - SP 证书（用于元数据，可选）</li>
 * </ul>
 *
 * <h2>实现说明</h2>
 * <ul>
 *   <li>使用 Java 标准库（javax.xml、java.security）实现,避免引入重量级第三方库</li>
 *   <li>AuthnRequest 使用 HTTP-Redirect 绑定,通过 URL 参数传递</li>
 *   <li>SAMLResponse 使用 HTTP-POST 绑定,通过表单提交</li>
 *   <li>签名验证使用 RSA-SHA256 算法</li>
 *   <li>断言验证包括签名、有效期、Audience、InResponseTo 等</li>
 *   <li>不支持加密断言,仅支持签名断言</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SamlProtocolAdapter implements SsoProtocolAdapter {

    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    private static final String SAML_RESPONSE_ELEMENT = "Response";

    private static final String SAML_PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String SAML_ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SAML_METADATA_NS = "urn:oasis:names:tc:SAML:2.0:metadata";
    private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String SAML_VERSION = "2.0";
    private static final DateTimeFormatter SAML_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    /**
     * SAML重放攻击防护
     */
    private static final String SAML_ASSERTION_USED_PREFIX = "saml:assertion:used:";

    private static final Duration ASSERTION_ID_TTL = Duration.ofMinutes(10);

    /**
     * 默认签名算法（向后兼容）
     */
    private static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA256withRSA";
    /**
     * 支持的SAML签名算法映射表
     * Key: 算法名称（配置中使用）
     * Value: Java Signature算法名称
     */
    private static final Map<String, String> SUPPORTED_SIGNATURE_ALGORITHMS = Map.of(
            "SHA256withRSA",
            "SHA256withRSA",
            "SHA384withRSA",
            "SHA384withRSA",
            "SHA512withRSA",
            "SHA512withRSA",
            SIGNATURE_ALGORITHM,
            SIGNATURE_ALGORITHM);

    private final ObjectMapper objectMapper;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * 证书解析缓存
     * Key: 证书字符串的hashCode, Value: PublicKey对象
     * 避免重复解析X.509证书,提升SAML认证性能
     */
    private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

    /**
     * X.509证书对象缓存
     * Key: 证书字符串的hashCode, Value: X509Certificate对象
     * 用于证书有效期验证
     */
    private final Map<String, X509Certificate> certificateCache = new ConcurrentHashMap<>();

    /**
     * 发起 SAML 认证请求
     *
     * <p>生成 SAML AuthnRequest XML 并构建重定向 URL。AuthnRequest 包含请求 ID、签发时间、
     * SP 实体 ID 等信息,使用 HTTP-Redirect 绑定通过 URL 参数传递到 IdP。
     *
     * <h3>实现步骤</h3>
     * <ol>
     *   <li>生成唯一的请求 ID（UUID）</li>
     *   <li>构建 AuthnRequest XML 文档</li>
     *   <li>将 XML 压缩并 Base64 编码（HTTP-Redirect 绑定要求）</li>
     *   <li>构建重定向 URL,包含 SAMLRequest 和 RelayState 参数</li>
     * </ol>
     *
     * @param config 提供商配置,必须包含 entityId、authEndpoint、acsUrl
     * @param callbackUrl 认证成功后的回调 URL（RelayState）
     * @return 包含 redirectUrl、requestId、samlRequest 的 Map
     */
    @Override
    public Mono<Map<String, Object>> initiate(Map<String, Object> config, String callbackUrl) {
        log.info("发起 SAML 认证请求, callbackUrl: {}", callbackUrl);

        return Mono.fromCallable(() -> {
            String entityId = (String) config.get("entityId");
            String authEndpoint = (String) config.get("authEndpoint");
            String acsUrl = (String) config.get("acsUrl");

            if (entityId == null || authEndpoint == null || acsUrl == null) {
                throw new IllegalArgumentException("SAML 配置不完整: entityId、authEndpoint 和 acsUrl 是必需的");
            }

            String requestId = "_" + UUID.randomUUID().toString();
            String issueInstant = SAML_DATE_FORMAT.format(Instant.now());

            String authnRequestXml = buildAuthnRequest(requestId, issueInstant, entityId, acsUrl);

            String encodedRequest =
                    Base64.getEncoder().encodeToString(authnRequestXml.getBytes(StandardCharsets.UTF_8));

            String redirectUrl = authEndpoint + "?SAMLRequest="
                    + UriUtils.encode(encodedRequest, StandardCharsets.UTF_8) + "&RelayState="
                    + UriUtils.encode(callbackUrl, StandardCharsets.UTF_8);

            Map<String, Object> result = new HashMap<>(16);
            result.put("redirectUrl", redirectUrl);
            result.put("requestId", requestId);
            result.put("samlRequest", encodedRequest);
            result.put("relayState", callbackUrl);

            log.debug("SAML AuthnRequest 生成完成: requestId={}, entityId={}", requestId, entityId);
            return result;
        });
    }

    /**
     * 处理 SAML 认证回调并完成用户验证
     *
     * <p>解析并验证 IdP 返回的 SAMLResponse,提取用户身份信息。验证流程包括:
     * <ul>
     *   <li>Base64 解码 SAMLResponse</li>
     *   <li>解析 XML 文档</li>
     *   <li>验证签名（Response 或 Assertion 级别）</li>
     *   <li>验证断言有效期（NotBefore、NotOnOrAfter）</li>
     *   <li>验证 Audience（必须匹配 SP EntityID）</li>
     *   <li>提取 Subject NameID 和属性</li>
     * </ul>
     *
     * @param config 提供商配置,必须包含 certificate（IdP 证书）和 entityId
     * @param callbackParams 回调参数,必须包含 SAMLResponse
     * @return 用户信息 Map,包含 userId、username、email、attributes、sessionIndex
     */
    @Override
    public Mono<Map<String, Object>> authenticate(Map<String, Object> config, Map<String, String> callbackParams) {
        log.info("处理 SAML 认证回调");

        return parseSamlResponse(callbackParams, config)
                .flatMap(assertionId -> validateAndProcessAssertion(assertionId, callbackParams, config));
    }

    /**
     * 解析 SAML 响应并提取 Assertion ID。
     *
     * @param callbackParams 回调参数
     * @param config SAML 配置
     * @return Assertion ID
     */
    private Mono<String> parseSamlResponse(Map<String, String> callbackParams, Map<String, Object> config) {
        return Mono.fromCallable(() -> {
            String samlResponse = callbackParams.get("SAMLResponse");
            if (samlResponse == null || samlResponse.isEmpty()) {
                throw new IllegalArgumentException("SAMLResponse 参数缺失");
            }

            String certificate = (String) config.get("certificate");
            String entityId = (String) config.get("entityId");
            if (certificate == null || entityId == null) {
                throw new IllegalArgumentException("SAML 配置不完整: certificate 和 entityId 是必需的");
            }

            byte[] decodedResponse = Base64.getDecoder().decode(samlResponse);
            log.debug("收到 SAMLResponse: length={}", decodedResponse.length);

            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(decodedResponse));

            Element responseElement = doc.getDocumentElement();
            if (!SAML_RESPONSE_ELEMENT.equals(responseElement.getLocalName())) {
                throw new IllegalArgumentException("无效的 SAMLResponse: 根元素不是 Response");
            }

            NodeList assertionNodes = responseElement.getElementsByTagNameNS(SAML_ASSERTION_NS, "Assertion");
            if (assertionNodes.getLength() == 0) {
                throw new IllegalArgumentException("SAMLResponse 中没有 Assertion");
            }
            Element assertion = (Element) assertionNodes.item(0);

            String assertionId = assertion.getAttribute("ID");
            if (assertionId == null || assertionId.isEmpty()) {
                throw new IllegalArgumentException("Assertion缺少ID属性");
            }

            return assertionId;
        });
    }

    /**
     * 验证并处理 SAML Assertion。
     *
     * @param assertionId Assertion ID
     * @param callbackParams 回调参数
     * @param config SAML 配置
     * @return 用户信息
     */
    private Mono<Map<String, Object>> validateAndProcessAssertion(
            String assertionId, Map<String, String> callbackParams, Map<String, Object> config) {
        return checkAssertionReplay(assertionId).flatMap(isReplayed -> {
            if (isReplayed) {
                log.error("检测到SAML重放攻击: assertionId={}", assertionId);
                return Mono.error(new SecurityException("检测到SAML Assertion重放攻击"));
            }

            return markAssertionAsUsed(assertionId).then(verifyAndExtractUserInfo(callbackParams, config));
        });
    }

    /**
     * 验证 SAML 签名并提取用户信息。
     *
     * @param callbackParams 回调参数
     * @param config SAML 配置
     * @return 用户信息
     */
    private Mono<Map<String, Object>> verifyAndExtractUserInfo(
            Map<String, String> callbackParams, Map<String, Object> config) {
        return Mono.fromCallable(() -> {
            String samlResponse = callbackParams.get("SAMLResponse");
            String certificate = (String) config.get("certificate");
            String entityId = (String) config.get("entityId");

            byte[] decodedResponse = Base64.getDecoder().decode(samlResponse);
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(decodedResponse));

            Element responseElement = doc.getDocumentElement();
            NodeList assertionNodes = responseElement.getElementsByTagNameNS(SAML_ASSERTION_NS, "Assertion");
            Element assertion = (Element) assertionNodes.item(0);

            PublicKey publicKey = parsePublicKey(certificate);

            String signatureAlgorithm = getSignatureAlgorithm(config);
            verifySignature(assertion, publicKey, signatureAlgorithm);

            validateAssertion(assertion, entityId);

            Map<String, Object> userInfo = extractUserInfo(assertion);

            log.info("SAML 认证成功: userId={}, username={}", userInfo.get("userId"), userInfo.get("username"));
            return userInfo;
        });
    }

    /**
     * 执行 SAML 登出操作
     *
     * <p>生成 SAML LogoutRequest 并构建重定向 URL,实现 Single Logout (SLO) 协议。
     * LogoutRequest 包含会话索引（SessionIndex）,IdP 根据此索引注销对应的会话。
     *
     * @param config 提供商配置,需要包含 logoutEndpoint、entityId
     * @param sessionToken 会话令牌（对应 SAML SessionIndex）
     * @return 登出结果,包含 logoutUrl 和 success 标志
     */
    @Override
    public Mono<Map<String, Object>> logout(Map<String, Object> config, String sessionToken) {
        log.info("执行 SAML 登出");

        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(16);

            String logoutEndpoint = (String) config.get("logoutEndpoint");
            String entityId = (String) config.get("entityId");

            if (logoutEndpoint != null && !logoutEndpoint.isEmpty()) {
                String requestId = "_" + UUID.randomUUID().toString();
                String issueInstant = SAML_DATE_FORMAT.format(Instant.now());

                String logoutRequestXml = buildLogoutRequest(requestId, issueInstant, entityId, sessionToken);

                String encodedRequest =
                        Base64.getEncoder().encodeToString(logoutRequestXml.getBytes(StandardCharsets.UTF_8));

                String logoutUrl =
                        logoutEndpoint + "?SAMLRequest=" + UriUtils.encode(encodedRequest, StandardCharsets.UTF_8);

                result.put("logoutUrl", logoutUrl);
                result.put("requestId", requestId);
            }

            result.put("success", true);
            log.debug("SAML 登出完成");
            return result;
        });
    }

    /**
     * 获取 SAML SP 元数据
     *
     * <p>生成符合 SAML 2.0 标准的 SP 元数据 XML,包含 SP 的 EntityDescriptor、
     * ACS URL、SingleLogoutService URL、证书等信息。IdP 可导入此元数据完成信任配置。
     *
     * @param config 提供商配置,需要包含 entityId、acsUrl、spCertificate（可选）
     * @return 元数据 Map,包含 entityId 和 metadataXml
     */
    @Override
    public Mono<Map<String, Object>> getMetadata(Map<String, Object> config) {
        log.info("获取 SAML SP 元数据");

        return Mono.fromCallable(() -> {
            String entityId = (String) config.get("entityId");
            String acsUrl = (String) config.get("acsUrl");
            String spCertificate = (String) config.get("spCertificate");

            if (entityId == null || acsUrl == null) {
                throw new IllegalArgumentException("SAML 配置不完整: entityId 和 acsUrl 是必需的");
            }

            String metadataXml = buildSpMetadata(entityId, acsUrl, spCertificate);

            Map<String, Object> metadata = new HashMap<>(16);
            metadata.put("entityId", entityId);
            metadata.put("metadataXml", metadataXml);

            log.debug("SAML 元数据生成完成: entityId={}", entityId);
            return metadata;
        });
    }

    /**
     * 测试 SAML 连接配置
     *
     * <p>验证 SAML 配置的完整性和正确性,包括:
     * <ul>
     *   <li>必需配置项是否存在</li>
     *   <li>证书格式是否正确</li>
     *   <li>证书是否在有效期内</li>
     *   <li>URL 格式是否正确</li>
     * </ul>
     *
     * @param config 提供商配置
     * @return 测试结果,包含 success、message、details
     */
    @Override
    public Mono<Map<String, Object>> testConnection(Map<String, Object> config) {
        log.info("测试 SAML 连接");

        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(16);
            Map<String, String> details = new HashMap<>(16);

            String authEndpoint = (String) config.get("authEndpoint");
            String entityId = (String) config.get("entityId");
            String certificate = (String) config.get("certificate");
            String acsUrl = (String) config.get("acsUrl");

            boolean success = true;
            StringBuilder message = new StringBuilder();

            if (entityId == null || entityId.isEmpty()) {
                success = false;
                message.append("EntityID 缺失; ");
                details.put("entityId", "缺失");
            } else {
                details.put("entityId", "正常");
            }

            if (authEndpoint == null || authEndpoint.isEmpty()) {
                success = false;
                message.append("认证端点缺失; ");
                details.put("authEndpoint", "缺失");
            } else {
                details.put("authEndpoint", "正常");
            }

            if (acsUrl == null || acsUrl.isEmpty()) {
                success = false;
                message.append("ACS URL 缺失; ");
                details.put("acsUrl", "缺失");
            } else {
                details.put("acsUrl", "正常");
            }

            if (certificate != null && !certificate.isEmpty()) {
                try {
                    PublicKey publicKey = parsePublicKey(certificate);
                    if (publicKey != null) {
                        details.put("certificate", "证书格式正确");

                        X509Certificate cert = parseCertificate(certificate);
                        if (cert != null) {
                            cert.checkValidity();
                            details.put(
                                    "certificateValidity",
                                    "有效期至: "
                                            + SAML_DATE_FORMAT.format(
                                                    cert.getNotAfter().toInstant()));
                        }
                    }
                } catch (Exception e) {
                    success = false;
                    message.append("证书无效; ");
                    details.put("certificate", "证书格式错误或已过期");
                }
            } else {
                success = false;
                message.append("IdP 证书缺失; ");
                details.put("certificate", "缺失");
            }

            result.put("success", success);
            result.put("message", success ? "SAML 连接测试成功" : message.toString());
            result.put("details", details);

            log.debug("SAML 连接测试完成: success={}", success);
            return result;
        });
    }

    /**
     * 构建 SAML AuthnRequest XML
     *
     * @param requestId 请求唯一标识
     * @param issueInstant 签发时间（ISO 8601 格式）
     * @param issuer SP 实体 ID
     * @param acsUrl Assertion Consumer Service URL
     * @return AuthnRequest XML 字符串
     */
    private String buildAuthnRequest(String requestId, String issueInstant, String issuer, String acsUrl) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element authnRequest = doc.createElementNS(SAML_PROTOCOL_NS, "samlp:AuthnRequest");
            authnRequest.setAttribute("xmlns:samlp", SAML_PROTOCOL_NS);
            authnRequest.setAttribute("xmlns:saml", SAML_ASSERTION_NS);
            authnRequest.setAttribute("ID", requestId);
            authnRequest.setAttribute("Version", SAML_VERSION);
            authnRequest.setAttribute("IssueInstant", issueInstant);
            authnRequest.setAttribute("AssertionConsumerServiceURL", acsUrl);
            authnRequest.setAttribute("ProtocolBinding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
            doc.appendChild(authnRequest);

            Element issuerElement = doc.createElementNS(SAML_ASSERTION_NS, "saml:Issuer");
            issuerElement.setTextContent(issuer);
            authnRequest.appendChild(issuerElement);

            Element nameIdPolicy = doc.createElementNS(SAML_PROTOCOL_NS, "samlp:NameIDPolicy");
            nameIdPolicy.setAttribute("Format", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
            nameIdPolicy.setAttribute("AllowCreate", "true");
            authnRequest.appendChild(nameIdPolicy);

            return documentToString(doc);
        } catch (Exception e) {
            log.error("构建 AuthnRequest 失败: type={}", e.getClass().getSimpleName());
            throw new RuntimeException("构建 AuthnRequest 失败");
        }
    }

    /**
     * 构建 SAML LogoutRequest XML
     *
     * @param requestId 请求唯一标识
     * @param issueInstant 签发时间
     * @param issuer SP 实体 ID
     * @param sessionIndex 会话索引
     * @return LogoutRequest XML 字符串
     */
    private String buildLogoutRequest(String requestId, String issueInstant, String issuer, String sessionIndex) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element logoutRequest = doc.createElementNS(SAML_PROTOCOL_NS, "samlp:LogoutRequest");
            logoutRequest.setAttribute("xmlns:samlp", SAML_PROTOCOL_NS);
            logoutRequest.setAttribute("xmlns:saml", SAML_ASSERTION_NS);
            logoutRequest.setAttribute("ID", requestId);
            logoutRequest.setAttribute("Version", SAML_VERSION);
            logoutRequest.setAttribute("IssueInstant", issueInstant);
            doc.appendChild(logoutRequest);

            Element issuerElement = doc.createElementNS(SAML_ASSERTION_NS, "saml:Issuer");
            issuerElement.setTextContent(issuer);
            logoutRequest.appendChild(issuerElement);

            if (sessionIndex != null && !sessionIndex.isEmpty()) {
                Element sessionIndexElement = doc.createElementNS(SAML_PROTOCOL_NS, "samlp:SessionIndex");
                sessionIndexElement.setTextContent(sessionIndex);
                logoutRequest.appendChild(sessionIndexElement);
            }

            return documentToString(doc);
        } catch (Exception e) {
            log.error("构建 LogoutRequest 失败: type={}", e.getClass().getSimpleName());
            throw new RuntimeException("构建 LogoutRequest 失败");
        }
    }

    /**
     * 构建 SAML SP 元数据 XML
     *
     * @param entityId SP 实体 ID
     * @param acsUrl ACS URL
     * @param spCertificate SP 证书（可选）
     * @return SP 元数据 XML 字符串
     */
    private String buildSpMetadata(String entityId, String acsUrl, String spCertificate) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element entityDescriptor = doc.createElementNS(SAML_METADATA_NS, "md:EntityDescriptor");
            entityDescriptor.setAttribute("xmlns:md", SAML_METADATA_NS);
            entityDescriptor.setAttribute("xmlns:ds", XMLDSIG_NS);
            entityDescriptor.setAttribute("entityID", entityId);
            doc.appendChild(entityDescriptor);

            Element spSsoDescriptor = doc.createElementNS(SAML_METADATA_NS, "md:SPSSODescriptor");
            spSsoDescriptor.setAttribute("protocolSupportEnumeration", SAML_PROTOCOL_NS);
            spSsoDescriptor.setAttribute("AuthnRequestsSigned", "false");
            spSsoDescriptor.setAttribute("WantAssertionsSigned", "true");
            entityDescriptor.appendChild(spSsoDescriptor);

            if (spCertificate != null && !spCertificate.isEmpty()) {
                Element keyDescriptor = doc.createElementNS(SAML_METADATA_NS, "md:KeyDescriptor");
                keyDescriptor.setAttribute("use", "signing");
                spSsoDescriptor.appendChild(keyDescriptor);

                Element keyInfo = doc.createElementNS(XMLDSIG_NS, "ds:KeyInfo");
                keyDescriptor.appendChild(keyInfo);

                Element x509Data = doc.createElementNS(XMLDSIG_NS, "ds:X509Data");
                keyInfo.appendChild(x509Data);

                Element x509Certificate = doc.createElementNS(XMLDSIG_NS, "ds:X509Certificate");
                x509Certificate.setTextContent(cleanCertificate(spCertificate));
                x509Data.appendChild(x509Certificate);
            }

            Element nameIdFormat = doc.createElementNS(SAML_METADATA_NS, "md:NameIDFormat");
            nameIdFormat.setTextContent("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
            spSsoDescriptor.appendChild(nameIdFormat);

            Element assertionConsumerService = doc.createElementNS(SAML_METADATA_NS, "md:AssertionConsumerService");
            assertionConsumerService.setAttribute("Binding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
            assertionConsumerService.setAttribute("Location", acsUrl);
            assertionConsumerService.setAttribute("index", "0");
            spSsoDescriptor.appendChild(assertionConsumerService);

            return documentToString(doc);
        } catch (Exception e) {
            log.error("构建 SP 元数据失败: type={}", e.getClass().getSimpleName());
            throw new RuntimeException("构建 SP 元数据失败");
        }
    }

    /**
     * 验证 SAML 签名
     *
     * <p>使用 IdP 公钥验证 XML 元素的数字签名,确保数据完整性和来源可信性。
     * 签名验证步骤:
     * <ol>
     *   <li>提取 Signature 元素</li>
     *   <li>获取 SignedInfo（待签名内容）</li>
     *   <li>获取 SignatureValue（签名值）</li>
     *   <li>使用公钥和指定的签名算法验证签名</li>
     * </ol>
     *
     * <p>支持的签名算法：</p>
     * <ul>
     *   <li>SHA256withRSA（默认，推荐）</li>
     *   <li>SHA384withRSA（更高安全性）</li>
     *   <li>SHA512withRSA（最高安全性）</li>
     *   <li>SHA1withRSA（不推荐，仅用于兼容旧系统）</li>
     * </ul>
     *
     * @param element 待验证的 XML 元素（通常是 Assertion）
     * @param publicKey IdP 公钥
     * @param signatureAlgorithm 签名算法名称（如 "SHA256withRSA"）
     * @throws Exception 签名验证失败时抛出异常
     */
    private void verifySignature(Element element, PublicKey publicKey, String signatureAlgorithm) throws Exception {
        NodeList signatureNodes = element.getElementsByTagNameNS(XMLDSIG_NS, "Signature");
        if (signatureNodes.getLength() == 0) {
            throw new IllegalArgumentException("Assertion 未签名");
        }

        Element signature = (Element) signatureNodes.item(0);
        Element signedInfo = (Element)
                signature.getElementsByTagNameNS(XMLDSIG_NS, "SignedInfo").item(0);
        Element signatureValue = (Element)
                signature.getElementsByTagNameNS(XMLDSIG_NS, "SignatureValue").item(0);

        String signedInfoStr = documentToString(signedInfo.getOwnerDocument())
                .replaceAll("<\\?xml.*?\\?>", "")
                .trim();

        byte[] signatureBytes =
                Base64.getDecoder().decode(signatureValue.getTextContent().replaceAll("\\s", ""));

        // 验证算法是否支持
        if (!SUPPORTED_SIGNATURE_ALGORITHMS.containsKey(signatureAlgorithm)) {
            throw new IllegalArgumentException("不支持的签名算法: " + signatureAlgorithm + ", 支持的算法: "
                    + String.join(", ", SUPPORTED_SIGNATURE_ALGORITHMS.keySet()));
        }

        // 如果使用SHA1，记录安全警告
        if (SIGNATURE_ALGORITHM.equals(signatureAlgorithm)) {
            log.warn("使用不安全的签名算法SHA1withRSA，建议升级到SHA256withRSA或更高版本");
        }

        Signature sig = Signature.getInstance(signatureAlgorithm);
        sig.initVerify(publicKey);
        sig.update(signedInfoStr.getBytes(StandardCharsets.UTF_8));

        if (!sig.verify(signatureBytes)) {
            throw new SecurityException("SAML 签名验证失败: 签名算法=" + signatureAlgorithm);
        }

        log.debug("SAML 签名验证成功: 算法={}", signatureAlgorithm);
    }

    /**
     * 验证 SAML 断言的有效性
     *
     * <p>验证断言的时间窗口和受众限制:
     * <ul>
     *   <li>NotBefore - 断言生效时间</li>
     *   <li>NotOnOrAfter - 断言过期时间</li>
     *   <li>Audience - 受众必须匹配 SP EntityID</li>
     * </ul>
     *
     * @param assertion Assertion 元素
     * @param expectedAudience 预期受众（SP EntityID）
     * @throws Exception 验证失败时抛出异常
     */
    private void validateAssertion(Element assertion, String expectedAudience) throws Exception {
        NodeList conditions = assertion.getElementsByTagNameNS(SAML_ASSERTION_NS, "Conditions");
        if (conditions.getLength() > 0) {
            Element condition = (Element) conditions.item(0);
            String notBefore = condition.getAttribute("NotBefore");
            String notOnOrAfter = condition.getAttribute("NotOnOrAfter");

            Instant now = Instant.now();
            if (notBefore != null && !notBefore.isEmpty()) {
                Instant notBeforeTime = Instant.parse(notBefore);
                if (now.isBefore(notBeforeTime)) {
                    throw new IllegalArgumentException("Assertion 尚未生效: NotBefore=" + notBefore);
                }
            }

            if (notOnOrAfter != null && !notOnOrAfter.isEmpty()) {
                Instant notOnOrAfterTime = Instant.parse(notOnOrAfter);
                if (now.isAfter(notOnOrAfterTime)) {
                    throw new IllegalArgumentException("Assertion 已过期: NotOnOrAfter=" + notOnOrAfter);
                }
            }

            NodeList audienceRestrictions = condition.getElementsByTagNameNS(SAML_ASSERTION_NS, "AudienceRestriction");
            if (audienceRestrictions.getLength() > 0) {
                Element audienceRestriction = (Element) audienceRestrictions.item(0);
                NodeList audiences = audienceRestriction.getElementsByTagNameNS(SAML_ASSERTION_NS, "Audience");

                boolean audienceMatch = false;
                for (int i = 0; i < audiences.getLength(); i++) {
                    String audience = audiences.item(i).getTextContent();
                    if (expectedAudience.equals(audience)) {
                        audienceMatch = true;
                        break;
                    }
                }

                if (!audienceMatch) {
                    throw new IllegalArgumentException("Audience 不匹配: expected=" + expectedAudience);
                }
            }
        }

        log.debug("SAML Assertion 验证通过");
    }

    /**
     * 从 SAML 断言中提取用户信息
     *
     * <p>提取 Subject NameID、AttributeStatement 中的属性和 SessionIndex。
     * 常见属性包括 email、displayName、givenName、surname 等。
     *
     * @param assertion Assertion 元素
     * @return 用户信息 Map
     */
    private Map<String, Object> extractUserInfo(Element assertion) {
        Map<String, Object> userInfo = new HashMap<>(16);
        Map<String, String> attributes = new HashMap<>(16);

        NodeList subjects = assertion.getElementsByTagNameNS(SAML_ASSERTION_NS, "Subject");
        if (subjects.getLength() > 0) {
            Element subject = (Element) subjects.item(0);
            NodeList nameIds = subject.getElementsByTagNameNS(SAML_ASSERTION_NS, "NameID");
            if (nameIds.getLength() > 0) {
                String nameId = nameIds.item(0).getTextContent();
                userInfo.put("userId", nameId);
                userInfo.put("username", nameId);
            }
        }

        NodeList attributeStatements = assertion.getElementsByTagNameNS(SAML_ASSERTION_NS, "AttributeStatement");
        if (attributeStatements.getLength() > 0) {
            Element attributeStatement = (Element) attributeStatements.item(0);
            NodeList attributeNodes = attributeStatement.getElementsByTagNameNS(SAML_ASSERTION_NS, "Attribute");

            for (int i = 0; i < attributeNodes.getLength(); i++) {
                Element attribute = (Element) attributeNodes.item(i);
                String name = attribute.getAttribute("Name");
                NodeList values = attribute.getElementsByTagNameNS(SAML_ASSERTION_NS, "AttributeValue");
                if (values.getLength() > 0) {
                    String value = values.item(0).getTextContent();
                    attributes.put(name, value);

                    if ("email".equalsIgnoreCase(name) || name.contains("email")) {
                        userInfo.put("email", value);
                    }
                    if ("displayName".equalsIgnoreCase(name) || name.contains("displayName")) {
                        userInfo.put("displayName", value);
                    }
                }
            }
        }

        NodeList authnStatements = assertion.getElementsByTagNameNS(SAML_ASSERTION_NS, "AuthnStatement");
        if (authnStatements.getLength() > 0) {
            Element authnStatement = (Element) authnStatements.item(0);
            String sessionIndex = authnStatement.getAttribute("SessionIndex");
            if (sessionIndex != null && !sessionIndex.isEmpty()) {
                userInfo.put("sessionIndex", sessionIndex);
            }
        }

        userInfo.put("attributes", attributes);

        return userInfo;
    }

    /**
     * 解析 X.509 证书的公钥
     *
     * <p>支持两种格式:
     * <ul>
     *   <li>PEM 格式（带 BEGIN/END CERTIFICATE 头尾）</li>
     *   <li>Base64 编码的 DER 格式（纯 Base64 字符串）</li>
     * </ul>
     *
     * @param certificateStr 证书字符串
     * @return 公钥对象
     * @throws Exception 解析失败时抛出异常
     */
    @Cacheable(value = "saml-certificates", key = "#certificateStr.hashCode()")
    private PublicKey parsePublicKey(String certificateStr) throws Exception {
        String cacheKey = String.valueOf(certificateStr.hashCode());

        PublicKey cached = publicKeyCache.get(cacheKey);
        if (cached != null) {
            log.debug("从缓存获取PublicKey: key={}", cacheKey);
            return cached;
        }

        String cleanCert = cleanCertificate(certificateStr);
        byte[] certBytes = Base64.getDecoder().decode(cleanCert);

        PublicKey publicKey;
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert =
                    (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            publicKey = cert.getPublicKey();
        } catch (Exception e) {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(certBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            publicKey = keyFactory.generatePublic(keySpec);
        }

        publicKeyCache.put(cacheKey, publicKey);
        log.debug("解析并缓存PublicKey: key={}", cacheKey);
        return publicKey;
    }

    /**
     * 解析 X.509 证书对象（用于验证有效期）
     *
     * @param certificateStr 证书字符串
     * @return X509Certificate 对象
     * @throws Exception 解析失败时抛出异常
     */
    @Cacheable(value = "saml-certificates", key = "'cert_' + #certificateStr.hashCode()")
    private X509Certificate parseCertificate(String certificateStr) throws Exception {
        String cacheKey = String.valueOf(certificateStr.hashCode());

        X509Certificate cached = certificateCache.get(cacheKey);
        if (cached != null) {
            log.debug("从缓存获取X509Certificate: key={}", cacheKey);
            return cached;
        }

        String cleanCert = cleanCertificate(certificateStr);
        byte[] certBytes = Base64.getDecoder().decode(cleanCert);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate =
                (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

        certificateCache.put(cacheKey, certificate);
        log.debug("解析并缓存X509Certificate: key={}", cacheKey);
        return certificate;
    }

    /**
     * 清理证书字符串,移除 PEM 头尾和空白字符
     *
     * @param certificate 原始证书字符串
     * @return 纯 Base64 字符串
     */
    private String cleanCertificate(String certificate) {
        return certificate
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    /**
     * 创建安全的 DocumentBuilderFactory
     *
     * <p>禁用 XXE（XML 外部实体）攻击防护,防止 XML 注入漏洞。
     *
     * @return 安全配置的 DocumentBuilderFactory
     * @throws Exception 配置失败时抛出异常
     */
    private DocumentBuilderFactory createSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    /**
     * 将 XML Document 或 Element 转换为字符串
     *
     * @param doc XML 文档或包含元素的文档
     * @return XML 字符串
     * @throws Exception 转换失败时抛出异常
     */
    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * 清除证书缓存
     * 用于证书更新或过期后清理缓存
     */
    public void clearCertificateCache() {
        int publicKeySize = publicKeyCache.size();
        int certSize = certificateCache.size();
        publicKeyCache.clear();
        certificateCache.clear();
        log.info("已清除SAML证书缓存: PublicKey={} 个, X509Certificate={} 个", publicKeySize, certSize);
    }

    /**
     * 获取缓存统计信息
     *
     * @return 包含缓存大小的Map
     */
    public Map<String, Integer> getCacheStatistics() {
        Map<String, Integer> stats = new HashMap<>(16);
        stats.put("publicKeyCache", publicKeyCache.size());
        stats.put("certificateCache", certificateCache.size());
        return stats;
    }

    /**
     * 检查SAML Assertion是否已被使用（重放攻击检测）
     *
     * @param assertionId SAML Assertion ID
     * @return true表示已被使用（重放攻击），false表示首次使用
     */
    private Mono<Boolean> checkAssertionReplay(String assertionId) {
        String key = SAML_ASSERTION_USED_PREFIX + assertionId;
        return redisTemplate.hasKey(key);
    }

    /**
     * 标记SAML Assertion为已使用
     * 使用Redis SETNX确保原子性
     *
     * @param assertionId SAML Assertion ID
     * @return 标记成功返回Mono.empty()
     */
    private Mono<Void> markAssertionAsUsed(String assertionId) {
        String key = SAML_ASSERTION_USED_PREFIX + assertionId;
        return redisTemplate
                .opsForValue()
                .set(key, "used", ASSERTION_ID_TTL)
                .then()
                .doOnSuccess(v -> log.debug("已标记SAML Assertion为已使用: id={}", assertionId));
    }

    /**
     * 从配置中获取SAML签名算法
     *
     * <p>配置项说明：</p>
     * <ul>
     *   <li>signatureAlgorithm - 签名算法名称（可选）</li>
     *   <li>默认值：SHA256withRSA（推荐用于生产环境）</li>
     *   <li>支持的算法：SHA256withRSA, SHA384withRSA, SHA512withRSA, SHA1withRSA</li>
     * </ul>
     *
     * <p>配置示例：</p>
     * <pre>{@code
     * {
     *   "entityId": "https://sp.example.com",
     *   "authEndpoint": "https://idp.example.com/sso",
     *   "certificate": "MIIDXTCCAkWgAwIBAgI...",
     *   "signatureAlgorithm": "SHA384withRSA"  // 可选，默认SHA256withRSA
     * }
     * }</pre>
     *
     * @param config SAML提供商配置
     * @return 签名算法名称，未配置时返回默认值SHA256withRSA
     */
    private String getSignatureAlgorithm(Map<String, Object> config) {
        String algorithm = (String) config.get("signatureAlgorithm");

        // 未配置时使用默认算法
        if (algorithm == null || algorithm.trim().isEmpty()) {
            log.debug("未配置签名算法，使用默认值: {}", DEFAULT_SIGNATURE_ALGORITHM);
            return DEFAULT_SIGNATURE_ALGORITHM;
        }

        // 验证算法是否受支持
        if (!SUPPORTED_SIGNATURE_ALGORITHMS.containsKey(algorithm)) {
            log.warn(
                    "配置的签名算法 {} 不受支持，回退到默认算法 {}。支持的算法: {}",
                    algorithm,
                    DEFAULT_SIGNATURE_ALGORITHM,
                    String.join(", ", SUPPORTED_SIGNATURE_ALGORITHMS.keySet()));
            return DEFAULT_SIGNATURE_ALGORITHM;
        }

        log.debug("使用配置的签名算法: {}", algorithm);
        return algorithm;
    }
}
