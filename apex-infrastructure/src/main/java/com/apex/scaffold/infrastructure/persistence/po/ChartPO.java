package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 图表持久化对象。
 *
 * <p>对应 {@code doc_chart} 表，存储文档图表配置：图表名称、类型（折线/柱状/饼图等）、
 * 分类、描述、数据源、ECharts 配置（JSON，经 JacksonTypeHandler 映射）、
 * 创建人信息、状态与查看次数，并携带租户标识实现多租户隔离。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName(value = "doc_chart", autoResultMap = true)
public class ChartPO extends BasePO {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String chartName;

    private String chartType;

    private String category;

    private String description;

    private String dataSource;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    private Long creatorId;

    private String creatorName;

    private Integer status;

    private Integer viewCount;

    private String tenantId;
}
