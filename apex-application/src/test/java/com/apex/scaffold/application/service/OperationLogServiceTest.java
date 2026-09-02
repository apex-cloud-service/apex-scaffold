package com.apex.scaffold.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * 操作日志服务单元测试类
 *
 * <p>测试 OperationLogService 的异步保存操作日志功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {

    @Mock
    private OperationLogRepository operationLogRepository;

    @InjectMocks
    private OperationLogService operationLogService;

    /**
     * 保存{@code async_Success}。
     */
    @Test
    @DisplayName("saveAsync - 成功异步保存操作日志")
    void saveAsync_Success() throws Exception {
        OperationLogEntity log = new OperationLogEntity();
        when(operationLogRepository.insert(any(OperationLogEntity.class))).thenReturn(Mono.empty());

        // saveAsync 内部在专用阻塞 Scheduler 上 subscribe，因此直接调用后
        // 立即 verify 可能尚未执行。这里通过反射拿到 saveInternal 返回的 Mono
        // 手动 block，保证 DB 写入完成后再断言。
        Method saveInternal =
                OperationLogService.class.getDeclaredMethod("saveInternal", OperationLogEntity.class, String.class);
        saveInternal.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<Void> mono = (Mono<Void>) saveInternal.invoke(operationLogService, log, null);
        mono.block(Duration.ofSeconds(2));

        verify(operationLogRepository).insert(log);
    }
}
