package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.config.OAuth2Properties;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * OAuth2 授权服务器元数据（RFC 8414）端点服务。
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

    /**
     * 构造服务并加载或生成RSA签名密钥对
     *
     * <p>优先从配置加载Base64编码的密钥对并做签名自检；basic profile下未配置时现场生成。
     *
     * @param properties OAuth2配置属性
     * @param environment Spring环境
     * @throws IllegalStateException RSA密钥配置无效时抛出
     */
    public OAuth2MetadataService(OAuth2Properties properties, Environment environment) {
        this.properties = properties;
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

    public Map<String, Object> jwks() {
        return Map.of(
                "keys",
                List.of(Map.of(
                        "kty",
                        "RSA",
                        "use",
                        "sig",
                        "alg",
                        "RS256",
                        "kid",
                        properties.getKeyId(),
                        "n",
                        unsigned(publicKey.getModulus()),
                        "e",
                        unsigned(publicKey.getPublicExponent()))));
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
