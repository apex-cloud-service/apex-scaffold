package com.apex.example.integration;

import com.apex.example.repository.ExampleUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 集成测试基类
 *
 * <p>提供通用的测试配置和WebTestClient实例。
 * 每个测试前自动清理非种子数据，保证测试隔离性。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    protected final WebTestClient webTestClient;

    private final ExampleUserMapper exampleUserMapper;

    /**
     * 构造器注入共享的测试依赖（Spring Boot 测试框架自动解析构造器参数）。
     *
     * @param webTestClient     响应式 Web 测试客户端
     * @param exampleUserMapper 示例用户 Mapper，用于测试数据清理
     */
    public BaseIntegrationTest(WebTestClient webTestClient, ExampleUserMapper exampleUserMapper) {
        this.webTestClient = webTestClient;
        this.exampleUserMapper = exampleUserMapper;
    }

    /**
     * 每个测试前的清理工作：删除非种子数据，保留 V1 迁移写入的固定 ID 种子用户
     * （id=1,2,3: zhangsan/lisi/wangwu），保证测试可重复运行且互不干扰。
     */
    @BeforeEach
    void setUp() {
        // 清理测试过程中创建的用户（保留种子数据 id <= 3）
        exampleUserMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.apex.example.model.ExampleUser>()
                .gt(com.apex.example.model.ExampleUser::getId, 3L));
    }
}
