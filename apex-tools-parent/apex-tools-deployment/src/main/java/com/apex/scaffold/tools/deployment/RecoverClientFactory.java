package com.apex.scaffold.tools.deployment;

import java.util.List;

/**
 * 创建 {@link RecoverClient} 的工厂，便于测试记录命令前缀与目标库。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public interface RecoverClientFactory {

    /**
     * 创建客户端。
     *
     * @param command  mysql 客户端命令前缀
     * @param database 目标数据库
     * @return 客户端实例
     */
    RecoverClient create(List<String> command, String database);
}
