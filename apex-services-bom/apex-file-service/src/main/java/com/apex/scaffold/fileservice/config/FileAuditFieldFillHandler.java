package com.apex.scaffold.fileservice.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * file-service 运行模块的 MyBatis-Plus 审计字段自动填充处理器。
 *
 * <p>填充文件域 PO 上声明了 {@code @TableField(fill = ...)} 的审计字段：插入时填充
 * {@code createTime}/{@code updateTime}/{@code version}，更新时填充 {@code updateTime}。
 * 语义与共享 {@code MyMetaObjectHandler} 一致，仅填充 null 值，业务代码已显式赋值的
 * 字段不会被覆盖。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Component
public class FileAuditFieldFillHandler implements MetaObjectHandler {

    /** {@inheritDoc} */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "version", Integer.class, 0);
    }

    /** {@inheritDoc} */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
