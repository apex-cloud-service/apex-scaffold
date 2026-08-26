package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.ReviewReasonUpdateRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.admin.ReviewReason;
import com.apex.scaffold.domain.repository.ReviewReasonRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 审核拒绝原因策略的后台管理应用服务。
 *
 * <p>提供拒绝原因查询、更新与启用校验；拒绝原因按 PHOTO/INTEREST 两种类型归属，
 * 要求必填备注的原因在未提供备注时拒绝使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class AdminReviewPolicyService {
    private static final java.util.Set<String> TYPES = java.util.Set.of("PHOTO", "INTEREST");
    private final ReviewReasonRepository repository;

    /**
     * 按类型查询拒绝原因列表。
     *
     * @param type 审核类型（PHOTO/INTEREST）
     * @param enabledOnly 为 true 时只返回启用中的原因
     * @return 拒绝原因列表
     */
    public Mono<List<ReviewReason>> list(String type, boolean enabledOnly) {
        return repository.findByType(normalizeType(type), enabledOnly);
    }

    /**
     * 更新一条拒绝原因，使用乐观锁版本号防止并发覆盖。
     *
     * @param id 拒绝原因 ID，必须为正数
     * @param request 更新内容
     * @return 更新后的拒绝原因
     */
    public Mono<ReviewReason> update(Long id, ReviewReasonUpdateRequest request) {
        if (id == null || id <= 0) return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "id不合法"));
        return repository.update(
                id,
                request.label().trim(),
                normalize(request.description()),
                request.enabled(),
                request.requireComment(),
                request.sortOrder(),
                request.expectedVersion());
    }

    /**
     * 校验拒绝原因已启用且合法，供审核裁决链路复用。
     *
     * <p>原因编码不区分大小写；要求备注的原因在备注为空时返回参数错误。</p>
     *
     * @param type 审核类型（PHOTO/INTEREST）
     * @param code 拒绝原因编码，可为空
     * @param comment 审核备注，可为空
     * @return 启用中的拒绝原因
     */
    public Mono<ReviewReason> requireEnabled(String type, String code, String comment) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.isEmpty()) return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "拒绝原因不能为空"));
        return repository
                .findEnabled(normalizeType(type), normalizedCode)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "拒绝原因已停用或不存在")))
                .flatMap(reason -> reason.requireComment() && (comment == null || comment.isBlank())
                        ? Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "该拒绝原因必须填写备注"))
                        : Mono.just(reason));
    }

    /**
     * 规范化审核类型，非法类型直接抛业务错误。
     *
     * @param type 原始类型值，可为空
     * @return 规范化后的大写类型
     */
    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(value)) throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的审核类型");
        return value;
    }

    /**
     * 将可空文本去首尾空白，空串归一为 null。
     *
     * @param value 原始文本，可为空
     * @return 归一化后的文本，或 null
     */
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
