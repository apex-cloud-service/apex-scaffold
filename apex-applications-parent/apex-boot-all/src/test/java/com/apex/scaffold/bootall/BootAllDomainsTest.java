package com.apex.scaffold.bootall;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 组合部署域解析与互斥属性测试。
 *
 * <p>验证：未声明域时 fail-closed 语义由调用方处理、重复域去重、未知域报错，
 * 以及为某域生成的属性会显式关闭其余域（避免自动配置全量装配）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("组合部署域解析")
class BootAllDomainsTest {

    @Test
    @DisplayName("空白配置返回全部域")
    void allDomainsWhenBlank() {
        assertThat(BootAllDomains.resolve("")).hasSize(6);
        assertThat(BootAllDomains.resolve(null)).hasSize(6);
    }

    @Test
    @DisplayName("重复与大小写混写的域应去重")
    void deduplicatesDomains() {
        List<BootAllDomains.Domain> domains = BootAllDomains.resolve("file, FILE, office");

        assertThat(domains).containsExactly(BootAllDomains.Domain.FILE, BootAllDomains.Domain.OFFICE);
    }

    @Test
    @DisplayName("未知域立即失败，不静默忽略")
    void rejectsUnknownDomain() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> BootAllDomains.resolve("file,unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("为单域生成属性时应关闭其他所有域")
    void disablesOtherDomains() {
        Map<String, Object> properties = BootAllDomains.propertiesFor(BootAllDomains.Domain.FILE);

        assertThat(properties.get("apex.component.file.enabled")).isEqualTo(true);
        for (BootAllDomains.Domain other : BootAllDomains.Domain.values()) {
            if (other != BootAllDomains.Domain.FILE) {
                assertThat(properties.get("apex.component." + other.code() + ".enabled"))
                        .as("未启用域必须显式关闭：" + other.code())
                        .isEqualTo(false);
            }
        }
        assertThat(properties.get("server.port")).isEqualTo("8085");
        assertThat(properties.get("apex.component.integration.device.enabled")).isEqualTo(false);
    }
}
