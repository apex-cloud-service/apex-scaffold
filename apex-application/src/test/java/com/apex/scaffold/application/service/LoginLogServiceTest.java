package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.LoginLogEntity;
import com.apex.scaffold.domain.repository.LoginLogRepository;
import java.util.List;
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
 * 登录日志应用服务单元测试。
 *
 * <p>覆盖登录日志的记录与分页回溯查询逻辑，验证日志实体转换与
 * 分页参数边界处理。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LoginLogServiceTest {

    @Mock
    private LoginLogRepository loginLogRepository;

    @InjectMocks
    private LoginLogService loginLogService;

    /** 登录日志实体 */
    private LoginLogEntity logEntity;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
        logEntity = LoginLogEntity.create("13800138000", "127.0.0.1", "Chrome", "macOS", true, "登录成功");
        logEntity.setId(1L);
    }

    /**
     * 测试记录登录日志。
     */
    @Test
    @DisplayName("record - 成功记录登录日志")
    void record_Success() {
        when(loginLogRepository.insert(any(LoginLogEntity.class))).thenReturn(Mono.just(1));

        loginLogService.record(logEntity).as(StepVerifier::create).verifyComplete();

        verify(loginLogRepository).insert(any(LoginLogEntity.class));
    }

    /**
     * 测试分页查询登录日志。
     */
    @Test
    @DisplayName("search - 成功分页查询登录日志")
    void search_Success() {
        PageResponse<LoginLogEntity> page = PageResponse.of(List.of(logEntity), 1L, 1, 20);
        when(loginLogRepository.search("138", null, 1, 20)).thenReturn(Mono.just(page));

        loginLogService
                .search("138", null, 1, 20)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getTotal()).isEqualTo(1L);
                    assertThat(result.getList()).hasSize(1);
                    assertThat(result.getList().get(0).getUsername()).isEqualTo("13800138000");
                    assertThat(result.getList().get(0).getStatus()).isEqualTo(0);
                })
                .verifyComplete();
    }

    /**
     * 测试分页大小超过上限时被截断。
     */
    @Test
    @DisplayName("search - 分页大小超过100时截断为100")
    void search_PageSizeBounded() {
        PageResponse<LoginLogEntity> page = PageResponse.of(List.of(), 0L, 1, 100);
        when(loginLogRepository.search(null, null, 1, 100)).thenReturn(Mono.just(page));

        loginLogService
                .search(null, null, 1, 500)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result.getPageSize()).isEqualTo(100))
                .verifyComplete();
    }
}
