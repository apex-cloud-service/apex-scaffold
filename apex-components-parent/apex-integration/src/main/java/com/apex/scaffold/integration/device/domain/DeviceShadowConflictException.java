package com.apex.scaffold.integration.device.domain;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;

/**
 * 设备影子版本冲突异常（方案 M5 / 批次 E3）。
 *
 * <p>影子 {@code reported} 更新以 {@code version} 乐观锁提交；数据库更新 0 行时抛出，
 * 由应用服务捕获并有限次重读重试，超出重试上限后向调用方传播。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class DeviceShadowConflictException extends BusinessException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造影子版本冲突异常。
     *
     * @param deviceKey 设备标识
     * @param version   提交时使用的期望版本
     */
    public DeviceShadowConflictException(String deviceKey, int version) {
        super(ResultCode.BUSINESS_ERROR, "设备影子版本冲突: device=" + deviceKey + ", version=" + version);
    }
}
