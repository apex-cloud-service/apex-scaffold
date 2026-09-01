package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.domain.repository.DeptRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DeptServiceTest {

    private final DeptRepository deptRepository = mock(DeptRepository.class);
    private final DeptService service = new DeptService(deptRepository);

    @Test
    void returnsLockVersionForDepartmentDetail() {
        DeptEntity stored = department("研发部");
        stored.setId(10L);
        stored.setLockVersion(3);
        when(deptRepository.findById(10L)).thenReturn(Mono.just(stored));

        StepVerifier.create(service.get(10L))
                .assertNext(dept -> assertThat(dept.getLockVersion()).isEqualTo(3))
                .verifyComplete();
    }

    @Test
    void propagatesRequestedLockVersionWhenUpdatingDepartment() {
        DeptEntity stored = department("研发部");
        stored.setId(10L);
        stored.setLockVersion(3);
        DeptEntity update = department("updated");
        update.setLockVersion(3);
        when(deptRepository.findById(10L)).thenReturn(Mono.just(stored));
        when(deptRepository.save(any(DeptEntity.class))).thenAnswer(invocation -> {
            DeptEntity saved = invocation.getArgument(0);
            saved.setLockVersion(saved.getLockVersion() + 1);
            return Mono.just(saved);
        });

        StepVerifier.create(service.update(10L, update))
                .assertNext(dept -> assertThat(dept.getLockVersion()).isEqualTo(4))
                .verifyComplete();

        verify(deptRepository).save(update);
        assertThat(update.getLockVersion()).isEqualTo(4);
    }

    @Test
    void rejectsUpdateWithoutLockVersionBeforeRepositoryLookup() {
        DeptEntity update = department("updated");

        StepVerifier.create(service.update(10L, update))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(error.getMessage()).contains("版本");
                })
                .verify();

        verifyNoInteractions(deptRepository);
    }

    @Test
    void rejectsUpdateWhenDepartmentDoesNotExist() {
        when(deptRepository.findById(99L)).thenReturn(Mono.empty());
        DeptEntity update = department("updated");
        update.setLockVersion(0);

        StepVerifier.create(service.update(99L, update))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(error.getMessage()).contains("不存在");
                })
                .verify();

        verifyNoInteractionsAfterLookup();
    }

    private void verifyNoInteractionsAfterLookup() {
        org.mockito.Mockito.verify(deptRepository).findById(99L);
        org.mockito.Mockito.verifyNoMoreInteractions(deptRepository);
    }

    private DeptEntity department(String name) {
        DeptEntity department = new DeptEntity();
        department.setName(name);
        department.setParentId(0L);
        return department;
    }
}
