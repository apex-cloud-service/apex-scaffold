package com.apex.scaffold.tools.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

/**
 * 模块注册表防漂移门禁：锁定 {@code build-manifest.yaml} 与仓库构建事实的四方一致。
 *
 * <p>本测试是 {@link #loadManifest() 注册表}作为"单一事实来源"的强制力所在，校验以下四方必须严格一致，
 * 任何一方单方面变更（新增/删除/改名模块、调整交付口径、迁移启动类、改写发布脚本）都会在 verify 阶段失败：
 *
 * <ol>
 *   <li>根 {@code pom.xml} 的 {@code <modules>} 声明集合 —— 与注册表条目双向一致；</li>
 *   <li>各模块 POM 的打包插件（spring-boot repackage / shade）—— 与注册表 {@code deliverable} 语义双向绑定；</li>
 *   <li>各模块 POM 显式声明的 {@code <mainClass>} —— 与注册表 {@code mainClass} 单点登记一致；</li>
 *   <li>{@code scripts/release/release.sh} 的 {@code SERVICES} 数组 —— 与注册表 {@code service-jar} 集合一致。</li>
 * </ol>
 *
 * <p>业务背景：历史上交付文档曾出现"六个服务 JAR"与发布脚本实际七个 JAR 的口径漂移，
 * 以及模块清单只能靠人工核对的现状；本门禁把这类漂移从"发布后人工发现"提前到"构建即失败"。
 *
 * <p>设计约束：全部解析基于 JDK 内置 DOM 与 snakeyaml（均无新增生产依赖），纯 JVM 断言，
 * 不执行任何 shell、不依赖本机容器/网络环境，可在任意 CI 上稳定复现。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class BuildManifestSyncTest {

    /** 发布脚本相对仓库根的路径；其 SERVICES 数组是"进入交付包的服务集合"的唯一实现处。 */
    private static final String RELEASE_SCRIPT = "scripts/release/release.sh";

    /**
     * 交付口径的合法取值，与 {@code build-manifest.yaml} 头注中 {@code deliverable} 字段的语义一一对应：
     * {@code none}（不产出可执行 JAR）、{@code service-jar}（进入交付包）、{@code optional}（按需构建）、
     * {@code tool-jar}（shade 打包的运维工具）。取值集合扩展时必须同步注册表头注与
     * {@link #deliverableMatchesPackagingPlugins()} 的分支覆盖。
     */
    private static final Set<String> ALLOWED_DELIVERABLES = Set.of("none", "service-jar", "optional", "tool-jar");

    /**
     * 定位仓库根目录。
     *
     * <p>{@code apex.root} 由模块 pom.xml 中 surefire 的 {@code systemPropertyVariables} 注入
     * （取 {@code maven.multiModuleProjectDirectory}，与 {@code apex-tools-deployment} 同款）；
     * 回退 {@code user.dir} 以覆盖 IDE 内单独运行测试的场景。
     *
     * @return 仓库根目录绝对路径
     */
    private static Path projectRoot() {
        String root = System.getProperty("apex.root");
        if (root == null || root.isBlank()) {
            root = System.getProperty("user.dir");
        }
        return Path.of(root);
    }

    /**
     * 解析并加载模块注册表。
     *
     * <p>注册表位于本模块 classpath（{@code /build-manifest.yaml}），以 snakeyaml 读取；
     * 顺带完成结构性自检：顶层 {@code modules} 段必须存在、条目不允许缺失 {@code artifactId}、
     * 不允许重复条目。使用 {@link LinkedHashMap} 保持 YAML 声明顺序，便于失败信息与文件顺序可对照。
     *
     * @return artifactId → 字段表 的有序注册表（字段值统一为字符串）
     */
    private static Map<String, Map<String, String>> loadManifest() {
        try (InputStream in = BuildManifestSyncTest.class.getResourceAsStream("/build-manifest.yaml")) {
            assertNotNull(in, "注册表资源缺失: apex-tools-build/src/main/resources/build-manifest.yaml");
            Map<String, Object> top = new Yaml().load(in);
            assertNotNull(top, "注册表为空");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modules = (List<Map<String, Object>>) top.get("modules");
            assertNotNull(modules, "注册表缺少 modules 段");
            Map<String, Map<String, String>> manifest = new LinkedHashMap<>();
            for (Map<String, Object> module : modules) {
                String id = String.valueOf(module.get("artifactId"));
                assertTrue(id != null && !id.isBlank(), "注册表存在缺少 artifactId 的条目");
                assertNull(manifest.putIfAbsent(id, stringify(module)), "注册表重复条目: " + id);
            }
            return manifest;
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("解析注册表失败", e);
        }
    }

    /**
     * 将注册表条目的字段值统一规整为字符串。
     *
     * <p>YAML 中的端口等数值会被 snakeyaml 读成 {@link Integer}，统一 {@link String#valueOf(Object)}
     * 后，断言侧只需按字符串比较，避免字段类型差异影响一致性判断。
     *
     * @param module snakeyaml 解析出的单个模块条目
     * @return 键 → 字符串值 的有序映射（保持 YAML 声明顺序）
     */
    private static Map<String, String> stringify(Map<String, Object> module) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : module.entrySet()) {
            fields.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return fields;
    }

    /**
     * 解析 XML 文件为 DOM 文档，并禁用 DOCTYPE 声明。
     *
     * <p>禁用 DOCTYPE 属 XXE 加固：仓库内 POM 均为固定结构、不含外部实体，
     * 关闭后可杜绝解析不可信 XML 时的外部实体注入面。选择 DOM 而非文本正则的原因见
     * {@link #hasPlugin(Document, String)}。
     *
     * @param file 待解析的 XML 文件（POM 或其他结构化配置）
     * @return 已加载的 DOM 文档
     * @throws Exception XML 语法错误或 IO 失败时原样抛出，交由 JUnit 记为测试错误
     */
    private static Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    /**
     * 判断 POM 中是否声明了指定 artifactId 的构建插件。
     *
     * <p>使用 DOM 元素匹配而非全文 {@code contains} 的原因：apex-system 的 POM 注释中出现过
     * "spring-boot-maven-plugin" 字样（解释打包链路），全文匹配会把注释当声明造成误报；
     * DOM 只匹配元素节点，注释天然不可见。{@code pluginManagement} 与 {@code build/plugins}
     * 中的声明均计入——两处任一存在即视为"该插件已声明"。
     *
     * @param pom 已解析的模块 POM 文档
     * @param artifactId 插件坐标的 artifactId（如 {@code spring-boot-maven-plugin}）
     * @return 存在声明返回 {@code true}
     */
    private static boolean hasPlugin(Document pom, String artifactId) {
        NodeList plugins = pom.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            NodeList ids = plugin.getElementsByTagName("artifactId");
            for (int j = 0; j < ids.getLength(); j++) {
                if (artifactId.equals(ids.item(j).getTextContent().trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 收集 POM 中全部显式 {@code <mainClass>} 声明。
     *
     * <p>注意口径：仅统计"显式声明"。全仓当前只有 apex-system-service 与 apex-example 两处显式声明，
     * 其余可执行服务由 spring-boot repackage 在构建期自动探测 {@code @SpringBootApplication}，
     * 注册表有意不登记这些自动探测值，避免与构建期探测机制形成第二份可漂移的副本。
     *
     * @param pom 已解析的模块 POM 文档
     * @return 显式声明的 mainClass 列表（保序，通常为空或单元素）
     */
    private static List<String> declaredMainClasses(Document pom) {
        List<String> values = new ArrayList<>();
        NodeList nodes = pom.getElementsByTagName("mainClass");
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent().trim());
        }
        return values;
    }

    /**
     * 断言一：根 POM {@code <modules>} 与注册表条目集合双向一致。
     *
     * <p>集合比较（顺序不敏感）覆盖三个失败方向：POM 新增/改名模块未登记、注册表残留已删除模块、
     * POM 与注册表各自拼写不一致。新增模块时必须同步根 POM 与注册表两处，二者缺一即失败，
     * 从而保证 {@code bin/apex.sh} 的 package/image/deploy 子命令与 {@code status} 概览
     * 不会遗漏任何模块。
     *
     * @throws Exception 根 POM 解析失败时抛出
     */
    @Test
    void manifestModulesMatchRootPomDeclarations() throws Exception {
        Set<String> declared = new LinkedHashSet<>();
        NodeList modules = parseXml(projectRoot().resolve("pom.xml")).getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            declared.add(modules.item(i).getTextContent().trim());
        }
        assertEquals(declared, loadManifest().keySet(), "根 POM modules 与注册表不一致");
    }

    /**
     * 断言二：交付口径 {@code deliverable} 与模块 POM 打包插件双向绑定。
     *
     * <p>语义映射：{@code none} ⇔ 无 spring-boot/shade 打包插件（库、starter、聚合模块）；
     * {@code service-jar} 与 {@code optional} ⇔ 必须有 {@code spring-boot-maven-plugin}（repackage 产出可执行 fat jar）；
     * {@code tool-jar} ⇔ 必须有 {@code maven-shade-plugin}（当前唯一使用者是 apex-tools-deployment）。
     * 该绑定意味着：给库模块误加 repackage、给服务漏配插件、或修改交付口径却不动 POM，
     * 都会在 verify 阶段失败。
     *
     * @throws Exception 任一模块 POM 解析失败时抛出
     */
    @Test
    void deliverableMatchesPackagingPlugins() throws Exception {
        Map<String, Map<String, String>> manifest = loadManifest();
        for (Map.Entry<String, Map<String, String>> entry : manifest.entrySet()) {
            String id = entry.getKey();
            String deliverable = entry.getValue().get("deliverable");
            assertTrue(
                    deliverable != null && ALLOWED_DELIVERABLES.contains(deliverable),
                    id + " 的 deliverable 非法或缺省: " + deliverable);
            Document pom = parseXml(projectRoot().resolve(id).resolve("pom.xml"));
            boolean springBoot = hasPlugin(pom, "spring-boot-maven-plugin");
            boolean shade = hasPlugin(pom, "maven-shade-plugin");
            switch (deliverable) {
                case "none" -> assertTrue(!springBoot && !shade, id + " 标记为 none 却声明了打包插件(spring-boot/shade)");
                case "service-jar", "optional" -> assertTrue(
                        springBoot, id + " 交付口径为 " + deliverable + " 却缺少 spring-boot-maven-plugin");
                case "tool-jar" -> assertTrue(shade, id + " 交付口径为 tool-jar 却缺少 maven-shade-plugin");
                default -> throw new IllegalStateException("未覆盖的 deliverable: " + deliverable);
            }
        }
    }

    /**
     * 断言三：POM 显式 {@code <mainClass>} 与注册表 {@code mainClass} 单点一致。
     *
     * <p>双向严格比较：POM 声明了而注册表未登记、注册表登记了而 POM 未声明、两侧值不同，
     * 三种漂移都会失败。口径与 {@link #declaredMainClasses(Document)} 一致——只约束显式声明，
     * 不触碰 repackage 自动探测的启动类。
     *
     * @throws Exception 任一模块 POM 解析失败时抛出
     */
    @Test
    void explicitMainClassMatchesManifest() throws Exception {
        Map<String, Map<String, String>> manifest = loadManifest();
        for (Map.Entry<String, Map<String, String>> entry : manifest.entrySet()) {
            String id = entry.getKey();
            String registered = entry.getValue().get("mainClass");
            List<String> declared =
                    declaredMainClasses(parseXml(projectRoot().resolve(id).resolve("pom.xml")));
            List<String> expected = registered == null ? List.of() : List.of(registered);
            assertEquals(expected, declared, id + " 的显式 mainClass 在 POM 与注册表之间不一致");
        }
    }

    /**
     * 断言四：release.sh 的 {@code SERVICES} 数组与注册表 {@code service-jar} 集合一致。
     *
     * <p>{@code SERVICES} 数组决定交付包内服务 JAR 的实际集合（网关 + 六个业务服务，共 7 个），
     * 是"发布脚本口径"的唯一实现处。此断言把交付文档、发布脚本、注册表三方钉在同一集合上——
     * 文档曾与脚本漂移出"六个服务 JAR"的错误口径，此后脚本数组变化必须同步注册表与交付文档。
     *
     * @throws Exception release.sh 读取失败时抛出
     */
    @Test
    void releaseScriptServicesMatchManifest() throws Exception {
        String script = Files.readString(projectRoot().resolve(RELEASE_SCRIPT));
        Matcher matcher = Pattern.compile("SERVICES=\\(([^)]+)\\)").matcher(script);
        assertTrue(matcher.find(), RELEASE_SCRIPT + " 未找到 SERVICES 数组声明");
        Set<String> services =
                new LinkedHashSet<>(Arrays.asList(matcher.group(1).trim().split("\\s+")));
        Set<String> expected = new LinkedHashSet<>();
        loadManifest().forEach((id, fields) -> {
            if ("service-jar".equals(fields.get("deliverable"))) {
                expected.add(id);
            }
        });
        assertEquals(expected, services, "release.sh SERVICES 数组与注册表 service-jar 集合不一致");
    }
}
