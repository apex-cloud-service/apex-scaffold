package com.apex.scaffold.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.model.entity.UserRoleEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class UserRolePoConverterTest {

    private final UserRolePoConverter converter = Mappers.getMapper(UserRolePoConverter.class);

    @Test
    void userRoleTenantIdIsMappedAcrossDomainAndPersistence() throws Exception {
        assertThat(UserRoleEntity.class.getDeclaredField("tenantId")).isNotNull();
        assertThat(UserRolePO.class.getDeclaredField("tenantId")).isNotNull();
        assertThat(UserRolePoConverter.class.getMethod("toPo", UserRoleEntity.class))
                .isNotNull();
        assertThat(UserRolePoConverter.class.getMethod("toEntity", UserRolePO.class))
                .isNotNull();
    }

    @Test
    void preservesTenantIdBidirectionally() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setTenantId("tenant-a");
        entity.setUserId(100L);
        entity.setRoleId(200L);

        UserRolePO po = converter.toPo(entity);

        assertThat(po.getTenantId()).isEqualTo("tenant-a");

        UserRoleEntity restored = converter.toEntity(po);

        assertThat(restored.getTenantId()).isEqualTo("tenant-a");
    }
}
