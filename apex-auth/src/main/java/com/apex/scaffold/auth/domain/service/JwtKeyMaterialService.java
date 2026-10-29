package com.apex.scaffold.auth.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JWT 非对称（RSA/RS256）密钥材料服务。
 *
 * <p>微服务迁移 Phase 2.1 的核心组件：将认证中心 Token 签名算法由对称的
 * HS256（共享密钥）升级为非对称的 RS256（私钥签名、公钥验证），实现：
 *
 * <h2>升级动机</h2>
 * <ul>
 *   <li><b>职责分离</b>：私钥仅存于认证中心用于签发；其它服务仅持公钥验签，
 *       无需再分发共享密钥，任一验证方泄露都不影响签发安全。</li>
 *   <li><b>标准互操作</b>：通过 {@code /.well-known/jwks.json} 暴露 JWKS，
 *       网关及各业务服务可按 {@code kid} 拉取公钥完成本地验签。</li>
 *   <li><b>平滑轮换</b>：JWT 头部携带 {@code kid}，新旧密钥可并存验证，
 *       密钥轮换对调用方透明。</li>
 * </ul>
 *
 * <h2>与密钥轮换的关系</h2>
 * <p>本服务不自行管理密钥生命周期，而是复用 {@link JwtKeyRotationService}
 * （Redis-RSA 实现）完成密钥的生成、轮换与宽限期管理，仅负责：
 * <ul>
 *   <li>用“当前活跃私钥”签发 Token，并写入与公钥一致的 {@code kid}；</li>
 *   <li>按 {@code kid} 选择对应公钥，对 Token 进行验签与解析；</li>
 *   <li>为 JWKS 端点提供密钥集合投影。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtKeyMaterialService {

    /**
     * JWT 头部中的密钥标识字段名（RFC 7515 §4.1.4）。
     */
    private static final String HEADER_KEY_ID = "kid";

    /**
     * RSA 密钥轮换与签名服务（Redis-RSA 实现，唯一保留的密钥轮换实现）。
     */
    private final JwtKeyRotationService jwtKeyRotationService;

    /**
     * 用于解码 JWT 头部 JSON 的 Jackson 映射器（线程安全，可直接复用）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用当前活跃私钥签发 RS256 Token。
     *
     * <p>方法签名与 {@code JwtUtil.generateToken(subject, claims, expireTime)} 保持一致，
     * 便于调用方从对称签名平滑切换到非对称签名。
     *
     * @param subject      Token 主题（通常为用户ID）
     * @param claims       自定义声明（可含 jti 等注册声明）
     * @param expireMillis 有效期（毫秒）
     * @return 已签名的紧凑型 JWT 字符串
     */
    public Mono<String> sign(String subject, Map<String, Object> claims, long expireMillis) {
        return jwtKeyRotationService
                .getCurrentKeyId()
                .zipWith(jwtKeyRotationService.getCurrentPrivateKey())
                .map(tuple -> buildSignedToken(tuple.getT1(), tuple.getT2(), subject, claims, expireMillis));
    }

    /**
     * 按 {@code kid} 验签并解析 Token。
     *
     * <p>先从 Token 头部读取 {@code kid}，再加载对应公钥验签；
     * 若未携带 {@code kid}（历史 Token）则回退到当前活跃公钥尝试验签，
     * 兼容密钥轮换宽限期内的旧 Token。
     *
     * @param token 紧凑型 JWT 字符串
     * @return 解析后的声明集合
     */
    public Mono<Claims> parse(String token) {
        return Mono.defer(() -> {
            String keyId = extractKeyId(token);
            if (keyId != null && !keyId.isBlank()) {
                return jwtKeyRotationService
                        .getPublicKey(keyId)
                        .switchIfEmpty(Mono.error(new JwtException("未找到 kid 对应的公钥: " + keyId)))
                        .map(publicKey -> parseWithKey(token, publicKey));
            }
            return jwtKeyRotationService
                    .getCurrentPublicKey()
                    .switchIfEmpty(Mono.error(new JwtException("JWT 密钥未初始化")))
                    .map(publicKey -> parseWithKey(token, publicKey));
        });
    }

    /**
     * 获取所有可用于验签的公钥（当前活跃 + 宽限期内旧密钥），用于 JWKS 投影。
     *
     * @return 密钥ID与公钥的映射（有序，当前活跃密钥优先）
     */
    public Mono<Map<String, PublicKey>> availablePublicKeys() {
        return jwtKeyRotationService
                .getAllAvailableKeyIds()
                .flatMapMany(Flux::fromIterable)
                .concatMap(keyId ->
                        jwtKeyRotationService.getPublicKey(keyId).map(publicKey -> Map.entry(keyId, publicKey)))
                .collectList()
                .map(entries -> {
                    Map<String, PublicKey> result = new LinkedHashMap<>();
                    entries.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
                    return result;
                });
    }

    /**
     * 从 Token 头部安全提取 {@code kid}（不验签，仅读取）。
     *
     * <p>紧凑型 JWT 的第一段即为 Base64URL 编码的头部 JSON，直接解码读取
     * {@code kid}。此方式不涉及任何验签，天然规避“先有公钥才能读 kid”的
     * 循环依赖，且对非法 Token 保持健壮（解析失败返回 {@code null} 交由
     * 上层回退到当前活跃公钥）。
     *
     * @param token 紧凑型 JWT 字符串
     * @return kid 值；无法解析时返回 {@code null}
     */
    private String extractKeyId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int firstDot = token.indexOf('.');
        if (firstDot <= 0) {
            return null;
        }
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(token.substring(0, firstDot));
            JsonNode header = objectMapper.readTree(headerBytes);
            JsonNode kid = header.get(HEADER_KEY_ID);
            return kid == null || kid.isNull() ? null : kid.asText();
        } catch (IllegalArgumentException | IOException exception) {
            log.debug("读取 JWT kid 失败，将回退当前活跃公钥验证: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 使用指定公钥验签并解析。
     *
     * @param token     紧凑型 JWT 字符串
     * @param publicKey RSA 公钥
     * @return 解析后的声明集合
     * @throws JwtException 验签失败或 Token 非法
     */
    private Claims parseWithKey(String token, PublicKey publicKey) {
        Jws<Claims> jws = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
        return jws.getPayload();
    }

    /**
     * 构建并签名 Token，写入 {@code kid} 与标准时间声明。
     *
     * @param keyId       密钥ID
     * @param privateKey  RSA 私钥
     * @param subject     Token 主题
     * @param claims      自定义声明
     * @param expireMillis 有效期（毫秒）
     * @return 紧凑型 JWT 字符串
     */
    private String buildSignedToken(
            String keyId, PrivateKey privateKey, String subject, Map<String, Object> claims, long expireMillis) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .setHeaderParam(HEADER_KEY_ID, keyId)
                .subject(subject)
                // 注意：必须使用 claims(claims) 的“追加”语义（与旧版 JwtUtil.generateToken 一致）；
                // 若改用 setClaims(claims) 会整体替换声明集合，从而覆盖上面设置的 subject。
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expireMillis)));
        return builder.signWith(privateKey, SignatureAlgorithm.RS256).compact();
    }
}
