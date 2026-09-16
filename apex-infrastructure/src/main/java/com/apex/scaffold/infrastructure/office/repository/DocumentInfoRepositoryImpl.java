package com.apex.scaffold.infrastructure.office.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.office.document.DocumentInfo;
import com.apex.scaffold.domain.office.document.DocumentInfoRepository;
import com.apex.scaffold.infrastructure.office.converter.DocumentInfoConverter;
import com.apex.scaffold.infrastructure.office.mapper.DocumentInfoMapper;
import com.apex.scaffold.infrastructure.office.po.DocumentInfoPO;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 文档信息仓储实现
 *
 * <p>实现DocumentInfoRepository接口，负责文档信息的持久化操作。
 * 使用BlockingIoSchedulers隔离JDBC阻塞操作，保证响应式编程的非阻塞特性。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DocumentInfoRepositoryImpl implements DocumentInfoRepository {

    private final DocumentInfoMapper mapper;
    private final DocumentInfoConverter converter;
    private final BlockingIoSchedulers blockingIoSchedulers;

    @Override
    public Mono<DocumentInfo> save(DocumentInfo documentInfo) {
        return Mono.fromCallable(() -> {
                    DocumentInfoPO po = converter.toPO(documentInfo);
                    mapper.insert(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(savedDoc -> log.debug("文档信息保存成功 - id: {}", savedDoc.getId()))
                .doOnError(error -> log.error("文档信息保存失败", error));
    }

    @Override
    public Mono<DocumentInfo> findById(Long id) {
        return Mono.fromCallable(() -> {
                    DocumentInfoPO po = mapper.selectById(id);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(doc -> log.debug("根据ID查询文档信息 - id: {}, found: {}", id, doc != null))
                .doOnError(error -> log.error("根据ID查询文档信息失败 - id: {}", id, error));
    }

    @Override
    public Mono<DocumentInfo> findByPlatformAndDocumentId(String platform, String platformDocumentId) {
        return Mono.fromCallable(() -> {
                    DocumentInfoPO po = mapper.selectByPlatformAndDocumentId(platform, platformDocumentId);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(doc -> log.debug(
                        "根据平台和文档ID查询 - platform: {}, documentId: {}, found: {}",
                        platform,
                        platformDocumentId,
                        doc != null))
                .doOnError(error ->
                        log.error("根据平台和文档ID查询失败 - platform: {}, documentId: {}", platform, platformDocumentId, error));
    }

    @Override
    public Mono<List<DocumentInfo>> findByOwner(String ownerId) {
        return Mono.fromCallable(() -> {
                    List<DocumentInfoPO> poList = mapper.selectByOwner(ownerId);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("根据所有者查询文档信息 - ownerId: {}, count: {}", ownerId, list.size()))
                .doOnError(error -> log.error("根据所有者查询文档信息失败 - ownerId: {}", ownerId, error));
    }

    @Override
    public Mono<List<DocumentInfo>> findByParentFolder(String parentFolderId) {
        return Mono.fromCallable(() -> {
                    List<DocumentInfoPO> poList = mapper.selectByParentFolder(parentFolderId);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(
                        list -> log.debug("根据父文件夹查询文档信息 - parentFolderId: {}, count: {}", parentFolderId, list.size()))
                .doOnError(error -> log.error("根据父文件夹查询文档信息失败 - parentFolderId: {}", parentFolderId, error));
    }

    @Override
    public Mono<List<DocumentInfo>> searchByTitle(String titleKeyword) {
        return Mono.fromCallable(() -> {
                    List<DocumentInfoPO> poList = mapper.searchByTitle(titleKeyword);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("根据标题关键字查询文档信息 - keyword: {}, count: {}", titleKeyword, list.size()))
                .doOnError(error -> log.error("根据标题关键字查询文档信息失败 - keyword: {}", titleKeyword, error));
    }

    @Override
    public Mono<List<DocumentInfo>> findByCollaborator(String collaboratorId) {
        return Mono.fromCallable(() -> {
                    List<DocumentInfoPO> poList = mapper.selectByCollaborator(collaboratorId);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(
                        list -> log.debug("根据协作者查询文档信息 - collaboratorId: {}, count: {}", collaboratorId, list.size()))
                .doOnError(error -> log.error("根据协作者查询文档信息失败 - collaboratorId: {}", collaboratorId, error));
    }

    @Override
    public Mono<List<DocumentInfo>> findArchivedDocuments(String ownerId) {
        return Mono.fromCallable(() -> {
                    List<DocumentInfoPO> poList = mapper.selectArchivedDocuments(ownerId);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("查询已归档文档 - ownerId: {}, count: {}", ownerId, list.size()))
                .doOnError(error -> log.error("查询已归档文档失败 - ownerId: {}", ownerId, error));
    }

    @Override
    public Mono<DocumentInfo> update(DocumentInfo documentInfo) {
        return Mono.fromCallable(() -> {
                    DocumentInfoPO po = converter.toPO(documentInfo);
                    mapper.updateById(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(updatedDoc -> log.debug("文档信息更新成功 - id: {}", updatedDoc.getId()))
                .doOnError(error -> log.error("文档信息更新失败 - id: {}", documentInfo.getId(), error));
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return Mono.fromCallable(() -> mapper.deleteById(id) > 0)
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(deleted -> log.debug("文档信息删除 - id: {}, deleted: {}", id, deleted))
                .doOnError(error -> log.error("文档信息删除失败 - id: {}", id, error));
    }

    @Override
    public Mono<Integer> batchSave(List<DocumentInfo> documentInfoList) {
        return Mono.fromCallable(() -> {
                    int count = 0;
                    for (DocumentInfo documentInfo : documentInfoList) {
                        DocumentInfoPO po = converter.toPO(documentInfo);
                        mapper.insert(po);
                        count++;
                    }
                    return count;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(count -> log.debug("批量保存文档信息成功 - count: {}", count))
                .doOnError(error -> log.error("批量保存文档信息失败", error));
    }
}
