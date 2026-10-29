package com.apex.scaffold.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.LocalDateTime;

/**
 * 时间点字段的持久化映射架构守护。
 *
 * <p>检查当前能力测试类路径内的 PO 和领域实体，约束指定业务时间字段使用 LocalDateTime，
 * 与数据库 DATETIME 或 TIMESTAMP 映射保持一致。
 */
@AnalyzeClasses(packages = "com.apex.scaffold", importOptions = ImportOption.DoNotIncludeTests.class)
class TimeFieldArchTest {

    @ArchTest
    static final ArchRule time_fields_in_po_must_use_local_date_time = fields().that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..persistence.po..")
            .and()
            .haveNameMatching(
                    ".*(createTime|updateTime|deletedAt|expireAt|expiredAt|loginTime|logoutTime|accessTime|heartbeatTime|lastLoginTime|lastAccessTime|lastHeartbeatTime|tokenExpireTime|operationTime|callTime).*")
            .and()
            .doNotHaveModifier(JavaModifier.STATIC)
            .and()
            .doNotHaveModifier(JavaModifier.FINAL)
            .should()
            .haveRawType(LocalDateTime.class)
            .because("PO 类的时间点字段必须使用 LocalDateTime 类型,与数据库 DATETIME/TIMESTAMP 映射");

    @ArchTest
    static final ArchRule entity_time_fields_must_use_local_date_time = fields().that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..domain..")
            .and()
            .haveNameMatching(
                    ".*(createTime|updateTime|deletedAt|expireAt|expiredAt|loginTime|logoutTime|accessTime|heartbeatTime|lastLoginTime|lastAccessTime|lastHeartbeatTime|tokenExpireTime|operationTime|callTime).*")
            .and()
            .doNotHaveModifier(JavaModifier.STATIC)
            .and()
            .doNotHaveModifier(JavaModifier.FINAL)
            .should()
            .haveRawType(LocalDateTime.class)
            .because("领域实体的时间点字段必须使用 LocalDateTime 类型");
}
