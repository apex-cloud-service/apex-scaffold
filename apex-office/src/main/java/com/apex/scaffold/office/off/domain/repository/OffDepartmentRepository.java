package com.apex.scaffold.office.off.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.off.domain.entity.OffDepartmentEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公部门仓储接口
 *
 * <p>定义办公平台部门信息的持久化操作契约，支持从外部办公平台（飞书、钉钉、企业微信）
 * 同步的部门数据管理。提供部门的增删改查、树形结构查询、外部ID映射、批量操作等功能。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>部门的基础 CRUD 操作</li>
 *   <li>部门树形结构查询（根节点、子部门）</li>
 *   <li>外部平台部门ID映射和同步</li>
 *   <li>批量插入部门数据（组织同步）</li>
 *   <li>分页查询和条件过滤</li>
 *   <li>部门编码唯一性管理</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>组织架构展示：按层级查询部门树</li>
 *   <li>部门选择器：查询所有可用部门</li>
 *   <li>同步映射：通过外部ID查找本地部门</li>
 *   <li>部门管理：分页查询、状态筛选</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>树形结构：支持父子关系和层级查询</li>
 *   <li>外部映射：syncSource + externalId 唯一标识外部部门</li>
 *   <li>批量操作：支持批量插入提高同步性能</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffDepartmentEntity
 */
public interface OffDepartmentRepository {

    /**
     * 根据部门ID查询部门信息
     *
     * @param id 部门ID
     * @return 部门实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffDepartmentEntity> findById(Long id);

    /**
     * 根据部门编码查询部门信息
     *
     * @param deptCode 部门编码，唯一标识
     * @return 部门实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffDepartmentEntity> findByDeptCode(String deptCode);

    /**
     * 查询指定父部门下的所有子部门
     *
     * @param parentId 父部门ID
     * @return 子部门列表的Flux流
     */
    Flux<OffDepartmentEntity> findByParentId(Long parentId);

    /**
     * 查询所有根部门（当前实现仅返回parentId为null的部门，不包含parentId为0的部门）
     *
     * @return parentId为null的根部门列表的Flux流
     */
    Flux<OffDepartmentEntity> findRootDepartments();

    /**
     * 根据外部系统标识查询部门
     *
     * @param syncSource 同步来源（如feishu、dingtalk、wechat）
     * @param externalId 外部系统中的部门ID
     * @return 部门实体列表的Flux流
     */
    Flux<OffDepartmentEntity> findByExternalId(String syncSource, String externalId);

    /**
     * 按同步来源查询全部部门，用于组织同步后统一重算层级。
     *
     * @param syncSource 同步来源（如 FEISHU、DINGTALK、WECHAT）
     * @return 该来源下全部部门实体的Flux流
     */
    Flux<OffDepartmentEntity> findBySyncSource(String syncSource);

    /**
     * 插入新部门记录
     *
     * @param entity 部门实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OffDepartmentEntity entity);

    /**
     * 批量插入部门记录，用于组织架构同步
     *
     * @param entities 部门实体列表
     * @return 受影响的总行数
     */
    Mono<Integer> batchInsert(java.util.List<OffDepartmentEntity> entities);

    /**
     * 更新部门信息
     *
     * @param entity 部门实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OffDepartmentEntity entity);

    /**
     * 根据ID删除部门（逻辑删除）
     *
     * @param id 部门ID
     */
    Mono<Void> deleteById(Long id);

    /**
     * 分页查询部门列表
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @param status 部门状态，可为null表示查询所有状态
     * @return 分页结果
     */
    Mono<PageResponse<OffDepartmentEntity>> findPage(Integer pageNum, Integer pageSize, String status);

    /**
     * 查询所有部门
     *
     * @return 所有部门的Flux流
     */
    Flux<OffDepartmentEntity> findAll();
}
