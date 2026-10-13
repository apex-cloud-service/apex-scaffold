package com.apex.scaffold.authservice.config;

import com.apex.scaffold.auth.domain.service.JwtKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * JWT 签名密钥启动初始化器。
 *
 * <p>微服务迁移 Phase 2.1 将认证中心 Token 签名算法由对称 HS256 升级为非对称
 * RS256，签名密钥由 {@link JwtKeyRotationService} 统一管理并存放于 Redis。
 * 本组件在 Spring 容器完全启动后（{@link ApplicationRunner} 阶段）触发
 * {@code initializeKeys()}：
 *
 * <ul>
 *   <li>若 Redis 中不存在当前密钥，则自动生成初始 RSA-2048 密钥对并持久化；</li>
 *   <li>若密钥已存在（非首次启动或已轮换），则直接确认就绪，不重复生成。</li>
 * </ul>
 *
 * <p>若缺少该初始化步骤，首次运行时 Redis 无密钥，{@code JwtKeyMaterialService.sign}
 * 将因“JWT密钥未初始化”而失败，导致登录接口返回 500。该初始化链路正是修复
 * auth-center 登录 500 回归（Phase 2.3-6）的关键一环。</p>
 *
 * <p>使用 {@code block()} 同步等待初始化完成，保证后续 HTTP 请求到达前密钥必然可用；
 * 阻塞点仅在应用启动阶段执行一次。</p>
 *
 * <p>本初始化器在全部 Profile 下生效：生产环境依赖真实 Redis，集成/启动测试
 * （{@code basic} profile）则通过测试侧提供的嵌入式 Redis 完成同一初始化链路，
 * 从而保证“启动即生成初始 RSA 密钥”的行为在两条链路上完全一致。</p>
 *
 * @author Apex Team
 * @since 2026-10-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtKeyBootstrapRunner implements ApplicationRunner {

    /**
     * JWT 密钥轮换服务（Redis-RSA 实现），负责密钥生成、轮换与宽限期内验签。
     */
    private final JwtKeyRotationService jwtKeyRotationService;

    /**
     * 应用启动完成后初始化 JWT 签名密钥。
     *
     * @param args 启动参数（本初始化器不消费）
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            String message = jwtKeyRotationService.initializeKeys().block();
            log.info("JWT 签名密钥初始化完成: {}", message);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT 签名密钥初始化失败，无法完成 RS256 签发", exception);
        }
    }
}
