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
 * <h2>职责范围</h2>
 * <ul>
 *   <li>基础CRUD: 继承自BaseMapper,自动生成</li>
 *   <li>释放过期锁: 查询并清理超时的消息锁</li>
 *   <li>锁定消息: 使用CAS操作原子性地锁定消息</li>
 * </ul>
 *
 * <h2>职责范围</h2>
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
     * 清空其 locked_by 和 locked_at 字段，但不修改 PROCESSING 状态；
     * 仅查询 PENDING 的调度器不会因此重新获得这些消息。
     *
     * <p>SQL逻辑(MySQL 与未识别方言的默认分支):
     * <pre>
     * UPDATE sys_message_outbox
     * SET locked_by = NULL, locked_at = NULL
     * WHERE status = 'PROCESSING'
     *   AND locked_at &lt; DATE_SUB(NOW(6), INTERVAL lock_timeout_seconds SECOND)
     * </pre>
     *
     * <p>PostgreSQL 分支(通过全局 {@code VendorDatabaseIdProvider} 识别的
     * {@code _databaseId == "postgresql"} 时生效):
     * <pre>
     * UPDATE sys_message_outbox
     * SET locked_by = NULL, locked_at = NULL
     * WHERE status = 'PROCESSING'
     *   AND locked_at &lt; CURRENT_TIMESTAMP(6) - (lock_timeout_seconds * INTERVAL '1 second')
     * </pre>
     *
     * <p>两条分支均使用数据库时钟按行各自的 lock_timeout_seconds 判断过期，
     * 在调用线程同步执行；调用方须隔离阻塞 I/O，任务调度频率由外部配置决定。
     * 未配置 DatabaseIdProvider 时 {@code _databaseId} 为 {@code null}，落到默认
     * MySQL 分支，与历史行为一致。
     *
     * @return 释放锁的消息数量
     */
    @Update("<script>"
            + "UPDATE sys_message_outbox SET locked_by = NULL, locked_at = NULL "
            + "WHERE status = 'PROCESSING' "
            + "<choose>"
            + "<when test=\"_databaseId == 'postgresql'\">"
            + "AND locked_at &lt; CURRENT_TIMESTAMP(6) - (lock_timeout_seconds * INTERVAL '1 second')"
            + "</when>"
            + "<otherwise>"
            + "AND locked_at &lt; DATE_SUB(NOW(6), INTERVAL lock_timeout_seconds SECOND)"
            + "</otherwise>"
            + "</choose>"
            + "</script>")
    int releaseExpiredLocks();

    /**
     * 锁定消息进行投递
     *
     * <p>使用CAS(Compare-And-Swap)原子操作锁定消息,防止多实例并发投递同一条消息。
     * 只有状态为 PENDING、已到下次尝试时间且未被其他实例持有的消息才能锁定成功；
     * 与待投递查询条件保持一致，避免锁定未到重试时间或已终态的消息。
     *
     * <p>SQL逻辑:
     * <pre>
     * UPDATE sys_message_outbox
     * SET locked_by = #{instanceId},
     *     locked_at = CURRENT_TIMESTAMP(6),
     *     status = 'PROCESSING',
     *     updated_at = CURRENT_TIMESTAMP(6)
     * WHERE message_id = #{messageId}
     *   AND status = 'PENDING'
     *   AND next_attempt_at &lt;= CURRENT_TIMESTAMP(6)
     *   AND (locked_by IS NULL OR locked_by = '')
     * </pre>
     *
     * <p>CURRENT_TIMESTAMP(6) 在 MySQL 中为 NOW(6) 的同义词，在 PostgreSQL 中
     * 同为标准时间戳函数，因此该语句在两种方言下均可执行，行为一致。
     * 使用数据库时钟标记锁定时刻，避免应用服务器与数据库时钟偏差影响过期判断。
     *
     * <p>返回值说明:
     * <ul>
     *   <li>返回1: 锁定成功,可以继续投递消息</li>
     *   <li>返回0: 锁定失败,消息已被其他实例锁定、不可投递或未到投递时间</li>
     * </ul>
     *
     * @param messageId 消息唯一标识
     * @param instanceId 当前实例的非空锁持有者标识，格式由调用方约定
     * @return 影响的行数,1表示锁定成功,0表示锁定失败
     */
    @Update("UPDATE sys_message_outbox "
            + "SET locked_by = #{instanceId}, locked_at = CURRENT_TIMESTAMP(6), status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE message_id = #{messageId} AND status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP(6) "
            + "AND (locked_by IS NULL OR locked_by = '')")
    int lockMessage(@Param("messageId") String messageId, @Param("instanceId") String instanceId);
}
