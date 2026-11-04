package com.apex.scaffold.application.openplatform;

import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 存量开放应用迁移（D22/D25）：把 system {@code oauth2_registered_client} 中带
 * {@code openPlatform.*} 元数据的行转换为 auth 单源导入请求。
 *
 * <p>只生成导入清单，不直接跨库写入；secret 以 bcrypt 哈希原样传递，明文永不出现。
 * 指纹由源行稳定字段计算，配合 importId 保证脚本可重复执行而不覆盖管理面新改动。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAppMigrationService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;

    private final OAuth2RegisteredClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    /**
     * 导出全部开放应用导入清单。
     *
     * @return 导入请求列表
     */
    public Mono<List<OpenAppImportRequest>> export() {
        return Flux.range(0, MAX_PAGES)
                .concatMap(page -> clientRepository.findAll(page, PAGE_SIZE).collectList())
                .takeWhile(page -> !page.isEmpty())
                .flatMapIterable(page -> page)
                .collectList()
                .map(this::toImports);
    }

    private List<OpenAppImportRequest> toImports(List<OAuth2RegisteredClientEntity> clients) {
        List<OpenAppImportRequest> imports = new ArrayList<>();
        for (OAuth2RegisteredClientEntity client : clients) {
            ObjectNode settings;
            try {
                JsonNode node =
                        objectMapper.readTree(client.getClientSettings() == null ? "{}" : client.getClientSettings());
                if (!(node instanceof ObjectNode object) || !object.has("openPlatform.appName")) {
                    continue;
                }
                settings = object;
            } catch (Exception malformed) {
                log.warn("跳过元数据不可解析的开放应用: clientId={}", client.getClientId());
                continue;
            }
            String hash = client.getClientSecret();
            if (hash == null || hash.isBlank()) {
                log.warn("跳过缺少密钥哈希的开放应用: clientId={}", client.getClientId());
                continue;
            }
            OpenAppRegistrationRequest registration = OpenAppRegistrationRequest.builder()
                    .clientId(client.getClientId())
                    .appName(text(settings, "openPlatform.appName"))
                    .description(text(settings, "openPlatform.description"))
                    .contact(text(settings, "openPlatform.contact"))
                    .rateLimitTier(text(settings, "openPlatform.rateLimitTier"))
                    .redirectUris(client.getRedirectUris())
                    .scopes(client.getScopes())
                    .enabled(!"DISABLED".equalsIgnoreCase(text(settings, "openPlatform.status")))
                    .subjectUserId(longOrNull(settings, "openPlatform.subjectUserId"))
                    .subjectValidated(Boolean.TRUE)
                    .build();
            OpenAppImportRequest request = new OpenAppImportRequest();
            request.setImportId("legacy:" + client.getClientId());
            request.setSourceFingerprint(fingerprint(client));
            request.setHashAlgorithm("BCRYPT");
            request.setSecretHash(hash);
            request.setRegistration(registration);
            imports.add(request);
        }
        return imports;
    }

    private String fingerprint(OAuth2RegisteredClientEntity client) {
        try {
            String source = String.join(
                    "\n",
                    nullSafe(client.getClientId()),
                    nullSafe(client.getClientSecret()),
                    nullSafe(client.getClientSettings()),
                    String.valueOf(client.getScopes()),
                    String.valueOf(client.getRedirectUris()));
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private String text(ObjectNode settings, String key) {
        JsonNode value = settings.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long longOrNull(ObjectNode settings, String key) {
        JsonNode value = settings.get(key);
        return value != null && value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
