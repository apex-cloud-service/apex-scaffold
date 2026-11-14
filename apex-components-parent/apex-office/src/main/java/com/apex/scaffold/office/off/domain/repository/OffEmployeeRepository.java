package com.apex.scaffold.office.off.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.off.domain.entity.OffEmployeeEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公员工仓储接口
 *
 * <p>定义办公平台员工信息的持久化操作契约，支持从外部办公平台（飞书、钉钉、企业微信）
 * 同步的员工数据管理。提供员工的增删改查、部门关联查询、用户映射、批量操作等功能。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>员工的基础 CRUD 操作</li>
 *   <li>按部门查询员工列表</li>
 *   <li>员工与系统用户的关联映射</li>
 *   <li>外部平台员工ID映射和同步</li>
 *   <li>批量插入员工数据（组织同步）</li>
 *   <li>分页查询和条件过滤</li>
 *   <li>员工编码唯一性管理</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>组织架构：查询部门下的所有员工</li>
 *   <li>用户绑定：通过 userId 查找对应的员工信息</li>
 *   <li>同步映射：通过外部ID查找本地员工</li>
 *   <li>员工管理：分页查询、状态筛选、部门筛选</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>部门关联：支持按部门查询员工</li>
 *   <li>用户映射：员工与系统用户的双向关联</li>
 *   <li>外部映射：syncSource + externalId 唯一标识外部员工</li>
 *   <li>批量操作：支持批量插入提高同步性能</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffEmployeeEntity
 */
public interface OffEmployeeRepository {

    /**
     * 根据员工ID查询员工信息
     *
     * @param id 员工ID
     * @return 员工实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffEmployeeEntity> findById(Long id);

    /**
     * 根据员工编码查询员工信息
     *
     * @param employeeCode 员工编码，唯一标识
     * @return 员工实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffEmployeeEntity> findByEmployeeCode(String employeeCode);

    /**
     * 根据系统用户ID查询关联的员工信息
     *
     * @param userId 系统用户ID
     * @return 员工实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffEmployeeEntity> findByUserId(Long userId);

    /**
     * 查询指定部门下的所有员工
     *
     * @param deptId 部门ID
     * @return 员工列表的Flux流
     */
    Flux<OffEmployeeEntity> findByDeptId(Long deptId);

    /**
     * 根据外部系统标识查询员工
     *
     * @param syncSource 同步来源（如feishu、dingtalk、wechat）
     * @param externalId 外部系统中的员工ID
     * @return 员工实体列表的Flux流
     */
    Flux<OffEmployeeEntity> findByExternalId(String syncSource, String externalId);

    /**
     * 插入新员工记录
     *
     * @param entity 员工实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OffEmployeeEntity entity);

    /**
     * 批量插入员工记录，用于组织架构同步
     *
     * @param entities 员工实体列表
     * @return 受影响的总行数
     */
    Mono<Integer> batchInsert(java.util.List<OffEmployeeEntity> entities);

    /**
     * 更新员工信息
     *
     * @param entity 员工实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OffEmployeeEntity entity);

    /**
     * 根据ID删除员工（逻辑删除）
     *
     * @param id 员工ID
     */
    Mono<Void> deleteById(Long id);

    /**
     * 分页查询员工列表
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @param deptId 部门ID，可为null表示查询所有部门
     * @param status 员工状态，可为null表示查询所有状态
     * @return 分页结果
     */
    Mono<PageResponse<OffEmployeeEntity>> findPage(Integer pageNum, Integer pageSize, Long deptId, String status);

    /**
     * 查询所有员工
     *
     * @return 所有员工的Flux流
     */
    Flux<OffEmployeeEntity> findAll();
}
