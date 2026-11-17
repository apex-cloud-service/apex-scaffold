package com.apex.scaffold.interfaces.util;

import com.apex.scaffold.interfaces.config.DegradeSnapshotProperties;
import com.apex.scaffold.interfaces.exception.DegradeFallbackService;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * 降级快照保存决策记录器。
 *
 * <p>作为"成功响应旁路保存快照"的统一入口，在目标读接口成功响应后调用
 * {@link #saveDegradeSnapshotIfNeeded(ServerRequest, Object)}，由本类完成
 * 方法过滤、资源归一化与白名单判断，命中后委托
 * {@link DegradeFallbackService#saveSnapshot(String, Object)} 保存。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>仅保存 GET/HEAD/OPTIONS 等只读方法的响应，写接口降级走"排队中"，无需快照；</li>
 *   <li>仅保存白名单（{@code resources}）内的归一化资源，
 *       默认空列表 = 不接入任何接口，对现有功能零影响；</li>
 *   <li>禁止接入个人化接口（用户专属对局/资料等）：快照 key 仅资源路径维度，
 *       跨用户复用快照会导致数据错配与业务归属校验失败。</li>
 * </ul>
 *
 * <p>保存动作位于成功响应旁路，任何异常由 {@link DegradeFallbackService} 内部吞掉，
 * 不改变主链路返回值。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DegradeFallbackService
 */
@Component
public class DegradeSnapshotRecorder {

    /** 允许保存快照的只读 HTTP 方法集合。 */
    private static final Set<HttpMethod> READ_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    /** 降级兜底服务，负责快照的本地/Redis 存储。 */
    private final DegradeFallbackService fallbackService;

    /** 降级快照配置（总开关 / 节流 / 容量 / 白名单）。 */
    private final DegradeSnapshotProperties properties;

    /**
     * 构造快照保存决策记录器（构造器注入）。
     *
     * @param fallbackService 降级兜底服务
     * @param properties      降级快照配置属性
     */
    public DegradeSnapshotRecorder(DegradeFallbackService fallbackService, DegradeSnapshotProperties properties) {
        this.fallbackService = fallbackService;
        this.properties = properties;
    }

    /**
     * 决策并保存快照（成功响应旁路调用）。
     *
     * <p>依次执行：总开关与空数据过滤 → 只读方法过滤 → URL 归一化 →
     * 白名单判断，全部通过后保存快照。任一条件不满足均静默跳过，不影响主链路。</p>
     *
     * @param request 当前请求（用于提取方法与路径）
     * @param data    成功响应数据
     */
    public void saveDegradeSnapshotIfNeeded(ServerRequest request, Object data) {
        if (!properties.isEnabled() || data == null || !READ_METHODS.contains(request.method())) {
            return;
        }
        String resource = SentinelResourceUtil.normalizePathVariables(request.path());
        if (resource == null || !properties.getResources().contains(resource)) {
            return;
        }
        fallbackService.saveSnapshot(resource, data);
    }
}
