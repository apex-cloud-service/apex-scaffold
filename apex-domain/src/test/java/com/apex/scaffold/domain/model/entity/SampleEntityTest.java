package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SampleEntity} 的单元测试类
 *
 * <p>测试采样的创建（二选一、四选二）、裁切区域设置、人脸类型设置等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class SampleEntityTest {

    @Nested
    @DisplayName("静态工厂方法 createTwoChoiceSample()")
    class CreateTwoChoiceSampleTest {

        /**
         * 执行 {@code withValidParamsShouldCreateTwoChoiceSample} 操作。
         */
        @Test
        @DisplayName("使用有效参数应创建二选一样本并设置正确初始状态")
        void withValidParamsShouldCreateTwoChoiceSample() {
            Long userId = 1L;
            Long photoId = 100L;
            String bucketName = "photos";
            String fileName = "user1.jpg";

            SampleEntity sample = SampleEntity.createTwoChoiceSample(userId, photoId, bucketName, fileName);

            assertThat(sample.getUserId()).isEqualTo(userId);
            assertThat(sample.getPhotoId()).isEqualTo(photoId);
            assertThat(sample.getBucketName()).isEqualTo(bucketName);
            assertThat(sample.getFileName()).isEqualTo(fileName);
            assertThat(sample.getMode()).isEqualTo(SampleEntity.SampleMode.TWO_CHOICE.getCode());
            assertThat(sample.getFaceType()).isNull();
            assertThat(sample.getCropX()).isNull();
            assertThat(sample.getCropY()).isNull();
            assertThat(sample.getCropW()).isNull();
            assertThat(sample.getCropH()).isNull();
            assertThat(sample.getCreateTime()).isNotNull();
            assertThat(sample.getUpdateTime()).isNotNull();
            assertThat(sample.isDeleted()).isFalse();
        }

        /**
         * 创建{@code timeShouldEqualUpdateTime}。
         */
        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            assertThat(sample.getCreateTime()).isEqualTo(sample.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("静态工厂方法 createFourPickTwoSample()")
    class CreateFourPickTwoSampleTest {

        /**
         * 执行 {@code withValidParamsShouldCreateFourPickTwoSample} 操作。
         */
        @Test
        @DisplayName("使用有效参数应创建四选二样本并设置正确初始状态")
        void withValidParamsShouldCreateFourPickTwoSample() {
            Long userId = 2L;
            Long photoId = 200L;
            String bucketName = "photos";
            String fileName = "user2.jpg";

            SampleEntity sample = SampleEntity.createFourPickTwoSample(userId, photoId, bucketName, fileName);

            assertThat(sample.getUserId()).isEqualTo(userId);
            assertThat(sample.getPhotoId()).isEqualTo(photoId);
            assertThat(sample.getBucketName()).isEqualTo(bucketName);
            assertThat(sample.getFileName()).isEqualTo(fileName);
            assertThat(sample.getMode()).isEqualTo(SampleEntity.SampleMode.FOUR_PICK_TWO.getCode());
            assertThat(sample.getCreateTime()).isNotNull();
            assertThat(sample.getUpdateTime()).isNotNull();
            assertThat(sample.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("设置裁切区域 setCropRegion()")
    class SetCropRegionTest {

        /**
         * 执行 {@code shouldSetCropRegionCoordinates} 操作。
         */
        @Test
        @DisplayName("应正确设置裁切区域的四个坐标值")
        void shouldSetCropRegionCoordinates() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            sample.setCropRegion(10, 20, 200, 300);

            assertThat(sample.getCropX()).isEqualTo(10);
            assertThat(sample.getCropY()).isEqualTo(20);
            assertThat(sample.getCropW()).isEqualTo(200);
            assertThat(sample.getCropH()).isEqualTo(300);
        }

        /**
         * 执行 {@code shouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            var originalUpdateTime = sample.getUpdateTime();
            sample.setCropRegion(0, 0, 100, 100);
            assertThat(sample.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }

        /**
         * 执行 {@code shouldSupportZeroCoordinates} 操作。
         */
        @Test
        @DisplayName("支持零值裁切坐标")
        void shouldSupportZeroCoordinates() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            sample.setCropRegion(0, 0, 100, 100);
            assertThat(sample.getCropX()).isEqualTo(0);
            assertThat(sample.getCropY()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("设置人脸类型 setFaceTypeValue()")
    class SetFaceTypeValueTest {

        /**
         * 执行 {@code shouldSetFaceTypeValue} 操作。
         */
        @Test
        @DisplayName("应正确设置人脸类型值")
        void shouldSetFaceTypeValue() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            sample.setFaceTypeValue(SampleEntity.FaceType.CLOSE_UP.getCode());

            assertThat(sample.getFaceType()).isEqualTo(SampleEntity.FaceType.CLOSE_UP.getCode());
        }

        /**
         * 执行 {@code shouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            var originalUpdateTime = sample.getUpdateTime();
            sample.setFaceTypeValue(SampleEntity.FaceType.ATMOSPHERE.getCode());
            assertThat(sample.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }

        /**
         * 执行 {@code shouldSupportCloseUpType} 操作。
         */
        @Test
        @DisplayName("应支持 CLOSE_UP 类型")
        void shouldSupportCloseUpType() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            sample.setFaceTypeValue(SampleEntity.FaceType.CLOSE_UP.getCode());
            assertThat(sample.getFaceType()).isEqualTo("CLOSE_UP");
        }

        /**
         * 执行 {@code shouldSupportAtmosphereType} 操作。
         */
        @Test
        @DisplayName("应支持 ATMOSPHERE 类型")
        void shouldSupportAtmosphereType() {
            SampleEntity sample = SampleEntity.createTwoChoiceSample(1L, 100L, "bucket", "file.jpg");
            sample.setFaceTypeValue(SampleEntity.FaceType.ATMOSPHERE.getCode());
            assertThat(sample.getFaceType()).isEqualTo("ATMOSPHERE");
        }
    }

    @Nested
    @DisplayName("setter 方法")
    class SetterTest {

        /**
         * 设置{@code tersShouldUpdateFields}。
         */
        @Test
        @DisplayName("使用 setter 设置属性应正确更新")
        void settersShouldUpdateFields() {
            SampleEntity sample = new SampleEntity();
            sample.setUserId(10L);
            sample.setPhotoId(20L);
            sample.setBucketName("my-bucket");
            sample.setFileName("photo.png");
            sample.setMode(SampleEntity.SampleMode.FOUR_PICK_TWO.getCode());
            sample.setFaceType(SampleEntity.FaceType.CLOSE_UP.getCode());
            sample.setCropX(50);
            sample.setCropY(60);
            sample.setCropW(150);
            sample.setCropH(200);

            assertThat(sample.getUserId()).isEqualTo(10L);
            assertThat(sample.getPhotoId()).isEqualTo(20L);
            assertThat(sample.getBucketName()).isEqualTo("my-bucket");
            assertThat(sample.getFileName()).isEqualTo("photo.png");
            assertThat(sample.getMode()).isEqualTo(SampleEntity.SampleMode.FOUR_PICK_TWO.getCode());
            assertThat(sample.getFaceType()).isEqualTo(SampleEntity.FaceType.CLOSE_UP.getCode());
            assertThat(sample.getCropX()).isEqualTo(50);
            assertThat(sample.getCropY()).isEqualTo(60);
            assertThat(sample.getCropW()).isEqualTo(150);
            assertThat(sample.getCropH()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("SampleMode 枚举")
    class SampleModeEnumTest {

        /**
         * 执行 {@code twoChoiceCodeShouldBeTwoChoice} 操作。
         */
        @Test
        @DisplayName("TWO_CHOICE 的 code 应为 TWO_CHOICE")
        void twoChoiceCodeShouldBeTwoChoice() {
            assertThat(SampleEntity.SampleMode.TWO_CHOICE.getCode()).isEqualTo("TWO_CHOICE");
        }

        /**
         * 执行 {@code fourPickTwoCodeShouldBeFourPickTwo} 操作。
         */
        @Test
        @DisplayName("FOUR_PICK_TWO 的 code 应为 FOUR_PICK_TWO")
        void fourPickTwoCodeShouldBeFourPickTwo() {
            assertThat(SampleEntity.SampleMode.FOUR_PICK_TWO.getCode()).isEqualTo("FOUR_PICK_TWO");
        }
    }

    @Nested
    @DisplayName("FaceType 枚举")
    class FaceTypeEnumTest {

        /**
         * 执行 {@code closeUpCodeShouldBeCloseUp} 操作。
         */
        @Test
        @DisplayName("CLOSE_UP 的 code 应为 CLOSE_UP")
        void closeUpCodeShouldBeCloseUp() {
            assertThat(SampleEntity.FaceType.CLOSE_UP.getCode()).isEqualTo("CLOSE_UP");
        }

        /**
         * 执行 {@code atmosphereCodeShouldBeAtmosphere} 操作。
         */
        @Test
        @DisplayName("ATMOSPHERE 的 code 应为 ATMOSPHERE")
        void atmosphereCodeShouldBeAtmosphere() {
            assertThat(SampleEntity.FaceType.ATMOSPHERE.getCode()).isEqualTo("ATMOSPHERE");
        }
    }
}
