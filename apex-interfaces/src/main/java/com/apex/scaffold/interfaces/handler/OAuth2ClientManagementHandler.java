package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.OAuth2ClientRequest;
import com.apex.scaffold.application.service.OAuth2ClientManagementService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * OAuth2 客户端管理处理器（函数式端点）。
 *
 * <p>提供 OAuth2 客户端应用的完整生命周期管理 API，包括客户端的创建、
 * 查询、更新、删除与密钥重置，支撑 OAuth2 授权服务器的客户端注册与管理。
 * 客户端密钥（clientSecret）采用 BCrypt 加密存储，密钥明文仅在创建与重置时返回一次。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>POST /oauth2/clients - 创建 OAuth2 客户端</li>
 *   <li>GET /oauth2/clients - 分页查询客户端列表</li>
 *   <li>GET /oauth2/clients/{clientId} - 查询客户端详情</li>
 *   <li>PUT /oauth2/clients/{clientId} - 更新客户端配置</li>
 *   <li>DELETE /oauth2/clients/{clientId} - 删除客户端及其令牌</li>
 *   <li>POST /oauth2/clients/{clientId}/reset-secret - 重置客户端密钥</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see OAuth2ClientManagementService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "OAuth2客户端管理", description = "OAuth2客户端的增删改查和密钥管理接口")
public class OAuth2ClientManagementHandler {

    private final OAuth2ClientManagementService clientManagementService;

    @RequirePermission("system:oauth2:client:create")
    @OperationLog(module = "OAuth2客户端管理", type = OperationLog.OperationType.INSERT, description = "创建OAuth2客户端")
    @Operation(
            operationId = "createOAuth2Client",
            summary = "创建OAuth2客户端",
            description = "创建新的OAuth2客户端应用,生成clientId和clientSecret")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "客户端创建成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> createClient(ServerRequest request) {
        return request.bodyToMono(OAuth2ClientRequest.class)
                .flatMap(clientManagementService::createClient)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(response)));
    }

    @RequirePermission("system:oauth2:client:update")
    @OperationLog(module = "OAuth2客户端管理", type = OperationLog.OperationType.UPDATE, description = "更新OAuth2客户端")
    @Operation(operationId = "updateOAuth2Client", summary = "更新OAuth2客户端", description = "更新指定客户端的配置信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "客户端更新成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "客户端不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> updateClient(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return request.bodyToMono(OAuth2ClientRequest.class)
                .flatMap(req -> clientManagementService.updateClient(clientId, req))
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(response)));
    }

    @RequirePermission("system:oauth2:client:delete")
    @OperationLog(module = "OAuth2客户端管理", type = OperationLog.OperationType.DELETE, description = "删除OAuth2客户端")
    @Operation(operationId = "deleteOAuth2Client", summary = "删除OAuth2客户端", description = "删除指定的OAuth2客户端及其相关的令牌")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "客户端删除成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "客户端不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> deleteClient(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return clientManagementService
                .deleteClient(clientId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success("删除成功")));
    }

    @RequirePermission("system:oauth2:client:query")
    @Operation(operationId = "getOAuth2Client", summary = "查询OAuth2客户端详情", description = "根据clientId查询客户端的详细信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "客户端不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getClient(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return clientManagementService.getClient(clientId).flatMap(response -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(response)));
    }

    @RequirePermission("system:oauth2:client:query")
    @Operation(operationId = "listOAuth2Clients", summary = "分页查询OAuth2客户端列表", description = "分页查询所有OAuth2客户端")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功,返回客户端列表和分页信息",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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

    @RequirePermission("system:oauth2:client:secret:reset")
    @OperationLog(module = "OAuth2客户端管理", type = OperationLog.OperationType.UPDATE, description = "重置OAuth2客户端密钥")
    @Operation(operationId = "resetOAuth2ClientSecret", summary = "重置客户端密钥", description = "重新生成指定客户端的clientSecret")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "密钥重置成功,返回新的clientSecret",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "客户端不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
