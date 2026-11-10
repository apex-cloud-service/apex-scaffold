package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.persistence.mapper.TenantPackageMapper;
import com.apex.scaffold.infrastructure.persistence.po.TenantPackagePO;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

/**
 * 租户套餐仓储实现测试。
 *
 * <p>覆盖套餐 JSON 字段严格解析：格式错误、类型不匹配和 null 元素必须标记为配置损坏；
 * null 与空白 JSON 保持历史数据语义，解析为空集合并视为有效。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 */
@DisplayName("租户套餐仓储实现测试")
class TenantPackageRepositoryImplTest {

    private final TenantPackageMapper mapper = mock(TenantPackageMapper.class);

    private TenantPackageRepositoryImpl repository;

    /**
     * 构造被测仓储。
     */
    @BeforeEach
    void setUp() {
        repository = new TenantPackageRepositoryImpl(mapper);
    }

    /**
     * 构造全部 JSON 字段有效的套餐。
     *
     * @return 套餐持久化对象
     */
    private TenantPackagePO validPackage() {
        TenantPackagePO po = new TenantPackagePO();
        po.setId(1L);
        po.setPackageCode("basic");
        po.setPackageName("基础套餐");
        po.setMenuIds("[10,11]");
        po.setPermissionCodes("[\"user:read\"]");
        po.setFeatures("[\"workflow\"]");
        po.setStatus(1);
        po.setDeleted(0);
        return po;
    }

    /**
     * 验证合法 JSON 可转换为套餐实体。
     */
    @Test
    @DisplayName("findByCode - 合法 JSON 解析成功")
    void parsesValidConfiguration() {
        when(mapper.selectOne(any())).thenReturn(validPackage());

        StepVerifier.create(repository.findByCode("basic"))
                .assertNext(pkg -> {
                    assertThat(pkg.isConfigurationInvalid()).isFalse();
                    assertThat(pkg.getMenuIds()).containsExactlyInAnyOrder(10L, 11L);
                    assertThat(pkg.getPermissionCodes()).containsExactly("user:read");
                    assertThat(pkg.getFeatures()).containsExactly("workflow");
                })
                .verifyComplete();
    }

    /**
     * 验证 null 与空白 JSON 解析为空集合并保持有效。
     */
    @Test
    @DisplayName("findByCode - null/空白 JSON 解析为空集合且不标记损坏")
    void treatsBlankJsonAsEmptyAndValid() {
        TenantPackagePO po = validPackage();
        po.setMenuIds(null);
        po.setPermissionCodes(" ");
        po.setFeatures(null);
        when(mapper.selectOne(any())).thenReturn(po);

        StepVerifier.create(repository.findByCode("basic"))
                .assertNext(pkg -> {
                    assertThat(pkg.isConfigurationInvalid()).isFalse();
                    assertThat(pkg.getMenuIds()).isEmpty();
                    assertThat(pkg.getPermissionCodes()).isEmpty();
                    assertThat(pkg.getFeatures()).isEmpty();
                })
                .verifyComplete();
    }

    /**
     * 构造损坏的 JSON 场景。
     *
     * @return 参数化测试数据
     */
    static Stream<Arguments> invalidJsonFields() {
        return Stream.of(
                Arguments.of("menuIds", "not-json"),
                Arguments.of("menuIds", "[\"not-a-number\"]"),
                Arguments.of("menuIds", "[10,null]"),
                Arguments.of("permissionCodes", "{\"read\":true}"),
                Arguments.of("permissionCodes", "[\"user:read\",null]"),
                Arguments.of("features", "[\"workflow\",null]"));
    }

    /**
     * 验证任一 JSON 字段损坏均标记配置无效。
     *
     * @param field 损坏字段
     * @param invalidJson 损坏 JSON 文本
     */
    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidJsonFields")
    @DisplayName("findByCode - 任一 JSON 字段损坏均标记 configurationInvalid")
    void marksInvalidWhenAnyJsonFieldIsMalformed(String field, String invalidJson) {
        TenantPackagePO po = validPackage();
        switch (field) {
            case "menuIds" -> po.setMenuIds(invalidJson);
            case "permissionCodes" -> po.setPermissionCodes(invalidJson);
            case "features" -> po.setFeatures(invalidJson);
            default -> throw new IllegalArgumentException("unknown field: " + field);
        }
        when(mapper.selectOne(any())).thenReturn(po);

        StepVerifier.create(repository.findByCode("basic"))
                .assertNext(pkg -> assertThat(pkg.isConfigurationInvalid()).isTrue())
                .verifyComplete();
    }
}
