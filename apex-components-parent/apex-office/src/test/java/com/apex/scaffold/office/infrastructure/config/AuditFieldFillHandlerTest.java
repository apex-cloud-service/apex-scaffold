package com.apex.scaffold.office.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.apex.scaffold.office.infrastructure.po.CalendarEventPO;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * office 审计字段填充处理器单元测试。
 *
 * <p>回归背景：本模块无 {@code MetaObjectHandler} 时，{@code @TableField(fill=...)}
 * 字段以 null 进入 SQL，创建日历事件触发非空约束失败（BE-039 已复现）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("AuditFieldFillHandler 填充语义")
class AuditFieldFillHandlerTest {

    private final AuditFieldFillHandler handler = new AuditFieldFillHandler();

    @Test
    @DisplayName("insertFill 为 null 的 createTime/updateTime 填充当前时间")
    void insertFillsNullTimes() {
        CalendarEventPO po = new CalendarEventPO();
        MetaObject meta = SystemMetaObject.forObject(po);

        handler.insertFill(meta);

        assertThat(po.getCreateTime()).isNotNull();
        assertThat(po.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("业务代码已显式赋值的时间不被覆盖")
    void insertKeepsExplicitTimes() {
        LocalDateTime explicit = LocalDateTime.of(2026, 11, 17, 10, 0);
        CalendarEventPO po = new CalendarEventPO();
        po.setCreateTime(explicit);
        po.setUpdateTime(explicit);
        MetaObject meta = SystemMetaObject.forObject(po);

        handler.insertFill(meta);

        assertThat(po.getCreateTime()).isEqualTo(explicit);
        assertThat(po.getUpdateTime()).isEqualTo(explicit);
    }

    @Test
    @DisplayName("updateFill 只补 null 的 updateTime，已有值不覆盖")
    void updateFillsNullOnly() {
        CalendarEventPO po = new CalendarEventPO();
        MetaObject meta = SystemMetaObject.forObject(po);
        handler.updateFill(meta);
        LocalDateTime first = po.getUpdateTime();

        LocalDateTime explicit = LocalDateTime.of(2026, 11, 17, 12, 0);
        po.setUpdateTime(explicit);
        handler.updateFill(SystemMetaObject.forObject(po));

        assertThat(po.getUpdateTime()).isEqualTo(explicit);
        assertThat(first).isNotNull();
    }

    /**
     * 回归 MF-1：审批流程 PO 使用 {@code createdAt}/{@code updatedAt} 命名，
     * 与日历的 {@code createTime}/{@code updateTime} 不同，填充器必须同时覆盖。
     */
    @Test
    @DisplayName("insertFill 覆盖 createdAt/updatedAt 命名的 PO（审批流程）")
    void insertFillsAuditNamedProperties() {
        ApprovalProcessPO po = new ApprovalProcessPO();
        MetaObject meta = SystemMetaObject.forObject(po);

        handler.insertFill(meta);

        assertThat(po.getCreatedAt()).isNotNull();
        assertThat(po.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateFill 覆盖 updatedAt 命名的 PO，且不覆盖已有值")
    void updateFillsAuditNamedPropertyNullOnly() {
        ApprovalProcessPO po = new ApprovalProcessPO();
        handler.updateFill(SystemMetaObject.forObject(po));

        assertThat(po.getUpdatedAt()).isNotNull();

        LocalDateTime explicit = LocalDateTime.of(2026, 11, 24, 9, 30);
        po.setUpdatedAt(explicit);
        handler.updateFill(SystemMetaObject.forObject(po));

        assertThat(po.getUpdatedAt()).isEqualTo(explicit);
    }
}
