package com.apex.scaffold.tools.deployment;

import java.util.List;

/**
 * 结构前置检查抽象，测试可注入固定的计划序列。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public interface PreflightCheck {

    /**
     * 执行前置检查。
     *
     * @param client 已连接客户端
     * @param source V42 迁移源文件内容
     * @return 需要执行的恢复语句；为空表示结构已符合契约
     * @throws Exception 检查不通过或客户端失败时抛出
     */
    List<String> check(RecoverClient client, String source) throws Exception;
}
