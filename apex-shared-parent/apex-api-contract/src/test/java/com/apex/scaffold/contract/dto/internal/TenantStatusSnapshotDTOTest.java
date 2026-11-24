package com.apex.scaffold.contract.dto.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TenantStatusSnapshotDTO} 租户状态快照契约测试.
 *
 * <p>该 DTO 是 {@code GET /internal/v1/tenants/{code}/status} 的跨服务契约载体
 * （system 提供方与 auth-service 消费方共用），测试锁定字段名与 JSON 往返语义，
 * 防止任一端字段演进而另一方无感知漂移（2026-11-07 模块评审 F3）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("TenantStatusSnapshotDTO 租户状态快照契约测试")
class TenantStatusSnapshotDTOTest {

    @Test
    @DisplayName("builder 构建完整快照，未知租户可表达 exists=false")
    void builderBuildsSnapshot() {
        TenantStatusSnapshotDTO snapshot = TenantStatusSnapshotDTO.builder()
                .exists(true)
                .status("ACTIVE")
                .expired(false)
                .maxUsers(100)
                .maxStorageMb(10240)
                .build();

        assertThat(snapshot.getExists()).isTrue();
        assertThat(snapshot.getStatus()).isEqualTo("ACTIVE");
        assertThat(snapshot.getExpired()).isFalse();
        assertThat(snapshot.getMaxUsers()).isEqualTo(100);
        assertThat(snapshot.getMaxStorageMb()).isEqualTo(10240);

        TenantStatusSnapshotDTO unknown = TenantStatusSnapshotDTO.builder().build();
        assertThat(unknown.getExists()).isFalse();
        assertThat(unknown.getExpired()).isFalse();
        assertThat(unknown.getStatus()).isNull();
        assertThat(unknown.getMaxUsers()).isNull();
    }

    @Test
    @DisplayName("JSON 往返保持字段名与值语义（Result 封装 + data 节点）")
    void jsonRoundTripKeepsContractShape() {
        TenantStatusSnapshotDTO source = TenantStatusSnapshotDTO.builder()
                .exists(true)
                .status("ACTIVE")
                .expired(true)
                .maxUsers(50)
                .maxStorageMb(null)
                .build();
        String body = JsonUtil.toJson(Result.success(source));
        assertThat(JsonUtil.getObjectMapper().valueToTree(source).path("exists").isBoolean())
                .isTrue();
        assertThat(JsonUtil.getObjectMapper()
                        .valueToTree(source)
                        .path("expired")
                        .isBoolean())
                .isTrue();

        Result<TenantStatusSnapshotDTO> parsed =
                JsonUtil.fromJson(body, new TypeReference<Result<TenantStatusSnapshotDTO>>() {});

        assertThat(parsed).isNotNull();
        assertThat(parsed.getData()).isNotNull();
        assertThat(parsed.getData().getExists()).isTrue();
        assertThat(parsed.getData().getStatus()).isEqualTo("ACTIVE");
        assertThat(parsed.getData().getExpired()).isTrue();
        assertThat(parsed.getData().getMaxUsers()).isEqualTo(50);
        assertThat(parsed.getData().getMaxStorageMb()).isNull();
    }

    @Test
    @DisplayName("缺省及显式 null 均沿用 false")
    void missingAndNullFlagsKeepFalseDefaults() {
        for (String json : new String[] {"{}", "{\"exists\":null,\"expired\":null}"}) {
            TenantStatusSnapshotDTO snapshot = JsonUtil.fromJson(json, TenantStatusSnapshotDTO.class);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getExists()).isFalse();
            assertThat(snapshot.getExpired()).isFalse();
        }
    }
}
