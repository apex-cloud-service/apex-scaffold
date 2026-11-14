package com.apex.scaffold.knowledge.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 知识检索请求。
 *
 * @param keyword 检索关键词
 * @param spaceIds 限定空间（可空表示租户内全部正常空间）
 * @param page 页码（从 1 开始）
 * @param size 页大小
 * @author Apex Team
 * @since 1.3.2
 */
public record KbSearchRequest(
        @NotBlank(message = "检索关键词不能为空") String keyword, List<Long> spaceIds, Integer page, Integer size) {}
