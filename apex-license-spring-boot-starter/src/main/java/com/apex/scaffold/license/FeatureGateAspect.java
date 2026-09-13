package com.apex.scaffold.license;

import com.apex.scaffold.common.annotation.FeatureGate;
import com.apex.scaffold.common.error.ErrorCode;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * License功能门控AOP切面
 *
 * <p>本切面拦截所有标注了{@code @FeatureGate}注解的方法,在方法执行前检查对应功能的License授权状态。
 * 作为运行时授权检查的核心组件,与{@link LicenseFeatureCondition}的装配期检查配合,形成双层防护机制。</p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li><b>运行时授权检查</b>:在方法执行前拦截,通过{@link LicenseValidator}检查功能授权状态</li>
 *   <li><b>WebFlux响应式支持</b>:自动识别{@link Mono}/{@link Flux}返回类型,通过响应式错误流传递异常</li>
 *   <li><b>同步方法支持</b>:非响应式方法直接返回统一错误结果{@link Result}</li>
 *   <li><b>全局自动装配</b>:由{@link LicenseAutoConfiguration}以{@code @Bean}方式注册,不依赖宿主应用的组件扫描路径</li>
 * </ul>
 *
 * <h3>工作原理</h3>
 * <p>切面使用Spring AOP的{@code @Around}通知,在方法执行前后进行拦截:
 * <ol>
 *   <li>检查方法上的{@code @FeatureGate}注解,提取功能标识符和未授权提示消息</li>
 *   <li>调用{@link LicenseValidator#isFeatureEnabled}检查功能授权状态</li>
 *   <li>已授权:调用{@code joinPoint.proceed()}继续执行原方法</li>
 *   <li>未授权:根据方法返回类型返回对应的错误响应(响应式错误流或统一错误结果)</li>
 * </ol>
 *
 * <h3>响应式方法处理</h3>
 * <p>切面自动识别方法返回类型,针对WebFlux响应式方法进行特殊处理:
 * <ul>
 *   <li>返回{@link Mono}:通过{@code Mono.error(BusinessException)}传递异常,由全局异常处理器转换为统一响应格式</li>
 *   <li>返回{@link Flux}:通过{@code Flux.error(BusinessException)}传递异常,确保响应式链路不被破坏</li>
 *   <li>同步方法:直接返回{@code Result.error()},包含错误码和提示消息</li>
 * </ul>
 *
 * <h3>与装配期条件的协同</h3>
 * <p>本切面与{@link LicenseFeatureCondition}共享同一个{@link LicenseValidator}实例(通过
 * {@link LicenseAutoConfiguration}注入),保证装配期和运行期的授权判断一致。这种双层防护确保:
 * <ul>
 *   <li>商业Bean在装配期就被过滤,减少运行时检查开销</li>
 *   <li>即使通过反射等手段绕过装配检查,运行时切面仍会拦截方法调用</li>
 * </ul>
 *
 * <h3>全局装配说明</h3>
 * <p>切面由{@link LicenseAutoConfiguration}以{@code @Bean}方式显式注册,不依赖宿主应用的
 * {@code @ComponentScan}路径。这意味着无论宿主应用主类位于什么包名下,只要引入本模块且未关闭
 * {@code apex.license.enabled},切面即自动生效。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在Service方法上标注功能门控
 * @Service
 * public class AdvancedCacheService {
 *     @FeatureGate(value = "advanced-cache", message = "高级缓存功能需要标准版或企业版License")
 *     public Mono<CacheStats> getCacheStats() {
 *         // 只有STANDARD或ENTERPRISE版License才能调用
 *         return cacheManager.getStats();
 *     }
 * }
 *
 * // 在Handler方法上标注功能门控
 * @Component
 * public class ReportHandler {
 *     @FeatureGate(value = "export-excel", message = "Excel导出功能需要企业版License")
 *     public Mono<ServerResponse> exportToExcel(ServerRequest request) {
 *         // 只有ENTERPRISE版License才能调用
 *         return ServerResponse.ok()
 *             .contentType(MediaType.APPLICATION_OCTET_STREAM)
 *             .body(excelService.export(), byte[].class);
 *     }
 * }
 *
 * // 同步方法也支持
 * public class ConfigService {
 *     @FeatureGate(value = "advanced-config", message = "高级配置需要企业版License")
 *     public Result<ConfigDTO> getAdvancedConfig() {
 *         // 同步方法,未授权时直接返回Result.error()
 *         return Result.success(configRepository.getAdvancedConfig());
 *     }
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see com.apex.scaffold.common.annotation.FeatureGate
 * @see LicenseValidator
 * @see LicenseAutoConfiguration
 * @see LicenseFeatureCondition
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class FeatureGateAspect {

    private final LicenseValidator licenseValidator;

    /**
     * 环绕通知: 校验@FeatureGate标注方法对应的功能是否已授权
     *
     * <p>功能已授权时直接放行; 未授权时根据方法返回类型分别处理:
     * <ul>
     *   <li>返回{@link Mono}/{@link Flux}的响应式方法, 通过{@code Mono.error}/{@code Flux.error}
     *       传递{@link BusinessException}, 交由全局异常处理器转换为统一响应格式;</li>
     *   <li>同步方法直接返回统一错误结果。</li>
     * </ul>
     *
     * @param joinPoint   被拦截方法的连接点, 可用于获取签名并继续执行
     * @param featureGate 方法上标注的{@link FeatureGate}注解, 包含功能名与未授权提示消息
     * @return 原方法返回值, 或未授权时对应的错误结果(响应式错误流/统一错误结果)
     * @throws Throwable 原方法执行过程中抛出的异常, 或连接点获取/执行失败时的异常
     */
    @Around("@annotation(featureGate)")
    public Object around(ProceedingJoinPoint joinPoint, FeatureGate featureGate) throws Throwable {
        // 功能已授权,继续执行
        if (licenseValidator.isFeatureEnabled(featureGate.value())) {
            return joinPoint.proceed();
        }

        log.warn("功能未授权: {}, 方法: {}", featureGate.value(), joinPoint.getSignature());
        BusinessException exception =
                new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), featureGate.message());

        // 响应式方法: 通过 Mono.error/Flux.error 传递,交给全局异常处理器输出统一格式
        Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> returnType = method.getReturnType();
        if (Mono.class.isAssignableFrom(returnType)) {
            return Mono.error(exception);
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return Flux.error(exception);
        }

        // 同步方法: 直接返回统一错误结果
        return Result.error(ErrorCode.PERMISSION_DENIED.getCode(), featureGate.message());
    }
}
