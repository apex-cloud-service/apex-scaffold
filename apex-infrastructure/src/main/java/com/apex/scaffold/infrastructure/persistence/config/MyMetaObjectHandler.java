package com.apex.scaffold.infrastructure.persistence.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 字段自动填充处理器。
 *
 * <p>实现 {@link MetaObjectHandler}，在插入与更新时自动填充
 * {@code createTime}、{@code updateTime}、{@code deleted} 等审计字段，
 * 避免业务代码手动设置这些通用字段。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /** 构造 MyBatis-Plus 字段自动填充处理器 */
    public MyMetaObjectHandler() {}

    /**
     * 插入时自动填充
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "version", Integer.class, 0);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    /**
     * 更新时自动填充
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
