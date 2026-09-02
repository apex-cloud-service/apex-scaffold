package com.apex.scaffold.infrastructure.moderation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.core.io.Resource;

/**
 * 在提交百度云前约束尺寸，并将超出 Base64 限制的图片缩放压缩为 JPEG。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
final class BaiduImagePreprocessor {

    static {
        // 服务端只做离屏图像处理，禁止 macOS/Linux 尝试初始化桌面图形环境。
        System.setProperty("java.awt.headless", "true");
    }

    private static final int MIN_DIMENSION = 128;
    private static final long MAX_PIXELS = 36_000_000L;
    private static final float MIN_JPEG_QUALITY = 0.55F;

    private final int maxBase64Bytes;
    private final int maxDimension;
    private final float initialJpegQuality;

    /**
     * 创建 {@link BaiduImagePreprocessor} 实例。
     *
     * @param maxBase64Bytes      Base64 编码最大字节数
     * @param maxDimension        图片最大边长（像素）
     * @param initialJpegQuality  JPEG 初始压缩质量（0~1）
     */
    BaiduImagePreprocessor(int maxBase64Bytes, int maxDimension, float initialJpegQuality) {
        if (maxBase64Bytes <= 0
                || maxDimension < MIN_DIMENSION
                || initialJpegQuality < MIN_JPEG_QUALITY
                || initialJpegQuality > 1.0F) {
            throw new IllegalArgumentException("百度云图片预处理配置不合法");
        }
        this.maxBase64Bytes = maxBase64Bytes;
        this.maxDimension = maxDimension;
        this.initialJpegQuality = initialJpegQuality;
    }

    /**
     * 预处理图片以符合百度云审核要求。
     *
     * <p>读取图片字节后识别其格式；若尺寸或 Base64 大小不超过限制则原样返回，
     * 否则解码后压缩为 JPEG。</p>
     *
     * @param resource 待审核的图片资源
     * @return 符合百度云大小与尺寸限制的图片字节
     * @throws Exception 图片读取、解码或压缩失败时抛出
     */
    byte[] prepare(Resource resource) throws Exception {
        byte[] original;
        try (var input = resource.getInputStream()) {
            original = input.readAllBytes();
        }
        if (original.length == 0) {
            throw new IllegalArgumentException("待审核图片不能为空");
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            if (imageInput == null) {
                throw new IllegalArgumentException("无法读取待审核图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                if (base64Length(original.length) <= maxBase64Bytes) {
                    return original;
                }
                throw new IllegalArgumentException("图片超过百度云大小限制且当前格式无法压缩");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                if (width <= maxDimension
                        && height <= maxDimension
                        && base64Length(original.length) <= maxBase64Bytes) {
                    return original;
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new IllegalArgumentException("无法解码需要压缩的图片");
                }
                return compress(decoded);
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * 压缩图片至满足百度云大小限制。
     *
     * <p>先按最长边等比缩放，再以递减的 JPEG 质量尝试编码；若仍未达标，则继续
     * 缩小尺寸后重试，直至命中大小限制或达到下限。</p>
     *
     * @param source 原始解码后的图片
     * @return 压缩后的 JPEG 字节数组
     * @throws Exception 图片宽高比例异常或压缩后仍超出限制时抛出
     */
    private byte[] compress(BufferedImage source) throws Exception {
        double dimensionScale = Math.min(
                1.0D, Math.min((double) maxDimension / source.getWidth(), (double) maxDimension / source.getHeight()));
        int width = (int) Math.round(source.getWidth() * dimensionScale);
        int height = (int) Math.round(source.getHeight() * dimensionScale);
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            throw new IllegalArgumentException("图片宽高比例过大，缩放后最短边不足 " + MIN_DIMENSION + " 像素");
        }

        for (int resizeAttempt = 0; resizeAttempt < 8; resizeAttempt++) {
            BufferedImage rgb = resizeToRgb(source, width, height);
            for (float quality = initialJpegQuality; quality >= MIN_JPEG_QUALITY; quality -= 0.08F) {
                byte[] encoded = encodeJpeg(rgb, quality);
                if (base64Length(encoded.length) <= maxBase64Bytes) {
                    return encoded;
                }
            }
            int nextWidth = (int) Math.floor(width * 0.82D);
            int nextHeight = (int) Math.floor(height * 0.82D);
            if (nextWidth < MIN_DIMENSION || nextHeight < MIN_DIMENSION) {
                break;
            }
            width = nextWidth;
            height = nextHeight;
        }
        throw new IllegalArgumentException("图片压缩后仍超过百度云大小限制");
    }

    /**
     * 将图片缩放至目标尺寸并转换为 RGB 色彩空间。
     *
     * @param source 源图片
     * @param width  目标宽度
     * @param height 目标高度
     * @return 缩放并转换后的图片
     */
    private BufferedImage resizeToRgb(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * 将图片按指定质量编码为 JPEG 字节。
     *
     * @param image   待编码的图片
     * @param quality JPEG 压缩质量（0~1）
     * @return JPEG 编码结果
     * @throws Exception 当前 JVM 不支持 JPEG 编码或编码失败时抛出
     */
    private byte[] encodeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前 JVM 不支持 JPEG 编码");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * 校验图片尺寸符合百度云审核要求。
     *
     * @param width  图片宽度
     * @param height 图片高度
     * @throws IllegalArgumentException 最短边过小或像素总量超出上限时抛出
     */
    private void validateDimensions(int width, int height) {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            throw new IllegalArgumentException("图片最短边不能小于 " + MIN_DIMENSION + " 像素");
        }
        if ((long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("图片像素总量不能超过 3600 万");
        }
    }

    /**
     * 按字节长度估算 Base64 编码后的字符长度。
     *
     * @param byteLength 原始字节长度
     * @return Base64 编码后的长度
     */
    private long base64Length(long byteLength) {
        return ((byteLength + 2L) / 3L) * 4L;
    }
}
