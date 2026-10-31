package com.apex.scaffold.file.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.file.domain.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** 验证上传流读取延迟到订阅并使用存储专用线程池。 */
class FileStreamIsolationTest {
    /** 验证组装上传链时不会读取流，订阅读取不占用请求线程。 */
    @Test
    void readsOnlyOnStorageScheduler() {
        StorageService storage = mock(StorageService.class);
        when(storage.upload(anyString(), anyString(), any(InputStream.class), anyString()))
                .thenReturn(Mono.just("stored.jpg"));
        com.apex.scaffold.file.domain.repository.FileRepository repository =
                mock(com.apex.scaffold.file.domain.repository.FileRepository.class);
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        FileService service = new FileService(
                storage,
                repository,
                new com.apex.scaffold.file.domain.service.StorageServiceProvider(
                        java.util.Map.of(com.apex.scaffold.file.domain.enums.StorageType.LOCAL, storage),
                        com.apex.scaffold.file.domain.enums.StorageType.LOCAL),
                "local",
                "apex",
                1024,
                300,
                (code, bytes) -> Mono.empty());
        AtomicBoolean read = new AtomicBoolean();
        AtomicReference<String> thread = new AtomicReference<>();
        InputStream stream = new ByteArrayInputStream(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}) {
            @Override
            public byte[] readAllBytes() {
                read.set(true);
                thread.set(Thread.currentThread().getName());
                return super.readAllBytes();
            }
        };
        Mono<String> upload = service.upload("apex", "photo.jpg", stream, "image/jpeg");
        assertThat(read).isFalse();
        StepVerifier.create(
                        upload.contextWrite(c -> c.put(FileAccessContext.KEY, new FileAccessContext("tenant-a", 7L))))
                .expectNext("stored.jpg")
                .verifyComplete();
        assertThat(thread.get()).contains("storage");
    }
}
