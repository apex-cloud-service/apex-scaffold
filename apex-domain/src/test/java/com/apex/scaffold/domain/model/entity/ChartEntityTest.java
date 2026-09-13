package com.apex.scaffold.domain.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChartEntity 单元测试")
class ChartEntityTest {

    @Test
    @DisplayName("创建图表实体")
    void testCreate() {
        ChartEntity entity =
                ChartEntity.create("销售趋势图", "line", "business", "月度销售趋势分析", "sales_db", 1L, "李四", "tenant-001");

        assertNotNull(entity);
        assertEquals("销售趋势图", entity.getChartName());
        assertEquals("line", entity.getChartType());
        assertEquals("business", entity.getCategory());
        assertEquals("月度销售趋势分析", entity.getDescription());
        assertEquals("sales_db", entity.getDataSource());
        assertEquals(1L, entity.getCreatorId());
        assertEquals("李四", entity.getCreatorName());
        assertEquals("tenant-001", entity.getTenantId());
        assertEquals(ChartEntity.ChartStatus.DRAFT.getCode(), entity.getStatus());
        assertEquals(0, entity.getViewCount());
        assertNotNull(entity.getCreateTime());
        assertNotNull(entity.getUpdateTime());
    }

    @Test
    @DisplayName("更新图表信息")
    void testUpdateInfo() {
        ChartEntity entity = ChartEntity.create("原标题", "line", "business", "原描述", "db1", 1L, "李四", "tenant-001");

        Map<String, Object> config = new HashMap<>();
        config.put("color", "blue");
        config.put("smooth", true);

        entity.updateInfo("新标题", "bar", "tech", "新描述", "db2", config);

        assertEquals("新标题", entity.getChartName());
        assertEquals("bar", entity.getChartType());
        assertEquals("tech", entity.getCategory());
        assertEquals("新描述", entity.getDescription());
        assertEquals("db2", entity.getDataSource());
        assertEquals(config, entity.getConfig());
    }

    @Test
    @DisplayName("发布图表")
    void testPublish() {
        ChartEntity entity = ChartEntity.create("测试图表", "pie", "operation", "描述", "db", 1L, "李四", "tenant-001");

        assertTrue(entity.isDraft());
        assertFalse(entity.isNormal());

        entity.publish();

        assertFalse(entity.isDraft());
        assertTrue(entity.isNormal());
        assertEquals(ChartEntity.ChartStatus.NORMAL.getCode(), entity.getStatus());
    }

    @Test
    @DisplayName("撤回为草稿")
    void testRevertToDraft() {
        ChartEntity entity = ChartEntity.create("测试图表", "pie", "operation", "描述", "db", 1L, "李四", "tenant-001");

        entity.publish();
        assertTrue(entity.isNormal());

        entity.revertToDraft();
        assertTrue(entity.isDraft());
        assertFalse(entity.isNormal());
        assertEquals(ChartEntity.ChartStatus.DRAFT.getCode(), entity.getStatus());
    }

    @Test
    @DisplayName("增加查看次数")
    void testIncrementViewCount() {
        ChartEntity entity = ChartEntity.create("测试图表", "scatter", "finance", "描述", "db", 1L, "李四", "tenant-001");

        assertEquals(0, entity.getViewCount());

        entity.incrementViewCount();
        assertEquals(1, entity.getViewCount());

        entity.incrementViewCount();
        assertEquals(2, entity.getViewCount());
    }

    @Test
    @DisplayName("图表状态判断")
    void testStatusCheck() {
        ChartEntity entity = ChartEntity.create("测试图表", "radar", "tech", "描述", "db", 1L, "李四", "tenant-001");

        assertTrue(entity.isDraft());
        assertFalse(entity.isNormal());

        entity.publish();
        assertFalse(entity.isDraft());
        assertTrue(entity.isNormal());

        entity.revertToDraft();
        assertTrue(entity.isDraft());
        assertFalse(entity.isNormal());
    }

    @Test
    @DisplayName("图表类型枚举")
    void testChartTypeEnum() {
        assertEquals("line", ChartEntity.ChartType.LINE.getCode());
        assertEquals("bar", ChartEntity.ChartType.BAR.getCode());
        assertEquals("pie", ChartEntity.ChartType.PIE.getCode());
        assertEquals("scatter", ChartEntity.ChartType.SCATTER.getCode());
        assertEquals("radar", ChartEntity.ChartType.RADAR.getCode());
    }

    @Test
    @DisplayName("图表分类枚举")
    void testCategoryEnum() {
        assertEquals("business", ChartEntity.Category.BUSINESS.getCode());
        assertEquals("tech", ChartEntity.Category.TECH.getCode());
        assertEquals("operation", ChartEntity.Category.OPERATION.getCode());
        assertEquals("finance", ChartEntity.Category.FINANCE.getCode());
    }

    @Test
    @DisplayName("设置图表配置")
    void testSetConfig() {
        ChartEntity entity = ChartEntity.create("测试图表", "line", "business", "描述", "db", 1L, "李四", "tenant-001");

        Map<String, Object> config = new HashMap<>();
        config.put("title", "图表标题");
        config.put("legend", true);
        config.put("grid", Map.of("left", "10%", "right", "10%"));

        entity.setConfig(config);

        assertNotNull(entity.getConfig());
        assertEquals(config, entity.getConfig());
        assertEquals("图表标题", entity.getConfig().get("title"));
    }
}
