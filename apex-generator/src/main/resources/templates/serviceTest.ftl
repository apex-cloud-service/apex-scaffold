package ${appPackage}.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ${appPackage}.dto.${module}.${entityName}DTO;
import ${appPackage}.dto.${module}.${entityName}Request;
import ${domainPackage}.model.entity.${entityName}Entity;
import ${domainPackage}.repository.${entityName}Repository;
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
 * ${tableComment}应用服务单元测试。
 *
 * <p>覆盖${tableComment}查询、创建、更新与删除的核心业务规则。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ${entityName}ServiceTest {

    @Mock
    private ${entityName}Repository ${entityVar}Repository;

    @InjectMocks
    private ${entityName}Service ${entityVar}Service;

    /** ${tableComment}实体 */
    private ${entityName}Entity entity;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
<#list fields as field>
        ${field.type} ${field.name} = null;
</#list>
        entity = ${entityName}Entity.create(
<#list fields as field>
                ${field.name}<#if field?has_next>,</#if>
</#list>);
        entity.setId(1L);
    }

    /**
     * 测试创建${tableComment}成功。
     */
    @Test
    @DisplayName("create - 成功创建${tableComment}")
    void create_Success() {
        ${entityName}Request request = new ${entityName}Request();

        when(${entityVar}Repository.insert(any(${entityName}Entity.class))).thenReturn(Mono.just(1));

        ${entityVar}Service
                .create(request)
                .as(StepVerifier::create)
                .assertNext(created -> assertThat(created.getId()).isEqualTo(1L))
                .verifyComplete();

        verify(${entityVar}Repository).insert(any(${entityName}Entity.class));
    }

    /**
     * 测试查询${tableComment}列表。
     */
    @Test
    @DisplayName("list - 成功查询全部${tableComment}")
    void list_Success() {
        when(${entityVar}Repository.findAll()).thenReturn(Mono.just(List.of(entity)));

        ${entityVar}Service
                .list()
                .as(StepVerifier::create)
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0)).isInstanceOf(${entityName}DTO.class);
                })
                .verifyComplete();
    }

    /**
     * 测试删除${tableComment}成功。
     */
    @Test
    @DisplayName("delete - 成功删除${tableComment}")
    void delete_Success() {
        when(${entityVar}Repository.findById(1L)).thenReturn(Mono.just(entity));
        when(${entityVar}Repository.deleteById(1L)).thenReturn(Mono.just(1));

        ${entityVar}Service
                .delete(1L)
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }
}
