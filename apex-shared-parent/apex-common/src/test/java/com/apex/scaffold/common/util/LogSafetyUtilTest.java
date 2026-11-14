package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogSafetyUtilTest {
    @Test
    void removesSecretsAndHonorsExclusions() throws Exception {
        String json = LogSafetyUtil.json(
                Map.of(
                        "access_token",
                        "token-value",
                        "nested",
                        Map.of("password", "password-value", "custom", "custom-value")),
                2000,
                "custom");
        assertThat(json)
                .doesNotContain("token-value", "password-value", "custom-value")
                .contains("***");
        assertThat(new ObjectMapper().readTree(json)).isNotNull();
    }

    @Test
    void boundsHugeAndCyclicInputs() throws Exception {
        Map<String, Object> cycle = new LinkedHashMap<>();
        cycle.put("self", cycle);
        cycle.put("large", "x".repeat(100000));
        String json = LogSafetyUtil.json(cycle, 128);
        assertThat(json.length()).isLessThanOrEqualTo(128);
        assertThat(new ObjectMapper().readTree(json)).isNotNull();
        assertThat(LogSafetyUtil.json(cycle, 200000).length()).isLessThanOrEqualTo(8192);
    }

    @Test
    void boundsAndCleansMetadata() {
        assertThat(LogSafetyUtil.text("a\nb\rc", 4)).isEqualTo("a b ");
        assertThat(LogSafetyUtil.uri("/login?password=secret#token", 100)).isEqualTo("/login");
        assertThat(LogSafetyUtil.text(null, 100)).isEmpty();
        assertThat(LogSafetyUtil.text("value", -1)).isEmpty();
    }

    @Test
    void omitsUnlabelledStrings() {
        assertThat(LogSafetyUtil.json("bearer-secret", 128)).doesNotContain("bearer-secret");
        assertThat(LogSafetyUtil.json(java.util.List.of("password-value"), 128)).doesNotContain("password-value");
        assertThat(LogSafetyUtil.json(new String[] {"token-value"}, 128)).doesNotContain("token-value");
    }

    @Test
    void preservesSharedReferencesWithoutExpandingCycles() throws Exception {
        Map<String, Object> shared = Map.of("count", 7);
        String json = LogSafetyUtil.json(Map.of("first", shared, "second", shared), 512);
        var tree = new ObjectMapper().readTree(json);
        assertThat(tree.path("first").path("count").asInt()).isEqualTo(7);
        assertThat(tree.path("second").path("count").asInt()).isEqualTo(7);
    }

    @Test
    void omitsNonFiniteNumbersWithoutLosingOtherFields() throws Exception {
        var tree = new ObjectMapper()
                .readTree(LogSafetyUtil.json(
                        Map.of("value", Double.NaN, "infinite", Float.POSITIVE_INFINITY, "count", 7), 512));
        assertThat(tree.path("count").asInt()).isEqualTo(7);
        assertThat(tree.path("value").isNull()).isTrue();
        assertThat(tree.path("infinite").isNull()).isTrue();
    }
}
