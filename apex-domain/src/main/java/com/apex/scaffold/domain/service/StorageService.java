package com.apex.scaffold.domain.service;

import java.io.InputStream;
import reactor.core.publisher.Mono;

/**
 * 对象存储服务接口。
 *
 * <p>定义对象存储（OSS/S3/MinIO等）的上传、下载、删除、
 * 预签名URL生成及文件存在性检查等核心操作规范。由基础设施层的
 * {@code AbstractStorageService} 子类提供具体实现，
 * 支持多种云存储厂商的适配接入。</p>
 *
 * <h2>支持的存储类型</h2>
 * <ul>
 *   <li>本地存储 - 默认实现，适用于单机部署</li>
 *   <li>MinIO - 开源对象存储，可显式启用</li>
 *   <li>阿里云OSS - 通过 {@code AliOssStorageService} 适配</li>
 *   <li>火山引擎TOS - 通过 {@code VolcEngineStorageService} 适配</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <p>通过 {@link StorageServiceProvider} 工厂类根据配置的存储类型
 * 获取对应的 {@link StorageService} 实现，业务层无需关注具体存储后端。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see StorageServiceProvider
 */
public interface StorageService {

    /**
     * 上传文件到对象存储。
     *
     * <p>将输入流中的文件数据上传到指定存储桶，支持设置Content-Type头。
     * 上传成功后返回对象的完整名称（含路径）。</p>
     *
     * <p>覆盖上传：若目标路径已存在同名文件，将被新文件覆盖。
     * 如需防止覆盖，调用方应在调用前使用 {@link #exists} 检查。</p>
     *
     * @param bucketName  存储桶名称
     * @param objectName  对象名称（含路径，如 "photos/2024/abc123.jpg"）
     * @param inputStream 文件输入流，调用方负责关闭
     * @param contentType 内容类型（如 "image/jpeg"、"image/png"）
     * @return 上传成功返回对象的完整名称；失败返回错误信号
     */
    Mono<String> upload(String bucketName, String objectName, InputStream inputStream, String contentType);

    /**
     * 从对象存储下载文件。
     *
     * <p>根据存储桶名称和对象名称获取文件的输入流。
     * 调用方负责在使用完毕后关闭输入流。</p>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（含路径）
     * @return 文件输入流；若文件不存在返回空信号
     */
    Mono<InputStream> download(String bucketName, String objectName);

    /**
     * 从对象存储删除文件。
     *
     * <p>根据存储桶名称和对象名称删除指定的文件。
     * 删除不可逆，调用前应确保文件不再需要。</p>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（含路径）
     * @return true表示删除成功；false表示文件不存在或删除失败
     */
    Mono<Boolean> delete(String bucketName, String objectName);

    /**
     * 获取文件的预签名访问URL。
     *
     * <p>生成带有临时签名参数的访问URL，允许未认证的外部客户端
     * 在指定时间内直接访问私有存储桶中的文件。
     * 适用于前端直接展示照片、下载文件等场景，
     * 避免将文件流经后端服务器中转造成带宽浪费。</p>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（含路径）
     * @param expiry     过期时间（秒），建议值：300（5分钟）~ 86400（1天）
     * @return 预签名URL字符串；生成失败返回空信号
     */
    Mono<String> getPresignedUrl(String bucketName, String objectName, Integer expiry);

    /**
     * 检查文件是否存在。
     *
     * <p>查询指定存储桶中是否存在指定路径的文件。
     * 常用于上传前的去重检查或删除前的存在性验证。</p>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（含路径）
     * @return true表示文件存在；false表示文件不存在
     */
    Mono<Boolean> exists(String bucketName, String objectName);

    /**
     * 获取照片公开访问 URL 前缀。
     *
     * <p>策略模式核心方法：每种存储实现返回各自的照片 URL 前缀，
     * 应用层通过此方法获得正确的 URL 格式，无需感知具体存储类型。
     * 前端通过此前缀拼接由 {@code VotePhotoPathResolver} 生成的
     * 对象键（如 {@code AF/a.jpg}）即可得到完整的照片访问地址。</p>
     *
     * <h2>各实现返回示例</h2>
     * <table>
     *   <tr><th>存储类型</th><th>返回值</th><th>完整 URL 示例</th></tr>
     *   <tr><td>LOCAL</td><td>{@code /uploads/apex}</td><td>{@code /uploads/apex/AF/a.jpg}</td></tr>
     *   <tr><td>MINIO</td><td>{@code /minio/apex}</td><td>{@code /minio/apex/AF/a.jpg}</td></tr>
     *   <tr><td>VOLCENGINE</td><td>{@code /tos/apex-photo}</td><td>{@code /tos/apex-photo/AF/a.jpg}</td></tr>
     * </table>
     *
     * @return 照片访问 URL 前缀（不含末尾斜杠）
     */
    String getPhotoUrlPrefix();
}
