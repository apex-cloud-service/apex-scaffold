package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link FileEntity} 的单元测试类
 *
 * <p>测试文件实体的 getter/setter 方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class FileEntityTest extends BaseEntityTest<FileEntity> {

    /** {@inheritDoc} */
    @Override
    protected FileEntity createEntity() {
        return new FileEntity();
    }

    @Nested
    @DisplayName("setter 和 getter 方法")
    class SetterGetterTest {

        /**
         * 设置{@code tersShouldUpdateFieldsAndGettersShouldReturnCorrectValues}。
         */
        @Test
        @DisplayName("使用 setter 设置属性后 getter 应正确返回")
        void settersShouldUpdateFieldsAndGettersShouldReturnCorrectValues() {
            FileEntity file = new FileEntity();
            file.setFileName("photo_2024.jpg");
            file.setOriginalName("我的照片.jpg");
            file.setFilePath("/uploads/photos/");
            file.setFileUrl("https://cdn.example.com/photos/photo_2024.jpg");
            file.setFileSize(2048000L);
            file.setFileType("IMAGE");
            file.setContentType("image/jpeg");
            file.setBucketName("user-photos");
            file.setMd5("d41d8cd98f00b204e9800998ecf8427e");
            file.setUserId(100L);

            assertThat(file.getFileName()).isEqualTo("photo_2024.jpg");
            assertThat(file.getOriginalName()).isEqualTo("我的照片.jpg");
            assertThat(file.getFilePath()).isEqualTo("/uploads/photos/");
            assertThat(file.getFileUrl()).isEqualTo("https://cdn.example.com/photos/photo_2024.jpg");
            assertThat(file.getFileSize()).isEqualTo(2048000L);
            assertThat(file.getFileType()).isEqualTo("IMAGE");
            assertThat(file.getContentType()).isEqualTo("image/jpeg");
            assertThat(file.getBucketName()).isEqualTo("user-photos");
            assertThat(file.getMd5()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
            assertThat(file.getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            FileEntity file = new FileEntity();
            assertThat(file.getFileName()).isNull();
            assertThat(file.getOriginalName()).isNull();
            assertThat(file.getFilePath()).isNull();
            assertThat(file.getFileUrl()).isNull();
            assertThat(file.getFileSize()).isNull();
            assertThat(file.getFileType()).isNull();
            assertThat(file.getContentType()).isNull();
            assertThat(file.getBucketName()).isNull();
            assertThat(file.getMd5()).isNull();
            assertThat(file.getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {
        @Test
        @DisplayName("上传图片文件记录")
        void shouldRecordImageFileUpload() {
            FileEntity file = new FileEntity();
            file.setFileName("avatar_001.jpg");
            file.setOriginalName("avatar.jpg");
            file.setFileSize(102400L);
            file.setFileType("IMAGE");
            file.setContentType("image/jpeg");
            file.setUserId(1L);

            assertThat(file.getFileType()).isEqualTo("IMAGE");
            assertThat(file.getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("上传视频文件记录")
        void shouldRecordVideoFileUpload() {
            FileEntity file = new FileEntity();
            file.setFileName("video_001.mp4");
            file.setFileSize(10485760L);
            file.setFileType("VIDEO");
            file.setContentType("video/mp4");

            assertThat(file.getFileType()).isEqualTo("VIDEO");
            assertThat(file.getContentType()).isEqualTo("video/mp4");
        }

        @Test
        @DisplayName("支持 MD5 校验值")
        void shouldSupportMd5Value() {
            FileEntity file = new FileEntity();
            String md5 = "abc123def456";
            file.setMd5(md5);
            assertThat(file.getMd5()).isEqualTo(md5);
        }
    }
}
