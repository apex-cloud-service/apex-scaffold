package com.apex.scaffold.infrastructure.cache.wrapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LogicalExpiryWrapperTest {

    @Test
    void of_shouldCreateWrapperWithCorrectExpireTime() {
        String data = "test-data";
        long ttlSeconds = 3600L;

        Instant before = Instant.now().plusSeconds(ttlSeconds).minusSeconds(1);
        LogicalExpiryWrapper<String> wrapper = LogicalExpiryWrapper.of(data, ttlSeconds);
        Instant after = Instant.now().plusSeconds(ttlSeconds).plusSeconds(1);

        assertEquals(data, wrapper.getData());
        assertNotNull(wrapper.getLogicalExpireTime());
        assertTrue(wrapper.getLogicalExpireTime().isAfter(before));
        assertTrue(wrapper.getLogicalExpireTime().isBefore(after));
    }

    @Test
    void isExpired_shouldReturnFalse_whenNotExpired() {
        LogicalExpiryWrapper<String> wrapper = LogicalExpiryWrapper.of("test", 3600L);

        assertFalse(wrapper.isExpired());
    }

    @Test
    void isExpired_shouldReturnTrue_whenExpired() {
        LogicalExpiryWrapper<String> wrapper = new LogicalExpiryWrapper<>();
        wrapper.setData("test");
        wrapper.setLogicalExpireTime(Instant.now().minusSeconds(10));

        assertTrue(wrapper.isExpired());
    }

    @Test
    void isExpired_shouldReturnFalse_whenExpireTimeIsNull() {
        LogicalExpiryWrapper<String> wrapper = new LogicalExpiryWrapper<>();
        wrapper.setData("test");
        wrapper.setLogicalExpireTime(null);

        assertFalse(wrapper.isExpired());
    }
}
