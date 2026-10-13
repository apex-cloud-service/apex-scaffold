package com.apex.scaffold.file.domain.enums;

/**
 * 存储类型枚举。
 *
 * <p>定义支持的文件存储类型，用于策略模式选择不同的存储实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum StorageType {

    /** MinIO 存储 */
    MINIO,

    /** 火山云存储 */
    VOLCENGINE,

    /** 本地存储（用于测试） */
    LOCAL
}
