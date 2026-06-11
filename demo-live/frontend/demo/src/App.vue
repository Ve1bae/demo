<template>
  <div class="app-container">
    <header class="navbar">
      <div class="logo" @click="goHome">🚀 航音视频</div>

      <div class="search-box">
        <input v-model="keyword" type="text" :placeholder="searchPlaceholder" />
        <button class="search-btn">搜索</button>
      </div>

      <div class="user-actions">
        <button class="upload-btn" @click="handlePrimaryAction">
          {{ primaryLiveActionText }}
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

    <div class="main-layout">
      <aside class="sidebar">
        <ul class="nav-links">
          <li :class="{ active: currentPage === 'home' }" @click="setPage('home')">🏠 首页推荐</li>
          <li :class="{ active: currentPage === 'ranking' }" @click="setPage('ranking')">🔥 热门排行</li>
          <li :class="{ active: currentPage === 'live' || currentPage === 'live-room' }" @click="setPage('live')">📡 直播大厅</li>
          <div class="divider"></div>
          <li>❤️ 我的关注</li>
          <li>🕒 历史记录</li>
          <li>⚙️ 创作者中心</li>
        </ul>
      </aside>

      <main class="content">
        <!-- 直播间页面 -->
        <section v-if="currentPage === 'live-room' && selectedLiveRoom" class="page-panel">
          <div class="page-header">
            <div>
              <h1>{{ selectedLiveRoom.title }}</h1>
              <p>主播：{{ selectedLiveRoom.anchorNickname }} · {{ selectedLiveRoom.createdAtText }}</p>
            </div>
            <button class="secondary-action" @click="setPage('live')">返回大厅</button>
          </div>

          <div class="live-room-layout">
            <!-- 视频区域 + 弹幕层 -->
            <div class="player-panel" ref="playerPanelRef">
              <video ref="liveVideo" class="live-player" controls autoplay muted playsinline></video>
              <div v-if="!selectedLiveRoom.pullUrl" class="player-empty">等待主播推流后即可观看</div>
              <div class="video-danmu-layer"></div>
            </div>

            <!-- 右侧聊天区域 -->
            <aside class="room-side-panel" ref="sidePanelRef">
              <div class="chat-header">
                <span>💬 弹幕聊天</span>
                <div class="like-area">
                  <button @click="sendLike" class="like-btn">👍 {{ likeCount }}</button>
                </div>
                <span class="online-count">{{ onlineCount }} 人在线</span>
              </div>
              <div class="message-list" ref="messageListRef">
                <div v-for="msg in messages" :key="msg.id" class="message-item">
                  <span class="username">{{ msg.username }}：</span>
                  <span class="content">{{ msg.content }}</span>
                </div>
              </div>
              <div class="chat-input-area">
                <!-- 颜色按钮 -->
                <label class="color-btn">
                  颜色
                  <input type="color" v-model="danmuColor" style="position: absolute; opacity: 0; width: 0; height: 0;" />
                </label>
                <input type="text" v-model="inputMsg" @keyup.enter="sendMessage" placeholder="说点什么..." maxlength="100" />
                <button @click="sendMessage">发送</button>
              </div>
            </aside>
          </div>
        </section>

        <!-- 直播大厅 -->
        <section v-else-if="currentPage === 'live'" class="page-panel">
          <div class="page-header">
            <div><h1>直播大厅</h1><p>正在开播的直播间会按照首页推荐卡片样式展示。</p></div>
            <button class="secondary-action" @click="fetchLiveRooms">刷新</button>
          </div>
          <div v-if="liveLoading" class="state-panel">正在加载直播间...</div>
          <div v-else-if="liveRooms.length === 0" class="state-panel">还没有开播的直播间，登录后点击右上角“开始直播”创建一个。</div>
          <div v-else class="video-grid">
            <div class="video-card" v-for="room in liveRooms" :key="room.roomId" @click="openLiveRoom(room)">
              <div class="thumbnail live-thumbnail">
                <img v-if="room.coverUrl" class="cover-image" :src="room.coverUrl" alt="" />
                <span class="live-badge">直播中</span>
                <span class="duration">{{ room.statusText }}</span>
                <div class="live-wave" v-if="!room.coverUrl"><span></span><span></span><span></span></div>
              </div>
              <div class="info">
                <h3 class="title">{{ room.title }}</h3>
                <p class="author">主播：{{ room.anchorNickname }}</p>
                <p class="stats">{{ room.createdAtText }} · {{ room.pullUrl ? '可观看' : '等待推流' }}</p>
              </div>
            </div>
          </div>
        </section>

        <!-- 首页 / 排行 -->
        <section v-else class="page-panel">
          <div class="page-header" v-if="currentPage === 'ranking'"><div><h1>热门排行</h1><p>按近期热度整理的视频内容。</p></div></div>
          <div class="video-grid">
            <div class="video-card" v-for="video in displayedVideos" :key="video.id">
              <div class="thumbnail"><span class="duration">{{ video.duration }}</span></div>
              <div class="info">
                <h3 class="title">{{ video.title }}</h3>
                <p class="author">{{ video.author }}</p>
                <p class="stats">{{ video.views }} 观看 · {{ video.date }}</p>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>

    <!-- 登录/注册模态框 -->
    <div class="modal-overlay" v-if="showAuthModal" @click.self="closeAuthModal">
      <div class="modal-content">
        <h2>{{ isRegisterMode ? '注册新账号' : '欢迎来到航音' }}</h2>
        <p class="subtitle">{{ isRegisterMode ? '加入后即可发布内容和开启直播' : '登录后可以投稿、开播与互动' }}</p>
        <div class="form-group"><input v-model="authForm.username" type="text" placeholder="请输入用户名" /></div>
        <div class="form-group" v-if="isRegisterMode"><input v-model="authForm.nickname" type="text" placeholder="请输入昵称" /></div>
        <div class="form-group"><input v-model="authForm.password" type="password" placeholder="请输入密码" @keyup.enter="isRegisterMode ? handleRegister() : handleLogin()" /></div>
        <div class="form-group" v-if="isRegisterMode"><input v-model="authForm.confirmPassword" type="password" placeholder="请再次输入密码" @keyup.enter="handleRegister" /></div>
        <div class="modal-actions">
          <button class="confirm-btn" @click="isRegisterMode ? handleRegister() : handleLogin()">{{ isRegisterMode ? '立即注册' : '登录' }}</button>
          <button class="cancel-btn" @click="closeAuthModal">取消</button>
        </div>
        <div class="switch-mode"><a href="#" @click.prevent="toggleMode">{{ isRegisterMode ? '已有账号，直接登录' : '还没有账号，点击注册' }}</a></div>
      </div>
    </div>

    <!-- 开播模态框 -->
    <div class="modal-overlay" v-if="showLiveModal" @click.self="closeLiveModal">
      <div class="modal-content live-modal">
        <h2>开始直播</h2>
        <p class="subtitle">填写直播信息后获取推流地址，可使用 OBS 等工具推流。</p>
        <template v-if="!createdRoom">
          <div class="form-group"><label>直播标题</label><input v-model="liveForm.title" type="text" placeholder="输入标题" /></div>
          <div class="form-group"><label>直播分类</label><select v-model="liveForm.categoryId"><option :value="1">娱乐生活</option><option :value="2">学习分享</option><option :value="3">音乐现场</option><option :value="4">聊天交流</option></select></div>
          <div class="form-group"><label>封面地址</label><input v-model="liveForm.coverUrl" type="text" placeholder="可选，填写图片 URL" /></div>
          <div class="modal-actions"><button class="confirm-btn" :disabled="creatingLive" @click="createLiveRoom">{{ creatingLive ? '正在创建...' : '获取推流地址' }}</button><button class="cancel-btn" @click="closeLiveModal">取消</button></div>
        </template>
        <template v-else>
          <div class="stream-result">
            <div class="stream-field"><span>OBS 服务器</span><strong>{{ createdRoom.pushServer }}</strong></div>
            <div class="stream-field"><span>OBS 推流码</span><strong>{{ createdRoom.streamKey }}</strong></div>
            <div class="stream-field"><span>播放地址</span><strong>{{ createdRoom.pullUrl }}</strong></div>
            <div class="stream-field"><span>直播间号</span><strong>{{ createdRoom.roomId }}</strong></div>
          </div>
          <p class="hint">在 OBS 中选择自定义服务，把“OBS 服务器”填入服务器地址，把“OBS 推流码”填入串流密钥。这个直播间属于当前账号，之后再次开播仍会复用同一个房间号。</p>
          <div class="modal-actions"><button class="confirm-btn" @click="finishLiveCreate">完成</button><button class="cancel-btn" @click="resetLiveCreate">继续创建</button></div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import flvjs from 'flv.js'

const API_BASE = 'http://localhost:8080/api'

// ========== 原有通用变量 ==========
const keyword = ref('')
const currentPage = ref('home')
const isLoggedIn = ref(false)
const currentUser = ref('')
const currentUserId = ref(null)
const showAuthModal = ref(false)
const isRegisterMode = ref(false)
const showLiveModal = ref(false)
const liveLoading = ref(false)
const creatingLive = ref(false)
const liveRooms = ref([])
const createdRoom = ref(null)
const selectedLiveRoom = ref(null)
const liveVideo = ref(null)
const flvPlayer = ref(null)

// ========== 弹幕聊天相关 ==========
const messages = ref([])
const inputMsg = ref('')
const ws = ref(null)
const messageListRef = ref(null)
const onlineCount = ref(0)
const danmuColor = ref('#ffffff')
const likeCount = ref(0)

// ========== 同步右侧面板高度的相关引用 ==========
const playerPanelRef = ref(null)
const sidePanelRef = ref(null)
let resizeObserver = null

// 同步右侧面板高度与视频区域高度
const syncSidePanelHeight = () => {
  if (playerPanelRef.value && sidePanelRef.value) {
    const height = playerPanelRef.value.offsetHeight
    if (height > 0) {
      sidePanelRef.value.style.height = `${height}px`
      sidePanelRef.value.style.minHeight = `${height}px`
    }
  }
}

// 监听视频区域尺寸变化
const observePlayerSize = () => {
  if (!playerPanelRef.value) return
  if (resizeObserver) resizeObserver.disconnect()
  resizeObserver = new ResizeObserver(() => {
    syncSidePanelHeight()
  })
  resizeObserver.observe(playerPanelRef.value)
}

// 清理 ResizeObserver
const stopObservingPlayerSize = () => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
}

// ========== 原有弹幕方法 ==========
const addMessage = (username, content, color = '#409eff') => {
  if (!content || content.trim() === '') return
  messages.value.push({
    id: Date.now() + Math.random(),
    username: username,
    content: content,
    color: color
  })
  setTimeout(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  }, 50)
}

const addFloatingDanmu = (content, color = '#ffffff') => {
  if (!content) return
  const layer = document.querySelector('.video-danmu-layer')
  if (!layer) return

  const danmu = document.createElement('div')
  danmu.textContent = content
  danmu.style.position = 'absolute'
  danmu.style.whiteSpace = 'nowrap'
  danmu.style.fontSize = '18px'
  danmu.style.fontWeight = 'bold'
  danmu.style.textShadow = '1px 1px 2px black'
  danmu.style.backgroundColor = 'transparent'
  danmu.style.color = color
  danmu.style.padding = '4px 12px'
  danmu.style.borderRadius = '20px'
  danmu.style.pointerEvents = 'none'
  danmu.style.left = '100%'
  danmu.style.top = `${10 + Math.random() * 70}%`
  layer.appendChild(danmu)

  const duration = 15000
  const startTime = performance.now()
  const animate = (now) => {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    const left = 100 - progress * 200
    danmu.style.left = `${left}%`
    if (progress < 1) {
      requestAnimationFrame(animate)
    } else {
      if (danmu.parentNode) danmu.remove()
    }
  }
  requestAnimationFrame(animate)
}

const sendMessage = () => {
  if (!inputMsg.value.trim()) return
  if (!ws.value || ws.value.readyState !== WebSocket.OPEN) {
    console.warn('WebSocket 未连接')
    return
  }
  const message = {
    type: 'danmu',
    roomId: selectedLiveRoom.value?.roomId,
    userId: currentUserId.value,
    username: currentUser.value || '游客',
    content: inputMsg.value.trim(),
    color: danmuColor.value,
    timestamp: Date.now()
  }
  ws.value.send(JSON.stringify(message))
  inputMsg.value = ''
}

const sendLike = () => {
  if (!ws.value || ws.value.readyState !== WebSocket.OPEN) {
    console.warn('WebSocket 未连接，无法点赞')
    return
  }
  const message = {
    type: 'like',
    roomId: selectedLiveRoom.value?.roomId,
    userId: currentUserId.value,
    timestamp: Date.now()
  }
  ws.value.send(JSON.stringify(message))
}

const fetchLikeCount = async (roomId) => {
  try {
    const res = await axios.get(`${API_BASE}/live/rooms/${roomId}/like`)
    console.log('点赞数接口返回:', res.data)
    let count = 0
    if (res.data.data && typeof res.data.data.likeCount !== 'undefined') {
      count = res.data.data.likeCount
    } else if (res.data.likeCount !== undefined) {
      count = res.data.likeCount
    } else if (typeof res.data === 'number') {
      count = res.data
    } else if (res.data.count !== undefined) {
      count = res.data.count
    }
    likeCount.value = count
  } catch (error) {
    console.error('获取点赞数失败', error)
    likeCount.value = 0
  }
}

// ========== 新增：加载历史弹幕 ==========
const loadHistoryDanmu = async (roomId) => {
  try {
    const res = await axios.get(`${API_BASE}/danmu/history/${roomId}?limit=50`)
    const history = res.data.data || []
    // 历史弹幕按时间从旧到新依次添加
    history.forEach(d => {
      addMessage(d.username, d.content, d.color || '#409eff')
    })
    // 加载完成后滚动到底部
    setTimeout(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
      }
    }, 100)
  } catch (error) {
    console.error('加载历史弹幕失败', error)
  }
}

const connectWebSocket = () => {
  if (ws.value && ws.value.readyState === WebSocket.OPEN) return
  const roomId = selectedLiveRoom.value?.roomId
  if (!roomId) return
  const wsUrl = `ws://localhost:8080/ws/danmu/${roomId}`
  ws.value = new WebSocket(wsUrl)
  ws.value.onopen = () => console.log('弹幕 WebSocket 连接成功')
  ws.value.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'online_count') {
        onlineCount.value = data.count
        return
      }
      if (data.type === 'like') {
        likeCount.value = data.likeCount
        return
      }
      let username = data.username || '游客'
      let content = data.content || ''
      let color = data.color || '#ffffff'
      if (data.type === 'danmu') {
        content = data.content
        color = data.color || '#ffffff'
      }
      addFloatingDanmu(content, color)
      addMessage(username, content, color)
    } catch (e) {
      addFloatingDanmu(event.data, '#ffffff')
      addMessage('游客', event.data, '#ffffff')
    }
  }
  ws.value.onerror = (error) => console.error('WebSocket 错误', error)
  ws.value.onclose = () => {
    console.log('WebSocket 连接关闭，3 秒后重连...')
    setTimeout(() => connectWebSocket(), 3000)
  }
}

const disconnectWebSocket = () => {
  if (ws.value) {
    ws.value.close()
    ws.value = null
  }
}

// ========== 以下原有认证和直播逻辑保持不变 ==========
const authForm = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: ''
})

const liveForm = reactive({
  title: '',
  categoryId: 1,
  coverUrl: ''
})

const videoList = ref([
  { id: 1, title: '【航音】2026 届校园十佳歌手总决赛', author: '校学生会', views: '1.2万', duration: '02:15:30', date: '昨天' },
  { id: 2, title: '软件工程基础大作业避坑指南', author: '学长小王', views: '8000', duration: '12:05', date: '3天前' },
  { id: 3, title: 'Vue3 + Spring Boot 从零实战', author: '码农老李', views: '5万', duration: '45:20', date: '1周前' },
  { id: 4, title: '校园街访：你最喜欢的食堂是哪家', author: '航音生活圈', views: '3200', duration: '08:45', date: '5小时前' },
  { id: 5, title: '高数期末突击复习全集', author: '数学教研室', views: '10万', duration: '04:00:00', date: '1个月前' },
  { id: 6, title: '晚霞绝美，航拍延时摄影', author: '光影社', views: '1500', duration: '03:12', date: '刚刚' },
  { id: 7, title: '前端 CSS Grid 布局完全指南', author: 'TechBro', views: '2.3万', duration: '28:10', date: '2天前' },
  { id: 8, title: '宿舍日常：当你的室友是个极客', author: '602 寝室', views: '900', duration: '05:40', date: '10分钟前' }
])

const displayedVideos = computed(() => {
  if (currentPage.value !== 'ranking') return videoList.value
  return [...videoList.value].sort((a, b) => parseViews(b.views) - parseViews(a.views))
})

const primaryActionText = computed(() => (currentPage.value === 'live' ? '开始直播' : '投稿'))
const currentUserLiveRoom = computed(() => {
  if (!isLoggedIn.value || !currentUserId.value) return null
  return liveRooms.value.find(room => Number(room.userId) === Number(currentUserId.value) && isRoomOnline(room)) || null
})
const primaryLiveActionText = computed(() => {
  if (currentPage.value !== 'live') return primaryActionText.value
  return currentUserLiveRoom.value ? '关闭直播' : '开始直播'
})
const searchPlaceholder = computed(() => (currentPage.value === 'live' ? '搜索直播间、主播...' : '搜索感兴趣的视频...'))
const avatarText = computed(() => currentUser.value?.slice(0, 1) || '用')

onMounted(async () => {
  restoreLoginState()
  window.addEventListener('hashchange', syncRouteFromHash)
  await syncRouteFromHash()
  window.addEventListener('resize', syncSidePanelHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRouteFromHash)
  window.removeEventListener('resize', syncSidePanelHeight)
  destroyLivePlayer()
  disconnectWebSocket()
  stopObservingPlayerSize()
})

watch(
  () => selectedLiveRoom.value?.pullUrl,
  () => {
    if (currentPage.value === 'live-room') setupLivePlayer()
  }
)

watch(currentPage, (page) => {
  if (page === 'live-room') {
    setupLivePlayer()
    return
  }
  destroyLivePlayer()
})

const restoreLoginState = () => {
  const savedNickname = localStorage.getItem('loginUserNickname') || localStorage.getItem('loginUser')
  const savedUserId = localStorage.getItem('loginUserId')
  if (savedNickname && savedUserId) {
    isLoggedIn.value = true
    currentUser.value = savedNickname
    currentUserId.value = Number(savedUserId)
  }
}

const goHome = () => setPage('home')

const setPage = async (page, updateRoute = true) => {
  if (currentPage.value === 'live-room' && page !== 'live-room') {
    disconnectWebSocket()
    messages.value = []
    likeCount.value = 0
    stopObservingPlayerSize()
  }
  currentPage.value = page
  if (page !== 'live-room') selectedLiveRoom.value = null
  localStorage.setItem('currentPage', page)
  if (updateRoute) setRouteHash(page)
  if (page === 'live') await fetchLiveRooms()
}

const setRouteHash = (page, roomId = null) => {
  const nextHash = page === 'live-room' && roomId ? `#/live/${roomId}` : `#/${page}`
  if (window.location.hash !== nextHash) window.location.hash = nextHash
}

const parseRouteFromHash = () => {
  const hashPath = window.location.hash.replace(/^#\/?/, '')
  if (!hashPath) {
    const savedPage = localStorage.getItem('currentPage') || 'home'
    return { page: savedPage === 'live-room' ? 'live' : savedPage }
  }
  const [page, roomId] = hashPath.split('/')
  if (page === 'live' && roomId) return { page: 'live-room', roomId }
  if (['home', 'ranking', 'live'].includes(page)) return { page }
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

const openLoginModal = () => {
  showAuthModal.value = true
  isRegisterMode.value = false
  resetAuthForm()
}
const closeAuthModal = () => {
  showAuthModal.value = false
  resetAuthForm()
}
const toggleMode = () => {
  isRegisterMode.value = !isRegisterMode.value
  resetAuthForm()
}
const resetAuthForm = () => {
  authForm.username = ''
  authForm.password = ''
  authForm.nickname = ''
  authForm.confirmPassword = ''
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
      currentUserId.value = loginUser.id
      localStorage.setItem('loginUserNickname', currentUser.value)
      localStorage.setItem('loginUserId', String(loginUser.id))
      showAuthModal.value = false
      resetAuthForm()
      if (currentPage.value === 'live') await fetchLiveRooms()
      return
    }
    alert(typeof res.data === 'string' ? res.data : res.data?.message || '登录失败')
  } catch (error) {
    alert('网络请求失败，请确保 Spring Boot 已启动')
  }
}

const normalizeLoginUser = (data) => {
  if (!data || typeof data !== 'object') return null
  if (data.id) return data
  if (data.user?.userId) return { id: data.user.userId, username: data.user.username, nickname: data.user.nickname }
  if (data.data?.user?.userId) return { id: data.data.user.userId, username: data.data.user.username, nickname: data.data.user.nickname }
  return null
}

const logout = () => {
  isLoggedIn.value = false
  currentUser.value = ''
  currentUserId.value = null
  localStorage.removeItem('loginUserNickname')
  localStorage.removeItem('loginUserId')
  localStorage.removeItem('loginUser')
}

const handlePrimaryAction = async () => {
  if (currentPage.value !== 'live') {
    alert('投稿功能待接入')
    return
  }
  if (!isLoggedIn.value) {
    alert('请先登录后再开播')
    openLoginModal()
    return
  }
  if (currentUserLiveRoom.value) {
    await closeCurrentLiveRoom()
    return
  }
  openLiveModal()
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
      headers: { 'X-User-Id': currentUserId.value }
    })
    const closedRoom = normalizeRoom(res.data) || { ...room, status: 'offline' }
    removeLiveRoom(closedRoom.roomId)
    alert('直播已关闭')
  } catch (error) {
    alert('关闭直播失败，请检查后端关播接口是否启动')
  }
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
      { headers: { 'X-User-Id': currentUserId.value } }
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
    alert('创建直播间失败，请检查后端直播接口是否启动')
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
    openLiveRoom(liveRooms.value.find(room => Number(room.roomId) === Number(roomToOpen.roomId)) || roomToOpen)
  }
}

const fetchLiveRooms = async () => {
  liveLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/live/rooms`, {
      params: { page: 1, pageSize: 12, categoryId: 0 }
    })
    const list = res.data?.data?.list || res.data?.list || []
    liveRooms.value = list.map(normalizeRoom).filter(isRoomOnline)
    if (selectedLiveRoom.value) {
      const latestRoom = liveRooms.value.find(room => Number(room.roomId) === Number(selectedLiveRoom.value.roomId))
      if (latestRoom) selectedLiveRoom.value = latestRoom
    }
  } catch (error) {
    liveRooms.value = loadLocalLiveRooms()
  } finally {
    liveLoading.value = false
  }
}

const upsertLiveRoom = (room) => {
  const nextRoom = withRoomDisplayFields(room)
  const index = liveRooms.value.findIndex(item => item.roomId === nextRoom.roomId)
  if (index >= 0) liveRooms.value.splice(index, 1, nextRoom)
  else liveRooms.value.unshift(nextRoom)
  saveLocalLiveRooms(liveRooms.value)
}

const removeLiveRoom = (roomId) => {
  liveRooms.value = liveRooms.value.filter(room => Number(room.roomId) !== Number(roomId))
  saveLocalLiveRooms(liveRooms.value)
}

const openLiveRoom = (room) => {
  if (!room?.roomId) return
  selectedLiveRoom.value = withRoomDisplayFields(room)
  currentPage.value = 'live-room'
  localStorage.setItem('currentPage', 'live-room')
  setRouteHash('live-room', room.roomId)
  setupLivePlayer()
  disconnectWebSocket()
  // 清空现有消息，加载历史弹幕
  messages.value = []
  loadHistoryDanmu(room.roomId)
  connectWebSocket()
  fetchLikeCount(room.roomId)
  // 同步右侧面板高度
  nextTick(() => {
    syncSidePanelHeight()
    observePlayerSize()
  })
}

const loadLiveRoom = async (roomId) => {
  if (!roomId) {
    await setPage('live')
    return
  }
  let room = liveRooms.value.find(item => Number(item.roomId) === Number(roomId))
  if (!room) {
    try {
      const res = await axios.get(`${API_BASE}/live/rooms/${roomId}`)
      room = normalizeRoom(res.data)
    } catch (error) {
      room = loadLocalLiveRooms().find(item => Number(item.roomId) === Number(roomId))
    }
  }
  if (!room || !isRoomOnline(room)) {
    alert('直播间不存在或已关闭')
    await setPage('live')
    return
  }
  selectedLiveRoom.value = room
  currentPage.value = 'live-room'
  localStorage.setItem('currentPage', 'live-room')
  setupLivePlayer()
  disconnectWebSocket()
  messages.value = []
  loadHistoryDanmu(roomId)
  connectWebSocket()
  fetchLikeCount(roomId)
  nextTick(() => {
    syncSidePanelHeight()
    observePlayerSize()
  })
}

const normalizeRoom = (data) => {
  const raw = data?.data || data
  if (!raw || typeof raw !== 'object') return null
  return withRoomDisplayFields({
    roomId: raw.roomId || raw.id,
    userId: raw.userId,
    anchorNickname: raw.anchorNickname || raw.nickname || (raw.userId ? `用户${raw.userId}` : '未知主播'),
    title: raw.title || '未命名直播间',
    pushUrl: raw.pushUrl,
    pullUrl: raw.pullUrl || raw.playUrl,
    coverUrl: raw.coverUrl || loadRoomCover(raw.roomId || raw.id),
    status: raw.status || 'online',
    createdAt: raw.createdAt || raw.createTime || new Date().toISOString()
  })
}

const withRoomDisplayFields = (room) => ({
  ...room,
  ...parsePushUrl(room.pushUrl),
  statusText: room.status === 'online' ? 'LIVE' : '已结束',
  createdAtText: formatDate(room.createdAt)
})

const parsePushUrl = (pushUrl) => {
  if (!pushUrl) return { pushServer: '', streamKey: '' }
  const slashIndex = pushUrl.lastIndexOf('/')
  if (slashIndex <= 'rtmp://'.length) return { pushServer: pushUrl, streamKey: '' }
  return { pushServer: pushUrl.slice(0, slashIndex), streamKey: pushUrl.slice(slashIndex + 1) }
}

const loadLocalLiveRooms = () => {
  try {
    const rooms = JSON.parse(localStorage.getItem('liveRooms') || '[]')
    return rooms.map(withRoomDisplayFields).filter(isRoomOnline)
  } catch { return [] }
}

const saveLocalLiveRooms = (rooms) => {
  localStorage.setItem('liveRooms', JSON.stringify(rooms))
}

const loadRoomCover = (roomId) => {
  if (!roomId) return ''
  try {
    const coverMap = JSON.parse(localStorage.getItem('liveRoomCovers') || '{}')
    return coverMap[String(roomId)] || ''
  } catch { return '' }
}

const saveRoomCover = (roomId, coverUrl) => {
  if (!roomId || !coverUrl) return
  const coverMap = JSON.parse(localStorage.getItem('liveRoomCovers') || '{}')
  coverMap[String(roomId)] = coverUrl
  localStorage.setItem('liveRoomCovers', JSON.stringify(coverMap))
}

const isRoomOnline = (room) => Boolean(room && room.status !== 'offline')

const setupLivePlayer = async () => {
  await nextTick()
  destroyLivePlayer()
  const video = liveVideo.value
  const room = selectedLiveRoom.value
  if (!video || !room?.pullUrl) return
  if (room.pullUrl.endsWith('.flv') && flvjs.isSupported()) {
    flvPlayer.value = flvjs.createPlayer({ type: 'flv', url: room.pullUrl, isLive: true })
    flvPlayer.value.attachMediaElement(video)
    flvPlayer.value.load()
    flvPlayer.value.play()
    return
  }
  video.src = room.pullUrl
  video.play().catch(() => {})
}

const destroyLivePlayer = () => {
  if (flvPlayer.value) {
    flvPlayer.value.pause()
    flvPlayer.value.unload()
    flvPlayer.value.detachMediaElement()
    flvPlayer.value.destroy()
    flvPlayer.value = null
  }
  if (liveVideo.value) {
    liveVideo.value.removeAttribute('src')
    liveVideo.value.load()
  }
}

const formatDate = (value) => {
  if (!value) return '刚刚开播'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚开播'
  return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

const parseViews = (text) => {
  const value = Number.parseFloat(text)
  return text.includes('万') ? value * 10000 : value
}
</script>

<style scoped>
/* 原有样式完全保留，未做任何删除或修改，仅追加视频弹幕层必要的定位样式（不影响其他元素） */

.app-container {
  min-height: 100vh;
  color: #222;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, "Microsoft YaHei", sans-serif;
  background: #f7f8fa;
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 60px;
  padding: 0 22px;
  border-bottom: 1px solid #e8e8e8;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
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
  max-width: 440px;
  margin: 0 24px;
}

.search-box input {
  flex: 1;
  min-width: 0;
  padding: 9px 12px;
  border: 1px solid #d7dbe0;
  border-radius: 6px 0 0 6px;
  font-size: 14px;
  outline: none;
}

.search-box input:focus {
  border-color: #1890ff;
}

.search-btn {
  flex-shrink: 0;
  padding: 9px 16px;
  border: 1px solid #d7dbe0;
  border-left: 0;
  border-radius: 0 6px 6px 0;
  background: #f3f5f7;
  color: #333;
  cursor: pointer;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-btn,
.login-action-btn,
.logout-btn,
.secondary-action,
.confirm-btn,
.cancel-btn {
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 700;
  transition: background 0.2s, border-color 0.2s, color 0.2s, transform 0.2s;
}

.upload-btn {
  min-width: 86px;
  padding: 9px 16px;
  background: #ff4d5e;
  color: #fff;
}

.upload-btn:hover {
  background: #e83c4d;
}

.login-action-btn {
  padding: 9px 16px;
  background: #eef1f4;
  color: #333;
}

.login-action-btn:hover {
  background: #e1e5e9;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 9px;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid #d8dde3;
  border-radius: 50%;
  background: #f1f4f8;
  color: #1890ff;
  font-size: 14px;
  font-weight: 800;
}

.username {
  max-width: 120px;
  overflow: hidden;
  color: #333;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  padding: 6px 10px;
  border: 1px solid #d2d7dd;
  background: #fff;
  color: #666;
  font-size: 12px;
}

.logout-btn:hover {
  border-color: #ff4d5e;
  color: #ff4d5e;
}

.main-layout {
  display: flex;
  height: calc(100vh - 60px);
}

.sidebar {
  width: 220px;
  background-color: #ffffff;
  padding: 16px 0;
  border-right: 1px solid #f0f0f0;
  overflow-y: auto;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.nav-links {
  margin: 0;
  padding: 0;
  list-style: none;
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
  overflow-y: auto;
  background: #f7f8fa;
  padding: 22px;
}

.page-panel {
  width: 100%;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 6px;
  color: #1d2329;
  font-size: 24px;
}

.page-header p {
  margin: 0;
  color: #68717d;
  font-size: 14px;
}

.secondary-action {
  padding: 9px 16px;
  border: 1px solid #cfd6df;
  background: #fff;
  color: #333;
}

.secondary-action:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 22px;
}

.video-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.video-card:hover {
  transform: translateY(-4px);
}

.thumbnail {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 77, 94, 0.12), rgba(24, 144, 255, 0.12)),
    #dce1e7;
}

.thumbnail::before {
  position: absolute;
  inset: 18%;
  border: 2px solid rgba(255, 255, 255, 0.55);
  border-radius: 8px;
  content: "";
}

.duration,
.live-badge {
  position: absolute;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.duration {
  right: 8px;
  bottom: 8px;
  padding: 3px 7px;
  background: rgba(0, 0, 0, 0.72);
}

.live-badge {
  top: 8px;
  left: 8px;
  padding: 4px 8px;
  background: #ff3348;
}

.live-thumbnail {
  background:
    linear-gradient(135deg, rgba(255, 51, 72, 0.2), rgba(21, 93, 252, 0.16)),
    #dce1e7;
}

.cover-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.live-wave {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 5px;
  height: 42px;
  transform: translate(-50%, -50%);
}

.live-wave span {
  width: 7px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.9);
  animation: pulse 0.9s ease-in-out infinite;
}

.live-wave span:nth-child(1) {
  height: 22px;
}

.live-wave span:nth-child(2) {
  height: 38px;
  animation-delay: 0.12s;
}

.live-wave span:nth-child(3) {
  height: 28px;
  animation-delay: 0.24s;
}

.info {
  padding-top: 12px;
}

.title {
  display: -webkit-box;
  margin: 0 0 8px;
  overflow: hidden;
  color: #222;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.author,
.stats {
  margin: 0 0 4px;
  color: #89919b;
  font-size: 13px;
}

.state-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  border: 1px dashed #ccd4dd;
  border-radius: 8px;
  background: #fff;
  color: #68717d;
}

.live-room-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 22px;
  /* height 由内容撑开，不要固定高度 */
}

.player-panel {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  background: #101418;
  aspect-ratio: 16 / 9;
}

.video-danmu-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: hidden;
}

.live-player {
  width: 100%;
  height: 100%;
  background: #101418;
}

.player-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
}

.room-side-panel {
  min-width: 0;
  border: 1px solid #e4e8ed;
  border-radius: 8px;
  background: #fff;
  padding: 0;
  display: flex;
  flex-direction: column;
  /* 高度由 JS 动态设置，这里不设 height 或 min-height */
  overflow: hidden;
}

.chat-header {
  padding: 8px 12px;
  border-bottom: 1px solid #eee;
  font-weight: bold;
  flex-shrink: 0;
  background: #fff;
  display: flex !important;
  justify-content: space-between !important;
  align-items: center !important;
}
.online-count {
  font-size: 12px;
  color: #999;
  font-weight: normal;
  margin-left: auto !important;
  padding-right: 4px;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  background: #f9f9f9;
  min-height: 0;
}
.message-list::-webkit-scrollbar {
  width: 0 !important;
  background: transparent !important;
}
.message-item {
  margin-bottom: 2px !important;
  padding: 0 !important;
  font-size: 13px;
  line-height: 1.3 !important;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
}
.username {
  font-weight: bold;
  color: #409eff;
  margin-right: 6px;
}
.content {
  word-break: break-all;
}
.chat-input-area {
  display: flex;
  padding: 8px 12px;
  border-top: 1px solid #eee;
  gap: 8px;
  flex-shrink: 0;
  background: #fff;
}
.chat-input-area input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 13px;
}
.chat-input-area button {
  padding: 5px 14px;
  background: #409eff;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
}
.chat-input-area button:hover {
  background: #66b1ff;
}

.color-btn {
  position: relative;
  display: inline-block;
  padding: 5px 12px;
  background: #f0f0f0;
  border: 1px solid #ccc;
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.color-btn:hover {
  background: #e0e0e0;
}
.color-btn input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.like-area {
  margin-left: auto;
  margin-right: 12px;
}
.like-btn {
  background: none;
  border: none;
  font-size: 16px;
  cursor: pointer;
  color: #ff4d4f;
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: bold;
}
.like-btn:hover {
  opacity: 0.8;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(2px);
}

.modal-content {
  width: min(400px, 100%);
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(0, 0, 0, 0.22);
  padding: 34px;
}

.live-modal {
  width: min(560px, 100%);
}

.modal-content h2 {
  margin: 0 0 6px;
  color: #222;
  font-size: 24px;
  text-align: center;
}

.subtitle {
  margin: 0 0 24px;
  color: #7b8490;
  font-size: 14px;
  line-height: 1.6;
  text-align: center;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 7px;
  color: #4b5563;
  font-size: 13px;
  font-weight: 700;
}

.form-group input,
.form-group select {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 12px;
  border: 1px solid #d8dde3;
  border-radius: 6px;
  background: #fff;
  color: #222;
  font-size: 14px;
  outline: none;
}

.form-group input:focus,
.form-group select:focus {
  border-color: #1890ff;
}

.modal-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.confirm-btn {
  padding: 12px;
  background: #1890ff;
  color: #fff;
  font-size: 16px;
}

.confirm-btn:hover {
  background: #116dc2;
}

.confirm-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.cancel-btn {
  padding: 11px;
  background: #eef1f4;
  color: #59636f;
  font-size: 14px;
}

.cancel-btn:hover {
  background: #e1e5e9;
}

.switch-mode {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
}

.switch-mode a {
  color: #1890ff;
  text-decoration: none;
}

.switch-mode a:hover {
  text-decoration: underline;
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

@keyframes pulse {
  0%, 100% { transform: scaleY(0.68); }
  50% { transform: scaleY(1); }
}

@media screen and (max-width: 860px) {
  .navbar { flex-wrap: wrap; gap: 12px; padding: 12px 16px; }
  .search-box { order: 3; max-width: none; width: 100%; margin: 0; }
  .main-layout { height: auto; min-height: calc(100vh - 112px); }
  .sidebar { width: 180px; }
  .content { padding: 18px; }
  .live-room-layout { grid-template-columns: 1fr; }
}

@media screen and (max-width: 640px) {
  .main-layout { display: block; }
  .sidebar { width: 100%; border-right: 0; border-bottom: 1px solid #eceff2; padding: 8px 0; }
  .nav-links { display: flex; overflow-x: auto; }
  .nav-links li { flex: 0 0 auto; padding: 10px 14px; white-space: nowrap; }
  .divider { display: none; }
  .user-actions { gap: 8px; }
  .username { display: none; }
  .video-grid { grid-template-columns: 1fr; }
}

/* 以下为已有的强制覆盖样式（用户要求不能动） */
.room-side-panel .message-list .message-item {
  margin: 0 !important;
  margin-bottom: 2px !important;
  padding: 0 !important;
  line-height: 1.2 !important;
  overflow: visible !important;
}

.room-side-panel .message-list .message-item * {
  margin: 0 !important;
  padding: 0 !important;
  overflow: visible !important;
}

.room-side-panel .message-list .message-item .content {
  word-break: break-word !important;
  white-space: normal !important;
}

/* 新增用于动态高度同步的辅助样式，不破坏原有规则 */
.page-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.page-panel .live-room-layout {
  flex: 1;
  min-height: 0;
}
/* 滚动条样式（可选） */
.message-list::-webkit-scrollbar {
  width: 6px !important;
  background: #e0e0e0 !important;
}
.message-list::-webkit-scrollbar-thumb {
  background: #aaa !important;
  border-radius: 3px !important;
}
</style>