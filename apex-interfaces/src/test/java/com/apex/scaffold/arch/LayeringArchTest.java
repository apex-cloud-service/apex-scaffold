package com.apex.scaffold.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 架构分层测试类。
 *
 * <p>使用 ArchUnit 验证模块间依赖关系，确保符合 DDD（领域驱动设计）架构原则。
 * 主要验证以下规则：
 * <ul>
 *   <li>Domain 层不依赖 Interfaces 层和 Infrastructure 层</li>
 *   <li>Application 层不依赖 Interfaces 层和 Infrastructure 层</li>
 *   <li>Infrastructure 层不依赖 Interfaces 层</li>
 *   <li>Domain 层不依赖任何持久化框架（MyBatis-Plus 等）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@AnalyzeClasses(packages = "com.apex.scaffold", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringArchTest {

    /**
     * Domain 层不应依赖 Interfaces 层或 Infrastructure 层。
     *
     * <p>Domain 层是核心业务逻辑所在，应保持对技术实现的独立性。
     */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_interfaces_or_infrastructure = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..interfaces..", "..infrastructure..");

    /**
     * Application 层不应依赖 Interfaces 层或 Infrastructure 实现。
     *
     * <p>Application 层通过 Domain 层定义的端口获取缓存、指标、事务结算等技术能力，
     * 具体实现由 Infrastructure 层提供，防止依赖方向倒置。
     */
    @ArchTest
    static final ArchRule application_does_not_depend_on_interfaces_or_infrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..interfaces..", "..infrastructure..");

    /**
     * 普通 Spring JDBC 事务不得标注返回 {@link Mono} 的响应式方法。
     *
     * <p>JDBC 事务在线程本地变量中绑定，方法返回 Publisher 后事务切面已经结束，后续订阅
     * 无法加入原事务。</p>
     */
    @ArchTest
    static final ArchRule transactional_methods_must_not_return_mono =
            noMethods().that().areAnnotatedWith(Transactional.class).should().haveRawReturnType(Mono.class);

    /**
     * 普通 Spring JDBC 事务不得标注返回 {@link Flux} 的响应式方法。
     */
    @ArchTest
    static final ArchRule transactional_methods_must_not_return_flux =
            noMethods().that().areAnnotatedWith(Transactional.class).should().haveRawReturnType(Flux.class);

    /**
     * Infrastructure 层不应依赖 Interfaces 层。
     *
     * <p>Infrastructure 层为 Domain 层提供技术实现，
     * 应保持对上层接口层的独立性。
     */
    @ArchTest
    static final ArchRule infrastructure_does_not_depend_on_interfaces = noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..interfaces..");

    /**
     * Domain 层不应依赖 MyBatis-Plus 等持久化框架。
     *
     * <p>Domain 层是纯业务逻辑，不应直接依赖任何 ORM 框架。
     * 持久化相关的注解和类应仅出现在 Infrastructure 层中。
     */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_mybatis_plus = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.baomidou..", "..mybatisplus..");
}
