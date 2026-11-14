package com.apex.scaffold.tools.build;

import static com.apex.scaffold.tools.build.MavenModuleInventory.child;
import static com.apex.scaffold.tools.build.MavenModuleInventory.childText;
import static com.apex.scaffold.tools.build.MavenModuleInventory.hasPlugin;
import static com.apex.scaffold.tools.build.MavenModuleInventory.parseXml;
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
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

/**
 * 模块注册表防漂移门禁：递归聚合清单、打包插件、显式 mainClass 与发布集合双向一致。
 *
 * <p>只沿 POM 顶层 modules 递归，不沿 parent 回溯；同时约束分组父模块与容器前置 POM COPY。
 * DOM 与 SnakeYAML 解析均在测试范围内，不执行 Maven、shell、容器或运行服务。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class BuildManifestSyncTest {

    private static final String RELEASE_SCRIPT = "scripts/release/release.sh";

    private static final Set<String> ALLOWED_DELIVERABLES = Set.of("none", "service-jar", "optional", "tool-jar");

    private static final Set<String> GROUPING_PARENTS = Set.of(
            "apex-shared-parent",
            "apex-components-parent",
            "apex-starters-parent",
            "apex-services-bom",
            "apex-applications-parent",
            "apex-tools-parent",
            "apex-business-parent");

    private static final Set<String> COMPONENTS =
            Set.of("apex-system", "apex-auth", "apex-workflow", "apex-file", "apex-office", "apex-integration");

    /** 聚合归属与继承归属不同的三个兼容例外。 */
    private static final Set<String> SERVICES_PARENT_EXCEPTIONS =
            Set.of("apex-api-contract", "apex-integration", "apex-boot-all");

    /** apex.root 由 Surefire 注入；IDE 从仓库根运行时可回退 user.dir。 */
    static Path projectRoot() {
        String root = System.getProperty("apex.root");
        if (root == null || root.isBlank()) {
            root = System.getProperty("user.dir");
        }
        return Path.of(root).toAbsolutePath().normalize();
    }

    private static Map<String, Map<String, String>> loadManifest() {
        try (InputStream in = BuildManifestSyncTest.class.getResourceAsStream("/build-manifest.yaml")) {
            assertNotNull(in, "注册表资源缺失: apex-tools-build/src/main/resources/build-manifest.yaml");
            return loadManifest(in);
        } catch (Exception e) {
            throw new IllegalStateException("解析注册表失败", e);
        }
    }

    static Map<String, Map<String, String>> loadManifest(InputStream in) {
        Map<String, Object> top = new Yaml().load(in);
        assertNotNull(top, "注册表为空");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) top.get("modules");
        assertNotNull(modules, "注册表缺少 modules 段");
        Map<String, Map<String, String>> manifest = new LinkedHashMap<>();
        for (Map<String, Object> module : modules) {
            Object value = module.get("artifactId");
            assertTrue(value instanceof String && !((String) value).isBlank(), "注册表存在缺少 artifactId 的条目");
            String id = (String) value;
            assertNull(manifest.putIfAbsent(id, stringify(module)), "注册表重复条目: " + id);
        }
        return manifest;
    }

    private static Map<String, String> stringify(Map<String, Object> module) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : module.entrySet()) {
            fields.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return fields;
    }

    /** 只比较 POM 显式声明，不登记 repackage 自动探测的启动类。 */
    private static List<String> declaredMainClasses(Document pom) {
        List<String> values = new ArrayList<>();
        NodeList nodes = pom.getElementsByTagName("mainClass");
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent().trim());
        }
        return values;
    }

    @Test
    void manifestModulesMatchRootPomDeclarations() throws Exception {
        MavenModuleInventory.assertManifestMatches(
                MavenModuleInventory.read(projectRoot()), loadManifest().keySet());
    }

    @Test
    void groupingParentsRemainPomOnly() throws Exception {
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(projectRoot());
        Map<String, Map<String, String>> manifest = loadManifest();
        Set<String> topLevel = new LinkedHashSet<>();
        for (MavenModuleInventory.Module module : inventory.modules().values()) {
            if (inventory.root().artifactId().equals(module.aggregator())) {
                topLevel.add(module.artifactId());
            }
        }
        assertEquals(GROUPING_PARENTS, topLevel, "根 POM 必须只直接聚合七个分组父模块");
        for (String id : GROUPING_PARENTS) {
            MavenModuleInventory.assertGroupingParent(inventory.modules().get(id), inventory.root(), manifest.get(id));
        }
    }

    @Test
    void leafParentsMatchGroupsAndCompatibilityExceptions() throws Exception {
        assertLeafParents(MavenModuleInventory.read(projectRoot()));
    }

    static void assertLeafParents(MavenModuleInventory.Inventory inventory) throws Exception {
        for (MavenModuleInventory.Module module : inventory.modules().values()) {
            String id = module.artifactId();
            if (GROUPING_PARENTS.contains(id)) {
                continue;
            }
            String group = aggregationGroup(id);
            assertEquals(group, module.aggregator(), id + " 的聚合归属错误");
            String parent = SERVICES_PARENT_EXCEPTIONS.contains(id) ? "apex-services-bom" : group;
            MavenModuleInventory.assertParent(module, inventory.modules().get(parent));
        }
    }

    /** 固定既有能力归属；未列出的项目业务归 business，新运行模块仍归 services。 */
    private static String aggregationGroup(String id) {
        if (Set.of("apex-common", "apex-domain", "apex-api-contract", "apex-infrastructure")
                        .contains(id)
                || id.startsWith("apex-infrastructure-")) {
            return "apex-shared-parent";
        }
        if (COMPONENTS.contains(id)) {
            return "apex-components-parent";
        }
        if (id.endsWith("-spring-boot-starter")) {
            return "apex-starters-parent";
        }
        if ("apex-gateway".equals(id) || id.endsWith("-service")) {
            return "apex-services-bom";
        }
        if (Set.of("apex-example", "apex-boot-all").contains(id)) {
            return "apex-applications-parent";
        }
        if (Set.of("apex-generator", "apex-license-tools").contains(id) || id.startsWith("apex-tools-")) {
            return "apex-tools-parent";
        }
        return "apex-business-parent";
    }

    /** 保持既有交付口径：pluginManagement 声明也计入，不把受管理的打包插件误当成无声明。 */
    @Test
    void deliverableMatchesPackagingPlugins() throws Exception {
        Map<String, Map<String, String>> manifest = loadManifest();
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(projectRoot());
        MavenModuleInventory.assertManifestMatches(inventory, manifest.keySet());
        for (Map.Entry<String, Map<String, String>> entry : manifest.entrySet()) {
            String id = entry.getKey();
            String deliverable = entry.getValue().get("deliverable");
            assertTrue(
                    deliverable != null && ALLOWED_DELIVERABLES.contains(deliverable),
                    id + " 的 deliverable 非法或缺省: " + deliverable);
            Document pom = inventory.modules().get(id).document();
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

    @Test
    void explicitMainClassMatchesManifest() throws Exception {
        Map<String, Map<String, String>> manifest = loadManifest();
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(projectRoot());
        MavenModuleInventory.assertManifestMatches(inventory, manifest.keySet());
        for (Map.Entry<String, Map<String, String>> entry : manifest.entrySet()) {
            String id = entry.getKey();
            String registered = entry.getValue().get("mainClass");
            List<String> declared =
                    declaredMainClasses(inventory.modules().get(id).document());
            List<String> expected = registered == null ? List.of() : List.of(registered);
            assertEquals(expected, declared, id + " 的显式 mainClass 在 POM 与注册表之间不一致");
        }
    }

    /** 标准交付为网关、六个业务服务与只读聚合服务；数量不替代集合一致性。 */
    @Test
    void releaseScriptServicesMatchManifest() throws Exception {
        String script = Files.readString(projectRoot().resolve(RELEASE_SCRIPT));
        Matcher matcher = Pattern.compile("SERVICES=\\(([^)]+)\\)").matcher(script);
        assertTrue(matcher.find(), RELEASE_SCRIPT + " 未找到 SERVICES 数组声明");
        Set<String> services =
                new LinkedHashSet<>(Arrays.asList(matcher.group(1).trim().split("\\s+")));
        assertEquals(serviceModules(loadManifest()), services, "release.sh SERVICES 数组与注册表 service-jar 集合不一致");
    }

    @Test
    void containerPomCopiesMatchRecursiveModules() throws Exception {
        ContainerBuildContract.assertPomCopies(
                MavenModuleInventory.read(projectRoot()),
                Files.readString(projectRoot().resolve("Containerfile")));
    }

    /** 静态校验现有放行格式和顺序；真实构建上下文及镜像验收仍须独立执行。 */
    @Test
    void containerIgnoreAllowsStandardServicePrebuiltJars() throws Exception {
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(projectRoot());
        Set<String> directories = new LinkedHashSet<>();
        for (String id : serviceModules(loadManifest())) {
            directories.add(inventory
                    .root()
                    .directory()
                    .relativize(inventory.modules().get(id).directory())
                    .toString());
        }
        ContainerBuildContract.assertPrebuiltAllowlist(
                directories, Files.readString(projectRoot().resolve(".containerignore")));
    }

    private static Set<String> serviceModules(Map<String, Map<String, String>> manifest) {
        Set<String> services = new LinkedHashSet<>();
        manifest.forEach((id, fields) -> {
            if ("service-jar".equals(fields.get("deliverable"))) {
                services.add(id);
            }
        });
        return services;
    }

    @Test
    void exampleProductionDependenciesMatchAgentsTable() throws Exception {
        Document pom = parseXml(projectRoot().resolve("apex-applications-parent/apex-example/pom.xml"));
        Element dependencies = child(pom.getDocumentElement(), "dependencies");
        assertNotNull(dependencies, "apex-example 缺少直接 dependencies");
        Set<String> pomDependencies = new LinkedHashSet<>();
        NodeList dependencyNodes = dependencies.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependency = (Element) dependencyNodes.item(i);
            String groupId = childText(dependency, "groupId");
            String scope = childText(dependency, "scope");
            if ("com.apex.scaffold".equals(groupId) && !"test".equals(scope)) {
                pomDependencies.add(childText(dependency, "artifactId").replaceFirst("^apex-", ""));
            }
        }
        String agents = Files.readString(projectRoot().resolve("AGENTS.md"));
        Matcher row = Pattern.compile("(?m)^\\| `apex-example` \\| ([^|]+) \\|").matcher(agents);
        assertTrue(row.find(), "AGENTS.md 未找到 apex-example 依赖表行");
        Set<String> documentedDependencies = new LinkedHashSet<>();
        Matcher module = Pattern.compile(
                        "\\b(?:common|domain|infrastructure-[a-z-]+|system|auth|workflow|file|office|integration|api-contract|storage-spring-boot-starter)\\b")
                .matcher(row.group(1));
        while (module.find()) {
            documentedDependencies.add(module.group());
        }
        assertEquals(pomDependencies, documentedDependencies, "apex-example 直接生产依赖与 AGENTS.md 不一致");
    }
}
