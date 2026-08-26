package com.apex.scaffold.application.util;

import static org.junit.jupiter.api.Assertions.*;

import com.apex.scaffold.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * 文件类型验证器测试类
 *
 * <p>测试 {@link FileTypeValidator} 的魔数检测功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class FileTypeValidatorTest {

    /**
     * 测试 JPEG 格式文件验证
     */
    @Test
    void testValidateImage_JPEG() {
        // JPEG 文件头魔数：FF D8 FF
        byte[] jpegHeader =
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                };
        InputStream inputStream = new ByteArrayInputStream(jpegHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateImage(inputStream, "test.jpg"));
    }

    /**
     * 测试 PNG 格式文件验证
     */
    @Test
    void testValidateImage_PNG() {
        // PNG 文件头魔数：89 50 4E 47 0D 0A 1A 0A
        byte[] pngHeader = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(pngHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateImage(inputStream, "test.png"));
    }

    /**
     * 测试 GIF 格式文件验证
     */
    @Test
    void testValidateImage_GIF() {
        // GIF 文件头魔数：47 49 46 38
        byte[] gifHeader = new byte[] {0x47, 0x49, 0x46, 0x38, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(gifHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateImage(inputStream, "test.gif"));
    }

    /**
     * 测试 BMP 格式文件验证
     */
    @Test
    void testValidateImage_BMP() {
        // BMP 文件头魔数：42 4D
        byte[] bmpHeader = new byte[] {0x42, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(bmpHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateImage(inputStream, "test.bmp"));
    }

    /**
     * 测试 WEBP 格式文件验证
     */
    @Test
    void testValidateImage_WEBP() {
        // WEBP 文件头：RIFF....WEBP
        byte[] webpHeader = new byte[] {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
        InputStream inputStream = new ByteArrayInputStream(webpHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateImage(inputStream, "test.webp"));
    }

    /**
     * 测试 HEIC 格式文件验证
     */
    @Test
    void testValidateImage_HEIC() {
        // HEIC 文件头魔数：00 00 00 20 66 74 79 70
        byte[] heicHeader = new byte[] {0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(heicHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateImage(inputStream, "test.heic"));
    }

    /**
     * 测试文件类型不匹配情况
     */
    @Test
    void testValidateImage_Mismatch() {
        // 使用 PNG 文件头但声明为 JPEG
        byte[] pngHeader = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(pngHeader);

        assertThrows(BusinessException.class, () -> FileTypeValidator.validateImage(inputStream, "test.jpg"));
    }

    /**
     * 测试不支持的文件类型
     */
    @Test
    void testValidateImage_UnsupportedType() {
        byte[] data = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(data);

        assertThrows(BusinessException.class, () -> FileTypeValidator.validateImage(inputStream, "test.exe"));
    }

    /**
     * 测试支持的扩展名列表
     */
    @Test
    void testGetSupportedExtensions() {
        var extensions = FileTypeValidator.getSupportedExtensions();

        assertNotNull(extensions);
        assertTrue(extensions.contains("jpg"));
        assertTrue(extensions.contains("jpeg"));
        assertTrue(extensions.contains("png"));
        assertTrue(extensions.contains("gif"));
        assertTrue(extensions.contains("bmp"));
        assertTrue(extensions.contains("webp"));
        assertTrue(extensions.contains("heic"));
        assertTrue(extensions.contains("heif"));
    }

    /**
     * 测试文件类型支持检查
     */
    @Test
    void testIsSupported() {
        assertTrue(FileTypeValidator.isSupported("test.jpg"));
        assertTrue(FileTypeValidator.isSupported("test.png"));
        assertTrue(FileTypeValidator.isSupported("test.gif"));
        assertFalse(FileTypeValidator.isSupported("test.exe"));
        assertFalse(FileTypeValidator.isSupported("test.txt"));
    }
}
