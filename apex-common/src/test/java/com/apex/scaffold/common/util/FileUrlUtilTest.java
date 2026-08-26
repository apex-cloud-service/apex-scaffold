package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FileUrlUtil 单元测试
 *
 * <p>测试文件URL解析功能，包括从各种格式的URL中提取存储桶名称和对象名称，
 * 以及BucketAndObject记录类的构造和访问器。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@DisplayName("FileUrlUtil 单元测试")
class FileUrlUtilTest {

    @Nested
    @DisplayName("parseBucketAndObject 解析URL测试")
    class ParseBucketAndObjectTests {

        /**
         * 执行 {@code shouldParseStandardHttpsUrl} 操作。
         */
        @Test
        @DisplayName("解析标准HTTPS URL应返回正确的bucket和object")
        void shouldParseStandardHttpsUrl() {
            String url = "https://storage.example.com/my-bucket/my-file.jpg";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("my-bucket");
            assertThat(result.objectName()).isEqualTo("my-file.jpg");
        }

        /**
         * 执行 {@code shouldParseHttpUrl} 操作。
         */
        @Test
        @DisplayName("解析HTTP URL应返回正确的bucket和object")
        void shouldParseHttpUrl() {
            String url = "http://localhost:9000/my-bucket/path/to/file.png";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("my-bucket");
            assertThat(result.objectName()).isEqualTo("path/to/file.png");
        }

        /**
         * 执行 {@code shouldParseRelativePath} 操作。
         */
        @Test
        @DisplayName("解析相对路径（以/开头）应返回正确结果")
        void shouldParseRelativePath() {
            String url = "/my-bucket/some-file.txt";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("my-bucket");
            assertThat(result.objectName()).isEqualTo("some-file.txt");
        }

        /**
         * 执行 {@code shouldParseUrlWithMultiLevelPath} 操作。
         */
        @Test
        @DisplayName("解析带多级路径的URL：取第一部分为bucket，剩余部分拼接为object")
        void shouldParseUrlWithMultiLevelPath() {
            String url = "https://storage.example.com/bucket-name/a/b/c/document.pdf";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("bucket-name");
            assertThat(result.objectName()).isEqualTo("a/b/c/document.pdf");
        }

        /**
         * 执行 {@code shouldParseLocalStorageUploadUrl} 操作。
         */
        @Test
        @DisplayName("解析本地存储上传返回路径时应跳过local-storage代理前缀")
        void shouldParseLocalStorageUploadUrl() {
            String url = "/local-storage/apex-files/2026/07/photo.jpg";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("apex-files");
            assertThat(result.objectName()).isEqualTo("2026/07/photo.jpg");
        }

        /**
         * 执行 {@code shouldParseMinioProxyUrl} 操作。
         */
        @Test
        @DisplayName("解析MinIO代理路径时应跳过minio前缀")
        void shouldParseMinioProxyUrl() {
            String url = "https://api.example.com/minio/test-files/uuid-photo.jpg";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("test-files");
            assertThat(result.objectName()).isEqualTo("uuid-photo.jpg");
        }

        /**
         * 执行 {@code shouldParseTosVirtualHostUrl} 操作。
         */
        @Test
        @DisplayName("解析火山TOS虚拟主机预签名URL应从域名提取bucket")
        void shouldParseTosVirtualHostUrl() {
            String url = "https://apex-files.tos-cn-beijing.volces.com/2026/07/uuid-photo.jpg?X-Tos-Signature=test";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("apex-files");
            assertThat(result.objectName()).isEqualTo("2026/07/uuid-photo.jpg");
        }

        /**
         * 执行 {@code shouldReturnNullForNullUrl} 操作。
         */
        @Test
        @DisplayName("解析null URL应返回null")
        void shouldReturnNullForNullUrl() {
            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(null);

            assertThat(result).isNull();
        }

        /**
         * 执行 {@code shouldReturnNullForEmptyUrl} 操作。
         */
        @Test
        @DisplayName("解析空字符串URL应返回null")
        void shouldReturnNullForEmptyUrl() {
            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject("");

            assertThat(result).isNull();
        }

        /**
         * 执行 {@code shouldReturnNullForBlankUrl} 操作。
         */
        @Test
        @DisplayName("解析空白字符串URL应返回null")
        void shouldReturnNullForBlankUrl() {
            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject("   ");

            assertThat(result).isNull();
        }

        /**
         * 执行 {@code shouldReturnNullForUrlWithoutPath} 操作。
         */
        @Test
        @DisplayName("解析无路径的URL（如纯域名）应返回null")
        void shouldReturnNullForUrlWithoutPath() {
            String url = "https://storage.example.com";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNull();
        }

        /**
         * 执行 {@code shouldReturnNullForUrlWithInsufficientPathParts} 操作。
         */
        @Test
        @DisplayName("解析路径部分不足2段的URL应返回null")
        void shouldReturnNullForUrlWithInsufficientPathParts() {
            String url = "https://storage.example.com/only-bucket";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNull();
        }

        /**
         * 执行 {@code shouldReturnNullForMalformedUrl} 操作。
         */
        @Test
        @DisplayName("解析格式严重错误的URL应返回null（不抛出异常）")
        void shouldReturnNullForMalformedUrl() {
            String url = "://::invalid-url::";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNull();
        }

        /**
         * 执行 {@code shouldFilterBlankPathSegments} 操作。
         */
        @Test
        @DisplayName("解析包含空路径段的URL应正确过滤空白段")
        void shouldFilterBlankPathSegments() {
            String url = "https://storage.example.com//my-bucket//my-file.jpg";

            FileUrlUtil.BucketAndObject result = FileUrlUtil.parseBucketAndObject(url);

            assertThat(result).isNotNull();
            assertThat(result.bucketName()).isEqualTo("my-bucket");
            assertThat(result.objectName()).isEqualTo("my-file.jpg");
        }
    }

    @Nested
    @DisplayName("BucketAndObject 记录类测试")
    class BucketAndObjectTests {

        /**
         * 执行 {@code shouldCreateBucketAndObjectWithValidParams} 操作。
         */
        @Test
        @DisplayName("正常构造BucketAndObject应正确设置属性")
        void shouldCreateBucketAndObjectWithValidParams() {
            FileUrlUtil.BucketAndObject bo = new FileUrlUtil.BucketAndObject("my-bucket", "path/to/file.txt");

            assertThat(bo.bucketName()).isEqualTo("my-bucket");
            assertThat(bo.objectName()).isEqualTo("path/to/file.txt");
        }

        /**
         * 执行 {@code shouldThrowExceptionForNullBucketName} 操作。
         */
        @Test
        @DisplayName("null bucketName应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullBucketName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FileUrlUtil.BucketAndObject(null, "file.txt"))
                    .withMessage("bucketName 不能为空");
        }

        /**
         * 执行 {@code shouldThrowExceptionForEmptyBucketName} 操作。
         */
        @Test
        @DisplayName("空字符串bucketName应抛出IllegalArgumentException")
        void shouldThrowExceptionForEmptyBucketName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FileUrlUtil.BucketAndObject("", "file.txt"))
                    .withMessage("bucketName 不能为空");
        }

        /**
         * 执行 {@code shouldThrowExceptionForBlankBucketName} 操作。
         */
        @Test
        @DisplayName("空白字符串bucketName应抛出IllegalArgumentException")
        void shouldThrowExceptionForBlankBucketName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FileUrlUtil.BucketAndObject("   ", "file.txt"))
                    .withMessage("bucketName 不能为空");
        }

        /**
         * 执行 {@code shouldThrowExceptionForNullObjectName} 操作。
         */
        @Test
        @DisplayName("null objectName应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullObjectName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FileUrlUtil.BucketAndObject("bucket", null))
                    .withMessage("objectName 不能为空");
        }

        /**
         * 执行 {@code shouldThrowExceptionForEmptyObjectName} 操作。
         */
        @Test
        @DisplayName("空字符串objectName应抛出IllegalArgumentException")
        void shouldThrowExceptionForEmptyObjectName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FileUrlUtil.BucketAndObject("bucket", ""))
                    .withMessage("objectName 不能为空");
        }

        /**
         * 执行 {@code shouldReturnFormattedToString} 操作。
         */
        @Test
        @DisplayName("toString应返回格式化的字符串")
        void shouldReturnFormattedToString() {
            FileUrlUtil.BucketAndObject bo = new FileUrlUtil.BucketAndObject("test-bucket", "test-object.txt");

            String str = bo.toString();

            assertThat(str).contains("bucketName=test-bucket");
            assertThat(str).contains("objectName=test-object.txt");
        }

        /**
         * 执行 {@code shouldHaveConsistentBucketNameAccessor} 操作。
         */
        @Test
        @DisplayName("bucketName访问器和构造函数参数应一致")
        void shouldHaveConsistentBucketNameAccessor() {
            String bucketName = "consistent-bucket";
            FileUrlUtil.BucketAndObject bo = new FileUrlUtil.BucketAndObject(bucketName, "file.txt");

            assertThat(bo.bucketName()).isSameAs(bucketName);
        }

        /**
         * 执行 {@code shouldHaveConsistentObjectNameAccessor} 操作。
         */
        @Test
        @DisplayName("objectName访问器和构造函数参数应一致")
        void shouldHaveConsistentObjectNameAccessor() {
            String objectName = "consistent-file.txt";
            FileUrlUtil.BucketAndObject bo = new FileUrlUtil.BucketAndObject("bucket", objectName);

            assertThat(bo.objectName()).isSameAs(objectName);
        }
    }
}
