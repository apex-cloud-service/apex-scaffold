package com.apex.scaffold.infrastructure.office.spi;

import com.apex.scaffold.domain.office.OfficePlatform;
import reactor.core.publisher.Mono;

/**
 * 办公平台通用业务能力端口。
 */
public interface OfficePlatformClient {
    /**
     * 返回平台类型。
     * @return 平台类型
     */
    OfficePlatform platform();

    /**
     * 查询外部用户身份。
     * @param accessToken 平台访问令牌
     * @return 外部用户标识
     */
    Mono<String> resolveUser(String accessToken);
}
