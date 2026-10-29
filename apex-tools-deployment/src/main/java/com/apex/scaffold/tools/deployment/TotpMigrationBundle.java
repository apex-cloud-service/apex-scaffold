package com.apex.scaffold.tools.deployment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * TOTP 敏感字段离线迁移包：生成备份、候选与审查报告，并在校验摘要后回滚。
 *
 * <p>本类永不连接数据库；产物目录权限 0700，文件权限 0600，且拒绝覆盖已有输出。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class TotpMigrationBundle {

    /** 子命令与报告共用的格式标识。 */
    public static final String FORMAT = TotpFieldCipher.FORMAT;

    /** 产物目录权限。 */
    private static final String DIRECTORY_PERMISSION = "rwx------";

    /** 产物文件权限。 */
    private static final String FILE_PERMISSION = "rw-------";

    /** 审查报告文件名。 */
    private static final String REVIEW_FILE = "review.json";

    /** 原始数据备份文件名。 */
    private static final String BACKUP_FILE = "backup.json";

    /** 迁移后候选数据文件名。 */
    private static final String CANDIDATE_FILE = "candidate.json";

    /** 行身份字段：租户。 */
    private static final String TENANT_ID = "tenant_id";

    /** 行身份字段：主键。 */
    private static final String ID = "id";

    /** 乐观锁字段。 */
    private static final String VERSION = "version";

    /** 摘要算法。 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private TotpMigrationBundle() {}

    /**
     * 生成迁移包：解析输入行、逐字段迁移并写出三份产物。
     *
     * @param source 导出的 auth_totp JSON 数组
     * @param output 输出目录，必须不存在
     * @param key    新数据加密密钥
     * @param oldKey 旧数据加密密钥，可为 null
     * @return 审查报告
     * @throws Exception 输入非法、输出已存在或写入失败时抛出
     */
    public static JsonNode prepare(Path source, Path output, byte[] key, byte[] oldKey) throws Exception {
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException("输出目录已存在；拒绝覆盖");
        }
        byte[] original = Files.readAllBytes(source);
        JsonNode rows = JSON.readTree(original);
        if (rows == null || !rows.isArray()) {
            throw new IllegalArgumentException("输入必须是包含完整 auth_totp 行的 JSON 数组");
        }
        Set<List<JsonNode>> identities = new HashSet<>();
        int changedFields = 0;
        int changedRows = 0;
        for (JsonNode row : rows) {
            requireRowShape(row, identities);
            boolean changed = false;
            for (String field : TotpFieldCipher.FIELDS) {
                JsonNode transformed = TotpFieldCipher.transform(field, row.get(field), key, oldKey);
                if (!transformed.equals(row.get(field))) {
                    changedFields++;
                    changed = true;
                }
                ((ObjectNode) row).set(field, transformed);
            }
            if (changed) {
                changedRows++;
            }
        }
        byte[] candidate = jsonBytes(rows);
        JsonNode report = report(original, candidate, rows.size(), changedRows, changedFields);
        Files.createDirectory(
                output, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(DIRECTORY_PERMISSION)));
        privateWrite(output.resolve(BACKUP_FILE), original);
        privateWrite(output.resolve(CANDIDATE_FILE), candidate);
        privateWrite(output.resolve(REVIEW_FILE), jsonBytes(report));
        return report;
    }

    /**
     * 校验迁移包摘要后写出原始备份，用于停写窗口回滚。
     *
     * @param bundle 迁移包目录
     * @param output 回滚输出文件，必须不存在
     * @throws Exception 摘要不符或写入失败时抛出
     */
    public static void rollback(Path bundle, Path output) throws Exception {
        JsonNode report = JSON.readTree(bundle.resolve(REVIEW_FILE).toFile());
        byte[] backup = Files.readAllBytes(bundle.resolve(BACKUP_FILE));
        byte[] candidate = Files.readAllBytes(bundle.resolve(CANDIDATE_FILE));
        boolean intact = report != null
                && FORMAT.equals(report.path("format").asText())
                && digest(backup).equals(report.path("backup_sha256").asText())
                && digest(candidate).equals(report.path("candidate_sha256").asText());
        if (!intact) {
            throw new IllegalArgumentException("迁移包摘要校验失败；拒绝回滚");
        }
        privateWrite(output, backup);
    }

    /**
     * 校验行结构并登记行身份，重复身份直接拒绝迁移。
     *
     * @param row        单行数据
     * @param identities 已出现的行身份集合
     */
    private static void requireRowShape(JsonNode row, Set<List<JsonNode>> identities) {
        boolean missing = !row.isObject()
                || !row.has(ID)
                || !row.has(TENANT_ID)
                || !row.has(VERSION)
                || TotpFieldCipher.FIELDS.stream().anyMatch(field -> !row.has(field));
        if (missing) {
            throw new IllegalArgumentException("行缺少迁移所需字段");
        }
        if (!row.get(ID).isIntegralNumber() || !row.get(TENANT_ID).isTextual()) {
            throw new IllegalArgumentException("行身份字段无效");
        }
        if (!identities.add(List.of(row.get(TENANT_ID), row.get(ID)))) {
            throw new IllegalArgumentException("重复行身份；拒绝迁移");
        }
    }

    /**
     * 构造审查报告，明确声明未对数据库执行任何变更。
     *
     * @param original      原始字节
     * @param candidate     候选字节
     * @param rows          行数
     * @param changedRows   变更行数
     * @param changedFields 变更字段数
     * @return 审查报告
     * @throws Exception 摘要计算失败时抛出
     */
    private static JsonNode report(byte[] original, byte[] candidate, int rows, int changedRows, int changedFields)
            throws Exception {
        ObjectNode report = JSON.createObjectNode();
        report.put("format", FORMAT)
                .put("cipher", TotpFieldCipher.cipherDescription())
                .put("rows", rows)
                .put("changed_rows", changedRows)
                .put("changed_fields", changedFields)
                .put("backup_sha256", digest(original))
                .put("candidate_sha256", digest(candidate))
                .put("database_applied", false);
        report.putArray("apply_requirements")
                .add("停写并另行备份数据库；在隔离副本验证后审查变更")
                .add("使用 tenant_id、id、version 及旧字段值执行事务内比较更新；冲突必须整批回滚")
                .add("只替换三个敏感字段；保持其他字段；恢复写入前验证 MFA")
                .add("回滚须在停写窗口使用原始备份；恢复后仍需原加密密钥");
        return report;
    }

    /**
     * 以只新建、不覆盖的方式写入文件并同步落盘。
     *
     * @param path 目标路径
     * @param data 文件内容
     * @throws IOException 写入失败时抛出
     */
    static void privateWrite(Path path, byte[] data) throws IOException {
        try (FileChannel file = FileChannel.open(
                path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(FILE_PERMISSION)))) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
            file.force(true);
        }
    }

    /**
     * 序列化为缩进 JSON 文本（末尾换行）。
     *
     * @param value JSON 节点
     * @return UTF-8 字节
     * @throws IOException 序列化失败时抛出
     */
    static byte[] jsonBytes(JsonNode value) throws IOException {
        String text = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        return (text + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param data 输入字节
     * @return 小写十六进制摘要
     * @throws Exception 摘要算法不可用时抛出
     */
    static String digest(byte[] data) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(data));
    }
}
