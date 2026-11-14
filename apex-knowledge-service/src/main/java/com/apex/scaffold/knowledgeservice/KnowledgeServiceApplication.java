package com.apex.scaffold.knowledgeservice;

import com.apex.scaffold.infrastructure.persistence.config.MyMetaObjectHandler;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 知识库服务启动类（可选运行模块，对齐 job/masking 模式）。
 *
 * <p>将知识库能力（知识空间/文档摄取/混合检索/问答编排）从业务库
 * {@code apex-knowledge} 装配为可启动的服务（端口 8099 / 独立 {@code apex_knowledge} 库）。
 * 不注册 Nacos：网关以 {@code ROUTE_KNOWLEDGE_URI} 固定地址路由（可选服务口径）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@code com.apex.scaffold.knowledgeservice} - 本服务安全装配与接口层</li>
 *   <li>{@code com.apex.scaffold.knowledge} - 知识库业务库（domain/application/infrastructure）</li>
 *   <li>{@code com.apex.scaffold.common} - 共享基础组件</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>MyBatis-Plus 拦截器链经 {@code apex-infrastructure-persistence} 的
 * AutoConfiguration 自动装配；字段填充处理器 {@code MyMetaObjectHandler} 为普通
 * {@code @Component}，经 {@code @Import} 显式登记（服务不扫描 infrastructure 包）。
 * Mapper 经 {@link MapperScan} 以域根包声明，{@code annotationClass = Mapper.class}
 * 确保仅注册标注 {@link Mapper} 的接口。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.apex.scaffold.knowledgeservice",
            "com.apex.scaffold.knowledge",
            "com.apex.scaffold.common"
        })
@MapperScan(
        basePackages = "com.apex.scaffold.knowledge.infrastructure.persistence.mapper",
        annotationClass = Mapper.class)
@Import(MyMetaObjectHandler.class)
@EnableScheduling
public class KnowledgeServiceApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeServiceApplication.class, args);
    }
}
