package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.LoginLogEntity;
import com.apex.scaffold.infrastructure.persistence.po.LoginLogPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link LoginLogEntity} 与 {@link LoginLogPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LoginLogPoConverter {

    /**
     * 将登录日志实体转换为持久化对象。
     *
     * @param entity 登录日志实体
     * @return 登录日志持久化对象
     */
    LoginLogPO toPo(LoginLogEntity entity);

    /**
     * 将登录日志持久化对象转换为实体。
     *
     * @param po 登录日志持久化对象
     * @return 登录日志实体
     */
    LoginLogEntity toEntity(LoginLogPO po);

    /**
     * 将登录日志实体列表转换为持久化对象列表。
     *
     * @param entities 登录日志实体列表
     * @return 登录日志持久化对象列表
     */
    List<LoginLogPO> toPoList(List<LoginLogEntity> entities);

    /**
     * 将登录日志持久化对象列表转换为实体列表。
     *
     * @param pos 登录日志持久化对象列表
     * @return 登录日志实体列表
     */
    List<LoginLogEntity> toEntityList(List<LoginLogPO> pos);
}
