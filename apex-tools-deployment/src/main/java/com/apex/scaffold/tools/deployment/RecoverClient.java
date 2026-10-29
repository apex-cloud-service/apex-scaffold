package com.apex.scaffold.tools.deployment;

import java.util.List;

/**
 * mysql 客户端抽象：真实实现走 mysql CLI，测试注入伪实现。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public interface RecoverClient {

    /**
     * 执行语句并返回去除首尾空白后的标准输出。
     *
     * @param sql 待执行 SQL
     * @return 标准输出文本
     * @throws Exception 客户端执行失败时抛出
     */
    String execute(String sql) throws Exception;

    /**
     * 执行查询并将每行解析为 JSON 数组。
     *
     * @param query 查询语句
     * @return 结果行集合
     * @throws Exception 客户端执行或解析失败时抛出
     */
    List<List<Object>> rows(String query) throws Exception;

    /**
     * 返回连接的目标库名，用于视图定义规范化。
     *
     * @return 数据库名
     */
    String database();
}
