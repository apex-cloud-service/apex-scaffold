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
 * 企业微信组织服务客户端
 *
 * <p>实现企业微信平台的组织通讯录同步功能，包括部门和成员的拉取。
 *
 * <p>API 文档：
 * <ul>
 *   <li>部门列表：https://developer.work.weixin.qq.com/document/path/90208</li>
 *   <li>成员列表：https://developer.work.weixin.qq.com/document/path/90201</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
public class WeComOrganizationClient implements OfficePlatformOrganizationClient {

    private final WebClient webClient;
    private final OfficePlatformProperties.Platform properties;

    public WeComOrganizationClient(WebClient webClient, OfficePlatformProperties platformProperties) {
        this.webClient = webClient;
        this.properties = platformProperties
                .getPlatforms()
                .getOrDefault(OfficePlatform.WECOM, new OfficePlatformProperties.Platform());
    }

    @Override
    public OfficePlatform platform() {
        return OfficePlatform.WECOM;
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment>> fetchDepartments(
            String appId, String secret, String cursor) {
        return getAccessToken(appId, secret)
                .<OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment>>flatMap(token -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/cgi-bin/department/list")
                                .queryParam("access_token", token)
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(this::parseDepartmentsResponse))
                .onErrorMap(e ->
                        new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信拉取部门失败：" + ((Throwable) e).getMessage()));
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchMembers(
            String appId, String secret, String cursor) {
        // 企业微信需要先获取部门列表，然后逐个拉取部门成员
        // 这里简化处理，拉取根部门(id=1)的成员
        return fetchDepartmentMembers(appId, secret, "1", cursor);
    }

    @Override
    public Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchDepartmentMembers(
            String appId, String secret, String externalDeptId, String cursor) {
        return getAccessToken(appId, secret)
                .<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>>flatMap(token -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/cgi-bin/user/list")
                                .queryParam("access_token", token)
                                .queryParam("department_id", externalDeptId)
                                .queryParam("fetch_child", 0)
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(this::parseMembersResponse))
                .onErrorMap(e -> new BusinessException(
                        ResultCode.BUSINESS_ERROR, "企业微信拉取部门成员失败：" + ((Throwable) e).getMessage()));
    }

    /**
     * 获取访问令牌
     */
    private Mono<String> getAccessToken(String corpId, String corpSecret) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/gettoken")
                        .queryParam("corpid", corpId)
                        .queryParam("corpsecret", corpSecret)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Integer errcode = (Integer) response.get("errcode");
                    if (errcode == null || errcode != 0) {
                        throw new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信获取令牌失败：" + response.get("errmsg"));
                    }
                    return (String) response.get("access_token");
                });
    }

    @SuppressWarnings("unchecked")
    private OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment> parseDepartmentsResponse(
            Map<String, Object> response) {
        Integer errcode = (Integer) response.get("errcode");
        if (errcode == null || errcode != 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信返回错误：" + response.get("errmsg"));
        }

        List<Map<String, Object>> department = (List<Map<String, Object>>) response.get("department");
        if (department == null || department.isEmpty()) {
            return OfficeOrganizationService.OrganizationSyncResult.<OfficeDepartment>empty();
        }

        List<OfficeDepartment> departments =
                department.stream().map(this::mapToDepartment).collect(Collectors.toList());

        // 企业微信一次返回所有部门，无分页
        return OfficeOrganizationService.OrganizationSyncResult.ofFinal(departments);
    }

    @SuppressWarnings("unchecked")
    private OfficeOrganizationService.OrganizationSyncResult<OfficeMember> parseMembersResponse(
            Map<String, Object> response) {
        Integer errcode = (Integer) response.get("errcode");
        if (errcode == null || errcode != 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信返回错误：" + response.get("errmsg"));
        }

        List<Map<String, Object>> userlist = (List<Map<String, Object>>) response.get("userlist");
        if (userlist == null || userlist.isEmpty()) {
            return OfficeOrganizationService.OrganizationSyncResult.<OfficeMember>empty();
        }

        List<OfficeMember> members = userlist.stream().map(this::mapToMember).collect(Collectors.toList());

        // 企业微信一次返回指定部门的所有成员，无分页
        return OfficeOrganizationService.OrganizationSyncResult.ofFinal(members);
    }

    @SuppressWarnings("unchecked")
    private OfficeDepartment mapToDepartment(Map<String, Object> item) {
        return OfficeDepartment.builder()
                .externalDeptId(String.valueOf(item.get("id")))
                .name((String) item.get("name"))
                .parentId(String.valueOf(item.get("parentid")))
                .order((Integer) item.get("order"))
                // 企业微信部门列表不直接返回成员数
                .memberCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private OfficeMember mapToMember(Map<String, Object> item) {
        List<Integer> department = (List<Integer>) item.get("department");
        Integer status = (Integer) item.get("status");

        return OfficeMember.builder()
                .externalUserId((String) item.get("userid"))
                .name((String) item.get("name"))
                .mobile((String) item.get("mobile"))
                .email((String) item.get("email"))
                .avatar((String) item.get("avatar"))
                .deptIds(
                        department != null
                                ? department.stream().map(String::valueOf).collect(Collectors.toList())
                                : List.of())
                .status(status != null && status == 1 ? "ACTIVE" : "INACTIVE")
                .position((String) item.get("position"))
                .jobNumber((String) item.get("employee_no"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
