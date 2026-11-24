package com.apex.scaffold.tools.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 测试报告汇总工具：沿 Maven reactor 递归发现模块，按模块汇总 Surefire / Failsafe XML。
 *
 * <p>设计约束：
 *
 * <ul>
 *   <li>模块清单来自 POM {@code <modules>} 递归，不使用硬编码模块名或单层目录 glob；</li>
 *   <li>XML 使用 JDK 自带解析器并禁用 DOCTYPE 与外部实体，避免 XXE；</li>
 *   <li>Surefire 与 Failsafe 分列，缺失报告明确标注“未发现报告”，不视为通过；</li>
 *   <li>{@code tests} 属性为计数权威来源；按 {@code classname#name} 去重后的 {@code testcase}
 *       数与之不符（报告被截断）即失败，仅有重复条目时告警；属性非法或 XML 畸形同样失败；</li>
 *   <li>只解析 XML 快照，不据此推断整次 {@code verify} 已通过。</li>
 * </ul>
 *
 * <p>输出 Markdown 与 JSON 到指定目录；退出码 0 表示汇总成功（重复 testcase 仅告警），
 * 1 表示报告被截断、属性非法或 XML 解析失败，2 表示参数无效。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public final class TestReportSummary {

    /** 退出码：成功。 */
    private static final int EXIT_OK = 0;

    /** 退出码：报告不一致或解析失败。 */
    private static final int EXIT_FAILURE = 1;

    /** 退出码：参数无效。 */
    private static final int EXIT_USAGE = 2;

    /** 使用说明。 */
    private static final String USAGE = "test-report --root PATH --output DIR";

    /** POM 命名空间。 */
    private static final String POM_NS = "http://maven.apache.org/POM/4.0.0";

    /** 本仓库 Maven groupId；用于排除仓库外父 POM。 */
    private static final String PROJECT_GROUP_ID = "com.apex.scaffold";

    /** Surefire 报告目录名。 */
    private static final String SUREFIRE_DIR = "surefire-reports";

    /** Failsafe 报告目录名。 */
    private static final String FAILSAFE_DIR = "failsafe-reports";

    private static final String MODULES_ELEMENT = "modules";
    private static final String MODULE_ELEMENT = "module";
    private static final int OPTION_PAIR_SIZE = 2;

    private TestReportSummary() {}

    /**
     * 可测试入口。
     *
     * @param args        命令行参数
     * @param out         标准输出
     * @param err         标准错误
     * @return 退出码
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        String root = null;
        String output = null;
        for (int i = 0; i < args.length; i += OPTION_PAIR_SIZE) {
            if (i + 1 >= args.length) {
                err.println(USAGE);
                return EXIT_USAGE;
            }
            if ("--root".equals(args[i])) {
                root = args[i + 1];
            } else if ("--output".equals(args[i])) {
                output = args[i + 1];
            } else {
                err.println(USAGE);
                return EXIT_USAGE;
            }
        }
        if (root == null || output == null) {
            err.println(USAGE);
            return EXIT_USAGE;
        }
        try {
            Summary summary = summarize(Path.of(root));
            write(summary, Path.of(output));
            out.printf(
                    "modules=%d, unit=%d, integration=%d, tests=%d, failures=%d, errors=%d, skipped=%d%n",
                    summary.modules().size(),
                    summary.unit().tests(),
                    summary.integration().tests(),
                    summary.total().tests(),
                    summary.total().failures(),
                    summary.total().errors(),
                    summary.total().skipped());
            if (!summary.warnings().isEmpty()) {
                out.printf(
                        "重复 testcase 告警 %d 条（按 tests 属性计数）：%n",
                        summary.warnings().size());
                summary.warnings().forEach(item -> out.println("  [WARN] " + item));
            }
            if (!summary.inconsistencies().isEmpty()) {
                err.printf(
                        "报告不一致 %d 条（去重后 testcase 数与 tests 不符，可能被截断）：%n",
                        summary.inconsistencies().size());
                summary.inconsistencies().forEach(item -> err.println("  [ERROR] " + item));
                return EXIT_FAILURE;
            }
            return EXIT_OK;
        } catch (Exception error) {
            err.println("测试报告汇总失败: " + error.getMessage());
            return EXIT_FAILURE;
        }
    }

    /**
     * 递归发现模块并汇总报告。
     *
     * @param root 仓库根目录
     * @return 汇总结果
     * @throws IOException POM 读取失败
     * @throws SAXException POM 解析失败
     */
    static Summary summarize(Path root) throws IOException, SAXException {
        List<Module> modules = new ArrayList<>();
        Set<Path> visited = new LinkedHashSet<>();
        discover(root.toAbsolutePath().normalize(), modules, visited);

        Counts unit = Counts.empty();
        Counts integration = Counts.empty();
        List<String> inconsistencies = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ModuleReport> reports = new ArrayList<>();
        for (Module module : modules) {
            Counts moduleUnit = scan(module.directory(), SUREFIRE_DIR, module.artifactId(), inconsistencies, warnings);
            Counts moduleIntegration =
                    scan(module.directory(), FAILSAFE_DIR, module.artifactId(), inconsistencies, warnings);
            unit = unit.plus(moduleUnit);
            integration = integration.plus(moduleIntegration);
            reports.add(new ModuleReport(
                    module.artifactId(),
                    relativePath(root, module.directory()),
                    moduleUnit,
                    moduleIntegration,
                    module.hasSources()));
        }
        return new Summary(root.toAbsolutePath().normalize(), reports, unit, integration, inconsistencies, warnings);
    }

    /**
     * 计算模块相对仓库根的路径；根模块显示为 {@code .}。
     *
     * @param root      仓库根
     * @param directory 模块目录
     * @return 相对路径
     */
    private static String relativePath(Path root, Path directory) {
        Path relative = root.toAbsolutePath().normalize().relativize(directory);
        String value = relative.toString();
        return value.isEmpty() ? "." : value;
    }

    /**
     * 沿 POM {@code <modules>} 递归收集叶子与父模块。
     *
     * @param directory 当前模块目录
     * @param modules   收集目标
     * @param visited   已访问目录（防止重复聚合）
     * @throws IOException POM 读取失败
     * @throws SAXException POM 解析失败
     */
    private static void discover(Path directory, List<Module> modules, Set<Path> visited)
            throws IOException, SAXException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!visited.add(normalized)) {
            return;
        }
        Path pom = normalized.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return;
        }
        Document document = parse(pom);
        Element project = document.getDocumentElement();
        String artifactId = text(project, "artifactId");
        if (artifactId == null || artifactId.isBlank()) {
            artifactId = normalized.getFileName().toString();
        }
        // 根 POM 可能以外部父 POM（如 spring-boot-starter-parent）为 parent；
        // 它不是本仓库模块。仅当自身声明了非本项目 groupId 且不是根时排除。
        String groupId = text(project, "groupId");
        boolean externalGroup = groupId != null && !groupId.isBlank() && !groupId.equals(PROJECT_GROUP_ID);
        if (externalGroup) {
            return;
        }
        boolean hasSources = Files.isDirectory(normalized.resolve("src"));
        modules.add(new Module(artifactId, normalized, hasSources));
        for (Element modulesElement : children(project, MODULES_ELEMENT)) {
            for (Element module : children(modulesElement, MODULE_ELEMENT)) {
                String name = module.getTextContent();
                if (name != null && !name.isBlank()) {
                    discover(normalized.resolve(name.trim()), modules, visited);
                }
            }
        }
    }

    /**
     * 扫描一个模块的一个报告目录。
     *
     * @param module          模块目录
     * @param reportDir       报告目录名
     * @param artifactId      模块坐标
     * @param inconsistencies 不一致信息收集（致命）
     * @param warnings        告警信息收集（非致命）
     * @return 统计数量
     * @throws IOException XML 读取失败
     * @throws SAXException XML 解析失败
     */
    private static Counts scan(
            Path module, String reportDir, String artifactId, List<String> inconsistencies, List<String> warnings)
            throws IOException, SAXException {
        Path directory = module.resolve("target").resolve(reportDir);
        if (!Files.isDirectory(directory)) {
            return Counts.empty();
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(path -> path.getFileName().toString().startsWith("TEST-")
                            && path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        Counts total = Counts.empty();
        for (Path file : files) {
            Document document = parse(file);
            Element suite = document.getDocumentElement();
            Counts counts = new Counts(
                    attribute(suite, "tests", file, artifactId),
                    attribute(suite, "failures", file, artifactId),
                    attribute(suite, "errors", file, artifactId),
                    attribute(suite, "skipped", file, artifactId));
            int declared = counts.tests();
            NodeList cases = suite.getElementsByTagName("testcase");
            int actual = cases.getLength();
            int distinct = distinctCases(cases);
            if (declared != distinct) {
                // 去重后仍不等：报告被截断或损坏，必须失败。
                inconsistencies.add(artifactId + " / " + file.getFileName() + ": tests=" + declared + " 与去重 testcase 数 "
                        + distinct + " 不一致");
            } else if (declared != actual) {
                // 仅存在重复 testcase（Surefire 重复执行/嵌套类），按 tests 属性计数并告警。
                warnings.add(artifactId + " / " + file.getFileName() + ": tests=" + declared + " 与 testcase 条目数 "
                        + actual + " 不一致（重复条目）");
            }
            total = total.plus(counts);
        }
        return total;
    }

    /**
     * 统计去重后的 testcase 数（按 {@code classname}/{@code name} 组合）。
     *
     * <p>Surefire 在重复执行或嵌套测试类时会输出多条同名 {@code testcase}；
     * 去重后与 {@code tests} 属性比较可区分「合法重复」与「报告被截断」。
     *
     * @param cases testcase 节点集合
     * @return 去重后的用例数
     */
    private static int distinctCases(NodeList cases) {
        Set<String> distinct = new LinkedHashSet<>();
        for (int i = 0; i < cases.getLength(); i++) {
            Element element = (Element) cases.item(i);
            String classname = element.getAttribute("classname");
            String name = element.getAttribute("name");
            distinct.add(classname + "#" + name);
        }
        return distinct.size();
    }

    /**
     * 解析 XML 并关闭外部实体。
     *
     * @param file XML 文件
     * @return 文档
     * @throws IOException 读取失败
     * @throws SAXException 解析失败
     */
    private static Document parse(Path file) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream input = Files.newInputStream(file)) {
                return builder.parse(input);
            }
        } catch (javax.xml.parsers.ParserConfigurationException error) {
            throw new IllegalStateException("XML 解析器初始化失败", error);
        }
    }

    /**
     * 读取整型属性。
     *
     * @param element    套件元素
     * @param name       属性名
     * @param file       来源文件（用于错误信息）
     * @param artifactId 模块坐标（用于错误信息）
     * @return 属性值；缺失时返回 0
     */
    private static int attribute(Element element, String name, Path file, String artifactId) {
        String raw = element.getAttribute(name);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    artifactId + " / " + file.getFileName() + ": 非法属性 " + name + "=" + raw, error);
        }
    }

    /**
     * 取直接子元素（按命名空间或不带命名空间），不使用后代搜索。
     *
     * <p>POM 中 {@code <parent>} 块先于项目自身坐标出现，后代搜索会把父坐标误当本模块坐标。
     *
     * @param parent 父元素
     * @param name   元素名
     * @return 直接子元素集合
     */
    private static List<Element> children(Element parent, String name) {
        List<Element> matched = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                String local = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
                String namespace = element.getNamespaceURI();
                if (name.equals(local) && isAllowedNamespace(namespace)) {
                    matched.add(element);
                }
            }
        }
        return matched;
    }

    private static boolean isAllowedNamespace(String namespace) {
        return namespace == null || POM_NS.equals(namespace);
    }

    /**
     * 读取首个直接子元素的文本。
     *
     * @param parent 父元素
     * @param name   元素名
     * @return 文本；缺失时返回 {@code null}
     */
    private static String text(Element parent, String name) {
        List<Element> matched = children(parent, name);
        return matched.isEmpty() ? null : matched.get(0).getTextContent();
    }

    /**
     * 输出 Markdown 与 JSON。
     *
     * @param summary 汇总结果
     * @param output  输出目录
     * @throws IOException 写入失败
     */
    static void write(Summary summary, Path output) throws IOException {
        Files.createDirectories(output);
        StringBuilder markdown = new StringBuilder(2048);
        markdown.append("# Apex Scaffold 测试报告\n\n");
        markdown.append("> 数据来源：各模块 `target/surefire-reports`、`target/failsafe-reports` XML 快照。\n")
                .append("> 本报告只统计已存在的报告文件，不代表整次 `verify` 通过；环境依赖跳过单列，不计为通过。\n\n");
        markdown.append("## 汇总\n\n");
        markdown.append("| 类别 | 总数 | 失败 | 错误 | 跳过 | 通过 |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        row(markdown, "单元测试（Surefire）", summary.unit());
        row(markdown, "集成测试（Failsafe）", summary.integration());
        row(markdown, "合计", summary.total());
        markdown.append("\n## 模块明细\n\n");
        markdown.append("| 模块 | 路径 | 单元 | 集成 | 未发现报告 |\n");
        markdown.append("| --- | --- | ---: | ---: | --- |\n");
        for (ModuleReport report : summary.modules()) {
            markdown.append("| ")
                    .append(report.artifactId())
                    .append(" | `")
                    .append(report.path())
                    .append("` | ")
                    .append(report.unit().tests())
                    .append(" | ")
                    .append(report.integration().tests())
                    .append(" | ")
                    .append(report.missingReports())
                    .append(" |\n");
        }
        if (!summary.warnings().isEmpty()) {
            markdown.append("\n## 重复 testcase 告警（按 tests 属性计数，不阻断）\n\n");
            summary.warnings()
                    .forEach(item -> markdown.append("- ").append(item).append('\n'));
        }
        if (!summary.inconsistencies().isEmpty()) {
            markdown.append("\n## 报告不一致（去重 testcase 数与 tests 不符，需修复）\n\n");
            summary.inconsistencies()
                    .forEach(item -> markdown.append("- ").append(item).append('\n'));
        }
        Files.writeString(output.resolve("test-report.md"), markdown.toString());

        StringBuilder json = new StringBuilder(1024);
        json.append("{\n")
                .append("  \"root\": \"")
                .append(escape(summary.root().toString()))
                .append("\",\n")
                .append("  \"modules\": ")
                .append(summary.modules().size())
                .append(",\n")
                .append("  \"unit\": ")
                .append(json(summary.unit()))
                .append(",\n")
                .append("  \"integration\": ")
                .append(json(summary.integration()))
                .append(",\n")
                .append("  \"total\": ")
                .append(json(summary.total()))
                .append(",\n")
                .append("  \"inconsistencies\": ")
                .append(summary.inconsistencies().size())
                .append(",\n")
                .append("  \"warnings\": ")
                .append(summary.warnings().size())
                .append("\n")
                .append("}\n");
        Files.writeString(output.resolve("test-summary.json"), json.toString());
    }

    private static void row(StringBuilder markdown, String label, Counts counts) {
        markdown.append("| ")
                .append(label)
                .append(" | ")
                .append(counts.tests())
                .append(" | ")
                .append(counts.failures())
                .append(" | ")
                .append(counts.errors())
                .append(" | ")
                .append(counts.skipped())
                .append(" | ")
                .append(counts.passed())
                .append(" |\n");
    }

    private static String json(Counts counts) {
        return "{\"tests\": " + counts.tests() + ", \"failures\": " + counts.failures() + ", \"errors\": "
                + counts.errors() + ", \"skipped\": " + counts.skipped() + ", \"passed\": " + counts.passed() + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 已发现模块。
     *
     * @param artifactId 模块坐标
     * @param directory  模块目录
     */
    record Module(String artifactId, Path directory, boolean hasSources) {}

    /**
     * 单个模块报告。
     *
     * @param artifactId 模块坐标
     * @param path       相对仓库根路径
     * @param unit       单元测试统计
     * @param integration 集成测试统计
     */
    record ModuleReport(String artifactId, String path, Counts unit, Counts integration, boolean hasSources) {

        /**
         * 无报告的测试类别描述。
         *
         * @return 未发现报告的类别名，均存在时返回“—”
         */
        String missingReports() {
            if (!hasSources) {
                // 纯 POM 聚合项目本身无测试源码，不按缺失计。
                return "—（聚合 POM）";
            }
            if (unit.tests() == 0 && integration.tests() == 0) {
                return "单元、集成";
            }
            if (unit.tests() == 0) {
                return "单元";
            }
            if (integration.tests() == 0) {
                return "集成";
            }
            return "—";
        }
    }

    /**
     * 测试计数。
     *
     * @param tests    总数
     * @param failures 失败数
     * @param errors   错误数
     * @param skipped  跳过数
     */
    record Counts(int tests, int failures, int errors, int skipped) {

        static Counts empty() {
            return new Counts(0, 0, 0, 0);
        }

        Counts plus(Counts other) {
            return new Counts(
                    tests + other.tests, failures + other.failures, errors + other.errors, skipped + other.skipped);
        }

        int passed() {
            return tests - failures - errors - skipped;
        }
    }

    /**
     * 汇总结果。
     *
     * @param root           仓库根
     * @param modules        模块明细
     * @param unit           单元测试统计
     * @param integration    集成测试统计
     * @param inconsistencies 报告不一致项（致命）
     * @param warnings        非致命差异告警（重复 testcase）
     */
    record Summary(
            Path root,
            List<ModuleReport> modules,
            Counts unit,
            Counts integration,
            List<String> inconsistencies,
            List<String> warnings) {

        Counts total() {
            return unit.plus(integration);
        }
    }
}
