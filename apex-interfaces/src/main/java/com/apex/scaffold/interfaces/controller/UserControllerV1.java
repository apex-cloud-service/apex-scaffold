package com.apex.scaffold.interfaces.controller;

import com.apex.scaffold.common.annotation.ApiVersion;
import com.apex.scaffold.common.result.Result;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理API V1版本
 *
 * <p>演示API版本管理的用法。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/v1/users")
@ApiVersion("v1")
public class UserControllerV1 {

    /**
     * 获取用户列表(V1)
     * <p>返回简化版用户信息
     */
    @GetMapping
    public Result<Object> listUsers() {
        // V1版本: 返回基础字段
        return Result.success();
    }

    /**
     * 获取用户详情(V1)
     */
    @GetMapping("/{id}")
    public Result<Object> getUser(@PathVariable Long id) {
        return Result.success();
    }

    /**
     * 创建用户(V1)
     */
    @PostMapping
    public Result<Object> createUser(@RequestBody Object user) {
        return Result.success();
    }
}
