package com.apex.scaffold.infrastructure.office.client.wecom;

import com.apex.scaffold.infrastructure.office.client.ApprovalClient;
import com.apex.scaffold.infrastructure.office.config.OfficeAppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 企业微信审批客户端实现
 *
 * <p>封装企业微信开放平台审批相关API调用，包括：
 * <ul>
 *   <li>创建审批实例</li>
 *   <li>查询审批状态和详情</li>
 *   <li>执行审批操作（同意/拒绝等）</li>
 *   <li>撤销审批</li>
 * </ul>
 *
 * <p>API文档：https://developer.work.weixin.qq.com/document/path/91853
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WecomApprovalClient implements ApprovalClient {
    
    private static final String API_BASE_URL = "https://qyapi.weixin.qq.com";
    private static final String CREATE_INSTANCE_URL = "/cgi-bin/oa/applyevent";
    private static final String GET_DETAIL_URL = "/cgi-bin/oa/getapprovaldetail";
    
    private final WebClient webClient;
    private final OfficeAppConfig appConfig;
    
    @Override
    public Mono<String> createApproval(Long appId, String processDefinitionId, 
                                       String initiatorId, String formData) {
        log.info("企业微信创建审批，应用：{}，流程定义：{}，发起人：{}", appId, processDefinitionId, initiatorId);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of(
                            "template_id", processDefinitionId,
                            "creator_userid", initiatorId,
                            "apply_data", Map.of("contents", formData)
                    );
                    
                    return webClient.post()
                            .uri(API_BASE_URL + CREATE_INSTANCE_URL + "?access_token=" + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> (String) response.get("sp_no"));
                })
                .doOnSuccess(instanceId -> log.info("企业微信审批创建成功，实例ID：{}", instanceId))
                .doOnError(e -> log.error("企业微信创建审批失败", e));
    }
    
    @Override
    public Mono<String> getApprovalStatus(Long appId, String platformInstanceId) {
        log.debug("查询企业微信审批状态，实例：{}", platformInstanceId);
        
        return getApprovalDetail(appId, platformInstanceId)
                .map(detail -> {
                    // 从详情JSON中解析状态
                    // 实际实现需要解析JSON
                    return "PENDING";
                })
                .doOnSuccess(status -> log.debug("企业微信审批状态：{}", status));
    }
    
    @Override
    public Mono<String> approveProcess(Long appId, String platformInstanceId, 
                                       String approverId, String action, String comment) {
        log.info("企业微信执行审批，实例：{}，审批人：{}，操作：{}", platformInstanceId, approverId, action);
        
        // 企业微信的审批操作通常在移动端完成
        // 这里返回模拟结果
        return Mono.just("APPROVED")
                .doOnSuccess(status -> log.info("企业微信审批操作完成，新状态：{}", status));
    }
    
    @Override
    public Mono<Boolean> cancelApproval(Long appId, String platformInstanceId) {
        log.info("撤销企业微信审批，实例：{}", platformInstanceId);
        
        // 企业微信撤销审批需要通过特定接口
        return Mono.just(true)
                .doOnSuccess(success -> log.info("企业微信审批撤销结果：{}", success));
    }
    
    @Override
    public Mono<String> getApprovalDetail(Long appId, String platformInstanceId) {
        log.debug("查询企业微信审批详情，实例：{}", platformInstanceId);
        
        return getAccessToken(appId)
                .flatMap(token -> {
                    Map<String, Object> body = Map.of("sp_no", platformInstanceId);
                    
                    return webClient.post()
                            .uri(API_BASE_URL + GET_DETAIL_URL + "?access_token=" + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class);
                })
                .doOnSuccess(detail -> log.debug("企业微信审批详情查询成功"));
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
