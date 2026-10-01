package com.apex.scaffold.common.annotation;

import java.lang.annotation.*;

/**
 * 资源级权限控制注解
 *
 * <p>用于方法级别的资源访问权限检查。支持SpEL表达式动态解析资源ID和类型。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 检查文档读权限
 * {@code @RequireResourcePermission(resourceType = "document", resourceIdParam = "docId", permission = "READ")}
 * public Mono&lt;Document&gt; getDocument(String docId) { ... }
 *
 * // 检查项目写权限
 * {@code @RequireResourcePermission(resourceType = "project", resourceIdParam = "projectId", permission = "WRITE")}
 * public Mono&lt;Void&gt; updateProject(String projectId, ProjectDTO dto) { ... }
 *
 * // 使用SpEL表达式从请求体解析资源ID
 * {@code @RequireResourcePermission(resourceType = "dataset", resourceIdExpression = "#request.datasetId", permission = "DELETE")}
 * public Mono&lt;Void&gt; deleteDataset(@RequestBody DeleteRequest request) { ... }
 * </pre>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireResourcePermission {

    /**
     * 资源类型（document/project/dataset等）
     *
     * @return 资源类型
     */
    String resourceType();

    /**
     * 资源ID参数名称（从方法参数获取）
     *
     * <p>例如：{@code resourceIdParam = "docId"} 会从方法参数中查找名为docId的参数</p>
     *
     * @return 参数名称
     */
    String resourceIdParam() default "";

    /**
     * 资源ID的SpEL表达式（支持复杂表达式）
     *
     * <p>例如：</p>
     * <ul>
     *   <li>{@code resourceIdExpression = "#request.id"} - 从请求体获取</li>
     *   <li>{@code resourceIdExpression = "#id"} - 从参数获取</li>
     *   <li>{@code resourceIdExpression = "T(com.example.Utils).extractId(#param)"} - 调用静态方法</li>
     * </ul>
     *
     * @return SpEL表达式
     */
    String resourceIdExpression() default "";

    /**
     * 所需的权限（READ/WRITE/DELETE/MANAGE）
     *
     * @return 权限名称
     */
    String permission();

    /**
     * 权限检查失败时的错误消息
     *
     * @return 错误消息
     */
    String errorMessage() default "无权访问该资源";
}
