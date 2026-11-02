package com.apex.scaffold.integrationservice.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 集成中心 i18n 资源一致性测试（方案 M5 交付物补齐）。
 *
 * <p>断言四语言资源文件的 key 集合完全一致，且高频错误码均已覆盖；
 * 防止新增翻译遗漏某个语言。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("集成中心 i18n 一致性测试")
class IntegrationI18nConsistencyTest {

    /** 四种语言。 */
    private static final String[] LOCALES = {"zh_CN", "zh_TW", "en_US", "th_TH"};

    /** 必须覆盖的高频错误码（Handler 层面向用户）。 */
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "integration.connector_not_found",
            "integration.credential_not_found",
            "integration.webhook_signature_invalid",
            "integration.webhook_duplicate",
            "iot.device_not_found",
            "iot.device_auth_failed",
            "iot.command_timeout",
            "iot.rule_not_found");

    @Test
    @DisplayName("四语言 key 集合一致且值非空")
    void allLocalesShareSameKeys() throws Exception {
        Set<String> baseline = load("zh_CN").stringPropertyNames();
        assertThat(baseline).isNotEmpty();
        for (String locale : LOCALES) {
            Properties properties = load(locale);
            assertThat(properties.stringPropertyNames())
                    .as("%s key 集合应与 zh_CN 一致", locale)
                    .isEqualTo(baseline);
            for (String key : baseline) {
                assertThat(properties.getProperty(key))
                        .as("%s.%s 不应为空", locale, key)
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("高频错误码全部已翻译")
    void requiredKeysPresent() throws Exception {
        for (String locale : LOCALES) {
            Properties properties = load(locale);
            assertThat(properties.stringPropertyNames())
                    .as("%s 应覆盖全部高频错误码", locale)
                    .containsAll(REQUIRED_KEYS);
        }
    }

    @Test
    @DisplayName("翻译内容不出现占位符污染（除带参数的消息）")
    void noUntranslatedPlaceholders() throws Exception {
        Properties zh = load("zh_CN");
        Properties en = load("en_US");
        for (String key : new LinkedHashSet<>(zh.stringPropertyNames())) {
            assertThat(en.getProperty(key)).as("en_US.%s 应已翻译而非照抄中文", key).isNotEqualTo(zh.getProperty(key));
        }
    }

    private Properties load(String locale) throws Exception {
        String path = "i18n/messages_" + locale + ".properties";
        Properties properties = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("资源文件应存在: " + path).isNotNull();
            properties.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }
}
