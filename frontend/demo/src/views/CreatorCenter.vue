<template>
  <div class="creator-center">
    <h1 class="page-title">⚙️ 创作者中心</h1>

    <!-- ========== 上传区域 ========== -->
    <section class="upload-section">
      <h2>📤 上传新视频</h2>

      <div class="upload-form">
        <div class="form-row">
          <div class="form-item">
            <label>视频标题 <span class="required">*</span></label>
            <input v-model="uploadForm.title" type="text" placeholder="给你的视频取个吸引人的标题..." />
          </div>
          <div class="form-item">
            <label>视频分类</label>
            <select v-model="uploadForm.categoryId">
              <option :value="0">全部</option>
              <option :value="1">音乐</option>
              <option :value="2">舞蹈</option>
              <option :value="3">游戏</option>
              <option :value="4">知识</option>
              <option :value="5">生活</option>
              <option :value="6">影视</option>
            </select>
          </div>
        </div>

        <div class="form-item">
          <label>视频描述</label>
          <textarea v-model="uploadForm.description" rows="3" placeholder="简单描述一下视频内容..."></textarea>
        </div>

        <!-- 视频文件上传 -->
        <div class="form-item" style="flex: none;">
          <label>视频文件 <span class="required">*</span></label>
          <div
            class="drop-zone"
            :class="{ 'dragover': isDragover, 'has-file': uploadForm.file }"
            @dragover.prevent="isDragover = true"
            @dragleave.prevent="isDragover = false"
            @drop.prevent="onDrop"
            @click="triggerFileInput">
            <input
              ref="fileInput"
              type="file"
              accept="video/*"
              style="display:none"
              @change="onFileSelected"
            />
            <template v-if="!uploadForm.file">
              <div class="drop-icon">📁</div>
              <p class="drop-text">拖拽视频文件到此处，或 <span class="link">点击选择文件</span></p>
              <p class="drop-hint">支持 MP4、AVI、MOV、MKV 等格式，建议使用 H.264 编码</p>
            </template>
            <template v-else>
              <div class="file-info">
                <div class="file-icon">🎬</div>
                <div class="file-detail">
                  <p class="file-name">{{ uploadForm.file.name }}</p>
                  <p class="file-size">{{ formatSize(uploadForm.file.size) }}</p>
                </div>
                <button class="btn-remove" @click.stop="removeFile">✕</button>
              </div>
            </template>
          </div>
        </div>

        <!-- 封面上传 -->
        <div class="form-item">
          <label>视频封面 <span class="optional">(可选)</span></label>
          <div class="cover-upload-row">
            <div class="cover-picker" @click.stop="triggerCoverInput">
              <input
                ref="coverInput"
                type="file"
                accept="image/*"
                style="display:none"
                @change="onCoverSelected"
              />
              <template v-if="!uploadForm.coverPreview">
                <span class="cover-placeholder">🖼️ 选择封面</span>
              </template>
              <template v-else>
                <img :src="uploadForm.coverPreview" class="cover-preview-img" />
              </template>
            </div>
            <button v-if="uploadForm.coverFile" class="btn-remove-cover" @click.stop="removeCover">✕ 移除</button>
          </div>
        </div>

        <!-- 上传按钮 + 进度 -->
        <div class="upload-actions">
          <button
            class="btn-upload"
            :disabled="!uploadForm.file || uploading"
            @click="startUpload">
            {{ uploading ? '上传中...' : uploadId ? '继续上传' : '开始上传' }}
          </button>
          <button v-if="uploading" class="btn-pause" @click="pauseUpload">暂停</button>
        </div>

        <!-- 进度条 -->
        <div v-if="uploading || uploadedChunks > 0" class="progress-section">
          <div class="progress-bar-wrap">
            <div class="progress-bar" :style="{ width: uploadPercent + '%' }"></div>
          </div>
          <div class="progress-info">
            <span>{{ uploadPercent }}%</span>
            <span>{{ uploadedChunks }} / {{ totalChunks }} 分片</span>
            <span>{{ formatSize(uploadedBytes) }} / {{ formatSize(uploadForm.file?.size || 0) }}</span>
          </div>
          <p v-if="uploadComplete" class="success-msg">✅ 上传完成！视频已发布。</p>
        </div>
      </div>
    </section>

    <!-- ========== 我的视频管理 ========== -->
    <section class="manage-section">
      <h2>📋 我的视频</h2>
      <p class="section-hint">管理你已上传的视频作品</p>

      <div class="video-table-wrap">
        <table class="video-table" v-if="myVideos.length > 0">
          <thead>
            <tr>
              <th style="width:40%">视频</th>
              <th>状态</th>
              <th>上传时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in myVideos" :key="v.id">
              <td class="td-title">
                <div class="video-mini-thumb">
                  <img v-if="v.coverUrl" :src="v.coverUrl.startsWith('http') ? v.coverUrl : (BASE + v.coverUrl)" class="thumb-cover" />
                  <video v-else-if="v.playUrl" :src="v.playUrl.startsWith('http') ? v.playUrl : (BASE + v.playUrl)" preload="metadata" class="thumb-video" />
                  <span v-else class="thumb-placeholder">🎬</span>
                </div>
                <div>
                  <p class="v-title">{{ v.title }}</p>
                  <p class="v-desc">{{ v.description || '暂无描述' }}</p>
                </div>
              </td>
              <td>
                <span class="status-badge" :class="v.status">{{ statusLabel(v.status) }}</span>
              </td>
              <td class="td-time">{{ formatTime(v.createdAt) }}</td>
              <td>
                <button class="btn-delete" @click="deleteVideo(v)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state">
          <p>📭 还没有上传任何视频</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import axios from 'axios'

const BASE = 'http://localhost:9090'
const CHUNK_SIZE = 5 * 1024 * 1024

const fileInput = ref(null)
const coverInput = ref(null)
const isDragover = ref(false)
const uploading = ref(false)
const uploadPaused = ref(false)
const uploadComplete = ref(false)

const uploadId = ref('')
const totalChunks = ref(0)
const uploadedChunks = ref(0)
const uploadedBytes = ref(0)
const videoId = ref(null)
const abortController = ref(null)

const myVideos = ref([])

const uploadForm = reactive({
  title: '',
  description: '',
  categoryId: 0,
  file: null,
  coverFile: null,
  coverPreview: '',
})

const uploadPercent = computed(() => {
  if (totalChunks.value === 0) return 0
  return Math.round((uploadedChunks.value / totalChunks.value) * 100)
})

function getToken() {
  return localStorage.getItem('loginToken') || ''
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

function statusLabel(s) {
  return { uploading: '上传中', processing: '处理中', published: '已发布', failed: '失败' }[s] || s
}

function triggerFileInput() {
  if (uploading.value) return
  fileInput.value?.click()
}

function onDrop(e) {
  isDragover.value = false
  const file = e.dataTransfer.files[0]
  if (file) setFile(file)
}

function onFileSelected(e) {
  const file = e.target.files[0]
  if (file) setFile(file)
}

function setFile(file) {
  if (!file.type.startsWith('video/')) {
    alert('请选择视频文件')
    return
  }
  uploadForm.file = file
  uploadId.value = ''
  totalChunks.value = Math.ceil(file.size / CHUNK_SIZE)
  uploadedChunks.value = 0
  uploadedBytes.value = 0
  uploadComplete.value = false
  videoId.value = null
}

function triggerCoverInput() {
  coverInput.value?.click()
}

function onCoverSelected(e) {
  const file = e.target.files[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  uploadForm.coverFile = file
  uploadForm.coverPreview = URL.createObjectURL(file)
}

function removeCover() {
  uploadForm.coverFile = null
  uploadForm.coverPreview = ''
  if (coverInput.value) coverInput.value.value = ''
}

function removeFile() {
  uploadForm.file = null
  uploadId.value = ''
  totalChunks.value = 0
  uploadedChunks.value = 0
  uploadedBytes.value = 0
  uploadComplete.value = false
  videoId.value = null
}

async function startUpload() {
  if (!uploadForm.file || !uploadForm.title) {
    return alert('请填写视频标题并选择文件')
  }

  uploading.value = true
  uploadPaused.value = false
  abortController.value = new AbortController()

  try {
    if (!uploadId.value) {
      await initUpload()
    }

    const file = uploadForm.file
    for (let i = uploadedChunks.value; i < totalChunks.value; i++) {
      if (uploadPaused.value) break

      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = file.slice(start, end)

      const formData = new FormData()
      formData.append('file', chunk, file.name + '.part' + i)
      formData.append('videoId', videoId.value)
      formData.append('chunkIndex', i)
      formData.append('totalChunks', totalChunks.value)
      formData.append('uploadId', uploadId.value)

      await axios.post(BASE + '/api/videos/chunk-upload', formData, {
        headers: {
          'Authorization': 'Bearer ' + getToken(),
          'Content-Type': 'multipart/form-data',
        },
        signal: abortController.value.signal,
      })

      uploadedChunks.value = i + 1
      uploadedBytes.value = end

      if ((i + 1) % 5 === 0) {
        saveProgress()
      }
    }

    if (!uploadPaused.value) {
      await completeUpload()
      uploadComplete.value = true
      clearProgress()
      loadMyVideos()
      // 2 秒后自动刷新页面
      setTimeout(() => {
        window.location.reload()
      }, 2000)
    }
  } catch (err) {
    if (err.name !== 'CanceledError' && err.name !== 'AbortError') {
      alert('上传出错: ' + (err.response?.data?.message || err.message))
    }
    saveProgress()
  } finally {
    uploading.value = false
  }
}

async function initUpload() {
  const res = await axios.post(BASE + '/api/videos/chunk-init', {
    title: uploadForm.title,
    description: uploadForm.description,
    categoryId: uploadForm.categoryId,
    fileName: uploadForm.file.name,
    fileSize: uploadForm.file.size,
    contentType: uploadForm.file.type,
    coverFile: uploadForm.coverFile ? uploadForm.coverFile.name : null,
  }, {
    headers: { 'Authorization': 'Bearer ' + getToken() }
  })

  const d = res.data.data
  videoId.value = d.videoId
  uploadId.value = d.uploadId
  totalChunks.value = Math.ceil(uploadForm.file.size / d.chunkSize)
  uploadedChunks.value = d.uploadedParts?.length || 0
  uploadedBytes.value = uploadedChunks.value * CHUNK_SIZE

  if (uploadForm.coverFile) {
    await uploadCover()
  }
}

async function uploadCover() {
  const formData = new FormData()
  formData.append('file', uploadForm.coverFile)
  formData.append('videoId', videoId.value)

  await axios.post(BASE + '/api/videos/upload-cover', formData, {
    headers: {
      'Authorization': 'Bearer ' + getToken(),
      'Content-Type': 'multipart/form-data',
    },
  })
}

async function completeUpload() {
  await axios.post(BASE + '/api/videos/chunk-complete', {
    videoId: videoId.value,
    uploadId: uploadId.value,
    totalChunks: totalChunks.value,
  }, {
    headers: { 'Authorization': 'Bearer ' + getToken() }
  })
}

function pauseUpload() {
  uploadPaused.value = true
  abortController.value?.abort()
  uploading.value = false
}

const PROGRESS_KEY = 'hangyin_upload_progress'

function saveProgress() {
  if (!videoId.value) return
  localStorage.setItem(PROGRESS_KEY, JSON.stringify({
    videoId: videoId.value,
    uploadId: uploadId.value,
    uploadedChunks: uploadedChunks.value,
    totalChunks: totalChunks.value,
    uploadedBytes: uploadedBytes.value,
    fileName: uploadForm.file?.name,
    fileSize: uploadForm.file?.size,
    title: uploadForm.title,
    description: uploadForm.description,
    categoryId: uploadForm.categoryId,
    coverPreview: uploadForm.coverPreview,
  }))
}

function clearProgress() {
  localStorage.removeItem(PROGRESS_KEY)
}

function restoreProgress() {
  const raw = localStorage.getItem(PROGRESS_KEY)
  if (!raw) return
  try {
    const p = JSON.parse(raw)
    videoId.value = p.videoId
    uploadId.value = p.uploadId
    uploadedChunks.value = p.uploadedChunks
    totalChunks.value = p.totalChunks
    uploadedBytes.value = p.uploadedBytes
    uploadForm.title = p.title
    uploadForm.description = p.description
    uploadForm.categoryId = p.categoryId
    uploadForm.file = { name: p.fileName, size: p.fileSize }
    if (p.coverPreview) uploadForm.coverPreview = p.coverPreview
  } catch (_) {}
}

async function loadMyVideos() {
  try {
    const res = await axios.get(BASE + '/api/videos/my?page=1&pageSize=50', {
      headers: { 'Authorization': 'Bearer ' + getToken() }
    })
    if (res.data.code === 200) {
      myVideos.value = res.data.data.list || []
    }
  } catch (_) {}
}

async function deleteVideo(v) {
  if (!confirm('确定删除「' + v.title + '」？此操作不可恢复。')) return
  try {
    const res = await axios.delete(BASE + '/api/videos/' + v.id, {
      headers: { 'Authorization': 'Bearer ' + getToken() }
    })
    if (res.data.code === 200) {
      loadMyVideos()
    } else {
      alert(res.data.message)
    }
  } catch (_) {}
}

onMounted(() => {
  restoreProgress()
  loadMyVideos()
})
</script>

<style scoped>
.creator-center {
  max-width: 1000px;
  margin: 0 auto;
}

.page-title {
  font-size: 24px;
  margin-bottom: 24px;
  color: #1a1a1a;
}

section {
  background: #fff;
  border-radius: 12px;
  padding: 28px;
  margin-bottom: 24px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

section h2 {
  font-size: 18px;
  color: #333;
  margin-bottom: 4px;
}

.section-hint {
  font-size: 13px;
  color: #999;
  margin-bottom: 20px;
}

.upload-form { margin-top: 20px; }

.form-row {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
}

.form-item {
  flex: 1;
  margin-bottom: 16px;
}

.form-item label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #555;
  margin-bottom: 6px;
}

.form-item input,
.form-item textarea,
.form-item select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  transition: border 0.2s;
}

.form-item input:focus,
.form-item textarea:focus,
.form-item select:focus {
  outline: none;
  border-color: #1890ff;
}

.required { color: #ff4757; }
.optional { color: #999; font-weight: normal; font-size: 12px; }

.drop-zone {
  border: 2px dashed #d9d9d9;
  border-radius: 12px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 20px;
}

.drop-zone:hover,
.drop-zone.dragover {
  border-color: #1890ff;
  background: #e6f7ff;
}

.drop-zone.has-file {
  border-style: solid;
  border-color: #52c41a;
  background: #f6ffed;
}

.drop-icon { font-size: 48px; margin-bottom: 12px; }

.drop-text { font-size: 15px; color: #666; }

.drop-text .link { color: #1890ff; }

.drop-hint { font-size: 12px; color: #bbb; margin-top: 8px; }

.file-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.file-icon { font-size: 36px; }

.file-detail { text-align: left; flex: 1; }

.file-name { font-size: 15px; font-weight: 500; color: #333; }

.file-size { font-size: 13px; color: #999; }

.btn-remove {
  background: #ff4d4f;
  color: #fff;
  border: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 14px;
}

.btn-remove-cover {
  background: transparent;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.btn-remove-cover:hover { background: #ff4d4f; color: #fff; }

.cover-upload-row { display: flex; align-items: center; gap: 12px; }

.cover-picker {
  width: 160px;
  height: 90px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  transition: border 0.2s;
}
.cover-picker:hover { border-color: #1890ff; }

.cover-placeholder { color: #bbb; font-size: 14px; }

.cover-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.btn-upload {
  background: #1890ff;
  color: #fff;
  border: none;
  padding: 12px 32px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: bold;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-upload:hover:not(:disabled) { background: #1166b5; }

.btn-upload:disabled {
  background: #d9d9d9;
  cursor: not-allowed;
}

.btn-pause {
  background: #faad14;
  color: #fff;
  border: none;
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 15px;
  cursor: pointer;
}

.progress-section { margin-top: 8px; }

.progress-bar-wrap {
  width: 100%;
  height: 10px;
  background: #f0f0f0;
  border-radius: 5px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #1890ff, #52c41a);
  border-radius: 5px;
  transition: width 0.3s;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #888;
  margin-top: 8px;
}

.success-msg {
  color: #52c41a;
  font-weight: 500;
  margin-top: 12px;
}

.video-table-wrap { overflow-x: auto; }

.video-table {
  width: 100%;
  border-collapse: collapse;
}

.video-table th {
  text-align: left;
  padding: 12px 16px;
  font-size: 13px;
  color: #999;
  border-bottom: 1px solid #f0f0f0;
}

.video-table td {
  padding: 14px 16px;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.td-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.video-mini-thumb {
  width: 80px;
  height: 45px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 6px;
  flex-shrink: 0;
}

.v-title { font-size: 14px; font-weight: 500; color: #333; margin-bottom: 2px; }

.v-desc { font-size: 12px; color: #bbb; }

.status-badge {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.published { background: #f6ffed; color: #52c41a; }
.status-badge.uploading { background: #fff7e6; color: #fa8c16; }
.status-badge.processing { background: #e6f7ff; color: #1890ff; }
.status-badge.failed { background: #fff2f0; color: #ff4d4f; }

.td-time { font-size: 13px; color: #999; white-space: nowrap; }

.btn-delete {
  background: transparent;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
  padding: 5px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.btn-delete:hover {
  background: #ff4d4f;
  color: #fff;
  border-color: #ff4d4f;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #ccc;
  font-size: 15px;
}
</style>
