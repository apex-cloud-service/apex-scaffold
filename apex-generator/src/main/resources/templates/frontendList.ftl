<!--
  ${tableComment}管理
  ------------------------------------------------------------------
  ${tableComment}列表页：关键字查询、分页、新增/编辑/删除。
  由 apex-generator 前端模板生成，需按业务微调字段展示。
-->
<template>
  <el-card shadow="never">
    <!-- 查询区 -->
    <el-form inline class="search-form">
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="按关键字搜索"
          clearable
          style="width: 200px"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <el-button type="success" :icon="Plus" @click="openCreate">新增</el-button>
      </el-form-item>
    </el-form>

    <!-- 列表区 -->
    <el-table v-loading="loading" :data="list" border stripe>
<#list fields as field>
      <el-table-column prop="${field.name}" label="${field.comment}" min-width="120" show-overflow-tooltip />
</#list>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页区 -->
    <div class="pagination-wrap">
      <Pagination :total="total" @change="onPageChange" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑${tableComment}' : '新增${tableComment}'" width="520px">
      <${entityVar}-form ref="formRef" :model-value="form" />
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
/**
 * ${tableComment}管理脚本
 * - 查询：关键字 + 分页
 * - 新增/编辑/删除（调用 ${entityName}Api 模块）
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import Pagination from '@/components/Pagination.vue'
import ${entityVar}Form from './form.vue'
import { showError } from '@/utils/request'
import {
  create${entityName},
  delete${entityName},
  list${entityName},
  update${entityName},
  type ${entityName}Item,
  type ${entityName}Payload,
} from '@/api/${module}/${entityVar}'

/** 加载/保存状态 */
const loading = ref(false)
const saving = ref(false)
/** 列表与总数 */
const list = ref<${entityName}Item[]>([])
const total = ref(0)
/** 查询参数 */
const query = reactive({ page: 1, size: 20, keyword: '' })

/** 弹窗状态与表单 */
const dialog = reactive({ visible: false, isEdit: false, id: 0 })
const form = reactive<${entityName}Payload>({})
const formRef = ref()

/**
 * 分页拉取${tableComment}列表。
 */
async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await list${entityName}({ page: query.page, size: query.size, keyword: query.keyword })
    list.value = data.list || []
    total.value = data.total || 0
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 查询：重置到第一页后拉取 */
function search(): void {
  query.page = 1
  fetchList()
}

/** 重置查询条件 */
function reset(): void {
  query.keyword = ''
  search()
}

/** 分页变化回调 */
function onPageChange(page: number, size: number): void {
  query.page = page
  query.size = size
  fetchList()
}

/** 打开新增弹窗 */
function openCreate(): void {
  dialog.isEdit = false
  dialog.id = 0
  Object.keys(form).forEach((key) => delete form[key as keyof ${entityName}Payload])
  dialog.visible = true
}

/**
 * 打开编辑弹窗（回填表单）。
 * @param row 行数据
 */
function openEdit(row: ${entityName}Item): void {
  dialog.isEdit = true
  dialog.id = row.id
  Object.assign(form, row)
  dialog.visible = true
}

/** 提交新增/编辑 */
async function submit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (dialog.isEdit) {
      await update${entityName}(dialog.id, form)
      ElMessage.success('更新成功')
    } else {
      await create${entityName}(form)
      ElMessage.success('创建成功')
    }
    dialog.visible = false
    fetchList()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 删除${tableComment}（二次确认）。
 * @param row 行数据
 */
async function remove(row: ${entityName}Item): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除${tableComment} ID ' + row.id + '？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await delete${entityName}(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    showError(error)
  }
}

// 页面挂载时初始化
onMounted(fetchList)
</script>

<style scoped>
/* 查询区与分页区 */
.search-form {
  margin-bottom: 8px;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
