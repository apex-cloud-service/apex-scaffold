package com.apex.scaffold.office.infrastructure.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * office 模块的 MyBatis-Plus 字段自动填充处理器。
 *
 * <p>本模块未依赖 {@code apex-infrastructure-persistence}（共享填充器不在类路径，
 * 也不在服务扫描包内），PO 上声明的 {@code @TableField(fill = ...)} 若无人填充，
 * 字段会以 null 进入 SQL 并触发非空约束失败（BE-039 端到端验证已复现），
 * 故在模块内提供本处理器。
 *
 * <p>语义与共享 {@code MyMetaObjectHandler} 一致：仅填充 null 值，业务代码已
 * 显式赋值的时间不会被覆盖；{@code version}/{@code deleted} 在本模块 PO 中无
 * fill 注解，不纳入填充。
 *
 * <p>本模块 PO 存在两套时间属性命名：{@code createTime}/{@code updateTime}
 * （日历、文档等）与 {@code createdAt}/{@code updatedAt}（审批流程、审批撤销）。
 * 填充器对两套命名都做尝试，且以 {@code hasSetter} 守卫，属性不存在时自动跳过。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Component
public class AuditFieldFillHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充创建与更新时间。
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        fillIfNull(metaObject, "createTime");
        fillIfNull(metaObject, "updateTime");
        fillIfNull(metaObject, "createdAt");
        fillIfNull(metaObject, "updatedAt");
    }

    /**
     * 更新时自动填充更新时间。
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfNull(metaObject, "updateTime");
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
     * @param property 属性名（createTime/updateTime 或 createdAt/updatedAt）
     */
    private void fillIfNull(MetaObject metaObject, String property) {
        if (metaObject.hasSetter(property) && metaObject.getValue(property) == null) {
            metaObject.setValue(property, LocalDateTime.now());
        }
    }
}
