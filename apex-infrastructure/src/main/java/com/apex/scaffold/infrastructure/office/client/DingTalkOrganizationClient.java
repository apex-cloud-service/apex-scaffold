package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.*;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import com.apex.scaffold.infrastructure.office.spi.OfficePlatformOrganizationClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 钉钉组织服务客户端
 *
 * <p>实现钉钉平台的组织通讯录同步功能，包括部门和成员的拉取。
 *
 * <p>API 文档：
 * <ul>
 *   <li>部门列表：https://open.dingtalk.com/document/orgapp/obtain-the-department-list-v2</li>
 *   <li>成员列表：https://open.dingtalk.com/document/orgapp/queries-the-complete-information-of-a-department-user</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
public class DingTalkOrganizationClient implements OfficePlatformOrganizationClient {

    private final WebClient webClient;
    private final OfficePlatformProperties.Platform properties;

    /** 构造钉钉组织客户端并加载钉钉平台配置。 */
    public DingTalkOrganizationClient(WebClient webClient, OfficePlatformProperties platformProperties) {
        this.webClient = webClient;
        this.properties = platformProperties
                .getPlatforms()
                .getOrDefault(OfficePlatform.DINGTALK, new OfficePlatformProperties.Platform());
    }

    @Override
    public OfficePlatform platform() {
        return OfficePlatform.DINGTALK;
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment>> fetchDepartments(
            String appId, String secret, String cursor) {
        return getAccessToken(appId, secret)
                .<OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment>>flatMap(token -> webClient
                        .post()
                        .uri("/v1.0/contact/departments/tree")
                        .header("x-acs-dingtalk-access-token", token)
                        .bodyValue(Map.of("cursor", cursor != null ? cursor : "0", "size", 50))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(this::parseDepartmentsResponse))
                .onErrorMap(e ->
                        new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉拉取部门失败：" + ((Throwable) e).getMessage()));
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchMembers(
            String appId, String secret, String cursor) {
        return getAccessToken(appId, secret)
                .<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>>flatMap(token -> webClient
                        .post()
                        .uri("/v1.0/contact/users")
                        .header("x-acs-dingtalk-access-token", token)
                        .bodyValue(Map.of("cursor", cursor != null ? cursor : "0", "size", 50))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(this::parseMembersResponse))
                .onErrorMap(e ->
                        new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉拉取成员失败：" + ((Throwable) e).getMessage()));
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchDepartmentMembers(
            String appId, String secret, String externalDeptId, String cursor) {
        return getAccessToken(appId, secret)
                .<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>>flatMap(token -> webClient
                        .post()
                        .uri("/v1.0/contact/departments/" + externalDeptId + "/users")
                        .header("x-acs-dingtalk-access-token", token)
                        .bodyValue(Map.of("cursor", cursor != null ? cursor : "0", "size", 50))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(this::parseMembersResponse))
                .onErrorMap(e ->
                        new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉拉取部门成员失败：" + ((Throwable) e).getMessage()));
    }

    /**
     * 获取访问令牌
     */
    private Mono<String> getAccessToken(String appKey, String appSecret) {
        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder.path("/v1.0/oauth2/accessToken").build())
                .bodyValue(Map.of(
                        "appKey", appKey,
                        "appSecret", appSecret))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("accessToken"));
    }

    @SuppressWarnings("unchecked")
    private OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment> parseDepartmentsResponse(
            Map<String, Object> response) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) response.get("list");
        if (list == null || list.isEmpty()) {
            return OfficeOrganizationService.OrganizationSyncResult.<OfficeDepartment>empty();
        }

        List<OfficeDepartment> departments =
                list.stream().map(this::mapToDepartment).collect(Collectors.toList());

        String nextCursor = (String) response.get("nextCursor");
        Boolean hasMore = (Boolean) response.get("hasMore");

        return OfficeOrganizationService.OrganizationSyncResult.of(
                departments, nextCursor, Boolean.TRUE.equals(hasMore));
    }

    @SuppressWarnings("unchecked")
    private OfficeOrganizationService.OrganizationSyncResult<OfficeMember> parseMembersResponse(
            Map<String, Object> response) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) response.get("list");
        if (list == null || list.isEmpty()) {
            return OfficeOrganizationService.OrganizationSyncResult.<OfficeMember>empty();
        }

        List<OfficeMember> members = list.stream().map(this::mapToMember).collect(Collectors.toList());

        String nextCursor = (String) response.get("nextCursor");
        Boolean hasMore = (Boolean) response.get("hasMore");

        return OfficeOrganizationService.OrganizationSyncResult.of(members, nextCursor, Boolean.TRUE.equals(hasMore));
    }

    @SuppressWarnings("unchecked")
    private OfficeDepartment mapToDepartment(Map<String, Object> item) {
        return OfficeDepartment.builder()
                .externalDeptId(String.valueOf(item.get("deptId")))
                .name((String) item.get("name"))
                .parentId(String.valueOf(item.get("parentId")))
                .order((Integer) item.get("order"))
                .memberCount((Integer) item.get("userCount"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private OfficeMember mapToMember(Map<String, Object> item) {
        List<Long> deptIds = (List<Long>) item.get("deptIdList");
        Boolean active = (Boolean) item.get("active");

        return OfficeMember.builder()
                .externalUserId((String) item.get("unionId"))
                .name((String) item.get("name"))
                .mobile((String) item.get("mobile"))
                .email((String) item.get("email"))
                .avatar((String) item.get("avatar"))
                .deptIds(
                        deptIds != null
                                ? deptIds.stream().map(String::valueOf).collect(Collectors.toList())
                                : List.of())
                .status(Boolean.TRUE.equals(active) ? "ACTIVE" : "INACTIVE")
                .position((String) item.get("title"))
                .jobNumber((String) item.get("jobNumber"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
