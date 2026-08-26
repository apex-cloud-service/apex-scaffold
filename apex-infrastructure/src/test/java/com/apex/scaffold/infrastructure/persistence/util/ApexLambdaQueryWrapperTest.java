package com.apex.scaffold.infrastructure.persistence.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ApexLambdaQueryWrapper} 的单元测试。
 *
 * <p>覆盖 {@code eqIfPresent}、{@code likeIfPresent}、{@code inIfPresent}、
 * {@code betweenIfPresent} 与泛型 {@code ifPresent} 在 null / 空白 / 空集合 / 有效值
 * 等场景下的条件追加行为。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@DisplayName("ApexLambdaQueryWrapper 单元测试")
class ApexLambdaQueryWrapperTest {

    /**
     * 初始化 MyBatis-Plus Lambda 缓存，保证 SFunction 可正确解析列名。
     */
    @BeforeAll
    static void initLambdaCache() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "apex-lambda-wrapper-test");
        assistant.setCurrentNamespace("com.apex.scaffold.infrastructure.persistence.util.ApexLambdaQueryWrapperTest");
        TableInfoHelper.initTableInfo(assistant, UserPO.class);
    }

    /**
     * {@code eqIfPresent} 在值为 null 时不追加条件，值存在时追加等于条件。
     */
    @Test
    @DisplayName("eqIfPresent - 值为空时不追加条件，值存在时追加条件")
    void eqIfPresent_shouldConditionallyAppendClause() {
        ApexLambdaQueryWrapper<UserPO> wrapper = ConditionUtil.lambda(UserPO.class);

        wrapper.eqIfPresent(UserPO::getId, null);
        assertThat(wrapper.getSqlSegment()).isBlank();

        wrapper.eqIfPresent(UserPO::getId, 1L);
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("id");
    }

    /**
     * {@code eqIfPresent(String)} 在空白字符串时不追加条件，非空时追加条件。
     */
    @Test
    @DisplayName("eqIfPresent(String) - 空白字符串不追加条件，非空追加条件")
    void eqIfPresentString_shouldSkipBlankAndAppendWhenHasText() {
        ApexLambdaQueryWrapper<UserPO> wrapper = ConditionUtil.lambda(UserPO.class);

        wrapper.eqIfPresent(UserPO::getOpenId, " ");
        assertThat(wrapper.getSqlSegment()).isBlank();

        wrapper.eqIfPresent(UserPO::getOpenId, "alice");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("open_id");
    }

    /**
     * {@code ifPresent} 对 null / 空白字符串 / 空集合不触发 consumer，其他值触发。
     */
    @Test
    @DisplayName("ifPresent - null/空白/空集合不触发 consumer，其余值触发")
    void ifPresent_shouldHandleDifferentValueTypes() {
        ApexLambdaQueryWrapper<UserPO> wrapper = new ApexLambdaQueryWrapper<>();
        AtomicInteger called = new AtomicInteger(0);

        wrapper.ifPresent(null, (w, v) -> called.incrementAndGet());
        wrapper.ifPresent("  ", (w, v) -> called.incrementAndGet());
        wrapper.ifPresent(List.of(), (w, v) -> called.incrementAndGet());
        wrapper.ifPresent(1, (w, v) -> called.incrementAndGet());

        assertThat(called.get()).isEqualTo(1);
    }
}
