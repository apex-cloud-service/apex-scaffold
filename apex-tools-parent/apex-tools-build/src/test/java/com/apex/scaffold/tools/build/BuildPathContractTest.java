package com.apex.scaffold.tools.build;

import static com.apex.scaffold.tools.build.MavenModuleInventory.child;
import static com.apex.scaffold.tools.build.MavenModuleInventory.childText;
import static com.apex.scaffold.tools.build.MavenModuleInventory.children;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    @Test
    void localRepositoryGuardAcceptsDefaultAndExternalPaths() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace"));
        Path home = Files.createDirectory(temp.resolve("home"));
        for (String shell : List.of("/bin/bash", "/bin/zsh")) {
            Probe defaultRepo = repositoryProbe(shell, root, home, Map.of(), List.of("test"));
            assertEquals(0, defaultRepo.exitCode(), defaultRepo.output());
            assertTrue(
                    defaultRepo.output().contains(home.resolve(".m2/repository").toString()));
            Probe externalRepo = repositoryProbe(
                    shell,
                    root,
                    home,
                    Map.of(),
                    List.of("-Dmaven.repo.local=" + temp.resolve("external repository"), "test"));
            assertEquals(0, externalRepo.exitCode(), externalRepo.output());
        }
    }

    @Test
    void localRepositoryGuardRejectsCommandLineAndEnvironmentOverrides() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace"));
        Path home = Files.createDirectory(temp.resolve("home"));
        for (List<String> arguments : List.of(
                List.of("-Dmaven.repo.local=target/project-local-repo", "verify"),
                List.of("-D", "maven.repo.local=target/project-local-repo", "verify"),
                List.of("-Dmaven.repo.local=" + root, "verify"),
                List.of("-Dmaven.repo.local=../workspace/target/project-local-repo", "verify"))) {
            Probe probe = repositoryProbe("/bin/zsh", root, home, Map.of(), arguments);
            assertTrue(probe.exitCode() != 0, probe.output());
            assertTrue(probe.output().contains("项目内本地仓库"), probe.output());
        }
        for (String name :
                List.of("MAVEN_ARGS", "MAVEN_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
            Probe probe = repositoryProbe(
                    "/bin/bash",
                    root,
                    home,
                    Map.of(name, "-Dsecret=must-not-be-printed -Dmaven.repo.local=target/project-local-repo"),
                    List.of("verify"));
            assertTrue(probe.exitCode() != 0, name + ": " + probe.output());
            assertFalse(probe.output().contains("must-not-be-printed"), probe.output());
        }
    }

    @Test
    void localRepositoryGuardReadsProjectConfigurationAndSettings() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace"));
        Path home = Files.createDirectory(temp.resolve("home"));
        Files.createDirectories(root.resolve(".mvn"));
        for (String name : List.of("maven.config", "jvm.config", "mvnd.properties", "maven.properties")) {
            String contents = name.endsWith(".properties")
                    ? "maven.repo.local = ${session.rootDirectory}/target/project-local-repo\n"
                    : "# ignored comment\n-Dmaven.repo.local=target/project-local-repo\n";
            Path configuration = root.resolve(".mvn/" + name);
            Files.writeString(configuration, contents);
            Probe probe = repositoryProbe("/bin/zsh", root, home, Map.of(), List.of("verify"));
            assertTrue(probe.exitCode() != 0, name + ": " + probe.output());
            Files.writeString(configuration, "# no repository override\n");
        }
        Path settings = root.resolve(".mvn/settings.xml");
        Files.writeString(
                settings,
                """
                <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
                  <localRepository>${session.rootDirectory}/target/project-local-repo</localRepository>
                  <servers><server><password>must-not-be-printed</password></server></servers>
                </settings>
                """);
        Probe projectSettings = repositoryProbe("/bin/bash", root, home, Map.of(), List.of("verify"));
        assertTrue(projectSettings.exitCode() != 0, projectSettings.output());
        assertFalse(projectSettings.output().contains("must-not-be-printed"), projectSettings.output());
        Files.writeString(settings, "<settings/>\n");
        Path explicitSettings = temp.resolve("custom settings.xml");
        Files.writeString(explicitSettings, "<settings><localRepository>" + root + "</localRepository></settings>");
        for (List<String> arguments : List.of(
                List.of("-s", explicitSettings.toString(), "verify"),
                List.of("--settings=" + explicitSettings, "verify"))) {
            Probe probe = repositoryProbe("/bin/zsh", root, home, Map.of(), arguments);
            assertTrue(probe.exitCode() != 0, probe.output());
        }
    }

    @Test
    void localRepositoryGuardReadsUserSettingsAndExplicitMvndProperties() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace"));
        Path home = Files.createDirectory(temp.resolve("home"));
        Files.createDirectories(home.resolve(".m2"));
        Files.writeString(
                home.resolve(".m2/settings.xml"),
                "<settings><localRepository>${env.APEX_REPO}/target/project-local-repo</localRepository></settings>");
        Probe settings = repositoryProbe("/bin/zsh", root, home, Map.of("APEX_REPO", root.toString()), List.of("test"));
        assertTrue(settings.exitCode() != 0, settings.output());
        Files.writeString(home.resolve(".m2/settings.xml"), "<settings/>\n");
        Path properties = temp.resolve("custom mvnd.properties");
        Files.writeString(properties, "maven.repo.local=" + root + "/target/project-local-repo\n");
        Probe explicit = repositoryProbe(
                "/bin/bash", root, home, Map.of("MVND_PROPERTIES_PATH", properties.toString()), List.of("test"));
        assertTrue(explicit.exitCode() != 0, explicit.output());
    }

    @Test
    void localRepositoryGuardCanonicalizesSymlinksAndDistinguishesSiblingPaths() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace"));
        Path home = Files.createDirectory(temp.resolve("home"));
        Path alias = Files.createSymbolicLink(temp.resolve("alias"), root);
        Probe unsafe = repositoryProbe(
                "/bin/zsh",
                root,
                home,
                Map.of(),
                List.of("-Dmaven.repo.local=" + alias + "/target/not-created", "test"));
        assertTrue(unsafe.exitCode() != 0, unsafe.output());
        Probe sibling = repositoryProbe(
                "/bin/zsh",
                root,
                home,
                Map.of(),
                List.of("-Dmaven.repo.local=" + temp.resolve("workspace-cache"), "test"));
        assertEquals(0, sibling.exitCode(), sibling.output());
        Files.createDirectories(home.resolve(".m2"));
        Files.createSymbolicLink(home.resolve(".m2/repository"), root);
        Probe defaultAlias = repositoryProbe("/bin/bash", root, home, Map.of(), List.of("test"));
        assertTrue(defaultAlias.exitCode() != 0, defaultAlias.output());
    }

    @Test
    void localRepositoryGuardHonorsFinalCommandLineValue() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace"));
        Path home = Files.createDirectory(temp.resolve("home"));
        Files.createDirectories(root.resolve(".mvn"));
        Files.writeString(root.resolve(".mvn/maven.config"), "-Dmaven.repo.local=target/project-local-repo\n");
        Probe safe = repositoryProbe(
                "/bin/zsh",
                root,
                home,
                Map.of("MAVEN_OPTS", "-Dmaven.repo.local=target/project-local-repo"),
                List.of(
                        "-Dmaven.repo.local=target/project-local-repo",
                        "-Dmaven.repo.local=" + home.resolve(".m2/repository"),
                        "test"));
        assertEquals(0, safe.exitCode(), safe.output());
    }

    private Probe repositoryProbe(
            String shell, Path root, Path home, Map<String, String> environment, List<String> arguments)
            throws Exception {
        List<String> command = new ArrayList<>(List.of(
                shell,
                "-c",
                "source \"$1\"\nshift\napex_check_local_repository \"$@\"",
                "repository-test",
                BuildManifestSyncTest.projectRoot()
                        .resolve("scripts/lib/common.sh")
                        .toString(),
                root.toString()));
        command.addAll(arguments);
        ProcessBuilder builder =
                new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment()
                .putAll(Map.of("HOME", home.toString(), "PATH", "/usr/bin:/bin:/opt/homebrew/bin", "LC_ALL", "C"));
        builder.environment().putAll(environment);
        Process process = builder.start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "repository guard timed out");
        return new Probe(
                process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record Probe(int exitCode, String output) {}

    private static Path propertyPath(String name) {
        String value = System.getProperty(name);
        assertNotNull(value, "当前 fork 缺少属性: " + name);
        return Path.of(value).toAbsolutePath().normalize();
    }
}
