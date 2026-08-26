package com.apex.scaffold.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户聚合根(示例)
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
