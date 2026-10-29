package com.apex.scaffold.file.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.file.domain.enums.StorageType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 验证精确策略选择与严格失败，阻止错误提供商写入。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class StorageServiceProviderTest {
    @Test
    void explicitSelectionNeverFallsBack() {
        StorageService local = Mockito.mock(StorageService.class);
        StorageServiceProvider provider =
                new StorageServiceProvider(Map.of(StorageType.LOCAL, local), StorageType.LOCAL);
        assertThat(provider.getDefaultStorageService()).isSameAs(local);
        assertThat(provider.getStorageService("local")).isSameAs(local);
        assertThatThrownBy(() -> provider.getStorageService(StorageType.MINIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getStorageService("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getStorageService((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StorageServiceProvider(Map.of(StorageType.LOCAL, local), StorageType.QINIU))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
