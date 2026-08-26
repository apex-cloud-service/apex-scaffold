package com.apex.scaffold.application.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apex.scaffold.application.dto.OperationLogDTO;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OperationLogAssemblerTest 单元测试。
 *
 * <p>覆盖核心正常流程、参数边界与失败分支，防止后续修改破坏既有行为。</p>
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationLogAssembler 单元测试")
class OperationLogAssemblerTest {

    private final OperationLogAssembler assembler = Mappers.getMapper(OperationLogAssembler.class);

    /**
     * 验证 {@code testToDTO} 场景。
     */
    @Test
    @DisplayName("toDTO - 将 OperationLogEntity 转换为 OperationLogDTO")
    void testToDTO() {
        LocalDateTime createTime = LocalDateTime.of(2025, 6, 15, 14, 30);
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(1L);
        entity.setModule("User");
        entity.setOperationType("CREATE");
        entity.setDescription("Create new user");
        entity.setRequestMethod("POST");
        entity.setRequestUri("/api/users");
        entity.setStatus(0);
        entity.setUserId(100L);
        entity.setCreateTime(createTime);

        OperationLogDTO dto = assembler.toDTO(entity);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("User", dto.getModule());
        assertEquals("CREATE", dto.getOperationType());
        assertEquals("Create new user", dto.getDescription());
        assertEquals("POST", dto.getRequestMethod());
        assertEquals("/api/users", dto.getRequestUri());
        assertEquals(0, dto.getStatus());
        assertEquals(100L, dto.getUserId());
        assertEquals(createTime, dto.getCreateTime());
    }

    /**
     * 验证 {@code testToDTO_Null} 场景。
     */
    @Test
    @DisplayName("toDTO - 传入 null 返回 null")
    void testToDTO_Null() {
        assertNull(assembler.toDTO((OperationLogEntity) null));
    }

    /**
     * 验证 {@code testToEntity} 场景。
     */
    @Test
    @DisplayName("toEntity - 将 OperationLogDTO 转换为 OperationLogEntity")
    void testToEntity() {
        LocalDateTime createTime = LocalDateTime.of(2025, 6, 15, 15, 0);
        OperationLogDTO dto = new OperationLogDTO();
        dto.setId(2L);
        dto.setModule("Role");
        dto.setOperationType("UPDATE");
        dto.setDescription("Update role");
        dto.setRequestMethod("PUT");
        dto.setRequestUri("/api/roles/1");
        dto.setStatus(1);
        dto.setUserId(200L);
        dto.setCreateTime(createTime);

        OperationLogEntity entity = assembler.toEntity(dto);

        assertNotNull(entity);
        assertEquals(2L, entity.getId());
        assertEquals("Role", entity.getModule());
        assertEquals("UPDATE", entity.getOperationType());
        assertEquals("Update role", entity.getDescription());
        assertEquals("PUT", entity.getRequestMethod());
        assertEquals("/api/roles/1", entity.getRequestUri());
        assertEquals(1, entity.getStatus());
        assertEquals(200L, entity.getUserId());
        assertEquals(createTime, entity.getCreateTime());
    }

    /**
     * 验证 {@code testToEntity_Null} 场景。
     */
    @Test
    @DisplayName("toEntity - 传入 null 返回 null")
    void testToEntity_Null() {
        assertNull(assembler.toEntity((OperationLogDTO) null));
    }

    /**
     * 验证 {@code testToDTOList} 场景。
     */
    @Test
    @DisplayName("toDTOList - 将 OperationLogEntity 列表转换为 OperationLogDTO 列表")
    void testToDTOList() {
        OperationLogEntity entity1 = new OperationLogEntity();
        entity1.setId(1L);
        entity1.setModule("User");

        OperationLogEntity entity2 = new OperationLogEntity();
        entity2.setId(2L);
        entity2.setModule("Role");

        List<OperationLogDTO> dtoList = assembler.toDTOList(List.of(entity1, entity2));

        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());
        assertEquals("User", dtoList.get(0).getModule());
        assertEquals("Role", dtoList.get(1).getModule());
    }

    /**
     * 验证 {@code testToDTOList_Empty} 场景。
     */
    @Test
    @DisplayName("toDTOList - 传入空列表返回空列表")
    void testToDTOList_Empty() {
        List<OperationLogDTO> dtoList = assembler.toDTOList(List.of());
        assertNotNull(dtoList);
        assertEquals(0, dtoList.size());
    }
}
