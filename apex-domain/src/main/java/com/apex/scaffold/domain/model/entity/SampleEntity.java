package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 采样聚合根实体
 *
 * <p>
 * 表示用户照片的采样数据。
 * 用于对局展示和算法分析。
 * </p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see PhotoEntity
 * @see BaseEntity
 */
@Getter
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

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets photoId. For persistence/construction use only. */
    public void setPhotoId(Long value) {
        this.photoId = value;
    }

    /** Sets bucketName. For persistence/construction use only. */
    public void setBucketName(String value) {
        this.bucketName = value;
    }

    /** Sets fileName. For persistence/construction use only. */
    public void setFileName(String value) {
        this.fileName = value;
    }

    /** Sets mode. For persistence/construction use only. */
    public void setMode(String value) {
        this.mode = value;
    }

    /** Sets faceType. For persistence/construction use only. */
    public void setFaceType(String value) {
        this.faceType = value;
    }

    /** Sets cropX. For persistence/construction use only. */
    public void setCropX(Integer value) {
        this.cropX = value;
    }

    /** Sets cropY. For persistence/construction use only. */
    public void setCropY(Integer value) {
        this.cropY = value;
    }

    /** Sets cropW. For persistence/construction use only. */
    public void setCropW(Integer value) {
        this.cropW = value;
    }

    /** Sets cropH. For persistence/construction use only. */
    public void setCropH(Integer value) {
        this.cropH = value;
    }

    /** Sets pitch. For persistence/construction use only. */
    public void setPitch(Double value) {
        this.pitch = value;
    }

    /** Sets yaw. For persistence/construction use only. */
    public void setYaw(Double value) {
        this.yaw = value;
    }

    /** Sets roll. For persistence/construction use only. */
    public void setRoll(Double value) {
        this.roll = value;
    }

    /** Sets faceRatio. For persistence/construction use only. */
    public void setFaceRatio(Double value) {
        this.faceRatio = value;
    }

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
         * 获取{@code code}。
         * @return 字符串结果
         */
        public String getCode() {
            return code;
        }

        /**
         * 执行 {@code fromCode} 操作。
         * @param code 参数 {@code code}
         * @return 操作结果
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
         * 获取{@code code}。
         * @return 字符串结果
         */
        public String getCode() {
            return code;
        }

        /**
         * 执行 {@code fromCode} 操作。
         * @param code 参数 {@code code}
         * @return 操作结果
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
