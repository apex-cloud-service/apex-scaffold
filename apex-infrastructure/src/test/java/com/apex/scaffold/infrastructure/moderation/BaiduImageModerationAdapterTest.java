package com.apex.scaffold.infrastructure.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.model.moderation.ModerationDecision;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 百度图片审核适配器单元测试。
 *
 * @author weiqiang.ling
 */
class BaiduImageModerationAdapterTest {

    private BaiduImageModerationAdapter adapter;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        adapter = new BaiduImageModerationAdapter(
                "api-key",
                "secret-key",
                "https://aip.baidubce.com/oauth/2.0/token",
                "https://aip.baidubce.com/rest/2.0/solution/v1/img_censor/v2/user_defined",
                2017253,
                10,
                9_500_000,
                4096,
                0.88F);
    }

    /**
     * 验证 {@code mapsCompliantResponseToPass} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsCompliantResponseToPass() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                {"log_id":123,"conclusion":"合规","conclusionType":1,"isHitMd5":false}
                """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.PASS);
        assertThat(result.provider()).isEqualTo("BAIDU_IMAGE_CENSOR");
        assertThat(result.requestId()).isEqualTo("123");
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.scene()).isEqualTo("overall");
            assertThat(finding.suggestion()).isEqualTo("pass");
        });
    }

    /**
     * 验证 {@code mapsSuspectedCelebrityAndKeepsStarDetails} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsSuspectedCelebrityAndKeepsStarDetails() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                {
                  "log_id":456,
                  "conclusion":"疑似",
                  "conclusionType":3,
                  "data":[{
                    "type":39,
                    "subType":1,
                    "conclusion":"疑似",
                    "conclusionType":3,
                    "msg":"存在公众人物疑似",
                    "stars":[{"name":"测试明星","probability":0.9775}]
                  }]
                }
                """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.scene()).isEqualTo("public_figure");
            assertThat(finding.suggestion()).isEqualTo("review");
            assertThat(finding.rate()).isEqualByComparingTo(new BigDecimal("97.7500"));
            assertThat(finding.details()).containsKey("stars");
        });
    }

    /**
     * 验证 {@code mapsPoliticalViolationToBlock} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsPoliticalViolationToBlock() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                {"log_id":789,"conclusion":"不合规","conclusionType":2,
                 "data":[{"type":42,"subType":420501,"conclusionType":2,
                           "msg":"存在涉政不合规","probability":0.99}]}
                """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.BLOCK);
        assertThat(result.findings().get(0).scene()).isEqualTo("political");
        assertThat(result.findings().get(0).rate()).isEqualByComparingTo("99.00");
    }

    /**
     * 验证 {@code failsClosedWhenBaiduReportsAuditFailure} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void failsClosedWhenBaiduReportsAuditFailure() throws Exception {
        var response =
                JsonUtil.getObjectMapper().readTree("{\"log_id\":999,\"conclusion\":\"审核失败\",\"conclusionType\":4}");

        assertThatThrownBy(() -> adapter.mapResponse(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审核失败");
    }
}
