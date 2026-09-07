package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PostgresqlMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9]+)__.+\\.sql");

    @Test
    void shouldProvideTheSameMigrationVersionsAsMysql() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migrationDirectory = projectRoot.resolve("apex-interfaces/src/main/resources/db/migration/postgresql");
        if (!Files.isDirectory(migrationDirectory)) {
            migrationDirectory = projectRoot.resolve("src/main/resources/db/migration/postgresql");
        }

        try (var files = Files.list(migrationDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString())
                    .filter(name -> VERSION_PATTERN.matcher(name).matches())
                    .map(name -> Integer.parseInt(VERSION_PATTERN.matcher(name).replaceFirst("$1")))
                    .sorted()
                    .toList())
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
        }
    }
}
