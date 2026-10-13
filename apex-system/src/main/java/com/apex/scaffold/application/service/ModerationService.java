package com.apex.scaffold.application.service;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.model.entity.BaseEntity.LogicalDeletion;
import com.apex.scaffold.domain.model.entity.BlacklistEntity;
import com.apex.scaffold.domain.model.entity.ReportTicketEntity;
import com.apex.scaffold.domain.repository.BlacklistRepository;
import com.apex.scaffold.domain.repository.ReportTicketRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 内容审核服务应用类
 *
 * <p>提供黑名单管理和举报功能，用于社区内容治理
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>黑名单管理：拉黑/取消拉黑其他用户</li>
 *   <li>举报管理：提交用户、照片、消息等举报工单</li>
 * </ul>
 *
 * <h2>黑名单规则</h2>
 * <ul>
 *   <li>双向拉黑检测：创建会话或发送消息时会进行双向黑名单校验</li>
 *   <li>enable标志：通过enable参数控制是拉黑还是取消拉黑</li>
 * </ul>
 *
 * <h2>举报目标类型</h2>
 * <ul>
 *   <li>USER：用户</li>
 *   <li>PHOTO：照片</li>
 *   <li>MESSAGE：消息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserService
 * @see BlacklistRepository
 * @see ReportTicketRepository
 */
@Service
@RequiredArgsConstructor
public class ModerationService {

    /**
     * 用户应用服务，用于根据 openId 查询当前操作用户信息。
     *
     * <p>所有黑名单和举报操作均需先通过此服务验证用户身份。</p>
     */
    private final UserService userService;

    /**
     * 黑名单仓储，提供黑名单关系的增删查改持久化操作。
     *
     * <p>支持 upsert 操作，拉黑/取消拉黑均通过同一接口完成，
     * 通过 enabled 字段区分当前拉黑状态。</p>
     */
    private final BlacklistRepository blacklistRepository;

    /**
     * 举报工单仓储，提供举报工单的创建和查询持久化操作。
     *
     * <p>工单创建后状态为 PENDING（待处理），由运营后台审核处理。</p>
     */
    private final ReportTicketRepository reportTicketRepository;

    /**
     * 拉黑或取消拉黑指定用户。
     *
     * <p>将目标用户添加到当前用户的黑名单中，或从黑名单中移除。
     * 当enabled为true时表示拉黑，false时表示取消拉黑。
     * 黑名单状态用于后续聊天和消息发送时的双向校验。
     *
     * @param openId 当前用户的openId（操作者）
     * @param targetUserId 目标用户ID（被拉黑/取消拉白的用户）
     * @param enabled true-拉黑目标用户，false-取消拉黑
     * @return 包含拉黑状态是否启用的Map对象，键为"enabled"
     * @throws BusinessException 当当前用户不存在时抛出
     */
    public Mono<Map<String, Object>> blacklist(String openId, Long targetUserId, boolean enabled) {
        return userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(user -> {
                    BlacklistEntity e = new BlacklistEntity();
                    e.setUserId(user.getId());
                    e.setTargetUserId(targetUserId);
                    e.setEnabled(enabled ? SystemConstants.BOOLEAN_TRUE : SystemConstants.BOOLEAN_FALSE);
                    e.setCreateTime(LocalDateTime.now());
                    e.setUpdateTime(LocalDateTime.now());
                    e.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
                    return blacklistRepository.upsert(e);
                })
                .map(saved -> Map.<String, Object>of("enabled", saved.getEnabled() == SystemConstants.BOOLEAN_TRUE));
    }

    /**
     * 提交举报工单。
     *
     * <p>用户举报不当内容，包括用户、照片或消息。
     * 举报信息包括目标类型、目标ID、原因说明和证据截图。
     * 提交后工单状态为PENDING，待运营审核处理。
     *
     * @param openId 举报用户的openId
     * @param body 举报请求体，包含targetType(目标类型)、targetId(目标ID)、reason(原因)、evidenceFileUrls(证据截图列表)
     * @return 包含工单ID和状态的Map，键为"ticketId"和"status"
     * @throws BusinessException 当举报用户不存在时抛出
     */
    public Mono<Map<String, Object>> report(String openId, Map<String, Object> body) {
        return userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(user -> {
                    String targetType = String.valueOf(body.getOrDefault("targetType", "USER"));
                    Object raw = body.get("targetId");
                    if (raw == null) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "targetId不能为空"));
                    }
                    // P0-12修复(SV-4): 捕获NumberFormatException，防止非法数字字符串导致500
                    final Long targetId;
                    try {
                        targetId = Long.parseLong(raw.toString());
                    } catch (NumberFormatException e) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "targetId格式错误，必须是有效数字", e));
                    }
                    String reason = String.valueOf(body.getOrDefault("reason", ""));
                    Object evidence = body.get("evidenceFileUrls");
                    ReportTicketEntity e = new ReportTicketEntity();
                    e.setReporterUserId(user.getId());
                    e.setTargetType(targetType);
                    e.setTargetId(targetId);
                    e.setReason(reason);
                    e.setEvidenceJson(JsonUtil.toJson(evidence == null ? List.of() : evidence));
                    e.setStatus("PENDING");
                    e.setCreateTime(LocalDateTime.now());
                    e.setUpdateTime(LocalDateTime.now());
                    e.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
                    return reportTicketRepository.save(e);
                })
                .map(saved ->
                        Map.<String, Object>of("ticketId", String.valueOf(saved.getId()), "status", saved.getStatus()));
    }
}
