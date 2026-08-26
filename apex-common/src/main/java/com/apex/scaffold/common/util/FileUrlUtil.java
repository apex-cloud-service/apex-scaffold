package com.apex.scaffold.common.util;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件 URL 工具类
 *
 * <p>提供文件 URL 解析功能，主要用于从存储服务（如 MinIO、火山引擎 TOS 等）的文件 URL 中提取存储桶名称和对象名称。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>解析文件 URL，提取存储桶名称</li>
 *   <li>解析文件 URL，提取对象名称（文件名）</li>
 *   <li>支持多种 URL 格式</li>
 * </ul>
 *
 * <h2>URL 格式要求</h2>
 * <p>URL 必须包含有效的对象路径。支持标准的 <code>/bucketName/objectName</code>、
 * 项目代理路径和火山 TOS 虚拟主机格式。</p>
 *
 * <h3>支持的 URL 格式示例</h3>
 * <ul>
 *   <li><code>https://storage.example.com/my-bucket/my-file.jpg</code></li>
 *   <li><code>http://localhost:9000/my-bucket/path/to/file.png</code></li>
 *   <li><code>/my-bucket/some-file.txt</code></li>
 *   <li><code>/local-storage/my-bucket/some-file.txt</code></li>
 *   <li><code>https://my-bucket.tos-cn-beijing.volces.com/some-file.txt</code></li>
 * </ul>
 *
 * <h3>解析规则</h3>
 * <ol>
 *   <li>从 URL 中提取路径部分</li>
 *   <li>按 <code>/</code> 分割路径</li>
 *   <li>过滤空字符串和空白字符串</li>
 *   <li>跳过可选的 <code>minio/tos/local-storage</code> 代理前缀</li>
 *   <li>提取存储桶名称和对象名称；TOS 虚拟主机格式从域名提取存储桶</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BucketAndObject
 */
@Slf4j
public final class FileUrlUtil {

    /** 最小路径部分数量，至少需要 bucketName/objectName 两部分 */
    private static final int MIN_PATH_PARTS = 2;

    /** 本项目本地或反向代理文件访问路径使用的固定前缀。 */
    private static final Set<String> STORAGE_PATH_PREFIXES = Set.of("minio", "tos", "local-storage");

    /** 火山引擎 TOS 虚拟主机域名中用于区分存储桶与地域的固定片段。 */
    private static final String TOS_VIRTUAL_HOST_MARKER = ".tos-";

    /**
     * 私有构造函数，防止实例化
     *
     * <p>此类为工具类，所有方法均为静态方法，不应被实例化。
     */
    private FileUrlUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 解析文件 URL，提取存储桶名称和对象名称
     *
     * <p>从给定的文件 URL 中提取存储桶名称和对象名称。
     * 如果 URL 为空、格式无效或路径不完整，则返回 <code>null</code>。
     *
     * <h3>输入输出示例</h3>
     * <pre>
     * 输入: "https://storage.example.com/my-bucket/my-file.jpg"
     * 输出: BucketAndObject{bucketName="my-bucket", objectName="my-file.jpg"}
     *
     * 输入: "https://storage.example.com/my-bucket/path/to/file.png"
     * 输出: BucketAndObject{bucketName="my-bucket", objectName="path/to/file.png"}
     * </pre>
     *
     * @param fileUrl 文件 URL，可以是完整的 HTTP/HTTPS URL 或相对路径
     * @return {@link BucketAndObject} 对象，包含存储桶名称和对象名称；如果解析失败则返回 &lt;code&gt;null&lt;/code&gt;
     */
    public static BucketAndObject parseBucketAndObject(String fileUrl) {
        // 参数校验：空值检查
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }

        try {
            // 解析 URI
            URI uri = URI.create(fileUrl);
            String path = uri.getPath();

            // 路径为空检查
            if (path == null || path.isBlank()) {
                return null;
            }

            // 分割路径并过滤空白
            List<String> parts = Arrays.stream(path.split("/"))
                    .filter(s -> s != null && !s.isBlank())
                    .toList();

            BucketAndObject tosObject = parseTosVirtualHost(uri, parts);
            if (tosObject != null) {
                return tosObject;
            }

            int bucketIndex = hasStoragePathPrefix(parts) ? 1 : 0;
            if (parts.size() >= bucketIndex + MIN_PATH_PARTS) {
                String bucket = parts.get(bucketIndex);
                String objectName = String.join("/", parts.subList(bucketIndex + 1, parts.size()));
                return new BucketAndObject(bucket, objectName);
            }

            return null;

        } catch (IllegalArgumentException e) {
            // 捕获具体的异常类型，记录警告日志后返回 null 表示解析失败
            log.warn("解析文件 URL 失败: {}", fileUrl, e);
            return null;
        }
    }

    /**
     * 判断路径首段是否为存储代理前缀（如 minio、tos、local-storage）。
     *
     * <p>当 URL 路径以 {@code /minio/}、{@code /tos/} 或 {@code /local-storage/} 开头时，
     * 首段为代理前缀而非存储桶名称，后续解析需跳过该段。
     *
     * @param parts URL 路径按 {@code /} 分割后的非空段列表
     * @return true-首段为存储代理前缀，false-首段为存储桶名称或其他
     */
    private static boolean hasStoragePathPrefix(List<String> parts) {
        return !parts.isEmpty() && STORAGE_PATH_PREFIXES.contains(parts.get(0).toLowerCase());
    }

    /**
     * 解析火山引擎 TOS 虚拟主机格式的 URL。
     *
     * <p>当 URL 主机名包含 {@code .tos-} 标记时，认定其为 TOS 虚拟主机格式
     * （如 {@code my-bucket.tos-cn-beijing.volces.com}），从主机名中提取存储桶名称，
     * 路径整体作为对象名称。
     *
     * @param uri   已解析的 URI 对象，用于提取主机名
     * @param parts URL 路径按 {@code /} 分割后的非空段列表
     * @return 解析出的存储桶和对象名称；非 TOS 虚拟主机格式或解析失败时返回 {@code null}
     */
    private static BucketAndObject parseTosVirtualHost(URI uri, List<String> parts) {
        String host = uri.getHost();
        if (parts.isEmpty() || host == null || !host.toLowerCase().contains(TOS_VIRTUAL_HOST_MARKER)) {
            return null;
        }
        int firstDot = host.indexOf('.');
        if (firstDot <= 0) {
            return null;
        }
        return new BucketAndObject(host.substring(0, firstDot), String.join("/", parts));
    }

    /**
     * 存储桶和对象名称的记录类
     *
     * <p>用于封装从文件 URL 中解析出的存储桶名称和对象名称。
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（文件名，包含路径时为完整路径）
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    public record BucketAndObject(
            /**
             * 存储桶名称
             *
             * <p>存储服务中存储桶的名称，用于标识文件存储的容器。
             */
            String bucketName,

            /**
             * 对象名称（文件名）
             *
             * <p>存储桶中的文件路径和名称，即对象在存储桶中的唯一标识。
             */
            String objectName) {

        /**
         * 创建 BucketAndObject 实例
         *
         * @param bucketName 存储桶名称，不能为空
         * @param objectName 对象名称，不能为空
         */
        public BucketAndObject {
            // 参数校验（可选，根据需求添加）
            if (bucketName == null || bucketName.isBlank()) {
                throw new IllegalArgumentException("bucketName 不能为空");
            }
            if (objectName == null || objectName.isBlank()) {
                throw new IllegalArgumentException("objectName 不能为空");
            }
        }
    }
}
