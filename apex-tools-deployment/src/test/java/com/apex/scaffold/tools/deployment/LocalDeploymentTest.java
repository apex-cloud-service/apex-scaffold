package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 离线迁移包生成与回滚的行为测试：产物摘要、权限、字段保持与密钥格式校验。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class LocalDeploymentTest {

    /** 测试用 ObjectMapper，仅用于构造与读取测试数据。 */
    static final ObjectMapper JSON = new ObjectMapper();

    /** 合法的 32 位 Base32 TOTP 种子明文。 */
    static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    /** 全零 32 字节对称密钥。 */
    static final byte[] KEY = new byte[32];

    /** 迁移包内的候选文件名。 */
    private static final String CANDIDATE_FILE = "candidate.json";

    /** 迁移包内的备份文件名。 */
    private static final String BACKUP_FILE = "backup.json";

    /** 迁移包内的审查报告文件名。 */
    private static final String REVIEW_FILE = "review.json";

    /** 产物目录期望权限。 */
    private static final String DIRECTORY_PERMISSION = "rwx------";

    /** 产物文件期望权限。 */
    private static final String FILE_PERMISSION = "rw-------";

    @TempDir
    Path temp;

    /**
     * 构造一行包含全部待迁移字段的 auth_totp 记录。
     *
     * @return 测试行
     */
    static ObjectNode row() {
        ObjectNode row = JSON.createObjectNode();
        row.put("id", 1)
                .put("tenant_id", "tenant-a")
                .put("user_id", 2)
                .put("version", 3)
                .put("secret", SECRET)
                .put("backup_codes", "12345678,87654321")
                .put("used_backup_codes", "")
                .put("deleted", 0);
        return row;
    }

    /**
     * 构造仅含合法密钥的完整环境变量映射。
     *
     * @return 环境变量映射
     */
    static Map<String, String> keys() {
        Map<String, String> env = new HashMap<>();
        for (String name : DeploymentKeyValidator.BASE64_KEYS) {
            env.put(name, Base64.getEncoder().encodeToString(KEY));
        }
        env.put(DeploymentKeyValidator.CONFIG_ENCRYPTION_KEY, "c".repeat(32));
        env.put(DeploymentKeyValidator.JWT_SECRET, "j".repeat(48));
        return env;
    }

    /**
     * 把 JSON 节点写入临时输入文件。
     *
     * @param rows 输入节点
     * @return 输入文件路径
     * @throws Exception 写入失败时抛出
     */
    Path source(JsonNode rows) throws Exception {
        Path source = temp.resolve(UUID.randomUUID() + ".json");
        Files.write(source, JSON.writeValueAsBytes(rows));
        return source;
    }

    @Test
    void snapshotRepeatBackupAndRollback() throws Exception {
        ObjectNode row = row();
        Path source = source(JSON.createArrayNode().add(row));
        byte[] original = Files.readAllBytes(source);
        Path bundle = temp.resolve("bundle");
        JsonNode report = TotpMigrationBundle.prepare(source, bundle, KEY, null);
        assertEquals(3, report.get("changed_fields").asInt());
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(original, Files.readAllBytes(bundle.resolve(BACKUP_FILE)));
        JsonNode candidate = JSON.readTree(bundle.resolve(CANDIDATE_FILE).toFile());
        for (String field : TotpFieldCipher.FIELDS) {
            assertEquals(
                    row.get(field).asText(),
                    TotpFieldCipher.decrypt(candidate.get(0).get(field).asText(), KEY));
        }
        assertEquals(3, candidate.get(0).get("version").asInt());
        assertFalse(Files.readString(bundle.resolve(REVIEW_FILE)).contains(SECRET));

        Path second = temp.resolve("second");
        assertEquals(
                0,
                TotpMigrationBundle.prepare(bundle.resolve(CANDIDATE_FILE), second, KEY, null)
                        .get("changed_fields")
                        .asInt());
        assertEquals(candidate, JSON.readTree(second.resolve(CANDIDATE_FILE).toFile()));

        Path restored = temp.resolve("restored.json");
        TotpMigrationBundle.rollback(bundle, restored);
        assertArrayEquals(original, Files.readAllBytes(restored));
        assertEquals(PosixFilePermissions.fromString(DIRECTORY_PERMISSION), Files.getPosixFilePermissions(bundle));
        for (String file : List.of(BACKUP_FILE, CANDIDATE_FILE, REVIEW_FILE)) {
            assertEquals(
                    PosixFilePermissions.fromString(FILE_PERMISSION),
                    Files.getPosixFilePermissions(bundle.resolve(file)));
        }
        assertEquals(PosixFilePermissions.fromString(FILE_PERMISSION), Files.getPosixFilePermissions(restored));

        assertThrows(Exception.class, () -> TotpMigrationBundle.prepare(source, bundle, KEY, null));
        assertThrows(Exception.class, () -> TotpMigrationBundle.rollback(bundle, restored));
        Files.writeString(bundle.resolve(CANDIDATE_FILE), "[]");
        assertThrows(Exception.class, () -> TotpMigrationBundle.rollback(bundle, temp.resolve("tampered")));
    }

    @Test
    void preservesAllOtherFieldsAndChecksBackupDigest() throws Exception {
        ObjectNode original = row();
        original.put("description", "中文记录").put("large_id", new BigInteger("123456789012345678901234567890"));
        original.set("metadata", JSON.readTree("{\"nested\":[true,null,\"unchanged\"]}"));
        Path bundle = temp.resolve("full-row");
        TotpMigrationBundle.prepare(source(JSON.createArrayNode().add(original)), bundle, KEY, null);
        ObjectNode candidate = (ObjectNode)
                JSON.readTree(bundle.resolve(CANDIDATE_FILE).toFile()).get(0);
        for (String field : TotpFieldCipher.FIELDS) {
            candidate.set(field, original.get(field));
        }
        assertEquals(original, candidate);

        Files.writeString(bundle.resolve(BACKUP_FILE), "tampered");
        Path restored = temp.resolve("rejected-restore");
        assertThrows(Exception.class, () -> TotpMigrationBundle.rollback(bundle, restored));
        assertFalse(Files.exists(restored));
    }

    @Test
    void incompleteRowsAndNonArraysProduceNoBundle() throws Exception {
        List<JsonNode> inputs = new ArrayList<>(List.of(JSON.createObjectNode(), JSON.nullNode()));
        for (String field : List.of("id", "tenant_id", "version", "secret", "backup_codes", "used_backup_codes")) {
            ObjectNode row = row();
            row.remove(field);
            inputs.add(JSON.createArrayNode().add(row));
        }
        for (JsonNode input : inputs) {
            Path output = temp.resolve(UUID.randomUUID().toString());
            Path inputFile = source(input);
            assertThrows(Exception.class, () -> TotpMigrationBundle.prepare(inputFile, output, KEY, null));
            assertFalse(Files.exists(output));
        }
    }

    @Test
    void rotationNullAndDeletedRows() throws Exception {
        byte[] old = new byte[32];
        Arrays.fill(old, (byte) 7);
        ObjectNode row = row();
        row.put("secret", TotpFieldCipher.encrypt(SECRET, old));
        row.putNull("backup_codes");
        row.putNull("used_backup_codes");
        row.put("deleted", 1);
        Path bundle = temp.resolve("rotation");
        assertEquals(
                1,
                TotpMigrationBundle.prepare(source(JSON.createArrayNode().add(row)), bundle, KEY, old)
                        .get("changed_fields")
                        .asInt());
        JsonNode result = JSON.readTree(bundle.resolve(CANDIDATE_FILE).toFile()).get(0);
        assertEquals(SECRET, TotpFieldCipher.decrypt(result.get("secret").asText(), KEY));
        assertTrue(result.get("backup_codes").isNull());
        assertTrue(result.get("used_backup_codes").isNull());
        assertEquals(1, result.get("deleted").asInt());

        Path repeated = temp.resolve("rotation-repeat");
        assertEquals(
                0,
                TotpMigrationBundle.prepare(bundle.resolve(CANDIDATE_FILE), repeated, KEY, old)
                        .get("changed_fields")
                        .asInt());
    }

    @Test
    void invalidInputAbortsBeforeArtifacts() throws Exception {
        byte[] wrong = new byte[32];
        Arrays.fill(wrong, (byte) 9);
        List<JsonNode> invalidSecrets = List.of(
                JSON.nullNode(),
                JSON.getNodeFactory().textNode("ENC(invalid)"),
                JSON.getNodeFactory().textNode("unknown"),
                JSON.getNodeFactory().textNode(TotpFieldCipher.encrypt(SECRET, wrong)),
                JSON.getNodeFactory().booleanNode(true));
        for (JsonNode invalid : invalidSecrets) {
            ObjectNode row = row();
            row.set("secret", invalid);
            Path output = temp.resolve(UUID.randomUUID().toString());
            Path source = source(JSON.createArrayNode().add(row));
            assertThrows(Exception.class, () -> TotpMigrationBundle.prepare(source, output, KEY, null));
            assertFalse(Files.exists(output));
        }

        Path duplicate = source(JSON.createArrayNode().add(row()).add(row()));
        assertThrows(
                Exception.class, () -> TotpMigrationBundle.prepare(duplicate, temp.resolve("duplicate"), KEY, null));

        ObjectNode bad = row();
        bad.put("id", true);
        Path badId = source(JSON.createArrayNode().add(bad));
        assertThrows(Exception.class, () -> TotpMigrationBundle.prepare(badId, temp.resolve("bool"), KEY, null));
    }

    @Test
    void gcmAuthenticationAndUtf8AreStrict() throws Exception {
        String encrypted = TotpFieldCipher.encrypt(SECRET, KEY);
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(Exception.class, () -> TotpFieldCipher.decrypt(tampered, KEY));
        assertThrows(
                Exception.class,
                () -> TotpFieldCipher.transform(
                        "secret", JSON.getNodeFactory().textNode(TotpFieldCipher.encrypt("invalid", KEY)), KEY, null));
    }

    @Test
    void unicodeWhitespaceMatchesPreMigrationValidation() {
        Map<String, String> env = keys();
        env.put(DeploymentKeyValidator.CONFIG_ENCRYPTION_KEY, "\u00a0".repeat(16));
        assertThrows(IllegalArgumentException.class, () -> DeploymentKeyValidator.validateKeys(env));
        for (String value : List.of("\u00a0", "\u0085", "\u001c")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DeploymentKeyValidator.validatePreflight(Map.of("REDIS_PASSWORD", value), "compose"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DeploymentKeyValidator.validatePreflight(
                            Map.of("DB_HOST_PROD", value), "nacos-datasource-prod"));
        }
    }

    @Test
    void keysValidateFormatsWithoutValues() {
        Map<String, String> env = keys();
        assertDoesNotThrow(() -> DeploymentKeyValidator.validateKeys(env));
        for (String name : DeploymentKeyValidator.BASE64_KEYS) {
            for (String bad : List.of("", "!".repeat(44), "YWJj")) {
                Map<String, String> copy = new HashMap<>(env);
                copy.put(name, bad);
                Exception failure = assertThrows(Exception.class, () -> DeploymentKeyValidator.validateKeys(copy));
                if (!bad.isEmpty()) {
                    assertFalse(failure.getMessage().contains(bad));
                }
            }
        }
        for (String bad : List.of(" ".repeat(32), "é".repeat(32), env.get("APEX_DATA_ENCRYPTION_KEY"))) {
            env.put(DeploymentKeyValidator.CONFIG_ENCRYPTION_KEY, bad);
            assertThrows(Exception.class, () -> DeploymentKeyValidator.validateKeys(env));
        }
        env.put(DeploymentKeyValidator.CONFIG_ENCRYPTION_KEY, "é".repeat(16));
        assertDoesNotThrow(() -> DeploymentKeyValidator.validateKeys(env));
        for (String bad : List.of(
                "short",
                "CHANGE-ME".repeat(8),
                "change_me".repeat(8),
                "please-change".repeat(8),
                "changeme".repeat(8))) {
            env.put(DeploymentKeyValidator.JWT_SECRET, bad);
            assertThrows(Exception.class, () -> DeploymentKeyValidator.validateKeys(env));
        }
    }
}
