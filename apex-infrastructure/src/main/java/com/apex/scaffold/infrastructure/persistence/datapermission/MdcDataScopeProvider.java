package com.apex.scaffold.infrastructure.persistence.datapermission;

import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.context.DataScopeProvider;
import com.apex.scaffold.common.enums.DataScopeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * 默认数据权限装配器（ThreadLocal 优先 + MDC 兜底）。
 *
 * <p>解析顺序：</p>
 * <ol>
 *   <li>{@link DataScopeContext}（ThreadLocal）：由 {@code DataScopeAspect} 或业务代码
 *       显式装配，优先级最高；</li>
 *   <li>MDC 键：{@code apex.userId} / {@code apex.deptId} / {@code apex.dataScope} /
 *       {@code apex.dataScopeDeptIds}，适用于认证过滤器在请求线程写入、查询在同一线程
 *       执行的同步场景（如定时任务、内部调用、经 ThreadLocal 透明调度器执行的阻塞查询）。</li>
 * </ol>
 *
 * <p><b>MDC 键语义</b>：</p>
 * <ul>
 *   <li>{@code apex.dataScope}：{@link DataScopeType} 编码（1-5），缺省时按
 *       {@code apex.deptId} 存在与否回退为"本部门数据权限"；</li>
 *   <li>{@code apex.dataScopeDeptIds}：逗号分隔的可见部门 ID 集合
 *       （{@link DataScopeType#DEPT_AND_CHILD} / {@link DataScopeType#CUSTOM} 使用）。</li>
 * </ul>
 *
 * <p>响应式应用注意：阻塞查询经 {@code BlockingIoSchedulers.jdbc()} 切换到 JDBC 线程执行，
 * 请求线程写入的 ThreadLocal/MDC 不会自动到达该线程；如需在响应式链路中使用数据权限拦截，
 * 应确保装配发生在查询执行线程（详见 {@code docs/DATA_SCOPE.md}）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
@Slf4j
public class MdcDataScopeProvider implements DataScopeProvider {

    private static final String COMMA_SEPARATOR = ",";

    /** MDC 键：当前用户 ID */
    public static final String MDC_USER_ID = "apex.userId";

    /** MDC 键：当前用户所属部门 ID */
    public static final String MDC_DEPT_ID = "apex.deptId";

    /** MDC 键：数据权限类型编码 */
    public static final String MDC_DATA_SCOPE = "apex.dataScope";

    /** MDC 键：可见部门 ID 集合（逗号分隔） */
    public static final String MDC_DATA_SCOPE_DEPT_IDS = "apex.dataScopeDeptIds";

    /**
     * 解析当前请求的数据权限信息。
     *
     * @return 数据权限信息；无法解析时返回 {@link Optional#empty()}（放行不拦截）
     */
    @Override
    public Optional<DataScopeInfo> resolve() {
        Optional<DataScopeInfo> explicit = DataScopeContext.currentDataScope();
        if (explicit.isPresent()) {
            return explicit;
        }
        return resolveFromMdc();
    }

    /**
     * 从 MDC 键解析数据权限信息。
     */
    private Optional<DataScopeInfo> resolveFromMdc() {
        Long userId = parseLongSafe(MDC.get(MDC_USER_ID));
        Long deptId = parseLongSafe(MDC.get(MDC_DEPT_ID));
        String typeCode = MDC.get(MDC_DATA_SCOPE);
        DataScopeType type = typeCode == null ? null : DataScopeType.fromCode(parseIntSafe(typeCode));
        List<Long> deptIds = parseLongList(MDC.get(MDC_DATA_SCOPE_DEPT_IDS));

        if (type != null) {
            return Optional.of(new DataScopeInfo(type, userId, deptId, deptIds));
        }
        if (deptId != null) {
            // 未显式声明类型时，按"本部门数据权限"处理
            return Optional.of(DataScopeInfo.ofDept(deptId));
        }
        return Optional.empty();
    }

    /**
     * 解析逗号分隔的 Long 列表。
     */
    private static List<Long> parseLongList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(COMMA_SEPARATOR)) {
            Long id = parseLongSafe(part.trim());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 安全解析 Long（失败返回 {@code null} 并 WARN）。
     */
    private static Long parseLongSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("数据权限 MDC 值解析失败: {}={}", value, e.getMessage());
            return null;
        }
    }

    /**
     * 安全解析 Integer（失败返回 {@code null}）。
     */
    private static Integer parseIntSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("数据权限 MDC 类型编码解析失败: {}={}", value, e.getMessage());
            return null;
        }
    }
}
