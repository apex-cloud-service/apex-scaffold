package com.apex.scaffold.application.service.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.ChartDataResponse;
import com.apex.scaffold.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.application.dto.data.ChartRequest;
import com.apex.scaffold.application.dto.data.ChartResponse;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.ChartEntity;
import com.apex.scaffold.domain.repository.ChartRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChartServiceImpl 单元测试")
class ChartServiceImplTest {

    @Mock
    private ChartRepository chartRepository;

    @Mock
    private com.apex.scaffold.application.assembler.ChartAssembler chartAssembler;

    @InjectMocks
    private ChartServiceImpl chartService;

    private ChartEntity mockEntity;
    private ChartRequest mockRequest;
    private ChartResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockEntity = ChartEntity.create("销售趋势图", "line", "business", "月度销售趋势分析", "sales_db", 1L, "李四", "tenant-001");
        mockEntity.setId(1L);

        Map<String, Object> config = new HashMap<>();
        config.put("color", "blue");

        mockRequest = ChartRequest.builder()
                .chartName("销售趋势图")
                .chartType("line")
                .category("business")
                .description("月度销售趋势分析")
                .dataSource("sales_db")
                .config(config)
                .build();

        mockResponse = ChartResponse.builder()
                .id(1L)
                .chartName("销售趋势图")
                .chartType("line")
                .category("business")
                .description("月度销售趋势分析")
                .dataSource("sales_db")
                .config(config)
                .creatorId(1L)
                .creatorName("李四")
                .build();

        // Set tenant ID in ThreadLocal
        TenantContext.setTenantId("tenant-001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("创建图表")
    void testCreate() {
        when(chartRepository.save(any(ChartEntity.class))).thenReturn(Mono.just(mockEntity));
        when(chartAssembler.toResponse(any(ChartEntity.class))).thenReturn(mockResponse);

        Mono<ChartResponse> result = chartService
                .create(mockRequest)
                .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-001"));

        StepVerifier.create(result)
                .expectNextMatches(response -> response.getChartName().equals("销售趋势图")
                        && response.getChartType().equals("line"))
                .verifyComplete();

        verify(chartRepository, times(1)).save(any(ChartEntity.class));
        verify(chartAssembler, times(1)).toResponse(any(ChartEntity.class));
    }

    @Test
    @DisplayName("根据 ID 查询图表")
    void testGetById() {
        when(chartRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(chartAssembler.toResponse(any(ChartEntity.class))).thenReturn(mockResponse);

        Mono<ChartResponse> result = chartService.getById(1L);

        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getId().equals(1L) && response.getChartName().equals("销售趋势图"))
                .verifyComplete();

        verify(chartRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("查询不存在的图表应抛出异常")
    void testGetByIdNotFound() {
        when(chartRepository.findById(999L)).thenReturn(Mono.empty());

        Mono<ChartResponse> result = chartService.getById(999L);

        StepVerifier.create(result).expectError(BusinessException.class).verify();
    }

    @Test
    @DisplayName("更新图表")
    void testUpdate() {
        mockRequest.setChartName("更新后的图表");

        when(chartRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(chartRepository.save(any(ChartEntity.class))).thenReturn(Mono.just(mockEntity));
        when(chartAssembler.toResponse(any(ChartEntity.class))).thenReturn(mockResponse);

        Mono<ChartResponse> result = chartService
                .update(1L, mockRequest)
                .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-001"));

        StepVerifier.create(result)
                .expectNextMatches(response -> response.getId().equals(1L))
                .verifyComplete();

        verify(chartRepository, times(1)).findById(1L);
        verify(chartRepository, times(1)).save(any(ChartEntity.class));
        verify(chartAssembler, times(1)).toResponse(any(ChartEntity.class));
    }

    @Test
    @DisplayName("删除图表")
    void testDelete() {
        when(chartRepository.deleteById(1L, "tenant-001")).thenReturn(Mono.empty());

        Mono<Void> result = chartService
                .delete(1L)
                .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-001"));

        StepVerifier.create(result).verifyComplete();

        verify(chartRepository, times(1)).deleteById(eq(1L), anyString());
    }

    @Test
    @DisplayName("分页查询图表")
    void testQuery() {
        ChartQueryRequest queryRequest = ChartQueryRequest.builder()
                .keyword("销售")
                .pageNum(1)
                .pageSize(10)
                .build();

        when(chartRepository.findAll(anyString())).thenReturn(Flux.just(mockEntity));
        when(chartAssembler.toResponse(any(ChartEntity.class))).thenReturn(mockResponse);

        Mono<PageResponse<ChartResponse>> result = chartService
                .query(queryRequest)
                .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-001"));

        StepVerifier.create(result)
                .expectNextMatches(page -> page.getTotal() > 0 && page.getList().size() > 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("更新图表状态")
    void testUpdateStatus() {
        when(chartRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(chartRepository.save(any(ChartEntity.class))).thenReturn(Mono.just(mockEntity));

        Mono<Void> result = chartService
                .updateStatus(1L, 1)
                .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-001"));

        StepVerifier.create(result).verifyComplete();

        verify(chartRepository, times(1)).findById(1L);
        verify(chartRepository, times(1)).save(any(ChartEntity.class));
    }

    @Test
    @DisplayName("获取图表数据")
    void testGetChartData() {
        when(chartRepository.findById(1L)).thenReturn(Mono.just(mockEntity));

        Mono<ChartDataResponse> result = chartService.getChartData(1L);

        StepVerifier.create(result)
                .expectNextMatches(data -> data.getChartType().equals("line")
                        && data.getTitle().equals("销售趋势图")
                        && data.getSeries().size() > 0)
                .verifyComplete();

        verify(chartRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("增加查看次数")
    void testIncrementViewCount() {
        when(chartRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(chartRepository.save(any(ChartEntity.class))).thenReturn(Mono.just(mockEntity));

        Mono<Void> result = chartService.incrementViewCount(1L);

        StepVerifier.create(result).verifyComplete();

        verify(chartRepository, times(1)).findById(1L);
        verify(chartRepository, times(1)).save(any(ChartEntity.class));
    }
}
