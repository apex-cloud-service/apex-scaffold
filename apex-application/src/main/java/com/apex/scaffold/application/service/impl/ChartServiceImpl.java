package com.apex.scaffold.application.service.impl;

import com.apex.scaffold.application.assembler.ChartAssembler;
import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.ChartDataResponse;
import com.apex.scaffold.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.application.dto.data.ChartRequest;
import com.apex.scaffold.application.dto.data.ChartResponse;
import com.apex.scaffold.application.service.ChartService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.ChartEntity;
import com.apex.scaffold.domain.repository.ChartRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 图表应用服务实现。
 *
 * <p>提供图表的创建、更新、删除、查询与数据生成能力，支持折线图、柱状图、饼图、
 * 散点图和雷达图等多种图表类型。图表数据为演示数据（随机生成），生产环境需接入
 * 真实数据源。</p>
 *
 * <h2>租户隔离</h2>
 * <p>所有图表操作强制租户隔离，更新与删除时校验租户权限。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ChartServiceImpl implements ChartService {

    private final ChartRepository chartRepository;
    private final ChartAssembler chartAssembler;
    private final Random random = new Random();

    /**
     * 创建图表
     *
     * <p>图表归属当前租户（取自 {@link TenantContext}），创建人固定为 system。
     *
     * @param request 图表创建请求（含图表名称、类型、分类、描述、数据源、配置与状态）
     * @return 新建图表的响应
     */
    @Override
    public Mono<ChartResponse> create(ChartRequest request) {
        return Mono.justOrEmpty(TenantContext.currentTenantId())
                .map(tenantId -> ChartEntity.create(
                        request.getChartName(),
                        request.getChartType(),
                        request.getCategory(),
                        request.getDescription(),
                        request.getDataSource(),
                        1L,
                        "system",
                        tenantId))
                .doOnNext(entity -> {
                    if (request.getConfig() != null) {
                        entity.setConfig(request.getConfig());
                    }
                    if (request.getStatus() != null) {
                        entity.setStatus(request.getStatus());
                    }
                })
                .flatMap(chartRepository::save)
                .map(chartAssembler::toResponse);
    }

    /**
     * 更新图表信息
     *
     * <p>校验图表归属当前租户后方可更新，请求中非空字段生效。
     *
     * @param id 图表 ID
     * @param request 图表更新请求
     * @return 更新后的图表响应；图表不存在或无租户权限时 Mono 以 BusinessException 错误终止
     */
    @Override
    public Mono<ChartResponse> update(Long id, ChartRequest request) {
        return Mono.justOrEmpty(TenantContext.currentTenantId()).flatMap(tenantId -> chartRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("图表不存在")))
                .flatMap(entity -> {
                    if (!entity.getTenantId().equals(tenantId)) {
                        return Mono.error(new BusinessException("无权限操作此图表"));
                    }
                    entity.updateInfo(
                            request.getChartName(),
                            request.getChartType(),
                            request.getCategory(),
                            request.getDescription(),
                            request.getDataSource(),
                            request.getConfig());
                    if (request.getStatus() != null) {
                        entity.setStatus(request.getStatus());
                    }
                    return chartRepository.save(entity);
                })
                .map(chartAssembler::toResponse));
    }

    /**
     * 删除图表
     *
     * <p>按当前租户隔离删除指定图表。
     *
     * @param id 图表 ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> delete(Long id) {
        return Mono.justOrEmpty(TenantContext.currentTenantId())
                .flatMap(tenantId -> chartRepository.deleteById(id, tenantId));
    }

    /**
     * 按 ID 查询图表详情
     *
     * @param id 图表 ID
     * @return 图表响应；图表不存在时 Mono 以 BusinessException 错误终止
     */
    @Override
    public Mono<ChartResponse> getById(Long id) {
        return chartRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("图表不存在")))
                .map(chartAssembler::toResponse);
    }

    /**
     * 分页查询图表列表
     *
     * <p>在当前租户范围内按关键词、图表类型、分类、状态条件过滤后内存分页。
     *
     * @param request 图表查询请求（含关键词、类型、分类、状态与分页参数）
     * @return 分页结果
     */
    @Override
    public Mono<PageResponse<ChartResponse>> query(ChartQueryRequest request) {
        return Mono.justOrEmpty(TenantContext.currentTenantId()).flatMap(tenantId -> chartRepository
                .findAll(tenantId)
                .filter(entity -> matchesFilter(entity, request))
                .collectList()
                .map(list -> {
                    int start = (request.getPageNum() - 1) * request.getPageSize();
                    int end = Math.min(start + request.getPageSize(), list.size());
                    List<ChartResponse> pageList = list.subList(start, end).stream()
                            .map(chartAssembler::toResponse)
                            .collect(Collectors.toList());
                    return PageResponse.of(pageList, (long) list.size(), request.getPageNum(), request.getPageSize());
                }));
    }

    /**
     * 更新图表状态
     *
     * <p>校验图表归属当前租户后方可更新状态。
     *
     * @param id 图表 ID
     * @param status 目标状态
     * @return 完成信号；图表不存在或无租户权限时 Mono 以 BusinessException 错误终止
     */
    @Override
    public Mono<Void> updateStatus(Long id, Integer status) {
        return Mono.justOrEmpty(TenantContext.currentTenantId()).flatMap(tenantId -> chartRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("图表不存在")))
                .flatMap(entity -> {
                    if (!entity.getTenantId().equals(tenantId)) {
                        return Mono.error(new BusinessException("无权限操作此图表"));
                    }
                    entity.setStatus(status);
                    return chartRepository.save(entity);
                })
                .then());
    }

    /**
     * 获取图表展示数据
     *
     * <p>当前数据为按图表类型随机生成的演示数据，生产环境需接入真实数据源。
     *
     * @param id 图表 ID
     * @return 图表数据（坐标轴与数据系列）；图表不存在时 Mono 以 BusinessException 错误终止
     */
    @Override
    public Mono<ChartDataResponse> getChartData(Long id) {
        return chartRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("图表不存在")))
                .map(entity -> generateChartData(entity.getChartType(), entity.getChartName()));
    }

    /**
     * 增加图表浏览计数
     *
     * <p>将指定图表的浏览计数加一并保存，图表不存在时直接完成。
     *
     * @param id 图表 ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> incrementViewCount(Long id) {
        return chartRepository
                .findById(id)
                .flatMap(entity -> {
                    entity.incrementViewCount();
                    return chartRepository.save(entity);
                })
                .then();
    }

    private boolean matchesFilter(ChartEntity entity, ChartQueryRequest request) {
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String keyword = request.getKeyword().toLowerCase();
            boolean nameMatch = entity.getChartName() != null
                    && entity.getChartName().toLowerCase().contains(keyword);
            boolean descMatch = entity.getDescription() != null
                    && entity.getDescription().toLowerCase().contains(keyword);
            if (!nameMatch && !descMatch) {
                return false;
            }
        }
        if (request.getChartType() != null && !request.getChartType().equals(entity.getChartType())) {
            return false;
        }
        if (request.getCategory() != null && !request.getCategory().equals(entity.getCategory())) {
            return false;
        }
        if (request.getStatus() != null && !request.getStatus().equals(entity.getStatus())) {
            return false;
        }
        return true;
    }

    private ChartDataResponse generateChartData(String chartType, String chartName) {
        List<String> xAxis = Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
        List<ChartDataResponse.ChartSeries> series = new ArrayList<>(16);

        switch (chartType) {
            case "line":
            case "bar":
                series.add(createSeries("Series 1", chartType, 7));
                series.add(createSeries("Series 2", chartType, 7));
                break;
            case "pie":
                xAxis = Arrays.asList("A", "B", "C", "D", "E");
                series.add(createSeries("Data", "pie", 5));
                break;
            case "scatter":
                series.add(createSeries("Series 1", "scatter", 7));
                break;
            case "radar":
                xAxis = Arrays.asList("Metric 1", "Metric 2", "Metric 3", "Metric 4", "Metric 5");
                series.add(createSeries("Series 1", "radar", 5));
                break;

            default:
                break;
        }

        return ChartDataResponse.builder()
                .chartId(null)
                .chartType(chartType)
                .title(chartName)
                .xAxis(xAxis)
                .series(series)
                .build();
    }

    private ChartDataResponse.ChartSeries createSeries(String name, String type, int count) {
        List<Object> data = new ArrayList<>(16);
        for (int i = 0; i < count; i++) {
            data.add(random.nextInt(100) + 50);
        }
        return ChartDataResponse.ChartSeries.builder()
                .name(name)
                .type(type)
                .data(data)
                .build();
    }
}
