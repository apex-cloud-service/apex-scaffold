package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.admin.ReviewReason;
import com.apex.scaffold.domain.repository.ReviewReasonRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 审核原因配置仓库的 JDBC 实现。
 *
 * <p>基于 {@code JdbcTemplate} 操作 {@code biz_admin_review_reason} 表，
 * 提供原因配置的查询与乐观锁更新，阻塞操作切换到专用 JDBC 调度器。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ReviewReasonRepositoryImpl implements ReviewReasonRepository {
    private final JdbcTemplate jdbcTemplate;

    /**
     * 按审核类型查询原因配置列表。
     *
     * @param type        审核类型
     * @param enabledOnly 为 {@code true} 时仅返回启用项，否则返回全部
     * @return 原因配置列表，按排序号升序
     */
    @Override
    public Mono<List<ReviewReason>> findByType(String type, boolean enabledOnly) {
        return Mono.fromCallable(() -> jdbcTemplate.query(
                        "SELECT id,review_type,code,label,description,enabled,require_comment,sort_order,version "
                                + "FROM biz_admin_review_reason WHERE deleted=0 AND review_type=? "
                                + (enabledOnly ? "AND enabled=1 " : "") + "ORDER BY sort_order,id",
                        (rs, row) -> map(rs),
                        type))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 按类型与编码查找启用中的原因配置。
     *
     * @param type 审核类型
     * @param code 原因编码
     * @return 匹配的启用配置；不存在时为空
     */
    @Override
    public Mono<ReviewReason> findEnabled(String type, String code) {
        return findByType(type, true)
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .filter(item -> item.code().equals(code))
                .next();
    }

    /**
     * 使用乐观锁更新原因配置并返回最新记录。
     *
     * <p>版本号不匹配或记录已被删除时抛业务异常，防止配置被并发覆盖；
     * 更新成功后重新读取整行以返回完整配置。</p>
     *
     * @param id              配置 ID
     * @param label           配置名称
     * @param description     配置说明
     * @param enabled         是否启用
     * @param requireComment  是否强制填写备注
     * @param sortOrder       排序值（越小越靠前）
     * @param expectedVersion 乐观锁预期版本号
     * @return 更新后的配置
     * @throws BusinessException 当配置已被修改（版本冲突）时抛出
     */
    @Override
    public Mono<ReviewReason> update(
            Long id,
            String label,
            String description,
            boolean enabled,
            boolean requireComment,
            int sortOrder,
            int expectedVersion) {
        return Mono.fromCallable(() -> {
                    int rows = jdbcTemplate.update(
                            "UPDATE biz_admin_review_reason SET label=?,description=?,enabled=?,"
                                    + "require_comment=?,sort_order=?,version=version+1,update_time=NOW() "
                                    + "WHERE id=? AND version=? AND deleted=0",
                            label,
                            description,
                            enabled ? 1 : 0,
                            requireComment ? 1 : 0,
                            sortOrder,
                            id,
                            expectedVersion);
                    if (rows != 1) throw new BusinessException(ResultCode.PARAM_ERROR, "配置已被修改，请刷新后重试");
                    return jdbcTemplate.queryForObject(
                            "SELECT id,review_type,code,label,description,enabled,require_comment,sort_order,version "
                                    + "FROM biz_admin_review_reason WHERE id=? AND deleted=0",
                            (rs, row) -> map(rs),
                            id);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 将当前行映射为审核原因领域对象。
     *
     * @param rs 结果集当前行
     * @return 审核原因对象
     * @throws java.sql.SQLException 读取列失败时抛出
     */
    private static ReviewReason map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReviewReason(
                rs.getLong("id"),
                rs.getString("review_type"),
                rs.getString("code"),
                rs.getString("label"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                rs.getBoolean("require_comment"),
                rs.getInt("sort_order"),
                rs.getInt("version"));
    }
}
