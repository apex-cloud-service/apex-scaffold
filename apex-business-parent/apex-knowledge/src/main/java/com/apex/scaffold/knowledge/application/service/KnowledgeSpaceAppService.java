package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceSaveRequest;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 知识空间应用服务：空间 CRUD 与租户内唯一性约束。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Service
@RequiredArgsConstructor
public class KnowledgeSpaceAppService {

    private final KnowledgeSpaceRepository spaceRepository;

    private final KnowledgeDocumentRepository documentRepository;

    private final KnowledgeAssembler assembler;

    /**
     * 创建空间。
     *
     * @param request 创建请求
     * @return 创建后的空间 DTO
     */
    public Mono<KnowledgeSpaceDTO> create(KnowledgeSpaceSaveRequest request) {
        return spaceRepository
                .existsByName(request.name(), null)
                .flatMap(exists -> exists
                        ? Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "空间名称已存在"))
                        : spaceRepository.insert(KnowledgeSpace.builder()
                                .name(request.name())
                                .description(request.description())
                                .status(request.status() == null ? 1 : request.status())
                                .build()))
                .map(assembler::toSpaceDTO);
    }

    /**
     * 更新空间。
     *
     * @param id 空间 ID
     * @param request 更新请求
     * @return 更新后的空间 DTO
     */
    public Mono<KnowledgeSpaceDTO> update(Long id, KnowledgeSpaceSaveRequest request) {
        return requireSpace(id)
                .flatMap(existing -> spaceRepository
                        .existsByName(request.name(), id)
                        .flatMap(exists -> exists
                                ? Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "空间名称已存在"))
                                : spaceRepository.update(KnowledgeSpace.builder()
                                        .id(existing.getId())
                                        .name(request.name())
                                        .description(request.description())
                                        .status(request.status() == null ? existing.getStatus() : request.status())
                                        .build())))
                .map(assembler::toSpaceDTO);
    }

    /**
     * 删除空间：空间下仍有文档时拒绝，避免悬挂文档。
     *
     * @param id 空间 ID
     * @return 删除结果
     */
    public Mono<Boolean> delete(Long id) {
        return requireSpace(id).flatMap(existing -> documentRepository
                .findPage(existing.getId(), null, null, 1, 1)
                .flatMap(page -> page.getList() != null && !page.getList().isEmpty()
                        ? Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "空间下仍有文档，请先清理"))
                        : spaceRepository.deleteById(id)));
    }

    /**
     * 分页查询空间。
     *
     * @param keyword 名称模糊匹配
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 空间分页
     */
    public Mono<PageResponse<KnowledgeSpaceDTO>> findPage(String keyword, int pageNum, int pageSize) {
        return spaceRepository
                .findPage(keyword, pageNum, pageSize)
                .map(page -> PageResponse.of(
                        page.getList().stream().map(assembler::toSpaceDTO).toList(),
                        page.getTotal(),
                        pageNum,
                        pageSize));
    }

    /**
     * 查询正常空间列表（检索与问答的空间选择）。
     *
     * @return 空间 DTO 列表
     */
    public Mono<List<KnowledgeSpaceDTO>> listActive() {
        return spaceRepository
                .findAllActive()
                .map(spaces -> spaces.stream().map(assembler::toSpaceDTO).toList());
    }

    /**
     * 查询空间详情。
     *
     * @param id 空间 ID
     * @return 空间 DTO
     */
    public Mono<KnowledgeSpaceDTO> findById(Long id) {
        return requireSpace(id).map(assembler::toSpaceDTO);
    }

    private Mono<KnowledgeSpace> requireSpace(Long id) {
        return spaceRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "知识空间不存在")));
    }
}
