package com.apex.scaffold.knowledge.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 知识库模块分层架构守护测试。
 *
 * <p>约束与 system 的 LayeringArchTest 同源：domain/application 不得依赖
 * infrastructure，domain 不依赖 application/infrastructure；并以源码文本扫描
 * 禁止直接使用 {@code Schedulers.boundedElastic()}（对齐 NoBoundedElasticArchGuardTest 口径）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class KnowledgeLayeringArchTest {

    @Test
    void layeringIsRespected() {
        ArchRule rule = Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain")
                .definedBy("com.apex.scaffold.knowledge.domain..")
                .layer("application")
                .definedBy("com.apex.scaffold.knowledge.application..")
                .layer("infrastructure")
                .definedBy("com.apex.scaffold.knowledge.infrastructure..")
                .whereLayer("application")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("infrastructure")
                .mayNotBeAccessedByAnyLayer()
                .withOptionalLayers(true);
        rule.check(importClasses());
    }

    @Test
    void domainDoesNotDependOnPersistenceTechnology() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.apex.scaffold.knowledge.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.baomidou..", "org.apache.ibatis..", "org.springframework.web..");
        rule.check(importClasses());
    }

    @Test
    void noDirectBoundedElasticInMainSources() throws IOException {
        Path moduleRoot = Path.of(System.getProperty("user.dir"), "src", "main", "java");
        try (Stream<Path> files = Files.walk(moduleRoot)) {
            List<String> offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::readAll)
                    .filter(content -> content.contains("Schedulers.boundedElastic"))
                    .toList();
            org.assertj.core.api.Assertions.assertThat(offenders)
                    .as("知识库主源码禁止直接使用 Schedulers.boundedElastic()，请改用 BlockingIoSchedulers 专用池")
                    .isEmpty();
        }
    }

    /**
     * 读取源码全文。
     *
     * @param path 源码文件
     * @return 文件内容；读取失败返回空串
     */
    private String readAll(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 导入知识库模块 classes。
     *
     * @return 导入的类集合
     */
    private com.tngtech.archunit.core.domain.JavaClasses importClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPath(Path.of(System.getProperty("user.dir"), "target", "classes")
                        .toString());
    }
}
