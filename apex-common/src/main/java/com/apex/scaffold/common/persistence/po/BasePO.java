package com.apex.scaffold.common.persistence.po;

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
 * <p>主键采用 MyBatis-Plus 雪花算法自动生成，创建时间和更新时间由元对象处理器自动填充，
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
public class BasePO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 使用 MyBatis-Plus 雪花算法生成的全局唯一主键。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 首次插入时由元对象处理器填充的创建时间。 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新记录时由元对象处理器填充的更新时间。 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 通过 MyBatis-Plus 乐观锁插件维护的版本号。 */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;

    /**
     * 获取领域实体使用的并发版本属性。
     *
     * @return 当前乐观锁版本号
     */
    public Integer getLockVersion() {
        return version;
    }

    /**
     * 设置领域实体传入的并发版本属性。
     *
     * @param lockVersion 领域实体携带的当前乐观锁版本号
     */
    public void setLockVersion(Integer lockVersion) {
        this.version = lockVersion;
    }

    /** 通过 MyBatis-Plus 逻辑删除插件维护的删除标记。 */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    /** 逻辑删除状态。 */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum LogicalDeletion {
        /** 未删除状态，对应数据库值 0。 */
        NOT_DELETED(0),
        /** 已删除状态，对应数据库值 1。 */
        DELETED(1);

        private final Integer code;
    }
}
