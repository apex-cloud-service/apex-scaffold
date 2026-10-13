package com.apex.scaffold.application.assembler;

import java.util.List;

/**
 * 通用 CRUD 对象转换器接口。
 *
 * <p>定义实体（E）、DTO（D）、请求对象（R）之间的标准双向映射契约。
 * 消除 {@link RoleAssembler} 和 {@link PermissionAssembler} 中重复的方法签名。
 *
 * @param <E> 领域实体类型
 * @param <D> 数据传输对象类型
 * @param <R> 请求对象类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface CrudAssembler<E, D, R> {

    /**
     * 将实体转换为 DTO。
     *
     * @param entity 领域实体
     * @return 对应的数据传输对象
     */
    D toDTO(E entity);

    /**
     * 将 DTO 转换为实体。
     *
     * @param dto 数据传输对象
     * @return 对应的领域实体
     */
    E toEntity(D dto);

    /**
     * 将请求对象转换为实体。
     *
     * @param request 客户端请求对象
     * @return 用于业务操作的领域实体
     */
    E fromRequest(R request);

    /**
     * 将实体列表转换为 DTO 列表。
     *
     * @param entities 领域实体列表
     * @return 对应的 DTO 列表
     */
    List<D> toDTOList(List<E> entities);
}
