package com.apex.scaffold.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 基础画像更新字段的边界与成人年龄校验。
 *
 * @author weiqiang.ling
 */
class ProfileUpdateRequestValidationTest {

    /**
     * 验证 {@code acceptsSupportedDemographicProfile} 场景下的预期行为。
     *
     */
    @Test
    void acceptsSupportedDemographicProfile() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setGender(2);
        request.setBirthday(LocalDate.now().minusYears(25));
        request.setCity("上海");
        request.setHeightCm(168);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    /**
     * 验证 {@code rejectsUnknownGenderUnderageBirthdayAndOutOfRangeHeight} 场景下的预期行为。
     *
     */
    @Test
    void rejectsUnknownGenderUnderageBirthdayAndOutOfRangeHeight() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setGender(0);
        request.setBirthday(LocalDate.now().minusYears(17));
        request.setHeightCm(251);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("gender", "birthdayInSupportedRange", "heightCm");
        }
    }
}
