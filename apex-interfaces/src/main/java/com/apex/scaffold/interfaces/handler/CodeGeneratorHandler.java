package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.CodeGenerationService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 代码生成器执行处理器（函数式端点）
 *
 * <p>提供代码生成执行的 RESTful API，根据预先配置的生成模板和数据源，
 * 自动生成符合 DDD 分层架构的 Java 代码，包括 Entity、Repository、Service、
 * Handler 等各层代码文件。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>执行代码生成：根据生成配置 ID 执行代码生成任务</li>
 *   <li>生成 DDD 分层代码：自动生成领域层、应用层、基础设施层和接口层代码</li>
 *   <li>记录生成历史：保存生成记录，便于追溯和回滚</li>
 *   <li>支持多种数据库：MySQL、PostgreSQL 等主流数据库</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>基于已有数据库表结构快速生成业务代码</li>
 *   <li>减少重复的样板代码编写工作</li>
 *   <li>确保代码风格和架构规范的一致性</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>生成前需先配置数据源和生成模板</li>
 *   <li>生成的代码仅作为基础框架，仍需手工完善业务逻辑</li>
 *   <li>生成操作需要 system:generator:generate 权限</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see com.apex.scaffold.application.service.CodeGenerationService
 */
@Component
@Tag(name = "后台-代码生成器-执行", description = "代码生成执行")
public class CodeGeneratorHandler extends BaseHandler {

    private final CodeGenerationService codeGenerationService;

    /**
     * 创建代码生成执行处理器。
     *
     * @param codeGenerationService 代码生成服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public CodeGeneratorHandler(
            CodeGenerationService codeGenerationService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.codeGenerationService = codeGenerationService;
    }

    /**
     * 执行代码生成
     *
     * <p>根据指定的生成配置 ID 执行代码生成任务，生成 DDD 分层代码文件并记录生成历史。</p>
     *
     * @param request 服务器请求对象，包含生成配置 ID 路径参数
     * @return 包含代码生成结果的响应对象
     */
    @RequirePermission("system:generator:generate")
    @Operation(
            operationId = "adminGenerateCode",
            summary = "执行代码生成",
            description = "根据指定的生成配置ID执行代码生成任务，生成 DDD 分层代码文件并记录生成历史")
    @Parameter(
            name = "id",
            description = "生成配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "代码生成执行请求参数（可选，用于覆盖配置中的部分参数）",
            required = false,
            content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "代码生成成功，返回生成结果和文件列表"),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:generate 权限"),
        @ApiResponse(responseCode = "404", description = "生成配置不存在"),
        @ApiResponse(responseCode = "500", description = "代码生成失败，返回错误信息")
    })
    @OperationLog(module = "代码生成器-执行", type = OperationLog.OperationType.OTHER, description = "执行代码生成")
    public Mono<ServerResponse> generate(ServerRequest request) {
        Long configId = requirePositiveId(request);
        return codeGenerationService.generate(configId).flatMap(this::buildSuccess);
    }
}
