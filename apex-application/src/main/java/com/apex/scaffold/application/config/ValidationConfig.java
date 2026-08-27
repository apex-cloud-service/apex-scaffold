package com.apex.scaffold.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * 参数校验配置类
 *
 * <p>配置Bean Validation支持，启用方法级别的参数校验。
 * 集成Hibernate Validator作为默认校验器实现。</p>
 *
 * <h2>校验注解说明</h2>
 * <ul>
 *   <li>{@code @NotBlank} - 字符串非空且长度大于0</li>
 *   <li>{@code @NotNull} - 值不为null</li>
 *   <li>{@code @Size} - 字符串长度或集合大小在指定范围内</li>
 *   <li>{@code @Min/@Max} - 数值最小值/最大值</li>
 *   <li>{@code @Pattern} - 正则表达式匹配</li>
 *   <li>{@code @Email} - 邮箱格式校验</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>{@code * // 在DTO上添加校验注解
 * public class LoginRequest {
 *     @NotBlank(message = "手机号不能为空")
 *     @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
 *     private String phone;
 * }
 *
 * // 在 WebFlux 函数式端点的 Handler 中启用校验（继承 BaseHandler 注入 Validator）
 * public Mono<ServerResponse> login(ServerRequest request) {
 *     return request.bodyToMono(LoginRequest.class)
 *             .flatMap(this::validateAndThrow)
 *             .flatMap(body -> authService.login(body, ...));
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class ValidationConfig {

    /**
     * 配置方法级别的校验处理器
     *
     * <p>启用Spring的方法参数校验支持，允许在Controller方法参数上使用@Valid注解。</p>
     *
     * @param validator 由 I18nConfig 提供的 LocalValidatorFactoryBean，已集成国际化消息源
     * @return 方法校验后置处理器
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor(LocalValidatorFactoryBean validator) {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setValidator(validator);
        return processor;
    }
}
