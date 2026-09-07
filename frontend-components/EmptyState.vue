<!--
  EmptyState 空状态组件
  ------------------------------------------------------------------
  用于列表/表格/搜索结果为空时的友好提示展示
  基于 Element Plus Empty 组件二次封装
  
  使用方式:
  将此文件复制到: /Users/work/apex-admin-web/src/components/EmptyState.vue
-->
<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  description?: string
  image?: string
  imageSize?: number
  type?: 'default' | 'search' | 'network' | 'permission'
}

const props = withDefaults(defineProps<Props>(), {
  description: '暂无数据',
  imageSize: 200,
  type: 'default'
})

const finalImage = computed(() => {
  if (props.image) return props.image
  
  switch (props.type) {
    case 'search':
      return 'search'
    case 'network':
      return 'network'
    case 'permission':
      return 'permission'
    default:
      return undefined
  }
})
</script>

<template>
  <div class="empty-state">
    <el-empty
      :description="description"
      :image="finalImage"
      :image-size="imageSize"
    >
      <slot />
    </el-empty>
  </div>
</template>

<style scoped lang="scss">
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
  padding: 40px 20px;
}
</style>
