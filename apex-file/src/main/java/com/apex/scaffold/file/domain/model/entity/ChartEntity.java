package com.apex.scaffold.file.domain.model.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 图表聚合根实体。
 *
 * <p>表示数据可视化图表的领域对象，支持多种图表类型（折线图、柱状图、饼图等），
 * 用于业务数据的可视化展示与分析。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>图表通过 {@code chartType} 区分类型（line/bar/pie/scatter/radar）</li>
 *   <li>图表配置 {@code config} 存储 ECharts 配置 JSON</li>
 *   <li>数据源 {@code dataSource} 指定数据来源（API 地址或查询配置）</li>
 *   <li>图表状态分为草稿（0）和正常（1）两种</li>
 *   <li>支持查看计数统计</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@lombok.NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ChartEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 图表名称。
     *
     * <p>图表的显示名称，用于列表展示与识别。</p>
     */
    private String chartName;

    /**
     * 图表类型。
     *
     * <p>取值说明：line-折线图，bar-柱状图，pie-饼图，scatter-散点图，radar-雷达图。</p>
     *
     * @see ChartType
     */
    private String chartType;

    /**
     * 图表分类。
     *
     * <p>取值说明：business-业务，tech-技术，operation-运营，finance-财务。</p>
     *
     * @see Category
     */
    private String category;

    /**
     * 图表描述。
     *
     * <p>图表的详细说明，用于展示图表用途与数据含义。</p>
     */
    private String description;

    /**
     * 数据源。
     *
     * <p>数据来源配置，可以是 API 地址或数据查询配置。</p>
     */
    private String dataSource;

    /**
     * 图表配置。
     *
     * <p>ECharts 配置 JSON，存储图表的样式、数据格式等配置信息。</p>
     */
    private Map<String, Object> config;

    /**
     * 图表状态。
     *
     * <p>取值说明：0-草稿，1-正常。</p>
     *
     * @see ChartStatus
     */
    private Integer status;

    /**
     * 创建人 ID。
     *
     * <p>关联 sys_user.id，记录图表的创建人。</p>
     */
    private Long creatorId;

    /**
     * 创建人姓名。
     *
     * <p>冗余字段，减少 JOIN 查询，用于列表展示。</p>
     */
    private String creatorName;

    /**
     * 查看次数。
     *
     * <p>图表的查看次数统计。</p>
     */
    private Integer viewCount;

    /**
     * 租户 ID。
     *
     * <p>多租户隔离字段，用于租户数据隔离。</p>
     */
    private String tenantId;

    /**
     * 静态工厂方法：创建图表。
     *
     * @param chartName 图表名称
     * @param chartType 图表类型（line/bar/pie/scatter/radar）
     * @param category 图表分类
     * @param description 图表描述
     * @param dataSource 数据源
     * @param creatorId 创建人 ID
     * @param creatorName 创建人姓名
     * @param tenantId 租户 ID
     * @return 新创建的图表实体，默认为草稿状态
     */
    public static ChartEntity create(
            String chartName,
            String chartType,
            String category,
            String description,
            String dataSource,
            Long creatorId,
            String creatorName,
            String tenantId) {
        ChartEntity entity = new ChartEntity();
        entity.chartName = chartName;
        entity.chartType = chartType;
        entity.category = category;
        entity.description = description;
        entity.dataSource = dataSource;
        entity.creatorId = creatorId;
        entity.creatorName = creatorName;
        entity.tenantId = tenantId;
        entity.status = ChartStatus.DRAFT.getCode();
        entity.viewCount = 0;
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：更新图表核心信息。
     *
     * @param chartName 图表名称
     * @param chartType 图表类型
     * @param category 图表分类
     * @param description 图表描述
     * @param dataSource 数据源
     * @param config 图表配置
     */
    public void updateInfo(
            String chartName,
            String chartType,
            String category,
            String description,
            String dataSource,
            Map<String, Object> config) {
        this.chartName = chartName;
        this.chartType = chartType;
        this.category = category;
        this.description = description;
        this.dataSource = dataSource;
        this.config = config;
        touch();
    }

    /**
     * 领域行为：发布图表。
     *
     * <p>将状态从草稿变为正常，图表可供用户查看。</p>
     */
    public void publish() {
        this.status = ChartStatus.NORMAL.getCode();
        touch();
    }

    /**
     * 领域行为：撤回为草稿。
     *
     * <p>将状态从正常变为草稿，图表不对外展示。</p>
     */
    public void revertToDraft() {
        this.status = ChartStatus.DRAFT.getCode();
        touch();
    }

    /**
     * 领域行为：增加查看次数。
     */
    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0 : this.viewCount) + 1;
        touch();
    }

    /**
     * 领域行为：检查图表是否为草稿。
     *
     * @return true-草稿状态，false-非草稿状态
     */
    public boolean isDraft() {
        return status != null && status.equals(ChartStatus.DRAFT.getCode());
    }

    /**
     * 领域行为：检查图表是否已发布。
     *
     * @return true-已发布，false-未发布
     */
    public boolean isNormal() {
        return status != null && status.equals(ChartStatus.NORMAL.getCode());
    }

    /**
     * 图表状态枚举。
     */
    @Getter
    @lombok.RequiredArgsConstructor
    public enum ChartStatus {
        /** 草稿 */
        DRAFT(0),
        /** 正常 */
        NORMAL(1);

        private final Integer code;
    }

    /**
     * 图表类型枚举。
     */
    @Getter
    @lombok.RequiredArgsConstructor
    public enum ChartType {
        /** 折线图 */
        LINE("line"),
        /** 柱状图 */
        BAR("bar"),
        /** 饼图 */
        PIE("pie"),
        /** 散点图 */
        SCATTER("scatter"),
        /** 雷达图 */
        RADAR("radar");

        private final String code;
    }

    /**
     * 图表分类枚举。
     */
    @Getter
    @lombok.RequiredArgsConstructor
    public enum Category {
        /** 业务 */
        BUSINESS("business"),
        /** 技术 */
        TECH("tech"),
        /** 运营 */
        OPERATION("operation"),
        /** 财务 */
        FINANCE("finance");

        private final String code;
    }
}
