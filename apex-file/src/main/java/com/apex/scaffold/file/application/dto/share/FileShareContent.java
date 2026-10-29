package com.apex.scaffold.file.application.dto.share;

import java.io.InputStream;

/**
 * 分享文件内容。
 *
 * <p>调用方负责关闭 {@code content}；内容通过服务端代理读取，不向前端暴露可直接访问的
 * 存储地址。</p>
 *
 * @param fileName 下载文件名
 * @param contentType 内容类型
 * @param fileSize 文件大小（字节）
 * @param content 文件内容流
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
public record FileShareContent(String fileName, String contentType, Long fileSize, InputStream content) {}
