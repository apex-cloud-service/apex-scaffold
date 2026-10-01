package com.apex.scaffold.domain.search;

import com.apex.scaffold.common.dto.PageResponse;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * 统一全文搜索仓储接口（Domain 层端口定义）
 *
 * <p>本接口定义系统管理数据（用户、角色、菜单、部门、字典、配置）的全文检索契约，
 * 隔离搜索引擎实现细节，Domain 层与 Application 层仅依赖本接口。
 *
 * <h3>设计理念（Hexagonal Architecture / Ports & Adapters）</h3>
 * <ul>
 *   <li><b>接口归属</b>：本接口属于 Domain 层，由 Infrastructure 层的适配器实现</li>
 *   <li><b>依赖方向</b>：Application 层 → Domain 层接口 ← Infrastructure 层适配器</li>
 *   <li><b>技术无关</b>：接口定义不依赖任何搜索引擎技术（Meilisearch/Elasticsearch），
 *       便于未来切换实现而不影响业务逻辑</li>
 *   <li><b>响应式设计</b>：返回 {@link Mono}，适配项目 WebFlux 响应式技术栈</li>
 * </ul>
 *
 * <h3>核心职责</h3>
 * <ol>
 *   <li><b>混合搜索</b>：跨资源类型（user/role/menu 等）统一检索，返回混合分页结果</li>
 *   <li><b>权限过滤</b>：基于租户ID和用户ID过滤搜索结果，确保数据安全</li>
 *   <li><b>高亮支持</b>：返回匹配关键词的高亮片段，提升用户体验</li>
 *   <li><b>相关性排序</b>：按匹配度评分排序，最相关结果排在前面</li>
 * </ol>
 *
 * <h3>索引数据源</h3>
 * <table border="1">
 *   <tr>
 *     <th>资源类型</th>
 *     <th>数据表</th>
 *     <th>索引字段</th>
 *     <th>备注</th>
 *   </tr>
 *   <tr>
 *     <td>user</td>
 *     <td>sys_user</td>
 *     <td>username, nickname, email, phone</td>
 *     <td>按租户隔离，按数据权限过滤</td>
 *   </tr>
 *   <tr>
 *     <td>role</td>
 *     <td>sys_role</td>
 *     <td>role_name, role_code, description</td>
 *     <td>按租户隔离</td>
 *   </tr>
 *   <tr>
 *     <td>menu</td>
 *     <td>sys_menu</td>
 *     <td>menu_name, path, component</td>
 *     <td>按可见性过滤</td>
 *   </tr>
 *   <tr>
 *     <td>dept</td>
 *     <td>sys_dept</td>
 *     <td>dept_name, dept_code</td>
 *     <td>按租户隔离</td>
 *   </tr>
 *   <tr>
 *     <td>dict</td>
 *     <td>sys_dict_data</td>
 *     <td>dict_label, dict_value, dict_type</td>
 *     <td>仅索引启用状态字典</td>
 *   </tr>
 *   <tr>
 *     <td>config</td>
 *     <td>sys_config</td>
 *     <td>config_name, config_key</td>
 *     <td>仅索引非敏感配置</td>
 *   </tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在 Application Service 中通过构造器注入本接口
 * @Service
 * @RequiredArgsConstructor
 * public class SearchApplicationService {
 *
 *     private final SearchRepository searchRepository;
 *
 *     public Mono<PageResponse<SearchResultEntity>> searchUsers(String keyword) {
 *         // 跨类型混合搜索
 *         return searchRepository.search(
 *             keyword,                    // 关键词
 *             Set.of("user", "role"),    // 搜索范围：用户和角色
 *             1L,                         // 租户ID（从 SecurityContext 获取）
 *             1001L,                      // 当前用户ID（从 SecurityContext 获取）
 *             1,                          // 页码
 *             20                          // 每页大小
 *         );
 *     }
 * }
 * }</pre>
 *
 * <h3>实现注意事项</h3>
 * <ul>
 *   <li><b>异步非阻塞</b>：Infrastructure 层适配器应使用 {@code Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())}
 *       封装阻塞 SDK 调用，避免阻塞 Reactor 线程</li>
 *   <li><b>错误降级</b>：搜索服务不可用时应返回空结果，记录日志但不中断业务流程</li>
 *   <li><b>租户隔离</b>：所有查询必须强制过滤 {@code tenantId}，防止跨租户数据泄漏</li>
 *   <li><b>数据权限</b>：用户类型（user）需根据 {@code userId} 应用数据权限规则（本部门/全部门等）</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 * @see SearchResultEntity 搜索结果值对象
 * @see com.apex.scaffold.infrastructure.search.MeilisearchAdapter Meilisearch 实现
 */
public interface SearchRepository {

    /**
     * 混合搜索系统管理资源
     *
     * <p>跨资源类型（用户、角色、菜单、部门、字典、配置）统一检索，返回混合分页结果。
     * 结果按相关性评分排序，匹配关键词的片段以 HTML {@code <em>} 标签高亮。
     *
     * <h4>查询逻辑</h4>
     * <ol>
     *   <li>关键词匹配：在 title、subtitle、description 字段中全文检索</li>
     *   <li>资源类型过滤：{@code resourceTypes} 为 null 时搜索所有类型</li>
     *   <li>租户隔离：强制过滤 {@code tenantId}，防止跨租户数据泄漏</li>
     *   <li>数据权限：user 类型根据 {@code userId} 应用数据权限规则</li>
     *   <li>状态过滤：仅返回 {@code status=1}（启用）的资源</li>
     *   <li>相关性排序：按匹配度评分降序，评分相同时按更新时间降序</li>
     * </ol>
     *
     * <h4>高亮规则</h4>
     * <p>匹配关键词在 {@code highlightSnippet} 字段中以 {@code <em>关键词</em>} 形式返回。
     * 前端直接 {@code v-html} 渲染即可高亮显示。示例：
     * <pre>
     * keyword: "系统"
     * highlightSnippet: "负责<em>系统</em>管理和配置的角色"
     * </pre>
     *
     * <h4>分页逻辑</h4>
     * <ul>
     *   <li>{@code pageNum} 从 1 开始，与前端 Element UI Pagination 一致</li>
     *   <li>{@code pageSize} 建议范围：10-50，过大影响前端渲染性能</li>
     *   <li>返回 {@link PageResponse} 包含 {@code total}（总数）、{@code pageNum}、{@code pageSize}、{@code list}（结果列表）</li>
     * </ul>
     *
     * <h4>性能考量</h4>
     * <ul>
     *   <li><b>响应时间目标</b>：P95 < 200ms（单次查询）</li>
     *   <li><b>结果数量限制</b>：单页最多返回 100 条（前端限制，后端兜底）</li>
     *   <li><b>超时处理</b>：搜索引擎查询超时（默认 5 秒）时返回空结果，记录 WARN 日志</li>
     * </ul>
     *
     * <h4>错误处理</h4>
     * <ul>
     *   <li><b>搜索引擎不可用</b>：返回空结果 {@code PageResponse.empty()}，记录 ERROR 日志</li>
     *   <li><b>网络超时</b>：返回空结果，记录 WARN 日志</li>
     *   <li><b>查询语法错误</b>：返回空结果，记录 ERROR 日志（含关键词）</li>
     *   <li><b>参数校验失败</b>：返回空结果或抛出 {@link IllegalArgumentException}</li>
     * </ul>
     *
     * @param keyword 搜索关键词，必填，长度限制 1-100 字符，空白字符会被 trim
     * @param resourceTypes 资源类型过滤集合，可选值：user/role/menu/dept/dict/config；
     *                      {@code null} 或空集合表示搜索所有类型
     * @param tenantId 租户ID，必填，用于租户隔离；系统管理员可传 null 跨租户搜索（谨慎）
     * @param userId 当前用户ID，必填，用于数据权限过滤（仅 user 类型生效）
     * @param pageNum 页码，从 1 开始，必须 ≥ 1，否则返回空结果
     * @param pageSize 每页大小，范围 1-100，超出范围时自动截断
     * @return 混合搜索结果分页对象，包含多种资源类型的结果；
     *         搜索失败或无结果时返回 {@code PageResponse.empty()}（{@code total=0, list=[]}）
     * @throws IllegalArgumentException 当 {@code keyword} 为空、{@code tenantId} 或 {@code userId} 为负数时抛出
     */
    Mono<PageResponse<SearchResultEntity>> search(
            String keyword, Set<String> resourceTypes, Long tenantId, Long userId, int pageNum, int pageSize);
}
