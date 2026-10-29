package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * TokenV42Recover 的行为测试：契约解析、视图规范化、CLI 校验与 plan/apply 流程。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class TokenV42RecoverTest {

    /** V42 迁移脚本相对仓库根的路径。 */
    private static final String V42_SOURCE =
            "apex-system/src/main/resources/db/migration/mysql/V42__add_token_encryption.sql";

    /** 默认 mysql 客户端命令前缀。 */
    private static final List<String> DEFAULT_COMMAND = List.of("mysql", "--no-defaults");

    /** apply 完成后探测健康视图的语句。 */
    private static final String VIEW_PROBE = "SELECT * FROM v_token_health LIMIT 0;";

    /** 计划输出的模式标记。 */
    private static final String PLAN_MARKER = "\"mode\": \"plan\"";

    /** apply 完成提示。 */
    private static final String COMPLETE_MARKER = "structure recovery complete";

    /** 仓库根目录。 */
    static final Path ROOT = Path.of(System.getProperty(LocalDeployment.ROOT_PROPERTY));

    /**
     * 读取 V42 迁移脚本内容。
     *
     * @return 迁移脚本内容
     * @throws IOException 读取失败时抛出
     */
    static String source() throws IOException {
        return Files.readString(ROOT.resolve(V42_SOURCE), StandardCharsets.UTF_8);
    }

    /**
     * 记录执行语句并按预设顺序返回结果的伪客户端。
     *
     * @author Apex Team
     * @since 1.3.1
     */
    static class FakeClient implements RecoverClient {

        /** 已执行的语句。 */
        final List<String> executed = new ArrayList<>();

        /** 预设结果队列，元素可为字符串或异常。 */
        final Queue<Object> effects = new ArrayDeque<>();

        FakeClient(Object... effects) {
            this.effects.addAll(Arrays.asList(effects));
        }

        @Override
        public String database() {
            return "probe";
        }

        @Override
        public String execute(String sql) throws Exception {
            executed.add(sql);
            Object next = effects.isEmpty() ? "" : effects.poll();
            if (next instanceof Exception error) {
                throw error;
            }
            return (String) next;
        }

        @Override
        public List<List<Object>> rows(String query) {
            return List.of();
        }
    }

    /**
     * 记录创建参数的伪工厂。
     *
     * @author Apex Team
     * @since 1.3.1
     */
    static class Factory implements RecoverClientFactory {

        /** 已请求的命令前缀。 */
        final List<List<String>> commands = new ArrayList<>();

        /** 已请求的目标库名。 */
        final List<String> databases = new ArrayList<>();

        /** 需要返回的客户端。 */
        final RecoverClient client;

        Factory(RecoverClient client) {
            this.client = client;
        }

        @Override
        public RecoverClient create(List<String> command, String database) {
            commands.add(command);
            databases.add(database);
            return client;
        }
    }

    /**
     * 构造自动 flush 的写入器。
     *
     * @param writer 目标字符串缓冲
     * @return 写入器
     */
    static PrintWriter sink(StringWriter writer) {
        return new PrintWriter(writer, true);
    }

    /**
     * 构造固定返回同一计划的前置检查。
     *
     * @return 前置检查
     */
    static PreflightCheck fixedPlan() {
        return (client, source) -> List.of("ALTER TABLE token ADD missing INT;");
    }

    /**
     * 构造固定返回空计划的前置检查。
     *
     * @return 前置检查
     */
    static PreflightCheck emptyPlan() {
        return (client, source) -> List.of();
    }

    @Test
    void originalSourceContract() throws Exception {
        SourceContract contract = TokenV42Recover.sourceContract(source());
        assertEquals(10, contract.getColumns().size());
        assertEquals(
                Set.of("token_rotation_history", "token_security_event", "encryption_key_version"),
                contract.getTables().keySet());
        assertEquals(
                Arrays.asList("tinyint(1)", "YES", "1", ""),
                contract.getTables().get("encryption_key_version").getFields().get("active"));
        assertEquals(
                Arrays.asList("datetime", "NO", "CURRENT_TIMESTAMP", "DEFAULT_GENERATED"),
                contract.getTables().get("token_rotation_history").getFields().get("rotated_at"));
        // 字符串字面量默认值必须剥离成对引号，与 information_schema.column_default 一致
        assertEquals(
                Arrays.asList("varchar(50)", "YES", "NORMAL_REFRESH", ""),
                contract.getTables().get("token_rotation_history").getFields().get("rotation_reason"));
        assertTrue(contract.getView().startsWith("CREATE VIEW"));
    }

    @Test
    void changedSourceIsRejected() throws Exception {
        String changed = source() + "\n";
        RejectedException error = assertThrows(RejectedException.class, () -> TokenV42Recover.sourceContract(changed));
        assertTrue(error.getMessage().contains("hash differs"));
    }

    @Test
    void viewNormalizationKeepsSemantics() {
        assertEquals("selectafromt", TokenV42Recover.normalizeView("SELECT `probe`.`a` FROM `probe`.`t`", "probe"));
        assertNotEquals(
                TokenV42Recover.normalizeView("select count(0)", "probe"),
                TokenV42Recover.normalizeView("select count(1)", "probe"));
    }

    @Test
    void viewNormalizationPreservesQuotedIdentifier() {
        assertNotEquals(
                TokenV42Recover.normalizeView("SELECT `a b` FROM `t`", "probe"),
                TokenV42Recover.normalizeView("SELECT `ab` FROM `t`", "probe"));
    }

    @Test
    void applyRejectsIncompletePostflight() {
        FakeClient client = new FakeClient("8.0.45");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = TokenV42Recover.run(
                new String[] {"--database", "probe", "--offline", "--apply"},
                sink(out),
                sink(err),
                new Factory(client),
                fixedPlan());
        assertEquals(1, code);
    }

    @Test
    void planDefaultsAndNoWrites() {
        FakeClient client = new FakeClient("8.0.45");
        Factory factory = new Factory(client);
        StringWriter out = new StringWriter();
        int code = TokenV42Recover.run(
                new String[] {"--database", "probe", "--offline"},
                sink(out),
                sink(new StringWriter()),
                factory,
                fixedPlan());
        assertEquals(0, code);
        assertEquals(1, factory.commands.size());
        assertEquals(DEFAULT_COMMAND, factory.commands.get(0));
        assertEquals("probe", factory.databases.get(0));
        assertEquals(List.of("SELECT VERSION();"), client.executed);
        assertTrue(out.toString().contains(PLAN_MARKER));
    }

    @Test
    void cliRequiresOfflineAndSimpleDatabase() {
        String[][] cases = {
            {"--database", "probe"},
            {"--database", "probe;drop", "--offline"},
            {"--database", "probe", "--offline", "--mysql-command"}
        };
        for (String[] args : cases) {
            Factory factory = new Factory(new FakeClient());
            int code =
                    TokenV42Recover.run(args, sink(new StringWriter()), sink(new StringWriter()), factory, emptyPlan());
            assertEquals(2, code, Arrays.toString(args));
            assertTrue(factory.commands.isEmpty(), Arrays.toString(args));
        }
    }

    @Test
    void applyChecksViewAfterCompletePostflight() {
        FakeClient client = new FakeClient("8.0.45", "", new RuntimeException("view unavailable"));
        Queue<List<String>> plans = new ArrayDeque<>(List.of(List.of("DDL"), List.of()));
        StringWriter out = new StringWriter();
        int code = TokenV42Recover.run(
                new String[] {"--database", "probe", "--offline", "--apply"},
                sink(out),
                sink(new StringWriter()),
                new Factory(client),
                (client1, source1) -> plans.poll());
        assertEquals(1, code);
        assertFalse(out.toString().contains(COMPLETE_MARKER));
        assertEquals(VIEW_PROBE, client.executed.get(client.executed.size() - 1));
    }

    @Test
    void forceOptionFormsRejectedBeforeClient() {
        for (String option : new String[] {"--force=true", "-vf"}) {
            Factory factory = new Factory(new FakeClient());
            int code = TokenV42Recover.run(
                    new String[] {"--database", "probe", "--offline", "--mysql-command", "mysql", option},
                    sink(new StringWriter()),
                    sink(new StringWriter()),
                    factory,
                    emptyPlan());
            assertEquals(2, code, option);
            assertTrue(factory.commands.isEmpty(), option);
        }
    }
}
