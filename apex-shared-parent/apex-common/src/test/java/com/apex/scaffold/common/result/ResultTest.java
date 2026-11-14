package com.apex.scaffold.common.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Result 统一响应封装测试类
 */
class ResultTest {

    @Test
    void testSuccessWithoutData() {
        Result<Void> result = Result.success();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMsg()).isEqualTo("成功");
        assertThat(result.getData()).isNull();
    }

    @Test
    void testSuccessWithData() {
        String testData = "test data";
        Result<String> result = Result.success(testData);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo(testData);
    }

    @Test
    void testSuccessWithDataAndTraceId() {
        String testData = "test data";
        String traceId = "trace-123";
        Result<String> result = Result.success(testData, traceId);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo(testData);
        assertThat(result.getTraceId()).isEqualTo(traceId);
    }

    @Test
    void testErrorWithoutMessage() {
        Result<Void> result = Result.error();

        assertThat(result.getCode()).isEqualTo(-1);
        assertThat(result.getMsg()).isEqualTo("系统错误");
        assertThat(result.getData()).isNull();
    }

    @Test
    void testErrorWithCustomMessage() {
        String errorMsg = "自定义错误消息";
        Result<Void> result = Result.error(errorMsg);

        assertThat(result.getCode()).isEqualTo(-1);
        assertThat(result.getMsg()).isEqualTo(errorMsg);
    }

    @Test
    void testErrorWithCodeAndMessage() {
        Integer errorCode = 404;
        String errorMsg = "资源未找到";
        Result<Void> result = Result.error(errorCode, errorMsg);

        assertThat(result.getCode()).isEqualTo(errorCode);
        assertThat(result.getMsg()).isEqualTo(errorMsg);
    }

    @Test
    void testErrorWithResultCode() {
        Result<Void> result = Result.error(ResultCode.PARAM_ERROR);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMsg()).isEqualTo(ResultCode.PARAM_ERROR.getMsg());
    }

    @Test
    void testSetTraceIdChainable() {
        Result<String> result = Result.success("data");
        Result<String> chained = result.setTraceId("new-trace-id");

        assertThat(chained).isSameAs(result);
        assertThat(result.getTraceId()).isEqualTo("new-trace-id");
    }
}
