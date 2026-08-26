package com.apex.example.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 示例用户实体类
 *
 * <p>展示如何定义符合脚手架规范的实体类:
 * <ul>
 *   <li>使用Lombok简化代码</li>
 *   <li>使用MyBatis-Plus注解配置数据库映射</li>
 *   <li>实现Serializable接口支持序列化</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("example_user")
public class ExampleUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID(主键,使用雪花算法自动生成)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户名(唯一)
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 状态(0-禁用, 1-启用)
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识(0-未删除, 1-已删除)
     */
    private Integer deleted;
}
