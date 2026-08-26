package com.apex.scaffold.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 分页请求基类
 *
 * <p>所有分页请求的基类，包含分页参数。使用 Lombok {@code @Data} 自动生成 getter/setter 等方法。
 * {@code isAsc} 字段通过 {@code @JsonProperty("isAsc")} 确保 JSON 序列化/反序列化时字段名一致，
 * 避免 Lombok 生成的 {@code getIsAsc()} 导致序列化问题。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class PageRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 当前页码
     *
     * <p>默认值为1，最小值为1
     */
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    /**
     * 每页大小
     *
     * <p>默认值为10，最小值为1，最大值为100
     */
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    private Integer pageSize = 10;

    /**
     * 排序字段
     *
     * <p>如：id, create_time等
     */
    private String orderBy;

    /**
     * 是否升序
     *
     * <p>默认值为true。使用 {@code @JsonProperty("isAsc")} 注解确保 Jackson 序列化/反序列化时
     * 字段名始终为 "isAsc"，避免 Lombok {@code @Data} 生成 {@code getIsAsc()} 导致的序列化不一致问题。
     */
    @JsonProperty("isAsc")
    private Boolean isAsc = true;

    /**
     * 获取起始偏移量
     *
     * <p>计算公式：(pageNum - 1) * pageSize
     *
     * @return 偏移量
     */
    public Integer getOffset() {
        int pn = pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
        int ps = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return (pn - 1) * ps;
    }
}
