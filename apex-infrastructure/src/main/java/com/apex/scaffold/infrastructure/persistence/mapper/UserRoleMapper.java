package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_user_role} 表的持久化操作，管理用户与角色的多对多关联关系。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRolePO> {

    /**
     * 恢复已逻辑删除的用户角色关联。
     *
     * <p>仅对 deleted=1 的历史关联生效：恢复后重新启用并刷新版本号与更新时间，
     * 用于账号注销撤销等场景的角色还原。</p>
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 受影响行数（0 表示关联不存在或未被删除）
     */
    @org.apache.ibatis.annotations.Update(
            """
            UPDATE sys_user_role
               SET deleted = 0, update_time = CURRENT_TIMESTAMP, version = version + 1
             WHERE user_id = #{userId} AND role_id = #{roleId} AND deleted = 1
            """)
    int restore(
            @org.apache.ibatis.annotations.Param("userId") Long userId,
            @org.apache.ibatis.annotations.Param("roleId") Long roleId);
}
