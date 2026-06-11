<template>
  <div class="app-container">
    <header class="navbar" v-show="!showVideoPlayer">
      <div class="logo" @click="goHome">航音视频</div>

      <div class="search-box">
        <input
          v-model.trim="keyword"
          type="text"
          :placeholder="currentPage === 'live' || currentPage === 'live-room' ? '搜索直播间、主播...' : '搜索感兴趣的视频...'"
          @keyup.enter="handleSearch"
        />
        <button class="search-btn" @click="handleSearch">搜索</button>
      </div>

      <div class="user-actions">
        <button class="upload-btn" @click="handlePrimaryAction">
          {{ primaryActionText }}
        </button>

        <template v-if="!isLoggedIn">
          <button class="login-action-btn" @click="openLoginModal">登录</button>
        </template>

        <template v-else>
          <div class="user-profile">
            <div class="avatar">{{ avatarText }}</div>
            <span class="username">{{ currentUser }}</span>
            <button class="logout-btn" @click="logout">退出</button>
          </div>
        </template>
      </div>
    </header>

    <div class="main-layout" v-show="!showVideoPlayer">
      <aside class="sidebar">
        <ul class="nav-links">
          <li :class="{ active: currentPage === 'home' }" @click="setPage('home')">首页推荐</li>
          <li :class="{ active: currentPage === 'ranking' }" @click="setPage('ranking')">热门排行</li>
          <li :class="{ active: currentPage === 'live' || currentPage === 'live-room' }" @click="setPage('live')">直播大厅</li>
          <div class="divider"></div>
          <li>我的关注</li>
          <li>历史记录</li>
          <li :class="{ active: currentPage === 'creator' }" @click="setPage('creator')">创作者中心</li>
        </ul>
      </aside>

      <main class="content">
        <template v-if="currentPage === 'live-room' && selectedLiveRoom">
          <section class="page-panel">
            <div class="page-header">
              <div>
                <h2>{{ selectedLiveRoom.title }}</h2>
                <p>主播：{{ selectedLiveRoom.anchorNickname }} · {{ selectedLiveRoom.createdAtText }}</p>
              </div>
              <button class="refresh-btn" @click="setPage('live')">返回大厅</button>
            </div>

            <div class="live-room-layout">
              <div class="live-player-panel">
                <video ref="liveVideoRef" class="live-player" controls autoplay playsinline muted></video>
                <div v-if="!selectedLiveRoom.pullUrl" class="player-empty">等待主播推流后即可观看</div>
              </div>

              <aside class="live-side-panel">
                <div class="room-cover">
                  <img v-if="selectedLiveRoom.coverUrl" :src="selectedLiveRoom.coverUrl" alt="" />
                </div>
                <h3>{{ selectedLiveRoom.title }}</h3>
                <p>主播：{{ selectedLiveRoom.anchorNickname }}</p>
                <p>状态：{{ selectedLiveRoom.statusText }}</p>
                <p class="stream-url">播放地址：{{ selectedLiveRoom.pullUrl || '等待推流' }}</p>
              </aside>
            </div>
          </section>
        </template>

        <template v-else-if="currentPage === 'live'">
          <section class="page-panel">
            <div class="page-header">
              <div>
                <h2>直播大厅</h2>
                <p>正在开播的直播间会展示在这里。</p>
              </div>
              <div class="live-toolbar">
                <select v-model.number="liveCategoryFilter" class="category-select" @change="fetchLiveRooms">
                  <option :value="0">全部分区</option>
                  <option :value="1">娱乐生活</option>
                  <option :value="2">学习分享</option>
                  <option :value="3">音乐现场</option>
                  <option :value="4">聊天交流</option>
                </select>
                <button class="refresh-btn" @click="fetchLiveRooms">刷新</button>
              </div>
            </div>

            <div v-if="liveLoading" class="empty-state">正在加载直播间...</div>
            <div v-else-if="filteredLiveRooms.length === 0" class="empty-state">当前还没有可展示的直播间</div>

            <div v-else class="video-grid">
              <div class="video-card" v-for="room in filteredLiveRooms" :key="room.roomId" @click="openLiveRoom(room)">
                <div class="thumbnail live-thumbnail">
                  <img v-if="room.coverUrl" class="cover-image" :src="room.coverUrl" alt="" />
                  <span class="live-badge">直播中</span>
                  <span class="duration">{{ room.pullUrl ? '可观看' : '等待推流' }}</span>
                </div>
                <div class="info">
                  <h3 class="title">{{ room.title }}</h3>
                  <p class="author">主播：{{ room.anchorNickname }}</p>
                  <p class="stats">{{ room.createdAtText }}</p>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else-if="currentPage === 'creator'">
          <section class="page-panel">
            <div class="page-header">
              <div>
                <h2>创作者中心</h2>
                <p>上传本地视频，加入站内列表。</p>
              </div>
            </div>

            <div class="creator-card">
              <div class="creator-row">
                <label>视频标题</label>
                <input v-model.trim="uploadForm.title" type="text" placeholder="请输入视频标题" />
              </div>
              <div class="creator-row">
                <label>视频简介</label>
                <textarea v-model.trim="uploadForm.description" rows="4" placeholder="请输入视频简介"></textarea>
              </div>
              <div class="creator-row">
                <label>封面地址</label>
                <input v-model.trim="uploadForm.coverUrl" type="text" placeholder="可选，填写封面图片 URL" />
              </div>
              <div class="creator-row">
                <label>选择视频</label>
                <input type="file" accept="video/mp4,video/webm,video/ogg" @change="handleFileChange" />
              </div>
              <div class="creator-actions">
                <button class="confirm-btn" :disabled="uploadingVideo" @click="submitUpload">
                  {{ uploadingVideo ? '上传中...' : '上传视频' }}
                </button>
              </div>
              <p v-if="selectedUploadFile" class="creator-hint">已选择：{{ selectedUploadFile.name }}</p>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="page-panel">
            <div v-if="currentPage === 'ranking'" class="page-header">
              <div>
                <h2>热门排行</h2>
                <p>按播放热度排序。</p>
              </div>
            </div>

            <div class="video-grid">
              <div class="video-card" v-for="video in displayedVideos" :key="video.id" @click="openVideoPlayer(video)">
                <div class="thumbnail">
                  <img v-if="video.coverUrl" class="cover-image" :src="video.coverUrl" alt="" />
                  <span class="duration">{{ video.duration }}</span>
                </div>
                <div class="info">
                  <h3 class="title">{{ video.title }}</h3>
                  <p class="author">{{ video.author }}</p>
                  <p class="stats">{{ video.views }} 观看 · {{ video.date }}</p>
                </div>
              </div>
            </div>
          </section>
        </template>
      </main>
    </div>

    <VideoPlayer v-if="showVideoPlayer" :videoData="selectedVideo" @back="closeVideoPlayer" />

    <div class="modal-overlay" v-if="showModal" @click.self="closeModal">
      <div class="modal-content">
        <h2>{{ isRegisterMode ? '注册新账号' : '欢迎来到航音' }}</h2>
        <p class="subtitle">{{ isRegisterMode ? '加入我们，探索更多精彩内容' : '登录后即可投稿、开播和互动' }}</p>

        <div class="form-group">
          <input v-model="authForm.username" type="text" placeholder="请输入用户名" />
        </div>

        <div class="form-group" v-if="isRegisterMode">
          <input v-model="authForm.nickname" type="text" placeholder="请输入昵称" />
        </div>

        <div class="form-group">
          <input
            v-model="authForm.password"
            type="password"
            placeholder="请输入密码"
            @keyup.enter="isRegisterMode ? handleRegister() : handleLogin()"
          />
        </div>

        <div class="form-group" v-if="isRegisterMode">
          <input
            v-model="authForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            @keyup.enter="handleRegister"
          />
        </div>

        <div class="modal-actions">
          <button class="confirm-btn" @click="isRegisterMode ? handleRegister() : handleLogin()">
            {{ isRegisterMode ? '立即注册' : '登录' }}
          </button>
          <button class="cancel-btn" @click="closeModal">取消</button>
        </div>

        <div class="switch-mode">
          <a href="#" @click.prevent="toggleMode">
            {{ isRegisterMode ? '已有账号，直接登录' : '还没有账号，点击注册' }}
          </a>
        </div>
      </div>
    </div>

    <div class="modal-overlay" v-if="showLiveModal" @click.self="closeLiveModal">
      <div class="modal-content live-modal">
        <h2>开始直播</h2>
        <p class="subtitle">填写直播信息后获取推流地址，可使用 OBS 等工具推流。</p>

        <template v-if="!createdRoom">
          <div class="form-group">
            <label>直播标题</label>
            <input v-model="liveForm.title" type="text" placeholder="输入标题" />
          </div>

          <div class="form-group">
            <label>直播分类</label>
            <select v-model="liveForm.categoryId">
              <option :value="1">娱乐生活</option>
              <option :value="2">学习分享</option>
              <option :value="3">音乐现场</option>
              <option :value="4">聊天交流</option>
            </select>
          </div>

          <div class="form-group">
            <label>封面地址</label>
            <input v-model="liveForm.coverUrl" type="text" placeholder="可选，填写图片 URL" />
          </div>

          <div class="modal-actions">
            <button class="confirm-btn" :disabled="creatingLive" @click="createLiveRoom">
              {{ creatingLive ? '正在创建...' : '获取推流地址' }}
            </button>
            <button class="cancel-btn" @click="closeLiveModal">取消</button>
          </div>
        </template>

        <template v-else>
          <div class="stream-result">
            <div class="stream-field">
              <span>OBS 服务器</span>
              <strong>{{ createdRoom.pushServer }}</strong>
            </div>
            <div class="stream-field">
              <span>OBS 推流码</span>
              <strong>{{ createdRoom.streamKey }}</strong>
            </div>
            <div class="stream-field">
              <span>播放地址</span>
              <strong>{{ createdRoom.pullUrl }}</strong>
            </div>
            <div class="stream-field">
              <span>直播间号</span>
              <strong>{{ createdRoom.roomId }}</strong>
            </div>
          </div>

          <p class="hint">
            在 OBS 中选择自定义服务，把“OBS 服务器”填入服务器地址，把“OBS 推流码”填入串流密钥。
          </p>

          <div class="modal-actions">
            <button class="confirm-btn" @click="finishLiveCreate">完成</button>
            <button class="cancel-btn" @click="resetLiveCreate">继续创建</button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import flvjs from 'flv.js'
import VideoPlayer from './videocomponents/VideoPlayer.vue'

const API_BASE = 'http://localhost:8080/api'

const keyword = ref('')
const isLoggedIn = ref(false)
const showModal = ref(false)
const isRegisterMode = ref(false)
const currentUser = ref('')
const currentUserId = ref(null)
const showVideoPlayer = ref(false)
const selectedVideo = ref(null)
const currentPage = ref('home')
const selectedUploadFile = ref(null)
const uploadingVideo = ref(false)
const liveLoading = ref(false)
const liveRooms = ref([])
const selectedLiveRoom = ref(null)
const liveVideoRef = ref(null)
const flvPlayer = ref(null)
const showLiveModal = ref(false)
const creatingLive = ref(false)
const createdRoom = ref(null)
const liveCategoryFilter = ref(0)

const authForm = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: ''
})

const uploadForm = reactive({
  title: '',
  description: '',
  coverUrl: ''
})

const liveForm = reactive({
  title: '',
  categoryId: 1,
  coverUrl: ''
})

const fallbackVideos = [
  {
    id: 1,
    title: '【航音】2026届校园十佳歌手总决赛',
    author: '校学生会',
    views: '1.2万',
    duration: '02:15:30',
    date: '昨天',
    videoUrl: 'video-singing-contest-2026',
    sources: {
      '240P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '1080P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  }
]

const videoList = ref([])

const avatarText = computed(() => currentUser.value?.slice(0, 1) || '用')

const filteredVideos = computed(() => {
  const lowerKeyword = keyword.value.toLowerCase()
  if (!lowerKeyword) {
    return videoList.value
  }
  return videoList.value.filter((video) => {
    return video.title.toLowerCase().includes(lowerKeyword) || String(video.author || '').toLowerCase().includes(lowerKeyword)
  })
})

const displayedVideos = computed(() => {
  const list = filteredVideos.value
  if (currentPage.value === 'ranking') {
    return [...list].sort((a, b) => parseViews(b.views) - parseViews(a.views))
  }
  return list
})

const filteredLiveRooms = computed(() => {
  const selectedCategoryId = Number(liveCategoryFilter.value || 0)
  const categoryFilteredRooms = selectedCategoryId > 0
    ? liveRooms.value.filter((room) => Number(room.categoryId) === selectedCategoryId)
    : liveRooms.value
  const lowerKeyword = keyword.value.toLowerCase()
  if (!lowerKeyword) {
    return categoryFilteredRooms
  }
  return categoryFilteredRooms.filter((room) => {
    return room.title.toLowerCase().includes(lowerKeyword) || room.anchorNickname.toLowerCase().includes(lowerKeyword)
  })
})

const currentUserLiveRoom = computed(() => {
  if (!isLoggedIn.value || !currentUserId.value) {
    return null
  }
  return liveRooms.value.find((room) => Number(room.userId) === Number(currentUserId.value) && room.status !== 'offline') || null
})

const primaryActionText = computed(() => {
  if (currentPage.value === 'live' || currentPage.value === 'live-room') {
    return currentUserLiveRoom.value ? '关闭直播' : '开始直播'
  }
  return '投稿'
})

onMounted(async () => {
  restoreLoginState()
  window.addEventListener('hashchange', syncRouteFromHash)
  await loadVideoList()
  await syncRouteFromHash()
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRouteFromHash)
  destroyLivePlayer()
})

watch(
  () => selectedLiveRoom.value?.pullUrl,
  () => {
    if (currentPage.value === 'live-room') {
      setupLivePlayer()
    }
  }
)

const restoreLoginState = () => {
  const savedNickname = localStorage.getItem('loginUserNickname') || localStorage.getItem('loginUser')
  const savedUserId = localStorage.getItem('loginUserId')
  if (savedNickname && savedUserId) {
    isLoggedIn.value = true
    currentUser.value = savedNickname
    currentUserId.value = Number(savedUserId)
  }
}

const goHome = () => {
  setPage('home')
}

const setRouteHash = (page, roomId = null) => {
  const nextHash = page === 'live-room' && roomId ? `#/live/${roomId}` : `#/${page}`
  if (window.location.hash !== nextHash) {
    window.location.hash = nextHash
  }
}

const parseRouteFromHash = () => {
  const hashPath = window.location.hash.replace(/^#\/?/, '')
  if (!hashPath) {
    const savedPage = localStorage.getItem('currentPage') || 'home'
    return { page: savedPage === 'live-room' ? 'live' : savedPage }
  }

  const [page, roomId] = hashPath.split('/')
  if (page === 'live' && roomId) {
    return { page: 'live-room', roomId }
  }

  if (['home', 'ranking', 'live', 'creator'].includes(page)) {
    return { page }
  }

  return { page: 'home' }
}

const syncRouteFromHash = async () => {
  const route = parseRouteFromHash()
  if (route.page === 'live-room') {
    await loadLiveRoom(route.roomId)
    return
  }
  await setPage(route.page, false)
}

const setPage = async (page, updateRoute = true) => {
  currentPage.value = page
  keyword.value = ''
  if (page !== 'live-room') {
    selectedLiveRoom.value = null
    destroyLivePlayer()
  }
  localStorage.setItem('currentPage', page)
  if (updateRoute) {
    setRouteHash(page)
  }
  if (page === 'live') {
    await fetchLiveRooms()
  }
}

const openLoginModal = () => {
  showModal.value = true
  isRegisterMode.value = false
  resetForm()
}

const closeModal = () => {
  showModal.value = false
  resetForm()
}

const toggleMode = () => {
  isRegisterMode.value = !isRegisterMode.value
  resetForm()
}

const resetForm = () => {
  authForm.username = ''
  authForm.password = ''
  authForm.confirmPassword = ''
  authForm.nickname = ''
}

const openVideoPlayer = (video) => {
  selectedVideo.value = video
  showVideoPlayer.value = true
  window.scrollTo(0, 0)
}

const closeVideoPlayer = () => {
  showVideoPlayer.value = false
  selectedVideo.value = null
}

const handleRegister = async () => {
  if (!authForm.username || !authForm.password || !authForm.confirmPassword || !authForm.nickname) {
    alert('请填写完整的注册信息')
    return
  }
  if (authForm.password !== authForm.confirmPassword) {
    alert('两次输入的密码不一致，请重新输入')
    return
  }

  try {
    const res = await axios.post(`${API_BASE}/user/register`, {
      username: authForm.username,
      password: authForm.password,
      nickname: authForm.nickname
    })

    const message = typeof res.data === 'string' ? res.data : res.data?.message
    if (message?.includes('成功')) {
      alert('注册成功，请使用新账号登录')
      isRegisterMode.value = false
      authForm.password = ''
      authForm.confirmPassword = ''
      authForm.nickname = ''
    } else {
      alert(message || '注册失败')
    }
  } catch (error) {
    alert('网络请求失败，请检查后端是否启动')
  }
}

const normalizeLoginUser = (data) => {
  if (!data || typeof data !== 'object') {
    return null
  }
  if (data.id) {
    return data
  }
  if (data.user?.userId) {
    return {
      id: data.user.userId,
      username: data.user.username,
      nickname: data.user.nickname
    }
  }
  if (data.data?.user?.userId) {
    return {
      id: data.data.user.userId,
      username: data.data.user.username,
      nickname: data.data.user.nickname
    }
  }
  return null
}

const handleLogin = async () => {
  if (!authForm.username || !authForm.password) {
    alert('账号和密码不能为空')
    return
  }

  try {
    const res = await axios.post(`${API_BASE}/user/login`, {
      username: authForm.username,
      password: authForm.password
    })

    const loginUser = normalizeLoginUser(res.data)
    if (loginUser?.id) {
      isLoggedIn.value = true
      currentUser.value = loginUser.nickname || loginUser.username
      currentUserId.value = Number(loginUser.id)
      localStorage.setItem('loginUser', currentUser.value)
      localStorage.setItem('loginUserNickname', currentUser.value)
      localStorage.setItem('loginUserId', String(loginUser.id))
      showModal.value = false
      resetForm()
      return
    }

    alert(typeof res.data === 'string' ? res.data : res.data?.message || '登录失败')
  } catch (error) {
    alert('网络请求失败，请确保 Spring Boot 已启动')
  }
}

const logout = () => {
  isLoggedIn.value = false
  currentUser.value = ''
  currentUserId.value = null
  localStorage.removeItem('loginUser')
  localStorage.removeItem('loginUserNickname')
  localStorage.removeItem('loginUserId')
}

const convertVideoFromBackend = (video) => {
  const sources = {}
  if (video.url240p) sources['240P'] = video.url240p
  if (video.url360p) sources['360P'] = video.url360p
  if (video.url480p) sources['480P'] = video.url480p
  if (video.url720p) sources['720P'] = video.url720p
  if (video.url1080p) sources['1080P'] = video.url1080p
  if (Object.keys(sources).length === 0 && video.playUrl) {
    sources['720P'] = video.playUrl
  }

  return {
    id: video.videoId || video.id,
    videoId: video.videoId || video.id,
    title: video.title,
    author: video.author || '匿名用户',
    views: video.views || formatPlayCount(video.playCount || 0),
    duration: video.duration || '00:00',
    date: video.createdAt ? formatRelativeDate(video.createdAt) : '刚刚',
    videoUrl: video.videoUrl || `video-${video.id}`,
    playUrl: video.playUrl || '',
    coverUrl: video.coverUrl || '',
    description: video.description || '',
    sources,
    defaultQuality: video.defaultQuality || '720P',
    likeCount: video.likeCount || 0,
    favoriteCount: video.favoriteCount || 0,
    commentCount: video.commentCount || 0,
    playCount: video.playCount || 0,
    liked: video.liked || false,
    favorited: video.favorited || false
  }
}

const loadVideoList = async () => {
  try {
    const res = await axios.get(`${API_BASE}/videos/recommend`)
    if (res.data?.code === 200 && Array.isArray(res.data.data) && res.data.data.length > 0) {
      videoList.value = res.data.data
        .map(convertVideoFromBackend)
        .filter((video) => video.sources['720P'] || Object.values(video.sources).length > 0)
      return
    }
  } catch (error) {
    console.log('加载视频失败，使用示例数据')
  }

  videoList.value = fallbackVideos
}

const formatPlayCount = (count) => {
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1)}万`
  }
  return String(count)
}

const formatRelativeDate = (dateString) => {
  const date = new Date(dateString)
  const now = new Date()
  const diff = now - date
  if (Number.isNaN(date.getTime())) return '刚刚'
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
}

const formatLiveDate = (value) => {
  if (!value) {
    return '刚刚开播'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '刚刚开播'
  }

  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(
    date.getMinutes()
  ).padStart(2, '0')}`
}

const parseViews = (value) => {
  const text = String(value || '0')
  const number = Number.parseFloat(text)
  if (text.includes('万')) {
    return number * 10000
  }
  return number
}

const handleSearch = async () => {
  if (currentPage.value === 'live' || currentPage.value === 'live-room') {
    await fetchLiveRooms()
    return
  }
  await loadVideoList()
}

const handlePrimaryAction = async () => {
  if (!isLoggedIn.value) {
    alert('请先登录')
    openLoginModal()
    return
  }

  if (currentPage.value === 'live' || currentPage.value === 'live-room') {
    if (currentUserLiveRoom.value) {
      await closeCurrentLiveRoom()
      return
    }
    openLiveModal()
    return
  }

  await setPage('creator')
}

const handleFileChange = (event) => {
  const files = event.target.files
  selectedUploadFile.value = files && files[0] ? files[0] : null
}

const submitUpload = async () => {
  if (!isLoggedIn.value) {
    alert('请先登录后再投稿')
    return
  }
  if (!uploadForm.title) {
    alert('请填写视频标题')
    return
  }
  if (!selectedUploadFile.value) {
    alert('请选择一个视频文件')
    return
  }

  const formData = new FormData()
  formData.append('title', uploadForm.title)
  formData.append('description', uploadForm.description)
  formData.append('coverUrl', uploadForm.coverUrl)
  formData.append('author', currentUser.value || '匿名用户')
  formData.append('file', selectedUploadFile.value)

  uploadingVideo.value = true
  try {
    const res = await axios.post(`${API_BASE}/videos/upload`, formData)
    if (res.data?.code === 200 && res.data.data) {
      alert('上传成功，已加入视频列表')
      resetUploadForm()
      await loadVideoList()
      currentPage.value = 'home'
      setRouteHash('home')
    } else {
      alert(res.data?.message || '上传失败')
    }
  } catch (error) {
    alert(error.response?.data?.message || error.message || '上传失败')
  } finally {
    uploadingVideo.value = false
  }
}

const resetUploadForm = () => {
  uploadForm.title = ''
  uploadForm.description = ''
  uploadForm.coverUrl = ''
  selectedUploadFile.value = null
}

const normalizeRoom = (data) => {
  const raw = data?.data || data
  if (!raw || typeof raw !== 'object') {
    return null
  }

  return withRoomDisplayFields({
    roomId: raw.roomId || raw.id,
    userId: raw.userId,
    categoryId: raw.categoryId,
    anchorNickname: raw.anchorNickname || raw.nickname || (raw.userId ? `用户${raw.userId}` : '未知主播'),
    title: raw.title || '未命名直播间',
    pushUrl: raw.pushUrl || '',
    pullUrl: raw.pullUrl || raw.playUrl || '',
    coverUrl: raw.coverUrl || loadRoomCover(raw.roomId || raw.id),
    status: raw.status || 'online',
    createdAt: raw.createdAt || raw.createTime || new Date().toISOString()
  })
}

const withRoomDisplayFields = (room) => ({
  ...room,
  ...parsePushUrl(room.pushUrl),
  statusText: room.status === 'online' ? '直播中' : '已结束',
  createdAtText: formatLiveDate(room.createdAt)
})

const parsePushUrl = (pushUrl) => {
  if (!pushUrl) {
    return {
      pushServer: '',
      streamKey: ''
    }
  }

  const slashIndex = pushUrl.lastIndexOf('/')
  if (slashIndex <= 'rtmp://'.length) {
    return {
      pushServer: pushUrl,
      streamKey: ''
    }
  }

  return {
    pushServer: pushUrl.slice(0, slashIndex),
    streamKey: pushUrl.slice(slashIndex + 1)
  }
}

const saveLocalLiveRooms = (rooms) => {
  localStorage.setItem('liveRooms', JSON.stringify(rooms))
}

const loadLocalLiveRooms = () => {
  try {
    const rooms = JSON.parse(localStorage.getItem('liveRooms') || '[]')
    return rooms.map(withRoomDisplayFields).filter((room) => room.status !== 'offline')
  } catch (error) {
    return []
  }
}

const saveRoomCover = (roomId, coverUrl) => {
  if (!roomId || !coverUrl) {
    return
  }
  const coverMap = JSON.parse(localStorage.getItem('liveRoomCovers') || '{}')
  coverMap[String(roomId)] = coverUrl
  localStorage.setItem('liveRoomCovers', JSON.stringify(coverMap))
}

const loadRoomCover = (roomId) => {
  if (!roomId) {
    return ''
  }
  try {
    const coverMap = JSON.parse(localStorage.getItem('liveRoomCovers') || '{}')
    return coverMap[String(roomId)] || ''
  } catch (error) {
    return ''
  }
}

const fetchLiveRooms = async () => {
  liveLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/live/rooms`, {
      params: {
        page: 1,
        pageSize: 12,
        categoryId: liveCategoryFilter.value || 0
      }
    })
    const list = res.data?.data?.list || res.data?.list || []
    liveRooms.value = list.map(normalizeRoom).filter(Boolean).filter((room) => room.status !== 'offline')
    saveLocalLiveRooms(liveRooms.value)

    if (selectedLiveRoom.value) {
      const latestRoom = liveRooms.value.find((room) => Number(room.roomId) === Number(selectedLiveRoom.value.roomId))
      if (latestRoom) {
        selectedLiveRoom.value = latestRoom
      }
    }
  } catch (error) {
    liveRooms.value = loadLocalLiveRooms()
  } finally {
    liveLoading.value = false
  }
}

const upsertLiveRoom = (room) => {
  const nextRoom = withRoomDisplayFields(room)
  const index = liveRooms.value.findIndex((item) => Number(item.roomId) === Number(nextRoom.roomId))
  if (index >= 0) {
    liveRooms.value.splice(index, 1, nextRoom)
  } else {
    liveRooms.value.unshift(nextRoom)
  }
  saveLocalLiveRooms(liveRooms.value)
}

const removeLiveRoom = (roomId) => {
  liveRooms.value = liveRooms.value.filter((room) => Number(room.roomId) !== Number(roomId))
  saveLocalLiveRooms(liveRooms.value)
}

const openLiveRoom = async (room) => {
  selectedLiveRoom.value = withRoomDisplayFields(room)
  currentPage.value = 'live-room'
  localStorage.setItem('currentPage', 'live-room')
  setRouteHash('live-room', room.roomId)
  await nextTick()
  setupLivePlayer()
}

const loadLiveRoom = async (roomId) => {
  if (!roomId) {
    await setPage('live')
    return
  }

  let room = liveRooms.value.find((item) => Number(item.roomId) === Number(roomId))
  if (!room) {
    try {
      const res = await axios.get(`${API_BASE}/live/rooms/${roomId}`)
      room = normalizeRoom(res.data)
    } catch (error) {
      room = loadLocalLiveRooms().find((item) => Number(item.roomId) === Number(roomId))
    }
  }

  if (!room || room.status === 'offline') {
    alert('直播间不存在或已关闭')
    await setPage('live')
    return
  }

  selectedLiveRoom.value = room
  currentPage.value = 'live-room'
  localStorage.setItem('currentPage', 'live-room')
  await nextTick()
  setupLivePlayer()
}

const openLiveModal = () => {
  showLiveModal.value = true
  resetLiveCreate()
}

const closeLiveModal = () => {
  showLiveModal.value = false
  resetLiveCreate()
}

const resetLiveCreate = () => {
  liveForm.title = ''
  liveForm.categoryId = 1
  liveForm.coverUrl = ''
  createdRoom.value = null
  creatingLive.value = false
}

const createLiveRoom = async () => {
  if (!liveForm.title.trim()) {
    alert('请填写直播标题')
    return
  }

  creatingLive.value = true
  try {
    const res = await axios.post(
      `${API_BASE}/live/rooms`,
      {
        title: liveForm.title.trim(),
        categoryId: liveForm.categoryId,
        coverUrl: liveForm.coverUrl.trim(),
        userId: currentUserId.value
      },
      {
        headers: {
          'X-User-Id': currentUserId.value
        }
      }
    )

    const room = normalizeRoom(res.data)
    if (!room) {
      alert(res.data?.message || '创建直播间失败')
      return
    }

    room.coverUrl = room.coverUrl || liveForm.coverUrl.trim()
    saveRoomCover(room.roomId, room.coverUrl)
    createdRoom.value = room
    upsertLiveRoom(room)
    await setPage('live')
  } catch (error) {
    alert(error.response?.data?.message || '创建直播间失败，请检查后端直播接口是否启动')
  } finally {
    creatingLive.value = false
  }
}

const finishLiveCreate = async () => {
  const roomToOpen = createdRoom.value
  showLiveModal.value = false
  resetLiveCreate()
  await fetchLiveRooms()
  if (roomToOpen) {
    const latestRoom = liveRooms.value.find((room) => Number(room.roomId) === Number(roomToOpen.roomId)) || roomToOpen
    await openLiveRoom(latestRoom)
  }
}

const closeCurrentLiveRoom = async () => {
  const room = currentUserLiveRoom.value
  if (!room?.roomId) {
    alert('没有找到正在直播的直播间')
    await fetchLiveRooms()
    return
  }

  try {
    const res = await axios.post(`${API_BASE}/live/rooms/${room.roomId}/close`, null, {
      headers: {
        'X-User-Id': currentUserId.value
      }
    })
    const closedRoom = normalizeRoom(res.data) || { ...room, status: 'offline' }
    removeLiveRoom(closedRoom.roomId)
    if (selectedLiveRoom.value && Number(selectedLiveRoom.value.roomId) === Number(closedRoom.roomId)) {
      await setPage('live')
    }
    alert('直播已关闭')
  } catch (error) {
    alert(error.response?.data?.message || '关闭直播失败')
  }
}

const setupLivePlayer = () => {
  destroyLivePlayer()
  if (!liveVideoRef.value || !selectedLiveRoom.value?.pullUrl) {
    return
  }

  const pullUrl = selectedLiveRoom.value.pullUrl
  if (pullUrl.endsWith('.flv') && flvjs.isSupported()) {
    flvPlayer.value = flvjs.createPlayer({
      type: 'flv',
      url: pullUrl,
      isLive: true
    })
    flvPlayer.value.attachMediaElement(liveVideoRef.value)
    flvPlayer.value.load()
    flvPlayer.value.play().catch(() => {})
    return
  }

  liveVideoRef.value.src = pullUrl
  liveVideoRef.value.play().catch(() => {})
}

const destroyLivePlayer = () => {
  if (flvPlayer.value) {
    flvPlayer.value.pause()
    flvPlayer.value.unload()
    flvPlayer.value.detachMediaElement()
    flvPlayer.value.destroy()
    flvPlayer.value = null
  }
  if (liveVideoRef.value) {
    liveVideoRef.value.removeAttribute('src')
    liveVideoRef.value.load()
  }
}
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  background: #f9f9f9;
  color: #222;
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  border-bottom: 1px solid #eaeaea;
  background: #fff;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  color: #1e1e1e;
  cursor: pointer;
}

.search-box {
  display: flex;
  flex: 1;
  max-width: 420px;
  margin: 0 20px;
}

.search-box input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 4px 0 0 4px;
  outline: none;
}

.search-btn {
  padding: 8px 16px;
  background: #f0f0f0;
  border: 1px solid #ccc;
  border-left: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.upload-btn {
  background: #ff4757;
  color: #fff;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-weight: bold;
}

.login-action-btn {
  background: #e0e0e0;
  color: #333;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-weight: bold;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f0f0f0;
  border: 1px solid #ddd;
  font-size: 16px;
  font-weight: 700;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.logout-btn {
  background: transparent;
  border: 1px solid #ccc;
  color: #666;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.main-layout {
  display: flex;
  height: calc(100vh - 60px);
}

.sidebar {
  width: 220px;
  background-color: #fff;
  padding: 16px 0;
  border-right: 1px solid #f0f0f0;
  overflow-y: auto;
  flex-shrink: 0;
}

.nav-links {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-links li {
  padding: 12px 24px;
  color: #333;
  cursor: pointer;
  transition: background 0.2s;
}

.nav-links li:hover {
  background: #eee;
}

.nav-links li.active {
  background-color: #e6f7ff;
  color: #1890ff;
  font-weight: bold;
}

.divider {
  height: 1px;
  background: #ddd;
  margin: 15px 0;
}

.content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f9f9f9;
}

.page-panel {
  width: 100%;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 22px;
}

.page-header p {
  margin: 0;
  color: #777;
  font-size: 14px;
}

.live-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.category-select {
  min-width: 140px;
  padding: 7px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  color: #333;
  font-size: 14px;
  outline: none;
}

.category-select:focus {
  border-color: #1890ff;
}

.refresh-btn {
  background: #fff;
  border: 1px solid #d9d9d9;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.video-card {
  background: transparent;
  cursor: pointer;
  transition: transform 0.2s;
}

.video-card:hover {
  transform: translateY(-4px);
}

.thumbnail {
  width: 100%;
  aspect-ratio: 16 / 9;
  background-color: #d9d9d9;
  border-radius: 8px;
  position: relative;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.duration {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background-color: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 2px 6px;
  font-size: 12px;
  border-radius: 4px;
}

.live-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  background: #ff4757;
  color: #fff;
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 12px;
}

.live-thumbnail {
  background: linear-gradient(135deg, #ffd6dc, #dcefff);
}

.info {
  padding-top: 12px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin: 0 0 8px;
  color: #222;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.author,
.stats {
  font-size: 14px;
  color: #999;
  margin: 0 0 4px;
}

.empty-state {
  padding: 32px;
  background: #fff;
  border-radius: 8px;
  color: #777;
  text-align: center;
}

.creator-card,
.live-side-panel {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.creator-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.creator-row label,
.form-group label {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.creator-row input,
.creator-row textarea,
.form-group input,
.form-group select {
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 14px;
  outline: none;
  width: 100%;
  box-sizing: border-box;
}

.creator-actions {
  margin-top: 20px;
}

.creator-hint {
  margin-top: 12px;
  color: #666;
  font-size: 13px;
}

.live-room-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
}

.live-player-panel {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
}

.live-player {
  width: 100%;
  height: 100%;
  background: #000;
}

.player-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
}

.room-cover {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: #eef1f4;
  margin-bottom: 16px;
}

.room-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stream-url {
  word-break: break-all;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
}

.modal-content {
  background: #fff;
  padding: 32px;
  border-radius: 12px;
  width: 360px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
}

.live-modal {
  width: 560px;
  max-width: calc(100vw - 32px);
}

.modal-content h2 {
  margin: 0 0 5px;
  color: #333;
  font-size: 24px;
  text-align: center;
}

.subtitle {
  text-align: center;
  color: #888;
  font-size: 14px;
  margin-bottom: 25px;
}

.form-group {
  margin-bottom: 15px;
}

.modal-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.confirm-btn {
  background: #1890ff;
  color: #fff;
  border: none;
  padding: 12px;
  border-radius: 6px;
  font-size: 16px;
  cursor: pointer;
  font-weight: bold;
}

.confirm-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-btn {
  background: #f0f0f0;
  color: #666;
  border: none;
  padding: 10px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.switch-mode {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
}

.switch-mode a {
  color: #1890ff;
  text-decoration: none;
}

.stream-result {
  display: grid;
  gap: 12px;
}

.stream-field {
  display: grid;
  gap: 7px;
  padding: 12px;
  border: 1px solid #e1e5e9;
  border-radius: 8px;
  background: #f8fafc;
}

.stream-field span {
  color: #68717d;
  font-size: 13px;
  font-weight: 700;
}

.stream-field strong {
  overflow-wrap: anywhere;
  color: #1d2329;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.5;
}

.hint {
  margin: 16px 0 0;
  color: #68717d;
  font-size: 13px;
  line-height: 1.7;
}

@media screen and (max-width: 960px) {
  .live-room-layout {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .main-layout {
    display: block;
    height: auto;
  }

  .sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #f0f0f0;
  }

  .search-box {
    margin: 0 12px;
  }
}
</style>
