package com.apex.scaffold.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.enums.DataScopeType;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.infrastructure.persistence.po.RolePO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class RolePoConverterTest {

    private final RolePoConverter converter = Mappers.getMapper(RolePoConverter.class);

    @Test
    void mapsDataScopeCodeBidirectionally() {
        RoleEntity entity = RoleEntity.create("审计员", "AUDITOR", "test");
        entity.setDataScope(DataScopeType.DEPT_AND_CHILD);

        RolePO po = converter.toPo(entity);

        assertThat(po.getDataScope()).isEqualTo(3);

        RoleEntity restored = converter.toEntity(po);

        assertThat(restored.getDataScope()).isEqualTo(DataScopeType.DEPT_AND_CHILD);
    }

    @Test
    void rejectsUnknownDataScopeCode() {
        RolePO po = new RolePO();
        po.setDataScope(99);

        assertThatThrownBy(() -> converter.toEntity(po))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported data scope code: 99");
    }

    @Test
    void preservesMissingDataScopeAsNull() {
        RolePO po = new RolePO();

        assertThat(converter.toEntity(po).getDataScope()).isNull();
    }
}
