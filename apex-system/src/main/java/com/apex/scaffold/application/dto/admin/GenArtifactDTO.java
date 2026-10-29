package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成产物 DTO
 *
 * <p>产物是执行阶段写入独立任务目录的文件记录。浏览器只能通过产物 ID 下载，
 * 不能提交任意文件路径；下载时服务端重新校验租户归属与保留期。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成产物")
public class GenArtifactDTO {

    @Schema(description = "产物 ID", example = "1")
    private Long id;

    @Schema(description = "生成任务 ID", example = "1830000000000000001")
    private Long taskId;

    @Schema(description = "生成配置 ID", example = "1")
    private Long configId;

    @Schema(description = "生成历史 ID", example = "1")
    private Long historyId;

    @Schema(description = "表名", example = "sys_user")
    private String tableName;

    @Schema(description = "模板 ID", example = "1001")
    private Long templateId;

    @Schema(description = "模板类型", example = "ENTITY")
    private String templateType;

    @Schema(description = "相对输出路径", example = "domain/model/UserEntity.java")
    private String relativePath;

    @Schema(description = "文件名", example = "UserEntity.java")
    private String fileName;

    @Schema(description = "文件大小（字节）", example = "2048")
    private Long fileSize;

    @Schema(description = "内容 SHA-256 摘要（前 16 位）", example = "1c9d…")
    private String contentDigest;

    @Schema(description = "发布状态（PUBLISHED/FAILED）", example = "PUBLISHED")
    private String status;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
