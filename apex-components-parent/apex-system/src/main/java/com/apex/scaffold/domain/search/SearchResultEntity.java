package com.apex.scaffold.domain.search;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一搜索结果实体（Domain 层值对象）
 *
 * <p>本类是全文搜索返回的统一结果模型，封装不同资源类型（用户、角色、菜单等）的通用字段。
 * 作为 Domain 层值对象（Value Object），本类是不可变的业务数据载体。
 *
 * <p><strong>设计理念（DDD 值对象特征）</strong></p>
 * <ul>
 *   <li><b>无标识符</b>：值对象无唯一标识符（ID 不用于比较对象相等性），仅基于字段值判等</li>
 *   <li><b>不可变性</b>：使用 Lombok {@code @Data} + {@code @Builder}，通过构建器创建后不可修改</li>
 *   <li><b>技术无关</b>：不依赖任何搜索引擎技术（Meilisearch/Elasticsearch），
 *       仅定义业务语义字段</li>
 *   <li><b>跨层传递</b>：Domain 层 → Application 层 DTO 转换 → Interfaces 层 VO 响应</li>
 * </ul>
 *
 * <p><strong>字段说明</strong></p>
 * <table border="1">
 *   <tr>
 *     <th>字段名</th>
 *     <th>类型</th>
 *     <th>说明</th>
 *     <th>示例</th>
 *   </tr>
 *   <tr>
 *     <td>resourceType</td>
 *     <td>String</td>
 *     <td>资源类型标识</td>
 *     <td>user / role / menu / dept / dict / config</td>
 *   </tr>
 *   <tr>
 *     <td>resourceId</td>
 *     <td>String</td>
 *     <td>资源唯一标识（数据库主键）</td>
 *     <td>"1001"（用户ID） / "2001"（角色ID）</td>
 *   </tr>
 *   <tr>
 *     <td>title</td>
 *     <td>String</td>
 *     <td>主标题（用户名/角色名/菜单名等）</td>
 *     <td>"张三" / "系统管理员" / "用户管理"</td>
 *   </tr>
 *   <tr>
 *     <td>subtitle</td>
 *     <td>String</td>
 *     <td>副标题（昵称/角色代码/菜单路径等）</td>
 *     <td>"研发工程师" / "ADMIN" / "/system/user"</td>
 *   </tr>
 *   <tr>
 *     <td>description</td>
 *     <td>String</td>
 *     <td>描述信息</td>
 *     <td>"负责系统开发和维护" / "具有所有权限"</td>
 *   </tr>
 *   <tr>
 *     <td>highlightSnippet</td>
 *     <td>String</td>
 *     <td>匹配高亮片段（HTML格式）</td>
 *     <td>"负责&lt;em&gt;系统&lt;/em&gt;开发"</td>
 *   </tr>
 *   <tr>
 *     <td>score</td>
 *     <td>Double</td>
 *     <td>相关性评分（0-1之间，越大越相关）</td>
 *     <td>0.95 / 0.78 / 0.42</td>
 *   </tr>
 *   <tr>
 *     <td>status</td>
 *     <td>Integer</td>
 *     <td>资源状态（1启用 0禁用）</td>
 *     <td>1 / 0</td>
 *   </tr>
 *   <tr>
 *     <td>createdAt</td>
 *     <td>LocalDateTime</td>
 *     <td>创建时间</td>
 *     <td>2024-01-01 12:00:00</td>
 *   </tr>
 *   <tr>
 *     <td>updatedAt</td>
 *     <td>LocalDateTime</td>
 *     <td>更新时间</td>
 *     <td>2024-01-15 18:30:00</td>
 *   </tr>
 *   <tr>
 *     <td>extra</td>
 *     <td>Map&lt;String, Object&gt;</td>
 *     <td>扩展字段（JSON格式）</td>
 *     <td>{"email": "zhangsan@example.com", "phone": "138****1234"}</td>
 *   </tr>
 * </table>
 *
 * <p><strong>字段映射规则</strong></p>
 * <p>不同资源类型的字段映射到统一模型：
 *
 * <p><strong>用户（user）</strong></p>
 * <ul>
 *   <li>title: username（用户名）</li>
 *   <li>subtitle: nickname（昵称）</li>
 *   <li>description: dept_name + " / " + post_name（部门/岗位）</li>
 *   <li>extra: {"email": "xxx@example.com", "phone": "138****1234"}</li>
 * </ul>
 *
 * <p><strong>角色（role）</strong></p>
 * <ul>
 *   <li>title: role_name（角色名）</li>
 *   <li>subtitle: role_code（角色代码）</li>
 *   <li>description: description（角色描述）</li>
 *   <li>extra: {"data_scope": "DEPT", "permission_count": 50}</li>
 * </ul>
 *
 * <p><strong>菜单（menu）</strong></p>
 * <ul>
 *   <li>title: menu_name（菜单名）</li>
 *   <li>subtitle: path（菜单路径）</li>
 *   <li>description: component（组件路径）</li>
 *   <li>extra: {"menu_type": "M", "icon": "user", "sort": 1}</li>
 * </ul>
 *
 * <p><strong>部门（dept）</strong></p>
 * <ul>
 *   <li>title: dept_name（部门名）</li>
 *   <li>subtitle: dept_code（部门代码）</li>
 *   <li>description: leader + " / " + phone（负责人/联系电话）</li>
 *   <li>extra: {"parent_name": "研发中心", "sort": 1}</li>
 * </ul>
 *
 * <p><strong>字典（dict）</strong></p>
 * <ul>
 *   <li>title: dict_label（字典标签）</li>
 *   <li>subtitle: dict_value（字典值）</li>
 *   <li>description: dict_type（字典类型）</li>
 *   <li>extra: {"sort": 1, "css_class": "primary"}</li>
 * </ul>
 *
 * <p><strong>配置（config）</strong></p>
 * <ul>
 *   <li>title: config_name（配置名称）</li>
 *   <li>subtitle: config_key（配置键）</li>
 *   <li>description: config_value（配置值，敏感值脱敏）</li>
 *   <li>extra: {"config_type": "Y"}</li>
 * </ul>
 *
 * <p><strong>使用示例</strong></p>
 * <pre>{@code
 * // 在 Infrastructure 层适配器中构建结果
 * SearchResultEntity userResult = SearchResultEntity.builder()
 *     .resourceType("user")
 *     .resourceId("1001")
 *     .title("张三")
 *     .subtitle("研发工程师")
 *     .description("研发部 / Java 开发")
 *     .highlightSnippet("研发部 / Java <em>开发</em>")
 *     .score(0.95)
 *     .status(1)
 *     .createdAt(LocalDateTime.of(2024, 1, 1, 10, 0))
 *     .updatedAt(LocalDateTime.of(2024, 1, 15, 18, 30))
 *     .extra(Map.of("email", "zhangsan@example.com", "phone", "138****1234"))
 *     .build();
 * }</pre>
 *
 * <p><strong>转换到 Application DTO</strong></p>
 * <pre>{@code
 * // Application 层服务转换为 DTO
 * SearchResultDTO dto = SearchResultDTO.builder()
 *     .resourceType(entity.getResourceType())
 *     .resourceId(entity.getResourceId())
 *     .title(entity.getTitle())
 *     .subtitle(entity.getSubtitle())
 *     .description(entity.getDescription())
 *     .highlightSnippet(entity.getHighlightSnippet())
 *     .score(entity.getScore())
 *     .status(entity.getStatus())
 *     .createdAt(entity.getCreatedAt().toString())  // 转为字符串
 *     .updatedAt(entity.getUpdatedAt().toString())
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @since 1.3.0
 * @see SearchRepository 搜索仓储接口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultEntity {

    /**
     * 资源类型标识
     *
     * <p>枚举值：{@code user / role / menu / dept / dict / config}
     *
     * <p>前端根据此字段：
     * <ul>
     *   <li>渲染不同图标（user → 用户图标、role → 角色图标）</li>
     *   <li>构建跳转路径（user → /system/user/detail/:id）</li>
     *   <li>显示类型标签（user → "用户"、role → "角色"）</li>
     * </ul>
     */
    private String resourceType;

    /**
     * 资源唯一标识（数据库主键）
     *
     * <p>用于前端跳转详情页，示例：
     * <pre>{@code
     * // 用户详情页：/system/user/detail/1001
     * router.push(`/system/user/detail/${result.resourceId}`);
     * }</pre>
     *
     * <p><b>类型说明</b>：虽然数据库主键是 {@code Long}，但统一为 {@code String} 便于扩展
     * （未来可能支持 UUID、复合主键等非数字标识符）
     */
    private String resourceId;

    /**
     * 主标题
     *
     * <p>用户：username（用户名）<br>
     * 角色：role_name（角色名）<br>
     * 菜单：menu_name（菜单名）<br>
     * 部门：dept_name（部门名）<br>
     * 字典：dict_label（字典标签）<br>
     * 配置：config_name（配置名称）
     *
     * <p><b>必填字段</b>，前端优先展示此字段，字体加粗
     */
    private String title;

    /**
     * 副标题
     *
     * <p>用户：nickname（昵称）<br>
     * 角色：role_code（角色代码）<br>
     * 菜单：path（菜单路径）<br>
     * 部门：dept_code（部门代码）<br>
     * 字典：dict_value（字典值）<br>
     * 配置：config_key（配置键）
     *
     * <p><b>可选字段</b>，前端以灰色小字显示在主标题下方
     */
    private String subtitle;

    /**
     * 描述信息
     *
     * <p>用户：部门/岗位信息<br>
     * 角色：角色描述<br>
     * 菜单：组件路径<br>
     * 部门：负责人/联系电话<br>
     * 字典：字典类型<br>
     * 配置：配置值（敏感值脱敏）
     *
     * <p><b>可选字段</b>，前端以正常字号显示在副标题下方，可能被截断（超出...）
     */
    private String description;

    /**
     * 匹配高亮片段（HTML 格式）
     *
     * <p>搜索引擎返回的匹配片段，关键词用 {@code <em>} 标签包裹，前端直接 {@code v-html} 渲染即可高亮显示。
     *
     * <p><b>示例</b>：
     * <pre>
     * 关键词: "系统"
     * highlightSnippet: "负责<em>系统</em>管理和配置的角色"
     * </pre>
     *
     * <p><b>安全注意</b>：虽然使用 {@code v-html}，但内容由后端控制，仅 {@code <em>} 标签，
     * 不存在 XSS 风险。搜索引擎（Meilisearch）已对用户输入进行转义。
     *
     * <p><b>可选字段</b>，若为 null，前端使用 {@code description} 替代
     */
    private String highlightSnippet;

    /**
     * 相关性评分
     *
     * <p>搜索引擎计算的匹配度评分，范围 0-1（或其他范围，取决于搜索引擎实现）。
     * 分数越高，匹配度越高，结果排序时优先级越高。
     *
     * <p><b>排序规则</b>：
     * <ol>
     *   <li>优先按 {@code score} 降序（相关性优先）</li>
     *   <li>{@code score} 相同时按 {@code updatedAt} 降序（最新优先）</li>
     * </ol>
     *
     * <p><b>可选字段</b>，若为 null，默认按更新时间排序
     */
    private Double score;

    /**
     * 资源状态
     *
     * <p>枚举值：{@code 1}（启用）、{@code 0}（禁用）
     *
     * <p><b>搜索过滤</b>：仅返回 {@code status=1} 的资源，禁用资源不应出现在搜索结果中。
     *
     * <p><b>必填字段</b>，前端根据此字段：
     * <ul>
     *   <li>status=1：正常显示</li>
     *   <li>status=0：置灰显示（理论上不应出现，兜底逻辑）</li>
     * </ul>
     */
    private Integer status;

    /**
     * 创建时间
     *
     * <p>资源首次创建的时间戳，用于前端显示"创建于"信息。
     *
     * <p><b>可选字段</b>，若为 null，前端不显示创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     *
     * <p>资源最后修改的时间戳，用于：
     * <ul>
     *   <li>前端显示"更新于"信息</li>
     *   <li>排序时的次要排序键（相关性相同时按更新时间降序）</li>
     * </ul>
     *
     * <p><b>必填字段</b>，所有资源表应有 {@code updated_at} 字段
     */
    private LocalDateTime updatedAt;

    /**
     * 扩展字段（JSON 格式）
     *
     * <p>存储资源特有的额外字段，不同资源类型包含不同的扩展信息：
     *
     * <p><strong>用户（user）</strong></p>
     * <pre>{@code
     * {
     *   "email": "zhangsan@example.com",  // 邮箱（脱敏）
     *   "phone": "138****1234",            // 手机号（脱敏）
     *   "dept_id": "10",                   // 部门ID
     *   "post": "Java开发工程师"            // 岗位
     * }
     * }</pre>
     *
     * <p><strong>角色（role）</strong></p>
     * <pre>{@code
     * {
     *   "data_scope": "DEPT",          // 数据权限范围
     *   "permission_count": 50,        // 权限数量
     *   "menu_check_strictly": true    // 菜单树选择父子联动
     * }
     * }</pre>
     *
     * <p><strong>菜单（menu）</strong></p>
     * <pre>{@code
     * {
     *   "menu_type": "M",    // 菜单类型：M目录 C菜单 F按钮
     *   "icon": "user",      // 图标名
     *   "sort": 1,           // 排序号
     *   "parent_name": "系统管理"  // 父菜单名
     * }
     * }</pre>
     *
     * <p><strong>部门（dept）</strong></p>
     * <pre>{@code
     * {
     *   "parent_name": "研发中心",  // 父部门名
     *   "sort": 1,                // 排序号
     *   "ancestors": "0,100"      // 祖级列表
     * }
     * }</pre>
     *
     * <p><strong>字典（dict）</strong></p>
     * <pre>{@code
     * {
     *   "sort": 1,               // 排序号
     *   "css_class": "primary",  // CSS类名
     *   "list_class": "default"  // 列表回显样式
     * }
     * }</pre>
     *
     * <p><strong>配置（config）</strong></p>
     * <pre>{@code
     * {
     *   "config_type": "Y",  // 配置类型：Y系统内置 N用户自定义
     *   "remark": "备注"
     * }
     * }</pre>
     *
     * <p><b>可选字段</b>，若为 null 或空 Map，前端忽略
     *
     * <p><b>注意</b>：扩展字段仅用于前端显示辅助信息，不应包含敏感数据（密码、密钥等）
     */
    private Map<String, Object> extra;
}
