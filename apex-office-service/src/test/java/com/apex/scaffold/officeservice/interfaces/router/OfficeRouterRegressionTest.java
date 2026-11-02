package com.apex.scaffold.officeservice.interfaces.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.officeservice.interfaces.handler.InternalNotifyHandler;
import com.apex.scaffold.officeservice.interfaces.handler.OfficeAdminHandler;
import com.apex.scaffold.officeservice.interfaces.handler.OfficeHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeApprovalHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeCalendarHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeDocumentHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeMessageHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeNotifyHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeOrganizationHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeSyncWizardHandler;
import com.apex.scaffold.officeservice.interfaces.off.handler.MessageHandler;
import com.apex.scaffold.officeservice.interfaces.off.handler.MessageTemplateHandler;
import com.apex.scaffold.officeservice.interfaces.off.handler.OrgSyncHandler;
import com.apex.scaffold.officeservice.interfaces.off.router.MessageRouter;
import com.apex.scaffold.officeservice.interfaces.off.router.OrgSyncRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeApprovalRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeCalendarRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeDocumentRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeMessageRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeNotifyRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeOrganizationRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeSyncWizardRouter;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公服务全接口路由回归测试.
 *
 * <p>以「无容器」方式加载 {@code apex-office-service} 暴露的全部 {@link RouterFunction} Bean 工厂方法，
 * 通过 {@link RouterFunctions.Visitor} 访问器遍历路由树，收集对外暴露的「HTTP 方法 + 绝对路径」端点集合，
 * 并与预期集合精确比对。路由依赖的 Handler 一律使用 Mockito 桩对象填充，仅用于构建路由树，不触发任何业务逻辑。
 *
 * <h2>覆盖的九个路由 Bean</h2>
 * <ul>
 *   <li>{@link OfficeRouter}（统一认证与状态）</li>
 *   <li>{@link OfficeAdminRouter}（管理端，SpringdocRouteBuilder）</li>
 *   <li>{@link OfficeApprovalRouter}（审批）</li>
 *   <li>{@link OfficeCalendarRouter}（日历事件）</li>
 *   <li>{@link OfficeOrganizationRouter}（组织同步）</li>
 *   <li>{@link OfficeDocumentRouter}（文档操作）</li>
 *   <li>{@link OfficeMessageRouter}（办公消息）</li>
 *   <li>{@link MessageRouter}（站内消息 + 消息模板）</li>
 *   <li>{@link OrgSyncRouter}（组织架构同步，SpringdocRouteBuilder）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@DisplayName("办公服务全接口路由回归测试")
class OfficeRouterRegressionTest {

    /** HTTP 方法与路径提取表达式。 */
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\b");

    /** 路由谓词中的相对路径提取表达式。 */
    private static final Pattern PATH_PATTERN = Pattern.compile("&& (/[^)]+)\\)");

    /** 统一认证与状态端点。 */
    private static final Set<Endpoint> OFFICE_ENDPOINTS = Set.of(
            new Endpoint("GET", "/api/v1/office/status"),
            new Endpoint("GET", "/api/v1/office/login"),
            new Endpoint("GET", "/api/v1/office/callback"));

    /** 管理端端点。 */
    private static final Set<Endpoint> OFFICE_ADMIN_ENDPOINTS = Set.of(
            new Endpoint("GET", "/admin-api/v1/office/apps"),
            new Endpoint("GET", "/admin-api/v1/office/apps/{platform}"),
            new Endpoint("POST", "/admin-api/v1/office/apps/{platform}"),
            new Endpoint("PUT", "/admin-api/v1/office/apps/{platform}"),
            new Endpoint("DELETE", "/admin-api/v1/office/apps/{platform}"),
            new Endpoint("GET", "/admin-api/v1/office/bindings"),
            new Endpoint("GET", "/admin-api/v1/office/bindings/page"),
            new Endpoint("POST", "/admin-api/v1/office/bindings/{id}/review"),
            new Endpoint("DELETE", "/admin-api/v1/office/bindings/{id}/unbind"));

    /** 审批端点。 */
    private static final Set<Endpoint> APPROVAL_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/office/approvals"),
            new Endpoint("GET", "/api/office/approvals/{processInstanceId}"),
            new Endpoint("POST", "/api/office/approvals/{processInstanceId}/approve"),
            new Endpoint("DELETE", "/api/office/approvals/{processInstanceId}"),
            new Endpoint("GET", "/api/office/approvals/pending"),
            new Endpoint("GET", "/api/office/approvals/completed"),
            new Endpoint("GET", "/api/office/approvals/initiated"));

    /** 日历事件端点。 */
    private static final Set<Endpoint> CALENDAR_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/office/calendar/events"),
            new Endpoint("GET", "/api/office/calendar/events/{eventId}"),
            new Endpoint("PUT", "/api/office/calendar/events/{eventId}"),
            new Endpoint("DELETE", "/api/office/calendar/events/{eventId}"),
            new Endpoint("GET", "/api/office/calendar/events/range"),
            new Endpoint("GET", "/api/office/calendar/events/my"),
            new Endpoint("GET", "/api/office/calendar/events/upcoming"));

    /** 组织同步（业务侧）端点。 */
    private static final Set<Endpoint> ORGANIZATION_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/v1/office/organization/sync"),
            new Endpoint("GET", "/api/v1/office/organization/mappings/department/{platform}/{externalOrgId}"),
            new Endpoint("GET", "/api/v1/office/organization/mappings/member/{platform}/{externalUserId}"),
            new Endpoint("GET", "/api/v1/office/organization/mappings"),
            new Endpoint("GET", "/api/v1/office/organization/sync/tasks"));

    /** 文档操作端点。 */
    private static final Set<Endpoint> DOCUMENT_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/office/documents"),
            new Endpoint("POST", "/api/office/documents/upload"),
            new Endpoint("GET", "/api/office/documents/{documentId}"),
            new Endpoint("GET", "/api/office/documents/folder"),
            new Endpoint("GET", "/api/office/documents/my"),
            new Endpoint("GET", "/api/office/documents/{documentId}/download"),
            new Endpoint("DELETE", "/api/office/documents/{documentId}"),
            new Endpoint("PUT", "/api/office/documents/{documentId}/move"),
            new Endpoint("POST", "/api/office/documents/{documentId}/copy"));

    /** 办公消息端点。 */
    private static final Set<Endpoint> OFFICE_MESSAGE_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/office/messages"),
            new Endpoint("GET", "/api/office/messages/{messageId}"),
            new Endpoint("POST", "/api/office/messages/{messageId}/resend"),
            new Endpoint("DELETE", "/api/office/messages/{messageId}"));

    /** 站内消息端点。 */
    private static final Set<Endpoint> INTERNAL_MESSAGE_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/v1/messages"),
            new Endpoint("GET", "/api/v1/messages/inbox"),
            new Endpoint("GET", "/api/v1/messages/inbox/cursor"),
            new Endpoint("GET", "/api/v1/messages/sent"),
            new Endpoint("GET", "/api/v1/messages/{messageId}"),
            new Endpoint("GET", "/api/v1/messages/{messageId}/deliveries"),
            new Endpoint("PUT", "/api/v1/messages/{messageId}/read"),
            new Endpoint("PUT", "/api/v1/messages/read-all"),
            new Endpoint("GET", "/api/v1/messages/unread/count"));

    /** 消息模板端点。 */
    private static final Set<Endpoint> MESSAGE_TEMPLATE_ENDPOINTS = Set.of(
            new Endpoint("GET", "/admin-api/v1/message-templates"),
            new Endpoint("POST", "/admin-api/v1/message-templates"),
            new Endpoint("GET", "/admin-api/v1/message-templates/{templateCode}"),
            new Endpoint("PUT", "/admin-api/v1/message-templates/{templateId}"),
            new Endpoint("DELETE", "/admin-api/v1/message-templates/{templateId}"));

    /** 组织架构同步（管理侧）端点。 */
    private static final Set<Endpoint> ORG_SYNC_ENDPOINTS = Set.of(
            new Endpoint("GET", "/admin-api/v1/org/departments"),
            new Endpoint("GET", "/admin-api/v1/org/departments/{id}"),
            new Endpoint("POST", "/admin-api/v1/org/departments"),
            new Endpoint("PUT", "/admin-api/v1/org/departments/{id}"),
            new Endpoint("DELETE", "/admin-api/v1/org/departments/{id}"),
            new Endpoint("GET", "/admin-api/v1/org/employees"),
            new Endpoint("GET", "/admin-api/v1/org/employees/{id}"),
            new Endpoint("GET", "/admin-api/v1/org/sync-history"));

    /** 组织同步向导（v2）端点。 */
    private static final Set<Endpoint> SYNC_WIZARD_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/v2/office/sync/preflight"),
            new Endpoint("POST", "/api/v2/office/sync/preview"),
            new Endpoint("POST", "/api/v2/office/sync/tasks"),
            new Endpoint("GET", "/api/v2/office/sync/tasks"),
            new Endpoint("GET", "/api/v2/office/sync/tasks/{taskId}"),
            new Endpoint("POST", "/api/v2/office/sync/tasks/{taskId}/retry"));

    /** 统一通知管理端点。 */
    private static final Set<Endpoint> NOTIFY_ENDPOINTS = Set.of(
            new Endpoint("POST", "/api/v2/office/notify/templates"),
            new Endpoint("GET", "/api/v2/office/notify/templates"),
            new Endpoint("GET", "/api/v2/office/notify/requests"),
            new Endpoint("GET", "/api/v2/office/notify/templates/{templateId}"),
            new Endpoint("PUT", "/api/v2/office/notify/templates/{templateId}"),
            new Endpoint("DELETE", "/api/v2/office/notify/templates/{templateId}"));

    /** 统一通知内部契约端点。 */
    private static final Set<Endpoint> INTERNAL_NOTIFY_ENDPOINTS = Set.of(new Endpoint("POST", "/internal/v1/notify"));

    /** 验证统一认证与状态接口正确注册。 */
    @Test
    @DisplayName("统一认证与状态接口全部正确注册")
    void shouldExposeOfficeEndpoints() {
        assertThat(collectEndpoints()).containsAll(OFFICE_ENDPOINTS);
    }

    /** 验证管理端接口正确注册。 */
    @Test
    @DisplayName("管理端接口全部正确注册")
    void shouldExposeOfficeAdminEndpoints() {
        assertThat(collectEndpoints()).containsAll(OFFICE_ADMIN_ENDPOINTS);
    }

    /** 验证审批接口正确注册。 */
    @Test
    @DisplayName("审批接口全部正确注册")
    void shouldExposeApprovalEndpoints() {
        assertThat(collectEndpoints()).containsAll(APPROVAL_ENDPOINTS);
    }

    /** 验证日历事件接口正确注册。 */
    @Test
    @DisplayName("日历事件接口全部正确注册")
    void shouldExposeCalendarEndpoints() {
        assertThat(collectEndpoints()).containsAll(CALENDAR_ENDPOINTS);
    }

    /** 验证业务侧组织同步接口正确注册。 */
    @Test
    @DisplayName("业务侧组织同步接口全部正确注册")
    void shouldExposeOrganizationEndpoints() {
        assertThat(collectEndpoints()).containsAll(ORGANIZATION_ENDPOINTS);
    }

    /** 验证文档操作接口正确注册。 */
    @Test
    @DisplayName("文档操作接口全部正确注册")
    void shouldExposeDocumentEndpoints() {
        assertThat(collectEndpoints()).containsAll(DOCUMENT_ENDPOINTS);
    }

    /** 验证办公消息接口正确注册。 */
    @Test
    @DisplayName("办公消息接口全部正确注册")
    void shouldExposeOfficeMessageEndpoints() {
        assertThat(collectEndpoints()).containsAll(OFFICE_MESSAGE_ENDPOINTS);
    }

    /** 验证站内消息与消息模板接口正确注册。 */
    @Test
    @DisplayName("站内消息与消息模板接口全部正确注册")
    void shouldExposeInternalMessageEndpoints() {
        assertThat(collectEndpoints()).containsAll(INTERNAL_MESSAGE_ENDPOINTS).containsAll(MESSAGE_TEMPLATE_ENDPOINTS);
    }

    /** 验证管理侧组织架构同步接口正确注册。 */
    @Test
    @DisplayName("管理侧组织架构同步接口全部正确注册")
    void shouldExposeOrgSyncEndpoints() {
        assertThat(collectEndpoints()).containsAll(ORG_SYNC_ENDPOINTS);
    }

    /** 验证组织同步向导（v2）接口正确注册。 */
    @Test
    @DisplayName("组织同步向导接口全部正确注册")
    void shouldExposeSyncWizardEndpoints() {
        assertThat(collectEndpoints()).containsAll(SYNC_WIZARD_ENDPOINTS);
    }

    /** 验证统一通知管理接口正确注册。 */
    @Test
    @DisplayName("统一通知管理接口全部正确注册")
    void shouldExposeNotifyEndpoints() {
        assertThat(collectEndpoints()).containsAll(NOTIFY_ENDPOINTS);
    }

    /** 验证统一通知内部契约接口正确注册。 */
    @Test
    @DisplayName("统一通知内部契约接口全部正确注册")
    void shouldExposeInternalNotifyEndpoints() {
        assertThat(collectEndpoints()).containsAll(INTERNAL_NOTIFY_ENDPOINTS);
    }

    /** 验证对外暴露的端点集合与预期完全一致（数量、路径、方法一一对应）。 */
    @Test
    @DisplayName("对外暴露端点集合与预期精确一致（共 79 个）")
    void shouldExposeExactlyExpectedEndpoints() {
        Set<Endpoint> expected = new LinkedHashSet<>();
        expected.addAll(OFFICE_ENDPOINTS);
        expected.addAll(OFFICE_ADMIN_ENDPOINTS);
        expected.addAll(APPROVAL_ENDPOINTS);
        expected.addAll(CALENDAR_ENDPOINTS);
        expected.addAll(ORGANIZATION_ENDPOINTS);
        expected.addAll(DOCUMENT_ENDPOINTS);
        expected.addAll(OFFICE_MESSAGE_ENDPOINTS);
        expected.addAll(INTERNAL_MESSAGE_ENDPOINTS);
        expected.addAll(MESSAGE_TEMPLATE_ENDPOINTS);
        expected.addAll(ORG_SYNC_ENDPOINTS);
        expected.addAll(SYNC_WIZARD_ENDPOINTS);
        expected.addAll(NOTIFY_ENDPOINTS);
        expected.addAll(INTERNAL_NOTIFY_ENDPOINTS);

        Set<Endpoint> actual = collectEndpoints();

        assertThat(actual).as("办公对外接口总数").hasSize(expected.size());
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * 收集全部路由 Bean 暴露的端点集合。
     *
     * <p>对九个路由类逐一实例化（构造注入型用 Mockito 桩对象填充），反射调用全部
     * {@code @Bean} 路由工厂方法，并使用 {@link RouteCollector} 遍历路由树收集端点。</p>
     *
     * @return 已注册的「HTTP 方法 + 绝对路径」端点集合
     */
    private Set<Endpoint> collectEndpoints() {
        Set<Endpoint> endpoints = new LinkedHashSet<>();

        OfficeRouter officeRouter = new OfficeRouter();
        collect(endpoints, officeRouter, "officeRoutes", OfficeHandler.class, Mockito.mock(OfficeHandler.class));

        OfficeAdminRouter officeAdminRouter = new OfficeAdminRouter();
        collect(
                endpoints,
                officeAdminRouter,
                "officeAdminRoutes",
                OfficeAdminHandler.class,
                Mockito.mock(OfficeAdminHandler.class));

        OfficeApprovalRouter approvalRouter = new OfficeApprovalRouter();
        collect(
                endpoints,
                approvalRouter,
                "approvalRoutes",
                OfficeApprovalHandler.class,
                Mockito.mock(OfficeApprovalHandler.class));

        OfficeCalendarRouter calendarRouter = new OfficeCalendarRouter(Mockito.mock(OfficeCalendarHandler.class));
        collect(endpoints, calendarRouter, "officeCalendarRoutes", null, null);

        OfficeOrganizationRouter organizationRouter =
                new OfficeOrganizationRouter(Mockito.mock(OfficeOrganizationHandler.class));
        collect(endpoints, organizationRouter, "officeOrganizationRoutes", null, null);

        OfficeDocumentRouter documentRouter = new OfficeDocumentRouter(Mockito.mock(OfficeDocumentHandler.class));
        collect(endpoints, documentRouter, "officeDocumentRoutes", null, null);

        OfficeMessageRouter messageRouter = new OfficeMessageRouter(Mockito.mock(OfficeMessageHandler.class));
        collect(endpoints, messageRouter, "messageRoutes", null, null);

        MessageRouter internalMessageRouter = new MessageRouter();
        collect(
                endpoints,
                internalMessageRouter,
                "messageRoutes",
                MessageHandler.class,
                Mockito.mock(MessageHandler.class));
        collect(
                endpoints,
                internalMessageRouter,
                "messageTemplateRoutes",
                MessageTemplateHandler.class,
                Mockito.mock(MessageTemplateHandler.class));

        OrgSyncRouter orgSyncRouter = new OrgSyncRouter();
        collect(endpoints, orgSyncRouter, "orgSyncRoutes", OrgSyncHandler.class, Mockito.mock(OrgSyncHandler.class));

        OfficeSyncWizardRouter syncWizardRouter =
                new OfficeSyncWizardRouter(Mockito.mock(OfficeSyncWizardHandler.class));
        collect(endpoints, syncWizardRouter, "officeSyncWizardRoutes", null, null);

        OfficeNotifyRouter notifyRouter = new OfficeNotifyRouter(Mockito.mock(OfficeNotifyHandler.class));
        collect(endpoints, notifyRouter, "officeNotifyRoutes", null, null);

        InternalNotifyRouter internalNotifyRouter = new InternalNotifyRouter();
        collect(
                endpoints,
                internalNotifyRouter,
                "internalNotifyRoutes",
                InternalNotifyHandler.class,
                Mockito.mock(InternalNotifyHandler.class));

        return endpoints;
    }

    /**
     * 反射调用单个路由工厂方法并收集其端点。
     *
     * @param endpoints    端点集合汇总容器
     * @param router       路由 Bean 实例
     * @param factoryName  工厂方法名称
     * @param paramType    工厂方法参数类型（无参方法传 null）
     * @param arg          工厂方法实参（无参方法传 null）
     */
    @SuppressWarnings("unchecked")
    private void collect(Set<Endpoint> endpoints, Object router, String factoryName, Class<?> paramType, Object arg) {
        try {
            Method factory;
            Object result;
            if (paramType == null) {
                factory = router.getClass().getMethod(factoryName);
                result = factory.invoke(router);
            } else {
                factory = router.getClass().getMethod(factoryName, paramType);
                result = factory.invoke(router, arg);
            }
            RouterFunction<ServerResponse> routes = (RouterFunction<ServerResponse>) result;
            RouteCollector collector = new RouteCollector();
            routes.accept(collector);
            endpoints.addAll(collector.endpoints);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法构建路由 Bean: " + router.getClass().getSimpleName() + "#" + factoryName, e);
        }
    }

    /** HTTP 方法与绝对路径组成的接口身份。 */
    private record Endpoint(String method, String path) {}

    /** 从 Spring RouterFunction 访问器回调中收集真实端点。 */
    private static final class RouteCollector implements RouterFunctions.Visitor {

        /** 当前嵌套路由路径栈。 */
        private final ArrayDeque<String> nesting = new ArrayDeque<>();

        /** 已识别的业务端点。 */
        private final Set<Endpoint> endpoints = new LinkedHashSet<>();

        /** {@inheritDoc} */
        @Override
        public void startNested(RequestPredicate predicate) {
            nesting.addLast(predicate.toString());
        }

        /** {@inheritDoc} */
        @Override
        public void endNested(RequestPredicate predicate) {
            nesting.removeLast();
        }

        /** {@inheritDoc} */
        @Override
        public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
            String predicateText = predicate.toString();
            Matcher method = METHOD_PATTERN.matcher(predicateText);
            Matcher path = PATH_PATTERN.matcher(predicateText);
            assertThat(method.find()).as(predicateText).isTrue();
            String relativePath = path.find() ? path.group(1) : extractTopLevelPath(predicateText);
            String absolutePath = String.join("", nesting) + relativePath;
            endpoints.add(new Endpoint(method.group(1), absolutePath));
        }

        /**
         * 从顶层非嵌套路由谓词（如 {@code "GET && /"}）中提取路径。
         *
         * @param predicateText 路由谓词字符串
         * @return 路径部分；无法识别时返回空串
         */
        private static String extractTopLevelPath(String predicateText) {
            int andIndex = predicateText.indexOf("&&");
            if (andIndex < 0) {
                return "";
            }
            String remainder = predicateText.substring(andIndex + 2).trim();
            if (!remainder.startsWith("/")) {
                return "";
            }
            int closeParen = remainder.indexOf(')');
            return closeParen >= 0 ? remainder.substring(0, closeParen) : remainder;
        }

        /** {@inheritDoc} */
        @Override
        public void resources(Function<ServerRequest, Mono<Resource>> lookupFunction) {}

        /** {@inheritDoc} */
        @Override
        public void attributes(Map<String, Object> attributes) {}

        /** {@inheritDoc} */
        @Override
        public void unknown(RouterFunction<?> routerFunction) {}
    }
}
