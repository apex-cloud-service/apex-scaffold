package com.apex.scaffold.common.result;

import com.apex.scaffold.common.error.ErrorCode;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 统一响应结果封装类
 *
 * <p>用于封装 API 接口的统一返回格式，包含状态码、消息、数据和追踪 ID。
 * 使用 {@code @Getter} 和 {@code @Setter} 替代 {@code @Data} 以避免 Lombok 生成的
 * {@code setTraceId(void)} 与手动定义的链式 {@code setTraceId(Result<T>)} 产生冲突。
 *
 * @param <T> 响应数据的类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 响应状态码 */
    private Integer code;

    /** 响应消息 */
    private String msg;

    /** 响应数据 */
    private T data;

    /** 追踪ID，用于链路追踪 */
    private String traceId;

    /**
     * 创建成功响应（无数据）
     *
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null, null);
    }

    /**
     * 创建成功响应（带数据）
     *
     * @param <T> 响应数据类型
     * @param data 响应数据
     * @return 成功响应对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data, null);
    }

    /**
     * 创建成功响应（带数据和追踪ID）
     *
     * @param <T> 响应数据类型
     * @param data 响应数据
     * @param traceId 追踪ID
     * @return 成功响应对象
     */
    public static <T> Result<T> success(T data, String traceId) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data, traceId);
    }

    /**
     * 创建错误响应（默认错误）
     *
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> Result<T> error() {
        return new Result<>(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg(), null, null);
    }

    /**
     * 创建错误响应（自定义消息）
     *
     * @param <T> 响应数据类型
     * @param msg 错误消息
     * @return 错误响应对象
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(ResultCode.ERROR.getCode(), msg, null, null);
    }

    /**
     * 创建错误响应（自定义状态码和消息）
     *
     * @param <T> 响应数据类型
     * @param code 错误状态码
     * @param msg 错误消息
     * @return 错误响应对象
     */
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null, null);
    }

    /**
     * 创建错误响应（使用预定义错误码）
     *
     * @param <T> 响应数据类型
     * @param resultCode 错误码枚举
     * @return 错误响应对象
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null, null);
    }

    /**
     * 创建错误响应（使用预定义错误码和追踪ID）
     *
     * @param <T> 响应数据类型
     * @param resultCode 错误码枚举
     * @param traceId 追踪ID
     * @return 错误响应对象
     */
    public static <T> Result<T> error(ResultCode resultCode, String traceId) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null, traceId);
    }

    /**
     * 创建错误响应（自定义状态码、消息和追踪ID）
     *
     * @param <T> 响应数据类型
     * @param code 错误状态码
     * @param msg 错误消息
     * @param traceId 追踪ID
     * @return 错误响应对象
     */
    public static <T> Result<T> error(Integer code, String msg, String traceId) {
        return new Result<>(code, msg, null, traceId);
    }

    /**
     * 创建错误响应（使用统一错误码枚举）
     *
     * @param <T> 响应数据类型
     * @param errorCode 统一错误码
     * @return 错误响应对象
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    /**
     * 创建错误响应（使用统一错误码枚举和追踪ID）
     *
     * @param <T> 响应数据类型
     * @param errorCode 统一错误码
     * @param traceId 追踪ID
     * @return 错误响应对象
     */
    public static <T> Result<T> error(ErrorCode errorCode, String traceId) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, traceId);
    }

    /**
     * 设置追踪ID（链式调用）
     *
     * <p>由于 Lombok {@code @Setter} 为 {@code traceId} 字段生成的是 void 返回类型的 setter，
     * 此处手动定义返回 {@code Result<T>} 的链式 setter 以支持流式调用。
     *
     * @param traceId 追踪ID
     * @return 当前响应对象，支持链式调用
     */
    public Result<T> setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
