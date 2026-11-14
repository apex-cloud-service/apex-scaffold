package com.apex.scaffold.tools.deployment;

/**
 * 离线运维工具的前置检查失败：消息不含密钥、密文或数据库内容，可直接展示给运维人员。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class RejectedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * 构造可安全展示的检查失败异常。
     *
     * @param message 不含敏感数据的原因说明
     */
    public RejectedException(String message) {
        super(message);
    }
}
