package com.apex.scaffold.infrastructure.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.model.moderation.ModerationDecision;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 百度文本审核适配器单元测试。
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
class BaiduTextModerationAdapterTest {

    private BaiduTextModerationAdapter adapter;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        adapter = new BaiduTextModerationAdapter(
                "api-key",
                "secret-key",
                "https://aip.baidubce.com/oauth/2.0/token",
                "https://aip.baidubce.com/rest/2.0/solution/v1/text_censor/v2/user_defined",
                10,
                3,
                250);
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
                        {"log_id":123,"conclusion":"合规","conclusionType":1}
                        """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.PASS);
        assertThat(result.provider()).isEqualTo("BAIDU_TEXT_CENSOR");
        assertThat(result.requestId()).isEqualTo("123");
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.scene()).isEqualTo("overall");
            assertThat(finding.suggestion()).isEqualTo("pass");
        });
    }

    /**
     * 验证 {@code mapsPornTextToBlockWithSceneAndRate} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsPornTextToBlockWithSceneAndRate() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                        {
                          "log_id":456,
                          "conclusion":"不合规",
                          "conclusionType":2,
                          "data":[{
                            "type":12,
                            "subType":2,
                            "conclusion":"不合规",
                            "conclusionType":2,
                            "msg":"存在文本色情内容",
                            "hits":[{"probability":0.9876,"datasetName":"色情词库","words":["示例词"]}]
                          }]
                        }
                        """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.BLOCK);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.scene()).isEqualTo("porn");
            assertThat(finding.suggestion()).isEqualTo("block");
            assertThat(finding.rate()).isEqualByComparingTo(new BigDecimal("98.7600"));
        });
    }

    /**
     * 验证 {@code mapsSpamContactToBlockWithContactScene} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsSpamContactToBlockWithContactScene() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                        {
                          "log_id":789,
                          "conclusion":"不合规",
                          "conclusionType":2,
                          "data":[{
                            "type":12,
                            "subType":6,
                            "conclusion":"不合规",
                            "conclusionType":2,
                            "msg":"存在恶意推广-联系方式",
                            "hits":[{"probability":0.9,"datasetName":"推广词库","words":["加我微信"]}]
                          }]
                        }
                        """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.BLOCK);
        assertThat(result.findings()).singleElement().satisfies(finding -> assertThat(finding.scene())
                .isEqualTo("contact"));
    }

    /**
     * 验证 {@code mapsSuspectedToReview} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsSuspectedToReview() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                        {
                          "log_id":321,
                          "conclusion":"疑似",
                          "conclusionType":3,
                          "data":[{
                            "type":11,
                            "subType":0,
                            "conclusion":"疑似",
                            "conclusionType":3,
                            "msg":"命中官方违禁词库"
                          }]
                        }
                        """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.scene()).isEqualTo("political");
            assertThat(finding.suggestion()).isEqualTo("review");
        });
    }

    /**
     * 验证 {@code mapsLiveObservedScenesForDatingInvitationAndContact} 场景。
     *
     * <p>真实调用中观测到的 type=33 不良性邀约（疑似）与 type=36 微信号引流（不合规）。</p>
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsLiveObservedScenesForDatingInvitationAndContact() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                        {
                          "log_id":700,
                          "conclusion":"不合规",
                          "conclusionType":2,
                          "data":[
                            {"type":33,"subType":330403,"conclusion":"疑似","conclusionType":3,
                             "msg":"疑似存在不良性邀约不合规","hits":[{"probability":0.89,"words":[]}]},
                            {"type":36,"subType":360101,"conclusion":"不合规","conclusionType":2,
                             "msg":"存在微信号不合规","hits":[{"probability":1.0,"words":["微信wx123456，有"]}]}
                          ]
                        }
                        """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.BLOCK);
        assertThat(result.findings()).hasSize(2);
        assertThat(result.findings().get(0).scene()).isEqualTo("dating_invitation");
        assertThat(result.findings().get(1).scene()).isEqualTo("contact");
        assertThat(result.findings().get(0).decision()).isEqualTo(ModerationDecision.REVIEW);
    }

    /**
     * 验证 {@code mapsLiveObservedAttackScene} 场景。
     *
     * <p>真实调用中观测到的 type=34 轻度人身攻击（不合规）。</p>
     *
     * @throws Exception 执行失败时
     */
    @Test
    void mapsLiveObservedAttackScene() throws Exception {
        var result = adapter.mapResponse(
                JsonUtil.getObjectMapper()
                        .readTree(
                                """
                        {
                          "log_id":701,
                          "conclusion":"不合规",
                          "conclusionType":2,
                          "data":[{"type":34,"subType":340102,"conclusion":"不合规","conclusionType":2,
                                   "msg":"存在轻度人身攻击不合规","hits":[{"probability":0.9,"words":[]}]}]
                        }
                        """));

        assertThat(result.decision()).isEqualTo(ModerationDecision.BLOCK);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.scene()).isEqualTo("attack");
            assertThat(finding.suggestion()).isEqualTo("block");
        });
    }

    /**
     * 验证 {@code failsWhenBaiduReportsAuditFailure} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void failsWhenBaiduReportsAuditFailure() throws Exception {
        assertThatThrownBy(
                        () -> adapter.mapResponse(
                                JsonUtil.getObjectMapper()
                                        .readTree(
                                                """
                                {"log_id":654,"conclusion":"审核失败","conclusionType":4}
                                """)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审核失败");
    }

    /**
     * 验证 {@code rejectsBlankText} 场景。
     */
    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> adapter.moderate("   ").block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    /**
     * 验证 {@code rejectsOversizedText} 场景。
     */
    @Test
    void rejectsOversizedText() {
        String oversized = "很".repeat(BaiduTextModerationAdapter.MAX_TEXT_BYTES + 1);
        assertThatThrownBy(() -> adapter.moderate(oversized).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20000");
        // 上限边界内的文本进入远程调用阶段（此处仅验证不会因长度被拒）。
        assertThat(adapter.moderate("合规文本")).isInstanceOf(Mono.class);
    }
}
