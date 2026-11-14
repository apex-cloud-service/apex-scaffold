<!--
感谢提交 Pull Request！请在合并前完成以下检查清单。
-->

## 变更类型

- [ ] Bug 修复
- [ ] 新功能
- [ ] 重构
- [ ] 文档更新
- [ ] 其他（请说明）：

## 变更说明

<!-- 简要描述本次变更的目的和内容 -->

## 接口文档检查清单（新增或修改 HTTP 接口时必填）

- [ ] 新增接口已在 `RouterOpenApiConfig` 中添加 `@RouterOperation` 声明
- [ ] Handler 方法的 `@Operation.operationId` 与 Router 声明完全一致
- [ ] 请求体 DTO 已声明 `@RequestBody` 或在 `OpenApiDocumentationCustomizer.JSON_REQUEST_TYPES` 中注册
- [ ] 查询参数已在 `OpenApiDocumentationCustomizer.QUERY_PARAMETERS` 中声明
- [ ] 路径参数已声明 `@Parameter(in = PATH, required = true)`
- [ ] 响应 DTO 已在 `OpenApiDocumentationCustomizer.RESPONSE_TYPES` 中注册
- [ ] DTO 类已添加类级和字段级 `@Schema` 注解
- [ ] SSE 接口响应媒体类型为 `text/event-stream`
- [ ] 文件上传接口请求媒体类型为 `multipart/form-data`
- [ ] 文件下载接口响应媒体类型为 `application/octet-stream`
- [ ] Handler 方法已补充 Javadoc（`@param`、`@return`）
- [ ] `RouterOpenApiCoverageTest` 全部通过

## 通用检查

- [ ] 代码遵循项目工程规范（AGENTS.md）
- [ ] 未引入 `@Autowired` 注解（使用构造器注入或 `@Resource`）
- [ ] 无 `bias`/`Bias`/`BIAS` 字样残留
- [ ] `mvn` 已替换为 `mvnd`，构建前执行 `source ./scripts/build/init-build-env.sh`
- [ ] 本地已运行相关测试并通过
- [ ] `git diff --check` 无空白错误

## 验证命令

```bash
source ./scripts/build/init-build-env.sh
mvnd -pl :apex-system -am -Dtest=RouterOpenApiCoverageTest -Dsurefire.failIfNoSpecifiedTests=false test
bash scripts/ci/check-springdoc-coverage.sh
bash scripts/dev/check/check-handler-javadoc.sh
```
