package com.apex.scaffold.application.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.UserDTO;
import com.apex.scaffold.domain.model.entity.UserEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UserAssemblerTest 单元测试。
 *
 * <p>覆盖核心正常流程、参数边界与失败分支，防止后续修改破坏既有行为。</p>
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAssembler 单元测试")
class UserAssemblerTest {

    private final UserAssembler assembler = Mappers.getMapper(UserAssembler.class);

    @Test
    void shouldPreserveCredentialsDuringProfileUpdate() {
        UserEntity target = new UserEntity();
        target.setPassword("stored-hash");
        target.setAccessToken("stored-access");
        target.setRefreshToken("stored-refresh");
        UserEntity source = new UserEntity();
        source.setPassword("plaintext");
        source.setAccessToken("external-access");
        source.setRefreshToken("external-refresh");
        source.setNickName("updated");
        assembler.mergeUpdate(target, source);
        assertEquals("stored-hash", target.getPassword());
        assertEquals("stored-access", target.getAccessToken());
        assertEquals("stored-refresh", target.getRefreshToken());
        assertEquals("updated", target.getNickName());
    }

    /**
     * 验证 {@code testToDTO} 场景。
     */
    @Test
    @DisplayName("toDTO - 将 UserEntity 转换为 UserDTO")
    void testToDTO() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setOpenId("openId_123");
        entity.setUnionId("unionId_456");
        entity.setNickName("TestUser");
        entity.setAvatarUrl("https://example.com/avatar.png");
        entity.setGender(1);
        LocalDate birthday = LocalDate.of(2000, 1, 2);
        entity.setBirthday(birthday);
        entity.setPhone("13800138000");
        entity.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        entity.setLastLoginTime(now);
        entity.setCreateTime(now);

        UserDTO dto = assembler.toDTO(entity);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("openId_123", dto.getOpenId());
        assertEquals("unionId_456", dto.getUnionId());
        assertEquals("TestUser", dto.getNickName());
        assertEquals("https://example.com/avatar.png", dto.getAvatarUrl());
        assertEquals(1, dto.getGender());
        assertEquals(birthday, dto.getBirthday());
        assertEquals("13800138000", dto.getPhone());
        assertEquals(1, dto.getStatus());
        assertEquals(now, dto.getLastLoginTime());
        assertEquals(now, dto.getCreateTime());
    }

    /**
     * 验证 {@code testToDTO_Null} 场景。
     */
    @Test
    @DisplayName("toDTO - 传入 null 返回 null")
    void testToDTO_Null() {
        assertNull(assembler.toDTO((UserEntity) null));
    }

    /**
     * 验证 {@code testToDTOList} 场景。
     */
    @Test
    @DisplayName("toDTOList - 将 UserEntity 列表转换为 UserDTO 列表")
    void testToDTOList() {
        UserEntity entity1 = new UserEntity();
        entity1.setId(1L);
        entity1.setNickName("User1");

        UserEntity entity2 = new UserEntity();
        entity2.setId(2L);
        entity2.setNickName("User2");

        List<UserDTO> dtoList = assembler.toDTOList(List.of(entity1, entity2));

        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());
        assertEquals("User1", dtoList.get(0).getNickName());
        assertEquals("User2", dtoList.get(1).getNickName());
    }

    /**
     * 验证 {@code testToDTOList_Empty} 场景。
     */
    @Test
    @DisplayName("toDTOList - 传入空列表返回空列表")
    void testToDTOList_Empty() {
        List<UserDTO> dtoList = assembler.toDTOList(List.of());
        assertNotNull(dtoList);
        assertEquals(0, dtoList.size());
    }

    /**
     * 验证 {@code testToUserInfo} 场景。
     */
    @Test
    @DisplayName("toUserInfo - 将 UserEntity 转换为 LoginResponse.UserInfo")
    void testToUserInfo() {
        UserEntity entity = new UserEntity();
        entity.setNickName("InfoUser");
        entity.setAvatarUrl("https://example.com/info-avatar.png");
        entity.setGender(1);
        entity.setPhone("13700137000");
        entity.setCountry("China");
        entity.setProvince("Guangdong");
        entity.setCity("Shenzhen");

        LoginResponse.UserInfo userInfo = assembler.toUserInfo(entity);

        assertNotNull(userInfo);
        assertEquals("InfoUser", userInfo.getNickName());
        assertEquals("https://example.com/info-avatar.png", userInfo.getAvatarUrl());
        assertEquals(1, userInfo.getGender());
        assertEquals("13700137000", userInfo.getPhone());
        assertEquals("China", userInfo.getCountry());
        assertEquals("Guangdong", userInfo.getProvince());
        assertEquals("Shenzhen", userInfo.getCity());
    }

    /**
     * 验证 {@code testToLoginResponse} 场景。
     */
    @Test
    @DisplayName("toLoginResponse - 将 UserEntity 转换为 LoginResponse，token 字段被忽略")
    void testToLoginResponse() {
        UserEntity entity = new UserEntity();
        entity.setOpenId("openId_login");
        entity.setUnionId("unionId_login");
        entity.setNickName("LoginUser");
        entity.setAvatarUrl("https://example.com/login-avatar.png");
        entity.setGender(1);
        entity.setPhone("13600136000");
        entity.setCountry("China");

        LoginResponse response = assembler.toLoginResponse(entity);

        assertNotNull(response);
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertNull(response.getTokenType());
        assertNull(response.getExpiresIn());
        assertNull(response.getIsNewUser());
        assertEquals("openId_login", response.getOpenId());
        assertEquals("unionId_login", response.getUnionId());

        LoginResponse.UserInfo userInfo = response.getUserInfo();
        assertNotNull(userInfo);
        assertEquals("LoginUser", userInfo.getNickName());
        assertEquals("https://example.com/login-avatar.png", userInfo.getAvatarUrl());
        assertEquals(1, userInfo.getGender());
        assertEquals("China", userInfo.getCountry());
    }

    /**
     * 验证 {@code testMergeUpdate} 场景。
     */
    @Test
    @DisplayName("mergeUpdate - 将 source 非空字段合并到 target，忽略 id/createTime/updateTime/deleted/openId/unionId")
    void testMergeUpdate() {
        LocalDateTime originalCreateTime = LocalDateTime.of(2025, 1, 1, 10, 0);
        UserEntity target = new UserEntity();
        target.setId(100L);
        target.setOpenId("target_openId");
        target.setUnionId("target_unionId");
        target.setNickName("OldName");
        target.setAvatarUrl("https://example.com/old-avatar.png");
        target.setGender(1);
        target.setPhone("13500135000");
        target.setStatus(1);
        target.setCreateTime(originalCreateTime);
        target.setUpdateTime(originalCreateTime);
        target.setDeleted(0);

        UserEntity source = new UserEntity();
        source.setNickName("NewName");
        source.setAvatarUrl("https://example.com/new-avatar.png");
        source.setGender(null);
        source.setPhone(null);

        UserEntity result = assembler.mergeUpdate(target, source);

        assertNotNull(result);
        // id should be preserved
        assertEquals(100L, result.getId());
        // createTime should be preserved
        assertEquals(originalCreateTime, result.getCreateTime());
        // openId/unionId should be preserved
        assertEquals("target_openId", result.getOpenId());
        assertEquals("target_unionId", result.getUnionId());
        // deleted should be preserved
        assertEquals(0, result.getDeleted());
        // non-null fields from source should overwrite
        assertEquals("NewName", result.getNickName());
        assertEquals("https://example.com/new-avatar.png", result.getAvatarUrl());
        // null fields from source should not overwrite target
        assertEquals(1, result.getGender());
        assertEquals("13500135000", result.getPhone());
    }
}
