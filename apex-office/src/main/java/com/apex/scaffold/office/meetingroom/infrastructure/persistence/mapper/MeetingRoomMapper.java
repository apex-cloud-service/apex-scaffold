package com.apex.scaffold.office.meetingroom.infrastructure.persistence.mapper;

import com.apex.scaffold.office.meetingroom.infrastructure.persistence.po.MeetingRoomPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议室资源 MyBatis Mapper 接口
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 获得单表 CRUD，租户与状态条件由仓储实现以
 * {@code LambdaQueryWrapper} 显式拼接。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 * @see MeetingRoomPO
 */
@Mapper
public interface MeetingRoomMapper extends BaseMapper<MeetingRoomPO> {}
