package com.apex.scaffold.knowledge;

import com.apex.scaffold.infrastructure.persistence.config.MyMetaObjectHandler;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 知识库模块测试装配入口（仅测试 classpath 使用）。
 *
 * <p>复刻 {@code KnowledgeServiceApplication} 的扫描口径：组件扫描业务库根包、
 * Mapper 以 {@code @Mapper} 注解限定登记、字段填充处理器显式导入；
 * 数据源与存储由测试 application.yml 提供（H2 + 本地存储）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@SpringBootApplication
@MapperScan(
        basePackages = "com.apex.scaffold.knowledge.infrastructure.persistence.mapper",
        annotationClass = Mapper.class)
@Import(MyMetaObjectHandler.class)
public class KnowledgeTestApplication {}
