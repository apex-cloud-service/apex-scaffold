package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.DeptAssembler;
import com.apex.scaffold.application.dto.admin.DeptDTO;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.domain.repository.DeptRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 部门管理服务。
 *
 * <p>提供部门树查询与 CRUD（C3 数据权限/部门体系），树形结构按
 * {@code parentId} 全量拉取后内存组装，避免 N+1 查询。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see DeptRepository
 */
@Service
/**n * DeptServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DeptService {

    /** 部门仓储 */
    private final DeptRepository deptRepository;
    /** 部门对象转换器 */
    private final DeptAssembler deptAssembler;

    /**
     * 构造部门管理服务。
     *
     * @param deptRepository 部门仓储
     * @param deptAssembler 部门对象转换器
     */
    public DeptService(DeptRepository deptRepository, DeptAssembler deptAssembler) {
        this.deptRepository = deptRepository;
        this.deptAssembler = deptAssembler;
    }

    /**
     * 查询部门树（全部，按父级组装）。
     *
     * @return 部门树根节点列表
     */
    public Mono<List<DeptDTO>> listTree() {
        return deptRepository.findAll().map(this::buildTree);
    }

    /**
     * 查询部门详情。
     *
     * @param id 部门 ID
     * @return 部门 DTO
     * @throws BusinessException 部门不存在时抛出
     */
    public Mono<DeptDTO> get(Long id) {
        return deptRepository
                .findById(id)
                .map(deptAssembler::toDTO)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND)));
    }

    /**
     * 新增部门。
     *
     * <p>自动计算 {@code ancestors}（父部门祖级 + 父 ID）。</p>
     *
     * @param dept 部门实体（id 为空）
     * @return 保存后的部门 DTO
     */
    public Mono<DeptDTO> create(DeptEntity dept) {
        dept.setId(null);
        return resolveAncestors(dept).flatMap(d -> deptRepository.save(d)).map(deptAssembler::toDTO);
    }

    /**
     * 更新部门。
     *
     * @param id   部门 ID
     * @param dept 部门实体（含待更新字段）
     * @return 更新后的部门 DTO
     */
    public Mono<DeptDTO> update(Long id, DeptEntity dept) {
        if (dept.getLockVersion() == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "部门版本不能为空"));
        }
        dept.setId(id);
        return deptRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND)))
                .then(resolveAncestors(dept))
                .flatMap(d -> deptRepository.save(d))
                .map(deptAssembler::toDTO);
    }

    /**
     * 删除部门（逻辑删除）。
     *
     * @param id 部门 ID
     * @return 是否删除成功
     */
    public Mono<Boolean> delete(Long id) {
        return deptRepository.deleteById(id);
    }

    /**
     * 计算祖级链并回填实体。
     *
     * <p>祖级 = 父部门祖级 + 父 ID（父为根则仅 "0"）。</p>
     *
     * @param dept 部门实体
     * @return 回填后的部门实体
     */
    private Mono<DeptEntity> resolveAncestors(DeptEntity dept) {
        Long parentId = dept.getParentId() == null ? 0L : dept.getParentId();
        dept.setParentId(parentId);
        if (parentId == 0L) {
            dept.setAncestors("0");
            return Mono.just(dept);
        }
        return deptRepository
                .findById(parentId)
                .map(parent -> {
                    dept.setAncestors(parent.getAncestors() + "," + parentId);
                    return dept;
                })
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "父部门不存在")));
    }

    /**
     * 树形组装（内存构建）。
     *
     * @param all 全量部门实体
     * @return 部门树根节点列表
     */
    private List<DeptDTO> buildTree(List<DeptEntity> all) {
        List<DeptDTO> nodes = deptAssembler.toDTOList(all);
        Map<Long, DeptDTO> byId = nodes.stream().collect(Collectors.toMap(DeptDTO::getId, Function.identity()));

        return nodes.stream()
                .filter(node -> {
                    Long parentId = node.getParentId() == null ? 0L : node.getParentId();
                    if (parentId == 0L) {
                        return true;
                    }
                    DeptDTO parent = byId.get(parentId);
                    if (parent != null) {
                        parent.getChildren().add(node);
                    }
                    return parentId == 0L || parent == null;
                })
                .toList();
    }
}
