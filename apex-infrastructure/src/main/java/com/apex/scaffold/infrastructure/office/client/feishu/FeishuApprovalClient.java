package com.apex.scaffold.infrastructure.office.client.feishu;

import com.apex.scaffold.infrastructure.office.client.ApprovalClient;
import com.apex.scaffold.infrastructure.office.config.OfficeAppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 飞书审批客户端实现
 *
 * <p>封装飞书开放平台审批相关API调用，包括：
 * <ul>
 *   <li>创建审批实例</li>
 *   <li>查询审批状态和详情</li>
 *   <li>执行审批操作（同意/拒绝等）</li>
 *   <li>撤销审批</li>
 * </ul>
 *
 * <p>API文档：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/approval-v4/instance/create
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuApprovalClient implements ApprovalClient {
    
    private static final String API_BASE_URL = "https://open.feishu.cn/open-apis";
    private static final String CREATE_INSTANCE_URL = "/approval/v4/instances";
    private static final String GET_INSTANCE_URL = "/approval/v4/instances/{instance_id}";
    private static final String APPROVE_URL = "/approval/v4/instances/{instance_id}/approve";
    private static final String CANCEL_URL = "/approval/v4/instances/{instance_id}/cancel";
    
    private final WebClient webClient;
    private final OfficeAppConfig appConfig;
    
    @Override
    public Mono<String> createApproval(Long appId, String processDefinitionId, 
                                       String initiatorId, String formData) {
        log.info("飞书创建审批，应用：{}，流程定义：{}，发起人：{}", appId, processDefinitionId, initiatorId);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of(
                            "approval_code", processDefinitionId,
                            "user_id", initiatorId,
                            "form", formData
                    );
                    
                    return webClient.post()
                            .uri(API_BASE_URL + CREATE_INSTANCE_URL)
                            .header("Authorization", "Bearer " + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> {
                                Map<String, Object> data = (Map<String, Object>) response.get("data");
                                return (String) data.get("instance_code");
                            });
                })
                .doOnSuccess(instanceId -> log.info("飞书审批创建成功，实例ID：{}", instanceId))
                .doOnError(e -> log.error("飞书创建审批失败", e));
    }
    
    @Override
    public Mono<String> getApprovalStatus(Long appId, String platformInstanceId) {
        log.debug("查询飞书审批状态，实例：{}", platformInstanceId);
        
        return getAccessToken(appId)
                .flatMap(token -> webClient.get()
                        .uri(API_BASE_URL + GET_INSTANCE_URL, platformInstanceId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> {
                            Map<String, Object> data = (Map<String, Object>) response.get("data");
                            return (String) data.get("status");
                        }))
                .doOnSuccess(status -> log.debug("飞书审批状态：{}", status));
    }
    
    @Override
    public Mono<String> approveProcess(Long appId, String platformInstanceId, 
                                       String approverId, String action, String comment) {
        log.info("飞书执行审批，实例：{}，审批人：{}，操作：", platformInstanceId, approverId, action);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of(
                            "approval_code", platformInstanceId,
                            "user_id", approverId,
                            "comment", comment,
                            "action", action
                    );
                    
                    return webClient.post()
                            .uri(API_BASE_URL + APPROVE_URL, platformInstanceId)
                            .header("Authorization", "Bearer " + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> {
                                Map<String, Object> data = (Map<String, Object>) response.get("data");
                                return (String) data.get("status");
                            });
                })
                .doOnSuccess(status -> log.info("飞书审批操作完成，新状态：{}", status))
                .doOnError(e -> log.error("飞书审批操作失败", e));
    }
    
    @Override
    public Mono<Boolean> cancelApproval(Long appId, String platformInstanceId) {
        log.info("撤销飞书审批，实例：{}", platformInstanceId);
        
        return getAccessToken(appId)
                .flatMap(token -> webClient.post()
                        .uri(API_BASE_URL + CANCEL_URL, platformInstanceId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> {
                            Integer code = (Integer) response.get("code");
                            return code == 0;
                        }))
                .doOnSuccess(success -> log.info("飞书审批撤销结果：{}", success))
                .doOnError(e -> log.error("飞书撤销审批失败", e));
    }
    
    @Override
    public Mono<String> getApprovalDetail(Long appId, String platformInstanceId) {
        log.debug("查询飞书审批详情，实例：{}", platformInstanceId);
        
        return getAccessToken(appId)
                .flatMap(token -> webClient.get()
                        .uri(API_BASE_URL + GET_INSTANCE_URL, platformInstanceId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(String.class))
                .doOnSuccess(detail -> log.debug("飞书审批详情查询成功"));
    }
    
    /**
     * 获取访问令牌
     *
     * @param appId 应用ID
     * @return 访问令牌
     */
    private Mono<String> getAccessToken(Long appId) {
        // 实际实现应该从缓存或配置中获取token
        // 这里简化处理
        return Mono.just("mock_access_token");
    }
}
