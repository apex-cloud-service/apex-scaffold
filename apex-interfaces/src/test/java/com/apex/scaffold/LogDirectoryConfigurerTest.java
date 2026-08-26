package com.apex.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link LogDirectoryConfigurer}.
 *
 * @author Apex Team
 */
class LogDirectoryConfigurerTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * Verifies that a module working directory resolves to the reactor root log directory.
     *
     * @throws IOException when the temporary project structure cannot be created
     */
    @Test
    void shouldUseReactorRootWhenStartedFromModuleDirectory() throws IOException {
        Path projectRoot = createProject(temporaryDirectory.resolve("project"));
        Path moduleDirectory = projectRoot.resolve("apex-interfaces");

        Path result = LogDirectoryConfigurer.resolveDefaultDirectory(moduleDirectory);

        assertThat(result).isEqualTo(projectRoot.resolve("logs"));
    }

    /**
     * Verifies that a reactor-root working directory resolves to the same log directory.
     *
     * @throws IOException when the temporary project structure cannot be created
     */
    @Test
    void shouldUseSameDirectoryWhenStartedFromReactorRoot() throws IOException {
        Path projectRoot = createProject(temporaryDirectory.resolve("project"));

        Path result = LogDirectoryConfigurer.resolveDefaultDirectory(projectRoot);

        assertThat(result).isEqualTo(projectRoot.resolve("logs"));
    }

    /** Verifies the deployment fallback when no Maven reactor can be found. */
    @Test
    void shouldFallBackToWorkingDirectoryOutsideSourceTree() {
        Path deploymentDirectory = temporaryDirectory.resolve("app");

        Path result = LogDirectoryConfigurer.resolveDefaultDirectory(deploymentDirectory);

        assertThat(result).isEqualTo(deploymentDirectory.toAbsolutePath().resolve("logs"));
    }

    /** Verifies that an explicitly configured log root also gets all required component directories. */
    @Test
    void shouldCreateComponentDirectoriesForExplicitSystemProperty() {
        String originalLogDirectory = System.getProperty(LogDirectoryConfigurer.LOG_DIRECTORY_PROPERTY);
        String originalSentinelDirectory = System.getProperty("csp.sentinel.log.dir");
        Path logDirectory = temporaryDirectory.resolve("explicit-logs");

        try {
            System.setProperty(LogDirectoryConfigurer.LOG_DIRECTORY_PROPERTY, logDirectory.toString());
            System.clearProperty("csp.sentinel.log.dir");

            LogDirectoryConfigurer.configure();

            assertThat(logDirectory)
                    .isDirectoryContaining(path -> path.getFileName().toString().equals("nacos"))
                    .isDirectoryContaining(path -> path.getFileName().toString().equals("sentinel"))
                    .isDirectoryContaining(path -> path.getFileName().toString().equals("rocketmq"))
                    .isDirectoryContaining(path -> path.getFileName().toString().equals("zipkin"));
            assertThat(System.getProperty("csp.sentinel.log.dir"))
                    .isEqualTo(logDirectory.resolve("sentinel").toAbsolutePath().toString());
        } finally {
            restoreSystemProperty(LogDirectoryConfigurer.LOG_DIRECTORY_PROPERTY, originalLogDirectory);
            restoreSystemProperty("csp.sentinel.log.dir", originalSentinelDirectory);
        }
    }

    /**
     * Creates the minimal marker files used to identify the Maven reactor root.
     *
     * @param projectRoot desired temporary project root
     * @return absolute temporary project root
     * @throws IOException when the marker files cannot be created
     */
    private static Path createProject(Path projectRoot) throws IOException {
        Files.createDirectories(projectRoot.resolve("apex-interfaces"));
        Files.createFile(projectRoot.resolve("pom.xml"));
        Files.createFile(projectRoot.resolve("apex-interfaces").resolve("pom.xml"));
        return projectRoot.toAbsolutePath();
    }

    /**
     * Restores a JVM system property changed by a test.
     *
     * @param name property name
     * @param value original value, or {@code null} when the property was absent
     */
    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
