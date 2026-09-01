package com.apex.scaffold.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.infrastructure.persistence.po.JobPO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class JobPoConverterTest {

    private final JobPoConverter converter = Mappers.getMapper(JobPoConverter.class);

    @Test
    void convertsTenantIdInBothDirections() {
        JobEntity entity = JobEntity.create("job", "DEFAULT", "bean.run()", "0 0 2 * * ?", "tenant-a");

        JobPO po = converter.toPo(entity);

        assertThat(po.getTenantId()).isEqualTo("tenant-a");
        assertThat(converter.toEntity(po).getTenantId()).isEqualTo("tenant-a");
    }
}
