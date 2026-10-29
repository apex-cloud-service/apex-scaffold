package com.apex.scaffold.file.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * 由接口层在认证成功后注入的文件访问身份；不从客户端请求头或线程本地租户推断用户。
 *
 * @param tenantId 已验证的租户标识
 * @param userId 已验证的上传用户标识
 * @author Apex Team
 * @since 1.3.1
 */
public record FileAccessContext(String tenantId, Long userId) {
    public static final String KEY = FileAccessContext.class.getName();

    /** @return 当前订阅中的可信身份，缺失身份时拒绝文件操作 */
    public static Mono<FileAccessContext> current() {
        return Mono.deferContextual(context -> {
            FileAccessContext identity = context.getOrDefault(KEY, null);
            if (identity == null
                    || identity.tenantId() == null
                    || identity.tenantId().isBlank()
                    || identity.userId() == null
                    || identity.userId() <= 0) {
                return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "缺少可信文件访问身份"));
            }
            return Mono.just(identity);
        });
    }

    /**
     * 校验文件记录的租户、用户和提供商归属；历史空归属记录不可自动接管。
     * @param file 待访问的文件记录
     */
    public void authorize(FileEntity file) {
        if (!Objects.equals(tenantId, file.getTenantId())
                || !Objects.equals(userId, file.getUserId())
                || file.getStorageType() == null
                || file.getStorageType().isBlank()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该文件或文件归属不完整");
        }
    }
}
