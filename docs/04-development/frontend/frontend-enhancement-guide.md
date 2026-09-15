# 办公平台前端功能增强指南

本文档提供阶段二和阶段三前端功能的完整实现指南。

## 📋 目录

1. [API 扩展](#api-扩展)
2. [控制台页面增强](#控制台页面增强)
3. [新增功能页面](#新增功能页面)
4. [路由配置](#路由配置)
5. [测试验证](#测试验证)

---

## 1. API 扩展

### 1.1 扩展 office-new.ts

将 `frontend-api-extension.ts` 中的所有代码追加到 `/Users/work/apex-admin-web/src/api/office-new.ts` 文件末尾。

新增的API包括：

**阶段二功能**：
- `syncOrganization()` - 组织同步
- `getOrganizationSyncStatus()` - 查询同步状态
- `sendMessage()` - 发送消息
- `getMessageStatus()` - 查询消息状态
- `createApprovalInstance()` - 创建审批实例
- `getApprovalInstance()` - 查询审批实例
- `listApprovalInstances()` - 列出审批实例

**阶段三功能**：
- `createCalendarEvent()` - 创建日历事件
- `getCalendarEvent()` - 查询日历事件
- `listCalendarEvents()` - 列出日历事件
- `updateCalendarEvent()` - 更新日历事件
- `deleteCalendarEvent()` - 删除日历事件
- `createDocument()` - 创建文档
- `getDocument()` - 查询文档
- `listDocuments()` - 列出文档

---

## 2. 控制台页面增强

### 2.1 更新控制台功能卡片

修改 `/Users/work/apex-admin-web/src/views/office/console/index.vue`：

```vue
<template>
  <div class="office-console-container">
    <!-- 头部 -->
    <el-card class="header-card">
      <h2>办公平台控制台</h2>
      <p class="description">集成飞书、钉钉、企业微信的完整办公能力</p>
    </el-card>

    <!-- 平台选择器 -->
    <el-card class="platform-selector-card">
      <el-radio-group v-model="selectedPlatform" size="large">
        <el-radio-button
          v-for="platform in platforms"
          :key="platform"
          :label="platform"
        >
          {{ getPlatformIcon(platform) }} {{ PLATFORM_NAMES[platform] }}
        </el-radio-button>
      </el-radio-group>
    </el-card>

    <!-- 功能面板 -->
    <el-row :gutter="20" v-if="selectedPlatform">
      <!-- 组织同步 -->
      <el-col :span="8">
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon :size="20"><Connection /></el-icon>
              <span>组织同步</span>
            </div>
          </template>
          <el-space direction="vertical" style="width: 100%">
            <el-select v-model="orgSyncForm.syncType" placeholder="同步类型" style="width: 100%">
              <el-option label="全量同步" value="FULL" />
              <el-option label="增量同步" value="INCREMENTAL" />
            </el-select>
            <el-button type="primary" @click="handleOrgSync" :loading="orgSyncLoading" block>
              开始同步
            </el-button>
          </el-space>
          <div v-if="orgSyncResult" class="result-info">
            <el-tag type="success">同步完成</el-tag>
            <p>部门: {{ orgSyncResult.departmentCount }}</p>
            <p>成员: {{ orgSyncResult.userCount }}</p>
          </div>
        </el-card>
      </el-col>

      <!-- 消息发送 -->
      <el-col :span="8">
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon :size="20"><ChatDotRound /></el-icon>
              <span>消息发送</span>
            </div>
          </template>
          <el-space direction="vertical" style="width: 100%">
            <el-select v-model="messageForm.messageType" placeholder="消息类型" style="width: 100%">
              <el-option label="文本消息" value="TEXT" />
              <el-option label="图片消息" value="IMAGE" />
              <el-option label="文件消息" value="FILE" />
              <el-option label="卡片消息" value="CARD" />
            </el-select>
            <el-input v-model="messageForm.content" placeholder="消息内容" type="textarea" :rows="3" />
            <el-input v-model="messageForm.receiverIds" placeholder="接收人ID（逗号分隔）" />
            <el-button type="primary" @click="handleSendMessage" :loading="messageSendLoading" block>
              发送消息
            </el-button>
          </el-space>
        </el-card>
      </el-col>

      <!-- 审批管理 -->
      <el-col :span="8">
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon :size="20"><Checked /></el-icon>
              <span>审批管理</span>
            </div>
          </template>
          <el-space direction="vertical" style="width: 100%">
            <el-button type="success" @click="showApprovalDialog = true" block>
              创建审批实例
            </el-button>
            <el-button type="primary" @click="loadApprovalInstances" block>
              查看审批列表
            </el-button>
          </el-space>
          <div v-if="approvalStats" class="result-info">
            <p>待审批: {{ approvalStats.pending }}</p>
            <p>已完成: {{ approvalStats.completed }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：日历和文档 -->
    <el-row :gutter="20" v-if="selectedPlatform" style="margin-top: 20px">
      <!-- 日历会议 -->
      <el-col :span="12">
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon :size="20"><Calendar /></el-icon>
              <span>日历会议</span>
            </div>
          </template>
          <el-space direction="vertical" style="width: 100%">
            <el-button type="primary" @click="showCalendarDialog = true" block>
              创建日历事件
            </el-button>
            <el-button type="success" @click="loadCalendarEvents" block>
              查看我的日程
            </el-button>
          </el-space>
        </el-card>
      </el-col>

      <!-- 文档操作 -->
      <el-col :span="12">
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon :size="20"><FolderOpened /></el-icon>
              <span>文档操作</span>
            </div>
          </template>
          <el-space direction="vertical" style="width: 100%">
            <el-button type="primary" @click="showDocumentDialog = true" block>
              创建文档
            </el-button>
            <el-button type="success" @click="loadDocuments" block>
              查看文档列表
            </el-button>
          </el-space>
        </el-card>
      </el-col>
    </el-row>

    <!-- 审批创建对话框 -->
    <el-dialog v-model="showApprovalDialog" title="创建审批实例" width="500px">
      <el-form :model="approvalForm" label-width="100px">
        <el-form-item label="审批代码">
          <el-input v-model="approvalForm.approvalCode" placeholder="例如：LEAVE_REQUEST" />
        </el-form-item>
        <el-form-item label="表单数据">
          <el-input
            v-model="approvalForm.formDataJson"
            type="textarea"
            :rows="5"
            placeholder='{"reason": "请假原因", "days": 1}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showApprovalDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateApproval" :loading="approvalCreateLoading">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 日历事件创建对话框 -->
    <el-dialog v-model="showCalendarDialog" title="创建日历事件" width="600px">
      <el-form :model="calendarForm" label-width="100px">
        <el-form-item label="事件标题">
          <el-input v-model="calendarForm.title" placeholder="会议标题" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="calendarForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="calendarForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="calendarForm.location" placeholder="会议地点" />
        </el-form-item>
        <el-form-item label="参与人">
          <el-input v-model="calendarForm.attendeesStr" placeholder="参与人ID（逗号分隔）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCalendarDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateCalendarEvent" :loading="calendarCreateLoading">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 文档创建对话框 -->
    <el-dialog v-model="showDocumentDialog" title="创建文档" width="500px">
      <el-form :model="documentForm" label-width="100px">
        <el-form-item label="文档标题">
          <el-input v-model="documentForm.title" placeholder="文档标题" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="documentForm.documentType" placeholder="选择类型" style="width: 100%">
            <el-option label="文档" value="DOC" />
            <el-option label="表格" value="SHEET" />
            <el-option label="幻灯片" value="SLIDE" />
            <el-option label="PDF" value="PDF" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始内容">
          <el-input v-model="documentForm.content" type="textarea" :rows="4" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDocumentDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateDocument" :loading="documentCreateLoading">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Connection,
  ChatDotRound,
  Checked,
  Calendar,
  FolderOpened,
} from '@element-plus/icons-vue'
import {
  listApps,
  syncOrganization,
  sendMessage,
  createApprovalInstance,
  listApprovalInstances,
  createCalendarEvent,
  listCalendarEvents,
  createDocument,
  listDocuments,
  PLATFORM_NAMES,
  type OfficePlatform,
  type OrganizationSyncRequest,
  type MessageSendRequest,
  type ApprovalInstanceRequest,
  type CalendarEventRequest,
  type DocumentRequest,
} from '@/api/office-new'

const platforms: OfficePlatform[] = ['FEISHU', 'DINGTALK', 'WECOM']
const selectedPlatform = ref<OfficePlatform>()
const apps = ref<any[]>([])

// 组织同步
const orgSyncLoading = ref(false)
const orgSyncForm = reactive({ syncType: 'FULL' as 'FULL' | 'INCREMENTAL' })
const orgSyncResult = ref<any>(null)

// 消息发送
const messageSendLoading = ref(false)
const messageForm = reactive({
  messageType: 'TEXT' as 'TEXT' | 'IMAGE' | 'FILE' | 'CARD',
  content: '',
  receiverType: 'USER' as 'USER' | 'DEPARTMENT' | 'GROUP',
  receiverIds: '',
})

// 审批
const showApprovalDialog = ref(false)
const approvalCreateLoading = ref(false)
const approvalForm = reactive({
  approvalCode: '',
  formDataJson: '',
})
const approvalStats = ref<any>(null)

// 日历
const showCalendarDialog = ref(false)
const calendarCreateLoading = ref(false)
const calendarForm = reactive({
  calendarId: 'default',
  title: '',
  startTime: null as Date | null,
  endTime: null as Date | null,
  location: '',
  isAllDay: false,
  attendeesStr: '',
})

// 文档
const showDocumentDialog = ref(false)
const documentCreateLoading = ref(false)
const documentForm = reactive({
  title: '',
  documentType: 'DOC' as 'DOC' | 'SHEET' | 'SLIDE' | 'PDF',
  content: '',
})

const getPlatformIcon = (platform: OfficePlatform) => {
  const iconMap: Record<OfficePlatform, string> = {
    FEISHU: '🚀',
    DINGTALK: '📱',
    WECOM: '💼',
  }
  return iconMap[platform] || '📋'
}

const currentApp = computed(() => {
  if (!selectedPlatform.value) return null
  return apps.value.find((app) => app.platform === selectedPlatform.value)
})

const handleOrgSync = async () => {
  if (!currentApp.value) {
    ElMessage.warning('请先配置该平台应用')
    return
  }

  orgSyncLoading.value = true
  try {
    const request: OrganizationSyncRequest = {
      appId: currentApp.value.id,
      syncType: orgSyncForm.syncType,
    }
    const result = await syncOrganization(selectedPlatform.value!, request)
    orgSyncResult.value = result
    ElMessage.success('同步任务已启动')
  } catch (error: any) {
    ElMessage.error(error.message || '同步失败')
  } finally {
    orgSyncLoading.value = false
  }
}

const handleSendMessage = async () => {
  if (!currentApp.value) {
    ElMessage.warning('请先配置该平台应用')
    return
  }

  if (!messageForm.content || !messageForm.receiverIds) {
    ElMessage.warning('请填写消息内容和接收人')
    return
  }

  messageSendLoading.value = true
  try {
    const request: MessageSendRequest = {
      appId: currentApp.value.id,
      messageType: messageForm.messageType,
      content: messageForm.content,
      receiverType: messageForm.receiverType,
      receiverIds: messageForm.receiverIds.split(',').map((id) => id.trim()),
    }
    const result = await sendMessage(selectedPlatform.value!, request)
    ElMessage.success(`消息已发送，成功: ${result.sentCount}`)
  } catch (error: any) {
    ElMessage.error(error.message || '发送失败')
  } finally {
    messageSendLoading.value = false
  }
}

const handleCreateApproval = async () => {
  if (!currentApp.value) {
    ElMessage.warning('请先配置该平台应用')
    return
  }

  approvalCreateLoading.value = true
  try {
    const formData = JSON.parse(approvalForm.formDataJson || '{}')
    const request: ApprovalInstanceRequest = {
      appId: currentApp.value.id,
      approvalCode: approvalForm.approvalCode,
      formData,
    }
    const result = await createApprovalInstance(selectedPlatform.value!, request)
    ElMessage.success(`审批实例已创建: ${result.instanceId}`)
    showApprovalDialog.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    approvalCreateLoading.value = false
  }
}

const loadApprovalInstances = async () => {
  if (!selectedPlatform.value) return
  try {
    const instances = await listApprovalInstances(selectedPlatform.value, 1, 100)
    approvalStats.value = {
      pending: instances.filter((i) => i.status === 'PENDING').length,
      completed: instances.filter((i) => i.status === 'APPROVED' || i.status === 'REJECTED').length,
    }
    ElMessage.success(`加载完成，共 ${instances.length} 条审批`)
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  }
}

const handleCreateCalendarEvent = async () => {
  if (!currentApp.value) {
    ElMessage.warning('请先配置该平台应用')
    return
  }

  calendarCreateLoading.value = true
  try {
    const request: CalendarEventRequest = {
      appId: currentApp.value.id,
      calendarId: calendarForm.calendarId,
      title: calendarForm.title,
      startTime: calendarForm.startTime?.toISOString() || '',
      endTime: calendarForm.endTime?.toISOString(),
      location: calendarForm.location,
      isAllDay: calendarForm.isAllDay,
      attendees: calendarForm.attendeesStr.split(',').map((id) => id.trim()),
    }
    const result = await createCalendarEvent(selectedPlatform.value!, request)
    ElMessage.success(`日历事件已创建: ${result.platformEventId}`)
    showCalendarDialog.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    calendarCreateLoading.value = false
  }
}

const loadCalendarEvents = async () => {
  if (!selectedPlatform.value) return
  try {
    const startTime = new Date().toISOString()
    const endTime = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
    const events = await listCalendarEvents(selectedPlatform.value, 'me', startTime, endTime)
    ElMessage.success(`加载完成，共 ${events.length} 个日程`)
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  }
}

const handleCreateDocument = async () => {
  if (!currentApp.value) {
    ElMessage.warning('请先配置该平台应用')
    return
  }

  documentCreateLoading.value = true
  try {
    const request: DocumentRequest = {
      appId: currentApp.value.id,
      title: documentForm.title,
      documentType: documentForm.documentType,
      content: documentForm.content || undefined,
    }
    const result = await createDocument(selectedPlatform.value!, request)
    ElMessage.success(`文档已创建`)
    window.open(result.url, '_blank')
    showDocumentDialog.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    documentCreateLoading.value = false
  }
}

const loadDocuments = async () => {
  if (!selectedPlatform.value) return
  try {
    const docs = await listDocuments(selectedPlatform.value)
    ElMessage.success(`加载完成，共 ${docs.length} 个文档`)
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  }
}

const loadData = async () => {
  try {
    apps.value = await listApps()
    if (apps.value.length > 0) {
      selectedPlatform.value = apps.value[0].platform
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.office-console-container {
  padding: 20px;

  .header-card {
    margin-bottom: 20px;

    h2 {
      margin: 0 0 8px;
      font-size: 20px;
    }

    .description {
      margin: 0;
      color: #666;
      font-size: 14px;
    }
  }

  .platform-selector-card {
    margin-bottom: 20px;
    text-align: center;
  }

  .feature-card {
    .card-header {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
    }

    .result-info {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid #eee;

      p {
        margin: 8px 0;
        font-size: 14px;
        color: #666;
      }
    }
  }
}
</style>
```

---

## 3. 新增功能页面

### 3.1 组织同步页面（可选）

创建 `/Users/work/apex-admin-web/src/views/office/organization/index.vue`，用于查看同步历史和详细统计。

### 3.2 消息管理页面（可选）

创建 `/Users/work/apex-admin-web/src/views/office/messages/index.vue`，用于查看消息发送历史和状态。

### 3.3 审批实例列表页面（可选）

创建 `/Users/work/apex-admin-web/src/views/office/approvals/index.vue`，用于管理审批实例。

---

## 4. 路由配置

如果需要添加新的页面路由，修改 `/Users/work/apex-admin-web/src/router/index.ts` 或动态路由配置。

---

## 5. 测试验证

### 5.1 本地开发测试

```bash
cd /Users/work/apex-admin-web
npm install
npm run dev
```

### 5.2 功能测试清单

- [ ] 平台应用配置（创建、编辑、删除）
- [ ] 账号绑定审批（批准、拒绝、解绑）
- [ ] 组织同步（全量、增量）
- [ ] 消息发送（文本、图片、文件、卡片）
- [ ] 审批流程（创建实例、查看状态）
- [ ] 日历事件（创建、查看、更新、删除）
- [ ] 文档操作（创建、查看、列表）

### 5.3 API 对接验证

确保后端服务已启动并可访问：

```bash
# 检查后端服务
curl http://localhost:8080/admin-api/v1/office/apps

# 测试组织同步
curl -X POST http://localhost:8080/admin-api/v1/office/organization/FEISHU/sync \
  -H "Content-Type: application/json" \
  -d '{"appId": 1, "syncType": "FULL"}'
```

---

## 📝 总结

本指南提供了完整的前端实现方案，包括：

1. ✅ API 扩展代码模板
2. ✅ 控制台页面完整实现
3. ✅ 对话框表单交互
4. ✅ 数据加载和状态管理
5. ✅ 错误处理和用户提示
6. ✅ 响应式布局和样式

按照本指南实施后，前端将具备完整的办公平台集成能力，支持阶段二和阶段三的所有功能。
