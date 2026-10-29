package com.apex.scaffold.file.infrastructure.storage.service;

import com.apex.scaffold.storage.TosStorage;
import com.volcengine.tos.TOSV2;

/**
 * TOS 旧类名兼容门面，下载保留流式语义并传播访问错误；不参与组件扫描。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class VolcEngineStorageServiceImpl extends ObjectStorageServiceAdapter {
    /** @param client TOS 客户端 */
    public VolcEngineStorageServiceImpl(TOSV2 client) {
        super(new TosStorage(client));
    }
}
