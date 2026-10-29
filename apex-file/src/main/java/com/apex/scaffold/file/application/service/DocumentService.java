package com.apex.scaffold.file.application.service;

import com.apex.scaffold.file.application.dto.PageResponse;
import com.apex.scaffold.file.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.file.application.dto.data.DocumentRequest;
import com.apex.scaffold.file.application.dto.data.DocumentResponse;
import reactor.core.publisher.Mono;

/**
 * 文档管理应用服务接口。
 *
 * <p>提供文档的业务逻辑处理，包括文档创建、更新、删除、查询、状态管理和计数统计。
 * 作为应用层服务接口，定义文档管理的核心业务契约，由实现类协调领域层和基础设施层完成业务逻辑。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>文档 CRUD：创建、更新、删除、按 ID 查询文档</li>
 *   <li>文档检索：支持按标题、文档类型、分类、作者等条件分页查询</li>
 *   <li>状态管理：更新文档状态（草稿 → 正常 → 归档）</li>
 *   <li>统计计数：浏览计数和下载计数的增量更新</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>应用服务模式：封装业务用例，协调多个领域对象和基础设施服务</li>
 *   <li>响应式编程：所有方法返回 Mono，支持非阻塞的异步处理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DocumentRequest
 * @see DocumentResponse
 * @see DocumentQueryRequest
 */
public interface DocumentService {

    /**
     *
     *
     * 创建文档。
     *
     *
     *
     *
     *
     * @param request 文档创建请求
     *
     *
     * @return 创建的文档
     *
     *
     */
    Mono<DocumentResponse> create(DocumentRequest request);

    /**
     *
     *
     * 更新文档。
     *
     *
     *
     *
     *
     * @param id 文档ID
     *
     *
     * @param request 文档更新请求
     *
     *
     * @return 更新后的文档
     *
     *
     */
    Mono<DocumentResponse> update(Long id, DocumentRequest request);

    /**
     *
     *
     * 删除文档。
     *
     *
     *
     *
     *
     * @param id 文档ID
     */
    Mono<Void> delete(Long id);

    /**
     * 根据ID查询文档。
     *
     * @param id 文档ID
     * @return 文档详情
     */
    Mono<DocumentResponse> getById(Long id);

    /**
     *
     *
     * 查询文档列表。
     *
     *
     *
     *
     *
     * @param request 查询请求
     *
     *
     * @return 文档列表
     *
     *
     */
    Mono<PageResponse<DocumentResponse>> query(DocumentQueryRequest request);

    /**
     *
     *
     * 更新文档状态。
     *
     *
     *
     *
     *
     * @param id 文档ID
     *
     *
     * @param status 新状态
     */
    Mono<Void> updateStatus(Long id, Integer status);

    /**
     *
     *
     * 增加文档查看次数。
     *
     *
     *
     *
     *
     * @param id 文档ID
     */
    Mono<Void> incrementViewCount(Long id);

    /**
     * 增加文档下载次数。
     *
     * @param id 文档ID
     */
    Mono<Void> incrementDownloadCount(Long id);
}
