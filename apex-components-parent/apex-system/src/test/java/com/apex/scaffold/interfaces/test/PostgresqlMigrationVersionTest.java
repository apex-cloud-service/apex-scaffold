package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PostgresqlMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9]+)__.+\\.sql");

    @Test
    void shouldProvideTheSameMigrationVersionsAsMysql() throws Exception {
        assertThat(migrationVersions("postgresql")).containsExactlyElementsOf(migrationVersions("mysql"));
    }

    private List<Integer> migrationVersions(String database) throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migrationDirectory = projectRoot.resolve("apex-system/src/main/resources/db/migration/" + database);
        if (!Files.isDirectory(migrationDirectory)) {
            migrationDirectory = projectRoot.resolve("src/main/resources/db/migration/" + database);
        }

        try (var files = Files.list(migrationDirectory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> VERSION_PATTERN.matcher(name).matches())
                    .map(name -> Integer.parseInt(VERSION_PATTERN.matcher(name).replaceFirst("$1")))
                    .sorted()
                    .toList();
        }
    }
}
