package com.apex.example.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 集成测试基类
 *
 * <p>提供通用的测试配置和WebTestClient实例
 *
 * @author Apex Team
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected WebTestClient webTestClient;

    /**
     * 每个测试前的清理工作
     */
    @BeforeEach
    void setUp() {
        // 子类可以重写此方法进行测试数据准备
    }
}
