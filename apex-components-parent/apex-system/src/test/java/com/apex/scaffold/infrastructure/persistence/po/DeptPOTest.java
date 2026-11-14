package com.apex.scaffold.infrastructure.persistence.po;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class DeptPOTest {

    @Test
    void usesAssignedIdForNewDepartments() throws NoSuchFieldException {
        Field id = DeptPO.class.getDeclaredField("id");
        TableId tableId = id.getAnnotation(TableId.class);

        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.ASSIGN_ID);
    }

    @Test
    void marksVersionForOptimisticLocking() throws NoSuchFieldException {
        Field version = DeptPO.class.getDeclaredField("version");

        assertThat(version.getAnnotation(Version.class)).isNotNull();
    }

    @Test
    void fillsAuditFieldsAndUsesDomainBaseEntity() throws NoSuchFieldException {
        Field createTime = DeptPO.class.getDeclaredField("createTime");
        Field updateTime = DeptPO.class.getDeclaredField("updateTime");

        assertThat(createTime.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT);
        assertThat(updateTime.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT_UPDATE);
        assertThat(DeptEntity.class.getSuperclass()).isEqualTo(BaseEntity.class);
    }

    @Test
    void inheritsCommonFieldsWithoutShadowingThem() {
        assertThat(DeptEntity.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("id", "createTime", "updateTime", "lockVersion", "deleted");
    }
}
