package ${appPackage}.service;

import ${appPackage}.dto.${module}.${entityName}DTO;
import ${appPackage}.dto.${module}.${entityName}Request;
import ${domainPackage}.model.entity.${entityName}Entity;
import ${domainPackage}.repository.${entityName}Repository;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * ${tableComment}应用服务。
 *
 * <p>提供${tableComment}的查询、创建、更新与删除管理能力，
 * 是接口层与领域层之间的业务编排入口。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>按 ID 查询时，${tableComment}不存在抛出 {@code NOT_FOUND} 业务异常</li>
 *   <li>删除采用逻辑删除，物理数据保留</li>
 * </ul>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ${entityName}Service {

    /**
     * ${tableComment}仓储。
     */
    private final ${entityName}Repository ${entityVar}Repository;

    /**
     * 查询全部${tableComment}。
     *
     * @return ${tableComment} DTO 列表
     */
    public Mono<List<${entityName}DTO>> list() {
        return ${entityVar}Repository.findAll().map(entities -> entities.stream().map(this::toDTO).toList());
    }

    /**
     * 查询${tableComment}详情。
     *
     * @param id ${tableComment} ID
     * @return ${tableComment}实体
     * @throws BusinessException 当${tableComment}不存在时抛出
     */
    public Mono<${entityName}Entity> get(Long id) {
        return ${entityVar}Repository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "${tableComment}不存在")));
    }

    /**
     * 创建${tableComment}。
     *
     * @param request ${tableComment}请求体
     * @return 已创建的${tableComment}实体
     */
    public Mono<${entityName}Entity> create(${entityName}Request request) {
        return Mono.defer(() -> {
                    ${entityName}Entity entity = ${entityName}Entity.create(
<#list createFields as field>
                            request.get${field.name?cap_first}()<#if field?has_next>,</#if>
</#list>);
                    return ${entityVar}Repository.insert(entity).map(rows -> entity);
                })
                .doOnSuccess(entity -> log.info("创建${tableComment}成功，id: {}", entity.getId()));
    }

    /**
     * 更新${tableComment}。
     *
     * @param id ${tableComment} ID
     * @param request ${tableComment}请求体
     * @return 已更新的${tableComment}实体
     * @throws BusinessException 当${tableComment}不存在时抛出
     */
    public Mono<${entityName}Entity> update(Long id, ${entityName}Request request) {
        return get(id)
                .flatMap(existing -> {
                    existing.updateInfo(
<#list updateFields as field>
                            request.get${field.name?cap_first}()<#if field?has_next>,</#if>
</#list>);
                    return ${entityVar}Repository.updateById(existing).map(rows -> existing);
                })
                .doOnSuccess(entity -> log.info("更新${tableComment}成功，id: {}", id));
    }

    /**
     * 删除${tableComment}。
     *
     * @param id ${tableComment} ID
     * @return 删除结果，true-删除成功，false-未删除任何记录
     * @throws BusinessException 当${tableComment}不存在时抛出
     */
    public Mono<Boolean> delete(Long id) {
        return get(id)
                .flatMap(entity -> ${entityVar}Repository.deleteById(id).map(rows -> rows > 0))
                .doOnSuccess(deleted -> log.info("删除${tableComment}完成，id: {}, deleted: {}", id, deleted));
    }
<#if stateMachineEnabled>

    /**
     * 流转${tableComment}状态。
     *
     * @param id ${tableComment} ID
     * @param targetState 目标状态
     * @return 已流转状态的${tableComment}实体
     * @throws BusinessException 当${tableComment}不存在或状态迁移非法时抛出
     */
    public Mono<${entityName}Entity> transition(Long id, String targetState) {
        return get(id)
                .flatMap(existing -> {
                    existing.transitionTo(targetState);
                    return ${entityVar}Repository.updateById(existing).map(rows -> existing);
                })
                .doOnSuccess(entity -> log.info("流转${tableComment}状态成功，id: {}, targetState: {}", id, targetState));
    }
</#if>

    /**
     * 将${tableComment}实体转换为 DTO。
     *
     * @param entity ${tableComment}实体
     * @return ${tableComment} DTO
     */
    private ${entityName}DTO toDTO(${entityName}Entity entity) {
        ${entityName}DTO dto = new ${entityName}DTO();
        dto.setId(entity.getId());
<#list fields as field>
        dto.set${field.name?cap_first}(entity.get${field.name?cap_first}());
</#list>
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
