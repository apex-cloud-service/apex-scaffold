package com.apex.scaffold.office.domain.document;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档类型枚举
 *
 * <p>定义办公平台支持的文档类型，包括在线文档、表格、演示文稿等。
 * 不同平台可能支持的文档类型有所差异。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum DocumentType {

    /** 在线文档（类似Word） */
    DOC("在线文档"),

    /** 在线表格（类似Excel） */
    SHEET("在线表格"),

    /** 在线演示（类似PPT） */
    SLIDE("在线演示"),

    /** 思维导图 */
    MINDMAP("思维导图"),

    /** 文件夹 */
    FOLDER("文件夹"),

    /** 通用文件 */
    FILE("通用文件");

    /** 文档类型说明 */
    private final String description;
}
