package com.apex.scaffold.auth.infrastructure.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * auth 模块的 MyBatis-Plus 字段自动填充处理器。
 *
 * <p>本模块未依赖 {@code apex-infrastructure-persistence}（共享填充器不在类路径，
 * 也不在服务扫描包内），而 {@code MfaConfigPO}/{@code TotpPO} 声明了
 * {@code @TableField(fill = ...)}：fill 字段即使为 null 也会进入 SQL，导致
 * MFA 配置与 TOTP 绑定的写路径触发非空约束失败，故在模块内提供本处理器。
 *
 * <p>语义与共享 {@code MyMetaObjectHandler} 一致：仅填充 null 值，业务代码已
 * 显式赋值的时间不会被覆盖；{@code version}/{@code deleted} 在本模块 PO 中无
 * fill 注解，不纳入填充。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Component
public class AuditFieldFillHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充 createdAt 与 updatedAt。
     *
     * <p>本模块 PO 的时间属性为 {@code createdAt}/{@code updatedAt}（列
     * {@code created_at}/{@code updated_at}），与共享填充器填充的
     * {@code createTime}/{@code updateTime} 命名不同——这也是共享填充器即使
     * 装配也无法覆盖本模块的原因之一。
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        fillIfNull(metaObject, "createdAt");
        fillIfNull(metaObject, "updatedAt");
    }

    /**
     * 更新时自动填充 updatedAt。
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfNull(metaObject, "updatedAt");
    }

    /**
     * 仅当属性存在且当前值为 null 时填充当前时间。
     *
     * <p>使用 {@code setFieldValByName} 而非 {@code strictInsertFill}：后者依赖
     * MyBatis-Plus 的 TableInfo 注册（随 Mapper 初始化），在无 Mapper 环境下会
     * 因 TableInfo 缺失抛 NPE；显式判断让语义与运行时环境解耦。
     *
     * @param metaObject 元数据对象
     * @param property 属性名（createdAt/updatedAt）
     */
    private void fillIfNull(MetaObject metaObject, String property) {
        if (metaObject.hasSetter(property) && metaObject.getValue(property) == null) {
            metaObject.setValue(property, LocalDateTime.now());
        }
    }
}
