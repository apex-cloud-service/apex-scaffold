package com.apex.scaffold.file.application.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FileTypeValidator 魔数校验单元测试")
class FileTypeValidatorTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 2, 3, 4};
    private static final byte[] BMP = {0x42, 0x4D, 1, 2, 3, 4, 5, 6};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    private static final byte[] HEIC = {0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, 1, 2, 3, 4};

    private static ByteArrayInputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    @Test
    @DisplayName("validateImage：合法 JPEG 魔数应通过")
    void validateImage_validJpg() {
        FileTypeValidator.validateImage(stream(JPG), "photo.jpg");
        FileTypeValidator.validateImage(stream(JPG), "photo.jpeg");
    }

    @Test
    @DisplayName("validateImage：合法 PNG 魔数应通过")
    void validateImage_validPng() {
        FileTypeValidator.validateImage(stream(PNG), "photo.png");
    }

    @Test
    @DisplayName("validateImage：合法 GIF/BMP/WEBP/HEIC 魔数应通过")
    void validateImage_validOthers() {
        FileTypeValidator.validateImage(stream(GIF), "a.gif");
        FileTypeValidator.validateImage(stream(BMP), "a.bmp");
        FileTypeValidator.validateImage(stream(WEBP), "a.webp");
        FileTypeValidator.validateImage(stream(HEIC), "a.heic");
        FileTypeValidator.validateImage(stream(HEIC), "a.heif");
    }

    @Test
    @DisplayName("validateImage：扩展名大写应被规范化处理")
    void validateImage_upperCaseExtension() {
        FileTypeValidator.validateImage(stream(PNG), "PHOTO.PNG");
    }

    @Test
    @DisplayName("validateImage：不支持的扩展名应抛出 FILE_TYPE_UNSUPPORTED")
    void validateImage_unsupportedExtension() {
        BusinessException ex = catchThrowableOfType(
                () -> FileTypeValidator.validateImage(stream(PNG), "a.txt"), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.FILE_TYPE_UNSUPPORTED.getCode());
    }

    @Test
    @DisplayName("validateImage：无扩展名或点号结尾应视为不支持")
    void validateImage_noExtension() {
        BusinessException ex1 = catchThrowableOfType(
                () -> FileTypeValidator.validateImage(stream(PNG), "noext"), BusinessException.class);
        assertThat(ex1.getCode()).isEqualTo(ResultCode.FILE_TYPE_UNSUPPORTED.getCode());

        BusinessException ex2 = catchThrowableOfType(
                () -> FileTypeValidator.validateImage(stream(PNG), "trailing."), BusinessException.class);
        assertThat(ex2.getCode()).isEqualTo(ResultCode.FILE_TYPE_UNSUPPORTED.getCode());
    }

    @Test
    @DisplayName("validateImage：魔数与声明扩展名不一致应抛出 FILE_TYPE_MISMATCH")
    void validateImage_magicMismatch() {
        BusinessException ex = catchThrowableOfType(
                () -> FileTypeValidator.validateImage(stream(PNG), "fake.jpg"), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.FILE_TYPE_MISMATCH.getCode());
    }

    @Test
    @DisplayName("validateImage：文件头过短应判定为不匹配")
    void validateImage_tooShortHeader() {
        BusinessException ex = catchThrowableOfType(
                () -> FileTypeValidator.validateImage(stream(new byte[] {1, 2}), "a.jpg"), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.FILE_TYPE_MISMATCH.getCode());
    }

    @Test
    @DisplayName("validateImage：WEBP 文件头不足 12 字节应判定为不匹配")
    void validateImage_webpTooShort() {
        byte[] shortWebp = {0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4, 5, 6};
        BusinessException ex = catchThrowableOfType(
                () -> FileTypeValidator.validateImage(stream(shortWebp), "a.webp"), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.FILE_TYPE_MISMATCH.getCode());
    }

    @Test
    @DisplayName("validateImage：读取文件头失败应抛出 STORAGE_ERROR")
    void validateImage_readFailure() {
        InputStream throwing = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("boom");
            }
        };
        BusinessException ex =
                catchThrowableOfType(() -> FileTypeValidator.validateImage(throwing, "a.jpg"), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.STORAGE_ERROR.getCode());
    }

    @Test
    @DisplayName("isSupported：应正确判断扩展名是否受支持")
    void isSupported() {
        assertThat(FileTypeValidator.isSupported("a.png")).isTrue();
        assertThat(FileTypeValidator.isSupported("a.JPG")).isTrue();
        assertThat(FileTypeValidator.isSupported("a.txt")).isFalse();
        assertThat(FileTypeValidator.isSupported("noext")).isFalse();
    }

    @Test
    @DisplayName("getSupportedExtensions：应包含全部支持的图片扩展名")
    void getSupportedExtensions() {
        assertThat(FileTypeValidator.getSupportedExtensions())
                .contains("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif");
    }
}
