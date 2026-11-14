package com.apex.scaffold.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 持久化能力的租户隔离架构守护。
 *
 * <p>在当前测试类路径的持久化包中，禁止类或方法使用忽略拦截器注解，避免绕过租户 SQL 条件。
 */
@AnalyzeClasses(
        packages = "com.apex.scaffold.infrastructure.persistence",
        importOptions = ImportOption.DoNotIncludeTests.class)
class TenantIsolationArchTest {

    /** 持久化类不得通过注解绕过租户拦截器。 */
    @ArchTest
    static final ArchRule persistence_classes_must_not_bypass_tenant_interceptor =
            noClasses().should().beAnnotatedWith(InterceptorIgnore.class);

    /** 持久化方法不得通过注解绕过租户拦截器。 */
    @ArchTest
    static final ArchRule persistence_methods_must_not_bypass_tenant_interceptor =
            noMethods().should().beAnnotatedWith(InterceptorIgnore.class);
}
