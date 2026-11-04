package com.apex.scaffold.domain.gateway;

import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** auth开放应用注册源端口；system不依赖auth业务库。 */
public interface OpenAppRegistryGateway {
    Mono<OpenAppRegistrationResponse> create(OpenAppRegistrationRequest request);

    Mono<OpenAppRegistrationResponse> update(String clientId, OpenAppRegistrationRequest request);

    Mono<OpenAppRegistrationResponse> get(String clientId);

    Flux<OpenAppRegistrationResponse> list(int page, int size);

    Mono<OpenAppRegistrationResponse> rotateSecret(String clientId);

    Mono<Void> delete(String clientId);

    /** 导入一条旧源开放应用；幂等由 importId + 指纹保证。 */
    Mono<com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse> importApp(
            com.apex.scaffold.contract.dto.internal.OpenAppImportRequest request);
}
