package com.apex.scaffold.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PageRequest 校验测试")
class PageRequestValidationTest {

    @Test
    @DisplayName("pageSize 超过100时应校验失败")
    void rejectsPageSizeAboveMaximum() {
        PageRequest request = new PageRequest();
        request.setPageSize(101);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("pageSize");
        }
    }

    @Test
    @DisplayName("应保留排序字段和升序标记")
    void keepsSortFields() {
        PageRequest request = new PageRequest();
        request.setOrderBy("create_time");
        request.setIsAsc(false);

        assertThat(request.getOrderBy()).isEqualTo("create_time");
        assertThat(request.getIsAsc()).isFalse();
    }
}
