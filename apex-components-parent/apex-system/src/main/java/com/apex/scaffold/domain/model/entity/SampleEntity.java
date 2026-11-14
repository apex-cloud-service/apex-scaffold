package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 采样数据聚合根实体（多级缓存示例实体）
 *
 * <p>
 * <strong>模块说明</strong>：本实体作为复杂缓存策略和数据采样的领域模型示例。
 * </p>
 *
 * <p>
 * <strong>适用场景</strong>：
 * - 内容采样：对用户上传的内容（图片/视频/文档）进行采样分析
 * - 数据裁剪：存储经过处理的数据切片（如图片裁剪、视频关键帧）
 * - 特征提取：记录从原始数据中提取的特征信息
 * - 推荐系统：存储用于推荐算法的采样数据
 * </p>
 *
 * <p>
 * <strong>技术特性</strong>：
 * - 演示多级缓存（Redisson + Caffeine）使用
 * - 演示复杂查询与索引设计
 * - 演示DDD聚合根设计模式
 * </p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SampleEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 采样用户ID
     */
    private Long userId;

    /**
     * 关联照片ID
     */
    private Long photoId;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 对象名称
     */
    private String fileName;

    /**
     * 采样模式
     *
     * @see SampleMode
     */
    private String mode;

    /**
     * 人脸类型
     *
     * @see FaceType
     */
    private String faceType;

    /**
     * 裁切X坐标
     */
    private Integer cropX;

    /**
     * 裁切Y坐标
     */
    private Integer cropY;

    /**
     * 裁切宽度
     */
    private Integer cropW;

    /**
     * 裁切高度
     */
    private Integer cropH;

    // === P0-3修复：新增AI算法分析字段 ===

    /**
     * 俯仰角（pitch），用于计算 FRONTAL / NON_FRONTAL 角度标签。
     *
     * <p>P0-3修复：新增字段，由AI算法接口返回。</p>
     *
     * @since 2.0.0
     */
    private Double pitch;

    /**
     * 偏航角（yaw），用于计算 FRONTAL / NON_FRONTAL 角度标签。
     *
     * <p>P0-3修复：新增字段，由AI算法接口返回。</p>
     *
     * @since 2.0.0
     */
    private Double yaw;

    /**
     * 翻滚角（roll），用于计算 FRONTAL / NON_FRONTAL 角度标签。
     *
     * <p>P0-3修复：新增字段，由AI算法接口返回。</p>
     *
     * @since 2.0.0
     */
    private Double roll;

    /**
     * 人脸占画面比例，用于计算 PORTRAIT_CLOSE / PORTRAIT_HALF / PORTRAIT_FULL 类型标签。
     *
     * <p>P0-3修复：新增字段，由AI算法接口返回。</p>
     *
     * @since 2.0.0
     */
    private Double faceRatio;

    /**
     * 默认构造函数
     */
    public SampleEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建二选一样本
     *
     * @param userId 用户ID
     * @param photoId 照片ID
     * @param bucketName 存储桶名称
     * @param fileName 对象名称
     * @return 新创建的采样实体
     */
    public static SampleEntity createTwoChoiceSample(Long userId, Long photoId, String bucketName, String fileName) {
        SampleEntity entity = new SampleEntity();
        entity.userId = userId;
        entity.photoId = photoId;
        entity.bucketName = bucketName;
        entity.fileName = fileName;
        entity.mode = SampleMode.TWO_CHOICE.getCode();
        entity.setTimestamps();
        return entity;
    }

    /**
     * 静态工厂方法：创建四选二样本
     *
     * @param userId 用户ID
     * @param photoId 照片ID
     * @param bucketName 存储桶名称
     * @param fileName 对象名称
     * @return 新创建的采样实体
     */
    public static SampleEntity createFourPickTwoSample(Long userId, Long photoId, String bucketName, String fileName) {
        SampleEntity entity = new SampleEntity();
        entity.userId = userId;
        entity.photoId = photoId;
        entity.bucketName = bucketName;
        entity.fileName = fileName;
        entity.mode = SampleMode.FOUR_PICK_TWO.getCode();
        entity.setTimestamps();
        return entity;
    }

    /**
     * 设置时间戳
     */
    private void setTimestamps() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        this.setCreateTime(now);
        this.setUpdateTime(now);
        this.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
    }

    /**
     * 领域行为：设置裁切区域
     *
     * @param x X坐标
     * @param y Y坐标
     * @param w 宽度
     * @param h 高度
     */
    public void setCropRegion(int x, int y, int w, int h) {
        this.cropX = x;
        this.cropY = y;
        this.cropW = w;
        this.cropH = h;
        touch();
    }

    /**
     * 领域行为：设置人脸类型
     *
     * @param faceType 人脸类型
     */
    public void setFaceTypeValue(String faceType) {
        this.faceType = faceType;
        touch();
    }

    /**
     * 采样模式枚举
     */
    @Getter
    public enum SampleMode {
        /** 二选一 */
        TWO_CHOICE("TWO_CHOICE"),
        /** 四选二 */
        FOUR_PICK_TWO("FOUR_PICK_TWO");

        private final String code;

        /**
         * 创建 {@code SampleMode} 实例。
         *
         * @param code 枚举编码
         */
        SampleMode(String code) {
            this.code = code;
        }

        /**
         * 根据枚举编码解析样本模式。
         *
         * @param code 样本模式编码
         * @return 匹配的样本模式；编码为空或未匹配时返回 {@code null}
         */
        public static SampleMode fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (SampleMode mode : values()) {
                if (mode.code.equals(code)) {
                    return mode;
                }
            }
            return null;
        }
    }

    /**
     * 人脸类型枚举
     */
    @Getter
    public enum FaceType {
        /** 特写 */
        CLOSE_UP("CLOSE_UP"),
        /** 氛围 */
        ATMOSPHERE("ATMOSPHERE");

        private final String code;

        /**
         * 创建 {@code FaceType} 实例。
         *
         * @param code 枚举编码
         */
        FaceType(String code) {
            this.code = code;
        }

        /**
         * 根据枚举编码解析人脸类型。
         *
         * @param code 人脸类型编码
         * @return 匹配的人脸类型；编码为空或未匹配时返回 {@code null}
         */
        public static FaceType fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (FaceType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }
    }
}
