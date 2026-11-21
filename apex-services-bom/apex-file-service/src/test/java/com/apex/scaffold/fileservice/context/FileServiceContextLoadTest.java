package com.apex.scaffold.fileservice.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.file.infrastructure.persistence.mapper.ChartMapper;
import com.apex.scaffold.file.infrastructure.persistence.mapper.DocumentMapper;
import com.apex.scaffold.file.infrastructure.persistence.mapper.FileChunkMapper;
import com.apex.scaffold.file.infrastructure.persistence.mapper.FileMapper;
import com.apex.scaffold.file.infrastructure.persistence.po.FilePO;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件服务 basic Profile 轻量上下文加载测试。
 *
 * <p>以 {@code basic} Profile 全量加载 {@link com.apex.scaffold.fileservice.FileServiceApplication}
 * 应用上下文（H2 内存库、本地存储、Nacos/RocketMQ 关闭），用于验证运行期装配的完整性——
 * 尤其是位于库模块 {@code apex-file} 的 Mapper 是否被 {@code @MapperScan} 正确注册。</p>
 *
 * <p>既有切片测试（{@code @WebFluxTest}）与静态迁移脚本测试均不启动完整容器，
 * 无法覆盖 {@code @MapperScan} 的装配结果；本测试通过断言上下文中的 Mapper Bean
 * 补齐该验证缺口（迁移方案 §4.6）。</p>
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
class FileServiceContextLoadTest {

    /** 文件服务应用上下文。 */
    private final ApplicationContext context;

    /**
     * 校验 {@code apex-file} 库中的文件 Mapper 均已注册到应用上下文。
     *
     * <p>{@code FileMapper / DocumentMapper / ChartMapper / FileChunkMapper} 均位于
     * 库模块 {@code com.apex.scaffold.file.infrastructure.persistence.mapper}，
     * 依赖启动类的 {@code @MapperScan} 才能被注册；任一缺失即说明装配回归。</p>
     */
    @Test
    void fileMappersAreWiredIntoContext() {
        assertThat(context.getBeanNamesForType(FileMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(DocumentMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ChartMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(FileChunkMapper.class)).hasSize(1);
    }

    @Test
    @Transactional
    void fileMapperInsertFillsRequiredAuditFields() {
        FileMapper mapper = context.getBean(FileMapper.class);
        FilePO file = FilePO.builder().fileName("audit-fixture.png").build();

        assertThat(mapper.insert(file)).isEqualTo(1);

        FilePO persisted = mapper.selectById(file.getId());
        assertThat(persisted.getCreateTime()).isNotNull();
        assertThat(persisted.getUpdateTime()).isNotNull();
        assertThat(persisted.getVersion()).isZero();
    }
}
