package com.apex.scaffold.office.off.application.service;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.off.application.assembler.DepartmentAssembler;
import com.apex.scaffold.office.off.application.assembler.EmployeeAssembler;
import com.apex.scaffold.office.off.application.assembler.SyncHistoryAssembler;
import com.apex.scaffold.office.off.application.dto.DepartmentDTO;
import com.apex.scaffold.office.off.application.dto.EmployeeDTO;
import com.apex.scaffold.office.off.application.dto.SyncHistoryDTO;
import com.apex.scaffold.office.off.domain.entity.OffSyncHistoryEntity;
import com.apex.scaffold.office.off.domain.repository.OffDepartmentRepository;
import com.apex.scaffold.office.off.domain.repository.OffEmployeeRepository;
import com.apex.scaffold.office.off.domain.repository.OffSyncHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 组织同步应用服务
 *
 * <p>提供部门和员工信息的同步管理功能，支持从飞书、钉钉、企业微信等外部系统同步组织架构数据。
 * 支持部门树形结构管理、员工信息管理、同步历史记录查询等核心业务场景。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>部门管理：创建、更新、删除部门，查询部门详情和列表</li>
 *   <li>部门树：查询根部门列表，按父级ID查询子部门</li>
 *   <li>部门分页：支持按状态分页查询部门列表</li>
 *   <li>员工管理：创建、更新、删除员工，查询员工详情和列表</li>
 *   <li>员工分页：支持按部门、状态分页查询员工列表</li>
 *   <li>同步历史：记录和查询每次同步操作的结果和详情</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>全量同步：从外部系统拉取全部组织架构数据，覆盖本地数据</li>
 *   <li>增量同步：仅同步变更的部门和员工信息，提高同步效率</li>
 *   <li>双向同步：支持本地修改推送到外部系统（待实现）</li>
 *   <li>冲突处理：按时间戳或优先级规则处理数据冲突</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>部门：支持树形层级结构，包含部门编码、名称、路径、层级、负责人等</li>
 *   <li>员工：包含员工编码、姓名、部门、职位、联系方式、在职状态等</li>
 *   <li>同步历史：记录同步类型、来源系统、同步结果、变更统计等</li>
 *   <li>外部ID映射：保留外部系统ID，支持双向同步和数据追溯</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>使用 Reactor 响应式编程模型，返回 Mono/Flux</li>
 *   <li>通过 Assembler 实现 DTO 与 Entity 之间的双向转换</li>
 *   <li>支持分页查询，返回 PageResponse 统一分页对象</li>
 *   <li>部门和员工数据独立存储，通过 deptId 关联</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffDepartmentRepository
 * @see OffEmployeeRepository
 * @see OffSyncHistoryRepository
 * @see DepartmentAssembler
 * @see EmployeeAssembler
 * @see SyncHistoryAssembler
 */
@Service
@RequiredArgsConstructor
public class OrgSyncService {

    private final OffDepartmentRepository departmentRepository;
    private final OffEmployeeRepository employeeRepository;
    private final OffSyncHistoryRepository syncHistoryRepository;
    private final DepartmentAssembler departmentAssembler;
    private final EmployeeAssembler employeeAssembler;
    private final SyncHistoryAssembler syncHistoryAssembler;

    /**
     * 创建部门
     *
     * @param dto 部门 DTO（含部门编码、名称、父级、层级、负责人等）
     * @return 创建后的部门 DTO
     */
    public Mono<DepartmentDTO> createDepartment(DepartmentDTO dto) {
        return Mono.fromCallable(() -> departmentAssembler.toEntity(dto))
                .flatMap(departmentRepository::insert)
                .map(count -> departmentAssembler.toDto(departmentAssembler.toEntity(dto)));
    }

    /**
     * 更新部门信息
     *
     * <p>更新部门编码、名称、父级、路径、层级、排序、负责人与联系方式等字段。
     *
     * @param id 部门 ID
     * @param dto 部门 DTO（含待更新的字段值）
     * @return 更新后的部门 DTO；部门不存在时 Mono 为空
     */
    public Mono<DepartmentDTO> updateDepartment(Long id, DepartmentDTO dto) {
        return departmentRepository.findById(id).flatMap(entity -> {
            entity.setDeptCode(dto.getDeptCode());
            entity.setDeptName(dto.getDeptName());
            entity.setParentId(dto.getParentId());
            entity.setDeptPath(dto.getDeptPath());
            entity.setDeptLevel(dto.getDeptLevel());
            entity.setSortOrder(dto.getSortOrder());
            entity.setLeaderId(dto.getLeaderId());
            entity.setLeaderName(dto.getLeaderName());
            entity.setPhone(dto.getPhone());
            entity.setEmail(dto.getEmail());
            entity.setStatus(dto.getStatus());
            return departmentRepository.update(entity).thenReturn(departmentAssembler.toDto(entity));
        });
    }

    /**
     * 删除部门
     *
     * @param id 部门 ID
     */
    public Mono<Void> deleteDepartment(Long id) {
        return departmentRepository.deleteById(id);
    }

    /**
     * 按 ID 查询部门详情
     *
     * @param id 部门 ID
     * @return 部门 DTO；不存在时 Mono 为空
     */
    public Mono<DepartmentDTO> getDepartmentById(Long id) {
        return departmentRepository.findById(id).map(departmentAssembler::toDto);
    }

    /**
     * 按父级 ID 查询子部门列表
     *
     * @param parentId 父部门 ID
     * @return 该父部门下的子部门 DTO 流
     */
    public Flux<DepartmentDTO> getDepartmentsByParentId(Long parentId) {
        return departmentRepository.findByParentId(parentId).map(departmentAssembler::toDto);
    }

    /**
     * 查询根部门列表
     *
     * @return 根部门 DTO 流
     */
    public Flux<DepartmentDTO> getRootDepartments() {
        return departmentRepository.findRootDepartments().map(departmentAssembler::toDto);
    }

    /**
     * 分页查询部门列表
     *
     * <p>支持按部门状态过滤。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param status 部门状态，可为 null 表示不过滤
     * @return 分页结果（含总条数与当前页部门列表）
     */
    public Mono<PageResponse<DepartmentDTO>> getDepartmentPage(Integer pageNum, Integer pageSize, String status) {
        return departmentRepository.findPage(pageNum, pageSize, status).map(page -> {
            PageResponse<DepartmentDTO> result = new PageResponse<>();
            result.setTotal(page.getTotal());
            result.setRecords(
                    page.getRecords().stream().map(departmentAssembler::toDto).toList());
            return result;
        });
    }

    /**
     * 创建员工
     *
     * @param dto 员工 DTO（含员工编码、姓名、部门、职位、联系方式等）
     * @return 创建后的员工 DTO
     */
    public Mono<EmployeeDTO> createEmployee(EmployeeDTO dto) {
        return Mono.fromCallable(() -> employeeAssembler.toEntity(dto))
                .flatMap(employeeRepository::insert)
                .map(count -> employeeAssembler.toDto(employeeAssembler.toEntity(dto)));
    }

    /**
     * 更新员工信息
     *
     * <p>更新员工编码、姓名、部门、职位、联系方式、在职状态与关联用户等字段。
     *
     * @param id 员工 ID
     * @param dto 员工 DTO（含待更新的字段值）
     * @return 更新后的员工 DTO；员工不存在时 Mono 为空
     */
    public Mono<EmployeeDTO> updateEmployee(Long id, EmployeeDTO dto) {
        return employeeRepository.findById(id).flatMap(entity -> {
            entity.setEmployeeCode(dto.getEmployeeCode());
            entity.setEmployeeName(dto.getEmployeeName());
            entity.setDeptId(dto.getDeptId());
            entity.setDeptName(dto.getDeptName());
            entity.setPosition(dto.getPosition());
            entity.setJobTitle(dto.getJobTitle());
            entity.setMobile(dto.getMobile());
            entity.setEmail(dto.getEmail());
            entity.setGender(dto.getGender());
            entity.setBirthday(dto.getBirthday());
            entity.setHireDate(dto.getHireDate());
            entity.setStatus(dto.getStatus());
            entity.setUserId(dto.getUserId());
            return employeeRepository.update(entity).thenReturn(employeeAssembler.toDto(entity));
        });
    }

    /**
     * 删除员工
     *
     * @param id 员工 ID
     */
    public Mono<Void> deleteEmployee(Long id) {
        return employeeRepository.deleteById(id);
    }

    /**
     * 按 ID 查询员工详情
     *
     * @param id 员工 ID
     * @return 员工 DTO；不存在时 Mono 为空
     */
    public Mono<EmployeeDTO> getEmployeeById(Long id) {
        return employeeRepository.findById(id).map(employeeAssembler::toDto);
    }

    /**
     * 按部门查询员工列表
     *
     * @param deptId 部门 ID
     * @return 该部门下的员工 DTO 流
     */
    public Flux<EmployeeDTO> getEmployeesByDeptId(Long deptId) {
        return employeeRepository.findByDeptId(deptId).map(employeeAssembler::toDto);
    }

    /**
     * 分页查询员工列表
     *
     * <p>支持按所属部门与在职状态过滤。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param deptId 所属部门 ID，可为 null 表示不过滤
     * @param status 员工状态，可为 null 表示不过滤
     * @return 分页结果（含总条数与当前页员工列表）
     */
    public Mono<PageResponse<EmployeeDTO>> getEmployeePage(
            Integer pageNum, Integer pageSize, Long deptId, String status) {
        return employeeRepository.findPage(pageNum, pageSize, deptId, status).map(page -> {
            PageResponse<EmployeeDTO> result = new PageResponse<>();
            result.setTotal(page.getTotal());
            result.setRecords(
                    page.getRecords().stream().map(employeeAssembler::toDto).toList());
            return result;
        });
    }

    /**
     * 创建同步历史记录
     *
     * @param dto 同步历史 DTO（含同步类型、来源系统、状态、起止时间、变更统计等）
     * @return 创建后的同步历史 DTO（回填生成的记录 ID）
     */
    public Mono<SyncHistoryDTO> createSyncHistory(SyncHistoryDTO dto) {
        OffSyncHistoryEntity entity = syncHistoryAssembler.toEntity(dto);
        return syncHistoryRepository.insert(entity).map(count -> {
            dto.setId(entity.getId());
            return dto;
        });
    }

    /**
     * 更新同步历史记录
     *
     * <p>更新同步类型、来源系统、状态、起止时间、耗时、部门与员工变更统计及错误信息。
     *
     * @param id 同步历史 ID
     * @param dto 同步历史 DTO（含待更新的字段值）
     * @return 更新后的同步历史 DTO；记录不存在时 Mono 为空
     */
    public Mono<SyncHistoryDTO> updateSyncHistory(Long id, SyncHistoryDTO dto) {
        return syncHistoryRepository.findById(id).flatMap(entity -> {
            entity.setSyncType(dto.getSyncType());
            entity.setSyncSource(dto.getSyncSource());
            entity.setSyncStatus(dto.getSyncStatus());
            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setDuration(dto.getDuration());
            entity.setDeptCount(dto.getDeptCount());
            entity.setEmployeeCount(dto.getEmployeeCount());
            entity.setErrorMessage(dto.getErrorMessage());
            return syncHistoryRepository.update(entity).thenReturn(syncHistoryAssembler.toDto(entity));
        });
    }

    /**
     * 按来源系统查询同步历史记录
     *
     * @param syncSource 同步来源系统（如飞书、钉钉、企业微信）
     * @return 该来源系统的同步历史 DTO 流
     */
    public Flux<SyncHistoryDTO> getSyncHistoryBySource(String syncSource) {
        return syncHistoryRepository.findBySyncSource(syncSource).map(syncHistoryAssembler::toDto);
    }

    /**
     * 查询指定来源系统最近一次同步历史
     *
     * @param syncSource 同步来源系统（如飞书、钉钉、企业微信）
     * @return 最近一次同步历史 DTO；无记录时 Mono 为空
     */
    public Mono<SyncHistoryDTO> getLatestSyncHistory(String syncSource) {
        return syncHistoryRepository.findLatestBySource(syncSource).map(syncHistoryAssembler::toDto);
    }

    /**
     * 分页查询同步历史记录
     *
     * <p>支持按同步来源系统与同步状态过滤。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param syncSource 同步来源系统，可为 null 表示不过滤
     * @param syncStatus 同步状态，可为 null 表示不过滤
     * @return 分页结果（含总条数与当前页同步历史列表）
     */
    public Mono<PageResponse<SyncHistoryDTO>> getSyncHistoryPage(
            Integer pageNum, Integer pageSize, String syncSource, String syncStatus) {
        return syncHistoryRepository
                .findPage(pageNum, pageSize, syncSource, syncStatus)
                .map(page -> {
                    PageResponse<SyncHistoryDTO> result = new PageResponse<>();
                    result.setTotal(page.getTotal());
                    result.setRecords(page.getRecords().stream()
                            .map(syncHistoryAssembler::toDto)
                            .toList());
                    return result;
                });
    }
}
