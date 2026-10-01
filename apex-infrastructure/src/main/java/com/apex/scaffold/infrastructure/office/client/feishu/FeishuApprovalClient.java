package com.apex.scaffold.infrastructure.office.client.feishu;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.client.ApprovalClient;
import com.apex.scaffold.infrastructure.office.client.OfficeApiSupport;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component("feishuApprovalClient")
public class FeishuApprovalClient extends OfficeApiSupport implements ApprovalClient {
    private final ObjectMapper objectMapper;

    public FeishuApprovalClient(
            WebClient.Builder builder, OfficePlatformProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, OfficePlatform.FEISHU, "https://open.feishu.cn");
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> createApproval(
            String templateId, String initiatorId, String[] approverIds, Map<String, Object> formData, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("approval_code", required(templateId, "飞书 approvalCode"));
        body.put("user_id", required(initiatorId, "飞书 initiatorId"));
        body.put("form", toJson(formData));
        if (title != null && !title.isBlank()) {
            body.put("uuid", title);
        }
        return token().flatMap(token -> call(
                response -> nestedText(response, "instance_code"),
                postJson("/open-apis/approval/v4/instances", body, auth(token))));
    }

    @Override
    public Mono<String> getApprovalStatus(String platformInstanceId) {
        return getApprovalDetail(platformInstanceId).map(detail -> normalizeStatus(detail.get("status")));
    }

    @Override
    public Mono<Boolean> approveProcess(String platformInstanceId, String approverId, String action, String comment) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "飞书审批操作需要 taskId，当前 ApprovalPort 未提供任务标识"));
    }

    @Override
    public Mono<Boolean> cancelApproval(String platformInstanceId, String initiatorId) {
        return token().flatMap(token -> call(
                body -> Boolean.TRUE,
                postJson(
                        "/open-apis/approval/v4/instances/cancel",
                        Map.of("instance_code", required(platformInstanceId, "飞书 instanceCode")),
                        auth(token))));
    }

    @Override
    public Mono<Map<String, Object>> getApprovalDetail(String platformInstanceId) {
        return token().flatMap(token -> call(
                body -> body,
                getJsonWithHeaders(
                        "/open-apis/approval/v4/instances/" + required(platformInstanceId, "飞书 instanceCode"),
                        auth(token))));
    }

    @Override
    public String getPlatformType() {
        return "feishu";
    }

    private Mono<String> token() {
        return call(
                body -> text(body.get("tenant_access_token"), "飞书 tenant_access_token"),
                postJson(
                        "/open-apis/auth/v3/tenant_access_token/internal",
                        Map.of(
                                "app_id", required(properties.getAppId(), "飞书 appId"),
                                "app_secret", secret("飞书"))));
    }

    private Map<String, String> auth(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    private String toJson(Map<String, Object> formData) {
        try {
            return objectMapper.writeValueAsString(formData == null ? Map.of() : formData);
        } catch (Exception error) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "飞书审批表单不是合法 JSON");
        }
    }

    private String nestedText(Map<String, Object> body, String field) {
        Object data = body.get("data");
        return text(data instanceof Map<?, ?> map ? map.get(field) : null, field);
    }

    private String normalizeStatus(Object value) {
        String status = value == null ? "UNKNOWN" : value.toString().toUpperCase();
        return switch (status) {
            case "PENDING", "PENDING_APPROVAL" -> "PENDING";
            case "APPROVED", "PASS" -> "APPROVED";
            case "REJECTED", "REJECT" -> "REJECTED";
            case "CANCELED", "CANCELLED" -> "CANCELLED";
            default -> "IN_PROGRESS";
        };
    }
}
