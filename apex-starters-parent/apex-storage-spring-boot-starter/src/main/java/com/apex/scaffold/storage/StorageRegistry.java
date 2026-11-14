package com.apex.scaffold.storage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 严格注册策略，重复、未启用与未知类型均明确失败，禁止隐式更换提供商。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class StorageRegistry {
    private final Map<String, ObjectStorage> providers = new LinkedHashMap<>();
    private final ObjectStorage defaultStorage;

    /** @param storages 已启用策略 @param defaultType 默认类型 @throws IllegalArgumentException 重复类型或默认类型不可用 */
    public StorageRegistry(Collection<ObjectStorage> storages, String defaultType) {
        for (ObjectStorage storage : storages) {
            if (providers.putIfAbsent(storage.type(), storage) != null) {
                throw new IllegalArgumentException("重复存储策略: " + storage.type());
            }
        }
        defaultStorage = require(defaultType);
    }

    /** @param type 精确提供商标识 @return 已注册策略 @throws IllegalArgumentException 类型未注册 */
    public ObjectStorage require(String type) {
        ObjectStorage storage = providers.get(type);
        if (storage == null) {
            throw new IllegalArgumentException("存储策略未启用或未知: " + type);
        }
        return storage;
    }

    /** @return 已注册提供商标识的不可变快照 */
    public java.util.Set<String> types() {
        return java.util.Set.copyOf(providers.keySet());
    }

    /** @return 配置指定的默认策略 */
    public ObjectStorage defaultStorage() {
        return defaultStorage;
    }
}
