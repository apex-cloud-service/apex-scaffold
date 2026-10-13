package com.apex.scaffold.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 验证通用乐观锁版本不会在 PO 与领域实体转换时丢失。
 *
 * <p>以用户实体转换器作为代表，校验 {@code BasePO.version} 经由语义别名
 * 映射到 {@code BaseEntity.lockVersion}，再转换回 PO 后仍保持相同值。
 * 所有继承相同基类的 MapStruct 转换器都会复用这一属性映射规则。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("乐观锁版本转换")
class OptimisticLockVersionMappingTest {

    /**
     * 被测用户 PO 转换器。
     *
     * <p>直接使用 MapStruct 生成实现，确保测试覆盖编译期实际生成的属性映射代码。
     */
    private final UserPoConverter converter = new UserPoConverterImpl();

    /**
     * 验证持久化对象与领域实体双向转换时完整保留并发版本。
     *
     * <p>给定数据库版本 {@code 7}，首次转换应写入领域实体的
     * {@code lockVersion}；反向转换则应重新写回 PO 的 {@code version}。
     */
    @Test
    @DisplayName("PO 与领域实体双向转换应透传并发版本")
    void shouldPreserveLockVersionAcrossRoundTrip() {
        UserPO source = new UserPO();
        source.setId(100L);
        source.setVersion(7);

        UserEntity entity = converter.toEntity(source);
        UserPO convertedBack = converter.toPo(entity);

        assertThat(entity.getLockVersion()).isEqualTo(7);
        assertThat(convertedBack.getVersion()).isEqualTo(7);
    }
}
