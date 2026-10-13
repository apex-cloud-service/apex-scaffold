package com.apex.scaffold.office.infrastructure.client.wecom;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.infrastructure.client.AbstractOfficeApiSupport;
import com.apex.scaffold.office.infrastructure.client.ApprovalClient;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 企业微信审批 API 客户端。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Component("wecomApprovalClient")
public class WecomApprovalClient extends AbstractOfficeApiSupport implements ApprovalClient {
    /** 构造企业微信审批客户端。 */
    public WecomApprovalClient(
            WebClient.Builder builder, OfficePlatformProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, OfficePlatform.WECOM, "https://qyapi.weixin.qq.com");
    }

    @Override
    public Mono<String> createApproval(
            String templateId, String initiatorId, String[] approverIds, Map<String, Object> formData, String title) {
        Map<String, Object> body = new HashMap<>(3);
        body.put("creator_userid", required(initiatorId, "企业微信 initiatorId"));
        body.put("template_id", required(templateId, "企业微信 templateId"));
        body.put("apply_data", formData == null ? Map.of() : formData);
        return token().flatMap(accessToken -> call(
                response -> text(response.get("sp_no"), "企业微信 sp_no"),
                postJson("/cgi-bin/oa/applyevent?access_token=" + accessToken, body)));
    }

    @Override
    public Mono<String> getApprovalStatus(String platformInstanceId) {
        return getApprovalDetail(platformInstanceId).map(detail -> normalizeStatus(detail.get("sp_status")));
    }

    @Override
    public Mono<Boolean> approveProcess(String platformInstanceId, String approverId, String action, String comment) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信审批操作需要审批节点上下文，当前端口不支持"));
    }

    @Override
    public Mono<Boolean> cancelApproval(String platformInstanceId, String initiatorId) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信 OA 审批不提供通用撤销接口"));
    }

    @Override
    public Mono<Map<String, Object>> getApprovalDetail(String platformInstanceId) {
        return token().flatMap(accessToken -> call(
                body -> body,
                postJson(
                        "/cgi-bin/oa/getapprovaldetail?access_token=" + accessToken,
                        Map.of("sp_no", required(platformInstanceId, "企业微信 sp_no")))));
    }

    @Override
    public String getPlatformType() {
        return "wecom";
    }

    private Mono<String> token() {
        return call(
                body -> text(body.get("access_token"), "企业微信 access_token"),
                getAccessToken(
                        "/cgi-bin/gettoken",
                        Map.of(
                                "corpid", required(properties.getAppId(), "企业微信 corpId"),
                                "corpsecret", secret("企业微信"))));
    }

    private String normalizeStatus(Object value) {
        String status = value == null ? "UNKNOWN" : value.toString();
        return switch (status) {
            case "1" -> "PENDING";
            case "2" -> "APPROVED";
            case "3" -> "REJECTED";
            case "4" -> "CANCELLED";
            default -> "IN_PROGRESS";
        };
    }
}
