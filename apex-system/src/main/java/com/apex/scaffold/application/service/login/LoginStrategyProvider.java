package com.apex.scaffold.application.service.login;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 登录策略提供者
 *
 * <p>策略模式的核心上下文类，负责管理和分发登录策略实例。
 * 通过 Spring 注入所有 {@link LoginStrategy} 实现，在初始化时
 * 遍历策略列表，调用每个策略的 {@link LoginStrategy#getLoginType()} 自动完成注册，
 * 内部使用 {@link EnumMap} 维护登录类型到策略实现的映射关系。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>采用 List 注入 + 自注册机制，新增策略时无需修改 Provider 代码，符合开闭原则</li>
 *   <li>通过 {@link PostConstruct} 在初始化时自动注册所有策略实现</li>
 *   <li>默认返回策略列表中第一个实现（适配向后兼容场景）</li>
 *   <li>不支持的登录类型抛出 {@link BusinessException}（错误码 1006）</li>
 * </ul>
 *
 * <h2>自注册流程</h2>
 * <ol>
 *   <li>Spring 容器通过 List 注入所有 {@link LoginStrategy} 实现类</li>
 *   <li>{@link #init()} 方法遍历 strategies 列表，调用 {@link LoginStrategy#getLoginType()} 完成注册</li>
 *   <li>运行时通过 {@link #getStrategy(String)} 根据登录类型获取策略</li>
 * </ol>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see LoginType
 * @see LoginStrategy
 * @see WeChatCodeLoginStrategy
 * @see WeChatPhoneLoginStrategy
 * @see SmsLoginStrategy
 * @see ChuanglanPhoneLoginStrategy
 * @see ChuanglanSmsLoginStrategy
 */
@Component
public class LoginStrategyProvider {

    /** 所有登录策略实现的列表，由 Spring 自动注入 */
    private final List<LoginStrategy> strategies;

    /** 登录类型到策略实现的映射表，由 {@link #init()} 自动构建 */
    private final Map<LoginType, LoginStrategy> strategyMap = new EnumMap<>(LoginType.class);

    /**
     * 构造登录策略提供者
     *
     * @param strategies Spring 注入的所有 {@link LoginStrategy} 实现列表
     */
    public LoginStrategyProvider(List<LoginStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 初始化策略映射表
     *
     * <p>遍历 Spring 注入的所有 {@link LoginStrategy} 实现，
     * 通过 {@link LoginStrategy#getLoginType()} 自动获取对应的登录类型并注册到映射表中。
     * 新增登录策略时只需实现 {@link LoginStrategy#getLoginType()} 方法即可自动注册，
     * 无需修改本方法。
     */
    @PostConstruct
    public void init() {
        for (LoginStrategy strategy : strategies) {
            LoginType loginType = strategy.getLoginType();
            strategyMap.put(loginType, strategy);
        }
    }

    /**
     * 根据登录类型名称获取对应的策略实现
     *
     * <p>支持传入 {@code null} 或空字符串，此时返回策略列表中第一个实现作为默认策略。
     * 登录类型名称不区分大小写，会自动转换为大写进行匹配。
     *
     * @param loginTypeName 登录类型名称（不区分大小写），如 "WX_CODE"、"wx_code"
     * @return 对应的登录策略实现
     * @throws BusinessException 如果登录类型名称无效或不被支持，抛出错误码 1006
     */
    public LoginStrategy getStrategy(String loginTypeName) {
        if (loginTypeName == null || loginTypeName.isBlank()) {
            if (strategies.isEmpty()) {
                throw new BusinessException(ResultCode.UNSUPPORTED_LOGIN_TYPE, "没有可用的登录策略");
            }
            return strategies.get(0);
        }
        try {
            LoginType loginType = LoginType.valueOf(loginTypeName.toUpperCase());
            LoginStrategy strategy = strategyMap.get(loginType);
            if (strategy == null) {
                throw new BusinessException(ResultCode.UNSUPPORTED_LOGIN_TYPE, "不支持的登录类型: " + loginTypeName);
            }
            return strategy;
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.UNSUPPORTED_LOGIN_TYPE, "不支持的登录类型: " + loginTypeName);
        }
    }
}
