package com.apex.scaffold.officeservice.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.infrastructure.mapper.ApprovalProcessMapper;
import com.apex.scaffold.office.infrastructure.mapper.CalendarEventMapper;
import com.apex.scaffold.office.infrastructure.mapper.DocumentInfoMapper;
import com.apex.scaffold.office.infrastructure.persistence.OfficeAppMapper;
import com.apex.scaffold.office.infrastructure.persistence.OfficeBindingMapper;
import com.apex.scaffold.office.infrastructure.persistence.OfficeOrganizationMappingMapper;
import com.apex.scaffold.office.infrastructure.persistence.OfficeSyncTaskMapper;
import com.apex.scaffold.office.infrastructure.persistence.mapper.OfficeMessageMapper;
import com.apex.scaffold.officeservice.interfaces.handler.OfficeAdminHandler;
import com.apex.scaffold.officeservice.interfaces.handler.OfficeHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeApprovalHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeCalendarHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeDocumentHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeMessageHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeOrganizationHandler;
import com.apex.scaffold.officeservice.interfaces.security.IdentityRelayAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * 办公服务 basic Profile 轻量上下文加载测试。
 *
 * <p>以 {@code basic} Profile 全量加载 {@link com.apex.scaffold.officeservice.OfficeServiceApplication}
 * 应用上下文（H2 内存库、Flyway 关闭改用 {@code basic-schema.sql}、Nacos 发现/配置关闭），
 * 用于验证运行期装配的完整性。</p>
 *
 * <h2>覆盖的装配回归场景</h2>
 * <ul>
 *   <li>位于库模块 {@code apex-office} 的 Mapper 是否被启动类 {@code @MapperScan} 正确注册；</li>
 *   <li>接口层 Handler（含 {@link OfficeHandler} / {@link OfficeAdminHandler} 及
 *       {@code office/} 分包下的审批、日历、消息、文档、组织处理器）是否成功装配——
 *       此前顶层单体拷贝的 {@code OfficeIntegrationHandler}/{@code OrgSyncHandler}
 *       依赖本服务内不存在实现 Bean 的 {@code com.apex.scaffold.domain.repository.*}，
 *       会触发 {@code NoSuchBeanDefinitionException} 导致启动失败（已按域切流移除）；</li>
 *   <li>身份中继过滤器 {@link IdentityRelayAuthenticationFilter} 是否注册。</li>
 * </ul>
 *
 * <p>既有静态迁移脚本测试不启动容器，无法覆盖上述装配结果；本测试通过断言上下文 Bean
 * 补齐该验证缺口（迁移方案 §3.6 / Phase 5 网关按域切流的运行期前置检查）。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            // apex-common ConfigEncryptionService 要求恰好 32 字节的配置加密密钥
            "security.config.encryption.key=01234567890123456789012345678901"
        })
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class OfficeServiceContextLoadTest {

    /** 办公服务应用上下文。 */
    private final ApplicationContext context;

    /**
     * 校验 {@code apex-office} 库中的办公 Mapper 均已注册到应用上下文。
     *
     * <p>这 8 个 Mapper 均位于库模块 {@code com.apex.scaffold.office.infrastructure}，
     * 依赖启动类的 {@code @MapperScan} 才能被注册；任一缺失即说明装配回归。</p>
     */
    @Test
    void officeMappersAreWiredIntoContext() {
        assertThat(context.getBeanNamesForType(OfficeAppMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeBindingMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeOrganizationMappingMapper.class))
                .hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeSyncTaskMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeMessageMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ApprovalProcessMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(CalendarEventMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(DocumentInfoMapper.class)).hasSize(1);
    }

    /**
     * 校验接口层 Handler 装配完整。
     *
     * <p>覆盖保留的顶层处理器 {@link OfficeHandler} / {@link OfficeAdminHandler}，以及
     * {@code office/} 分包下的审批、日历、消息、文档、组织处理器。上下文若成功加载，
     * 即证明这些处理器所依赖的 {@code apex-office} 垂直切片服务（Layer-#2 仓储实现）
     * 均可解析，不会在启动阶段抛出 {@code NoSuchBeanDefinitionException}。</p>
     */
    @Test
    void officeHandlersAreWiredIntoContext() {
        assertThat(context.getBeanNamesForType(OfficeHandler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeAdminHandler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeApprovalHandler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeCalendarHandler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeMessageHandler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeDocumentHandler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OfficeOrganizationHandler.class)).hasSize(1);
    }

    /**
     * 校验网关注入身份所依赖的身份中继过滤器已注册，避免未认证请求被静默放行。
     */
    @Test
    void identityRelayFilterIsWiredIntoContext() {
        assertThat(context.getBeanNamesForType(IdentityRelayAuthenticationFilter.class))
                .hasSize(1);
    }
}
