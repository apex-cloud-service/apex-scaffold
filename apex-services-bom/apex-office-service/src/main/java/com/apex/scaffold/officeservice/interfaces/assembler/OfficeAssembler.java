package com.apex.scaffold.officeservice.interfaces.assembler;

import com.apex.scaffold.office.application.OfficeAppCommand;
import com.apex.scaffold.office.domain.OfficeIdentityBinding;
import com.apex.scaffold.office.domain.OfficePlatformApp;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeBindingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 办公平台接口层对象转换器。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficeAssembler {
    /**
     * 将请求对象转换为命令对象。
     *
     * @param request 应用配置请求
     * @return 应用配置命令
     */
    OfficeAppCommand toCommand(OfficeAppRequest request);

    /**
     * 将领域应用对象转换为响应对象。
     *
     * @param app 领域应用对象
     * @return 应用配置响应
     */
    @Mapping(target = "platform", expression = "java(app.getPlatform().name())")
    OfficeAppResponse toAppResponse(OfficePlatformApp app);

    /**
     * 将领域绑定对象转换为响应对象。
     *
     * @param binding 领域绑定对象
     * @return 绑定响应
     */
    @Mapping(target = "platform", expression = "java(binding.getPlatform().name())")
    OfficeBindingResponse toBindingResponse(OfficeIdentityBinding binding);
}
