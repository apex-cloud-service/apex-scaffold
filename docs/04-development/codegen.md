# 代码生成器（模板开发功能）使用说明

> 版本：v1.2.0（2026-08-27）
> 模块：`apex-generator`（独立模块，仅依赖 FreeMarker 2.3.33 + Jackson，offline 可构建）

## 一、是什么

**DDD 五层代码生成器**：按一张业务表的结构定义（JSON），一键生成一套完整的通用 CRUD 代码，
覆盖脚手架的全部五层，风格与项目现有代码（Javadoc / Lombok / MapStruct / WebFlux 函数式端点）完全一致。

这是"模板开发功能"的核心载体：客户拿到脚手架后，业务模块不再手写样板代码，
改表 → 填 JSON → 跑脚本 → 得到可编译、可测试、可运行的完整模块。

## 二、生成产物（以 `Product` 表为例，共 13 个文件）

| 层 | 文件 | 说明 |
|---|---|---|
| domain | `ProductEntity.java` | 聚合根实体（BaseEntity + 工厂方法 + 领域行为） |
| domain | `ProductRepository.java` | 仓储接口 |
| infrastructure | `ProductPO.java` | 持久化对象（@TableName） |
| infrastructure | `ProductMapper.java` | MyBatis-Plus Mapper |
| infrastructure | `ProductPoConverter.java` | MapStruct 双向转换器 |
| infrastructure | `ProductRepositoryImpl.java` | 仓储实现（JDBC 线程池调度） |
| application | `dto/demo/ProductRequest.java` | 创建/更新请求 DTO（含校验注解） |
| application | `dto/demo/ProductDTO.java` | 响应 DTO |
| application | `ProductService.java` | 应用服务（CRUD + 日志） |
| interfaces | `handler/demo/ProductHandler.java` | 函数式 Handler（含 `demo:product:*` 权限点） |
| 辅助 | `ProductRouterFragment.txt` | 路由注册片段（复制到 ApiRouter） |
| 辅助 | `V3__create_biz_product.sql` | Flyway 迁移片段（合并到 db/migration） |
| 辅助 | `ProductServiceTest.java` | 单元测试骨架（Mockito + StepVerifier） |

## 三、表结构定义（JSON）

```json
{
  "module": "demo",                        // 业务模块（DTO/Handler 子包名）
  "entityName": "Product",                 // 实体名（大驼峰）
  "tableName": "biz_product",              // 数据库表名
  "tableComment": "商品",                  // 表注释
  "fields": [
    { "name": "productName", "type": "String",       "comment": "商品名称", "required": true },
    { "name": "price",      "type": "BigDecimal",    "comment": "销售价格" },
    { "name": "stock",      "type": "Integer",       "comment": "库存数量" },
    { "name": "onSale",     "type": "Boolean",       "comment": "是否上架" },
    { "name": "launchAt",   "type": "LocalDateTime", "comment": "上架时间" }
  ]
}
```

支持类型：`String` / `Long` / `Integer` / `BigDecimal` / `Boolean` / `Double` / `LocalDateTime`，
自动推导数据库列名（驼峰→下划线）与建表 SQL 类型。

## 四、使用方式

```bash
# 一键生成（sdkman 环境，自动构建 apex-generator）
./scripts/codegen/generate.sh -i scripts/codegen/examples/product.json -o ./generated -v 3

# 仅生成到指定目录（迁移版本号可选，缺省 2）
./scripts/codegen/generate.sh -i my-table.json -o ./my-module -v 3

# 同时生成 Vue3 管理前端页面（-f 指定 apex-admin-web 工程根目录）
./scripts/codegen/generate.sh -i my-table.json -f /path/to/apex-admin-web
```

生成后：
1. 将 `domain/`、`infrastructure/`、`application/` 下的类移动到目标业务库（如 `apex-system`）对应包目录；
2. 将 `interfaces/handler/` 下的 Handler 移入目标运行模块（如 `apex-system-service`）或业务库
   `interfaces/` 包，按 `ProductRouterFragment.txt` 在对应 Router 注册路由；
3. 将 `V{n}__create_*.sql` 合并入目标域的 Flyway 迁移目录（如
   `apex-system/src/main/resources/db/migration/mysql`，system 域当前至 V90）；
4. 将 `ProductServiceTest.java` 移入测试目录，并把表定义对应的建表语句追加到
   Flyway 迁移 `db/migration`（MySQL 与 PostgreSQL 两套目录需配对维护）。

## 四·五、前端代码生成（Vue3 页面）

指定 `-f <frontendDir>` 后，除后端代码外还会在 `apex-admin-web` 工程下生成：

| 文件 | 说明 |
|------|------|
| `src/api/{module}/{entityVar}.ts` | 类型化 API 封装（Result 解包、JSDoc 注释） |
| `src/views/{module}/{entityVar}/index.vue` | 列表页（关键字搜索 + 表格 + 分页 + 新增/编辑/删除） |
| `src/views/{module}/{entityVar}/form.vue` | 新增/编辑共用表单（v-model + validate 暴露） |
| `generated/{entityName}RouteFragment.txt` | 动态路由注册说明（VIEW_REGISTRY + sys_menu + 权限码） |

接入步骤（详见生成的路由说明文件）：
1. 在 `src/router/dynamic.ts` 的 `VIEW_REGISTRY` 注册组件；
2. 在后端 `sys_menu` 表新增目录/菜单/按钮三条记录；
3. 后端 Handler 需已按 `@RequirePermission("${module}:${entityVar}:list")` 等权限点标注。

## 五、扩展模板

模板位于 `apex-generator/src/main/resources/templates/`（FreeMarker 2.3.33）：
`entity/repository/po/mapper/converter/repositoryImpl/dtoRequest/dtoResponse/service/handler/
routerFragment/migrationFragment/serviceTest.ftl`，以及前端模板
`frontendApi/frontendList/frontendForm/frontendRouteFragment.ftl`。

自定义字段类型、校验规则、权限点或生成风格时，直接修改对应 `.ftl` 即可。

## 六、说明

- `apex-generator` 为独立工具模块，不参与主应用运行时依赖（主应用无需引入）。
- offline 构建说明：FreeMarker 锁定 2.3.33（本地仓库已有，BOM 默认 2.3.34 未下载，
  已显式指定版本并在本地移除 `_remote.repositories` 元数据以保证 offline 可解析）。
- 生成代码遵循项目全库规范（原项目名零残留、apex.* 命名空间、Javadoc 完整注释）。
> 配套图表：[52 DDD 代码生成流水线](../diagrams/52_codegen_pipeline.svg)
