package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.application.dto.data.DocumentRequest;
import com.apex.scaffold.application.dto.data.DocumentResponse;
import reactor.core.publisher.Mono;

/**
 * 文档管理应用服务接口。
 *
 * <p>提供文档的业务逻辑处理，包括文档创建、更新、删除、查询、状态管理和计数统计。
 * 作为应用层服务接口，定义文档管理的核心业务契约，由实现类协调领域层和基础设施层完成业务逻辑。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>文档 CRUD：创建、更新、删除、按 ID 查询文档</li>
 *   <li>文档检索：支持按标题、文档类型、分类、作者等条件分页查询</li>
 *   <li>状态管理：更新文档状态（草稿 → 正常 → 归档）</li>
 *   <li>统计计数：浏览计数和下载计数的增量更新</li>
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
 * @see DocumentRequest
 * @see DocumentResponse
 * @see DocumentQueryRequest
 */
public interface DocumentService {

    Mono<DocumentResponse> create(DocumentRequest request);

    Mono<DocumentResponse> update(Long id, DocumentRequest request);

    Mono<Void> delete(Long id);

    Mono<DocumentResponse> getById(Long id);

    Mono<PageResponse<DocumentResponse>> query(DocumentQueryRequest request);

    Mono<Void> updateStatus(Long id, Integer status);

    Mono<Void> incrementViewCount(Long id);

    Mono<Void> incrementDownloadCount(Long id);
}
