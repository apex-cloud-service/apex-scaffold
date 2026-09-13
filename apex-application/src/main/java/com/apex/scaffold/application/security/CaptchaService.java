package com.apex.scaffold.application.security;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * 图形验证码应用服务。
 *
 * <p>提供验证码的生成与一次性校验能力：生成时绘制包含随机字符的 PNG
 * 图片并以 Base64 返回，答案写入 Redis（{@code apex:captcha:*}）并设
 * 置有效期；校验时按 {@code captchaId} 读取答案比对，校验后立即删除
 * （一次性消费），防止重放。</p>
 *
 * <h2>防爆破设计</h2>
 * <ul>
 *   <li>验证码 ID 使用 UUID，无法预测</li>
 *   <li>校验忽略大小写，容忍 OCR 识别误差</li>
 *   <li>每次校验（无论成败）均删除验证码，杜绝重放攻击</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
/**n * CaptchaServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class CaptchaService {

    /**
     * 验证码缓存键前缀。
     */
    private static final String CAPTCHA_KEY_PREFIX = "apex:captcha:";

    /**
     * 验证码图片宽度（像素）。
     */
    private static final int IMAGE_WIDTH = 130;

    /**
     * 验证码图片高度（像素）。
     */
    private static final int IMAGE_HEIGHT = 48;

    /**
     * 验证码字符集（排除易混淆的 0/O/1/I/l）。
     */
    private static final String CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /**
     * 验证码配置属性。
     */
    private final CaptchaProperties properties;

    /**
     * 响应式 Redis 模板，用于存取验证码答案。
     */
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 安全随机数生成器。
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * 生成验证码。
     *
     * <p>生成随机字符并绘制为 PNG 图片，答案写入 Redis。
     * 验证码功能被禁用时抛出 {@code PARAM_ERROR}。</p>
     *
     * @return 验证码响应（ID + Base64 图片）
     * @throws BusinessException 当验证码功能被禁用时抛出
     */
    public Mono<CaptchaVO> generate() {
        if (!properties.isEnabled()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "图形验证码功能未启用"));
        }
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        byte[] png = drawImage(code);
        String image = Base64.getEncoder().encodeToString(png);

        String key = CAPTCHA_KEY_PREFIX + captchaId;
        return redisTemplate
                .opsForValue()
                .set(key, code, properties.getTtl())
                .map(success -> new CaptchaVO(captchaId, image))
                .doOnError(error -> log.error("验证码写入缓存失败", error));
    }

    /**
     * 校验验证码（一次性）。
     *
     * <p>验证码不存在、已过期或答案不匹配均校验失败；无论结果如何
     * 都会删除缓存，保证一次性消费。</p>
     *
     * @param captchaId 验证码 ID
     * @param code 用户输入的验证码
     * @return 校验结果，true-通过，false-失败
     */
    public Mono<Boolean> verify(String captchaId, String code) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(code)) {
            return Mono.just(false);
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        return redisTemplate
                .opsForValue()
                .get(key)
                .defaultIfEmpty("")
                .flatMap(expected -> redisTemplate.opsForValue().delete(key).thenReturn(expected))
                .map(expected -> expected.equalsIgnoreCase(code.trim()));
    }

    /**
     * 生成随机验证码字符。
     *
     * @return 随机验证码
     */
    private String randomCode() {
        int length = Math.min(Math.max(properties.getLength(), 4), 6);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    /**
     * 绘制验证码 PNG 图片。
     *
     * <p>使用 {@link java.awt} 绘制带噪点与干扰线的字符图片，
     * 不依赖任何第三方验证码库。</p>
     *
     * @param code 验证码字符
     * @return PNG 图片字节
     */
    private byte[] drawImage(String code) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(randomPastel());
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            // 干扰线
            for (int i = 0; i < 4; i++) {
                g.setColor(randomColor(100, 200));
                int x1 = random.nextInt(IMAGE_WIDTH);
                int y1 = random.nextInt(IMAGE_HEIGHT);
                int x2 = random.nextInt(IMAGE_WIDTH);
                int y2 = random.nextInt(IMAGE_HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 字符
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(20, 120));
                int x = 12 + i * 26;
                int y = 32 + random.nextInt(8);
                double angle = (random.nextDouble() - 0.5) * 0.4;
                g.rotate(angle, x + 10, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-angle, x + 10, y);
            }

            // 噪点
            for (int i = 0; i < 40; i++) {
                g.setColor(randomColor(0, 200));
                g.fillRect(random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT), 1, 1);
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        }
    }

    /**
     * 生成柔和背景色。
     *
     * @return 浅色背景
     */
    private Color randomPastel() {
        return new Color(230 + random.nextInt(26), 235 + random.nextInt(21), 240 + random.nextInt(16));
    }

    /**
     * 生成指定亮度区间的随机颜色。
     *
     * @param min 最小分量值
     * @param max 最大分量值
     * @return 随机颜色
     */
    private Color randomColor(int min, int max) {
        int range = max - min + 1;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }
}
