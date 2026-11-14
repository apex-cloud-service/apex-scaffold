package com.apex.scaffold.application.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apex.scaffold.application.dto.PermissionDTO;
import com.apex.scaffold.application.dto.PermissionRequest;
import com.apex.scaffold.domain.model.entity.PermissionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PermissionAssemblerTest 单元测试。
 *
 * <p>覆盖核心正常流程、参数边界与失败分支，防止后续修改破坏既有行为。</p>
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionAssembler 单元测试")
class PermissionAssemblerTest {

    private final PermissionAssembler assembler = Mappers.getMapper(PermissionAssembler.class);

    /**
     * 验证 {@code testToDTO} 场景。
     */
    @Test
    @DisplayName("toDTO - 将 PermissionEntity 转换为 PermissionDTO")
    void testToDTO() {
        LocalDateTime createTime = LocalDateTime.of(2025, 6, 1, 10, 0);
        PermissionEntity entity = new PermissionEntity();
        entity.setId(1L);
        entity.setName("Read User");
        entity.setCode("user:read");
        entity.setDescription("Read user permission");
        entity.setType(1);
        entity.setStatus(1);
        entity.setCreateTime(createTime);

        PermissionDTO dto = assembler.toDTO(entity);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("Read User", dto.getName());
        assertEquals("user:read", dto.getCode());
        assertEquals("Read user permission", dto.getDescription());
        assertEquals(1, dto.getType());
        assertEquals(1, dto.getStatus());
        assertEquals(createTime, dto.getCreateTime());
    }

    /**
     * 验证 {@code testToEntityFromDTO} 场景。
     */
    @Test
    @DisplayName("toEntity - 将 PermissionDTO 转换为 PermissionEntity")
    void testToEntityFromDTO() {
        LocalDateTime createTime = LocalDateTime.of(2025, 6, 1, 10, 0);
        PermissionDTO dto = new PermissionDTO();
        dto.setId(2L);
        dto.setName("Write User");
        dto.setCode("user:write");
        dto.setDescription("Write user permission");
        dto.setType(2);
        dto.setStatus(1);
        dto.setCreateTime(createTime);

        PermissionEntity entity = assembler.toEntity(dto);

        assertNotNull(entity);
        assertEquals(2L, entity.getId());
        assertEquals("Write User", entity.getName());
        assertEquals("user:write", entity.getCode());
        assertEquals("Write user permission", entity.getDescription());
        assertEquals(2, entity.getType());
        assertEquals(1, entity.getStatus());
        assertEquals(createTime, entity.getCreateTime());
    }

    /**
     * 验证 {@code testToEntityFromRequest} 场景。
     */
    @Test
    @DisplayName("toEntity - 将 PermissionRequest 转换为 PermissionEntity")
    void testToEntityFromRequest() {
        PermissionRequest request = new PermissionRequest();
        request.setId(3L);
        request.setName("Delete User");
        request.setCode("user:delete");
        request.setDescription("Delete user permission");
        request.setType(3);
        request.setStatus(0);

        PermissionEntity entity = assembler.fromRequest(request);

        assertNotNull(entity);
        assertEquals(3L, entity.getId());
        assertEquals("Delete User", entity.getName());
        assertEquals("user:delete", entity.getCode());
        assertEquals("Delete user permission", entity.getDescription());
        assertEquals(3, entity.getType());
        assertEquals(0, entity.getStatus());
    }

    /**
     * 验证 {@code testToEntity_Null} 场景。
     */
    @Test
    @DisplayName("toEntity - null 输入返回 null")
    void testToEntity_Null() {
        assertNull(assembler.toEntity((PermissionDTO) null));
        assertNull(assembler.fromRequest(null));
    }

    /**
     * 验证 {@code testToDTOList} 场景。
     */
    @Test
    @DisplayName("toDTOList - 将 PermissionEntity 列表转换为 PermissionDTO 列表")
    void testToDTOList() {
        PermissionEntity entity1 = new PermissionEntity();
        entity1.setId(1L);
        entity1.setName("Read");

        PermissionEntity entity2 = new PermissionEntity();
        entity2.setId(2L);
        entity2.setName("Write");

        List<PermissionDTO> dtoList = assembler.toDTOList(List.of(entity1, entity2));

        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());
        assertEquals("Read", dtoList.get(0).getName());
        assertEquals("Write", dtoList.get(1).getName());
    }
}
