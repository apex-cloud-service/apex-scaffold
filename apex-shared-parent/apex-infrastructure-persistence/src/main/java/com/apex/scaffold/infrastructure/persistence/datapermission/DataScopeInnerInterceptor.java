package com.apex.scaffold.infrastructure.persistence.datapermission;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.context.DataScopeProvider;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import java.lang.reflect.Field;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * MyBatis-Plus 数据权限拦截器（自定义数据权限）。
 *
 * <p>实现 {@link InnerInterceptor#beforeQuery}，在每条 SELECT 执行前：</p>
 *
 * <ol>
 *   <li>通过 {@link DataScopeProvider} 解析当前用户的数据权限范围（{@link DataScopeInfo}），
 *       未装配或为"全部数据权限"时放行；</li>
 *   <li>读取 {@link DataScopeContext} 中的注入配置（{@link DataScopeConfig}，
 *       由 {@code DataScopeAspect} 从 {@code @DataScope} 注解装配），缺省用默认配置；</li>
 *   <li>由 {@link DataScopeSqlRewriter} 将过滤条件注入 SQL，并通过反射改写
 *       {@link BoundSql#getSql()}（MyBatis 的 {@code BoundSql} 未提供 sql setter）。</li>
 * </ol>
 *
 * <p><b>特性</b>：</p>
 * <ul>
 *   <li>支持全部/本部门/本部门及以下/仅本人/自定义五种数据权限类型；</li>
 *   <li>分页数据 SQL 与经过本拦截器的 count SQL 均按改写器支持的结构处理；
 *       不支持的结构（如 FROM 包含无法识别的子查询）会阻断查询；</li>
 *   <li>装配器未解析到权限范围或范围为全部数据时保留原 SQL；</li>
 *   <li>需要过滤时采用 fail-closed 策略：权限关键值缺失、解析或改写失败抛出
 *       {@link IllegalStateException}，反射写入失败抛出 {@link MybatisPlusException}，阻止查询。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
@Slf4j
@lombok.RequiredArgsConstructor
public class DataScopeInnerInterceptor implements InnerInterceptor {

    /** 数据权限装配器 */
    private final DataScopeProvider dataScopeProvider;

    /**
     * 允许进入查询前处理，数据权限校验与 SQL 改写由 {@link #beforeQuery} 执行。
     *
     * @param executor MyBatis 执行器
     * @param ms 映射语句
     * @param parameter 查询参数
     * @param rowBounds 分页边界
     * @param resultHandler 结果处理器
     * @param boundSql 绑定 SQL
     * @return 始终为 {@code true}，后续查询前处理仍可能抛异常阻断查询
     */
    @Override
    public boolean willDoQuery(
            Executor executor,
            MappedStatement ms,
            Object parameter,
            RowBounds rowBounds,
            ResultHandler resultHandler,
            BoundSql boundSql) {
        return true;
    }

    /**
     * 查询执行前注入数据权限过滤条件，无权限范围或全部数据权限时保留原 SQL。
     *
     * @param executor MyBatis 执行器
     * @param ms 映射语句
     * @param parameter 查询参数
     * @param rowBounds 分页边界
     * @param resultHandler 结果处理器
     * @param boundSql 待改写的绑定 SQL
     * @throws IllegalStateException 需要过滤但权限关键值缺失，或 SQL 无法解析、校验或改写时抛出
     * @throws MybatisPlusException 反射写入 SQL 失败时抛出
     */
    @Override
    public void beforeQuery(
            Executor executor,
            MappedStatement ms,
            Object parameter,
            RowBounds rowBounds,
            ResultHandler resultHandler,
            BoundSql boundSql) {
        DataScopeInfo scope = dataScopeProvider.resolve().orElse(null);
        if (scope == null || scope.isAll()) {
            return;
        }
        DataScopeConfig config = DataScopeContext.currentConfig().orElseGet(DataScopeConfig::defaults);
        String sql = boundSql.getSql();
        String rewritten = DataScopeSqlRewriter.rewrite(sql, config, scope);
        if (!rewritten.equals(sql)) {
            setSql(boundSql, rewritten);
            log.debug("数据权限注入: {} | {}", ms.getId(), rewritten);
        }
    }

    /**
     * 反射改写 {@link BoundSql} 的 {@code sql} 字段。
     *
     * <p>{@code BoundSql.sql} 是 {@code private final} 字段且未提供 setter，
     * 这是 MyBatis 插件改写 SQL 的标准做法（MyBatis-Plus 3.5.9 前的官方拦截器、
     * PageHelper 均如此）。改写发生在 {@code Executor.query} 读取 SQL 之前，线程安全。</p>
     *
     * @param boundSql 绑定 SQL
     * @param sql      注入后的 SQL
     * @throws MybatisPlusException 访问或写入 SQL 字段发生反射异常时抛出
     */
    private static void setSql(BoundSql boundSql, String sql) {
        try {
            Field field = BoundSql.class.getDeclaredField("sql");
            field.setAccessible(true);
            field.set(boundSql, sql);
        } catch (ReflectiveOperationException e) {
            throw new MybatisPlusException("数据权限: 改写 BoundSql.sql 失败", e);
        }
    }
}
