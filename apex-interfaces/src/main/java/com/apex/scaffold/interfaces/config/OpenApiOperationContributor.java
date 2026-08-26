package com.apex.scaffold.interfaces.config;

/**
 * OpenAPI 操作文档贡献器。
 *
 * <p>每个贡献器只处理一组相互关联的文档信息。贡献器按注册顺序组成处理链，新文档规则可通过
 * Lambda 注册，不需要继续扩大主遍历方法。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@FunctionalInterface
interface OpenApiOperationContributor {

    /**
     * 增强单个 OpenAPI 操作。
     *
     * @param context 当前操作的完整文档上下文
     */
    void contribute(OpenApiOperationContext context);
}
