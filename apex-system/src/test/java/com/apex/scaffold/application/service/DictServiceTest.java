package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.DictDataRequest;
import com.apex.scaffold.application.dto.admin.DictTypeRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.DictDataEntity;
import com.apex.scaffold.domain.model.entity.DictTypeEntity;
import com.apex.scaffold.domain.repository.DictDataRepository;
import com.apex.scaffold.domain.repository.DictTypeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 字典管理应用服务单元测试。
 *
 * <p>覆盖字典类型与字典数据的核心业务规则，重点验证类型编码唯一性、
 * 类型存在性校验与默认项唯一性管理。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock
    private DictTypeRepository dictTypeRepository;

    @Mock
    private DictDataRepository dictDataRepository;

    @Mock
    private TenantGuard tenantGuard;

    @InjectMocks
    private DictService dictService;

    /** 字典类型实体 */
    private DictTypeEntity typeEntity;

    /** 字典数据实体 */
    private DictDataEntity dataEntity;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
        typeEntity = DictTypeEntity.create("用户性别", "sys_user_sex", "性别字典");
        typeEntity.setId(1L);
        dataEntity = DictDataEntity.create(0, "男", "1", "sys_user_sex");
        dataEntity.setId(10L);
        lenient().when(tenantGuard.assertActive()).thenReturn(Mono.empty());
    }

    /**
     * 测试创建字典类型成功。
     */
    @Test
    @DisplayName("createType - 成功创建字典类型")
    void createType_Success() {
        DictTypeRequest request = new DictTypeRequest();
        request.setDictName("用户性别");
        request.setDictType("sys_user_sex");

        when(dictTypeRepository.existsByType("sys_user_sex", null)).thenReturn(Mono.just(false));
        when(dictTypeRepository.insert(any(DictTypeEntity.class))).thenReturn(Mono.just(1));

        dictService
                .createType(request)
                .as(StepVerifier::create)
                .assertNext(type -> {
                    assertThat(type.getDictType()).isEqualTo("sys_user_sex");
                    assertThat(type.isEnabled()).isTrue();
                })
                .verifyComplete();

        verify(dictTypeRepository).insert(any(DictTypeEntity.class));
    }

    /**
     * 测试创建重复编码的字典类型被拒绝。
     */
    @Test
    @DisplayName("createType - 编码已存在时抛出异常")
    void createType_DuplicateTypeFails() {
        DictTypeRequest request = new DictTypeRequest();
        request.setDictName("用户性别");
        request.setDictType("sys_user_sex");

        when(dictTypeRepository.existsByType("sys_user_sex", null)).thenReturn(Mono.just(true));

        dictService
                .createType(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(4712);
                })
                .verify();
    }

    /**
     * 测试删除含数据项的字典类型被拒绝。
     */
    @Test
    @DisplayName("deleteType - 类型下存在数据项时拒绝删除")
    void deleteType_HasDataFails() {
        when(dictTypeRepository.findById(1L)).thenReturn(Mono.just(typeEntity));
        when(dictDataRepository.existsByType("sys_user_sex")).thenReturn(Mono.just(true));

        dictService
                .deleteType(1L)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 测试删除无数据项的字典类型成功。
     */
    @Test
    @DisplayName("deleteType - 成功删除空字典类型")
    void deleteType_Success() {
        when(dictTypeRepository.findById(1L)).thenReturn(Mono.just(typeEntity));
        when(dictDataRepository.existsByType("sys_user_sex")).thenReturn(Mono.just(false));
        when(dictTypeRepository.deleteById(1L)).thenReturn(Mono.just(1));

        dictService.deleteType(1L).as(StepVerifier::create).expectNext(true).verifyComplete();
    }

    /**
     * 测试创建字典数据成功。
     */
    @Test
    @DisplayName("createData - 成功创建字典数据")
    void createData_Success() {
        DictDataRequest request = new DictDataRequest();
        request.setDictLabel("男");
        request.setDictValue("1");
        request.setDictType("sys_user_sex");
        request.setDictSort(0);

        when(dictTypeRepository.findByType("sys_user_sex")).thenReturn(Mono.just(typeEntity));
        when(dictDataRepository.insert(any(DictDataEntity.class))).thenReturn(Mono.just(1));

        dictService
                .createData(request)
                .as(StepVerifier::create)
                .assertNext(data -> {
                    assertThat(data.getDictValue()).isEqualTo("1");
                    assertThat(data.isDefaultItem()).isFalse();
                })
                .verifyComplete();
    }

    /**
     * 测试创建归属不存在类型的字典数据被拒绝。
     */
    @Test
    @DisplayName("createData - 字典类型不存在时抛出异常")
    void createData_TypeNotFoundFails() {
        DictDataRequest request = new DictDataRequest();
        request.setDictLabel("男");
        request.setDictValue("1");
        request.setDictType("sys_unknown");

        when(dictTypeRepository.findByType("sys_unknown")).thenReturn(Mono.empty());

        dictService
                .createData(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(4711);
                })
                .verify();
    }

    /**
     * 测试创建默认项时先清空同类型旧默认项。
     */
    @Test
    @DisplayName("createData - 默认项创建时先清空旧默认项")
    void createData_DefaultClearsOldDefaults() {
        DictDataRequest request = new DictDataRequest();
        request.setDictLabel("男");
        request.setDictValue("1");
        request.setDictType("sys_user_sex");
        request.setIsDefault(1);

        when(dictTypeRepository.findByType("sys_user_sex")).thenReturn(Mono.just(typeEntity));
        when(dictDataRepository.clearDefaultByType("sys_user_sex")).thenReturn(Mono.just(1));
        when(dictDataRepository.insert(any(DictDataEntity.class))).thenReturn(Mono.just(1));

        dictService
                .createData(request)
                .as(StepVerifier::create)
                .assertNext(data -> assertThat(data.isDefaultItem()).isTrue())
                .verifyComplete();

        verify(dictDataRepository).clearDefaultByType("sys_user_sex");
    }

    /**
     * 测试按类型查询字典数据。
     */
    @Test
    @DisplayName("listDataByType - 成功按类型查询启用数据项")
    void listDataByType_Success() {
        when(dictDataRepository.findByType("sys_user_sex")).thenReturn(Mono.just(List.of(dataEntity)));

        dictService
                .listDataByType("sys_user_sex")
                .as(StepVerifier::create)
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0).getDictValue()).isEqualTo("1");
                })
                .verifyComplete();
    }

    /**
     * 测试更新字典数据为默认项。
     */
    @Test
    @DisplayName("updateData - 更新为默认项时先清空旧默认项")
    void updateData_MarkDefaultClearsOld() {
        DictDataRequest request = new DictDataRequest();
        request.setDictLabel("男");
        request.setDictValue("1");
        request.setDictType("sys_user_sex");
        request.setIsDefault(1);

        when(dictDataRepository.findById(10L)).thenReturn(Mono.just(dataEntity));
        when(dictTypeRepository.findByType("sys_user_sex")).thenReturn(Mono.just(typeEntity));
        when(dictDataRepository.clearDefaultByType("sys_user_sex")).thenReturn(Mono.just(1));
        when(dictDataRepository.updateById(any(DictDataEntity.class))).thenReturn(Mono.just(1));

        dictService
                .updateData(10L, request)
                .as(StepVerifier::create)
                .assertNext(data -> assertThat(data.isDefaultItem()).isTrue())
                .verifyComplete();

        verify(dictDataRepository).clearDefaultByType("sys_user_sex");
    }
}
