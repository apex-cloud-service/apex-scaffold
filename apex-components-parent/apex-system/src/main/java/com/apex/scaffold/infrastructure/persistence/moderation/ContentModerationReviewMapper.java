package com.apex.scaffold.infrastructure.persistence.moderation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * {@code biz_content_moderation_review} 内容人工复核任务 Mapper。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface ContentModerationReviewMapper extends BaseMapper<ContentModerationReviewPO> {

    /**
     * 查询未被删除的复核任务。
     *
     * @param id 主键 ID
     * @return 复核任务持久化对象；不存在时返回 null
     */
    @Select("SELECT * FROM biz_content_moderation_review WHERE id=#{id} AND deleted=0")
    ContentModerationReviewPO selectActive(@Param("id") Long id);

    /**
     * 插入复核任务；发送方重试产生的重复内容命中唯一键时忽略（content_hash 去重）。
     *
     * @param row 复核任务
     * @return 影响行数
     */
    @Insert("INSERT IGNORE INTO biz_content_moderation_review (id,source_type,user_id,content_text,media_json,"
            + "provider,provider_request_id,findings_json,content_hash,status,reviewer_open_id,review_comment,"
            + "claimed_at,decided_at,create_time,update_time,version,deleted) VALUES (#{id},#{sourceType},#{userId},"
            + "#{contentText},#{mediaJson},#{provider},#{providerRequestId},#{findingsJson},#{contentHash},#{status},"
            + "#{reviewerOpenId},#{reviewComment},#{claimedAt},#{decidedAt},NOW(),NOW(),0,0)")
    int insertIgnore(ContentModerationReviewPO row);

    /**
     * 分页查询复核任务，PENDING 优先排序。
     *
     * @param status         状态过滤，为空表示不过滤
     * @param mine           是否仅查本人（审核员视角）
     * @param reviewerOpenId 审核员 OpenId
     * @param offset         分页偏移量
     * @param limit          每页条数
     * @return 复核任务持久化对象列表
     */
    @Select({
        "<script>",
        "SELECT * FROM biz_content_moderation_review WHERE deleted=0",
        "<if test='status != null'> AND status=#{status}</if>",
        "<if test='mine and reviewerOpenId != null'> AND reviewer_open_id=#{reviewerOpenId}</if>",
        "ORDER BY CASE WHEN status='PENDING' THEN 0 WHEN status='REVIEWING' THEN 1 ELSE 2 END, id ASC ",
        "LIMIT #{offset},#{limit}",
        "</script>"
    })
    List<ContentModerationReviewPO> selectPage(
            @Param("status") String status,
            @Param("mine") boolean mine,
            @Param("reviewerOpenId") String reviewerOpenId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 按状态 / 是否本人统计复核任务数量。
     *
     * @param status         状态过滤，为空表示不过滤
     * @param mine           是否仅查本人（审核员视角）
     * @param reviewerOpenId 审核员 OpenId
     * @return 符合条件的记录数量
     */
    @Select({
        "<script>",
        "SELECT COUNT(*) FROM biz_content_moderation_review WHERE deleted=0",
        "<if test='status != null'> AND status=#{status}</if>",
        "<if test='mine and reviewerOpenId != null'> AND reviewer_open_id=#{reviewerOpenId}</if>",
        "</script>"
    })
    long count(
            @Param("status") String status,
            @Param("mine") boolean mine,
            @Param("reviewerOpenId") String reviewerOpenId);

    /**
     * 乐观锁认领任务：仅当状态为 PENDING 且版本匹配时更新为 REVIEWING。
     *
     * @param id              主键 ID
     * @param reviewerOpenId  审核员 OpenId
     * @param expectedVersion 期望的版本号（乐观锁）
     * @param claimedAt       认领时间
     * @return 影响行数，1 表示认领成功，0 表示并发冲突
     */
    @Update("UPDATE biz_content_moderation_review SET status='REVIEWING',reviewer_open_id=#{reviewerOpenId},"
            + "claimed_at=#{claimedAt},version=version+1,update_time=NOW() WHERE id=#{id} AND status='PENDING' "
            + "AND version=#{expectedVersion} AND deleted=0")
    int casClaim(
            @Param("id") Long id,
            @Param("reviewerOpenId") String reviewerOpenId,
            @Param("expectedVersion") long expectedVersion,
            @Param("claimedAt") LocalDateTime claimedAt);

    /**
     * 乐观锁裁决任务：仅当状态为 REVIEWING 且认领人与版本匹配时更新为目标状态。
     *
     * @param id              主键 ID
     * @param reviewerOpenId  审核员 OpenId（须与认领人一致）
     * @param toStatus        目标状态
     * @param comment         裁决意见
     * @param expectedVersion 期望的版本号（乐观锁）
     * @return 影响行数，1 表示裁决成功，0 表示并发冲突
     */
    @Update("UPDATE biz_content_moderation_review SET status=#{toStatus},review_comment=#{comment},"
            + "decided_at=NOW(),version=version+1,update_time=NOW() WHERE id=#{id} AND status='REVIEWING' "
            + "AND reviewer_open_id=#{reviewerOpenId} AND version=#{expectedVersion} AND deleted=0")
    int casDecide(
            @Param("id") Long id,
            @Param("reviewerOpenId") String reviewerOpenId,
            @Param("toStatus") String toStatus,
            @Param("comment") String comment,
            @Param("expectedVersion") long expectedVersion);

    /**
     * 重新开放认领超时的任务：REVIEWING → PENDING 并清除认领信息。
     *
     * @param claimedBefore 认领时间早于该时间点的任务将被重新开放
     * @return 影响行数，即被重新开放的任务数量
     */
    @Update("UPDATE biz_content_moderation_review SET status='PENDING',reviewer_open_id=NULL,claimed_at=NULL,"
            + "version=version+1,update_time=NOW() WHERE status='REVIEWING' AND claimed_at IS NOT NULL "
            + "AND claimed_at<#{claimedBefore} AND deleted=0")
    int reopenStaleClaims(@Param("claimedBefore") LocalDateTime claimedBefore);
}
