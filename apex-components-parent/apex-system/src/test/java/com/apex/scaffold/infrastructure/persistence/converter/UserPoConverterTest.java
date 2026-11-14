package com.apex.scaffold.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("UserPoConverter tenantId")
class UserPoConverterTest {

    private final UserPoConverter converter = Mappers.getMapper(UserPoConverter.class);

    @Test
    @DisplayName("租户ID在实体与PO之间双向保留")
    void convertsTenantId() {
        UserEntity entity = UserEntity.create("open-id", "union-id");
        entity.setTenantId("tenant-a");

        UserPO po = converter.toPo(entity);
        assertThat(po.getTenantId()).isEqualTo("tenant-a");

        assertThat(converter.toEntity(po).getTenantId()).isEqualTo("tenant-a");
    }
}
