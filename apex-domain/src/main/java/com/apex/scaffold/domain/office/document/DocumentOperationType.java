package com.apex.scaffold.domain.office.document;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档操作类型枚举
 *
 * <p>定义文档操作的类型，包括创建、读取、更新、删除等操作。
 * 用于记录和控制用户对文档的操作权限。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum DocumentOperationType {

    /** 创建文档 */
    CREATE("创建"),

    /** 读取文档 */
    READ("读取"),

    /** 更新文档 */
    UPDATE("更新"),

    /** 删除文档 */
    DELETE("删除"),

    /** 移动文档 */
    MOVE("移动"),

    /** 复制文档 */
    COPY("复制"),

    /** 分享文档 */
    SHARE("分享"),

    /** 下载文档 */
    DOWNLOAD("下载"),

    /** 上传文档 */
    UPLOAD("上传");

    /** 操作说明 */
    private final String description;
}
