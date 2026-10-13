package com.apex.scaffold.interfaces.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.apex.scaffold.interfaces.config.DegradeSnapshotProperties;
import com.apex.scaffold.interfaces.exception.DegradeFallbackService;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * 降级快照保存决策记录器单元测试。
 *
 * <p>验证 {@link DegradeSnapshotRecorder} 的决策链路：总开关过滤、null 数据过滤、
 * 只读方法过滤、白名单命中判断以及保存委托，确保各安全约束不被绕过。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("DegradeSnapshotRecorder 快照保存决策测试")
class DegradeSnapshotRecorderTest {

    /** Mock 降级兜底服务，用于验证保存委托。 */
    private final DegradeFallbackService fallbackService = mock(DegradeFallbackService.class);

    /** 被测记录器。 */
    private DegradeSnapshotRecorder recorder;

    /** 白名单资源名，与被测场景一致。 */
    private static final String WHITELISTED_RESOURCE = "/api/v1/rankings/rules";

    /**
     * 初始化记录器：默认开启、白名单含 {@code /api/v1/rankings/rules}。
     */
    @BeforeEach
    void setUp() {
        DegradeSnapshotProperties properties = new DegradeSnapshotProperties();
        properties.setEnabled(true);
        properties.setResources(List.of(WHITELISTED_RESOURCE));
        recorder = new DegradeSnapshotRecorder(fallbackService, properties);
    }

    /**
     * 测试总开关关闭时，即使白名单命中也不应保存快照。
     */
    @Test
    @DisplayName("总开关关闭时应跳过保存")
    void shouldSkipWhenDisabled() {
        DegradeSnapshotProperties properties = new DegradeSnapshotProperties();
        properties.setEnabled(false);
        properties.setResources(List.of(WHITELISTED_RESOURCE));
        DegradeSnapshotRecorder disabledRecorder = new DegradeSnapshotRecorder(fallbackService, properties);

        disabledRecorder.saveDegradeSnapshotIfNeeded(getRequest(WHITELISTED_RESOURCE), "data");

        verify(fallbackService, never()).saveSnapshot(anyString(), any());
    }

    /**
     * 测试响应数据为 null 时应跳过保存。
     */
    @Test
    @DisplayName("null响应数据应跳过保存")
    void shouldSkipWhenDataNull() {
        recorder.saveDegradeSnapshotIfNeeded(getRequest(WHITELISTED_RESOURCE), null);

        verify(fallbackService, never()).saveSnapshot(anyString(), any());
    }

    /**
     * 测试非只读方法（POST）不应保存快照，写接口降级走排队语义。
     */
    @Test
    @DisplayName("写方法不应保存快照")
    void shouldSkipForWriteMethod() {
        MockServerRequest postRequest = MockServerRequest.builder()
                .method(HttpMethod.POST)
                .uri(URI.create(WHITELISTED_RESOURCE))
                .build();

        recorder.saveDegradeSnapshotIfNeeded(postRequest, "data");

        verify(fallbackService, never()).saveSnapshot(anyString(), any());
    }

    /**
     * 测试资源不在白名单时应跳过保存，默认空白名单对现有功能零影响。
     */
    @Test
    @DisplayName("白名单未命中应跳过保存")
    void shouldSkipWhenResourceNotWhitelisted() {
        recorder.saveDegradeSnapshotIfNeeded(getRequest("/api/v1/eyesight/me"), "data");

        verify(fallbackService, never()).saveSnapshot(anyString(), any());
    }

    /**
     * 测试白名单命中且为只读方法时，应委托保存归一化资源名的快照。
     */
    @Test
    @DisplayName("白名单命中应委托保存快照")
    void shouldSaveSnapshotWhenWhitelisted() {
        recorder.saveDegradeSnapshotIfNeeded(getRequest(WHITELISTED_RESOURCE), "data");

        verify(fallbackService).saveSnapshot(WHITELISTED_RESOURCE, "data");
    }

    /**
     * 构造指定路径的 GET 请求。
     *
     * @param path 请求路径
     * @return GET 请求
     */
    private static ServerRequest getRequest(String path) {
        return MockServerRequest.builder()
                .method(HttpMethod.GET)
                .uri(URI.create(path))
                .build();
    }
}
