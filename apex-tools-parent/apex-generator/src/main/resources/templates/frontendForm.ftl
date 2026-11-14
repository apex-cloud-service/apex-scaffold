<!--
  ${tableComment}表单
  ------------------------------------------------------------------
  新增/编辑共用表单（由 apex-generator 前端模板生成），
  通过 defineExpose 暴露 validate 供父级弹窗提交前校验。
-->
<template>
  <el-form ref="formEl" :model="props.modelValue" :rules="rules" label-width="100px">
<#list formFields as field>
    <el-form-item label="${field.comment}" prop="${field.name}"<#if !field.required> class="form-item-optional"</#if>>
<#if field.tsType == 'number'>
      <el-input-number v-model="props.modelValue.${field.name}" :min="0" style="width: 100%" placeholder="请输入${field.comment}" />
<#elseif field.tsType == 'boolean'>
      <el-switch v-model="props.modelValue.${field.name}" />
<#elseif field.name?contains('Time') || field.name?contains('Date')>
      <el-date-picker
        v-model="props.modelValue.${field.name}"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="选择${field.comment}"
        style="width: 100%"
      />
<#else>
      <el-input v-model="props.modelValue.${field.name}" placeholder="请输入${field.comment}" />
</#if>
    </el-form-item>
</#list>
  </el-form>
</template>

<script setup lang="ts">
/**
 * ${tableComment}表单脚本
 * - modelValue：父组件传入的表单数据（v-model 双向绑定）
 * - validate：表单校验方法（供父级提交前调用）
 */
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { ${entityName}Payload } from '@/api/${module}/${entityVar}'

/** 组件属性：表单数据（双向绑定） */
const props = defineProps<{ modelValue: ${entityName}Payload }>()

/** 表单实例（校验入口） */
const formEl = ref<FormInstance>()

/** 表单校验规则（必填项生成规则） */
const rules = reactive<FormRules>({
<#list formFields as field>
<#if field.required>
  ${field.name}: [{ required: true, message: '请输入${field.comment}', trigger: 'blur' }],
</#if>
</#list>
})

/**
 * 触发全量校验。
 * @returns 校验通过时 resolve，失败时 reject（含校验结果）
 */
function validate(): Promise<void> {
  return formEl.value ? formEl.value.validate() : Promise.resolve()
}

/** 暴露校验方法给父组件 */
defineExpose({ validate })
</script>

<style scoped>
/* 可选字段样式弱化提示 */
.form-item-optional :deep(.el-form-item__label)::after {
  content: '';
}
</style>
