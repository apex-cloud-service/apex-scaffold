package com.apex.scaffold.common.annotation;

/**
 * 操作类型枚举
 *
 * @author Apex Team
 * @version 1.0.0
 */
public enum OperationType {
    /**
     * 新增
     */
    CREATE,

    /**
     * 修改
     */
    UPDATE,

    /**
     * 删除
     */
    DELETE,

    /**
     * 查询
     */
    QUERY,

    /**
     * 导出
     */
    EXPORT,

    /**
     * 导入
     */
    IMPORT,

    /**
     * 登录
     */
    LOGIN,

    /**
     * 登出
     */
    LOGOUT,

    /**
     * 授权
     */
    AUTHORIZE,

    /**
     * 其他
     */
    OTHER
}
