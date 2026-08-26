package com.apex.scaffold.infrastructure.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.model.object.PutObjectInput;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 火山云 TOS 存储服务单元测试。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
class VolcEngineStorageServiceImplTest {

    @Mock
    private TOSV2 tosClient;

    private VolcEngineStorageServiceImpl storageService;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        storageService = new VolcEngineStorageServiceImpl(tosClient);
    }

    /**
     * 验证 {@code shouldPreserveOriginalFileNameExactly} 场景。
     */
    @Test
    @DisplayName("doUpload - TOS对象key和返回值应保持原文件名完全一致")
    void shouldPreserveOriginalFileNameExactly() {
        String originalFileName = "照片目录/原 图.V1.JPG";
        ByteArrayInputStream content = new ByteArrayInputStream("image-content".getBytes(StandardCharsets.UTF_8));
        when(tosClient.putObject(any(PutObjectInput.class))).thenReturn(null);

        String savedFileName = storageService.doUpload("apex-photo", originalFileName, content, "image/jpeg");

        ArgumentCaptor<PutObjectInput> inputCaptor = ArgumentCaptor.forClass(PutObjectInput.class);
        verify(tosClient).putObject(inputCaptor.capture());
        assertThat(inputCaptor.getValue().getKey()).isEqualTo(originalFileName);
        assertThat(savedFileName).isSameAs(originalFileName);
    }
}
