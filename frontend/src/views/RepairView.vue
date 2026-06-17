<template>
  <div class="repair-view">
    <header class="repair-header">
      <h2>🛠️ 校园报修服务</h2>
      <div class="role-switch">
        <el-radio-group v-model="role" size="small" @change="onRoleChange">
          <el-radio-button value="student">👤 学生</el-radio-button>
          <el-radio-button value="admin">🔧 管理员/维修员</el-radio-button>
        </el-radio-group>
      </div>
    </header>

    <!-- 统计卡片 -->
    <div class="stats-row" v-if="headerStats">
      <div class="stat-item blue">
        <div class="stat-num">{{ headerStats.totalOrders }}</div>
        <div class="stat-name">总报修数</div>
      </div>
      <div class="stat-item orange">
        <div class="stat-num">{{ headerStats.pendingCount }}</div>
        <div class="stat-name">待处理</div>
      </div>
      <div class="stat-item green">
        <div class="stat-num">{{ headerStats.completedCount }}</div>
        <div class="stat-name">已完成</div>
      </div>
      <div class="stat-item yellow">
        <div class="stat-num">⭐{{ headerStats.avgRating }}</div>
        <div class="stat-name">平均评分</div>
      </div>
      <div class="stat-item gray">
        <div class="stat-num">{{ headerStats.avgResponseTimeMinutes }}分钟</div>
        <div class="stat-name">平均响应</div>
      </div>
    </div>
    <el-skeleton v-else :rows="1" animated style="padding: 0 24px" />

    <!-- ============ 学生视图 ============ -->
    <el-tabs v-if="role === 'student'" v-model="activeTab" class="repair-tabs">
      <el-tab-pane label="提交报修" name="submit">
        <div class="tab-content">
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="80px"
            label-position="top"
            class="repair-form"
          >
            <el-form-item label="报修标题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="请简要描述问题，如：宿舍水龙头漏水（2-50字）"
                maxlength="50"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="报修地点" prop="location">
              <el-input
                v-model="form.location"
                placeholder="请填写具体位置，如：学生宿舍3号楼502室（2-100字）"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="问题描述" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="4"
                placeholder="请详细描述故障情况（10-500字），方便维修员提前准备工具和材料"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="紧急程度" prop="urgencyLevel">
              <el-radio-group v-model="form.urgencyLevel">
                <el-radio-button value="LOW">低</el-radio-button>
                <el-radio-button value="NORMAL">普通</el-radio-button>
                <el-radio-button value="HIGH">高</el-radio-button>
                <el-radio-button value="URGENT">紧急</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="上传图片">
              <el-upload
                v-model:file-list="fileList"
                list-type="picture-card"
                :auto-upload="false"
                :limit="3"
                :on-exceed="onExceed"
                :before-upload="beforeUpload"
              >
                <el-icon><Plus /></el-icon>
              </el-upload>
              <div class="upload-tip">最多上传3张图片，每张不超过5MB</div>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">
                提交报修
              </el-button>
              <el-button size="large" @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- ============ Tab 2: 我的报修 ============ -->
      <el-tab-pane label="我的报修" name="my">
        <div class="tab-content">
          <div class="filter-bar">
            <el-select v-model="myFilter.status" placeholder="全部状态" clearable style="width: 160px" @change="loadMyOrders">
              <el-option label="待派单" value="PENDING" />
              <el-option label="已派单" value="ASSIGNED" />
              <el-option label="维修中" value="REPAIRING" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-button @click="loadMyOrders">刷新</el-button>
          </div>

          <el-table :data="myOrders" stripe v-loading="myLoading" empty-text="暂无报修记录">
            <el-table-column prop="orderNo" label="报修单号" width="200" />
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="location" label="地点" width="140" show-overflow-tooltip />
            <el-table-column label="紧急" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.urgencyLevel === 'URGENT'" type="danger" size="small">紧急</el-tag>
                <el-tag v-else-if="row.urgencyLevel === 'HIGH'" type="warning" size="small">高</el-tag>
                <span v-else style="color:var(--text-tertiary)">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ row.createTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="showDetail(row.id)">查看</el-button>
                <template v-if="row.status === 'PENDING'">
                  <el-button size="small" link type="danger" @click="handleCancel(row.id)">取消</el-button>
                </template>
                <template v-else-if="row.status === 'COMPLETED' && !row.reviewRating">
                  <el-button size="small" link type="warning" @click="openReview(row.id)">评价</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="myTotal > myFilter.pageSize"
            style="margin-top: 16px; justify-content: center"
            background
            layout="prev, pager, next"
            :total="myTotal"
            :page-size="myFilter.pageSize"
            v-model:current-page="myFilter.page"
            @current-change="loadMyOrders"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 管理员视图 ============ -->
    <el-tabs v-if="role === 'admin'" v-model="adminSubTab" class="repair-tabs">
      <el-tab-pane label="待派单" name="pending">
        <div class="tab-content">
          <div class="filter-bar">
            <el-button @click="() => loadAdminOrders('PENDING')">刷新</el-button>
            <el-button type="primary" @click="loadStats" :loading="statsLoading">统计概览</el-button>
          </div>
          <admin-table
            :orders="adminOrders"
            :loading="adminLoading"
            @detail="showDetail"
            @assign="openAssign"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="维修中" name="progress">
        <div class="tab-content">
          <div class="filter-bar">
            <el-button @click="() => loadAdminOrders('in_progress')">刷新</el-button>
            <el-button type="primary" @click="loadStats" :loading="statsLoading">统计概览</el-button>
          </div>
          <el-table :data="adminOrders" stripe v-loading="adminLoading" empty-text="暂无进行中的报修单">
            <el-table-column prop="orderNo" label="报修单号" width="200" />
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="location" label="地点" width="140" show-overflow-tooltip />
            <el-table-column label="紧急" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.urgencyLevel === 'URGENT'" type="danger" size="small">紧急</el-tag>
                <el-tag v-else-if="row.urgencyLevel === 'HIGH'" type="warning" size="small">高</el-tag>
                <span v-else style="color:var(--text-tertiary)">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="维修员" width="100">
              <template #default="{ row }">{{ row.maintainerName || '-' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ row.createTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="showDetail(row.id)">详情</el-button>
                <template v-if="row.status === 'ASSIGNED'">
                  <el-button size="small" link type="warning" @click="handleAccept(row.id)">接单</el-button>
                </template>
                <template v-else-if="row.status === 'REPAIRING'">
                  <el-button size="small" link type="success" @click="handleComplete(row.id)">完成</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="已完成" name="completed">
        <div class="tab-content">
          <div class="filter-bar">
            <el-button @click="() => loadAdminOrders('COMPLETED')">刷新</el-button>
            <el-button type="primary" @click="loadStats" :loading="statsLoading">统计概览</el-button>
          </div>
          <el-table :data="adminOrders" stripe v-loading="adminLoading" empty-text="暂无已完成的报修单">
            <el-table-column prop="orderNo" label="报修单号" width="200" />
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="location" label="地点" width="140" show-overflow-tooltip />
            <el-table-column label="维修员" width="100">
              <template #default="{ row }">{{ row.maintainerName || '-' }}</template>
            </el-table-column>
            <el-table-column label="完成时间" width="160">
              <template #default="{ row }">{{ row.completedTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="评分" width="140">
              <template #default="{ row }">
                <el-rate v-if="row.reviewRating" :model-value="row.reviewRating" disabled show-score text-color="#ff9900" size="small" />
                <span v-else style="color:var(--text-tertiary)">未评价</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="showDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 详情弹窗（含时间线） ============ -->
    <el-dialog v-model="detailVisible" title="报修单详情" width="640px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="报修单号">{{ detailData.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detailData.status)">{{ statusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detailData.title }}</el-descriptions-item>
          <el-descriptions-item label="地点">{{ detailData.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="紧急程度">
            <el-tag v-if="detailData.urgencyLevel === 'URGENT'" type="danger" size="small">紧急</el-tag>
            <el-tag v-else-if="detailData.urgencyLevel === 'HIGH'" type="warning" size="small">高</el-tag>
            <span v-else>{{ urgencyLabel(detailData.urgencyLevel) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">{{ detailData.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报修人">{{ detailData.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="维修员">{{ detailData.maintainerName || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 时间线 -->
        <div class="timeline-wrap" v-if="detailData.createTime">
          <h4 style="margin: 20px 0 12px; color: var(--text-primary);">处理进度</h4>
          <el-timeline>
            <el-timeline-item
              :timestamp="detailData.createTime"
              placement="top"
              type="primary"
            >
              提交报修
            </el-timeline-item>
            <el-timeline-item
              v-if="detailData.assignedTime"
              :timestamp="detailData.assignedTime"
              placement="top"
              type="primary"
            >
              已派单 — 维修员：{{ detailData.maintainerName || '-' }}
            </el-timeline-item>
            <el-timeline-item
              v-if="detailData.status === 'REPAIRING' || detailData.status === 'COMPLETED'"
              :timestamp="detailData.assignedTime || detailData.createTime"
              placement="top"
              :type="detailData.status === 'COMPLETED' ? 'success' : 'warning'"
            >
              维修中
            </el-timeline-item>
            <el-timeline-item
              v-if="detailData.completedTime"
              :timestamp="detailData.completedTime"
              placement="top"
              type="success"
            >
              维修完成
            </el-timeline-item>
            <el-timeline-item
              v-if="detailData.reviewRating"
              :timestamp="detailData.createTime"
              placement="top"
              type="warning"
            >
              已评价 — {{ detailData.reviewRating }} 星
              <el-rate :model-value="detailData.reviewRating" disabled show-score text-color="#ff9900" size="small" style="display: inline-flex; vertical-align: middle; margin-left: 8px" />
            </el-timeline-item>
            <el-timeline-item
              v-if="detailData.status === 'CANCELLED'"
              :timestamp="detailData.updateTime || detailData.createTime"
              placement="top"
              type="danger"
            >
              已取消
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 图片展示 -->
        <div v-if="detailData.imageUrls" class="image-gallery">
          <h4 style="margin: 20px 0 12px; color: var(--text-primary);">报修图片</h4>
          <div v-if="parseImages(detailData.imageUrls).length > 0" class="image-list">
            <el-image
              v-for="(url, i) in parseImages(detailData.imageUrls)"
              :key="i"
              :src="url"
              fit="cover"
              style="width: 120px; height: 120px; border-radius: 6px; margin-right: 8px;"
              :preview-src-list="parseImages(detailData.imageUrls)"
              :initial-index="i"
            />
          </div>
          <span v-else style="color:var(--text-tertiary)">暂无图片</span>
        </div>
      </template>
    </el-dialog>

    <!-- ============ 派单弹窗 ============ -->
    <el-dialog v-model="assignVisible" title="派单" width="480px">
      <el-form label-width="80px">
        <el-form-item label="报修单号">{{ assignOrderNo }}</el-form-item>
        <el-form-item label="报修标题">{{ assignOrderTitle }}</el-form-item>
        <el-form-item label="选择维修员">
          <el-select v-model="assignMaintainerId" placeholder="请选择维修员" style="width: 100%">
            <el-option
              v-for="m in maintainers"
              :key="m.id"
              :label="`${m.name}（${m.skillCategory}）`"
              :value="m.id"
            >
              <span>{{ m.name }}</span>
              <span style="float: right; color: var(--text-tertiary); font-size: 13px">{{ m.skillCategory }} | {{ m.status === 'AVAILABLE' ? '空闲' : '忙碌' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!assignMaintainerId" @click="handleAssign">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- ============ 评价弹窗 ============ -->
    <el-dialog v-model="reviewVisible" title="评价报修" width="450px">
      <el-form label-width="80px">
        <el-form-item label="评分">
          <el-rate
            v-model="reviewForm.rating"
            :max="5"
            :texts="['非常差', '较差', '一般', '满意', '非常满意']"
            show-text
          />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="3"
            placeholder="请分享您的维修体验..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :disabled="reviewForm.rating === 0" :loading="reviewing" @click="handleReview">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- ============ 统计弹窗 ============ -->
    <el-dialog v-model="statsVisible" title="报修统计概览" width="480px">
      <template v-if="stats">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-statistic title="总报修数" :value="stats.totalOrders" />
          </el-col>
          <el-col :span="12">
            <el-statistic title="待处理" :value="stats.pendingCount" />
          </el-col>
          <el-col :span="12" style="margin-top: 20px">
            <el-statistic title="已完成" :value="stats.completedCount" />
          </el-col>
          <el-col :span="12" style="margin-top: 20px">
            <el-statistic title="完成率" :value="stats.completionRate + '%'" />
          </el-col>
          <el-col :span="12" style="margin-top: 20px">
            <el-statistic title="平均评分">
              <template #suffix>
                <el-rate :model-value="stats.avgRating" disabled show-score text-color="#ff9900" style="display: inline-flex" />
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="12" style="margin-top: 20px">
            <el-statistic title="平均响应时间" :value="stats.avgResponseTimeMinutes + ' 分钟'" />
          </el-col>
        </el-row>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createRepair, getRepairOrders, getRepairDetail,
  assignOrder, acceptOrder, completeOrder, cancelOrder, submitReview,
  getRepairStats, getAvailableMaintainers
} from '../api/repair'

const DEFAULT_USER_ID = 1

// ---- 角色切换 ----
const role = ref(localStorage.getItem('repairRole') || 'student')

function onRoleChange(val) {
  localStorage.setItem('repairRole', val)
  if (val === 'admin') {
    loadAdminOrders('PENDING')
  } else {
    loadMyOrders()
  }
}

// ---- 学生 Tab ----
const activeTab = ref('submit')

// ---- Tab 1: 提交报修 ----
const formRef = ref(null)
const submitting = ref(false)
const fileList = ref([])

const form = reactive({
  title: '',
  location: '',
  description: '',
  urgencyLevel: 'NORMAL'
})

const rules = {
  title: [
    { required: true, message: '请输入报修标题', trigger: 'blur' },
    { min: 2, max: 50, message: '标题长度为 2-50 个字符', trigger: 'blur' }
  ],
  location: [
    { required: true, message: '请输入报修地点', trigger: 'blur' },
    { min: 2, max: 100, message: '地点长度为 2-100 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入问题描述', trigger: 'blur' },
    { min: 10, max: 500, message: '描述长度为 10-500 个字符', trigger: 'blur' }
  ],
  urgencyLevel: [
    { required: true, message: '请选择紧急程度', trigger: 'change' }
  ]
}

function onExceed() {
  ElMessage.warning('最多只能上传 3 张图片')
}

function beforeUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('只能上传图片文件'); return false }
  if (!isLt5M) { ElMessage.error('图片大小不能超过 5MB'); return false }
  return false
}

function getImageUrls() {
  return fileList.value
    .filter(f => f.raw)
    .map(f => new Promise(resolve => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.readAsDataURL(f.raw)
    }))
}

function resetForm() {
  formRef.value?.resetFields()
  form.urgencyLevel = 'NORMAL'
  fileList.value = []
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const imageUrls = await Promise.all(getImageUrls())
    const res = await createRepair({
      title: form.title,
      description: form.description,
      location: form.location,
      urgencyLevel: form.urgencyLevel,
      imageUrls: imageUrls.length > 0 ? imageUrls : undefined
    })
    const data = res?.data || res
    ElMessage.success(`报修单提交成功！单号：${data.orderNo}`)
    resetForm()
    activeTab.value = 'my'
    loadMyOrders()
  } catch (e) {
    ElMessage.error('提交失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

// ---- Tab 2: 我的报修 ----
const myOrders = ref([])
const myLoading = ref(false)
const myTotal = ref(0)
const myFilter = reactive({ page: 1, pageSize: 10, status: '' })

async function loadMyOrders() {
  myLoading.value = true
  try {
    const res = await getRepairOrders({
      userId: DEFAULT_USER_ID,
      page: myFilter.page,
      pageSize: myFilter.pageSize,
      status: myFilter.status || undefined
    })
    const data = res?.data || res
    myOrders.value = data.records || []
    myTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    myLoading.value = false
  }
}

// ---- 管理员视图 ----
const adminSubTab = ref('pending')
const adminOrders = ref([])
const adminLoading = ref(false)

async function loadAdminOrders(filter) {
  adminLoading.value = true
  try {
    let statusParam = undefined
    if (filter === 'PENDING') {
      statusParam = 'PENDING'
    } else if (filter === 'in_progress') {
    } else if (filter === 'COMPLETED') {
      statusParam = 'COMPLETED'
    }
    const statuses = filter === 'in_progress' ? 'ASSIGNED,REPAIRING' : statusParam

    const res = await getRepairOrders({
      page: 1,
      pageSize: 50,
      status: statuses || undefined
    })
    const data = res?.data || res
    if (filter === 'in_progress') {
      const records = data.records || []
      adminOrders.value = records.filter(r => r.status === 'ASSIGNED' || r.status === 'REPAIRING')
    } else {
      adminOrders.value = data.records || []
    }
  } catch (e) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    adminLoading.value = false
  }
}

// ---- 详情 ----
const detailVisible = ref(false)
const detailData = ref(null)

async function showDetail(id) {
  try {
    const res = await getRepairDetail(id)
    detailData.value = res?.data || res
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('加载详情失败: ' + e.message)
  }
}

function parseImages(imageUrls) {
  if (!imageUrls) return []
  try {
    const parsed = typeof imageUrls === 'string' ? JSON.parse(imageUrls) : imageUrls
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

// ---- 取消 ----
function handleCancel(id) {
  ElMessageBox.confirm('确定要取消该报修单吗？', '确认取消', {
    confirmButtonText: '确定',
    cancelButtonText: '再想想',
    type: 'warning'
  }).then(async () => {
    try {
      await cancelOrder(id, DEFAULT_USER_ID)
      ElMessage.success('已取消')
      loadMyOrders()
    } catch (e) {
      ElMessage.error('取消失败: ' + e.message)
    }
  }).catch(() => {})
}

// ---- 派单 ----
const assignVisible = ref(false)
const assignOrderId = ref(null)
const assignOrderNo = ref('')
const assignOrderTitle = ref('')
const assignMaintainerId = ref(null)
const maintainers = ref([])

async function openAssign(orderId) {
  assignOrderId.value = orderId
  assignMaintainerId.value = null
  try {
    const res = await getRepairDetail(orderId)
    const data = res?.data || res
    assignOrderNo.value = data.orderNo
    assignOrderTitle.value = data.title
    const mRes = await getAvailableMaintainers()
    maintainers.value = mRes?.data || mRes || []
    assignVisible.value = true
  } catch (e) {
    ElMessage.error('加载派单信息失败: ' + e.message)
  }
}

async function handleAssign() {
  try {
    await assignOrder(assignOrderId.value, assignMaintainerId.value)
    ElMessage.success('派单成功')
    assignVisible.value = false
    loadAdminOrders(adminSubTab.value === 'pending' ? 'PENDING' : adminSubTab.value === 'completed' ? 'COMPLETED' : 'in_progress')
  } catch (e) {
    ElMessage.error('派单失败: ' + e.message)
  }
}

// ---- 接单 ----
async function handleAccept(orderId) {
  try {
    await acceptOrder(orderId, DEFAULT_USER_ID)
    ElMessage.success('已接单')
    loadAdminOrders('in_progress')
  } catch (e) {
    ElMessage.error('接单失败: ' + e.message)
  }
}

// ---- 完成 ----
async function handleComplete(orderId) {
  ElMessageBox.confirm('确认维修已完成？', '确认完成', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'success'
  }).then(async () => {
    try {
      await completeOrder(orderId, DEFAULT_USER_ID)
      ElMessage.success('维修已完成')
      loadAdminOrders('COMPLETED')
    } catch (e) {
      ElMessage.error('操作失败: ' + e.message)
    }
  }).catch(() => {})
}

// ---- 评价 ----
const reviewVisible = ref(false)
const reviewOrderId = ref(null)
const reviewing = ref(false)
const reviewForm = reactive({ rating: 0, comment: '' })

function openReview(orderId) {
  reviewOrderId.value = orderId
  reviewForm.rating = 0
  reviewForm.comment = ''
  reviewVisible.value = true
}

async function handleReview() {
  reviewing.value = true
  try {
    await submitReview(reviewOrderId.value, {
      userId: DEFAULT_USER_ID,
      rating: reviewForm.rating,
      comment: reviewForm.comment
    })
    ElMessage.success('评价成功')
    reviewVisible.value = false
    loadMyOrders()
  } catch (e) {
    ElMessage.error('评价失败: ' + e.message)
  } finally {
    reviewing.value = false
  }
}

// ---- 统计 ----
const statsVisible = ref(false)
const stats = ref(null)
const statsLoading = ref(false)
const headerStats = ref(null)

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getRepairStats()
    stats.value = res?.data || res
    statsVisible.value = true
  } catch (e) {
    ElMessage.error('加载统计失败: ' + e.message)
  } finally {
    statsLoading.value = false
  }
}

async function loadHeaderStats() {
  try {
    const res = await getRepairStats()
    headerStats.value = res?.data || res
  } catch (e) {}
}

// ---- 工具方法 ----
function statusType(status) {
  const map = { PENDING: 'info', ASSIGNED: '', REPAIRING: 'warning', COMPLETED: 'success', CANCELLED: 'danger' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { PENDING: '待派单', ASSIGNED: '已派单', REPAIRING: '维修中', COMPLETED: '已完成', CANCELLED: '已取消' }
  return map[status] || status
}

function urgencyLabel(level) {
  const map = { URGENT: '紧急', HIGH: '高', NORMAL: '普通', LOW: '低' }
  return map[level] || level || '-'
}

// ---- 初始化 ----
onMounted(() => {
  loadHeaderStats()
  if (role.value === 'admin') {
    loadAdminOrders('PENDING')
  } else {
    loadMyOrders()
  }
})
</script>

<style scoped>
.repair-view {
  height: calc(100vh - 44px);
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  transition: background 0.3s;
}

.repair-header {
  padding: 12px 24px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: background 0.3s;
}

.repair-header h2 {
  font-size: 18px;
  color: var(--text-primary);
  font-weight: 600;
}

.role-switch { flex-shrink: 0; }

.repair-tabs { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

.repair-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 24px;
  background: var(--bg-secondary);
}

.repair-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.tab-content { padding: 24px; max-width: 960px; margin: 0 auto; }

.repair-form { max-width: 600px; }

.filter-bar { display: flex; gap: 12px; margin-bottom: 16px; }

.upload-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 6px;
}

.timeline-wrap { margin-top: 4px; }
.image-gallery { margin-top: 4px; }
.image-list { display: flex; flex-wrap: wrap; }

/* ---- 统计卡片 ---- */
.stats-row {
  display: flex;
  gap: 12px;
  padding: 12px 24px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  transition: background 0.3s;
}

.stat-item {
  flex: 1;
  text-align: center;
  padding: 8px;
  border-radius: 8px;
  background: var(--bg-primary);
}

.stat-item.blue { background: var(--stat-blue-bg); }
.stat-item.orange { background: var(--stat-orange-bg); }
.stat-item.green { background: var(--stat-green-bg); }
.stat-item.yellow { background: var(--stat-yellow-bg); }
.stat-item.gray { background: var(--bg-primary); }

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-item.blue .stat-num { color: var(--stat-blue-text); }
.stat-item.orange .stat-num { color: var(--stat-orange-text); }
.stat-item.green .stat-num { color: var(--stat-green-text); }
.stat-item.yellow .stat-num { color: var(--stat-yellow-text); }
.stat-item.gray .stat-num { color: var(--text-tertiary); }

.stat-name {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}
</style>
