package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据 MyBatis Mapper 接口
 *
 * <p>负责 {@code sys_user} 表的持久化操作，提供用户记录的 CRUD 和特殊查询能力。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作（insert、update、delete、selectById 等）。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>通用 CRUD：继承自 {@link BaseMapper}，支持单表增删改查</li>
 *   <li>行级锁查询：{@link #selectByIdForUpdate} 提供 {@code SELECT ... FOR UPDATE} 悲观锁支持</li>
 *   <li>账号封禁：{@link #banForPhotoReports} 因举报照片审核失败而封禁账号</li>
 *   <li>账号注销：{@link #deactivateForAccountDeletion} 清空用户敏感数据并停用账号</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>并发更新：使用 {@code selectByIdForUpdate} 锁定记录后更新，防止并发冲突</li>
 *   <li>内容审核：照片举报审核通过后调用 {@code banForPhotoReports} 封禁违规用户</li>
 *   <li>GDPR 合规：用户申请注销时调用 {@code deactivateForAccountDeletion} 清除个人信息</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>行级锁查询必须在事务中使用，否则锁立即释放</li>
 *   <li>封禁和注销操作会更新 {@code version} 字段（乐观锁）</li>
 *   <li>注销操作不可逆，会清空 OpenID/UnionID 等身份字段</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see UserPO
 * @see BaseMapper
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    /**
     * 因举报照片审核失败而封禁账号
     *
     * <p>将用户状态设置为禁用（{@code status=0}），并更新版本号。
     * 仅封禁当前正常状态的账号（{@code status=1}），已封禁账号忽略。
     *
     * <p><strong>SQL 语义</strong>：
     * <pre>{@code
     * UPDATE sys_user
     * SET status=0, update_time=NOW(), version=version+1
     * WHERE id=#{userId} AND deleted=0 AND status=1
     * }</pre>
     *
     * @param userId 用户ID，必须存在且未删除
     * @return 更新行数，成功返回 1，用户不存在或已封禁返回 0
     */
    @Update("UPDATE sys_user SET status=0,update_time=NOW(),version=version+1 "
            + "WHERE id=#{userId} AND deleted=0 AND status=1")
    int banForPhotoReports(@Param("userId") Long userId);

    /**
     * 锁定用户记录（悲观锁）
     *
     * <p>使用 {@code SELECT ... FOR UPDATE} 行级锁锁定用户记录，
     * 防止并发修改。必须在事务中使用，事务提交或回滚时释放锁。
     *
     * <p><strong>使用场景</strong>：
     * <ul>
     *   <li>积分扣减：锁定账户余额后执行扣减操作</li>
     *   <li>状态变更：确保状态机转换的原子性</li>
     *   <li>并发写入：多个请求同时修改同一用户时避免冲突</li>
     * </ul>
     *
     * <p><strong>注意事项</strong>：
     * <ul>
     *   <li>必须在 {@code @Transactional} 事务中调用，否则锁立即释放</li>
     *   <li>锁等待超时时间由 MySQL {@code innodb_lock_wait_timeout} 控制（默认 50 秒）</li>
     *   <li>避免长时间持有锁，影响并发性能</li>
     * </ul>
     *
     * @param userId 用户ID，必须存在且未删除
     * @return 用户持久化对象，用户不存在返回 {@code null}
     */
    @Select("SELECT * FROM sys_user WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    UserPO selectByIdForUpdate(@Param("userId") Long userId);

    /**
     * 停用账号并清空敏感数据（账号注销）
     *
     * <p>执行用户账号注销操作，符合 GDPR "被遗忘权" 要求：
     * <ul>
     *   <li>停用账号：{@code status=0, deleted=1}</li>
     *   <li>清空身份信息：{@code open_id, union_id, access_token, refresh_token} 设为 {@code NULL}</li>
     *   <li>清空个人信息：{@code nick_name, avatar_url, gender, birthday, phone} 等设为 {@code NULL}</li>
     *   <li>清空地理位置：{@code country, province, city} 设为 {@code NULL}</li>
     *   <li>清空登录记录：{@code last_login_time, last_login_ip, last_heartbeat_time} 设为 {@code NULL}</li>
     * </ul>
     *
     * <p><strong>注意事项</strong>：
     * <ul>
     *   <li>此操作不可逆，清空的数据无法恢复</li>
     *   <li>清空 {@code open_id} 和 {@code phone} 后，用户可使用相同微信号或手机号重新注册</li>
     *   <li>保留用户ID（{@code id}）和创建时间（{@code create_time}）用于数据分析</li>
     *   <li>会更新版本号（{@code version+1}）用于乐观锁</li>
     * </ul>
     *
     * @param userId 用户ID，必须存在且未删除
     * @return 更新行数，成功返回 1，用户不存在返回 0
     */
    @Update("UPDATE sys_user SET status=0, deleted=1, open_id=NULL, union_id=NULL, "
            + "access_token=NULL, refresh_token=NULL, token_expire_time=NULL, nick_name=NULL, "
            + "avatar_url=NULL, gender=NULL, birthday=NULL, country=NULL, province=NULL, city=NULL, "
            + "language=NULL, phone=NULL, last_login_time=NULL, last_login_ip=NULL, "
            + "last_heartbeat_time=NULL, "
            + "update_time=NOW(), version=version+1 WHERE id=#{userId} AND deleted=0 AND status IN (0,1)")
    int deactivateForAccountDeletion(@Param("userId") Long userId);
}
