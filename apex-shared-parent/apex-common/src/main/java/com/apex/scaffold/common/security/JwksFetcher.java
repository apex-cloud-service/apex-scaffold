package com.apex.scaffold.common.security;

import com.apex.scaffold.common.config.JwksFetchProperties;
import com.apex.scaffold.common.util.JsonUtil;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 共享 JWKS 拉取客户端：周期性从认证中心 {@code /.well-known/jwks.json} 拉取公钥集合并维护本地快照。
 *
 * <p>统一认证收口（A2）的关键闭环件：认证中心登录密钥 30 天轮换 + 7 天宽限后，
 * 网关与各下游服务无需人工同步 Nacos，最迟在一个刷新周期内自动获得新公钥。
 * 行为约定：
 * <ul>
 *   <li>启动时立即拉取一次，此后按 {@code apex.jwks.refresh-interval-seconds}（默认 300）周期刷新；</li>
 *   <li>拉取失败或响应非法时保留上一次快照并告警，不影响在用公钥；</li>
 *   <li>{@code apex.jwks.remote-url} 为空时组件处于禁用态（快照恒为空），
 *       验签方回退到静态配置公钥兜底；</li>
 *   <li>拉取为非阻塞 WebClient 调用，定时器运行于 Reactor 并行调度器，不占用阻塞池。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class JwksFetcher implements InitializingBean, DisposableBean {

    private final JwksFetchProperties properties;
    private final WebClient webClient;
    private final AtomicReference<Map<String, PublicKey>> snapshot = new AtomicReference<>(Map.of());
    private volatile Disposable refreshDisposable;

    /**
     * 构造拉取客户端。
     *
     * @param properties JWKS 拉取配置（{@code apex.jwks.*}）
     */
    public JwksFetcher(JwksFetchProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled()) {
            log.info("JWKS 拉取未配置远程地址（apex.jwks.remote-url 为空），使用静态公钥兜底");
            return;
        }
        Duration interval = Duration.ofSeconds(Math.max(30, properties.getRefreshIntervalSeconds()));
        refreshDisposable = fetchAndStore()
                .onErrorResume(e -> {
                    log.warn("JWKS 初始拉取失败，等待下个周期重试: url={}, reason={}", resolveUrl(), e.getMessage());
                    return Mono.empty();
                })
                .thenMany(Flux.interval(interval, interval))
                .concatMap(ignore -> fetchAndStore().onErrorResume(e -> {
                    log.warn("JWKS 周期拉取失败，保留上一次快照: url={}, reason={}", resolveUrl(), e.getMessage());
                    return Mono.empty();
                }))
                .subscribe();
        log.info("JWKS 拉取已启动: url={}, interval={}s", resolveUrl(), interval.getSeconds());
    }

    @Override
    public void destroy() {
        Disposable disposable = refreshDisposable;
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    /**
     * 是否已启用远程拉取。
     *
     * @return {@code apex.jwks.remote-url} 非空时为 {@code true}
     */
    public boolean enabled() {
        return properties.getRemoteUrl() != null && !properties.getRemoteUrl().isBlank();
    }

    /**
     * 当前公钥快照（kid → RSA 公钥）；禁用态或尚未拉取成功时为空映射。
     *
     * @return 不可变公钥快照
     */
    public Map<String, PublicKey> snapshot() {
        return snapshot.get();
    }

    /**
     * 解析 JWKS 响应为公钥映射（仅取 kty=RSA 条目，非法条目跳过并告警）。
     *
     * <p>静态方法：网关的远程 JWKS 合并与本拉取客户端共用同一解析语义。
     *
     * @param body JWKS JSON 响应体
     * @return kid → RSA 公钥映射
     */
    public static Map<String, PublicKey> parseJwks(String body) {
        Map<?, ?> root = JsonUtil.fromJson(body, Map.class);
        Object keys = root == null ? null : root.get("keys");
        if (!(keys instanceof List<?> list)) {
            throw new IllegalArgumentException("JWKS 响应缺少 keys 数组");
        }
        Map<String, PublicKey> result = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> jwk)) {
                continue;
            }
            if (!"RSA".equals(jwk.get("kty"))) {
                log.debug("跳过非 RSA JWK 条目: kty={}", jwk.get("kty"));
                continue;
            }
            String kid = String.valueOf(jwk.get("kid"));
            String modulus = String.valueOf(jwk.get("n"));
            String exponent = String.valueOf(jwk.get("e"));
            if (kid.isBlank() || "null".equals(kid) || modulus.isBlank() || "null".equals(modulus)) {
                log.warn("跳过不完整的 RSA JWK 条目: kid={}", kid);
                continue;
            }
            try {
                RSAPublicKeySpec spec = new RSAPublicKeySpec(
                        new BigInteger(1, decodeBase64Url(modulus)), new BigInteger(1, decodeBase64Url(exponent)));
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
                result.put(kid, publicKey);
            } catch (Exception e) {
                log.warn("解析 RSA JWK 失败: kid={}, reason={}", kid, e.getMessage());
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 执行一次拉取并更新快照（测试与刷新共用）。
     *
     * @return 本次拉取到的公钥映射
     */
    Mono<Map<String, PublicKey>> fetchAndStore() {
        return fetch(resolveUrl()).doOnNext(keys -> {
            if (keys.isEmpty()) {
                log.warn("JWKS 响应未包含可用 RSA 公钥，保留上一次快照");
                return;
            }
            snapshot.set(keys);
            log.info("JWKS 公钥快照已更新: keys={}", keys.size());
        });
    }

    /**
     * 拉取 JWKS 并解析（可见性供测试注入替身）。
     *
     * @param url JWKS 地址
     * @return 公钥映射
     */
    Mono<Map<String, PublicKey>> fetch(String url) {
        return webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMillis())))
                .map(JwksFetcher::parseJwks);
    }

    /**
     * 解析最终 JWKS 地址：remote-url 为完整地址时直接使用；仅为主机基址时追加 endpoint 路径。
     *
     * @return JWKS 地址
     */
    private String resolveUrl() {
        String remoteUrl = properties.getRemoteUrl();
        String path = java.net.URI.create(remoteUrl).getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            String endpoint = properties.getEndpoint();
            String suffix = endpoint == null ? "" : endpoint;
            return remoteUrl.endsWith("/") && suffix.startsWith("/")
                    ? remoteUrl + suffix.substring(1)
                    : remoteUrl + suffix;
        }
        return remoteUrl;
    }

    private static byte[] decodeBase64Url(String value) {
        String padded = value;
        int remainder = padded.length() % 4;
        if (remainder != 0) {
            padded = padded + "=".repeat(4 - remainder);
        }
        return Base64.getUrlDecoder().decode(padded);
    }
}
