package com.apex.scaffold.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

/**
 * 防止账号注销清理 SQL 误删聊天、举报、订阅或 Elo 历史事实。
 *
 * <p>通过反射读取 Mapper 注解中的 SQL 文本做契约断言，
 * 防止后续修改清理 SQL 时破坏数据保留边界。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class AccountDeletionCleanupSqlContractTest {

    /**
     * 断言全部 DELETE 语句都不触碰受保护的历史事实表。
     *
     * <p>账号注销允许删除个人私有投影，但聊天记录、举报事实表、订阅与
     * Elo 评分/变更流水属于其他用户或平台的历史事实，必须保留。</p>
     */
    @Test
    void doesNotDeleteProtectedHistoricalFacts() {
        String deleteSql = Arrays.stream(AccountDeletionCleanupMapper.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Delete.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.joining("\n"))
                .toLowerCase();

        assertThat(deleteSql)
                .doesNotContain("biz_chat")
                .doesNotContain("biz_message")
                // The cleanup deliberately reads biz_report_ticket to avoid deleting
                // evidence files; only the report fact table itself must be protected.
                .doesNotContain("delete from biz_report ")
                .doesNotContain("biz_subscription")
                .doesNotContain("biz_elo_rating")
                .doesNotContain("elo_change_log");
    }

    /**
     * 断言调度器 SQL 使用绑定参数阈值而不是数据库专用日期函数。
     *
     * <p>锁超时边界必须由应用传入 {@code #{staleBefore}}，避免 SQL 中出现
     * {@code DATE_SUB} 等数据库方言函数导致跨库契约被破坏。</p>
     *
     * @throws Exception 反射读取注解失败时抛出
     */
    @Test
    void schedulerQueriesUseBoundCutoffInsteadOfDatabaseSpecificDateFunctions() throws Exception {
        Method selectMethod =
                AccountDeletionTaskMapper.class.getDeclaredMethod("findNextCandidate", java.time.LocalDateTime.class);
        Select select = selectMethod.getAnnotation(Select.class);

        Method claimMethod = AccountDeletionTaskMapper.class.getDeclaredMethod(
                "claim", Long.class, java.time.LocalDateTime.class, String.class, java.time.LocalDateTime.class);
        Update claim = claimMethod.getAnnotation(Update.class);

        String selectSql = String.join(" ", select.value());
        String claimSql = String.join(" ", claim.value());
        assertThat(selectSql).contains("#{staleBefore}").doesNotContainIgnoringCase("DATE_SUB");
        assertThat(claimSql).contains("#{staleBefore}").doesNotContainIgnoringCase("DATE_SUB");
    }
}
