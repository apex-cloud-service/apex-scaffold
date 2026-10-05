package com.apex.scaffold.common.persistence.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import lombok.Data;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ApexLambdaQueryWrapper} 的单元测试。
 *
 * <p>验证公共查询包装器对实体列解析、空值、空白字符串、空集合和有效值的处理行为。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@DisplayName("ApexLambdaQueryWrapper 单元测试")
class ApexLambdaQueryWrapperTest {

    @BeforeAll
    static void initLambdaCache() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "apex-lambda-wrapper-test");
        assistant.setCurrentNamespace(ApexLambdaQueryWrapperTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, TestPO.class);
    }

    @Test
    @DisplayName("eqIfPresent - 值为空时不追加条件，值存在时追加条件")
    void eqIfPresent_shouldConditionallyAppendClause() {
        ApexLambdaQueryWrapper<TestPO> wrapper = ConditionUtil.lambda(TestPO.class);

        wrapper.eqIfPresent(TestPO::getId, null);
        assertThat(wrapper.getSqlSegment()).isBlank();

        wrapper.eqIfPresent(TestPO::getId, 1L);
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("id");
    }

    @Test
    @DisplayName("eqIfPresent(String) - 空白字符串不追加条件，非空追加条件")
    void eqIfPresentString_shouldSkipBlankAndAppendWhenHasText() {
        ApexLambdaQueryWrapper<TestPO> wrapper = ConditionUtil.lambda(TestPO.class);

        wrapper.eqIfPresent(TestPO::getOpenId, " ");
        assertThat(wrapper.getSqlSegment()).isBlank();

        wrapper.eqIfPresent(TestPO::getOpenId, "alice");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("open_id");
    }

    @Test
    @DisplayName("ifPresent - null/空白/空集合不触发 consumer，其余值触发")
    void ifPresent_shouldHandleDifferentValueTypes() {
        ApexLambdaQueryWrapper<TestPO> wrapper = new ApexLambdaQueryWrapper<>();
        AtomicInteger called = new AtomicInteger(0);

        wrapper.ifPresent(null, (w, v) -> called.incrementAndGet());
        wrapper.ifPresent("  ", (w, v) -> called.incrementAndGet());
        wrapper.ifPresent(List.of(), (w, v) -> called.incrementAndGet());
        wrapper.ifPresent(1, (w, v) -> called.incrementAndGet());

        assertThat(called.get()).isEqualTo(1);
    }

    /** 测试用持久化实体，仅用于验证公共 Lambda 列解析行为。 */
    @Data
    @TableName("test_po")
    static class TestPO extends BasePO {
        @TableField("open_id")
        private String openId;
    }
}
