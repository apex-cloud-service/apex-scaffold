package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.ReportTicketEntity;
import com.apex.scaffold.domain.repository.ReportTicketRepository;
import com.apex.scaffold.infrastructure.persistence.converter.ReportTicketPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.ReportTicketMapper;
import com.apex.scaffold.infrastructure.persistence.po.ReportTicketPO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 报告票据仓库实现（基础设施层）。
 *
 * <p>负责 {@link ReportTicketEntity} 的持久化读写，并通过 {@link ReportTicketPoConverter}
 * 完成领域实体与持久化对象之间的转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ReportTicketRepository
 */
@Repository
@RequiredArgsConstructor
public class ReportTicketRepositoryImpl implements ReportTicketRepository {

    private final ReportTicketMapper mapper;
    private final ReportTicketPoConverter reportTicketPoConverter;

    /**
     * 保存报告票据（新增或更新）。
     *
     * <p>当 id 为空时执行 insert；否则执行 updateById。
     * 保存时会设置 deleted=0，并维护 createTime/updateTime。
     *
     * @param entity 报告票据实体
     * @return 保存后的报告票据实体
     */
    @Override
    public Mono<ReportTicketEntity> save(ReportTicketEntity entity) {
        return Mono.fromCallable(() -> {
                    ReportTicketPO po = reportTicketPoConverter.toPo(entity);
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
                .map(reportTicketPoConverter::toEntity);
    }
}
