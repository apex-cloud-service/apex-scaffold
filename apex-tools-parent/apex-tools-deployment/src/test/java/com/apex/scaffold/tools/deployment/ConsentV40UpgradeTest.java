package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * consent_before_v40 显式升级资源的契约断言。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class ConsentV40UpgradeTest {

    /** 仓库根目录。 */
    static final Path ROOT = Path.of(System.getProperty(LocalDeployment.ROOT_PROPERTY));

    /**
     * 读取指定方言的显式升级脚本。
     *
     * @param dialect 方言目录名
     * @return 脚本内容
     * @throws IOException 读取失败时抛出
     */
    static String upgradeSql(String dialect) throws IOException {
        Path path =
                ROOT.resolve("apex-components-parent/apex-system/src/main/resources/db/upgrade/" + dialect
                        + "/consent_before_v40.sql");
        assertTrue(Files.isRegularFile(path), dialect + " V40前置修复尚未实现");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void pairedExplicitUpgradeResourcesExist() throws IOException {
        for (String dialect : new String[] {"mysql", "postgresql"}) {
            String text = upgradeSql(dialect);
            assertFalse(text.contains("flyway_schema_history"), dialect);
            assertTrue(text.contains("registered_client_id"), dialect);
            assertTrue(text.contains("principal_name"), dialect);
            assertTrue(text.contains("updated_at"), dialect);
        }
    }

    @Test
    void postgresqlRejectsUnexpectedExistingIdType() throws IOException {
        String text = upgradeSql("postgresql");
        assertTrue(text.contains("atttypid='character varying'::regtype AND atttypmod=104"));
        assertTrue(text.indexOf("unexpected consent id type") < text.indexOf("UPDATE oauth2_authorization_consent"));
    }
}
