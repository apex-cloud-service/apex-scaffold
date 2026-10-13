package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.DictDataDTO;
import com.apex.scaffold.application.dto.admin.DictDataRequest;
import com.apex.scaffold.application.dto.admin.DictTypeDTO;
import com.apex.scaffold.application.dto.admin.DictTypeRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.DictDataEntity;
import com.apex.scaffold.domain.model.entity.DictTypeEntity;
import com.apex.scaffold.domain.repository.DictDataRepository;
import com.apex.scaffold.domain.repository.DictTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 字典管理应用服务。
 *
 * <p>统一提供字典类型与字典数据的查询、创建、更新、删除与启停管理，
 * 是管理系统下拉选项与枚举值翻译能力的基础支撑。字典采用
 * "类型-数据"两级模型：类型编码全局唯一，数据项按类型归属。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>字典类型编码全局唯一，创建与更新时校验（{@code DICT_TYPE_EXISTS}）</li>
 *   <li>字典数据必须归属已存在的字典类型（{@code DICT_TYPE_NOT_FOUND}）</li>
 *   <li>含数据项的字典类型禁止删除（{@code DICT_DATA_NOT_FOUND} 语义复用）</li>
 *   <li>同一类型内默认项唯一，设置新默认项前自动清空旧默认项</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService {

    /**
     * 字典类型仓储。
     */
    private final DictTypeRepository dictTypeRepository;

    /**
     * 字典数据仓储。
     */
    private final DictDataRepository dictDataRepository;

    // ==================== 字典类型 ====================

    /**
     * 查询全部字典类型。
     *
     * @return 字典类型 DTO 列表
     */
    public Mono<List<DictTypeDTO>> listTypes() {
        return dictTypeRepository
                .findAll()
                .map(types -> types.stream().map(this::toTypeDTO).toList());
    }

    /**
     * 查询字典类型详情。
     *
     * @param id 字典类型 ID
     * @return 字典类型实体
     * @throws BusinessException 当字典类型不存在时抛出 {@code DICT_TYPE_NOT_FOUND}
     */
    public Mono<DictTypeEntity> getType(Long id) {
        return dictTypeRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.DICT_TYPE_NOT_FOUND)));
    }

    /**
     * 创建字典类型。
     *
     * <p>校验编码唯一性后持久化。</p>
     *
     * @param request 字典类型请求体
     * @return 已创建的字典类型实体
     * @throws BusinessException 当编码已存在时抛出 {@code DICT_TYPE_EXISTS}
     */
    public Mono<DictTypeEntity> createType(DictTypeRequest request) {
        return dictTypeRepository.existsByType(request.getDictType(), null).flatMap(exists -> {
            if (exists) {
                return Mono.error(new BusinessException(ResultCode.DICT_TYPE_EXISTS));
            }
            DictTypeEntity entity =
                    DictTypeEntity.create(request.getDictName(), request.getDictType(), request.getRemark());
            return dictTypeRepository.insert(entity).map(rows -> {
                log.info("创建字典类型成功，type: {}", entity.getDictType());
                return entity;
            });
        });
    }

    /**
     * 更新字典类型。
     *
     * <p>类型编码不允许变更（类型编码是数据归属键），仅更新名称与备注。</p>
     *
     * @param id 字典类型 ID
     * @param request 字典类型请求体
     * @return 已更新的字典类型实体
     * @throws BusinessException 当字典类型不存在时抛出
     */
    public Mono<DictTypeEntity> updateType(Long id, DictTypeRequest request) {
        return getType(id)
                .flatMap(existing -> {
                    existing.updateInfo(request.getDictName(), request.getRemark());
                    return dictTypeRepository.updateById(existing).map(rows -> existing);
                })
                .doOnSuccess(type -> log.info("更新字典类型成功，id: {}", id));
    }

    /**
     * 删除字典类型。
     *
     * <p>含数据项的字典类型禁止删除，防止孤儿数据。</p>
     *
     * @param id 字典类型 ID
     * @return 删除结果，true-删除成功，false-未删除任何记录
     * @throws BusinessException 当字典类型不存在或含数据项时抛出
     */
    public Mono<Boolean> deleteType(Long id) {
        return getType(id)
                .flatMap(type -> dictDataRepository
                        .existsByType(type.getDictType())
                        .flatMap(hasData -> {
                            if (hasData) {
                                return Mono.error(
                                        new BusinessException(ResultCode.DICT_DATA_NOT_FOUND, "字典类型下存在数据项，无法删除"));
                            }
                            return dictTypeRepository.deleteById(id).map(rows -> rows > 0);
                        }))
                .doOnSuccess(deleted -> log.info("删除字典类型完成，id: {}, deleted: {}", id, deleted));
    }

    /**
     * 启用或停用字典类型。
     *
     * @param id 字典类型 ID
     * @param enabled true-启用，false-停用
     * @return 更新后的字典类型实体
     * @throws BusinessException 当字典类型不存在时抛出
     */
    public Mono<DictTypeEntity> updateTypeStatus(Long id, boolean enabled) {
        return getType(id).flatMap(type -> {
            if (enabled) {
                type.enable();
            } else {
                type.disable();
            }
            return dictTypeRepository.updateById(type).map(rows -> type);
        });
    }

    // ==================== 字典数据 ====================

    /**
     * 根据字典类型编码查询全部启用数据项。
     *
     * <p>供前端下拉选项与业务代码读取，仅返回启用状态的数据。</p>
     *
     * @param dictType 字典类型编码
     * @return 字典数据 DTO 列表
     */
    public Mono<List<DictDataDTO>> listDataByType(String dictType) {
        return dictDataRepository
                .findByType(dictType)
                .map(items -> items.stream().map(this::toDataDTO).toList());
    }

    /**
     * 查询字典数据详情。
     *
     * @param id 字典数据 ID
     * @return 字典数据实体
     * @throws BusinessException 当字典数据不存在时抛出 {@code DICT_DATA_NOT_FOUND}
     */
    public Mono<DictDataEntity> getData(Long id) {
        return dictDataRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.DICT_DATA_NOT_FOUND)));
    }

    /**
     * 创建字典数据。
     *
     * <p>校验归属字典类型存在后持久化；标记为默认项时先清空同类型旧默认项。</p>
     *
     * @param request 字典数据请求体
     * @return 已创建的字典数据实体
     * @throws BusinessException 当字典类型不存在时抛出
     */
    public Mono<DictDataEntity> createData(DictDataRequest request) {
        return assertTypeExists(request.getDictType())
                .then(Mono.defer(() -> {
                    DictDataEntity entity = DictDataEntity.create(
                            request.getDictSort(),
                            request.getDictLabel(),
                            request.getDictValue(),
                            request.getDictType());
                    entity.setRemark(request.getRemark());
                    boolean isDefault = request.getIsDefault() != null && request.getIsDefault() == 1;
                    if (isDefault) {
                        entity.markAsDefault();
                        return dictDataRepository
                                .clearDefaultByType(request.getDictType())
                                .then(dictDataRepository.insert(entity))
                                .map(rows -> entity);
                    }
                    return dictDataRepository.insert(entity).map(rows -> entity);
                }))
                .doOnSuccess(data -> log.info("创建字典数据成功，type: {}, value: {}", data.getDictType(), data.getDictValue()));
    }

    /**
     * 更新字典数据。
     *
     * @param id 字典数据 ID
     * @param request 字典数据请求体
     * @return 已更新的字典数据实体
     * @throws BusinessException 当字典数据不存在时抛出
     */
    public Mono<DictDataEntity> updateData(Long id, DictDataRequest request) {
        return getData(id)
                .flatMap(existing -> assertTypeExists(request.getDictType()).thenReturn(existing))
                .flatMap(existing -> {
                    existing.updateInfo(
                            request.getDictSort(), request.getDictLabel(), request.getDictValue(), request.getRemark());
                    boolean wantDefault = request.getIsDefault() != null && request.getIsDefault() == 1;
                    if (wantDefault && !existing.isDefaultItem()) {
                        return dictDataRepository
                                .clearDefaultByType(request.getDictType())
                                .then(Mono.fromRunnable(existing::markAsDefault))
                                .then(dictDataRepository.updateById(existing))
                                .map(rows -> existing);
                    }
                    return dictDataRepository.updateById(existing).map(rows -> existing);
                })
                .doOnSuccess(data -> log.info("更新字典数据成功，id: {}", id));
    }

    /**
     * 删除字典数据。
     *
     * @param id 字典数据 ID
     * @return 删除结果，true-删除成功，false-未删除任何记录
     * @throws BusinessException 当字典数据不存在时抛出
     */
    public Mono<Boolean> deleteData(Long id) {
        return getData(id)
                .flatMap(data -> dictDataRepository.deleteById(id).map(rows -> rows > 0))
                .doOnSuccess(deleted -> log.info("删除字典数据完成，id: {}, deleted: {}", id, deleted));
    }

    /**
     * 启用或停用字典数据。
     *
     * @param id 字典数据 ID
     * @param enabled true-启用，false-停用
     * @return 更新后的字典数据实体
     * @throws BusinessException 当字典数据不存在时抛出
     */
    public Mono<DictDataEntity> updateDataStatus(Long id, boolean enabled) {
        return getData(id).flatMap(data -> {
            if (enabled) {
                data.enable();
            } else {
                data.disable();
            }
            return dictDataRepository.updateById(data).map(rows -> data);
        });
    }

    /**
     * 校验字典类型编码存在。
     *
     * @param dictType 字典类型编码
     * @return 校验通过的信号
     * @throws BusinessException 当字典类型不存在时抛出 {@code DICT_TYPE_NOT_FOUND}
     */
    private Mono<Void> assertTypeExists(String dictType) {
        return dictTypeRepository
                .findByType(dictType)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.DICT_TYPE_NOT_FOUND)))
                .then();
    }

    /**
     * 将字典类型实体转换为 DTO。
     *
     * @param entity 字典类型实体
     * @return 字典类型 DTO
     */
    private DictTypeDTO toTypeDTO(DictTypeEntity entity) {
        DictTypeDTO dto = new DictTypeDTO();
        dto.setId(entity.getId());
        dto.setDictName(entity.getDictName());
        dto.setDictType(entity.getDictType());
        dto.setStatus(entity.getStatus());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }

    /**
     * 将字典数据实体转换为 DTO。
     *
     * @param entity 字典数据实体
     * @return 字典数据 DTO
     */
    private DictDataDTO toDataDTO(DictDataEntity entity) {
        DictDataDTO dto = new DictDataDTO();
        dto.setId(entity.getId());
        dto.setDictSort(entity.getDictSort());
        dto.setDictLabel(entity.getDictLabel());
        dto.setDictValue(entity.getDictValue());
        dto.setDictType(entity.getDictType());
        dto.setIsDefault(entity.getIsDefault());
        dto.setStatus(entity.getStatus());
        dto.setRemark(entity.getRemark());
        return dto;
    }
}
