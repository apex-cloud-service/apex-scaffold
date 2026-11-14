package com.apex.scaffold.office.off.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import com.apex.scaffold.office.off.domain.repository.MessageDeliveryRepository;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessagePoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffEmployeeMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageRecipientMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffEmployeePO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageRecipientPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * 在专用 JDBC 调度器上执行消息及接收人原子写入。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class MessageDeliveryRepositoryImpl implements MessageDeliveryRepository {
    /** 单次发送允许的最大接收人数量，与 SendMessageRequest 的校验上限保持一致。 */
    private static final int MAX_RECIPIENTS = 100;

    private final OffMessageMapper messageMapper;
    private final OffMessageRecipientMapper recipientMapper;
    private final OffMessagePoConverter converter;
    private final PlatformTransactionManager transactionManager;
    private final OffEmployeeMapper employeeMapper;

    /**
     * 单线程本地事务校验接收用户归属并写入，任一接收人失败使全部写入回滚。
     * JDBC 线程显式安装可信租户，结束后恢复原线程上下文。
     * @param message 待发送消息
     * @param recipients 去重后的接收用户，须在本租户绑定有效办公员工
     * @return 已完成事务提交的消息
     */
    @Override
    public Mono<OffMessageEntity> save(OffMessageEntity message, List<Long> recipients) {
        return Mono.deferContextual(context -> Mono.fromCallable(() -> {
                    String tenant = context.get(TenantContext.REACTOR_CONTEXT_KEY);
                    if (!tenant.equals(message.getTenantId())) {
                        throw new IllegalStateException("站内信写入租户不匹配");
                    }
                    String previous = TenantContext.setTenantId(tenant);
                    try {
                        return new TransactionTemplate(transactionManager).execute(status -> {
                            validateRecipients(tenant, recipients);
                            var po = converter.toPo(message);
                            var now = java.time.LocalDateTime.now();
                            po.setCreateTime(now);
                            po.setUpdateTime(now);
                            po.setVersion(0);
                            po.setDeleted(0);
                            if (messageMapper.insert(po) != 1) {
                                throw new IllegalStateException("消息写入失败");
                            }
                            message.setId(po.getId());
                            for (Long userId : recipients) {
                                var recipient = new OffMessageRecipientPO();
                                recipient.setMessageId(po.getId());
                                recipient.setRecipientId(userId);
                                recipient.setRecipientType("USER");
                                recipient.setTenantId(tenant);
                                recipient.setReadStatus(false);
                                recipient.setCreateTime(now);
                                recipient.setUpdateTime(now);
                                recipient.setVersion(0);
                                recipient.setDeleted(0);
                                if (recipientMapper.insert(recipient) != 1) {
                                    throw new IllegalStateException("消息接收人写入失败");
                                }
                            }
                            return message;
                        });
                    } finally {
                        TenantContext.restoreTenantId(previous);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    /**
     * 根据本地办公员工的用户绑定验证收件人归属，拒绝跨租户、未绑定或停用用户。
     * @param tenant 可信租户
     * @param recipients 接收用户标识，不能使用员工主键替代
     */
    private void validateRecipients(String tenant, List<Long> recipients) {
        boolean invalidSize = recipients.isEmpty() || recipients.size() > MAX_RECIPIENTS;
        boolean invalidId = recipients.stream().anyMatch(MessageDeliveryRepositoryImpl::isInvalidUserId);
        if (invalidSize || invalidId) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "接收人参数无效");
        }
        var users = employeeMapper
                .selectList(new LambdaQueryWrapper<OffEmployeePO>()
                        .select(OffEmployeePO::getUserId)
                        .eq(OffEmployeePO::getTenantId, tenant)
                        .eq(OffEmployeePO::getStatus, "ACTIVE")
                        .in(OffEmployeePO::getUserId, recipients))
                .stream()
                .map(OffEmployeePO::getUserId)
                .collect(Collectors.toSet());
        if (!users.containsAll(recipients)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "接收人不存在或不属于当前租户");
        }
    }

    /**
     * 判断单个接收用户标识是否非法，空值与非正值均视为非法输入。
     * @param userId 待检查的接收用户标识
     * @return 非法返回 true
     */
    private static boolean isInvalidUserId(Long userId) {
        return userId == null || userId <= 0;
    }
}
