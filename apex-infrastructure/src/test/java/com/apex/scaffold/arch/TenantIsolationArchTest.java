package com.apex.scaffold.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.apex.scaffold.infrastructure.persistence",
        importOptions = ImportOption.DoNotIncludeTests.class)
class TenantIsolationArchTest {

    @ArchTest
    static final ArchRule persistence_classes_must_not_bypass_tenant_interceptor =
            noClasses().should().beAnnotatedWith(InterceptorIgnore.class);

    @ArchTest
    static final ArchRule persistence_methods_must_not_bypass_tenant_interceptor =
            noMethods().should().beAnnotatedWith(InterceptorIgnore.class);
}
