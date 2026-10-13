package com.apex.scaffold.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户聚合根(示例)
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class User {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
