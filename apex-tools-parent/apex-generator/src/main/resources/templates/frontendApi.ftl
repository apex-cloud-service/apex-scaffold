/**
 * ${tableComment} API（由 apex-generator 前端模板生成）
 * ------------------------------------------------------------------
 * 与后端 /admin-api/v1/${module}/${entityVar}s 端点一一对应，
 * 遵循 apex-admin-web 请求封装约定（Result 解包 / traceId 透传）。
 */
import { del, get, post, put } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types'

/** ${tableComment}列表项 */
export interface ${entityName}Item {
  id: number
<#list fields as field>
  /** ${field.comment} */
  ${field.name}?: ${field.tsType}
</#list>
}

/** ${tableComment}查询参数 */
export interface ${entityName}Query extends PageQuery {
  keyword?: string
}

/** ${tableComment}新增/编辑请求体 */
export interface ${entityName}Payload {
<#list formFields as field>
  /** ${field.comment} */
  ${field.name}?: ${field.tsType}
</#list>
}

/**
 * 分页查询${tableComment}列表。
 * @param params 分页与筛选参数
 */
export function list${entityName}(params: ${entityName}Query): Promise<PageResponse<${entityName}Item>> {
  return get<PageResponse<${entityName}Item>>(
    '/admin-api/v1/${module}/${entityVar}s',
    params as unknown as Record<string, unknown>,
  )
}

/**
 * 创建${tableComment}。
 * @param payload ${tableComment}信息
 */
export function create${entityName}(payload: ${entityName}Payload): Promise<void> {
  return post<void>('/admin-api/v1/${module}/${entityVar}s', payload)
}

/**
 * 更新${tableComment}。
 * @param id ${tableComment}ID
 * @param payload ${tableComment}信息
 */
export function update${entityName}(id: number, payload: ${entityName}Payload): Promise<void> {
  return put<void>('/admin-api/v1/${module}/${entityVar}s/' + id, payload)
}

/**
 * 删除${tableComment}。
 * @param id ${tableComment}ID
 */
export function delete${entityName}(id: number): Promise<void> {
  return del<void>('/admin-api/v1/${module}/${entityVar}s/' + id)
}
