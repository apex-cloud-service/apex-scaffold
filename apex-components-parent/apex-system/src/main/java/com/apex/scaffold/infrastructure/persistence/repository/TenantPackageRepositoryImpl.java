package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.model.entity.TenantPackageEntity;
import com.apex.scaffold.domain.repository.TenantPackageRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantPackageMapper;
import com.apex.scaffold.infrastructure.persistence.po.TenantPackagePO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户套餐仓储实现（平台级定义表，方案批次 B3）。
 *
 * <p>sys_tenant_package 在租户拦截器白名单中（平台级定义，先于租户上下文查询），
 * 读写均为平台维度，与 {@code JdbcTenantScope} 业务路径分离。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TenantPackageRepositoryImpl implements TenantPackageRepository {

    private final TenantPackageMapper mapper;

    @Override
    public Mono<TenantPackageEntity> findByCode(String packageCode) {
        return Mono.fromCallable(() -> converter(mapper.selectOne(new QueryWrapper<TenantPackagePO>()
                        .eq("package_code", packageCode)
                        .eq("deleted", 0))))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<TenantPackageEntity> findAll() {
        return Mono.fromCallable(() -> mapper.selectList(
                        new QueryWrapper<TenantPackagePO>().eq("deleted", 0).orderByAsc("id")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(this::converter);
    }

    @Override
    public Mono<TenantPackageEntity> save(TenantPackageEntity entity) {
        return Mono.fromCallable(() -> {
                    TenantPackagePO po = toPo(entity);
                    if (entity.getId() == null) {
                        po.setCreateTime(LocalDateTime.now());
                        po.setUpdateTime(LocalDateTime.now());
                        mapper.insert(po);
                    } else {
                        po.setUpdateTime(LocalDateTime.now());
                        mapper.updateById(po);
                    }
                    return converter(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** 实体 → PO（集合序列化为 JSON 数组文本）。 */
    private TenantPackagePO toPo(TenantPackageEntity entity) {
        TenantPackagePO po = new TenantPackagePO();
        po.setId(entity.getId());
        po.setPackageCode(entity.getPackageCode());
        po.setPackageName(entity.getPackageName());
        po.setMenuIds(JsonUtil.toJson(entity.getMenuIds() == null ? List.of() : entity.getMenuIds()));
        po.setPermissionCodes(
                JsonUtil.toJson(entity.getPermissionCodes() == null ? List.of() : entity.getPermissionCodes()));
        po.setFeatures(JsonUtil.toJson(entity.getFeatures() == null ? List.of() : entity.getFeatures()));
        po.setStatus(entity.getStatus());
        po.setRemark(entity.getRemark());
        po.setVersion(entity.getVersion() == null ? 0 : entity.getVersion());
        po.setDeleted(entity.getDeleted() == null ? 0 : entity.getDeleted());
        return po;
    }

    /**
     * PO → 实体。
     *
     * <p>菜单、权限码与能力数组分别解析；任一字段损坏时设置
     * {@code configurationInvalid=true}，由应用层统一转成拒绝范围。损坏字段不会被
     * 当作空集合，因为空集合在菜单/权限维度具有“全量”语义。</p>
     *
     * @param po 套餐持久化对象；可为 {@code null}
     * @return 套餐实体；入参为 {@code null} 时返回 {@code null}
     */
    private TenantPackageEntity converter(TenantPackagePO po) {
        if (po == null) {
            return null;
        }
        ParseResult<Long> menuIds = parseArray(po.getMenuIds(), "menu_ids", Long.class);
        ParseResult<String> permissionCodes = parseArray(po.getPermissionCodes(), "permission_codes", String.class);
        ParseResult<String> features = parseArray(po.getFeatures(), "features", String.class);
        return TenantPackageEntity.builder()
                .id(po.getId())
                .packageCode(po.getPackageCode())
                .packageName(po.getPackageName())
                .menuIds(new HashSet<>(menuIds.values()))
                .permissionCodes(new HashSet<>(permissionCodes.values()))
                .features(new HashSet<>(features.values()))
                .configurationInvalid(!menuIds.valid() || !permissionCodes.valid() || !features.valid())
                .status(po.getStatus())
                .remark(po.getRemark())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .version(po.getVersion())
                .deleted(po.getDeleted())
                .build();
    }

    /**
     * 解析 JSON 字符串数组，并明确返回解析有效性。
     *
     * <p>{@code null} 或空白 JSON 保持历史数据语义：菜单/权限维度为空集合，
     * 能力维度为空集合即未订阅。非空但格式错误、类型不匹配或元素为 {@code null}
     * 时视为配置损坏，返回空集合且 {@code valid=false}；调用方必须结合有效性判定，
     * 不得单独使用返回集合做授权决策。</p>
     *
     * @param json 待解析 JSON 文本，可为 {@code null} 或空白
     * @param fieldName 字段名，用于告警定位
     * @param elementType 数组元素类型
     * @param <T> 数组元素类型
     * @return 解析结果；损坏时 {@code valid=false}
     */
    private <T> ParseResult<T> parseArray(String json, String fieldName, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return new ParseResult<>(List.of(), true);
        }
        try {
            java.util.List<T> values = JsonUtil.getObjectMapper()
                    .readValue(
                            json,
                            JsonUtil.getObjectMapper()
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, elementType));
            if (values == null || values.stream().anyMatch(java.util.Objects::isNull)) {
                log.warn("套餐 {} 解析失败：数组为空值或包含 null 元素，按配置损坏处理", fieldName);
                return new ParseResult<>(List.of(), false);
            }
            return new ParseResult<>(values, true);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException error) {
            log.warn("套餐 {} 解析失败，按配置损坏拒绝授权: reason={}", fieldName, error.getMessage());
            return new ParseResult<>(List.of(), false);
        }
    }

    /**
     * JSON 数组解析结果。
     *
     * @param values 解析出的值；失效时为空集合
     * @param valid 配置是否可安全用于授权判定
     * @param <T> 元素类型
     */
    private record ParseResult<T>(List<T> values, boolean valid) {}
}
