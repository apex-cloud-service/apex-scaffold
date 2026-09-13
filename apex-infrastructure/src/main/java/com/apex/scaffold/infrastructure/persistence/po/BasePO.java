package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 持久化对象（PO）基类。
 *
 * <p>提供所有持久化对象的通用属性，包括主键ID、创建时间、更新时间、
 * 乐观锁版本号和逻辑删除标记。子类通过继承获得这些字段，无需重复定义。
 *
 * <p>主键采用 MyBatis-Plus 雪花算法（{@link IdType#ASSIGN_ID}）自动生成，
 * 创建时间和更新时间由 {@code MyMetaObjectHandler} 自动填充，
 * 逻辑删除通过 {@link TableLogic} 注解实现软删除。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
/**n * BasePO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class BasePO implements Serializable {

    /**
     * 构造基础持久化对象。
     */

    /**
     * 序列化版本号。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     *
     * <p>使用 MyBatis-Plus 雪花算法（{@link IdType#ASSIGN_ID}）自动生成，
     * 全局唯一，非自增。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建时间。
     *
     * <p>记录首次插入时由 {@code MyMetaObjectHandler} 自动填充，
     * 填充策略为 {@link FieldFill#INSERT}。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * <p>每次更新记录时由 {@code MyMetaObjectHandler} 自动填充，
     * 填充策略为 {@link FieldFill#INSERT_UPDATE}。
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号。
     *
     * <p>通过 MyBatis-Plus {@link Version} 注解实现乐观锁控制。
     * 每次更新时自动递增，当多个事务同时更新同一条记录时，
     * 版本号较低的事务会使更新行数为 0，并由
     * {@code OptimisticLockUpdateAspect} 转换为
     * {@link org.springframework.dao.OptimisticLockingFailureException}，
     * 配合 {@link com.apex.scaffold.common.annotation.OptimisticLockRetry}
     * 注解可实现自动重试。
     *
     * <p>填充策略为 {@link FieldFill#INSERT}，首次插入时初始化为 0。
     */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;

    /**
     * 获取领域实体使用的并发版本属性。
     *
     * <p>这是 {@link #version} 的语义化别名，使 MapStruct 能在
     * {@code BaseEntity.lockVersion} 与数据库 {@code version} 列之间自动透传。
     * 该方法不声明新的持久化字段，只返回由 {@link Version} 标记的同一个版本值。
     *
     * @return 当前乐观锁版本号
     * @see #setLockVersion(Integer)
     */
    public Integer getLockVersion() {
        return version;
    }

    /**
     * 设置领域实体传入的并发版本属性。
     *
     * <p>MapStruct 将领域实体的 {@code lockVersion} 写入此方法，最终赋值给
     * {@link #version}。MyBatis-Plus 随后使用该值生成
     * {@code WHERE version = oldVersion} 条件，并在更新成功后递增版本。
     *
     * @param lockVersion 领域实体携带的当前乐观锁版本号；新实体允许为 {@code null}
     * @see #getLockVersion()
     * @see #version
     */
    public void setLockVersion(Integer lockVersion) {
        this.version = lockVersion;
    }

    /**
     * 逻辑删除标记。
     *
     * <p>通过 MyBatis-Plus {@link TableLogic} 注解实现逻辑删除。
     * 值为 0 表示未删除，值为 1 表示已删除。
     * 查询时自动过滤已删除记录，删除操作自动转换为更新标记。
     */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    /**
     * 逻辑删除状态枚举。
     *
     * <p>定义逻辑删除的两种状态：未删除（0）和已删除（1）。
     */
    public enum LogicalDeletion {

        /**
         * 未删除状态，对应数据库值 0。
         */
        NOT_DELETED(0),

        /**
         * 已删除状态，对应数据库值 1。
         */
        DELETED(1);

        /**
         * 状态对应的数据库值。
         */
        private final Integer code;

        /**
         * 构造逻辑删除状态枚举实例。
         *
         * @param code 数据库存储的整数值
         */
        LogicalDeletion(Integer code) {
            this.code = code;
        }

        /**
         * 获取状态对应的数据库值。
         *
         * @return 数据库存储的整数值
         */
        public Integer getCode() {
            return code;
        }
    }
}
