package com.apex.scaffold.officeservice.interfaces.assembler.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.domain.approval.ApprovalStatus;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.ApprovalProcessResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("ApprovalProcessAssembler 单元测试")
class ApprovalProcessAssemblerTest {

    private ApprovalProcessAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = Mappers.getMapper(ApprovalProcessAssembler.class);
    }

    @Test
    @DisplayName("toResponse 映射审批流程字段并将审批人字符串转为列表")
    void toResponse() {
        ApprovalProcess entity = ApprovalProcess.builder()
                .id(1L)
                .processInstanceId("inst-1")
                .initiatorId("user-1")
                .initiatorName("张三")
                .status(ApprovalStatus.IN_PROGRESS)
                .currentApproverIds("u1,u2")
                .build();

        ApprovalProcessResponse response = assembler.toResponse(entity);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getProcessInstanceId()).isEqualTo("inst-1");
        assertThat(response.getInitiatorName()).isEqualTo("张三");
        assertThat(response.getStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(response.getCurrentApproverIds()).containsExactly("u1", "u2");
    }

    @Test
    @DisplayName("map 空字符串返回空列表")
    void mapBlank() {
        assertThat(assembler.map("   ")).isEmpty();
    }

    @Test
    @DisplayName("map null 返回空列表")
    void mapNull() {
        assertThat(assembler.map(null)).isEmpty();
    }

    @Test
    @DisplayName("map 逗号分隔字符串拆分为列表")
    void mapValue() {
        assertThat(assembler.map("a,b,c")).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("toResponse null 输入返回 null")
    void toResponseNull() {
        ApprovalProcessResponse response = assembler.toResponse(null);
        assertThat(response).isNull();
    }

    @Test
    @DisplayName("toResponse 映射审批记录列表")
    void toResponseRecords() {
        ApprovalProcess entity =
                ApprovalProcess.builder().id(1L).records(List.of()).build();

        ApprovalProcessResponse response = assembler.toResponse(entity);
        assertThat(response.getRecords()).isEmpty();
    }
}
