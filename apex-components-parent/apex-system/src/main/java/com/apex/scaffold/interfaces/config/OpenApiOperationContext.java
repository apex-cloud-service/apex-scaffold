package com.apex.scaffold.interfaces.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;

/**
 * 单个 OpenAPI 操作的文档增强上下文。
 *
 * <p>上下文把路径、操作标识、操作模型、共享组件和匿名访问属性封装为不可变数据，供不同
 * {@link OpenApiOperationContributor} 依次处理，避免扩展器之间通过可变成员变量传递状态。
 *
 * @param path 当前接口路径
 * @param operationId SpringDoc 生成的操作唯一标识，可能为 {@code null}
 * @param operation 当前待增强的 OpenAPI 操作
 * @param components OpenAPI 文档共享组件容器
 * @param publicPath 当前路径是否允许匿名访问
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
record OpenApiOperationContext(
        String path, String operationId, Operation operation, Components components, boolean publicPath) {}
