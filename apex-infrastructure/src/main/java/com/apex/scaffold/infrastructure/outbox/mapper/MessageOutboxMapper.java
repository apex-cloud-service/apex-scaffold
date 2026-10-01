package com.apex.scaffold.infrastructure.outbox.mapper;

import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 消息发件箱Mapper接口
 *
 * <p>提供对sys_message_outbox表的数据访问操作,包括基础的CRUD和自定义的锁管理操作。
 * 继承MyBatis-Plus的BaseMapper,自动获得常用的单表操作方法。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>基础CRUD: 继承自BaseMapper,自动生成</li>
 *   <li>释放过期锁: 查询并清理超时的消息锁</li>
 *   <li>锁定消息: 使用CAS操作原子性地锁定消息</li>
 * </ul>
 *
 * <h2>分布式锁机制</h2>
 * <p>lockMessage方法使用CAS(Compare-And-Swap)操作实现分布式锁:
 * <ol>
 *   <li>只有当locked_by为NULL或空字符串时才能锁定成功</li>
 *   <li>锁定成功后设置locked_by为当前实例ID</li>
 *   <li>同时更新locked_at为当前时间</li>
 *   <li>状态变更为PROCESSING</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MessageOutboxEntity
 * @see com.apex.scaffold.infrastructure.outbox.service.impl.MessageOutboxServiceImpl
 */
@Mapper
public interface MessageOutboxMapper extends BaseMapper<MessageOutboxEntity> {

    /**
     * 释放过期的消息锁
     *
     * <p>查询所有状态为PROCESSING且锁定时间超过lock_timeout_seconds的消息,
     * 清空其locked_by和locked_at字段,使其可以被其他实例重新锁定和投递。
     *
     * <p>SQL逻辑:
     * <pre>
     * UPDATE sys_message_outbox
     * SET locked_by = NULL, locked_at = NULL
     * WHERE status = 'PROCESSING'
     *   AND locked_at < DATE_SUB(NOW(6), INTERVAL lock_timeout_seconds SECOND)
     * </pre>
     *
     * <p>该方法由定时任务调用,通常每30秒执行一次,确保因实例宕机或网络问题
     * 而未能正常释放的锁能够及时释放,避免消息永久阻塞。
     *
     * @return 释放锁的消息数量
     */
    @Update("UPDATE sys_message_outbox SET locked_by = NULL, locked_at = NULL "
            + "WHERE status = 'PROCESSING' "
            + "AND locked_at < DATE_SUB(NOW(6), INTERVAL lock_timeout_seconds SECOND)")
    int releaseExpiredLocks();

    /**
     * 锁定消息进行投递
     *
     * <p>使用CAS(Compare-And-Swap)原子操作锁定消息,防止多实例并发投递同一条消息。
     * 只有当消息的locked_by字段为NULL或空字符串时,才能锁定成功。
     *
     * <p>SQL逻辑:
     * <pre>
     * UPDATE sys_message_outbox
     * SET locked_by = #{instanceId},
     *     locked_at = NOW(6),
     *     status = 'PROCESSING',
     *     updated_at = NOW(6)
     * WHERE message_id = #{messageId}
     *   AND (locked_by IS NULL OR locked_by = '')
     * </pre>
     *
     * <p>返回值说明:
     * <ul>
     *   <li>返回1: 锁定成功,可以继续投递消息</li>
     *   <li>返回0: 锁定失败,消息已被其他实例锁定</li>
     * </ul>
     *
     * @param messageId 消息唯一标识
     * @param instanceId 当前实例ID(UUID)
     * @return 影响的行数,1表示锁定成功,0表示锁定失败
     */
    @Update("UPDATE sys_message_outbox "
            + "SET locked_by = #{instanceId}, locked_at = NOW(6), status = 'PROCESSING', updated_at = NOW(6) "
            + "WHERE message_id = #{messageId} AND (locked_by IS NULL OR locked_by = '')")
    int lockMessage(@Param("messageId") String messageId, @Param("instanceId") String instanceId);
}
