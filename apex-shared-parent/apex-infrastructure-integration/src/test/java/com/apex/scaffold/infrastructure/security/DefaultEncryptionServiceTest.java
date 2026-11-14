package com.apex.scaffold.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultEncryptionServiceTest {
    @Test
    void shouldDecryptAcrossInstancesWithStableKey() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        DefaultEncryptionService first = new DefaultEncryptionService(key);
        DefaultEncryptionService second = new DefaultEncryptionService(key);
        String encrypted = first.encrypt("database-secret").block();
        assertThat(first.encrypt("database-secret").block()).isNotEqualTo(encrypted);
        StepVerifier.create(second.decrypt(encrypted))
                .expectNext("database-secret")
                .verifyComplete();
        byte[] other = new byte[32];
        other[0] = 1;
        StepVerifier.create(new DefaultEncryptionService(Base64.getEncoder().encodeToString(other)).decrypt(encrypted))
                .expectError()
                .verify();
    }

    @Test
    void shouldRejectInvalidKeys() {
        for (String key : new String[] {null, "", " ", "invalid-secret!"}) {
            assertThatThrownBy(() -> new DefaultEncryptionService(key)).isInstanceOf(IllegalArgumentException.class);
        }
        for (int length : new int[] {16, 24, 31, 33}) {
            assertThatThrownBy(() ->
                            new DefaultEncryptionService(Base64.getEncoder().encodeToString(new byte[length])))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
