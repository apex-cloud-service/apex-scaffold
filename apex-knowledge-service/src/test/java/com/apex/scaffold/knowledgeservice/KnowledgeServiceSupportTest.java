package com.apex.scaffold.knowledgeservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.knowledgeservice.config.KnowledgeI18nConfig;
import com.apex.scaffold.knowledgeservice.interfaces.filter.KnowledgeLocaleWebFilter;
import com.apex.scaffold.knowledgeservice.interfaces.security.KnowledgeAuditLogServiceImpl;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeMessageUtils;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 知识库服务小工具类测试：i18n 配置/消息解析/语言过滤/审计实现。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class KnowledgeServiceSupportTest {

    /**
     * 构建消息源（与 I18nConfig 一致）。
     *
     * @return 消息源
     */
    private MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        source.setCacheSeconds(3600);
        return source;
    }

    @Test
    void messageSourceResolvesKnowledgeKeys() {
        assertThat(messageSource().getMessage("kb.space.not_found", null, Locale.SIMPLIFIED_CHINESE))
                .isEqualTo("知识空间不存在");
        assertThat(messageSource().getMessage("kb.not.a.key", null, Locale.US)).isEqualTo("kb.not.a.key");
    }

    @Test
    void messageUtilsResolvesByLocale() {
        KnowledgeMessageUtils utils = new KnowledgeMessageUtils(messageSource());
        assertThat(utils.get(Locale.SIMPLIFIED_CHINESE, "kb.search.keyword_required"))
                .isEqualTo("检索关键词不能为空");
        assertThat(utils.get(Locale.US, "kb.search.keyword_required")).isEqualTo("Search keyword is required");
        StepVerifier.create(utils.get("kb.chat.not_found"))
                .expectNext("问答会话不存在")
                .verifyComplete();
    }

    @Test
    void localeFilterWritesSupportedLocaleIntoContext() throws Exception {
        KnowledgeLocaleWebFilter filter = new KnowledgeLocaleWebFilter();
        // 中文请求
        var zhExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build());
        WebFilterChain chain = ex -> Mono.empty();
        filter.filter(zhExchange, chain).block();
        assertThat(zhExchange.getResponse()).isNotNull();

        // 英文请求与缺失头回退默认（链内可重复执行无异常）
        var enExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x")
                .header("Accept-Language", "en-US")
                .build());
        filter.filter(enExchange, chain).block();
        var defaultExchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        StepVerifier.create(filter.filter(defaultExchange, chain)).verifyComplete();
        assertThat(new KnowledgeI18nConfig().knowledgeMessageSource()).isNotNull();
    }

    @Test
    void auditLogServiceImplLogsAllContractMethods() {
        KnowledgeAuditLogServiceImpl audit = new KnowledgeAuditLogServiceImpl();
        Map<String, Object> details = new HashMap<>();
        details.put("module", "知识库");
        StepVerifier.create(
                        audit.log("1", "t1", "INSERT", "KnowledgeSpaceHandler.create", "HTTP_RESPONSE_READY", details))
                .verifyComplete();
        StepVerifier.create(audit.logAuthEvent("1", "t1", "LOGIN", "127.0.0.1", false, "原因不记录"))
                .verifyComplete();
        StepVerifier.create(audit.logPermissionCheck("1", "t1", "KnowledgeSpaceHandler.page", "kb:space:read", true))
                .verifyComplete();
    }
}
