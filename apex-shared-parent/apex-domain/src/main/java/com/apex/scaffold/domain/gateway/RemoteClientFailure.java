package com.apex.scaffold.domain.gateway;

/**
 * 远程适配器向应用服务暴露的失败契约。
 *
 * <p>将该分类保留在领域边界，可避免应用代码为了区分“确定性的客户端输入错误”
 * 与“模糊的网络故障”而引入基础设施异常。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface RemoteClientFailure {

    /**
     * 远端是否以确定性的客户端输入错误拒绝了请求。
     *
     * @return 4xx 类输入拒绝返回 {@code true}，可据此判定重试无意义
     */
    boolean isClientError();
}
