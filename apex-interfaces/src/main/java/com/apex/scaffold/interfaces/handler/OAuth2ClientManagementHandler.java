package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.OAuth2ClientRequest;
import com.apex.scaffold.application.service.OAuth2ClientManagementService;
import com.apex.scaffold.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * OAuth2客户端管理Handler
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2ClientManagementHandler {

    private final OAuth2ClientManagementService clientManagementService;

    public Mono<ServerResponse> createClient(ServerRequest request) {
        return request.bodyToMono(OAuth2ClientRequest.class)
                .flatMap(clientManagementService::createClient)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(response)));
    }

    public Mono<ServerResponse> updateClient(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return request.bodyToMono(OAuth2ClientRequest.class)
                .flatMap(req -> clientManagementService.updateClient(clientId, req))
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(response)));
    }

    public Mono<ServerResponse> deleteClient(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return clientManagementService
                .deleteClient(clientId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success("删除成功")));
    }

    public Mono<ServerResponse> getClient(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return clientManagementService.getClient(clientId).flatMap(response -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(response)));
    }

    public Mono<ServerResponse> listClients(ServerRequest request) {
        int page = request.queryParam("page").map(Integer::parseInt).orElse(1);
        int size = request.queryParam("size").map(Integer::parseInt).orElse(10);

        return clientManagementService
                .listClients(page, size)
                .collectList()
                .zipWith(clientManagementService.countClients())
                .flatMap(tuple -> {
                    var data = new java.util.HashMap<String, Object>();
                    data.put("list", tuple.getT1());
                    data.put("total", tuple.getT2());
                    data.put("page", page);
                    data.put("size", size);

                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(data));
                });
    }

    public Mono<ServerResponse> resetClientSecret(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return clientManagementService.resetClientSecret(clientId).flatMap(newSecret -> {
            var data = new java.util.HashMap<String, String>();
            data.put("clientId", clientId);
            data.put("clientSecret", newSecret);

            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Result.success(data));
        });
    }
}
