package com.apex.scaffold.application.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apex.scaffold.application.dto.MeVO;
import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ProfileAssembler 单元测试
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileAssembler 单元测试")
class ProfileAssemblerTest {

    private final ProfileAssembler assembler = Mappers.getMapper(ProfileAssembler.class);

    /**
     * 验证 {@code testToMeVO} 场景。
     */
    @Test
    @DisplayName("toMeVO - 将 UserEntity + UserProfileEntity + UserComplianceEntity + maps 转换为 MeVO")
    void testToMeVO() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setNickName("TestUser");
        user.setGender(1);
        user.setPhone("13800138000");
        user.setOpenId("openId_123");
        user.setUnionId("unionId_456");

        UserProfileEntity profile = new UserProfileEntity();
        profile.setSocialUser(SystemConstants.BOOLEAN_TRUE);

        UserComplianceEntity compliance = new UserComplianceEntity();
        compliance.setTeenMode(SystemConstants.BOOLEAN_TRUE);

        Map<String, Object> sameFrequency = Map.of("enabled", true, "match", "high");
        Map<String, Object> tags = Map.of("personality", "INTJ", "interest", "reading");

        MeVO meVO = assembler.toMeVO(user, profile, compliance, sameFrequency, tags);

        assertNotNull(meVO);
        assertEquals("1", meVO.getUserId());
        assertEquals("TestUser", meVO.getNickName());
        assertEquals(1, meVO.getGender());
        assertEquals("13800138000", meVO.getPhone());
        assertEquals("openId_123", meVO.getOpenId());
        assertEquals("unionId_456", meVO.getUnionId());
        assertTrue(meVO.getTeenMode());
        assertTrue(meVO.getSocialUser());
        assertEquals(sameFrequency, meVO.getSameFrequency());
        assertEquals(tags, meVO.getTags());
        assertNull(meVO.getBirthday());
    }

    /**
     * 验证 {@code testToMeVO_NullProfile} 场景。
     */
    @Test
    @DisplayName("toMeVO - profile 为 null 时 socialUser 为 null")
    void testToMeVO_NullProfile() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setNickName("TestUser");

        MeVO meVO = assembler.toMeVO(user, null, null, null, null);

        assertNotNull(meVO);
        assertNull(meVO.getSocialUser());
        assertNull(meVO.getTeenMode());
    }

    /**
     * 验证 {@code testToMeVO_NullCompliance} 场景。
     */
    @Test
    @DisplayName("toMeVO - compliance 为 null 时 teenMode 为 null")
    void testToMeVO_NullCompliance() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setNickName("TestUser");
        UserProfileEntity profile = new UserProfileEntity();

        MeVO meVO = assembler.toMeVO(user, profile, null, null, null);

        assertNotNull(meVO);
        assertNull(meVO.getTeenMode());
    }

    /**
     * 验证 {@code testToMeVO_DisabledFlags} 场景。
     */
    @Test
    @DisplayName("toMeVO - teenMode/socialUser 为 0 时转换为 false")
    void testToMeVO_DisabledFlags() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setNickName("TestUser");

        UserProfileEntity profile = new UserProfileEntity();
        profile.setSocialUser(0);

        UserComplianceEntity compliance = new UserComplianceEntity();
        compliance.setTeenMode(0);

        MeVO meVO = assembler.toMeVO(user, profile, compliance, null, null);

        assertNotNull(meVO);
        assertFalse(meVO.getTeenMode());
        assertFalse(meVO.getSocialUser());
    }

    // ========== complianceToTeenMode 方法测试 ==========

    /**
     * 验证 {@code testComplianceToTeenMode_Null} 场景。
     */
    @Test
    @DisplayName("complianceToTeenMode - compliance 为 null 返回 null")
    void testComplianceToTeenMode_Null() {
        assertNull(assembler.complianceToTeenMode(null));
    }

    /**
     * 验证 {@code testComplianceToTeenMode_NullTeenMode} 场景。
     */
    @Test
    @DisplayName("complianceToTeenMode - teenMode 为 null 返回 null")
    void testComplianceToTeenMode_NullTeenMode() {
        UserComplianceEntity compliance = new UserComplianceEntity();
        assertNull(assembler.complianceToTeenMode(compliance));
    }

    /**
     * 验证 {@code testComplianceToTeenMode_True} 场景。
     */
    @Test
    @DisplayName("complianceToTeenMode - teenMode 为 BOOLEAN_TRUE 返回 true")
    void testComplianceToTeenMode_True() {
        UserComplianceEntity compliance = new UserComplianceEntity();
        compliance.setTeenMode(SystemConstants.BOOLEAN_TRUE);
        assertTrue(assembler.complianceToTeenMode(compliance));
    }

    /**
     * 验证 {@code testComplianceToTeenMode_False} 场景。
     */
    @Test
    @DisplayName("complianceToTeenMode - teenMode 为 0 返回 false")
    void testComplianceToTeenMode_False() {
        UserComplianceEntity compliance = new UserComplianceEntity();
        compliance.setTeenMode(0);
        assertFalse(assembler.complianceToTeenMode(compliance));
    }

    // ========== profileToSocialUser 方法测试 ==========

    /**
     * 验证 {@code testProfileToSocialUser_Null} 场景。
     */
    @Test
    @DisplayName("profileToSocialUser - profile 为 null 返回 null")
    void testProfileToSocialUser_Null() {
        assertNull(assembler.profileToSocialUser(null));
    }

    /**
     * 验证 {@code testProfileToSocialUser_NullSocialUser} 场景。
     */
    @Test
    @DisplayName("profileToSocialUser - socialUser 为 null 返回 null")
    void testProfileToSocialUser_NullSocialUser() {
        UserProfileEntity profile = new UserProfileEntity();
        assertNull(assembler.profileToSocialUser(profile));
    }

    /**
     * 验证 {@code testProfileToSocialUser_True} 场景。
     */
    @Test
    @DisplayName("profileToSocialUser - socialUser 为 BOOLEAN_TRUE 返回 true")
    void testProfileToSocialUser_True() {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setSocialUser(SystemConstants.BOOLEAN_TRUE);
        assertTrue(assembler.profileToSocialUser(profile));
    }

    /**
     * 验证 {@code testProfileToSocialUser_False} 场景。
     */
    @Test
    @DisplayName("profileToSocialUser - socialUser 为 0 返回 false")
    void testProfileToSocialUser_False() {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setSocialUser(0);
        assertFalse(assembler.profileToSocialUser(profile));
    }
}
