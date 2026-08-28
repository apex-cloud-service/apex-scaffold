================================================================
${tableComment}前端路由注册说明
================================================================
生成文件已输出到前端工程 apex-admin-web：
  src/api/${module}/${entityVar}.ts
  src/views/${module}/${entityVar}/index.vue
  src/views/${module}/${entityVar}/form.vue

接入动态路由（两步）：

1. 在 src/router/dynamic.ts 的 VIEW_REGISTRY 注册组件：
   '${module}/${entityVar}/index': () => import('@/views/${module}/${entityVar}/index.vue'),

2. 在后端菜单表（sys_menu）新增菜单记录：
   - 目录（menuType=0）：path = /${module}，icon 按需
   - 菜单（menuType=1）：path = /${module}/${entityVar}，component = ${module}/${entityVar}/index
   - 按钮（menuType=2）：perms = ${module}:${entityVar}:list / create / update / delete

权限码（后端 RequirePermissionAspect 校验）：
  ${module}:${entityVar}:list / query / create / update / delete

后端路由前缀：/admin-api/v1/${module}/${entityVar}s
