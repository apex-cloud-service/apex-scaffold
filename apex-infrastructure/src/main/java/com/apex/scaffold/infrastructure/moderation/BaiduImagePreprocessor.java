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
 * @author weiqiang.ling
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
     * @param maxBase64Bytes maxBase64Bytes 参数
     *
     * @param maxDimension maxDimension 参数
     *
     * @param initialJpegQuality initialJpegQuality 参数
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
     * 执行 {@code prepare} 逻辑。
     *
     * @param resource resource 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
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
     * 执行 {@code compress} 逻辑。
     *
     * @param source source 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
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
     * 执行 {@code resizeToRgb} 逻辑。
     *
     * @param source source 参数
     *
     * @param width width 参数
     *
     * @param height height 参数
     *
     * @return 执行结果
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
     * 执行 {@code encodeJpeg} 逻辑。
     *
     * @param image image 参数
     *
     * @param quality quality 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
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
     * 执行 {@code validateDimensions} 逻辑。
     *
     * @param width width 参数
     *
     * @param height height 参数
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
     * 执行 {@code base64Length} 逻辑。
     *
     * @param byteLength byteLength 参数
     *
     * @return 执行结果
     */
    private long base64Length(long byteLength) {
        return ((byteLength + 2L) / 3L) * 4L;
    }
}
