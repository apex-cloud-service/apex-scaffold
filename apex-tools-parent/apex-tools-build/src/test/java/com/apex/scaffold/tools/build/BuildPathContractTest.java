package com.apex.scaffold.tools.build;

import static com.apex.scaffold.tools.build.MavenModuleInventory.child;
import static com.apex.scaffold.tools.build.MavenModuleInventory.childText;
import static com.apex.scaffold.tools.build.MavenModuleInventory.children;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

/** 路径声明、当前测试 fork 的实际属性及临时文件探针；不代替业务 Logback / 部署动态验证。 */
class BuildPathContractTest {

    @TempDir
    Path temp;

    @Test
    void sourcePomRetainsRootAndLeafRelativePathDeclarations() throws Exception {
        MavenModuleInventory.Inventory inventory = MavenModuleInventory.read(BuildManifestSyncTest.projectRoot());
        Element management =
                child(child(inventory.root().document().getDocumentElement(), "build"), "pluginManagement");
        List<Element> plugins = children(child(management, "plugins"), "plugin");
        for (String id : List.of("maven-surefire-plugin", "maven-failsafe-plugin")) {
            Element plugin = plugins.stream()
                    .filter(candidate -> id.equals(childText(candidate, "artifactId")))
                    .findFirst()
                    .orElseThrow();
            String argLine = childText(child(plugin, "configuration"), "argLine");
            assertTrue(argLine.contains("-Dapex.log.dir=${project.basedir}/../../logs"), id + " 日志路径声明漂移");
            assertTrue(
                    argLine.contains("-Dcsp.sentinel.log.dir=${project.build.directory}/sentinel-logs"),
                    id + " Sentinel 路径声明漂移");
        }
        Element build =
                child(inventory.modules().get("apex-tools-build").document().getDocumentElement(), "build");
        Element surefire = children(child(build, "plugins"), "plugin").stream()
                .filter(plugin -> "maven-surefire-plugin".equals(childText(plugin, "artifactId")))
                .findFirst()
                .orElseThrow();
        Element properties = child(child(surefire, "configuration"), "systemPropertyVariables");
        assertEquals("${maven.multiModuleProjectDirectory}", childText(properties, "apex.root"));
    }

    @Test
    void currentMavenForkReceivesRootAndLeafPaths() throws Exception {
        assumeTrue(System.getProperty("apex.root") != null, "IDE 未注入 apex.root；仅 Maven fork 校验此项");
        Path root = BuildManifestSyncTest.projectRoot().toRealPath();
        Path module = root.resolve("apex-tools-parent/apex-tools-build").toRealPath();
        assertEquals(module, Path.of(System.getProperty("user.dir")).toRealPath(), "当前 fork workingDirectory 应仍为叶子目录");
        assertEquals(root.resolve("logs"), propertyPath("apex.log.dir"), "当前 fork 日志属性应指向根 logs");
        assertEquals(module.resolve("target/sentinel-logs"), propertyPath("csp.sentinel.log.dir"));
    }

    @Test
    void siblingBasedirAndExplicitPathWriteOnlyToTemporaryProbeLocations() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repository"));
        Path group = Files.createDirectory(root.resolve("apex-tools-parent"));
        Path leaf = Files.createDirectory(group.resolve("apex-tools-build"));
        Path logs = leaf.resolve("../../logs").normalize();
        Path sentinel = leaf.resolve("target/sentinel-logs");
        Path explicit = temp.resolve("explicit-logs");
        for (Path directory : List.of(logs, sentinel, explicit)) {
            Files.createDirectories(directory);
            Path probe = directory.resolve("probe.log");
            Files.writeString(probe, "temporary path probe");
            assertEquals("temporary path probe", Files.readString(probe));
        }
        assertEquals(root.resolve("logs"), logs);
        assertFalse(Files.exists(group.resolve("logs")));
        assertFalse(Files.exists(group.resolve("target")));
    }

    private static Path propertyPath(String name) {
        String value = System.getProperty(name);
        assertNotNull(value, "当前 fork 缺少属性: " + name);
        return Path.of(value).toAbsolutePath().normalize();
    }
}
