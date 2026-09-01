package com.apex.scaffold.common.context;

import reactor.core.publisher.Mono;

/**
 * 响应式数据权限解析入口。
 *
 * <p>实现方需从当前安全上下文、请求头或调用方显式参数中解析出用户可访问的
 * 数据范围（如全部、本部门、本部门及下级、本人、自定义部门集合），返回
 * {@link DataScopeInfo} 供 MyBatis 拦截器拼接 SQL。</p>
 *
 * @author apex-scaffold
 */
@FunctionalInterface
public interface ReactiveDataScopeProvider {

    /**
     * 解析当前调用链上的数据权限。
     *
     * @return 异步计算得到的数据范围描述；空流或发出错误表示 fail-closed，
     *         调用方应视为“无可见数据”并抛错或返回空结果。
     */
    Mono<DataScopeInfo> resolve();
}
