package com.apex.scaffold.file.domain.repository;

import com.apex.scaffold.file.domain.model.entity.FileShareEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Mono;

/**
 * 文件分享记录仓储端口。
 *
 * <p>仓储实现必须保证撤销操作同时校验租户与创建者，避免跨租户代为撤销。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
public interface FileShareRepository {

    /**
     * 保存分享记录。
     *
     * @param share 分享记录
     * @return 保存后的分享记录
     */
    Mono<FileShareEntity> save(FileShareEntity share);

    /**
     * 按编号查询分享记录。
     *
     * @param id 分享记录编号
     * @return 分享记录，未找到时返回空
     */
    Mono<FileShareEntity> findById(Long id);

    /**
     * 按令牌摘要查询分享记录。
     *
     * @param tokenHash 令牌 SHA-256 十六进制摘要
     * @return 分享记录，未找到时返回空
     */
    Mono<FileShareEntity> findByTokenHash(String tokenHash);

    /**
     * 条件撤销：仅当记录属于给定租户与创建者且仍有效时置为已撤销。
     *
     * @param id 分享记录编号
     * @param tenantId 可信租户标识
     * @param creatorId 可信用户标识
     * @param now 撤销时间
     * @return 撤销成功返回 true
     */
    Mono<Boolean> markRevoked(Long id, String tenantId, Long creatorId, LocalDateTime now);
}
