package com.apex.scaffold.infrastructure.office.client.dingtalk;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.client.AbstractOfficeApiSupport;
import com.apex.scaffold.infrastructure.office.client.ApprovalClient;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 钉钉审批 API 客户端。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Component("dingtalkApprovalClient")
public class DingtalkApprovalClient extends AbstractOfficeApiSupport implements ApprovalClient {
    private final ObjectMapper objectMapper;

    public DingtalkApprovalClient(
            WebClient.Builder builder, OfficePlatformProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, OfficePlatform.DINGTALK, "https://api.dingtalk.com");
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> createApproval(
            String templateId, String initiatorId, String[] approverIds, Map<String, Object> formData, String title) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("processCode", required(templateId, "钉钉 processCode"));
        body.put("originatorUserId", required(initiatorId, "钉钉 initiatorId"));
        body.put("title", required(title, "钉钉审批标题"));
        body.put("formComponentValues", formData == null ? Map.of() : formData);
        return token().flatMap(token -> call(
                response -> text(response.get("processInstanceId"), "processInstanceId"),
                postJson("/v1.0/workflow/processInstances", body, Map.of("x-acs-dingtalk-access-token", token))));
    }

    @Override
    public Mono<String> getApprovalStatus(String platformInstanceId) {
        return getApprovalDetail(platformInstanceId).map(detail -> normalizeStatus(detail.get("status")));
    }

    @Override
    public Mono<Boolean> approveProcess(String platformInstanceId, String approverId, String action, String comment) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉审批操作需要 taskId，当前 ApprovalPort 未提供任务标识"));
    }

    @Override
    public Mono<Boolean> cancelApproval(String platformInstanceId, String initiatorId) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉流程实例撤销需要平台专用撤回上下文，当前端口不支持"));
    }

    @Override
    public Mono<Map<String, Object>> getApprovalDetail(String platformInstanceId) {
        return token().flatMap(token -> call(
                body -> body,
                getJsonWithHeaders(
                        "/v1.0/workflow/processInstances/" + required(platformInstanceId, "钉钉 processInstanceId"),
                        Map.of("x-acs-dingtalk-access-token", token))));
    }

    @Override
    public String getPlatformType() {
        return "dingtalk";
    }

    private Mono<String> token() {
        return call(
                body -> text(body.get("accessToken"), "钉钉 accessToken"),
                postJson(
                        "/v1.0/oauth2/accessToken",
                        Map.of(
                                "appKey", required(properties.getAppId(), "钉钉 appId"),
                                "appSecret", secret("钉钉"))));
    }

    private String normalizeStatus(Object value) {
        String status = value == null ? "UNKNOWN" : value.toString().toUpperCase();
        return switch (status) {
            case "COMPLETED", "APPROVED", "PASS" -> "APPROVED";
            case "TERMINATED", "REJECTED", "REJECT" -> "REJECTED";
            case "CANCELED", "CANCELLED" -> "CANCELLED";
            case "RUNNING", "PROCESSING" -> "IN_PROGRESS";
            default -> "PENDING";
        };
    }
}
