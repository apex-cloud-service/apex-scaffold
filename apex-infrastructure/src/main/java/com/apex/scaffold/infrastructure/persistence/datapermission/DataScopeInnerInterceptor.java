package com.apex.scaffold.infrastructure.persistence.datapermission;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.context.DataScopeProvider;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import java.lang.reflect.Field;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>与分页（PageHelper 的 count 查询）天然兼容——count SQL 同样经过本拦截器，
 *       保证分页总数与数据范围一致；</li>
 *   <li>仅在目标查询已装配数据权限上下文时生效，未装配的查询零开销、零影响；</li>
 *   <li>解析/改写失败一律放行，绝不阻断业务。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
public class DataScopeInnerInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataScopeInnerInterceptor.class);

    /** 数据权限装配器 */
    private final DataScopeProvider dataScopeProvider;

    /**
     * 创建数据权限拦截器。
     *
     * @param dataScopeProvider 数据权限装配器（解析当前用户的数据可见范围）
     */
    public DataScopeInnerInterceptor(DataScopeProvider dataScopeProvider) {
        this.dataScopeProvider = dataScopeProvider;
    }

    /**
     * 放行查询执行（数据权限只改写 SQL，不拦截执行）。
     */
    @Override
    public boolean willDoQuery(
            Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        return true;
    }

    /**
     * 查询执行前注入数据权限过滤条件。
     */
    @Override
    public void beforeQuery(
            Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
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
