package com.apex.scaffold.interfaces.test;

/**
 * 测试数据工厂类
 *
 * <p>提供集成测试中使用的测试数据常量和实体/POJO 创建工具方法。
 *
 * @author Apex Team
 */
public final class TestDataFactory {

    public static final String TEST_OPEN_ID = "test_open_id_001";
    public static final String TEST_NICK_NAME = "测试用户";
    public static final String TEST_AVATAR_URL = "https://example.com/avatar.png";

    /**
     * 创建 {@code TestDataFactory} 实例。
     */
    private TestDataFactory() {
        // 工具类，禁止实例化
    }
}
