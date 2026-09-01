package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.BlacklistEntity;
import com.apex.scaffold.domain.model.entity.ReportTicketEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.BlacklistRepository;
import com.apex.scaffold.domain.repository.ReportTicketRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 内容审核服务单元测试类
 *
 * <p>测试 ModerationService 的核心业务逻辑，包括黑名单管理和举报功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private BlacklistRepository blacklistRepository;

    @Mock
    private ReportTicketRepository reportTicketRepository;

    @InjectMocks
    private ModerationService moderationService;

    private UserEntity user;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setOpenId("test_openid");
        user.setNickName("TestUser");
    }

    @Test
    @DisplayName("blacklist - 成功拉黑目标用户")
    void blacklist_Enable_Success() {
        BlacklistEntity saved = new BlacklistEntity();
        saved.setId(10L);
        saved.setUserId(1L);
        saved.setTargetUserId(2L);
        saved.setEnabled(SystemConstants.BOOLEAN_TRUE);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(blacklistRepository.upsert(any(BlacklistEntity.class))).thenReturn(Mono.just(saved));

        moderationService
                .blacklist("test_openid", 2L, true)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).containsEntry("enabled", true))
                .verifyComplete();

        verify(blacklistRepository).upsert(any(BlacklistEntity.class));
    }

    @Test
    @DisplayName("blacklist - 成功取消拉黑目标用户")
    void blacklist_Disable_Success() {
        BlacklistEntity saved = new BlacklistEntity();
        saved.setId(10L);
        saved.setUserId(1L);
        saved.setTargetUserId(2L);
        saved.setEnabled(SystemConstants.BOOLEAN_FALSE);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(blacklistRepository.upsert(any(BlacklistEntity.class))).thenReturn(Mono.just(saved));

        moderationService
                .blacklist("test_openid", 2L, false)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).containsEntry("enabled", false))
                .verifyComplete();

        verify(blacklistRepository).upsert(any(BlacklistEntity.class));
    }

    @Test
    @DisplayName("blacklist - 用户不存在时抛出异常")
    void blacklist_UserNotFound() {
        when(userService.getByOpenId("unknown")).thenReturn(Mono.empty());

        moderationService
                .blacklist("unknown", 2L, true)
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
                });

        verify(blacklistRepository, never()).upsert(any());
    }

    @Test
    @DisplayName("report - 成功提交举报工单")
    void report_Success() {
        Map<String, Object> body = Map.of(
                "targetType", "USER",
                "targetId", "2",
                "reason", "不当行为");

        ReportTicketEntity saved = new ReportTicketEntity();
        saved.setId(100L);
        saved.setStatus("PENDING");
        saved.setReporterUserId(1L);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(reportTicketRepository.save(any(ReportTicketEntity.class))).thenReturn(Mono.just(saved));

        moderationService
                .report("test_openid", body)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsKey("ticketId");
                    assertThat(result).containsEntry("status", "PENDING");
                })
                .verifyComplete();

        verify(reportTicketRepository).save(any(ReportTicketEntity.class));
    }

    @Test
    @DisplayName("report - 举报照片类型成功")
    void report_PhotoTargetType_Success() {
        Map<String, Object> body = Map.of(
                "targetType", "PHOTO",
                "targetId", "999",
                "reason", "违规图片");

        ReportTicketEntity saved = new ReportTicketEntity();
        saved.setId(200L);
        saved.setStatus("PENDING");

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(reportTicketRepository.save(any(ReportTicketEntity.class))).thenReturn(Mono.just(saved));

        moderationService
                .report("test_openid", body)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsKey("ticketId");
                    assertThat(result).containsEntry("status", "PENDING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("report - 举报消息类型成功")
    void report_MessageTargetType_Success() {
        Map<String, Object> body = Map.of(
                "targetType", "MESSAGE",
                "targetId", "555",
                "reason", "骚扰消息");

        ReportTicketEntity saved = new ReportTicketEntity();
        saved.setId(300L);
        saved.setStatus("PENDING");

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(reportTicketRepository.save(any(ReportTicketEntity.class))).thenReturn(Mono.just(saved));

        moderationService
                .report("test_openid", body)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsKey("ticketId");
                    assertThat(result).containsEntry("status", "PENDING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("report - 举报时附带证据截图")
    void report_WithEvidenceFileUrls_Success() {
        Map<String, Object> body = Map.of(
                "targetType", "USER",
                "targetId", "2",
                "reason", "欺诈行为",
                "evidenceFileUrls", List.of("http://evidence.com/img1.jpg", "http://evidence.com/img2.jpg"));

        ReportTicketEntity saved = new ReportTicketEntity();
        saved.setId(400L);
        saved.setStatus("PENDING");

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(reportTicketRepository.save(any(ReportTicketEntity.class))).thenReturn(Mono.just(saved));

        moderationService
                .report("test_openid", body)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsKey("ticketId");
                    assertThat(result).containsEntry("status", "PENDING");
                })
                .verifyComplete();

        verify(reportTicketRepository).save(any(ReportTicketEntity.class));
    }

    @Test
    @DisplayName("report - 用户不存在时抛出异常")
    void report_UserNotFound() {
        Map<String, Object> body = Map.of("targetType", "USER", "targetId", "2");

        when(userService.getByOpenId("unknown")).thenReturn(Mono.empty());

        moderationService.report("unknown", body).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
        });

        verify(reportTicketRepository, never()).save(any());
    }

    @Test
    @DisplayName("report - 无reason时使用空字符串")
    void report_WithoutReason_Success() {
        Map<String, Object> body = Map.of("targetType", "USER", "targetId", "2");

        ReportTicketEntity saved = new ReportTicketEntity();
        saved.setId(500L);
        saved.setStatus("PENDING");

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(reportTicketRepository.save(any(ReportTicketEntity.class))).thenReturn(Mono.just(saved));

        moderationService
                .report("test_openid", body)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsKey("ticketId");
                    assertThat(result).containsEntry("status", "PENDING");
                })
                .verifyComplete();
    }
}
