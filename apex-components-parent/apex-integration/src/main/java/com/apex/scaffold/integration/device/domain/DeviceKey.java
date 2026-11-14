package com.apex.scaffold.integration.device.domain;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.util.regex.Pattern;

/**
 * 设备标识值对象（方案 M5 / 批次 E3）。
 *
 * <p>设备标识同时用作 MQTT topic 命名空间段
 * （{@code apex/{tenantCode}/{deviceKey}/up|down}），因此仅允许 topic 安全字符：
 * 字母数字、下划线、连字符，1~64 位，且不以符号开头。
 *
 * @param value 设备标识原文
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public record DeviceKey(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    /**
     * 构造并校验设备标识。
     *
     * @param value 设备标识原文
     * @return 设备标识值对象
     * @throws BusinessException 标识为空或含非法字符
     */
    public static DeviceKey of(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备标识非法（1~64 位，仅字母数字/下划线/连字符且不以符号开头）");
        }
        return new DeviceKey(value);
    }
}
