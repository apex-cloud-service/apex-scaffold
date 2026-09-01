package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper;
import com.apex.scaffold.infrastructure.persistence.po.DeptPO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.test.StepVerifier;

class DeptRepositoryImplTest {

    @Test
    void mapsTenantAndPersistenceFieldsFromStoredDepartment() {
        DeptMapper mapper = mock(DeptMapper.class);
        DeptRepositoryImpl repository = new DeptRepositoryImpl(mapper);
        LocalDateTime createTime = LocalDateTime.of(2026, 8, 31, 10, 0);
        LocalDateTime updateTime = createTime.plusHours(1);
        DeptPO po = departmentPo(10L, "tenant-a", 3, 0, createTime, updateTime);
        when(mapper.selectById(10L)).thenReturn(po);

        StepVerifier.create(repository.findById(10L))
                .assertNext(dept -> {
                    assertThat(dept.getTenantId()).isEqualTo("tenant-a");
                    assertThat(dept.getCreateTime()).isEqualTo(createTime);
                    assertThat(dept.getUpdateTime()).isEqualTo(updateTime);
                    assertThat(dept.getLockVersion()).isEqualTo(3);
                    assertThat(dept.getDeleted()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void rejectsUpdateWhenNoRowWasChanged() {
        DeptMapper mapper = mock(DeptMapper.class);
        DeptRepositoryImpl repository = new DeptRepositoryImpl(mapper);
        DeptEntity dept = departmentEntity(10L, "tenant-a", 3);
        when(mapper.updateById(any(DeptPO.class))).thenReturn(0);

        StepVerifier.create(repository.save(dept))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
    }

    @Test
    void mapsPersistenceFieldsToUpdateAndReturnsIncrementedVersion() {
        DeptMapper mapper = mock(DeptMapper.class);
        DeptRepositoryImpl repository = new DeptRepositoryImpl(mapper);
        DeptEntity dept = departmentEntity(10L, "tenant-a", 3);
        LocalDateTime createTime = LocalDateTime.of(2026, 8, 31, 10, 0);
        LocalDateTime updateTime = createTime.plusHours(1);
        dept.setCreateTime(createTime);
        dept.setUpdateTime(updateTime);
        dept.setDeleted(0);
        ArgumentCaptor<DeptPO> captor = ArgumentCaptor.forClass(DeptPO.class);
        when(mapper.updateById(captor.capture())).thenAnswer(invocation -> {
            captor.getValue().setVersion(4);
            return 1;
        });

        StepVerifier.create(repository.save(dept))
                .assertNext(saved -> assertThat(saved.getLockVersion()).isEqualTo(4))
                .verifyComplete();

        DeptPO updated = captor.getValue();
        assertThat(updated.getTenantId()).isEqualTo("tenant-a");
        assertThat(updated.getCreateTime()).isEqualTo(createTime);
        assertThat(updated.getUpdateTime()).isEqualTo(updateTime);
        assertThat(updated.getVersion()).isEqualTo(4);
        assertThat(updated.getDeleted()).isZero();
    }

    private DeptEntity departmentEntity(Long id, String tenantId, Integer version) {
        DeptEntity dept = new DeptEntity();
        dept.setId(id);
        dept.setTenantId(tenantId);
        dept.setName("研发部");
        dept.setParentId(0L);
        dept.setAncestors("0");
        dept.setOrderNum(1);
        dept.setStatus(1);
        dept.setLockVersion(version);
        return dept;
    }

    private DeptPO departmentPo(
            Long id,
            String tenantId,
            Integer version,
            Integer deleted,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
        DeptPO po = new DeptPO();
        po.setId(id);
        po.setTenantId(tenantId);
        po.setVersion(version);
        po.setDeleted(deleted);
        po.setCreateTime(createTime);
        po.setUpdateTime(updateTime);
        return po;
    }
}
