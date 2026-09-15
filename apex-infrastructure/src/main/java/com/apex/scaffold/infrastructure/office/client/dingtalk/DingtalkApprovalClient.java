package com.apex.scaffold.infrastructure.office.client.dingtalk;

import com.apex.scaffold.infrastructure.office.client.ApprovalClient;
import com.apex.scaffold.infrastructure.office.config.OfficeAppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 钉钉审批客户端实现
 *
 * <p>封装钉钉开放平台审批相关API调用，包括：
 * <ul>
 *   <li>创建审批实例</li>
 *   <li>查询审批状态和详情</li>
 *   <li>执行审批操作（同意/拒绝等）</li>
 *   <li>撤销审批</li>
 * </ul>
 *
 * <p>API文档：https://open.dingtalk.com/document/orgapp/approval-process-instance-management
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingtalkApprovalClient implements ApprovalClient {
    
    private static final String API_BASE_URL = "https://oapi.dingtalk.com";
    private static final String CREATE_INSTANCE_URL = "/topapi/processinstance/create";
    private static final String GET_INSTANCE_URL = "/topapi/processinstance/get";
    private static final String APPROVE_URL = "/topapi/process/workrecord/taskgroup/cancel";
    
    private final WebClient webClient;
    private final OfficeAppConfig appConfig;
    
    @Override
    public Mono<String> createApproval(Long appId, String processDefinitionId, 
                                       String initiatorId, String formData) {
        log.info("钉钉创建审批，应用：{}，流程定义：{}，发起人：{}", appId, processDefinitionId, initiatorId);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of(
                            "process_code", processDefinitionId,
                            "originator_user_id", initiatorId,
                            "form_component_values", formData
                    );
                    
                    return webClient.post()
                            .uri(API_BASE_URL + CREATE_INSTANCE_URL + "?access_token=" + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> {
                                return (String) response.get("process_instance_id");
                            });
                })
                .doOnSuccess(instanceId -> log.info("钉钉审批创建成功，实例ID：{}", instanceId))
                .doOnError(e -> log.error("钉钉创建审批失败", e));
    }
    
    @Override
    public Mono<String> getApprovalStatus(Long appId, String platformInstanceId) {
        log.debug("查询钉钉审批状态，实例：{}", platformInstanceId);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of("process_instance_id", platformInstanceId);
                    
                    return webClient.post()
                            .uri(API_BASE_URL + GET_INSTANCE_URL + "?access_token=" + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> {
                                Map<String, Object> result = (Map<String, Object>) response.get("process_instance");
                                return (String) result.get("status");
                            });
                })
                .doOnSuccess(status -> log.debug("钉钉审批状态：{}", status));
    }
    
    @Override
    public Mono<String> approveProcess(Long appId, String platformInstanceId, 
                                       String approverId, String action, String comment) {
        log.info("钉钉执行审批，实例：{}，审批人：{}，操作：{}", platformInstanceId, approverId, action);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of(
                            "process_instance_id", platformInstanceId,
                            "user_id", approverId,
                            "remark", comment,
                            "result", action.equals("APPROVE") ? "agree" : "refuse"
                    );
                    
                    return webClient.post()
                            .uri(API_BASE_URL + "/topapi/processinstance/execute?access_token=" + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .flatMap(response -> getApprovalStatus(appId, platformInstanceId));
                })
                .doOnSuccess(status -> log.info("钉钉审批操作完成，新状态：{}", status))
                .doOnError(e -> log.error("钉钉审批操作失败", e));
    }
    
    @Override
    public Mono<Boolean> cancelApproval(Long appId, String platformInstanceId) {
        log.info("撤销钉钉审批，实例：{}", platformInstanceId);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of("process_instance_id", platformInstanceId);
                    
                    return webClient.post()
                            .uri(API_BASE_URL + "/topapi/processinstance/terminate?access_token=" + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> {
                                Integer errcode = (Integer) response.get("errcode");
                                return errcode == 0;
                            });
                })
                .doOnSuccess(success -> log.info("钉钉审批撤销结果：{}", success))
                .doOnError(e -> log.error("钉钉撤销审批失败", e));
    }
    
    @Override
    public Mono<String> getApprovalDetail(Long appId, String platformInstanceId) {
        log.debug("查询钉钉审批详情，实例：{}", platformInstanceId);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of("process_instance_id", platformInstanceId);
                    
                    return webClient.post()
                            .uri(API_BASE_URL + GET_INSTANCE_URL + "?access_token=" + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class);
                })
                .doOnSuccess(detail -> log.debug("钉钉审批详情查询成功"));
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
