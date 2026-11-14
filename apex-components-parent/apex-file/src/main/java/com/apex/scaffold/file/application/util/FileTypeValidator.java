package com.apex.scaffold.file.application.util;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件类型验证器
 *
 * <p>基于文件头魔数（Magic Number）验证文件真实类型，防止通过修改文件扩展名
 * 绕过类型校验的安全风险。
 *
 * <h2>设计说明</h2>
 * <p>每种文件格式在文件开头都有特定的字节序列标识（魔数），通过读取并比对
 * 这些字节序列可以准确识别文件的真实格式，不受扩展名影响。
 *
 * <h2>职责范围</h2>
 * <table border="1">
 *   <caption>常见图片格式魔数对照表</caption>
 *   <tr><th>格式</th><th>魔数（十六进制）</th><th>说明</th></tr>
 *   <tr><td>JPEG</td><td>FF D8 FF</td><td>JPEG 图像文件</td></tr>
 *   <tr><td>PNG</td><td>89 50 4E 47 0D 0A 1A 0A</td><td>便携式网络图形</td></tr>
 *   <tr><td>GIF</td><td>47 49 46 38</td><td>GIF87a 或 GIF89a</td></tr>
 *   <tr><td>BMP</td><td>42 4D</td><td>Windows 位图</td></tr>
 *   <tr><td>WEBP</td><td>52 49 46 46 ... 57 45 42 50</td><td>WebP 图像格式</td></tr>
 *   <tr><td>HEIC</td><td>00 00 00 20 66 74 79 70</td><td>高效图像格式</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 验证上传的图片文件
 * InputStream inputStream = ...;
 * String expectedType = "image/jpeg";
 * FileTypeValidator.validateImage(inputStream, expectedType);
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class FileTypeValidator {

    private static final int MAGIC_NUMBER_MAP_CAPACITY = 11;
    /** 文件类型魔数映射表：扩展名 -> 魔数列表 */
    private static final Map<String, List<byte[]>> MAGIC_NUMBER_MAP = new HashMap<>(MAGIC_NUMBER_MAP_CAPACITY);

    private static final int MIN_HEADER_LENGTH_FOR_MAGIC_CHECK = 4;
    private static final int MIN_HEADER_LENGTH_FOR_WEBP = 12;
    private static final String EXTENSION_WEBP = "webp";

    /** 支持的最大魔数长度（字节） */
    private static final int MAX_MAGIC_NUMBER_LENGTH = 12;

    static {
        // JPEG: FF D8 FF
        MAGIC_NUMBER_MAP.put("jpg", List.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        MAGIC_NUMBER_MAP.put("jpeg", List.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        MAGIC_NUMBER_MAP.put("png", List.of(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));

        // GIF: 47 49 46 38 (GIF8)
        MAGIC_NUMBER_MAP.put("gif", List.of(new byte[] {0x47, 0x49, 0x46, 0x38}));

        // BMP: 42 4D
        MAGIC_NUMBER_MAP.put("bmp", List.of(new byte[] {0x42, 0x4D}));

        // WEBP: 52 49 46 46 ... 57 45 42 50 (RIFF....WEBP)
        MAGIC_NUMBER_MAP.put("webp", List.of(new byte[] {0x52, 0x49, 0x46, 0x46}, new byte[] {0x57, 0x45, 0x42, 0x50}));

        // HEIC: 00 00 00 20 66 74 79 70 (....ftyp)
        MAGIC_NUMBER_MAP.put("heic", List.of(new byte[] {0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}));
        MAGIC_NUMBER_MAP.put("heif", List.of(new byte[] {0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}));
    }

    /**
     * 验证图片文件类型
     *
     * <p>读取输入流的前 N 个字节，与文件扩展名对应的魔数进行比对，
     * 验证文件真实类型是否与声明的扩展名一致。
     *
     * @param inputStream 文件输入流
     * @param fileName 文件名（用于提取扩展名）
     * @throws BusinessException 文件类型不匹配或读取失败时抛出
     */
    public static void validateImage(InputStream inputStream, String fileName) {
        String extension = extractExtension(fileName).toLowerCase();
        List<byte[]> expectedMagicNumbers = MAGIC_NUMBER_MAP.get(extension);

        if (expectedMagicNumbers == null) {
            log.warn("不支持的文件类型：{}", extension);
            throw new BusinessException(ResultCode.FILE_TYPE_UNSUPPORTED);
        }

        try {
            byte[] fileHeader = readFileHeader(inputStream);
            if (!matchesMagicNumber(fileHeader, expectedMagicNumbers, extension)) {
                log.warn("文件类型不匹配，声明类型：{}, 实际魔数：{}", extension, bytesToHex(fileHeader));
                throw new BusinessException(ResultCode.FILE_TYPE_MISMATCH);
            }
            log.debug("文件类型验证通过，扩展名：{}", extension);
        } catch (IOException e) {
            log.error("读取文件头失败", e);
            throw new BusinessException(ResultCode.STORAGE_ERROR);
        }
    }

    /**
     * 读取文件头魔数
     *
     * <p>从输入流中读取前 MAX_MAGIC_NUMBER_LENGTH 个字节。
     * 读取后会自动重置流位置以便后续使用。
     *
     * @param inputStream 文件输入流
     * @return 文件头字节数组
     * @throws IOException 读取失败时抛出
     */
    private static byte[] readFileHeader(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[MAX_MAGIC_NUMBER_LENGTH];
        int bytesRead = inputStream.read(buffer);
        inputStream.reset();
        return Arrays.copyOf(buffer, bytesRead > 0 ? bytesRead : 0);
    }

    /**
     * 匹配魔数
     *
     * <p>对于某些格式（如 WEBP）需要检查多个位置的魔数。
     *
     * @param fileHeader 文件头字节
     * @param magicNumbers 期望的魔数列表
     * @param extension 文件扩展名
     * @return 是否匹配成功
     */
    private static boolean matchesMagicNumber(byte[] fileHeader, List<byte[]> magicNumbers, String extension) {
        if (fileHeader.length < MIN_HEADER_LENGTH_FOR_MAGIC_CHECK) {
            return false;
        }

        if (EXTENSION_WEBP.equals(extension)) {
            return matchesWebP(fileHeader);
        }

        // 其他格式：检查是否匹配任一魔数
        for (byte[] magic : magicNumbers) {
            if (startsWith(fileHeader, magic)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查文件头是否以指定魔数开头
     *
     * @param fileHeader 文件头
     * @param magic 魔数
     * @return 是否匹配
     */
    private static boolean startsWith(byte[] fileHeader, byte[] magic) {
        if (fileHeader.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (fileHeader[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * WEBP 格式特殊匹配
     *
     * <p>WEBP 文件格式为 RIFF 容器，需要检查：
     * <ul>
     *   <li>开头 4 字节：RIFF (52 49 46 46)</li>
     *   <li>第 8-11 字节：WEBP (57 45 42 50)</li>
     * </ul>
     *
     * @param fileHeader 文件头
     * @return 是否为 WEBP 格式
     */
    private static boolean matchesWebP(byte[] fileHeader) {
        if (fileHeader.length < MIN_HEADER_LENGTH_FOR_WEBP) {
            return false;
        }

        // 检查 RIFF 标识
        boolean hasRiff =
                fileHeader[0] == 0x52 && fileHeader[1] == 0x49 && fileHeader[2] == 0x46 && fileHeader[3] == 0x46;

        // 检查 WEBP 标识（位置 8-11）
        boolean hasWebp =
                fileHeader[8] == 0x57 && fileHeader[9] == 0x45 && fileHeader[10] == 0x42 && fileHeader[11] == 0x50;

        return hasRiff && hasWebp;
    }

    /**
     * 提取文件扩展名
     *
     * @param fileName 文件名
     * @return 扩展名（不含点）
     */
    private static String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * 检查文件类型是否支持
     *
     * <p>根据文件扩展名检查是否在支持的类型列表中。
     *
     * @param fileName 文件名
     * @return 是否支持
     */
    public static boolean isSupported(String fileName) {
        String extension = extractExtension(fileName).toLowerCase();
        return MAGIC_NUMBER_MAP.containsKey(extension);
    }

    /**
     * 获取所有支持的图片格式扩展名
     *
     * @return 支持的扩展名集合
     */
    public static Set<String> getSupportedExtensions() {
        return MAGIC_NUMBER_MAP.keySet();
    }
}
