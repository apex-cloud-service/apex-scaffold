package com.apex.scaffold.office.domain;

import lombok.Builder;
import lombok.Value;

/**
 * 办公平台组织同步任务聚合根
 *
 * <p>记录办公平台组织架构和成员的同步任务状态，支持增量同步和断点续传。
 *
 * <p>同步流程：
 * <ol>
 *   <li>创建同步任务，state 初始为 PENDING</li>
 *   <li>调用平台 API 获取组织数据，更新 cursor（用于分页或增量拉取）</li>
 *   <li>同步成功，state 更新为 SUCCESS</li>
 *   <li>同步失败，state 更新为 FAILED，记录 failureMessage</li>
 * </ol>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Value
@Builder
public class OfficeSyncTask {
    /** 主键 ID */
    Long id;

    /** 租户标识 */
    String tenantId;

    /** 平台类型 */
    OfficePlatform platform;

    /** 同步类型（DEPARTMENT/MEMBER），与租户、平台联合隔离任务与游标 */
    String syncType;

    /** 同步游标（用于增量同步和分页） */
    String cursor;

    /** 同步状态（PENDING/RUNNING/SUCCESS/FAILED） */
    String state;

    /** 失败原因（仅 FAILED 状态时记录） */
    String failureMessage;

    /** 乐观锁版本号 */
    Integer version;

    /** 逻辑删除标记（0-未删除，1-已删除） */
    Integer deleted;
}
