package com.apex.scaffold.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.JobDTO;
import com.apex.scaffold.application.dto.admin.JobQueryRequest;
import com.apex.scaffold.application.service.JobService;
import com.apex.scaffold.common.dto.PageResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link JobHandler} 分页查询接口测试。
 *
 * <p>测试定时任务分页查询接口的请求处理、参数解析、校验和响应构建逻辑。
 * 使用 {@link MockServerRequest} 模拟 HTTP 请求，验证接口层的正确性。</p>
 *
 * <h2>测试场景</h2>
 * <ul>
 *   <li>正常分页查询请求</li>
 *   <li>带查询条件的分页请求</li>
 *   <li>缺少必填参数的错误处理</li>
 *   <li>参数格式错误的处理</li>
 *   <li>参数超出范围的处理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("定时任务接口 - 分页查询测试")
class JobHandlerPageTest {

    @Mock
    private JobService jobService;

    @InjectMocks
    private JobHandler jobHandler;

    private JobDTO testJobDTO;
    private PageResponse<JobDTO> testPageResponse;

    /**
     * 测试前置准备。
     *
     * <p>初始化测试数据，准备用于模拟的任务 DTO 和分页响应对象。</p>
     */
    @BeforeEach
    void setUp() {
        testJobDTO = JobDTO.builder()
                .id(1L)
                .jobName("数据同步任务")
                .jobGroup("DEFAULT")
                .invokeTarget("syncBean.execute")
                .cronExpression("0 0 2 * * ?")
                .status(1)
                .build();

        testPageResponse = PageResponse.of(List.of(testJobDTO), 1L, 1, 10);
    }

    /**
     * 测试正常分页查询请求。
     *
     * <p>验证传递正确的分页参数时，能够正确调用服务层并返回成功响应。</p>
     */
    @Test
    @DisplayName("正常分页查询 - 应返回200和分页数据")
    void testPageJobsSuccess() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "1")
                .queryParam("pageSize", "10")
                .build();

        when(jobService.pageJobs(any(JobQueryRequest.class))).thenReturn(Mono.just(testPageResponse));

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            200, response.statusCode().value(), "状态码应为 200");
                })
                .verifyComplete();

        verify(jobService).pageJobs(any(JobQueryRequest.class));
    }

    /**
     * 测试带查询条件的分页请求。
     *
     * <p>验证传递筛选条件时，能够正确解析参数并调用服务层。</p>
     */
    @Test
    @DisplayName("带查询条件的分页请求 - 应正确解析所有参数")
    void testPageJobsWithConditions() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "1")
                .queryParam("pageSize", "10")
                .queryParam("jobName", "数据同步")
                .queryParam("jobGroup", "DEFAULT")
                .queryParam("status", "1")
                .build();

        when(jobService.pageJobs(any(JobQueryRequest.class))).thenReturn(Mono.just(testPageResponse));

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            200, response.statusCode().value(), "状态码应为 200");
                })
                .verifyComplete();

        verify(jobService).pageJobs(any(JobQueryRequest.class));
    }

    /**
     * 测试缺少 pageNum 参数。
     *
     * <p>验证缺少必填的 pageNum 参数时，应返回业务异常。</p>
     */
    @Test
    @DisplayName("缺少 pageNum 参数 - 应返回参数错误异常")
    void testPageJobsWithoutPageNum() {
        MockServerRequest request =
                MockServerRequest.builder().queryParam("pageSize", "10").build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageNum"))
                .verify();
    }

    /**
     * 测试缺少 pageSize 参数。
     *
     * <p>验证缺少必填的 pageSize 参数时，应返回业务异常。</p>
     */
    @Test
    @DisplayName("缺少 pageSize 参数 - 应返回参数错误异常")
    void testPageJobsWithoutPageSize() {
        MockServerRequest request =
                MockServerRequest.builder().queryParam("pageNum", "1").build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageSize"))
                .verify();
    }

    /**
     * 测试 pageNum 为零。
     *
     * <p>验证 pageNum 小于 1 时，应返回参数错误异常。</p>
     */
    @Test
    @DisplayName("pageNum 为零 - 应返回参数错误异常")
    void testPageJobsWithZeroPageNum() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "0")
                .queryParam("pageSize", "10")
                .build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageNum"))
                .verify();
    }

    /**
     * 测试 pageNum 为负数。
     *
     * <p>验证 pageNum 为负数时，应返回参数错误异常。</p>
     */
    @Test
    @DisplayName("pageNum 为负数 - 应返回参数错误异常")
    void testPageJobsWithNegativePageNum() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "-1")
                .queryParam("pageSize", "10")
                .build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageNum"))
                .verify();
    }

    /**
     * 测试 pageSize 超出最大值。
     *
     * <p>验证 pageSize 超过 100 时，应返回参数错误异常。</p>
     */
    @Test
    @DisplayName("pageSize 超出最大值 - 应返回参数错误异常")
    void testPageJobsWithOversizedPageSize() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "1")
                .queryParam("pageSize", "101")
                .build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageSize"))
                .verify();
    }

    /**
     * 测试 pageSize 为零。
     *
     * <p>验证 pageSize 小于 1 时，应返回参数错误异常。</p>
     */
    @Test
    @DisplayName("pageSize 为零 - 应返回参数错误异常")
    void testPageJobsWithZeroPageSize() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "1")
                .queryParam("pageSize", "0")
                .build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageSize"))
                .verify();
    }

    /**
     * 测试参数格式错误。
     *
     * <p>验证传递非数字的参数时，应正确处理格式错误。</p>
     */
    @Test
    @DisplayName("参数格式错误 - 应返回参数错误异常")
    void testPageJobsWithInvalidFormat() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "abc")
                .queryParam("pageSize", "10")
                .build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageNum"))
                .verify();
    }

    /**
     * 测试空字符串参数。
     *
     * <p>验证传递空字符串的参数时，应视为缺少参数。</p>
     */
    @Test
    @DisplayName("空字符串参数 - 应返回参数错误异常")
    void testPageJobsWithEmptyStringParams() {
        MockServerRequest request = MockServerRequest.builder()
                .queryParam("pageNum", "")
                .queryParam("pageSize", "10")
                .build();

        Mono<ServerResponse> responseMono = jobHandler.page(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(
                        throwable -> throwable instanceof com.apex.scaffold.common.exception.BusinessException
                                && throwable.getMessage().contains("pageNum"))
                .verify();
    }
}
