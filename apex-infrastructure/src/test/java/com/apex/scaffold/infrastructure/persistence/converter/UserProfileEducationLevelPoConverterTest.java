package com.apex.scaffold.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.enums.EducationLevel;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserProfilePO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * 用户画像学历字段持久化转换测试。
 *
 * @author weiqiang.ling
 */
@DisplayName("UserProfilePoConverter educationLevel")
class UserProfileEducationLevelPoConverterTest {

    private final UserProfilePoConverter converter = Mappers.getMapper(UserProfilePoConverter.class);

    /**
     * 验证 {@code convertsEducationLevel} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("学历枚举在实体与PO之间双向保留")
    void convertsEducationLevel() {
        UserProfileEntity entity = UserProfileEntity.create(8L);
        entity.setEducationLevel(EducationLevel.MASTER);

        UserProfilePO po = converter.toPo(entity);
        assertThat(po.getEducationLevel()).isEqualTo(EducationLevel.MASTER);

        assertThat(converter.toEntity(po).getEducationLevel()).isEqualTo(EducationLevel.MASTER);
    }
}
