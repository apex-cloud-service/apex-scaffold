package com.apex.scaffold.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 生成任务 Mapper 注解 SQL 契约测试。
 *
 * <p>任务表的候选、进度与清理 SQL 为注解手写，无法通过普通单元测试验证真实数据库行为；
 * 本测试以注解文本为契约，锁定多实例认领安全与双方言可移植性的关键约束，
 * 防止后续改动无声回退：
 *
 * <ul>
 *   <li>RUNNING 过期候选同样受尝试次数上限约束，耗尽重试的任务不被接管分支无限重启；</li>
 *   <li>进度回写以执行者（{@code locked_by}）为作用域并顺延续租，任务被取消或接管后
 *       旧执行者写入不生效；</li>
 *   <li>终态写入同样限定执行者，避免接管双方写冲突终态；</li>
 *   <li>保留期清理采用「先查主键、后按主键删除」的可移植两步实现，
 *       不依赖 MySQL 专属的 {@code DELETE ... ORDER BY ... LIMIT} 方言。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 * @see GenTaskMapper
 */
@DisplayName("GenTaskMapper 注解 SQL 契约测试")
class GenTaskMapperSqlContractTest {

    @Test
    @DisplayName("findNextCandidate 两条候选分支都必须携带尝试次数上限")
    void findNextCandidateCapsAttemptsOnBothBranches() {
        String sql = selectSql("findNextCandidate");

        assertThat(sql)
                .contains("(status='PENDING' AND attempts < #{maxAttempts})")
                .contains("(status='RUNNING' AND locked_at < #{leaseDeadline} AND attempts < #{maxAttempts})");
    }

    @Test
    @DisplayName("updateProgress 限定执行者并顺延续租")
    void updateProgressScopesToOwnerAndRenewsLease() {
        String sql = updateSql("updateProgress");

        assertThat(sql).contains("locked_at=#{lockedAt}").contains("status='RUNNING' AND locked_by=#{lockedBy}");
    }

    @Test
    @DisplayName("markCompleted 与 markFailed 都限定执行者")
    void terminalWritesScopeToOwner() {
        assertThat(updateSql("markCompleted")).contains("status='RUNNING' AND locked_by=#{lockedBy}");
        assertThat(updateSql("markFailed")).contains("status='RUNNING' AND locked_by=#{lockedBy}");
    }

    @Test
    @DisplayName("保留期清理为可移植两步实现，不使用 MySQL 专属 DELETE ORDER BY LIMIT")
    void purgeUsesPortableTwoStepSql() {
        Method select = declaredMethod("findFinishedIdsBefore");
        Method delete = declaredMethod("deleteFinishedByIds");

        assertThat(select.getAnnotation(Select.class)).as("主键发现须为 SELECT").isNotNull();
        assertThat(select.getAnnotation(Select.class).value()[0])
                .contains("SELECT id FROM gen_task")
                .contains("ORDER BY completed_at ASC, id ASC LIMIT #{limit}");

        Delete deleteAnnotation = delete.getAnnotation(Delete.class);
        assertThat(deleteAnnotation).as("物理删除须为显式 DELETE").isNotNull();
        assertThat(String.join(" ", deleteAnnotation.value()))
                .doesNotContain("ORDER BY")
                .doesNotContain("LIMIT")
                .contains("IN");
    }

    /**
     * 读取指定 SELECT 方法的注解 SQL。
     *
     * @param method 方法名
     * @return 注解 SQL 文本
     */
    private static String selectSql(String method) {
        return selectAnnotation(method).value()[0];
    }

    /**
     * 读取指定 UPDATE 方法的注解 SQL。
     *
     * @param method 方法名
     * @return 注解 SQL 文本
     */
    private static String updateSql(String method) {
        Update annotation = declaredMethod(method).getAnnotation(Update.class);
        assertThat(annotation).as("%s 应为 @Update 注解 SQL", method).isNotNull();
        return String.join(" ", annotation.value());
    }

    /**
     * 读取指定 SELECT 方法的注解。
     *
     * @param method 方法名
     * @return @Select 注解
     */
    private static Select selectAnnotation(String method) {
        Select annotation = declaredMethod(method).getAnnotation(Select.class);
        assertThat(annotation).as("%s 应为 @Select 注解 SQL", method).isNotNull();
        return annotation;
    }

    /**
     * 按名称查找 Mapper 方法（带参方法无法用单参数 {@code getMethod} 直接获取）。
     *
     * @param name 方法名
     * @return 反射方法
     */
    private static Method declaredMethod(String name) {
        for (Method method : GenTaskMapper.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalStateException("Mapper 方法不存在: " + name);
    }
}
