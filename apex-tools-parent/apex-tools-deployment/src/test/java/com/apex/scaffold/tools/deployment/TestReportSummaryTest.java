package com.apex.scaffold.tools.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link TestReportSummary} 测试：模块发现、Surefire/Failsafe 分列、缺失报告、
 * 不一致检测、畸形 XML、特殊路径与安全解析。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("测试报告汇总")
class TestReportSummaryTest {

    @TempDir
    Path temp;

    private void pom(Path directory, String artifactId, String... modules) throws IOException {
        modulePom(directory, "com.apex.scaffold", artifactId, modules);
    }

    private void pomWithParent(Path directory, String parentGroupId, String parentArtifactId, String artifactId)
            throws IOException {
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("pom.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                        + "  <parent><groupId>" + parentGroupId + "</groupId>"
                        + "<artifactId>" + parentArtifactId + "</artifactId>"
                        + "<version>1.0</version><relativePath/></parent>\n"
                        + "  <artifactId>" + artifactId + "</artifactId>\n"
                        + "</project>\n");
    }

    private void modulePom(Path directory, String groupId, String artifactId, String... modules) throws IOException {
        Files.createDirectories(directory);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n")
                .append("  <artifactId>")
                .append(artifactId)
                .append("</artifactId>\n");
        if (modules.length > 0) {
            xml.append("  <modules>\n");
            for (String module : modules) {
                xml.append("    <module>").append(module).append("</module>\n");
            }
            xml.append("  </modules>\n");
        }
        xml.append("</project>\n");
        Files.writeString(directory.resolve("pom.xml"), xml.toString());
    }

    private void suite(Path module, String dir, String name, int tests, int failures, int errors, int skipped)
            throws IOException {
        Path reportDir = module.resolve("target").resolve(dir);
        Files.createDirectories(reportDir);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<testsuite name=\"")
                .append(name)
                .append("\" tests=\"")
                .append(tests)
                .append("\" failures=\"")
                .append(failures)
                .append("\" errors=\"")
                .append(errors)
                .append("\" skipped=\"")
                .append(skipped)
                .append("\">\n");
        int cases = tests;
        for (int i = 0; i < cases; i++) {
            xml.append("  <testcase name=\"case").append(i).append("\"/>\n");
        }
        xml.append("</testsuite>\n");
        Files.writeString(reportDir.resolve("TEST-" + name + ".xml"), xml.toString());
    }

    @Test
    @DisplayName("嵌套模块递归发现，Surefire 与 Failsafe 分列汇总")
    void discoversNestedModulesAndSeparatesReports() throws Exception {
        pom(temp, "root", "group-parent");
        Path group = temp.resolve("group-parent");
        pom(group, "group-parent", "leaf-a", "leaf-b");
        Path leafA = group.resolve("leaf-a");
        Path leafB = group.resolve("leaf-b");
        pom(leafA, "leaf-a");
        pom(leafB, "leaf-b");
        Files.createDirectories(leafA.resolve("src/main/java"));
        Files.createDirectories(leafB.resolve("src/main/java"));
        suite(leafA, "surefire-reports", "LeafAUnitTest", 3, 0, 0, 1);
        suite(leafB, "surefire-reports", "LeafBUnitTest", 2, 0, 0, 0);
        suite(leafB, "failsafe-reports", "LeafBIntegrationTest", 4, 1, 1, 0);

        TestReportSummary.Summary summary = TestReportSummary.summarize(temp);

        assertThat(summary.modules())
                .extracting(TestReportSummary.ModuleReport::artifactId)
                .containsExactly("root", "group-parent", "leaf-a", "leaf-b");
        assertThat(summary.unit().tests()).isEqualTo(5);
        assertThat(summary.unit().skipped()).isEqualTo(1);
        assertThat(summary.integration().tests()).isEqualTo(4);
        assertThat(summary.integration().failures()).isEqualTo(1);
        assertThat(summary.integration().errors()).isEqualTo(1);
        assertThat(summary.total().tests()).isEqualTo(9);
        assertThat(summary.inconsistencies()).isEmpty();
    }

    @Test
    @DisplayName("父 POM 坐标不被当作子模块自身坐标")
    void readsOwnCoordinatesNotParent() throws Exception {
        pom(temp, "root", "child");
        pomWithParent(temp.resolve("child"), "org.springframework.boot", "spring-boot-starter-parent", "child");

        TestReportSummary.Summary summary = TestReportSummary.summarize(temp);

        assertThat(summary.modules())
                .extracting(TestReportSummary.ModuleReport::artifactId)
                .containsExactly("root", "child");
        assertThat(summary.modules().get(1).path()).isEqualTo("child");
    }

    @Test
    @DisplayName("无报告模块标注缺失，不计为通过")
    void marksMissingReports() throws Exception {
        pom(temp, "root", "leaf-a");
        pom(temp.resolve("leaf-a"), "leaf-a");
        Files.createDirectories(temp.resolve("leaf-a/src/main/java"));
        suite(temp.resolve("leaf-a"), "surefire-reports", "LeafAUnitTest", 1, 0, 0, 0);

        TestReportSummary.Summary summary = TestReportSummary.summarize(temp);
        TestReportSummary.ModuleReport root = summary.modules().get(0);
        TestReportSummary.ModuleReport leaf = summary.modules().get(1);

        assertThat(root.missingReports()).contains("聚合 POM");
        assertThat(leaf.missingReports()).isEqualTo("集成");
        assertThat(summary.integration().tests()).isZero();
    }

    @Test
    @DisplayName("报告被截断（去重后仍不一致）时汇总失败")
    void failsWhenReportsAreTruncated() throws Exception {
        pom(temp, "root");
        Path reportDir = temp.resolve("target").resolve("surefire-reports");
        Files.createDirectories(reportDir);
        Files.writeString(
                reportDir.resolve("TEST-MismatchTest.xml"),
                "<testsuite name=\"MismatchTest\" tests=\"3\" failures=\"0\" errors=\"0\" skipped=\"0\">"
                        + "<testcase classname=\"A\" name=\"onlyOne\"/></testsuite>");

        TestReportSummary.Summary summary = TestReportSummary.summarize(temp);

        assertThat(summary.inconsistencies()).hasSize(1);
        assertThat(summary.inconsistencies().get(0)).contains("tests=3").contains("去重 testcase 数 1");
        assertThat(summary.warnings()).isEmpty();

        int code = TestReportSummary.run(
                new String[] {
                    "--root", temp.toString(), "--output", temp.resolve("out").toString()
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
        assertThat(code).isEqualTo(1);
    }

    @Test
    @DisplayName("重复 testcase 属 Surefire 合法输出，按 tests 计并告警而不失败")
    void toleratesDuplicateTestCases() throws Exception {
        pom(temp, "root");
        Path reportDir = temp.resolve("target").resolve("surefire-reports");
        Files.createDirectories(reportDir);
        StringBuilder xml = new StringBuilder(
                "<testsuite name=\"NestedTest\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\">");
        for (int i = 0; i < 9; i++) {
            xml.append("<testcase classname=\"Nested\" name=\"a\"/>");
            xml.append("<testcase classname=\"Nested\" name=\"b\"/>");
        }
        xml.append("</testsuite>");
        Files.writeString(reportDir.resolve("TEST-NestedTest.xml"), xml.toString());

        TestReportSummary.Summary summary = TestReportSummary.summarize(temp);

        assertThat(summary.inconsistencies()).isEmpty();
        assertThat(summary.warnings()).hasSize(1);
        assertThat(summary.warnings().get(0)).contains("tests=2").contains("重复条目");

        int code = TestReportSummary.run(
                new String[] {
                    "--root", temp.toString(), "--output", temp.resolve("out").toString()
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
        assertThat(code).isZero();
    }

    @Test
    @DisplayName("畸形 XML 使汇总失败")
    void failsOnMalformedXml() throws Exception {
        pom(temp, "root");
        Path reportDir = temp.resolve("target").resolve("surefire-reports");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("TEST-Broken.xml"), "<testsuite tests=\"1\"</testsuite>");

        int code = TestReportSummary.run(
                new String[] {
                    "--root", temp.toString(), "--output", temp.resolve("out").toString()
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertThat(code).isEqualTo(1);
    }

    @Test
    @DisplayName("非法计数属性使汇总失败而非静默按 0 通过")
    void failsOnInvalidAttribute() throws Exception {
        pom(temp, "root");
        Path reportDir = temp.resolve("target").resolve("surefire-reports");
        Files.createDirectories(reportDir);
        Files.writeString(
                reportDir.resolve("TEST-BadAttr.xml"),
                "<testsuite name=\"Bad\" tests=\"many\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");

        int code = TestReportSummary.run(
                new String[] {
                    "--root", temp.toString(), "--output", temp.resolve("out").toString()
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertThat(code).isEqualTo(1);
    }

    @Test
    @DisplayName("DOCTYPE 被拒绝，避免外部实体解析")
    void rejectsDoctype() throws Exception {
        pom(temp, "root");
        Path reportDir = temp.resolve("target").resolve("surefire-reports");
        Files.createDirectories(reportDir);
        Files.writeString(
                reportDir.resolve("TEST-Xxe.xml"),
                "<?xml version=\"1.0\"?><!DOCTYPE testsuite [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<testsuite name=\"Xxe\" tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\">&xxe;</testsuite>");

        int code = TestReportSummary.run(
                new String[] {
                    "--root", temp.toString(), "--output", temp.resolve("out").toString()
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertThat(code).isEqualTo(1);
    }

    @Test
    @DisplayName("输出 Markdown 与 JSON，JSON 保留汇总与分类字段")
    void writesMarkdownAndJson() throws Exception {
        pom(temp, "root", "leaf-a");
        pom(temp.resolve("leaf-a"), "leaf-a");
        Files.createDirectories(temp.resolve("leaf-a/src/main/java"));
        suite(temp.resolve("leaf-a"), "surefire-reports", "LeafAUnitTest", 2, 0, 0, 0);
        suite(temp.resolve("leaf-a"), "failsafe-reports", "LeafAIntegrationTest", 1, 0, 0, 0);
        Path output = temp.resolve("out");

        int code = TestReportSummary.run(
                new String[] {"--root", temp.toString(), "--output", output.toString()},
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertThat(code).isZero();
        String markdown = Files.readString(output.resolve("test-report.md"), StandardCharsets.UTF_8);
        String json = Files.readString(output.resolve("test-summary.json"), StandardCharsets.UTF_8);
        assertThat(markdown).contains("单元测试（Surefire）").contains("leaf-a");
        assertThat(json)
                .contains("\"unit\"")
                .contains("\"integration\"")
                .contains("\"total\":")
                .contains("\"tests\": 3");
    }

    @Test
    @DisplayName("参数缺失返回用法错误码")
    void rejectsMissingArguments() {
        int code = TestReportSummary.run(
                new String[] {"--root", temp.toString()},
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertThat(code).isEqualTo(2);
    }
}
