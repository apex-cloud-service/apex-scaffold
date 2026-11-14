package com.apex.scaffold.tools.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MavenModuleInventoryTest {

    @TempDir
    Path temp;

    @Test
    void siblingModulesRecurseTwoLevelsWithoutFollowingParentsOrProfiles() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repository"));
        pom(
                root,
                "root",
                "",
                "<modules><module>shared</module><module>services</module><module>business</module></modules>");
        pom(
                root.resolve("shared"),
                "shared",
                parent("root", "../pom.xml"),
                "<modules><module>../contract</module></modules>");
        pom(root.resolve("services"), "services", parent("root", "../pom.xml"), "");
        pom(root.resolve("business"), "business", parent("root", "../pom.xml"), "");
        pom(
                root.resolve("contract"),
                "contract",
                parent("services", "../services/pom.xml"),
                "<profiles><profile><id>unused</id><modules><module>missing</module></modules></profile></profiles>");
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(root);
        assertEquals(
                Set.of("shared", "services", "business", "contract"),
                inventory.modules().keySet());
        assertFalse(inventory.modules().containsKey("root"));
        assertEquals("shared", inventory.modules().get("contract").aggregator());
        MavenModuleInventory.assertParent(
                inventory.modules().get("contract"), inventory.modules().get("services"));
    }

    @Test
    void unaggregatedParentIsNotDiscoveredThroughInheritance() throws Exception {
        Path root = fixture("<module>child</module>");
        pom(root.resolve("unaggregated"), "unaggregated", parent("root", "../pom.xml"), "");
        pom(root.resolve("child"), "child", parent("unaggregated", "../unaggregated/pom.xml"), "");
        assertEquals(Set.of("child"), MavenModuleInventory.read(root).modules().keySet());
    }

    @Test
    void threeCompatibilityExceptionsKeepServicesParentWithoutDuplicateAggregation() throws Exception {
        Path root = exceptionFixture();
        BuildManifestSyncTest.assertLeafParents(MavenModuleInventory.read(root));
    }

    @ParameterizedTest
    @ValueSource(strings = {"apex-api-contract", "apex-integration", "apex-boot-all"})
    void compatibilityExceptionCannotSilentlySwitchToItsAggregationParent(String id) throws Exception {
        Path root = exceptionFixture();
        String group =
                switch (id) {
                    case "apex-api-contract" -> "apex-shared-parent";
                    case "apex-integration" -> "apex-components-parent";
                    case "apex-boot-all" -> "apex-applications-parent";
                    default -> throw new IllegalArgumentException(id);
                };
        pom(root.resolve(id), id, parent(group, "../" + group + "/pom.xml"), "");
        assertThrows(
                AssertionError.class, () -> BuildManifestSyncTest.assertLeafParents(MavenModuleInventory.read(root)));
    }

    @Test
    void emptyBusinessParentIsIncludedAndCanLaterAddBusinessModules() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repository"));
        pom(root, "root", "", "<modules><module>apex-business-parent</module></modules>");
        Path business = root.resolve("apex-business-parent");
        pom(business, "apex-business-parent", parent("root", "../pom.xml"), "<packaging>pom</packaging>");
        MavenModuleInventory.Inventory empty = MavenModuleInventory.read(root);
        MavenModuleInventory.assertManifestMatches(empty, Set.of("apex-business-parent"));
        MavenModuleInventory.assertGroupingParent(
                empty.modules().get("apex-business-parent"), empty.root(), Map.of("deliverable", "none"));
        pom(
                business,
                "apex-business-parent",
                parent("root", "../pom.xml"),
                "<packaging>pom</packaging><modules><module>../orders</module></modules>");
        pom(root.resolve("orders"), "orders", parent("apex-business-parent", "../apex-business-parent/pom.xml"), "");
        MavenModuleInventory.assertManifestMatches(
                MavenModuleInventory.read(root), Set.of("apex-business-parent", "orders"));
    }

    @Test
    void repeatedNormalizedPathFailsInsteadOfBeingDeduplicated() throws Exception {
        Path root = fixture("<module>child</module><module>child/../child</module>");
        pom(root.resolve("child"), "child", parent("root", "../pom.xml"), "");
        failure(root, "重复模块路径");
    }

    @Test
    void repeatedRealPathThroughSymlinkFails() throws Exception {
        Path root = fixture("<module>child</module><module>alias</module>");
        pom(root.resolve("child"), "child", parent("root", "../pom.xml"), "");
        Files.createSymbolicLink(root.resolve("alias"), Path.of("child"));
        failure(root, "重复模块路径");
    }

    @Test
    void symlinkAndDotDotResolveByFilesystemRatherThanLexicalNormalization() throws Exception {
        Path root = fixture("<module>group</module>");
        pom(
                root.resolve("group"),
                "group",
                parent("root", "../pom.xml"),
                "<modules><module>alias/../child</module></modules>");
        pom(root.resolve("child"), "child", parent("group", "../group/pom.xml"), "");
        Files.createDirectory(root.resolve("destination"));
        Files.createSymbolicLink(root.resolve("group/alias"), Path.of("../destination"));
        assertEquals(
                Set.of("group", "child"),
                MavenModuleInventory.read(root).modules().keySet());
    }

    @Test
    void moduleRegisteredByTwoAggregatorsFails() throws Exception {
        Path root = fixture("<module>first</module><module>second</module>");
        for (String group : List.of("first", "second")) {
            pom(
                    root.resolve(group),
                    group,
                    parent("root", "../pom.xml"),
                    "<modules><module>../child</module></modules>");
        }
        pom(root.resolve("child"), "child", parent("first", "../first/pom.xml"), "");
        failure(root, "重复模块路径");
    }

    @Test
    void duplicateGavAtDifferentPathsFails() throws Exception {
        Path root = fixture("<module>child</module><module>other</module>");
        for (String directory : List.of("child", "other")) {
            pom(root.resolve(directory), "child", parent("root", "../pom.xml"), "");
        }
        failure(root, "重复模块 GAV");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../pom.xml", "../child"})
    void aggregationCyclesFail(String backEdge) throws Exception {
        Path root = fixture("<module>child</module>");
        pom(
                root.resolve("child"),
                "child",
                parent("root", "../pom.xml"),
                "<modules><module>" + backEdge + "</module></modules>");
        failure(root, "聚合环");
    }

    @Test
    void moduleOutsideRepositoryFailsEvenWhenPomExists() throws Exception {
        Path root = fixture("<module>../outside</module>");
        pom(temp.resolve("outside"), "outside", "", "");
        failure(root, "模块路径越界");
    }

    @Test
    void symlinkOutsideRepositoryFails() throws Exception {
        Path root = fixture("<module>outside</module>");
        pom(temp.resolve("outside"), "outside", "", "");
        Files.createSymbolicLink(root.resolve("outside"), temp.resolve("outside"));
        failure(root, "模块真实路径越界");
    }

    @Test
    void missingModulePomFails() throws Exception {
        Path root = fixture("<module>missing</module>");
        Files.createDirectory(root.resolve("missing"));
        failure(root, "缺失模块 POM");
    }

    @Test
    void artifactIdMustMatchRootLevelDirectory() throws Exception {
        Path root = fixture("<module>child</module>");
        pom(root.resolve("child"), "different", parent("root", "../pom.xml"), "");
        failure(root, "artifactId 必须匹配根级目录");
    }

    @Test
    void physicallyNestedLeafFailsRootLevelConvention() throws Exception {
        Path root = fixture("<module>group</module>");
        pom(root.resolve("group"), "group", parent("root", "../pom.xml"), "<modules><module>child</module></modules>");
        pom(root.resolve("group/child"), "child", parent("group", "../pom.xml"), "");
        failure(root, "artifactId 必须匹配根级目录");
    }

    @Test
    void manifestMissingAndExtraEntriesReportAllDifferences() throws Exception {
        Path root = fixture("<module>child</module><module>other</module>");
        for (String id : List.of("child", "other")) {
            pom(root.resolve(id), id, parent("root", "../pom.xml"), "");
        }
        Path manifest = temp.resolve("manifest.yaml");
        Files.writeString(manifest, "modules:\n  - artifactId: stale\n  - artifactId: root\n");
        try (InputStream input = Files.newInputStream(manifest)) {
            Set<String> registered = BuildManifestSyncTest.loadManifest(input).keySet();
            AssertionError error = assertThrows(
                    AssertionError.class,
                    () -> MavenModuleInventory.assertManifestMatches(MavenModuleInventory.read(root), registered));
            assertTrue(error.getMessage().contains("缺项=[child, other]"));
            assertTrue(error.getMessage().contains("多项=[stale, root]"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"- artifactId: child\n  - artifactId: child", "- category: missing", "- artifactId: null"})
    void malformedManifestEntriesFail(String entries) throws Exception {
        Path manifest = temp.resolve("manifest.yaml");
        Files.writeString(manifest, "modules:\n  " + entries + "\n");
        try (InputStream input = Files.newInputStream(manifest)) {
            assertThrows(AssertionError.class, () -> BuildManifestSyncTest.loadManifest(input));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-gav", "wrong-version", "wrong-path", "missing-path", "disabled-path"})
    void parentCoordinatesAndRelativePathAreBothChecked(String scenario) throws Exception {
        Path root = fixture("<module>group</module><module>child</module>");
        pom(root.resolve("group"), "group", parent("root", "../pom.xml"), "");
        String declared =
                switch (scenario) {
                    case "wrong-gav" -> parent("root", "../group/pom.xml");
                    case "wrong-version" -> parent("group", "../group/pom.xml")
                            .replace("<version>1</version>", "<version>2</version>");
                    case "wrong-path" -> parent("group", "../pom.xml");
                    case "missing-path" -> parent("group", "../missing/pom.xml");
                    case "disabled-path" -> parent("group", "");
                    default -> throw new IllegalArgumentException(scenario);
                };
        pom(root.resolve("child"), "child", declared, "");
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(root);
        assertThrows(
                AssertionError.class,
                () -> MavenModuleInventory.assertParent(
                        inventory.modules().get("child"), inventory.modules().get("group")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "packaging",
                "dependency",
                "profile-dependency",
                "resource",
                "mainClass",
                "boot",
                "managed-boot",
                "shade",
                "goal",
                "source",
                "resource-file",
                "deliverable",
                "port"
            })
    void groupingParentRejectsRuntimeAndPackagingBehavior(String scenario) throws Exception {
        Path root = fixture("<module>group</module>");
        String extra =
                switch (scenario) {
                    case "packaging" -> "<packaging>jar</packaging>";
                    case "dependency" -> "<dependencies><dependency><groupId>test</groupId><artifactId>runtime</artifactId></dependency></dependencies>";
                    case "profile-dependency" -> "<profiles><profile><id>prod</id><dependencies><dependency><groupId>test</groupId><artifactId>runtime</artifactId><scope>runtime</scope></dependency></dependencies></profile></profiles>";
                    case "resource" -> "<build><resources><resource><directory>data</directory></resource></resources></build>";
                    case "mainClass" -> "<properties><mainClass>example.Main</mainClass></properties>";
                    case "boot" -> plugin("spring-boot-maven-plugin", false);
                    case "managed-boot" -> plugin("spring-boot-maven-plugin", true);
                    case "shade" -> plugin("maven-shade-plugin", false);
                    case "goal" -> "<build><plugins><plugin><artifactId>custom</artifactId><executions><execution><goals><goal>repackage</goal></goals></execution></executions></plugin></plugins></build>";
                    default -> "";
                };
        Path group = root.resolve("group");
        pom(
                group,
                "group",
                parent("root", "../pom.xml"),
                ("packaging".equals(scenario) ? "" : "<packaging>pom</packaging>") + extra);
        if ("source".equals(scenario) || "resource-file".equals(scenario)) {
            Path file = group.resolve(
                    "source".equals(scenario) ? "src/main/java/Main.java" : "src/main/resources/application.yml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "fixture");
        }
        Map<String, String> fields =
                switch (scenario) {
                    case "deliverable" -> Map.of("deliverable", "service-jar");
                    case "port" -> Map.of("deliverable", "none", "port", "8080");
                    default -> Map.of("deliverable", "none");
                };
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(root);
        assertThrows(
                AssertionError.class,
                () -> MavenModuleInventory.assertGroupingParent(
                        inventory.modules().get("group"), inventory.root(), fields));
    }

    private Path exceptionFixture() throws Exception {
        Path root = fixture("<module>apex-shared-parent</module><module>apex-components-parent</module>"
                + "<module>apex-applications-parent</module><module>apex-services-bom</module>");
        Map<String, String> groups = Map.of(
                "apex-shared-parent", "apex-api-contract",
                "apex-components-parent", "apex-integration",
                "apex-applications-parent", "apex-boot-all");
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            pom(
                    root.resolve(entry.getKey()),
                    entry.getKey(),
                    parent("root", "../pom.xml"),
                    "<modules><module>../" + entry.getValue() + "</module></modules>");
            pom(
                    root.resolve(entry.getValue()),
                    entry.getValue(),
                    parent("apex-services-bom", "../apex-services-bom/pom.xml"),
                    "");
        }
        pom(root.resolve("apex-services-bom"), "apex-services-bom", parent("root", "../pom.xml"), "");
        return root;
    }

    private Path fixture(String modules) throws Exception {
        Path root = Files.createDirectory(temp.resolve("repository"));
        pom(root, "root", "", "<modules>" + modules + "</modules>");
        return root;
    }

    static void pom(Path directory, String id, String parent, String extra) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>"
                        + (parent.isEmpty() ? "<groupId>fixture</groupId><version>1</version>" : parent)
                        + "<artifactId>" + id + "</artifactId>" + extra + "</project>");
    }

    static String parent(String id, String relativePath) {
        return "<parent><groupId>fixture</groupId><artifactId>" + id + "</artifactId><version>1</version><relativePath>"
                + relativePath + "</relativePath></parent>";
    }

    private static String plugin(String id, boolean managed) {
        String plugins = "<plugins><plugin><artifactId>" + id + "</artifactId></plugin></plugins>";
        return "<build>" + (managed ? "<pluginManagement>" + plugins + "</pluginManagement>" : plugins) + "</build>";
    }

    private static void failure(Path root, String message) {
        AssertionError error = assertThrows(AssertionError.class, () -> MavenModuleInventory.read(root));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }
}
