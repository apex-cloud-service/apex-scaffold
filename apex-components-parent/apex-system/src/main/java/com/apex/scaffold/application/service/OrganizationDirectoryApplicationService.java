package com.apex.scaffold.application.service;

import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 组织目录内部接口应用服务。
 *
 * <p>供集群内其他服务（当前为 office 组织同步）按手机号、邮箱或登录名把外部通讯录
 * 身份映射到本租户真实的 {@code sys_user.id}。租户由调用方通过 {@code X-Tenant-Id}
 * 声明，接口层已绑定到 Reactor Context，本服务只按既定条件读取仓储，不写入任何数据。</p>
 *
 * <p>本服务不依赖接口层 DTO：入参为已经过「拒绝优先」过滤的匹配字段集合，
 * 出参为本租户命中的用户实体，字段回填由接口层完成。</p>
 *
 * @author Apex Team
 * @since 2.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDirectoryApplicationService {

    /** 单次解析最多返回的用户条数，与请求 DTO 的上限一致。 */
    private static final int MAX_BATCH_SIZE = 200;

    /** 用户仓储。 */
    private final UserRepository userRepository;

    /**
     * 按已绑定主键与手机号/邮箱/登录名查询候选用户。
     *
     * <p>已绑定主键优先走精确查询，其余条件再用手机号/邮箱/登录名兜底匹配；
     * 过滤结果的正规化版本，空值统一转为空串。</p>
     *
     * @param boundIds  已绑定的系统用户 ID，可为空
     * @param phones    手机号，可为空
     * @param emails    邮箱，可为空
     * @param usernames 登录名，可为空
     * @return 主键命中与条件命中的用户；调用方按匹配优先级自行归并
     */
    public Mono<CandidateUsers> findCandidates(
            List<Long> boundIds, List<String> phones, List<String> emails, List<String> usernames) {
        Mono<List<UserEntity>> exact = boundIds.isEmpty()
                ? Mono.just(List.of())
                : userRepository.findByIds(boundIds).collectList();
        return exact.zipWith(userRepository
                        .findByPhonesOrEmailsOrUsernames(phones, emails, usernames)
                        .collectList())
                .map(tuple -> new CandidateUsers(tuple.getT1(), tuple.getT2()));
    }

    /**
     * 单次请求允许解析的最大条目数。
     *
     * @return 最大条目数
     */
    public static int maxBatchSize() {
        return MAX_BATCH_SIZE;
    }

    /**
     * 候选用户查询结果。
     *
     * @param byUserId  已绑定主键命中的用户（已按当前租户过滤）
     * @param byContact 手机号/邮箱/登录名命中的用户（已按当前租户过滤）
     */
    public record CandidateUsers(List<UserEntity> byUserId, List<UserEntity> byContact) {

        /**
         * 规整候选集合，避免调用方拿到 null 列表。
         */
        public CandidateUsers {
            byUserId = byUserId == null
                    ? List.of()
                    : byUserId.stream().filter(Objects::nonNull).toList();
            byContact = byContact == null
                    ? List.of()
                    : byContact.stream().filter(Objects::nonNull).toList();
        }
    }
}
