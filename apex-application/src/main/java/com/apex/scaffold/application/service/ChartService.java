package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.ChartDataResponse;
import com.apex.scaffold.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.application.dto.data.ChartRequest;
import com.apex.scaffold.application.dto.data.ChartResponse;
import reactor.core.publisher.Mono;

/**
 * 图表管理应用服务接口。
 *
 * <p>提供图表的业务逻辑处理，包括图表创建、更新、删除、查询、状态管理、数据获取和浏览计数。
 * 作为应用层服务接口，定义图表管理的核心业务契约，由实现类协调领域层和基础设施层完成业务逻辑。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>图表 CRUD：创建、更新、删除、按 ID 查询图表</li>
 *   <li>图表检索：支持按图表名称、类型、分类等条件分页查询</li>
 *   <li>状态管理：更新图表状态（草稿 → 正常 → 归档）</li>
 *   <li>数据获取：获取图表展示所需的数据（时间序列、统计值、数据点等）</li>
 *   <li>统计计数：浏览计数的增量更新</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>应用服务模式：封装业务用例，协调多个领域对象和基础设施服务</li>
 *   <li>响应式编程：所有方法返回 Mono，支持非阻塞的异步处理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChartRequest
 * @see ChartResponse
 * @see ChartDataResponse
 * @see ChartQueryRequest
 */
public interface ChartService {

    Mono<ChartResponse> create(ChartRequest request);

    Mono<ChartResponse> update(Long id, ChartRequest request);

    Mono<Void> delete(Long id);

    Mono<ChartResponse> getById(Long id);

    Mono<PageResponse<ChartResponse>> query(ChartQueryRequest request);

    Mono<Void> updateStatus(Long id, Integer status);

    Mono<ChartDataResponse> getChartData(Long id);

    Mono<Void> incrementViewCount(Long id);
}
