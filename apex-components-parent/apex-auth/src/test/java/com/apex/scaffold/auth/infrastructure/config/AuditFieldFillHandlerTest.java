package com.apex.scaffold.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.auth.infrastructure.persistence.po.MfaConfigPO;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * auth 审计字段填充处理器单元测试。
 *
 * <p>回归背景：本模块无 {@code MetaObjectHandler} 时，MFA/TOTP 的
 * {@code @TableField(fill=...)} 字段以 null 进入 SQL，写路径触发非空约束
 * 失败（BE-040 登记的潜在缺陷）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("auth AuditFieldFillHandler 填充语义")
class AuditFieldFillHandlerTest {

    private final AuditFieldFillHandler handler = new AuditFieldFillHandler();

    @Test
    @DisplayName("insertFill 为 null 的 createTime/updateTime 填充当前时间")
    void insertFillsNullTimes() {
        MfaConfigPO po = new MfaConfigPO();
        MetaObject meta = SystemMetaObject.forObject(po);

        handler.insertFill(meta);

        assertThat(po.getCreatedAt()).isNotNull();
        assertThat(po.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("业务代码已显式赋值的时间不被覆盖")
    void insertKeepsExplicitTimes() {
        LocalDateTime explicit = LocalDateTime.of(2026, 11, 17, 10, 0);
        MfaConfigPO po = new MfaConfigPO();
        po.setCreatedAt(explicit);
        po.setUpdatedAt(explicit);
        MetaObject meta = SystemMetaObject.forObject(po);

        handler.insertFill(meta);

        assertThat(po.getCreatedAt()).isEqualTo(explicit);
        assertThat(po.getUpdatedAt()).isEqualTo(explicit);
    }

    @Test
    @DisplayName("updateFill 只补 null 的 updateTime")
    void updateFillsNullOnly() {
        MfaConfigPO po = new MfaConfigPO();
        MetaObject meta = SystemMetaObject.forObject(po);

        handler.updateFill(meta);

        assertThat(po.getUpdatedAt()).isNotNull();
    }
}
