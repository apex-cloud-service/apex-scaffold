package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.*;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import com.apex.scaffold.infrastructure.office.spi.OfficePlatformOrganizationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 飞书组织服务客户端
 *
 * <p>实现飞书平台的组织通讯录同步功能，包括部门和成员的拉取。
 *
 * <p>API 文档：
 * <ul>
 *   <li>部门列表：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/department/list</li>
 *   <li>成员列表：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/user/list</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
public class FeishuOrganizationClient implements OfficePlatformOrganizationClient {

    private final WebClient webClient;
    private final OfficePlatformProperties.Platform properties;

    public FeishuOrganizationClient(
        WebClient webClient,
        OfficePlatformProperties platformProperties
    ) {
        this.webClient = webClient;
        this.properties = platformProperties.getPlatforms()
            .getOrDefault(OfficePlatform.FEISHU, new OfficePlatformProperties.Platform());
    }

    @Override
    public OfficePlatform platform() {
        return OfficePlatform.FEISHU;
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment>> fetchDepartments(
        String appId,
        String secret,
        String cursor
    ) {
        return getTenantAccessToken(appId, secret)
            .flatMap(token -> webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/open-apis/contact/v3/departments")
                        .queryParam("page_size", 50);
                    if (cursor != null && !cursor.isBlank()) {
                        builder.queryParam("page_token", cursor);
                    }
                    return builder.build();
                })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseDepartmentsResponse)
            )
            .onErrorMap(e -> new BusinessException(
                ResultCode.BUSINESS_ERROR,
                "飞书拉取部门失败：" + e.getMessage()
            ));
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchMembers(
        String appId,
        String secret,
        String cursor
    ) {
        return getTenantAccessToken(appId, secret)
            .flatMap(token -> webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/open-apis/contact/v3/users")
                        .queryParam("page_size", 50);
                    if (cursor != null && !cursor.isBlank()) {
                        builder.queryParam("page_token", cursor);
                    }
                    return builder.build();
                })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseMembersResponse)
            )
            .onErrorMap(e -> new BusinessException(
                ResultCode.BUSINESS_ERROR,
                "飞书拉取成员失败：" + e.getMessage()
            ));
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchDepartmentMembers(
        String appId,
        String secret,
        String externalDeptId,
        String cursor
    ) {
        return getTenantAccessToken(appId, secret)
            .flatMap(token -> webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/open-apis/contact/v3/users/find_by_department")
                        .queryParam("department_id", externalDeptId)
                        .queryParam("page_size", 50);
                    if (cursor != null && !cursor.isBlank()) {
                        builder.queryParam("page_token", cursor);
                    }
                    return builder.build();
                })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseMembersResponse)
            )
            .onErrorMap(e -> new BusinessException(
                ResultCode.BUSINESS_ERROR,
                "飞书拉取部门成员失败：" + e.getMessage()
            ));
    }

    /**
     * 获取租户访问令牌
     *
     * @param appId  应用 ID
     * @param secret 应用密钥
     * @return 访问令牌
     */
    private Mono<String> getTenantAccessToken(String appId, String secret) {
        return webClient.post()
            .uri("/open-apis/auth/v3/tenant_access_token/internal")
            .bodyValue(Map.of(
                "app_id", appId,
                "app_secret", secret
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                Integer code = (Integer) response.get("code");
                if (code == null || code != 0) {
                    throw new BusinessException(
                        ResultCode.BUSINESS_ERROR,
                        "飞书获取令牌失败：" + response.get("msg")
                    );
                }
                return (String) response.get("tenant_access_token");
            });
    }

    /**
     * 解析部门列表响应
     */
    @SuppressWarnings("unchecked")
    private OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment> parseDepartmentsResponse(
        Map<String, Object> response
    ) {
        Integer code = (Integer) response.get("code");
        if (code == null || code != 0) {
            throw new BusinessException(
                ResultCode.BUSINESS_ERROR,
                "飞书返回错误：" + response.get("msg")
            );
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            return OfficeOrganizationService.OrganizationSyncResult.empty();
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items == null || items.isEmpty()) {
            return OfficeOrganizationService.OrganizationSyncResult.empty();
        }

        List<OfficeDepartment> departments = items.stream()
            .map(this::mapToDepartment)
            .collect(Collectors.toList());

        String nextCursor = (String) data.get("page_token");
        Boolean hasMore = (Boolean) data.get("has_more");

        return OfficeOrganizationService.OrganizationSyncResult.of(
            departments,
            nextCursor,
            Boolean.TRUE.equals(hasMore)
        );
    }

    /**
     * 解析成员列表响应
     */
    @SuppressWarnings("unchecked")
    private OfficeOrganizationService.OrganizationSyncResult<OfficeMember> parseMembersResponse(
        Map<String, Object> response
    ) {
        Integer code = (Integer) response.get("code");
        if (code == null || code != 0) {
            throw new BusinessException(
                ResultCode.BUSINESS_ERROR,
                "飞书返回错误：" + response.get("msg")
            );
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            return OfficeOrganizationService.OrganizationSyncResult.empty();
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items == null || items.isEmpty()) {
            return OfficeOrganizationService.OrganizationSyncResult.empty();
        }

        List<OfficeMember> members = items.stream()
            .map(this::mapToMember)
            .collect(Collectors.toList());

        String nextCursor = (String) data.get("page_token");
        Boolean hasMore = (Boolean) data.get("has_more");

        return OfficeOrganizationService.OrganizationSyncResult.of(
            members,
            nextCursor,
            Boolean.TRUE.equals(hasMore)
        );
    }

    /**
     * 将飞书部门数据映射为领域对象
     */
    @SuppressWarnings("unchecked")
    private OfficeDepartment mapToDepartment(Map<String, Object> item) {
        return OfficeDepartment.builder()
            .externalDeptId((String) item.get("open_department_id"))
            .name((String) item.get("name"))
            .parentId((String) item.get("parent_department_id"))
            .order((Integer) item.get("order"))
            .memberCount((Integer) item.get("member_count"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 将飞书成员数据映射为领域对象
     */
    @SuppressWarnings("unchecked")
    private OfficeMember mapToMember(Map<String, Object> item) {
        List<String> deptIds = (List<String>) item.get("department_ids");
        Integer status = (Integer) item.get("status");
        
        return OfficeMember.builder()
            .externalUserId((String) item.get("open_id"))
            .name((String) item.get("name"))
            .mobile((String) item.get("mobile"))
            .email((String) item.get("email"))
            .avatar((String) ((Map<String, Object>) item.getOrDefault("avatar", Map.of())).get("avatar_origin"))
            .deptIds(deptIds != null ? deptIds : List.of())
            .status(status != null && status == 1 ? "ACTIVE" : "INACTIVE")
            .position((String) item.get("job_title"))
            .jobNumber((String) item.get("employee_no"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
