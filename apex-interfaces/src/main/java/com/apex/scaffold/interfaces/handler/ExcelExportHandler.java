package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.UserExportDTO;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.infrastructure.excel.ExcelExportService;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Excel 导出处理器（函数式端点）。
 *
 * <p>提供通用 Excel 导出示例（C5）：导出用户列表为 xlsx 附件，
 * 演示 {@link ExcelExportService} 的接入方式，业务方可按同模式扩展
 * 其他实体的导出。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/export/users - 导出用户列表（xlsx 附件）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see ExcelExportService
 */
@Component
@Tag(name = "后台-数据导出", description = "Excel 导出（EasyExcel）")
public class ExcelExportHandler extends BaseHandler {

    /** Excel 导出服务 */
    private final ExcelExportService excelExportService;

    /** 用户应用服务 */
    private final UserService userService;

    /**
     * 创建 Excel 导出处理器。
     *
     * @param excelExportService Excel 导出服务
     * @param userService 用户应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public ExcelExportHandler(
            ExcelExportService excelExportService,
            UserService userService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.excelExportService = excelExportService;
        this.userService = userService;
    }

    /**
     * 导出用户列表（xlsx 附件）。
     *
     * <p>拉取用户列表（演示取前 1000 条），转换为 {@link UserExportDTO}
     * 后由 EasyExcel 写为 xlsx 字节流，以附件形式返回。</p>
     *
     * @param request HTTP 请求
     * @return xlsx 附件响应（Content-Disposition: attachment）
     */
    @RequirePermission("system:user:list")
    @Operation(operationId = "adminExportUsers", summary = "导出用户列表（xlsx）")
    @OperationLog(module = "数据导出", type = OperationLog.OperationType.SELECT, description = "导出用户列表")
    public Mono<ServerResponse> exportUsers(ServerRequest request) {
        return userService.findPage(1, 100, "create_time", false).flatMap(page -> {
            List<UserExportDTO> rows =
                    page.getList().stream().map(this::toExportDto).collect(Collectors.toList());
            byte[] bytes = excelExportService.export("用户列表", UserExportDTO.class, rows);
            String filename =
                    URLEncoder.encode("用户列表", StandardCharsets.UTF_8) + "-" + System.currentTimeMillis() + ".xlsx";
            return ServerResponse.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .bodyValue(bytes);
        });
    }

    /** 用户实体 → 导出 DTO 转换。 */
    private UserExportDTO toExportDto(UserEntity user) {
        UserExportDTO dto = new UserExportDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickName(user.getNickName());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus());
        dto.setLastLoginTime(user.getLastLoginTime());
        return dto;
    }
}
