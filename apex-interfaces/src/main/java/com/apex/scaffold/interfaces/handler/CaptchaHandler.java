package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.security.CaptchaService;
import com.apex.scaffold.application.security.CaptchaVO;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 图形验证码处理器（函数式端点）。
 *
 * <p>提供图形验证码的获取与校验 API，用于登录场景的防自动化爆破。
 * 验证码生成后由前端展示并回传 {@code captchaId} 与用户输入，
 * 服务端一次性校验。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /api/v1/auth/captcha - 获取验证码（ID + Base64 图片）</li>
 *   <li>POST /api/v1/auth/captcha/verify - 校验验证码</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see CaptchaService
 */
@Component
@Tag(name = "图形验证码", description = "登录图形验证码获取与校验")
public class CaptchaHandler extends BaseHandler {

    /**
     * 图形验证码应用服务。
     */
    private final CaptchaService captchaService;

    /**
     * 创建图形验证码处理器。
     *
     * @param captchaService 图形验证码应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public CaptchaHandler(CaptchaService captchaService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.captchaService = captchaService;
    }

    /**
     * 获取图形验证码。
     *
     * <p>返回验证码 ID 与 Base64 编码的 PNG 图片，前端拼接
     * {@code data:image/png;base64,} 前缀后展示。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为 {@link CaptchaVO}
     */
    @Operation(operationId = "getCaptcha", summary = "获取图形验证码")
    public Mono<ServerResponse> generate(ServerRequest request) {
        return captchaService.generate().flatMap(this::ok);
    }

    /**
     * 校验图形验证码。
     *
     * <p>请求体携带 {@code captchaId} 与用户输入的 {@code code}，
     * 校验通过返回 {@code {"valid": true}}；验证码一次性消费。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为 {@code {"valid": 是否通过}}
     */
    @Operation(operationId = "verifyCaptcha", summary = "校验图形验证码")
    public Mono<ServerResponse> verify(ServerRequest request) {
        return request.bodyToMono(CaptchaVerifyRequest.class)
                .defaultIfEmpty(new CaptchaVerifyRequest())
                .flatMap(body -> captchaService.verify(body.getCaptchaId(), body.getCode()))
                .flatMap(valid -> ok(java.util.Map.of("valid", valid)));
    }

    /**
     * 构造统一 JSON 成功响应。
     *
     * @param data 业务数据，可为 {@code null}
     * @return 携带当前 traceId 的 200 JSON 响应
     */
    private <T> Mono<ServerResponse> ok(T data) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(data, TraceIdUtil.getTraceId()));
    }

    /**
     * 验证码校验请求体。
     *
     * <p>承载 {@code captchaId}（获取验证码时返回的 ID）与用户输入的
     * {@code code}（忽略大小写）。</p>
     */
    public static class CaptchaVerifyRequest {

        /** 验证码 ID */
        private String captchaId;

        /** 用户输入的验证码 */
        private String code;

        /**
         * 构造验证码校验请求体。
         */
        public CaptchaVerifyRequest() {}

        /**
         * 获取验证码 ID。
         *
         * @return 验证码 ID
         */
        public String getCaptchaId() {
            return captchaId;
        }

        /**
         * 设置验证码 ID。
         *
         * @param captchaId 验证码 ID
         */
        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        /**
         * 获取用户输入的验证码。
         *
         * @return 验证码
         */
        public String getCode() {
            return code;
        }

        /**
         * 设置用户输入的验证码。
         *
         * @param code 验证码
         */
        public void setCode(String code) {
            this.code = code;
        }
    }
}
