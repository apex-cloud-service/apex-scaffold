package com.apex.scaffold.interfaces.controller;

import com.apex.scaffold.common.annotation.ApiVersion;
import com.apex.scaffold.common.result.Result;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理API V2版本
 *
 * <p>V2版本增强了功能,返回更丰富的用户信息。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/v2/users")
@ApiVersion(value = "v2", deprecated = false)
public class UserControllerV2 {

    /**
     * 获取用户列表(V2)
     * <p>返回完整版用户信息,包含扩展字段
     */
    @GetMapping
    public Result<Object> listUsers(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        // V2版本: 支持分页,返回完整字段
        return Result.success();
    }

    /**
     * 获取用户详情(V2)
     * <p>包含用户的角色、权限等详细信息
     */
    @GetMapping("/{id}")
    public Result<Object> getUser(@PathVariable Long id) {
        return Result.success();
    }

    /**
     * 创建用户(V2)
     * <p>支持更多字段验证
     */
    @PostMapping
    public Result<Object> createUser(@RequestBody Object user) {
        return Result.success();
    }

    /**
     * 批量删除用户(V2新增)
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteUsers(@RequestBody java.util.List<Long> ids) {
        return Result.success();
    }
}
