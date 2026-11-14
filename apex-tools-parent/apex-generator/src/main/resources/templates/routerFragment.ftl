================================================================
${tableComment}路由注册片段
================================================================
将以下代码追加到对应的子域路由器（com.apex.scaffold.interfaces.router
包下的 ConfigRouter/LogRouter 等，按业务域选择），并在文件头部 import 对应的
Handler 类：${ifacePackage}.handler.${module}.${entityName}Handler

    /**
     * ${tableComment}路由（/admin-api/v1/${module}/${entityVar}s）。
     *
     * <p>提供${tableComment}的查询、创建、更新与删除端点，
     * 经 RequirePermissionAspect 校验 ${module}:${entityVar}:* 权限。</p>
     *
     * @param handler ${tableComment}处理器
     * @return ${tableComment}管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> admin${entityName}Routes(${entityName}Handler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/${module}/${entityVar}s", routes -> routes.GET("", handler::list)
                        .GET("/{id}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", handler::delete))
                .build();
    }
