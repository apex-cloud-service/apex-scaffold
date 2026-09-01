package com.apex.scaffold.infrastructure.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/**
 * 百度图片预处理器单元测试。
 *
 * @author weiqiang.ling
 */
class BaiduImagePreprocessorTest {

    /**
     * 验证 {@code keepsImageWhenItAlreadyFitsBaiduLimit} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void keepsImageWhenItAlreadyFitsBaiduLimit() throws Exception {
        byte[] original = png(256, 256, false);
        var preprocessor = new BaiduImagePreprocessor(500_000, 4096, 0.88F);

        byte[] prepared = preprocessor.prepare(new ByteArrayResource(original));

        assertThat(prepared).isEqualTo(original);
    }

    /**
     * 验证 {@code compressesLargeImageBeforeBase64Submission} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void compressesLargeImageBeforeBase64Submission() throws Exception {
        byte[] original = png(900, 900, true);
        var preprocessor = new BaiduImagePreprocessor(120_000, 4096, 0.88F);

        byte[] prepared = preprocessor.prepare(new ByteArrayResource(original));

        assertThat(prepared.length).isLessThan(original.length);
        assertThat(Base64.getEncoder().encode(prepared)).hasSizeLessThanOrEqualTo(120_000);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(prepared))).isNotNull();
    }

    /**
     * 验证 {@code rejectsImageWhoseShortestEdgeIsTooSmall} 场景。
     *
     * @throws Exception 执行失败时
     */
    @Test
    void rejectsImageWhoseShortestEdgeIsTooSmall() throws Exception {
        byte[] original = png(127, 256, false);
        var preprocessor = new BaiduImagePreprocessor(500_000, 4096, 0.88F);

        assertThatThrownBy(() -> preprocessor.prepare(new ByteArrayResource(original)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最短边");
    }

    /**
     * 生成指定尺寸的 PNG 图片字节数组。
     *
     * @param width  图片宽度
     * @param height 图片高度
     * @param noise  是否填充随机噪点像素
     * @return PNG 图片字节数组
     * @throws Exception 图片编码失败时抛出
     */
    private byte[] png(int width, int height, boolean noise) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = noise ? random.nextInt(0x1000000) : 0x336699;
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
