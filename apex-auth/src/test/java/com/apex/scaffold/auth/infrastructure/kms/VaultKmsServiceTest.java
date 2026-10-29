package com.apex.scaffold.auth.infrastructure.kms;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Vault KMS服务测试")
class VaultKmsServiceTest {

    private VaultKmsService vaultKmsService;
    private VaultKmsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new VaultKmsProperties();
        properties.setAddress("http://localhost:8200");
        properties.setToken("test-token");
        properties.setNamespace("admin");
        properties.setTransitEnginePath("transit");
        properties.setKeyName("test-key");

        vaultKmsService = new VaultKmsService(properties);
    }

    @Test
    void propertiesDoNotExposeToken() {
        assertThat(properties.toString()).doesNotContain("test-token");
    }

    @Test
    void failuresDoNotExposeRemoteCredentials() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(VaultKmsService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        RuntimeException failure = new IllegalStateException("vault-token-private-secret");
        org.springframework.web.reactive.function.client.WebClient client =
                org.springframework.web.reactive.function.client.WebClient.builder()
                        .exchangeFunction(request -> reactor.core.publisher.Mono.error(failure))
                        .build();
        org.springframework.test.util.ReflectionTestUtils.setField(vaultKmsService, "webClient", client);
        try {
            reactor.test.StepVerifier.create(vaultKmsService.encrypt("private-secret", null))
                    .expectError()
                    .verify();
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("private-secret");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("VaultKmsService初始化 - 验证属性设置")
    void testVaultKmsServiceInitialization() {
        assertThat(vaultKmsService).isNotNull();
    }

    @Test
    @DisplayName("VaultKmsProperties - 属性设置和获取")
    void testVaultKmsProperties() {
        VaultKmsProperties props = new VaultKmsProperties();
        props.setAddress("http://vault:8200");
        props.setToken("my-token");
        props.setNamespace("my-namespace");
        props.setTransitEnginePath("my-transit");
        props.setKeyName("my-key");

        assertThat(props.getAddress()).isEqualTo("http://vault:8200");
        assertThat(props.getToken()).isEqualTo("my-token");
        assertThat(props.getNamespace()).isEqualTo("my-namespace");
        assertThat(props.getTransitEnginePath()).isEqualTo("my-transit");
        assertThat(props.getKeyName()).isEqualTo("my-key");
    }

    @Test
    @DisplayName("VaultKmsProperties - 默认值验证")
    void testVaultKmsPropertiesDefaults() {
        VaultKmsProperties props = new VaultKmsProperties();
        assertThat(props.getAddress()).isEqualTo("http://localhost:8200");
        assertThat(props.getToken()).isNull();
        assertThat(props.getNamespace()).isEqualTo("");
        assertThat(props.getTransitEnginePath()).isEqualTo("transit");
        assertThat(props.getKeyName()).isEqualTo("apex-encryption-key");
    }

    @Test
    @DisplayName("VaultKmsProperties - 完整属性链测试")
    void testVaultKmsPropertiesChaining() {
        VaultKmsProperties props = new VaultKmsProperties();
        props.setAddress("http://test:8200");
        props.setToken("token123");
        props.setNamespace("ns1");
        props.setTransitEnginePath("transit-engine");
        props.setKeyName("key-name");

        assertThat(props).isNotNull();
        assertThat(props.getAddress()).contains("test");
        assertThat(props.getToken()).contains("token");
        assertThat(props.getNamespace()).isEqualTo("ns1");
        assertThat(props.getTransitEnginePath()).isEqualTo("transit-engine");
        assertThat(props.getKeyName()).isEqualTo("key-name");
    }

    @Test
    @DisplayName("VaultKmsProperties - 空字符串处理")
    void testVaultKmsPropertiesEmptyStrings() {
        VaultKmsProperties props = new VaultKmsProperties();
        props.setAddress("");
        props.setToken("");
        props.setNamespace("");
        props.setTransitEnginePath("");
        props.setKeyName("");

        assertThat(props.getAddress()).isEmpty();
        assertThat(props.getToken()).isEmpty();
        assertThat(props.getNamespace()).isEmpty();
        assertThat(props.getTransitEnginePath()).isEmpty();
        assertThat(props.getKeyName()).isEmpty();
    }

    @Test
    @DisplayName("AwsKmsProperties - 属性设置和获取")
    void testAwsKmsProperties() {
        AwsKmsProperties props = new AwsKmsProperties();
        props.setRegion("us-east-1");
        props.setMasterKeyId("arn:aws:kms:us-east-1:123456789012:key/test");

        assertThat(props.getRegion()).isEqualTo("us-east-1");
        assertThat(props.getMasterKeyId()).contains("test");
    }

    @Test
    @DisplayName("AwsKmsProperties - 默认值验证")
    void testAwsKmsPropertiesDefaults() {
        AwsKmsProperties props = new AwsKmsProperties();
        assertThat(props.getRegion()).isEqualTo("us-east-1");
        assertThat(props.getMasterKeyId()).isNull();
    }

    @Test
    @DisplayName("AwsKmsProperties - ARN格式验证")
    void testAwsKmsPropertiesArnFormat() {
        AwsKmsProperties props = new AwsKmsProperties();
        String arn = "arn:aws:kms:ap-southeast-1:999999999999:key/abc-def-123";
        props.setMasterKeyId(arn);

        assertThat(props.getMasterKeyId()).startsWith("arn:aws:kms:");
        assertThat(props.getMasterKeyId()).contains("ap-southeast-1");
        assertThat(props.getMasterKeyId()).contains("key/");
    }

    @Test
    @DisplayName("AwsKmsProperties - Region枚举值验证")
    void testAwsKmsPropertiesRegionValues() {
        AwsKmsProperties props = new AwsKmsProperties();
        String[] regions = {"us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1"};

        for (String region : regions) {
            props.setRegion(region);
            assertThat(props.getRegion()).isEqualTo(region);
        }
    }

    @Test
    @DisplayName("KeyManagementService - DataKey接口验证")
    void testDataKeyInterface() {
        KeyManagementService.DataKey dataKey = new TestDataKey("plaintext-key", "encrypted-key");

        assertThat(dataKey.getPlaintextKey()).isEqualTo("plaintext-key");
        assertThat(dataKey.getEncryptedKey()).isEqualTo("encrypted-key");
    }

    @Test
    @DisplayName("KeyManagementService - KeyMetadata接口验证")
    void testKeyMetadataInterface() {
        KeyManagementService.KeyMetadata metadata = new TestKeyMetadata(
                "key-id-123",
                "arn:aws:kms:us-east-1:123456789012:key/key-id-123",
                "RSA_2048",
                "Enabled",
                java.time.Instant.now(),
                true);

        assertThat(metadata.getKeyId()).isEqualTo("key-id-123");
        assertThat(metadata.getKeyArn()).contains("key-id-123");
        assertThat(metadata.getAlgorithm()).isEqualTo("RSA_2048");
        assertThat(metadata.getKeyState()).isEqualTo("Enabled");
        assertThat(metadata.getCreationDate()).isNotNull();
        assertThat(metadata.isEnabled()).isTrue();
    }

    @Getter
    static class TestDataKey implements KeyManagementService.DataKey {
        private final String plaintextKey;
        private final String encryptedKey;

        public TestDataKey(String plaintextKey, String encryptedKey) {
            this.plaintextKey = plaintextKey;
            this.encryptedKey = encryptedKey;
        }
    }

    @Getter
    static class TestKeyMetadata implements KeyManagementService.KeyMetadata {
        private final String keyId;
        private final String keyArn;
        private final String algorithm;
        private final String keyState;
        private final java.time.Instant creationDate;
        private final boolean enabled;

        public TestKeyMetadata(
                String keyId,
                String keyArn,
                String algorithm,
                String keyState,
                java.time.Instant creationDate,
                boolean enabled) {
            this.keyId = keyId;
            this.keyArn = keyArn;
            this.algorithm = algorithm;
            this.keyState = keyState;
            this.creationDate = creationDate;
            this.enabled = enabled;
        }
    }
}
