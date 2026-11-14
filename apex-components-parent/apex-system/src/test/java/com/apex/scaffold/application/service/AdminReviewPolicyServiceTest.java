package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.admin.ReviewReason;
import com.apex.scaffold.domain.repository.ReviewReasonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 审核原因策略服务单元测试。
 *
 * <p>覆盖原因启用状态校验与备注必填策略。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AdminReviewPolicyServiceTest {
    @Mock
    ReviewReasonRepository repository;

    /**
     * 验证停用或不存在的原因无法通过校验，并给出明确的拒绝原因。
     */
    @Test
    void rejectsDisabledOrUnknownReason() {
        when(repository.findEnabled("PHOTO", "OTHER")).thenReturn(Mono.empty());
        AdminReviewPolicyService service = new AdminReviewPolicyService(repository);
        StepVerifier.create(service.requireEnabled("PHOTO", "other", "note"))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("停用或不存在"))
                .verify();
    }

    /**
     * 验证需要备注的原因在备注为空白时被拒绝。
     */
    @Test
    void enforcesCommentPolicy() {
        ReviewReason reason = new ReviewReason(1L, "INTEREST", "OTHER", "其他", null, true, true, 1, 0);
        when(repository.findEnabled("INTEREST", "OTHER")).thenReturn(Mono.just(reason));
        AdminReviewPolicyService service = new AdminReviewPolicyService(repository);
        StepVerifier.create(service.requireEnabled("INTEREST", "OTHER", " "))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("必须填写备注"))
                .verify();
    }
}
