package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 采样记录持久化对象。
 *
 * <p>映射数据库表 biz_sample，记录照片采样数据，
 * 包括采样模式、人脸类型、裁切参数和人脸姿态角等信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("biz_sample")
/**n * SamplePO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class SamplePO extends BasePO {

    /**
     * 创建采样记录实体对象。
     *
     */

    /**
     * 采样用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 关联照片 ID。
     */
    @TableField("photo_id")
    private Long photoId;

    /**
     * 存储桶名称。
     */
    @TableField("bucket_name")
    private String bucketName;

    /**
     * 对象名（文件名）。
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 采样模式（如 TWO_CHOICE / FOUR_PICK_TWO）。
     */
    @TableField("mode")
    private String mode;

    /**
     * 人脸类型（如 CLOSE_UP / ATMOSPHERE）。
     */
    @TableField("face_type")
    private String faceType;

    /**
     * 裁切 X 坐标。
     */
    @TableField("crop_x")
    private Integer cropX;

    /**
     * 裁切 Y 坐标。
     */
    @TableField("crop_y")
    private Integer cropY;

    /**
     * 裁切宽度。
     */
    @TableField("crop_w")
    private Integer cropW;

    /**
     * 裁切高度。
     */
    @TableField("crop_h")
    private Integer cropH;

    /**
     * 俯仰角，用于计算 FRONTAL / NON_FRONTAL（P0-3 修复）。
     */
    @TableField("pitch")
    private Double pitch;

    /**
     * 偏航角，用于计算 FRONTAL / NON_FRONTAL（P0-3 修复）。
     */
    @TableField("yaw")
    private Double yaw;

    /**
     * 翻滚角，用于计算 FRONTAL / NON_FRONTAL（P0-3 修复）。
     */
    @TableField("roll")
    private Double roll;

    /**
     * 人脸占画面比例，用于计算 PORTRAIT_CLOSE / HALF / FULL（P0-3 修复）。
     */
    @TableField("face_ratio")
    private Double faceRatio;
}
