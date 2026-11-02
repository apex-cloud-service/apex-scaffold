package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.integration.device.domain.Device;

/**
 * 设备注册结果（方案 M5 / 批次 E3）。
 *
 * @param device       落库后的设备实体（仅含哈希，不含明文）
 * @param plainSecret  生成的明文 secret（仅在注册响应出现一次，服务端不落库不落日志）
 * @param duplicateKey 是否与既有设备重名（false 为正常注册）
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public record DeviceRegistration(Device device, String plainSecret, boolean duplicateKey) {}
