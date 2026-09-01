package com.apex.scaffold.license;

/**
 * License授权版本枚举
 *
 * @author Apex Team
 * @version 1.0.0
 */
public enum LicenseVersion {
    /**
     * 基础版(免费)
     * <p>包含核心功能,无高级特性
     */
    BASIC("基础版", 0),

    /**
     * 标准版
     * <p>包含审计日志、API版本管理等P1功能
     */
    STANDARD("标准版", 9800),

    /**
     * 企业版
     * <p>包含所有功能,支持定制化
     */
    ENTERPRISE("企业版", 29800);

    private final String displayName;
    private final int annualPrice;

    LicenseVersion(String displayName, int annualPrice) {
        this.displayName = displayName;
        this.annualPrice = annualPrice;
    }

    /**
     * 获取授权版本展示名称
     *
     * @return 版本展示名称,如"基础版"
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取授权版本年度价格
     *
     * @return 年度价格(单位: 分)
     */
    public int getAnnualPrice() {
        return annualPrice;
    }
}
