package com.apex.scaffold.tools.deployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * V42 迁移的结构契约常量：期望字段、索引与健康视图定义。
 *
 * <p>字段与索引在静态块中一次性构建，供前置检查按固定顺序比对。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class V42Contract {

    /** token 表需要补齐的字段定义：类型、可空、默认值、extra。 */
    private static final LinkedHashMap<String, List<String>> FIELDS = new LinkedHashMap<>();

    /** token 表需要补齐的索引定义。 */
    private static final LinkedHashMap<String, List<List<Object>>> INDEXES = new LinkedHashMap<>();

    /** MySQL 8 canonical VIEW_DEFINITION；规范化时移除 schema 限定符。 */
    private static final String VIEW =
            "select cast(`oauth2_authorization`.`access_token_issued_at` as date) AS `issue_date`,count(0) AS `total_tokens`,sum((case when (`oauth2_authorization`.`access_token_encrypted` is not null) then 1 else 0 end)) AS `encrypted_tokens`,sum((case when ((`oauth2_authorization`.`revoked_at` is null) and (`oauth2_authorization`.`access_token_expires_at` > now())) then 1 else 0 end)) AS `active_tokens`,sum((case when (`oauth2_authorization`.`revoked_at` is not null) then 1 else 0 end)) AS `revoked_tokens`,sum((case when (`oauth2_authorization`.`rotation_count` > 0) then 1 else 0 end)) AS `rotated_tokens`,avg(`oauth2_authorization`.`rotation_count`) AS `avg_rotation_count`,count(distinct `oauth2_authorization`.`token_family`) AS `active_families` from `oauth2_authorization` where (`oauth2_authorization`.`access_token_issued_at` >= (now() - interval 7 day)) group by cast(`oauth2_authorization`.`access_token_issued_at` as date) order by `issue_date` desc";

    /** 需要建立索引的列名，索引名统一为 {@code idx_<column>}。 */
    private static final List<String> INDEXED_COLUMNS =
            List.of("access_token_hash", "refresh_token_hash", "token_family", "revoked_at");

    /** information_schema.statistics 中非唯一索引的 non_unique 取值。 */
    private static final int NON_UNIQUE = 1;

    static {
        FIELDS.put("access_token_hash", field("varchar(64)", "YES", null, ""));
        FIELDS.put("access_token_encrypted", field("text", "YES", null, ""));
        FIELDS.put("access_token_iv", field("varchar(32)", "YES", null, ""));
        FIELDS.put("refresh_token_hash", field("varchar(64)", "YES", null, ""));
        FIELDS.put("refresh_token_encrypted", field("text", "YES", null, ""));
        FIELDS.put("refresh_token_iv", field("varchar(32)", "YES", null, ""));
        FIELDS.put("token_family", field("varchar(64)", "YES", null, ""));
        FIELDS.put("previous_jti", field("varchar(100)", "YES", null, ""));
        FIELDS.put("revoked_at", field("datetime", "YES", null, ""));
        FIELDS.put("rotation_count", field("int", "YES", "0", ""));
        for (String column : INDEXED_COLUMNS) {
            INDEXES.put("idx_" + column, new ArrayList<>(List.of(indexRow(NON_UNIQUE, column))));
        }
    }

    private V42Contract() {}

    /**
     * 返回期望字段定义。
     *
     * @return 字段名到定义的映射
     */
    public static LinkedHashMap<String, List<String>> fields() {
        return FIELDS;
    }

    /**
     * 返回期望索引定义。
     *
     * @return 索引名到索引列的映射
     */
    public static LinkedHashMap<String, List<List<Object>>> indexes() {
        return INDEXES;
    }

    /**
     * 返回期望视图定义。
     *
     * @return canonical 视图定义
     */
    public static String view() {
        return VIEW;
    }

    /**
     * 构造字段定义行。
     *
     * @param type     列类型
     * @param nullable 是否可空
     * @param defaults 默认值
     * @param extra    额外属性
     * @return 定义行
     */
    public static List<String> field(String type, String nullable, String defaults, String extra) {
        return new ArrayList<>(Arrays.asList(type, nullable, defaults, extra));
    }

    /**
     * 构造 information_schema.statistics 行，{@code sub_part} 与 {@code expression} 置空。
     *
     * @param nonUnique 是否非唯一
     * @param column    列名
     * @return 索引行
     */
    public static List<Object> indexRow(int nonUnique, String column) {
        return new ArrayList<>(Arrays.asList(nonUnique, column, null, "A", "BTREE", "YES", null));
    }
}
