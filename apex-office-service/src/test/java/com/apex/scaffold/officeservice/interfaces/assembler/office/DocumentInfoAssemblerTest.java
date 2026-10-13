package com.apex.scaffold.officeservice.interfaces.assembler.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.domain.document.DocumentInfo;
import com.apex.scaffold.office.domain.document.DocumentType;
import com.apex.scaffold.officeservice.interfaces.dto.office.document.DocumentInfoResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("DocumentInfoAssembler 单元测试")
class DocumentInfoAssemblerTest {

    private DocumentInfoAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = Mappers.getMapper(DocumentInfoAssembler.class);
    }

    @Test
    @DisplayName("toResponse 将文档类型枚举转为名称字符串")
    void toResponse() {
        DocumentInfo entity = DocumentInfo.builder()
                .id(1L)
                .title("需求文档")
                .documentType(DocumentType.DOC)
                .build();

        DocumentInfoResponse response = assembler.toResponse(entity);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("需求文档");
        assertThat(response.getDocumentType()).isEqualTo("DOC");
    }

    @Test
    @DisplayName("toResponseList 列表映射")
    void toResponseList() {
        DocumentInfo a =
                DocumentInfo.builder().id(1L).documentType(DocumentType.SHEET).build();
        DocumentInfo b =
                DocumentInfo.builder().id(2L).documentType(DocumentType.SLIDE).build();

        List<DocumentInfoResponse> list = assembler.toResponseList(List.of(a, b));

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getDocumentType()).isEqualTo("SHEET");
        assertThat(list.get(1).getDocumentType()).isEqualTo("SLIDE");
    }

    @Test
    @DisplayName("documentTypeToString 对 null 返回 null")
    void documentTypeNull() {
        assertThat(assembler.documentTypeToString(null)).isNull();
    }

    @Test
    @DisplayName("documentTypeToString 返回枚举名称")
    void documentTypeValue() {
        assertThat(assembler.documentTypeToString(DocumentType.FOLDER)).isEqualTo("FOLDER");
    }

    @Test
    @DisplayName("toResponse null 输入返回 null")
    void toResponseNull() {
        assertThat(assembler.toResponse(null)).isNull();
    }
}
