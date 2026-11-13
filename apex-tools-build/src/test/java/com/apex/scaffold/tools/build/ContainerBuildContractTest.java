package com.apex.scaffold.tools.build;

import static com.apex.scaffold.tools.build.MavenModuleInventoryTest.parent;
import static com.apex.scaffold.tools.build.MavenModuleInventoryTest.pom;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContainerBuildContractTest {

    @TempDir
    Path temp;

    @Test
    void allRecursivePomsMustBeCopiedBeforeDependencyResolution() throws Exception {
        MavenModuleInventory.Inventory inventory = inventory();
        Path file = containerfile("COPY pom.xml .\nCOPY group/pom.xml group/\nCOPY child/pom.xml child/\n" + offline());
        ContainerBuildContract.assertPomCopies(inventory, Files.readString(file));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"missing", "late", "duplicate", "extra", "wrong-destination", "no-boundary", "early-source"})
    void incompleteOrIncorrectPomCopiesFail(String scenario) throws Exception {
        MavenModuleInventory.Inventory inventory = inventory();
        String copies = "COPY pom.xml .\nCOPY group/pom.xml group/\n";
        String content =
                switch (scenario) {
                    case "missing" -> copies + offline();
                    case "late" -> copies + offline() + "COPY child/pom.xml child/\n";
                    case "duplicate" -> copies + "COPY child/pom.xml child/\nCOPY child/pom.xml child/\n" + offline();
                    case "extra" -> copies + "COPY child/pom.xml child/\nCOPY stale/pom.xml stale/\n" + offline();
                    case "wrong-destination" -> copies + "COPY child/pom.xml group/\n" + offline();
                    case "no-boundary" -> copies + "COPY child/pom.xml child/\n";
                    case "early-source" -> copies + "COPY child/pom.xml child/\nCOPY . .\n" + offline();
                    default -> throw new IllegalArgumentException(scenario);
                };
        Path file = containerfile(content);
        assertThrows(
                AssertionError.class, () -> ContainerBuildContract.assertPomCopies(inventory, Files.readString(file)));
    }

    @Test
    void standardServiceDirectoryAndJarAllowlistIsOrderedAfterTargetExclusion() throws Exception {
        Path file = ignore("**/target\n**/classes\n!gateway/target/\n!gateway/target/gateway-*.jar\n"
                + "!aggregate/target/\n!aggregate/target/aggregate-*.jar\n");
        ContainerBuildContract.assertPrebuiltAllowlist(Set.of("gateway", "aggregate"), Files.readString(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-directory", "missing-jar", "early", "wrong-jar", "reexcluded"})
    void incompletePrebuiltAllowlistFails(String scenario) throws Exception {
        String content =
                switch (scenario) {
                    case "missing-directory" -> "**/target\n**/classes\n!aggregate/target/aggregate-*.jar\n";
                    case "missing-jar" -> "**/target\n**/classes\n!aggregate/target/\n";
                    case "early" -> "!aggregate/target/\n!aggregate/target/aggregate-*.jar\n**/target\n**/classes\n";
                    case "wrong-jar" -> "**/target\n**/classes\n!aggregate/target/\n!aggregate/target/gateway-*.jar\n";
                    case "reexcluded" -> "**/target\n**/classes\n!aggregate/target/\n!aggregate/target/aggregate-*.jar\naggregate/target/\n";
                    default -> throw new IllegalArgumentException(scenario);
                };
        Path file = ignore(content);
        AssertionError error = assertThrows(
                AssertionError.class,
                () -> ContainerBuildContract.assertPrebuiltAllowlist(Set.of("aggregate"), Files.readString(file)));
        assertTrue(error.getMessage().contains("aggregate"));
    }

    private MavenModuleInventory.Inventory inventory() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repository"));
        pom(root, "root", "", "<modules><module>group</module></modules>");
        pom(
                root.resolve("group"),
                "group",
                parent("root", "../pom.xml"),
                "<modules><module>../child</module></modules>");
        pom(root.resolve("child"), "child", parent("group", "../group/pom.xml"), "");
        return MavenModuleInventory.read(root);
    }

    private Path containerfile(String content) throws Exception {
        Path file = temp.resolve("Containerfile");
        Files.writeString(file, "# COPY ignored/pom.xml ignored/\nFROM builder\n" + content);
        return file;
    }

    private Path ignore(String content) throws Exception {
        Path file = temp.resolve(".containerignore");
        Files.writeString(file, content);
        return file;
    }

    private static String offline() {
        return "RUN if [ \"${BUILD_MODE}\" = \"source\" ]; then \\\n    mvn dependency:go-offline; fi\n";
    }
}
