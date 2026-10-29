package com.apex.scaffold.license;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * License授权版本枚举
 *
 * <p>本枚举定义了系统支持的所有License版本类型,每个版本对应不同的功能集和价格策略。
 * 版本之间呈递进关系,高级版本包含低级版本的所有功能。</p>
 *
 * <h3>版本体系</h3>
 * <ul>
 *   <li><b>基础版(BASIC)</b>:免费版本,提供核心功能,不包含任何商业高级特性</li>
 *   <li><b>标准版(STANDARD)</b>:付费版本,包含审计日志、API版本管理等P1优先级功能</li>
 *   <li><b>企业版(ENTERPRISE)</b>:旗舰版本,包含所有功能,支持定制化开发和专属技术支持</li>
 * </ul>
 *
 * <h3>功能映射</h3>
 * <p>不同版本包含的功能特性:
 * <table border="1">
 *   <tr>
 *     <th>功能特性</th>
 *     <th>基础版</th>
 *     <th>标准版</th>
 *     <th>企业版</th>
 *   </tr>
 *   <tr>
 *     <td>核心业务功能</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <td>审计日志</td>
 *     <td>✗</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <td>API版本管理</td>
 *     <td>✗</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <td>高级缓存</td>
 *     <td>✗</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <td>消息队列集成</td>
 *     <td>✗</td>
 *     <td>✗</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <td>分布式追踪</td>
 *     <td>✗</td>
 *     <td>✗</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <td>定制化开发</td>
 *     <td>✗</td>
 *     <td>✗</td>
 *     <td>✓</td>
 *   </tr>
 * </table>
 *
 * <h3>价格策略</h3>
 * <p>价格以分为单位存储,便于精确计算和货币转换:
 * <ul>
 *   <li>基础版:免费(0分/年)</li>
 *   <li>标准版:9,800分/年(98元/年)</li>
 *   <li>企业版:29,800分/年(298元/年)</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 从License信息中获取版本
 * LicenseInfo info = validator.getLicenseInfo();
 * LicenseVersion version = info.getVersion();
 * System.out.println("当前版本: " + version.getDisplayName());
 * System.out.println("年度价格: " + version.getAnnualPrice() + "分");
 *
 * // 版本比较
 * if (version == LicenseVersion.ENTERPRISE) {
 *     // 启用企业版专属功能
 *     enableEnterpriseFeatures();
 * }
 *
 * // 遍历所有版本
 * for (LicenseVersion v : LicenseVersion.values()) {
 *     System.out.println(v.getDisplayName() + ": " + v.getAnnualPrice() + "分/年");
 * }
 *
 * // 在配置文件中指定版本
 * // license.json
 * {
 *   "version": "STANDARD",
 *   "customerName": "示例公司",
 *   "expireTime": "2025-12-31T23:59:59"
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see LicenseInfo
 * @see LicenseValidator
 */
@Getter
@RequiredArgsConstructor
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
}
