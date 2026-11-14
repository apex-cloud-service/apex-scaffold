package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.config.OAuth2Properties;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * OAuth2 授权服务器元数据（RFC 8414）端点服务。
 *
 * <p>统一认证收口（A2）：JWKS 端点聚合输出「Redis 轮换密钥（活跃 + 宽限期，经
 * {@link JwtKeyMaterialService}）+ 配置注入静态密钥（过渡期回退，可关）」，
 * 是登录令牌与 OAuth2 令牌唯一的公钥分发点。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.0
 */
@Service
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2MetadataService {
    private static final String BASIC = "basic";

    private static final int RSA_KEY_BITS = 2048;
    private final OAuth2Properties properties;
    private final RSAPublicKey publicKey;
    private final java.security.PrivateKey privateKey;
    private final JwtKeyMaterialService keyMaterialService;

    /**
     * 构造服务并加载或生成RSA签名密钥对
     *
     * <p>优先从配置加载Base64编码的密钥对并做签名自检；basic profile下未配置时现场生成。
     *
     * @param properties OAuth2配置属性
     * @param environment Spring环境
     * @param keyMaterialService 统一密钥材料服务（轮换 RSA，聚合 JWKS 的数据源）
     * @throws IllegalStateException RSA密钥配置无效时抛出
     */
    public OAuth2MetadataService(
            OAuth2Properties properties, Environment environment, JwtKeyMaterialService keyMaterialService) {
        this.properties = properties;
        this.keyMaterialService = keyMaterialService;
        try {
            if (properties.getPrivateKey() != null && properties.getPublicKey() != null) {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                publicKey = (RSAPublicKey) factory.generatePublic(
                        new X509EncodedKeySpec(Base64.getDecoder().decode(properties.getPublicKey())));
                privateKey = factory.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(properties.getPrivateKey())));
                java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
                byte[] probe = properties.getKeyId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                signature.initSign(privateKey);
                signature.update(probe);
                byte[] signed = signature.sign();
                signature.initVerify(publicKey);
                signature.update(probe);
                if (!signature.verify(signed)) {
                    throw new IllegalStateException("OAuth2 signing keys do not match");
                }
            } else {
                if (!environment.matchesProfiles(BASIC)
                        || properties.getPrivateKey() != null
                        || properties.getPublicKey() != null) {
                    throw new IllegalStateException("OAuth2 RSA keys must be configured outside basic profile");
                }
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(RSA_KEY_BITS);
                KeyPair pair = generator.generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid OAuth2 RSA signing configuration", exception);
        }
    }

    /**
     * 构造 RFC 8414 授权服务器元数据，端点地址以配置的 issuer 为前缀。
     *
     * @return 端点地址及支持的授权类型、PKCE 和客户端认证方式
     */
    public Map<String, Object> discovery() {
        String issuer = properties.getIssuer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
        metadata.put("token_endpoint", issuer + "/oauth2/token");
        metadata.put("revocation_endpoint", issuer + "/oauth2/revoke");
        metadata.put("introspection_endpoint", issuer + "/oauth2/introspect");
        metadata.put("jwks_uri", issuer + "/.well-known/jwks.json");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put(
                "grant_types_supported",
                List.of("authorization_code", "password", "client_credentials", "refresh_token"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        return metadata;
    }

    /**
     * 导出聚合 JWK Set：Redis 轮换密钥（活跃 + 宽限期）在前，配置注入静态密钥
     * （{@code legacyKeyInJwks=true} 且已配置时）追加在后；仅含 RSA 公钥，不暴露私钥。
     *
     * <p>统一认证收口（A2）：本端点是登录令牌与 OAuth2 令牌唯一的公钥分发点，
     * 网关与下游服务经共享 JWKS 拉取客户端消费。
     *
     * @return 含全部可用 RS256 签名公钥的 keys 集合，模数和指数采用无填充 Base64URL 编码
     */
    public Mono<Map<String, Object>> jwks() {
        return keyMaterialService.availablePublicKeys().defaultIfEmpty(Map.of()).map(rotating -> {
            List<Map<String, Object>> keys = new ArrayList<>();
            if (properties.isLegacyKeyInJwks()) {
                keys.add(jwk(properties.getKeyId(), publicKey));
            }
            rotating.forEach((kid, key) -> {
                if (key instanceof RSAPublicKey rsa) {
                    keys.add(jwk(kid, rsa));
                }
            });
            return Map.<String, Object>of("keys", List.copyOf(keys));
        });
    }

    /**
     * 投影单个 RSA 公钥为 JWK 条目。
     *
     * @param kid 密钥ID
     * @param key RSA 公钥
     * @return JWK 条目（kty/use/alg/kid/n/e）
     */
    private Map<String, Object> jwk(String kid, RSAPublicKey key) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("kty", "RSA");
        entry.put("use", "sig");
        entry.put("alg", "RS256");
        entry.put("kid", kid);
        entry.put("n", unsigned(key.getModulus()));
        entry.put("e", unsigned(key.getPublicExponent()));
        return entry;
    }

    /**
     * 获取令牌签名私钥
     *
     * @return RSA签名私钥
     */
    public java.security.PrivateKey signingKey() {
        return privateKey;
    }

    /**
     * 获取令牌验证公钥
     *
     * @return RSA验证公钥
     */
    public RSAPublicKey verificationKey() {
        return publicKey;
    }

    private String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
