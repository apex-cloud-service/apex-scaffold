package com.apex.scaffold.application.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apex.scaffold.application.dto.RoleDTO;
import com.apex.scaffold.application.dto.RoleRequest;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RoleAssemblerTest 单元测试。
 *
 * <p>覆盖核心正常流程、参数边界与失败分支，防止后续修改破坏既有行为。</p>
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleAssembler 单元测试")
class RoleAssemblerTest {

    private final RoleAssembler assembler = Mappers.getMapper(RoleAssembler.class);

    /**
     * 验证 {@code testToDTO} 场景。
     */
    @Test
    @DisplayName("toDTO - 将 RoleEntity 转换为 RoleDTO")
    void testToDTO() {
        LocalDateTime createTime = LocalDateTime.of(2025, 6, 1, 10, 0);
        RoleEntity entity = new RoleEntity();
        entity.setId(1L);
        entity.setName("Admin");
        entity.setCode("ADMIN");
        entity.setDescription("Administrator role");
        entity.setStatus(1);
        entity.setCreateTime(createTime);

        RoleDTO dto = assembler.toDTO(entity);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("Admin", dto.getName());
        assertEquals("ADMIN", dto.getCode());
        assertEquals("Administrator role", dto.getDescription());
        assertEquals(1, dto.getStatus());
        assertEquals(createTime, dto.getCreateTime());
    }

    /**
     * 验证 {@code testToEntityFromDTO} 场景。
     */
    @Test
    @DisplayName("toEntity - 将 RoleDTO 转换为 RoleEntity")
    void testToEntityFromDTO() {
        LocalDateTime createTime = LocalDateTime.of(2025, 6, 1, 10, 0);
        RoleDTO dto = new RoleDTO();
        dto.setId(2L);
        dto.setName("User");
        dto.setCode("USER");
        dto.setDescription("Regular user role");
        dto.setStatus(1);
        dto.setCreateTime(createTime);

        RoleEntity entity = assembler.toEntity(dto);

        assertNotNull(entity);
        assertEquals(2L, entity.getId());
        assertEquals("User", entity.getName());
        assertEquals("USER", entity.getCode());
        assertEquals("Regular user role", entity.getDescription());
        assertEquals(1, entity.getStatus());
        assertEquals(createTime, entity.getCreateTime());
    }

    /**
     * 验证 {@code testToEntityFromRequest} 场景。
     */
    @Test
    @DisplayName("toEntity - 将 RoleRequest 转换为 RoleEntity")
    void testToEntityFromRequest() {
        RoleRequest request = new RoleRequest();
        request.setId(3L);
        request.setName("Manager");
        request.setCode("MANAGER");
        request.setDescription("Manager role");
        request.setStatus(1);

        RoleEntity entity = assembler.fromRequest(request);

        assertNotNull(entity);
        assertEquals(3L, entity.getId());
        assertEquals("Manager", entity.getName());
        assertEquals("MANAGER", entity.getCode());
        assertEquals("Manager role", entity.getDescription());
        assertEquals(1, entity.getStatus());
    }

    /**
     * 验证 {@code testToEntity_Null} 场景。
     */
    @Test
    @DisplayName("toEntity - null 输入返回 null")
    void testToEntity_Null() {
        assertNull(assembler.toEntity((RoleDTO) null));
        assertNull(assembler.fromRequest(null));
    }

    /**
     * 验证 {@code testToDTOList} 场景。
     */
    @Test
    @DisplayName("toDTOList - 将 RoleEntity 列表转换为 RoleDTO 列表")
    void testToDTOList() {
        RoleEntity entity1 = new RoleEntity();
        entity1.setId(1L);
        entity1.setName("Admin");

        RoleEntity entity2 = new RoleEntity();
        entity2.setId(2L);
        entity2.setName("User");

        List<RoleDTO> dtoList = assembler.toDTOList(List.of(entity1, entity2));

        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());
        assertEquals("Admin", dtoList.get(0).getName());
        assertEquals("User", dtoList.get(1).getName());
    }
}
