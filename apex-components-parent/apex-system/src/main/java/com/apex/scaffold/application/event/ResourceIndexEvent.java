package com.apex.scaffold.application.event;

import org.springframework.context.ApplicationEvent;

/**
 * 资源索引事件
 *
 * <p>当业务资源发生变更时发布此事件，触发搜索索引更新。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@lombok.Getter
public class ResourceIndexEvent extends ApplicationEvent {

    /**
     * 操作类型
     */
    private final Action action;

    /**
     * 资源类型
     */
    private final String resourceType;

    /**
     * 资源ID
     */
    private final String resourceId;

    /**
     * 构造资源索引事件
     *
     * @param source       事件源
     * @param action       操作类型
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     */
    public ResourceIndexEvent(Object source, Action action, String resourceType, String resourceId) {
        super(source);
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * 操作类型枚举
     */
    public enum Action {
        /**
         * 创建
         */
        CREATE,

        /**
         * 更新
         */
        UPDATE,

        /**
         * 删除
         */
        DELETE
    }

    @Override
    public String toString() {
        return String.format("ResourceIndexEvent{action=%s, type=%s, id=%s}", action, resourceType, resourceId);
    }
}
