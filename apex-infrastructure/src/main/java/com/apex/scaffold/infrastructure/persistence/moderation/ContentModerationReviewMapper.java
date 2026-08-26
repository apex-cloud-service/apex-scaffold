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
     * 查询 active。
     * @param id 主键 ID
     * @return 返回 ContentModerationReviewPO 类型结果
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
     * 查询 分页。
     * @param status 状态过滤
     * @param mine 是否仅查本人
     * @param reviewerOpenId 审核员 OpenId
     * @param offset 分页偏移量
     * @param limit 每页条数
     * @return 返回 List&lt;ContentModerationReviewPO&gt; 类型结果
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
     * 执行 count 相关逻辑。
     * @param status 状态过滤
     * @param mine 是否仅查本人
     * @param reviewerOpenId 审核员 OpenId
     * @return 计算结果
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
     * 执行 casClaim 相关逻辑。
     * @param id 主键 ID
     * @param reviewerOpenId 审核员 OpenId
     * @param expectedVersion 参数 expectedVersion（类型 long）
     * @param claimedAt 参数 claimedAt（类型 LocalDateTime）
     * @return 计算结果
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
     * 执行 casDecide 相关逻辑。
     * @param id 主键 ID
     * @param reviewerOpenId 审核员 OpenId
     * @param toStatus 参数 toStatus（类型 String）
     * @param comment 参数 comment（类型 String）
     * @param expectedVersion 参数 expectedVersion（类型 long）
     * @return 计算结果
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
     * 执行 reopenStaleClaims 相关逻辑。
     * @param claimedBefore 参数 claimedBefore（类型 LocalDateTime）
     * @return 计算结果
     */
    @Update("UPDATE biz_content_moderation_review SET status='PENDING',reviewer_open_id=NULL,claimed_at=NULL,"
            + "version=version+1,update_time=NOW() WHERE status='REVIEWING' AND claimed_at IS NOT NULL "
            + "AND claimed_at<#{claimedBefore} AND deleted=0")
    int reopenStaleClaims(@Param("claimedBefore") LocalDateTime claimedBefore);
}
