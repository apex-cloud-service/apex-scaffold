package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;

/**
 * 领域模型基类
 *
 * <p>
 * 所有DDD领域实体的抽象基类，提供通用字段和基础能力。
 * 采用{@link Serializable}接口支持分布式传输和持久化。
 * </p>
 *
 * <h2>包含的通用字段</h2>
 * <ul>
 *   <li>{@link #id} - 主键ID，全局唯一标识</li>
 *   <li>{@link #createTime} - 创建时间，记录实体首次创建时间戳</li>
 *   <li>{@link #updateTime} - 更新时间，记录实体最近修改时间戳</li>
 *   <li>{@link #lockVersion} - 持久化并发版本号，用于乐观锁校验</li>
 *   <li>{@link #deleted} - 逻辑删除标记，支持软删除机制</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>
 * 使用{@code @Getter}替代{@code @Getter}，禁止外部直接修改字段。
 * 所有字段修改应通过具体实体的领域行为方法进行，确保业务规则的完整性。
 * </p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see Serializable
 */
@Getter
public abstract class BaseEntity implements Serializable {

    /**
     * 序列化版本号
     *
     * <p>
     * 用于对象序列化版本控制，确保反序列化兼容性。
     * 当类结构发生变化时应更新此版本号。
     * </p>
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     *
     * <p>
     * 实体的全局唯一标识符，由持久层自动生成。
     * 新创建的实体在未保存前此字段为null。
     * </p>
     *
     * <h3>设计说明</h3>
     * <p>
     * 使用Long类型以支持更大的ID范围，支持分库分表场景。
     * 数据库层面应配置为自增主键或使用分布式ID生成器（如Snowflake）。
     * </p>
     */
    protected Long id;

    /**
     * 创建时间
     *
     * <p>
     * 记录实体首次创建的时间戳。
     * 此字段在实体创建时自动设置，之后不应修改。
     * </p>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>数据审计和追溯</li>
     *   <li>时间范围查询</li>
     *   <li>数据统计分析</li>
     * </ul>
     */
    protected LocalDateTime createTime;

    /**
     * 更新时间
     *
     * <p>
     * 记录实体最近一次修改的时间戳。
     * 每次实体状态发生变化时应自动更新。
     * </p>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>并发冲突检测（乐观锁）</li>
     *   <li>缓存失效策略</li>
     *   <li>数据同步</li>
     * </ul>
     */
    protected LocalDateTime updateTime;

    /**
     * 持久化并发版本号。
     *
     * <p>该属性在领域层使用 {@code lockVersion} 命名，避免与协议版本、算法版本等
     * 业务字段混淆。基础设施层会把它映射到数据库的 {@code version} 列，并由
     * MyBatis-Plus 的乐观锁插件在更新时校验和递增。
     *
     * <h3>生命周期</h3>
     * <ul>
     *   <li>新实体尚未持久化时允许为 {@code null}，插入时由持久层初始化为 {@code 0}</li>
     *   <li>查询实体时由持久层恢复数据库中的当前版本</li>
     *   <li>更新实体时作为比较并交换条件，成功后由 MyBatis-Plus 自动递增</li>
     * </ul>
     *
     * <p>领域行为不应直接修改该字段，否则可能跳过并发冲突检测或制造无效冲突。
     *
     * @see #setLockVersion(Integer)
     */
    protected Integer lockVersion;

    /**
     * 逻辑删除标记
     *
     * <p>
     * 支持软删除机制，避免物理删除导致的数据丢失。
     * <ul>
     *   <li>0 - 未删除（正常状态）</li>
     *   <li>1 - 已删除（软删除状态）</li>
     * </ul>
     * </p>
     *
     * <h3>设计说明</h3>
     * <p>
     * 所有查询方法应默认过滤已删除的记录。
     * 恢复已删除数据时，只需将此字段重置为0。
     * </p>
     *
     * @see #isDeleted()
     * @see #markAsDeleted()
     */
    protected Integer deleted;

    /**
     * 保护型构造函数
     *
     * <p>
     * 强制子类通过自身构造函数完成初始化。
     * 子类应在构造函数中初始化必要的字段。
     * </p>
     *
     * @see #initTimestamps()
     */
    protected BaseEntity() {
        this.deleted = LogicalDeletion.NOT_DELETED.getCode();
    }

    /**
     * 初始化时间戳
     *
     * <p>
     * 在实体首次创建时调用，设置创建时间和更新时间。
     * 通常在工厂方法或构造函数末尾调用。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public class UserEntity extends BaseEntity {
     *     public UserEntity() {
     *         super();
     *         initTimestamps();
     *     }
     * }
     * }</pre>
     *
     * @see #createTime
     * @see #updateTime
     */
    protected void initTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
    }

    /**
     * 更新时间戳
     *
     * <p>
     * 在实体状态每次发生变化时调用，更新修改时间。
     * 子类应在业务方法中主动调用此方法。
     * </p>
     *
     * <h3>调用时机</h3>
     * <ul>
     *   <li>字段值变更后</li>
     *   <li>关联关系变更后</li>
     *   <li>状态转换后</li>
     * </ul>
     *
     * @see #updateTime
     */
    protected void touch() {
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 检查是否已删除
     *
     * <p>
     * 判断当前实体是否处于软删除状态。
     * </p>
     *
     * @return true-已删除，false-未删除
     * @see #deleted
     * @see #markAsDeleted()
     */
    public boolean isDeleted() {
        return Objects.equals(this.deleted, LogicalDeletion.DELETED.getCode());
    }

    /**
     * 标记为已删除
     *
     * <p>
     * 执行软删除，将deleted字段设置为1。
     * 物理数据保留，仅改变查询条件。
     * </p>
     *
     * <h3>业务规则</h3>
     * <ul>
     *   <li>软删除应同时处理关联数据</li>
     *   <li>敏感数据可配合数据加密</li>
     *   <li>定期清理历史数据</li>
     * </ul>
     *
     * @see #deleted
     * @see #isDeleted()
     */
    public void markAsDeleted() {
        this.deleted = LogicalDeletion.DELETED.getCode();
        touch();
    }

    /**
     * 恢复已删除的实体
     *
     * <p>
     * 撤销软删除操作，将deleted字段重置为0。
     * 通常用于数据恢复场景。
     * </p>
     *
     * @see #deleted
     * @see #markAsDeleted()
     */
    public void restore() {
        this.deleted = LogicalDeletion.NOT_DELETED.getCode();
        touch();
    }

    /**
     * 检查是否为新实体
     *
     * <p>
     * 判断实体是否从未被持久化。
     * 新实体的ID为空（null）。
     * </p>
     *
     * @return true-新实体，false-已持久化的实体
     * @see #id
     */
    public boolean isNew() {
        return this.id == null;
    }

    /**
     * 设置主键ID
     *
     * <p>
     * 仅供持久层在保存后调用，填充自动生成的主键。
     * 领域代码中应避免直接调用此方法。
     * </p>
     *
     * @param id 主键ID
     * @see #id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 设置创建时间
     *
     * <p>
     * 仅供持久层在保存时调用。
     * </p>
     *
     * @param createTime 创建时间
     * @see #createTime
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 设置更新时间
     *
     * <p>
     * 仅供持久层在保存时调用。
     * </p>
     *
     * @param updateTime 更新时间
     * @see #updateTime
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 设置持久化并发版本号。
     *
     * <p>仅供持久层在 PO 与领域实体转换时调用，用于恢复数据库中的并发版本。
     * 业务代码不应自行递增该值；更新成功后的新版本应由 MyBatis-Plus 生成并通过
     * 持久层重新映射到领域实体。
     *
     * @param lockVersion 当前持久化版本号
     * @see #lockVersion
     */
    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    /**
     * 设置删除标记
     *
     * <p>
     * 仅供持久层在查询时调用。
     * </p>
     *
     * @param deleted 删除标记
     * @see #deleted
     */
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    /**
     * 逻辑删除状态枚举
     *
     * <p>
     * 定义软删除的两种状态，提供类型安全的取值方式。
     * 所有领域实体的deleted字段应使用此枚举来设置值。
     * </p>
     *
     * @author Apex Team
     */
    public enum LogicalDeletion {
        /**
         * 未删除（正常状态）
         *
         * <p>数据库中deleted字段存储值为0</p>
         */
        NOT_DELETED(0),

        /**
         * 已删除
         *
         * <p>数据库中deleted字段存储值为1</p>
         */
        DELETED(1);

        /**
         * 数据库存储值
         */
        private final Integer code;

        /**
         * 构造函数
         *
         * @param code 数据库存储的整数值
         */
        LogicalDeletion(Integer code) {
            this.code = code;
        }

        /**
         * 获取数据库存储值
         *
         * @return 整数值
         */
        public Integer getCode() {
            return code;
        }

        /**
         * 根据整数值获取枚举实例
         *
         * @param code 数据库存储的整数值
         * @return 对应的枚举实例，未知值返回null
         */
        public static LogicalDeletion fromCode(Integer code) {
            if (code == null) {
                return null;
            }
            for (LogicalDeletion status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }
}
