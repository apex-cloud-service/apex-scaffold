package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.ReportEntity;
import com.apex.scaffold.domain.repository.ReportRepository;
import com.apex.scaffold.infrastructure.persistence.converter.ReportPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.ReportMapper;
import com.apex.scaffold.infrastructure.persistence.po.ReportPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 报告仓库实现（基础设施层）。
 *
 * <p>负责 {@link ReportEntity} 的持久化读写，并通过 {@link ReportPoConverter}
 * 完成领域实体与持久化对象之间的转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ReportRepository
 */
@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private final ReportMapper mapper;
    private final ReportPoConverter reportPoConverter;

    /**
     * 根据报告 ID 查询报告详情。
     *
     * @param id 报告 ID
     * @return 报告实体；若不存在则返回空 Mono
     */
    @Override
    public Mono<ReportEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(reportPoConverter::toEntity);
    }

    /**
     * 查询用户指定类型的最新报告。
     *
     * <p>按 ownerUserId 与 reportType 过滤，并按 createdAt 倒序取第一条（limit 1）。
     *
     * @param userId 用户 ID
     * @param reportType 报告类型
     * @return 最新报告实体；若不存在则返回空 Mono
     */
    @Override
    public Mono<ReportEntity> findLatestByUserAndType(Long userId, String reportType) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<ReportPO>()
                        .eq(ReportPO::getOwnerUserId, userId)
                        .eq(ReportPO::getReportType, reportType)
                        .orderByDesc(ReportPO::getCreatedAt)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(reportPoConverter::toEntity);
    }

    /**
     * 保存报告（新增或更新）。
     *
     * <p>当 id 为空时执行 insert；否则执行 updateById。
     * 保存时会设置 deleted=0，并维护 createTime/updateTime。
     *
     * @param entity 报告实体
     * @return 保存后的报告实体
     */
    @Override
    public Mono<ReportEntity> save(ReportEntity entity) {
        return Mono.fromCallable(() -> {
                    ReportPO po = reportPoConverter.toPo(entity);
                    po.setDeleted(0);
                    LocalDateTime now = LocalDateTime.now();
                    if (po.getCreateTime() == null) {
                        po.setCreateTime(now);
                    }
                    po.setUpdateTime(now);
                    if (po.getId() == null) {
                        mapper.insert(po);
                    } else {
                        mapper.updateById(po);
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(reportPoConverter::toEntity);
    }
}
