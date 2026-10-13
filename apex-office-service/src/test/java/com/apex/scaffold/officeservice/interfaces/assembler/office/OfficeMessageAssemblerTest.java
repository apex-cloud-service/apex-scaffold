package com.apex.scaffold.officeservice.interfaces.assembler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.domain.message.MessageContent;
import com.apex.scaffold.office.domain.message.MessageType;
import com.apex.scaffold.office.domain.message.OfficeMessage;
import com.apex.scaffold.officeservice.interfaces.dto.office.message.MessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("OfficeMessageAssembler 单元测试")
class OfficeMessageAssemblerTest {

    private OfficeMessageAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = Mappers.getMapper(OfficeMessageAssembler.class);
    }

    @Test
    @DisplayName("toResponse 将消息内容对象转为文本")
    void toResponse() {
        MessageContent content = mock(MessageContent.class);
        when(content.getText()).thenReturn("hello");

        OfficeMessage message = OfficeMessage.builder()
                .id(1L)
                .messageType(MessageType.TEXT)
                .content(content)
                .build();

        MessageResponse response = assembler.toResponse(message);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessageType()).isEqualTo(MessageType.TEXT);
        assertThat(response.getContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("mapContent 对 null 返回 null")
    void mapContentNull() {
        assertThat(assembler.mapContent(null)).isNull();
    }

    @Test
    @DisplayName("mapContent 返回内容文本")
    void mapContentValue() {
        MessageContent content = mock(MessageContent.class);
        when(content.getText()).thenReturn("text");
        assertThat(assembler.mapContent(content)).isEqualTo("text");
    }

    @Test
    @DisplayName("toResponse null 输入返回 null")
    void toResponseNull() {
        assertThat(assembler.toResponse(null)).isNull();
    }
}
