package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.OrganizationDirectoryApplicationService;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.interfaces.dto.internal.OrgUserResolveRequest;
import com.apex.scaffold.interfaces.dto.internal.OrgUserResolveResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 组织目录内部接口的请求/响应适配器。
 *
 * <p>负责把外部通讯录身份翻译为仓储匹配条件，再把命中的用户实体回填为响应 DTO。
 * 匹配策略为「拒绝优先」：条目没有可用匹配字段时直接跳过，不猜测、不按其他字段
 * 模糊匹配；命中优先级为已绑定主键、手机号、邮箱、登录名；多个外部标识指向同一
 * 系统用户时只回传一条，避免调用方重复绑定。</p>
 *
 * @author Apex Team
 * @since 2.6.0
 */
@Slf4j
@Component
public class OrgUserResolveAdapter {

    /** 组织目录应用服务。 */
    private final OrganizationDirectoryApplicationService directoryService;

    /**
     * 创建适配器。
     *
     * @param directoryService 组织目录应用服务
     */
    public OrgUserResolveAdapter(OrganizationDirectoryApplicationService directoryService) {
        this.directoryService = directoryService;
    }

    /**
     * 批量解析真实系统用户。
     *
     * @param request 待解析用户列表
     * @return 命中用户的响应；无任何可匹配字段时返回空结果
     */
    public Mono<OrgUserResolveResponse> resolve(OrgUserResolveRequest request) {
        List<OrgUserResolveRequest.OrgUserRef> refs = request.users().stream()
                .filter(ref -> ref != null && hasMatchableField(ref))
                .limit(OrganizationDirectoryApplicationService.maxBatchSize())
                .toList();
        if (refs.isEmpty()) {
            return Mono.just(new OrgUserResolveResponse(List.of()));
        }
        List<String> phones = refs.stream()
                .map(OrgUserResolveRequest.OrgUserRef::mobile)
                .map(OrgUserResolveAdapter::normalize)
                .toList();
        List<String> emails = refs.stream()
                .map(OrgUserResolveRequest.OrgUserRef::email)
                .map(OrgUserResolveAdapter::normalize)
                .toList();
        List<String> usernames = refs.stream()
                .map(OrgUserResolveRequest.OrgUserRef::username)
                .map(OrgUserResolveAdapter::normalize)
                .toList();
        List<Long> boundIds = refs.stream()
                .map(OrgUserResolveRequest.OrgUserRef::userId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        return directoryService
                .findCandidates(boundIds, phones, emails, usernames)
                .map(candidates ->
                        new OrgUserResolveResponse(match(refs, candidates.byUserId(), candidates.byContact())));
    }

    /**
     * 按已绑定主键、手机号、邮箱、登录名的优先级把用户匹配回外部引用。
     *
     * @param refs     外部用户引用
     * @param byId     已绑定主键命中的用户（已按当前租户过滤）
     * @param users    手机号/邮箱/登录名命中的用户（已按当前租户过滤）
     * @return 命中列表，与请求顺序一致
     */
    private List<OrgUserResolveResponse.ResolvedUser> match(
            List<OrgUserResolveRequest.OrgUserRef> refs, List<UserEntity> byId, List<UserEntity> users) {
        Map<Long, UserEntity> byUserId = new HashMap<>(byId.size());
        for (UserEntity user : byId) {
            if (user.getId() != null) {
                byUserId.putIfAbsent(user.getId(), user);
            }
        }
        Map<String, UserEntity> byPhone = new HashMap<>(users.size());
        Map<String, UserEntity> byEmail = new HashMap<>(users.size());
        Map<String, UserEntity> byUsername = new HashMap<>(users.size());
        for (UserEntity user : users) {
            putIfAbsent(byPhone, user.getPhone(), user);
            putIfAbsent(byEmail, user.getEmail(), user);
            putIfAbsent(byUsername, user.getUsername(), user);
        }
        Map<Long, OrgUserResolveResponse.ResolvedUser> resolved = new LinkedHashMap<>(refs.size());
        for (OrgUserResolveRequest.OrgUserRef ref : refs) {
            UserEntity user = ref.userId() == null ? null : byUserId.get(ref.userId());
            String matchedBy = "USER_ID";
            if (user == null) {
                user = byPhone.get(normalize(ref.mobile()));
                matchedBy = "MOBILE";
            }
            if (user == null) {
                user = byEmail.get(normalize(ref.email()));
                matchedBy = "EMAIL";
            }
            if (user == null) {
                user = byUsername.get(normalize(ref.username()));
                matchedBy = "USERNAME";
            }
            if (user == null || user.getId() == null) {
                continue;
            }
            UserEntity matched = user;
            String matchedField = matchedBy;
            resolved.computeIfAbsent(
                    matched.getId(),
                    userId -> new OrgUserResolveResponse.ResolvedUser(
                            ref.externalUserId(),
                            userId,
                            matched.getOpenId(),
                            matched.getUsername(),
                            matched.getPhone(),
                            matched.getEmail(),
                            matched.getDeptId(),
                            matchedField));
        }
        return new ArrayList<>(resolved.values());
    }

    private static void putIfAbsent(Map<String, UserEntity> target, String key, UserEntity user) {
        String normalized = normalize(key);
        if (!normalized.isEmpty()) {
            target.putIfAbsent(normalized, user);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean hasMatchableField(OrgUserResolveRequest.OrgUserRef ref) {
        return ref.userId() != null
                || !normalize(ref.mobile()).isEmpty()
                || !normalize(ref.email()).isEmpty()
                || !normalize(ref.username()).isEmpty();
    }
}
