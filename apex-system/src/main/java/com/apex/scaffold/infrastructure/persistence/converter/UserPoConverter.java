package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 用户实体与持久化对象转换器（MapStruct）
 *
 * <p>负责 {@link UserEntity}（领域层实体）与 {@link UserPO}（基础设施层持久化对象）
 * 之间的双向转换，使用 MapStruct 自动生成转换代码，确保类型安全和高性能。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>字段映射：同名字段自动映射（如 {@code id}、{@code openId}、{@code nickname} 等）</li>
 *   <li>忽略策略：{@code unmappedTargetPolicy = IGNORE} 忽略目标对象中未匹配的字段</li>
 *   <li>Spring 集成：{@code componentModel = "spring"} 生成 Spring Bean，支持依赖注入</li>
 *   <li>空值处理：{@code null} 输入返回 {@code null}，不抛出异常</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>Repository 实现：{@code UserRepositoryImpl} 查询数据库后将 {@code UserPO} 转换为 {@code UserEntity}</li>
 *   <li>数据持久化：将内存中的 {@code UserEntity} 转换为 {@code UserPO} 后保存到数据库</li>
 *   <li>批量操作：{@code toPoList} 和 {@code toEntityList} 支持批量转换，减少循环代码</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>编译时 MapStruct 会自动生成 {@code UserPoConverterImpl} 实现类，包含具体的字段复制逻辑。
 * 生成的代码位于 {@code target/generated-sources/annotations/} 目录。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>不支持自定义映射：如需特殊转换逻辑（如日期格式、枚举转换），使用 {@code @Mapping} 注解</li>
 *   <li>性能优化：MapStruct 生成纯 Java 代码，性能接近手写，优于反射方式（如 BeanUtils）</li>
 *   <li>循环引用：避免实体间循环引用，否则可能导致 StackOverflowError</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see UserEntity
 * @see UserPO
 * @see Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserPoConverter {

    /**
     * 内部转换方法：将用户领域实体转换为持久化对象（MapStruct 生成）
     *
     * <p>此方法由 MapStruct 自动生成实现，仅映射 UserPO.builder() 支持的业务字段。
     * BasePO 的字段（id, createTime, updateTime, version, deleted）不在 builder 中，
     * 需要通过 {@link #toPo(UserEntity)} 包装方法手动设置。
     *
     * <p><strong>注意</strong>：不要直接调用此方法，应使用 {@link #toPo(UserEntity)}。
     *
     * @param entity 用户领域实体
     * @return 用户持久化对象（缺少 BasePO 字段）
     */
    @Named("toPoInternal")
    UserPO toPoInternal(UserEntity entity);

    /**
     * 将用户领域实体转换为持久化对象
     *
     * <p>用于将内存中的领域实体保存到数据库前的转换。
     * 转换规则：同名字段自动映射，未匹配字段忽略。
     *
     * <p><strong>实现细节</strong>：
     * <ol>
     *   <li>调用 {@link #toPoInternal(UserEntity)} 由 MapStruct 生成业务字段映射</li>
     *   <li>手动设置 BasePO 字段（id, createTime, updateTime, lockVersion, deleted）</li>
     * </ol>
     *
     * <p><strong>使用示例</strong>：
     * <pre>{@code
     * UserEntity entity = UserEntity.builder()
     *     .id(1L)
     *     .openId("wx_123456")
     *     .nickname("张三")
     *     .build();
     * UserPO po = converter.toPo(entity);
     * userMapper.insert(po);
     * }</pre>
     *
     * @param entity 用户领域实体，{@code null} 返回 {@code null}
     * @return 用户持久化对象，包含所有映射字段
     */
    default UserPO toPo(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        UserPO po = toPoInternal(entity);
        // 手动设置 BasePO 字段（UserPO.builder() 不包含这些字段）
        po.setId(entity.getId());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        po.setLockVersion(entity.getLockVersion());
        po.setDeleted(entity.getDeleted());
        return po;
    }

    /**
     * 将用户持久化对象转换为领域实体
     *
     * <p>用于从数据库查询后将持久化对象转换为领域实体。
     * 转换规则：同名字段自动映射，未匹配字段忽略。
     *
     * <p><strong>使用示例</strong>：
     * <pre>{@code
     * UserPO po = userMapper.selectById(1L);
     * UserEntity entity = converter.toEntity(po);
     * // 调用领域方法
     * entity.changeStatus(UserStatus.ACTIVE);
     * }</pre>
     *
     * @param po 用户持久化对象，{@code null} 返回 {@code null}
     * @return 用户领域实体，包含所有映射字段
     */
    UserEntity toEntity(UserPO po);

    /**
     * 批量将用户领域实体列表转换为持久化对象列表
     *
     * <p>用于批量保存场景，减少循环代码。
     * 内部实现：遍历列表并逐个调用 {@link #toPo(UserEntity)}。
     *
     * <p><strong>使用示例</strong>：
     * <pre>{@code
     * List<UserEntity> entities = Arrays.asList(entity1, entity2, entity3);
     * List<UserPO> pos = converter.toPoList(entities);
     * userMapper.insertBatch(pos);
     * }</pre>
     *
     * @param entities 用户领域实体列表，{@code null} 或空列表返回空列表
     * @return 用户持久化对象列表，保持顺序一致
     */
    List<UserPO> toPoList(List<UserEntity> entities);

    /**
     * 批量将用户持久化对象列表转换为领域实体列表
     *
     * <p>用于批量查询场景，减少循环代码。
     * 内部实现：遍历列表并逐个调用 {@link #toEntity(UserPO)}。
     *
     * <p><strong>使用示例</strong>：
     * <pre>{@code
     * List<UserPO> pos = userMapper.selectList(queryWrapper);
     * List<UserEntity> entities = converter.toEntityList(pos);
     * // 对领域实体列表进行业务处理
     * entities.forEach(entity -> entity.updateLastHeartbeat());
     * }</pre>
     *
     * @param pos 用户持久化对象列表，{@code null} 或空列表返回空列表
     * @return 用户领域实体列表，保持顺序一致
     */
    List<UserEntity> toEntityList(List<UserPO> pos);
}
