package com.apex.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * Structural contracts for {@code logback-spring.xml}.
 *
 * @author Apex Team
 */
class LogbackConfigurationTest {

    private static Document configuration;

    /** Loads the logging configuration without starting a Spring application. */
    @BeforeAll
    static void loadConfiguration() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        configuration = factory.newDocumentBuilder()
                .parse(Path.of("src/main/resources/logback-spring.xml").toFile());
    }

    /**
     * sentinel-logging-slf4j 桥接器将 Sentinel 的 RecordLog/CommandCenterLog 重定向到 SLF4J，
     * 使用扁平 logger 名（sentinelRecordLogger、sentinelCommandCenterLogger）。
     * Logback 必须为这些 logger 配置 SENTINEL_FILE appender，使记录日志落入
     * {@code logs/sentinel/sentinel.log}，而非落回根 logger 混入主应用日志。
     */
    @Test
    void shouldRouteSentinelSlf4jBridgeToDedicatedFile() throws Exception {
        assertThat(count("//appender[@name='SENTINEL_FILE']")).isEqualTo(1);
        assertThat(count("//logger[@name='sentinelRecordLogger']/appender-ref[@ref='SENTINEL_FILE']"))
                .isGreaterThan(0);
        assertThat(count("//logger[@name='sentinelCommandCenterLogger']/appender-ref[@ref='SENTINEL_FILE']"))
                .isGreaterThan(0);
    }

    /** P6Spy is enabled only for development, so its file appender must have the same scope. */
    @Test
    void shouldCreateSqlFileAppenderOnlyForDevelopment() throws Exception {
        assertThat(count("/configuration/appender[@name='SQL_FILE']")).isZero();
        assertThat(count("/configuration/springProfile[@name='dev']/appender[@name='SQL_FILE']"))
                .isEqualTo(1);
    }

    /** Production component events must be routed to the component files that are created. */
    @Test
    void shouldRouteProductionComponentLogsToTheirFiles() throws Exception {
        assertThat(count("/configuration/springProfile[@name='prod']/logger[@name='com.alibaba.nacos']"
                        + "/appender-ref[@ref='NACOS_FILE']"))
                .isEqualTo(1);
        assertThat(count("/configuration/springProfile[@name='prod']/logger[@name='org.apache.rocketmq']"
                        + "/appender-ref[@ref='ROCKETMQ_FILE']"))
                .isEqualTo(1);
    }

    /**
     * Counts nodes matching an XPath expression.
     *
     * @param expression XPath expression
     * @return matching node count
     */
    private static int count(String expression) throws Exception {
        Double result = (Double) XPathFactory.newInstance()
                .newXPath()
                .evaluate("count(" + expression + ")", configuration, XPathConstants.NUMBER);
        return result.intValue();
    }
}
