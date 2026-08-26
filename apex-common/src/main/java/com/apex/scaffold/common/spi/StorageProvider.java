package com.apex.scaffold.common.spi;

import java.io.InputStream;
import reactor.core.publisher.Mono;

/**
 * 存储提供者服务提供者接口(SPI)。
 *
 * <p>该接口定义了对象存储服务的基本操作规范,允许通过Java标准的ServiceLoader机制
 * 动态加载不同的存储后端实现(如MinIO、火山云TOS、阿里云OSS等)。</p>
 *
 * <h2>SPI使用方式</h2>
 * <pre>{@code
 * // 加载所有可用的存储提供者
 * ServiceLoader<StorageProvider> loader = ServiceLoader.load(StorageProvider.class);
 * for (StorageProvider provider : loader) {
 *     System.out.println("发现存储提供者: " + provider.getProviderName());
 * }
 *
 * // 根据名称获取特定提供者
 * StorageProvider minioProvider = loader.stream()
 *     .filter(p -> p.get().getProviderName().equals("minio"))
 *     .findFirst()
 *     .map(ServiceLoader.Provider::get)
 *     .orElse(null);
 * }</pre>
 *
 * <h2>实现要求</h2>
 * <ul>
 *   <li>每个实现类必须提供无参构造函数</li>
 *   <li>必须在 {@code META-INF/services/com.apex.scaffold.common.spi.StorageProvider} 文件中注册</li>
 *   <li>建议实现线程安全,因为可能被多个线程并发调用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.0.0
 * @see java.util.ServiceLoader
 */
public interface StorageProvider {

    /**
     * 获取存储提供者名称。
     *
     * <p>返回该存储提供者的唯一标识名称,用于区分不同的存储后端实现。
     * 名称应为小写字母,不含空格或特殊字符。</p>
     *
     * <h2>示例返回值</h2>
     * <ul>
     *   <li>"minio" - MinIO对象存储</li>
     *   <li>"tos" - 火山引擎TOS</li>
     *   <li>"oss" - 阿里云OSS</li>
     *   <li>"s3" - AWS S3兼容存储</li>
     * </ul>
     *
     * @return 存储提供者名称,不能为null或空字符串
     */
    String getProviderName();

    /**
     * 初始化存储提供者。
     *
     * <p>在存储提供者被加载后调用此方法进行初始化,可以在此方法中建立连接池、
     * 验证配置参数等。如果初始化失败,应抛出IllegalStateException。</p>
     *
     * <p>该方法只会被调用一次,在首次使用该提供者之前。</p>
     *
     * @throws IllegalStateException 当初始化失败时抛出,包含详细的错误信息
     */
    void initialize();

    /**
     * 上传文件到对象存储。
     *
     * <p>将输入流中的文件数据上传到指定存储桶,支持设置Content-Type头。
     * 上传成功后返回对象的完整名称(含路径)。</p>
     *
     * <p>覆盖上传:若目标路径已存在同名文件,将被新文件覆盖。
     * 如需防止覆盖,调用方应在调用前使用 {@link #exists} 检查。</p>
     *
     * @param bucketName  存储桶名称,不能为null或空
     * @param objectName  对象名称(含路径,如 "photos/2024/abc123.jpg"),不能为null或空
     * @param inputStream 文件输入流,调用方负责关闭
     * @param contentType 内容类型(如 "image/jpeg"、"image/png"),可为null
     * @return 上传成功返回对象的完整名称;失败返回错误信号
     * @throws IllegalArgumentException 当bucketName或objectName为空时抛出
     */
    Mono<String> upload(String bucketName, String objectName, InputStream inputStream, String contentType);

    /**
     * 从对象存储下载文件。
     *
     * <p>根据存储桶名称和对象名称获取文件的输入流。
     * 调用方负责在使用完毕后关闭输入流。</p>
     *
     * @param bucketName 存储桶名称,不能为null或空
     * @param objectName 对象名称(含路径),不能为null或空
     * @return 文件输入流;若文件不存在返回空信号
     * @throws IllegalArgumentException 当bucketName或objectName为空时抛出
     */
    Mono<InputStream> download(String bucketName, String objectName);

    /**
     * 从对象存储删除文件。
     *
     * <p>根据存储桶名称和对象名称删除指定的文件。
     * 删除不可逆,调用前应确保文件不再需要。</p>
     *
     * @param bucketName 存储桶名称,不能为null或空
     * @param objectName 对象名称(含路径),不能为null或空
     * @return true表示删除成功;false表示文件不存在或删除失败
     * @throws IllegalArgumentException 当bucketName或objectName为空时抛出
     */
    Mono<Boolean> delete(String bucketName, String objectName);

    /**
     * 获取文件的预签名访问URL。
     *
     * <p>生成带有临时签名参数的访问URL,允许未认证的外部客户端
     * 在指定时间内直接访问私有存储桶中的文件。
     * 适用于前端直接展示照片、下载文件等场景,
     * 避免将文件流经后端服务器中转造成带宽浪费。</p>
     *
     * @param bucketName 存储桶名称,不能为null或空
     * @param objectName 对象名称(含路径),不能为null或空
     * @param expiry     过期时间(秒),建议值:300(5分钟) ~ 86400(1天)
     * @return 预签名URL字符串;生成失败返回空信号
     * @throws IllegalArgumentException 当bucketName或objectName为空时抛出
     */
    Mono<String> getPresignedUrl(String bucketName, String objectName, Integer expiry);

    /**
     * 检查文件是否存在。
     *
     * <p>查询指定存储桶中是否存在指定路径的文件。
     * 常用于上传前的去重检查或删除前的存在性验证。</p>
     *
     * @param bucketName 存储桶名称,不能为null或空
     * @param objectName 对象名称(含路径),不能为null或空
     * @return true表示文件存在;false表示文件不存在
     * @throws IllegalArgumentException 当bucketName或objectName为空时抛出
     */
    Mono<Boolean> exists(String bucketName, String objectName);

    /**
     * 获取照片公开访问 URL 前缀。
     *
     * <p>每种存储实现返回各自的照片 URL 前缀,应用层通过此方法获得正确的 URL 格式,
     * 无需感知具体存储类型。前端通过此前缀拼接对象键即可得到完整的照片访问地址。</p>
     *
     * <h2>各实现返回示例</h2>
     * <table>
     *   <tr><th>存储类型</th><th>返回值</th><th>完整 URL 示例</th></tr>
     *   <tr><td>MINIO</td><td>/minio/apex</td><td>/minio/apex/AF/a.jpg</td></tr>
     *   <tr><td>TOS</td><td>/tos/apex-photo</td><td>/tos/apex-photo/AF/a.jpg</td></tr>
     *   <tr><td>OSS</td><td>/oss/apex-bucket</td><td>/oss/apex-bucket/AF/a.jpg</td></tr>
     * </table>
     *
     * @return 照片访问 URL 前缀(不含末尾斜杠),不能为null
     */
    String getPhotoUrlPrefix();

    /**
     * 销毁存储提供者资源。
     *
     * <p>在应用关闭或存储提供者不再使用时调用此方法释放资源,
     * 如关闭连接池、清理缓存等。</p>
     *
     * <p>该方法只会被调用一次,在应用生命周期结束时。</p>
     */
    void destroy();
}
