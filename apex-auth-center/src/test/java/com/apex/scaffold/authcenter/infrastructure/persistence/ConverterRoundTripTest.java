package com.apex.scaffold.authcenter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

class ConverterRoundTripTest {
    @ParameterizedTest
    @ValueSource(strings = {"User", "Token", "Permission", "Role", "Session", "AuditLog"})
    void preservesAllPersistedFields(String name) throws Exception {
        String prefix = "com.apex.scaffold.authcenter.";
        Class<?> entityType = Class.forName(prefix + "domain.model.entity." + name + "Entity");
        Class<?> converterType = Class.forName(prefix + "infrastructure.persistence.converter." + name + "Converter");
        Class<?> poType = Class.forName(prefix + "infrastructure.persistence.po." + name + "PO");
        Object entity = entityType.getConstructor().newInstance();
        for (Field field : entityType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("permissionIds")) {
                continue;
            }
            field.setAccessible(true);
            if (field.getType() == String.class) {
                field.set(entity, "租户字段-" + field.getName());
            } else if (field.getType() == Long.class) {
                field.set(entity, 123L);
            } else if (field.getType() == Integer.class) {
                field.set(entity, 1);
            } else if (field.getType() == Boolean.class) {
                field.set(entity, true);
            } else if (field.getType() == LocalDateTime.class) {
                field.set(entity, LocalDateTime.of(2026, 9, 20, 10, 15));
            }
        }
        Object converter = Mappers.getMapper(converterType);
        Object po = converterType.getMethod("toPo", entityType).invoke(converter, entity);
        Object restored = converterType.getMethod("toEntity", poType).invoke(converter, po);
        assertThat(restored)
                .usingRecursiveComparison()
                .ignoringFields("permissionIds")
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Role", "Permission", "Session"})
    void usesNumericFlagsForSmallintColumns(String name) throws Exception {
        String prefix = "com.apex.scaffold.authcenter.";
        Class<?> entityType = Class.forName(prefix + "domain.model.entity." + name + "Entity");
        Class<?> converterType = Class.forName(prefix + "infrastructure.persistence.converter." + name + "Converter");
        Class<?> poType = Class.forName(prefix + "infrastructure.persistence.po." + name + "PO");
        Object converter = Mappers.getMapper(converterType);
        for (String flag : name.equals("Session") ? new String[] {"Deleted"} : new String[] {"Deleted", "Enabled"}) {
            for (Boolean value : new Boolean[] {true, false, null}) {
                Object entity = entityType.getConstructor().newInstance();
                entityType.getMethod("set" + flag, Boolean.class).invoke(entity, value);
                Object po = converterType.getMethod("toPo", entityType).invoke(converter, entity);
                assertThat(poType.getMethod("get" + flag).invoke(po)).isEqualTo(value == null ? null : value ? 1 : 0);
                Object restored = converterType.getMethod("toEntity", poType).invoke(converter, po);
                assertThat(entityType.getMethod("get" + flag).invoke(restored)).isEqualTo(value);
            }
        }
    }
}
