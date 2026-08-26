package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_user} 表的持久化操作，提供用户记录查询与行级锁读取能力。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>按主键锁定用户记录（{@code SELECT ... FOR UPDATE}），供并发更新场景使用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    /** Disable an account as the result of a reported-photo review. */
    @Update("UPDATE sys_user SET status=0,update_time=NOW(),version=version+1 "
            + "WHERE id=#{userId} AND deleted=0 AND status=1")
    int banForPhotoReports(@Param("userId") Long userId);

    /**
     * 锁定用户记录。
     *
     * @param userId 用户 ID
     * @return 用户持久化记录
     */
    @Select("SELECT * FROM sys_user WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    UserPO selectByIdForUpdate(@Param("userId") Long userId);

    /**
     * 同步停用账号并清空可重新注册的身份字段。
     *
     * @param userId 用户 ID
     * @return 更新行数
     */
    @Update("UPDATE sys_user SET status=0, deleted=1, open_id=NULL, union_id=NULL, "
            + "access_token=NULL, refresh_token=NULL, token_expire_time=NULL, nick_name=NULL, "
            + "avatar_url=NULL, gender=NULL, birthday=NULL, country=NULL, province=NULL, city=NULL, "
            + "language=NULL, phone=NULL, last_login_time=NULL, last_login_ip=NULL, "
            + "last_heartbeat_time=NULL, "
            + "update_time=NOW(), version=version+1 WHERE id=#{userId} AND deleted=0 AND status IN (0,1)")
    int deactivateForAccountDeletion(@Param("userId") Long userId);
}
