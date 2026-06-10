<template>
  <div class="video-player-container">
    <div class="back-btn" @click="goBack">
      <span class="back-icon">←</span> 返回
    </div>
    
    <div class="player-wrapper">
      <div class="video-area" ref="videoArea" @mousemove="handleMouseMove" @click="handleVideoClick">
        <video
          ref="videoRef"
          class="main-video"
          :src="currentSrc"
          playsinline
          preload="auto"
          @timeupdate="onTimeUpdate"
          @play="onPlay"
          @pause="onPause"
          @ended="onEnded"
          @volumechange="onVolumeChange"
          @loadedmetadata="onLoadedMetadata"
          @canplay="onCanPlay"
          @error="onVideoError"
          @waiting="onWaiting"
        ></video>

        <div class="danmaku-container" :class="{ hidden: !danmakuEnabled }" ref="danmakuContainer">
          <div
            v-for="danmaku in visibleDanmakuList"
            :key="danmaku.displayId"
            class="danmaku-item danmaku-scroll"
            :class="{ 'user-danmaku': danmaku.isUser }"
            :style="{ 
              top: (30 + danmaku.track * 40) + 'px', 
              '--duration': danmaku.duration + 's',
              opacity: danmakuOpacity / 100,
              color: resolveDanmakuColor(danmaku.color)
            }"
          >
            {{ danmaku.content }}
          </div>
        </div>

        <div class="controls-overlay" :class="{ 'show-controls': showControls, 'hide-controls': !showControls && isPlaying }">
          <div class="progress-area">
            <div 
              class="progress-bar" 
              @mousedown="startDragProgress"
              @click="seekVideo"
            >
              <div class="progress-bg"></div>
              <div class="progress-buffered" :style="{ width: bufferedProgress + '%' }"></div>
              <div class="progress-played" :style="{ width: currentProgress + '%' }">
                <div class="progress-thumb"></div>
              </div>
            </div>
          </div>

          <div class="danmaku-input-area">
            <button 
              :class="['danmaku-toggle', { disabled: !danmakuEnabled }]"
              @click="toggleDanmaku"
            >
              弹幕
            </button>
            <button class="danmaku-settings-btn" title="弹幕设置" @click="toggleDanmakuSettings">
              <span>⚙</span>
            </button>
            <div class="danmaku-settings-panel" v-if="showDanmakuSettings">
              <div class="setting-item">
                <span class="setting-label">透明度</span>
                <input 
                  type="range" 
                  min="0" 
                  max="100" 
                  v-model="danmakuOpacity"
                  class="setting-slider"
                />
                <span class="setting-value">{{ danmakuOpacity }}%</span>
              </div>
              <div class="setting-item">
                <span class="setting-label">速度</span>
                <div class="speed-options">
                  <button 
                    :class="['speed-btn', { active: danmakuSpeed === 0.6 }]"
                    @click="danmakuSpeed = 0.6"
                  >慢</button>
                  <button 
                    :class="['speed-btn', { active: danmakuSpeed === 1 }]"
                    @click="danmakuSpeed = 1"
                  >中</button>
                  <button 
                    :class="['speed-btn', { active: danmakuSpeed === 1.4 }]"
                    @click="danmakuSpeed = 1.4"
                  >快</button>
                </div>
              </div>
            </div>
            <div class="danmaku-color-picker">
              <button 
                v-for="color in danmakuColors" 
                :key="color.value"
                :class="['color-btn', { active: danmakuColor === color.value }]"
                :style="{ backgroundColor: color.hex }"
                @click="danmakuColor = color.value"
              ></button>
            </div>
            <input
              v-model="danmakuInput"
              class="danmaku-input"
              placeholder="在这里输入弹幕..."
              @keyup.enter="sendDanmaku"
            />
            <button class="send-danmaku-btn" @click="sendDanmaku">发送</button>
          </div>

          <div class="bottom-controls">
            <div class="left-controls">
              <button class="control-btn" @click="togglePlay">
                {{ isPlaying ? '⏸' : '▶' }}
              </button>
              
              <div class="volume-control">
                <button class="control-btn" @click="toggleMute">
                  {{ isMuted ? '🔇' : '🔊' }}
                </button>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.1"
                  v-model="volume"
                  @input="onVolumeInput"
                  class="volume-slider"
                />
              </div>

              <span class="time-display">
                {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
              </span>
            </div>

            <div class="right-controls">
              <div class="speed-control">
                <button class="control-btn small" @click="toggleSpeedDropdown">
                  <span class="speed-label">倍速</span>
                  <span class="speed-value">{{ playbackRate }}x</span>
                </button>
                <div class="speed-dropdown" :class="{ 'show-speed-dropdown': showSpeedDropdown }">
                  <button
                    v-for="speed in [0.5, 0.75, 1, 1.25, 1.5, 2]"
                    :key="speed"
                    class="speed-option"
                    :class="{ 'active': playbackRate === speed }"
                    @click="changeSpeed(speed)"
                  >
                    {{ speed }}x
                  </button>
                </div>
              </div>

              <div class="quality-control">
                <button class="control-btn small" @click="toggleQualityDropdown">
                  <span class="quality-label">清晰度</span>
                  <span class="quality-value">{{ currentQuality }}</span>
                </button>
                <div class="quality-dropdown" :class="{ 'show-quality-dropdown': showQualityDropdown }">
                  <button
                    v-for="q in qualities"
                    :key="q"
                    class="quality-option"
                    :class="{ 'active': currentQuality === q }"
                    @click="changeQuality(q)"
                  >
                    {{ q }}
                  </button>
                </div>
              </div>

              <button class="control-btn" @click="toggleFullscreen">
                ⛶
              </button>
            </div>
          </div>
        </div>

        <div class="play-button-overlay" v-show="currentTime === 0 && !isPlaying && !showControls" @click="togglePlay">
          <div class="big-play-btn">▶</div>
        </div>

        <div class="loading-overlay" v-show="isLoading">
          <div class="loading-spinner"></div>
          <span class="loading-text">正在切换 {{ currentQuality }}...</span>
        </div>
      </div>
    </div>

    <div class="video-info-area">
      <div class="video-title-row">
        <div class="video-title">{{ videoData.title }}</div>
        <!-- 点赞收藏按钮 -->
        <div class="video-actions">
          <button
            :class="['action-btn', 'like-btn', { active: liked }]"
            @click="toggleLike"
          >
            <span class="action-icon">{{ liked ? '❤️' : '🤍' }}</span>
            <span class="action-text">{{ formatNumber(likeCount) }}</span>
          </button>
          <button
            :class="['action-btn', 'favorite-btn', { active: favorited }]"
            @click="toggleFavorite"
          >
            <span class="action-icon">{{ favorited ? '⭐' : '☆' }}</span>
            <span class="action-text">{{ formatNumber(favoriteCount) }}</span>
          </button>
        </div>
      </div>
      <div class="video-meta">
        <span>{{ formatNumber(playCount) }} 播放</span>
        <span class="meta-divider">·</span>
        <span>{{ formatNumber(likeCount) }} 点赞</span>
        <span class="meta-divider">·</span>
        <span>{{ formatNumber(favoriteCount) }} 收藏</span>
        <span class="meta-divider">·</span>
        <span>{{ videoData.date }}</span>
      </div>
      <div class="video-author">
        <div class="author-avatar">👨‍💻</div>
        <div class="author-name">{{ videoData.author }}</div>
        <button class="follow-btn">+ 关注</button>
      </div>
    </div>

    <!-- 双击点赞动画 -->
    <div class="like-animation" v-if="showLikeAnimation">❤️</div>

    <!-- 评论区 -->
    <div class="comments-area">
      <div class="comments-header">
        <h3>评论</h3>
        <span class="comments-count">{{ commentsTotal }} 条</span>
      </div>

      <!-- 评论输入框 -->
      <div class="comment-input-area">
        <input
          v-model="commentInput"
          class="comment-input"
          placeholder="写下你的评论..."
          @keyup.enter="sendComment"
        />
        <button class="send-comment-btn" @click="sendComment">发送</button>
      </div>

      <!-- 评论列表 -->
      <div class="comments-list" ref="commentsList">
        <div
          v-for="comment in commentsListData"
          :key="comment.commentId"
          class="comment-item"
        >
          <div class="comment-avatar">👤</div>
          <div class="comment-content">
            <div class="comment-header">
              <span class="comment-nickname">{{ comment.user?.nickname || '匿名用户' }}</span>
              <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
            </div>
            <p class="comment-text">{{ comment.content }}</p>
            <div class="comment-actions">
              <button class="comment-action-btn" @click="likeComment(comment.commentId)">
                <span>👍</span>
                <span>{{ comment.likeCount || 0 }}</span>
              </button>
              <button class="comment-action-btn" @click="replyComment(comment)">
                <span>💬</span>
                <span>回复</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 加载更多 -->
        <div v-if="hasMoreComments" class="load-more">
          <button class="load-more-btn" @click="loadMoreComments">加载更多</button>
        </div>

        <!-- 暂无评论 -->
        <div v-if="commentsListData.length === 0 && !loadingComments" class="no-comments">
          <p>暂无评论，快来发表第一条评论吧！</p>
        </div>

        <!-- 加载中 -->
        <div v-if="loadingComments" class="loading-comments">
          <span>加载中...</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  videoData: {
    type: Object,
    default: () => ({
      id: 1,
      title: '【航音】2026届校园十佳歌手总决赛',
      author: '校学生会',
      views: '1.2万',
      duration: '02:15:30',
      date: '昨天',
      // 视频唯一标识（用于弹幕数据库关联）
      videoUrl: 'video-demo-bigbuckbunny',
      // 各清晰度视频源（由上传系统提供）
      sources: {
        '240P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '720P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '1080P': 'https://www.w3schools.com/html/mov_bbb.mp4'
      },
      // 推荐清晰度
      defaultQuality: '720P'
    })
  }
})

const emit = defineEmits(['back'])

const videoRef = ref(null)
const videoArea = ref(null)
const danmakuContainer = ref(null)

const isLoggedIn = ref(!!localStorage.getItem('loginUserNickname'))
const currentUserId = ref(localStorage.getItem('loginUserId'))

const isPlaying = ref(false)
const isVideoReady = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(1)
const isMuted = ref(false)
const playbackRate = ref(1)
const currentProgress = ref(0)
const bufferedProgress = ref(0)
const showControls = ref(true)
const isLoading = ref(false)
const isDraggingProgress = ref(false)
const danmakuEnabled = ref(true)
const danmakuOpacity = ref(100)
const danmakuSpeed = ref(1)

// 动态生成清晰度选项（根据视频提供的清晰度）
const qualities = computed(() => {
  if (props.videoData && props.videoData.sources) {
    return Object.keys(props.videoData.sources)
  }
  return ['720P']
})

// 当前清晰度（从视频数据推荐值初始化）
const currentQuality = ref(props.videoData.defaultQuality || '1080P')
const showQualityDropdown = ref(false)
const showSpeedDropdown = ref(false)

const danmakuInput = ref('')
const danmakuColors = [
  { value: '#ffffff', hex: '#ffffff' },
  { value: '#ff0000', hex: '#ff0000' },
  { value: '#ffff00', hex: '#ffff00' },
  { value: '#00ff00', hex: '#00ff00' },
  { value: '#00a0ff', hex: '#00a0ff' },
  { value: '#ff00ff', hex: '#ff00ff' }
]
const danmakuColor = ref('#ffffff')
const danmakuLegacyColorMap = {
  '1': '#ffffff',
  '2': '#ff0000',
  '3': '#ffff00',
  '4': '#00ff00',
  '5': '#00a0ff',
  '6': '#ff00ff'
}

// ========== 评论相关变量 ==========
const commentsList = ref(null)
const commentsListData = ref([])
const commentInput = ref('')
const commentsTotal = ref(0)
const currentCommentPage = ref(1)
const commentPageSize = 20
const hasMoreComments = ref(true)
const loadingComments = ref(false)

// ========== 点赞收藏相关变量 ==========
const liked = ref(props.videoData.liked || false)
const favorited = ref(props.videoData.favorited || false)
const likeCount = ref(props.videoData.likeCount || 0)
const favoriteCount = ref(props.videoData.favoriteCount || 0)
const playCount = ref(props.videoData.playCount || 0)
const showLikeAnimation = ref(false)
const hasIncrementedPlayCount = ref(false) // 防止重复增加播放量

// ========== 弹幕相关变量 ==========
const danmakuList = ref([])
const visibleDanmakuList = ref([])
let danmakuIdCounter = 1
let danmakuTracks = [null, null, null, null, null, null, null, null, null, null] // 增加到10条轨道
const displayedDanmakuIds = new Set() // 记录已显示的弹幕ID，防止重复显示

// 关键变量：用于 requestAnimationFrame 精确同步
let rafId = null
let danmakuListSorted = [] // 按时间排序的弹幕数组（用于快速遍历）
let danmakuIndex = 0 // 当前弹幕指针，追踪下一个应该检查的弹幕

// ✅ 使用 props 中的视频源配置（由上传系统提供）
// videoUrl: 视频唯一标识，用于弹幕数据库关联
// sources: 各清晰度视频源URL
const videoUrl = computed(() => props.videoData.videoUrl || props.videoData.id || 'video-' + Date.now())

// 当前播放视频源（根据清晰度动态切换）
const currentSrc = computed(() => {
  console.log('=== 视频源调试信息 ===')
  console.log('videoData:', props.videoData)
  console.log('sources:', props.videoData?.sources)
  console.log('currentQuality:', currentQuality.value)
  console.log('defaultQuality:', props.videoData?.defaultQuality)
  
  const sources = props.videoData?.sources || {}
  const result = sources[currentQuality.value] || sources['720P'] || Object.values(sources)[0] || ''
  console.log('最终视频URL:', result)
  return result
})

// ========== 弹幕排序（优化性能）==========
const sortDanmakuByTime = () => {
  danmakuListSorted = [...danmakuList.value].sort((a, b) => a.time - b.time)
}

const resolveDanmakuColor = (color) => {
  if (!color) {
    return '#ffffff'
  }

  return danmakuLegacyColorMap[color] || color
}

// ========== 初始化测试弹幕数据 ==========
const initMockDanmaku = () => {
  const mockDanmakuData = [
    { time: 1, content: '开始了开始了！', color: '1' },
    { time: 3, content: '前排！', color: '2' },
    { time: 5, content: '这届选手很强啊', color: '3' },
    { time: 8, content: '加油加油！', color: '4' },
    { time: 10, content: '神仙打架', color: '5' },
    { time: 12, content: '太好听了！', color: '6' },
    { time: 15, content: '期待冠军', color: '1' },
    { time: 20, content: '666666', color: '2' }
  ]
  
  mockDanmakuData.forEach(mock => {
    if (!danmakuList.value.find(d => d.time === mock.time && d.content === mock.content)) {
      const newDanmaku = {
        id: danmakuIdCounter++,
        content: mock.content,
        color: mock.color,
        time: mock.time,
        isUser: false
      }
      danmakuList.value.push(newDanmaku)
    }
  })
  sortDanmakuByTime() // 每次添加后排序
}

// ========== 从服务器加载弹幕（按照 API 规范调用）==========
const loadDanmakuFromServer = async () => {
  try {
    // 使用 videoId 调用标准 API
    const videoId = props.videoData.id
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/danmakus?startTime=0&endTime=${duration.value || 3600}`)
    const result = await response.json()
    
    // 按照 API 规范，响应格式为 { code, message, data }
    if (result.code !== 200 || !result.data) {
      console.warn('加载弹幕失败:', result.message)
      return
    }
    
    const data = result.data
    
    data.forEach(item => {
      if (!danmakuList.value.find(d => d.id === item.id)) {
        danmakuList.value.push({
          id: item.id,
          content: item.content,
          color: item.color,
          time: item.time,
          userId: item.userId,
          isUser: item.isUser
        })
      }
    })
    sortDanmakuByTime() // 每次添加后排序
    
    // ✅ 移除错误的跳过逻辑，弹幕同步由 requestAnimationFrame 在播放时处理
  } catch (error) {
    console.error('加载弹幕失败:', error)
  }
}

// ========== 弹幕滚动速度（像素/秒）==========
const SCROLL_SPEED = 180 // px/秒（基础速度）

// ========== 动态计算弹幕动画时长 ==========
const getDanmakuDuration = (text) => {
  // 获取视频容器宽度
  const videoArea = document.querySelector('.video-area')
  const containerWidth = videoArea ? videoArea.offsetWidth : 1200
  
  // 弹幕宽度：每个中文字符约16px
  const charWidth = 16
  const danmakuWidth = text.length * charWidth
  
  // 总移动距离 = 容器宽度 + 弹幕宽度
  const totalDistance = containerWidth + danmakuWidth
  
  // 计算时长，考虑弹幕速度控制
  // 速度越快，时长越短
  const speedMultiplier = danmakuSpeed.value || 1
  const baseDuration = totalDistance / SCROLL_SPEED
  const duration = baseDuration / speedMultiplier
  
  // 限制时长范围：最小0.5秒，最大15秒
  return Math.max(0.5, Math.min(15, duration))
}

// ========== 获取可用轨道 ==========
const getAvailableTrack = (duration) => {
  let trackIndex = 0
  const now = Date.now()
  const durationMs = duration * 1000 // 转换为毫秒
  for (let i = 0; i < danmakuTracks.length; i++) {
    if (!danmakuTracks[i] || now - danmakuTracks[i] > durationMs) {
      trackIndex = i
      break
    }
  }
  danmakuTracks[trackIndex] = now
  return trackIndex
}

// ========== 显示一条弹幕 ==========
const showSingleDanmaku = (danmaku) => {
  const duration = getDanmakuDuration(danmaku.content)
  const track = getAvailableTrack(duration)
  const newVisibleDanmaku = {
    ...danmaku,
    track: track,
    duration: duration,
    displayId: `${danmaku.id}-${Date.now()}-${Math.random()}`
  }
  visibleDanmakuList.value.push(newVisibleDanmaku)
  displayedDanmakuIds.add(danmaku.id) // ✅ 标记为已显示
  
  // 动画结束后自动移除（加100ms缓冲）
  setTimeout(() => {
    const index = visibleDanmakuList.value.findIndex(d => d.displayId === newVisibleDanmaku.displayId)
    if (index > -1) {
      visibleDanmakuList.value.splice(index, 1)
    }
  }, duration * 1000 + 100)
}

// ========== 检查并显示弹幕（在requestAnimationFrame中调用）==========
const checkAndShowDanmaku = () => {
  if (!videoRef.value || videoRef.value.paused || videoRef.value.ended) {
    return
  }
  
  const currentVideoTime = videoRef.value.currentTime
  
  // 调试日志
  console.log('[弹幕同步] 当前时间:', currentVideoTime.toFixed(2), '指针:', danmakuIndex, '总数:', danmakuListSorted.length)
  
  // 检查当前指针位置及之后的弹幕
  while (danmakuIndex < danmakuListSorted.length && danmakuListSorted[danmakuIndex].time <= currentVideoTime) {
    const danmaku = danmakuListSorted[danmakuIndex]
    // ✅ 检查是否已经显示过，防止重复显示
    if (!displayedDanmakuIds.has(danmaku.id)) {
      console.log('[弹幕显示]', danmaku.time, danmaku.content)
      showSingleDanmaku(danmaku)
      displayedDanmakuIds.add(danmaku.id)
    }
    danmakuIndex++
  }
}

// ========== 弹幕同步主循环（使用requestAnimationFrame实现60fps精确同步）==========
const danmakuSyncLoop = () => {
  if (!videoRef.value || videoRef.value.paused || videoRef.value.ended) {
    rafId = null
    return
  }
  
  checkAndShowDanmaku()
  rafId = requestAnimationFrame(danmakuSyncLoop)
}

// ========== 启动弹幕同步 ==========
const startDanmakuSync = () => {
  if (rafId) return
  rafId = requestAnimationFrame(danmakuSyncLoop)
}

// ========== 停止弹幕同步 ==========
const stopDanmakuSync = () => {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
}

// ========== 重置弹幕状态（快退时调用）==========
const resetDanmakuState = () => {
  visibleDanmakuList.value = []
  danmakuIndex = 0
  danmakuTracks = [null, null, null, null, null, null, null, null, null, null]
  displayedDanmakuIds.clear() // ✅ 清空已显示记录
}

const hideControlsTimer = ref(null)

const formatTime = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 格式化数字（如 1.2万）
const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toString()
}

// ========== 增加播放量（只增加一次）==========
const incrementPlayCount = async () => {
  if (hasIncrementedPlayCount.value) return
  hasIncrementedPlayCount.value = true

  const videoId = props.videoData.id
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/play`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      }
    })
    const result = await response.json()
    if (result.code === 200) {
      playCount.value = result.data.playCount || playCount.value + 1
    }
  } catch (error) {
    console.error('增加播放量失败:', error)
  }
}

// ========== 加载用户视频状态（点赞、收藏）==========
const loadUserVideoStatus = async () => {
  if (!isLoggedIn.value || !currentUserId.value) {
    return
  }
  
  const videoId = props.videoData.id
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/status?userId=${currentUserId.value}`)
    const result = await response.json()
    if (result.code === 200) {
      liked.value = result.data.liked
      favorited.value = result.data.favorited
    }
  } catch (error) {
    console.error('加载用户视频状态失败:', error)
  }
}

// ========== 点赞视频（每个用户只能点赞一次）==========
const toggleLike = async () => {
  const videoId = props.videoData.id
  try {
    const method = liked.value ? 'DELETE' : 'POST'
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/likes`, {
      method: method,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ userId: currentUserId.value })
    })
    const result = await response.json()
    if (result.code === 200) {
      liked.value = !liked.value
      likeCount.value = liked.value ? likeCount.value + 1 : likeCount.value - 1
    }
  } catch (error) {
    console.error('点赞失败:', error)
  }
}

// ========== 收藏视频（每个用户只能收藏一次）==========
const toggleFavorite = async () => {
  const videoId = props.videoData.id
  try {
    const method = favorited.value ? 'DELETE' : 'POST'
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/favorites`, {
      method: method,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ userId: currentUserId.value })
    })
    const result = await response.json()
    if (result.code === 200) {
      favorited.value = !favorited.value
      favoriteCount.value = favorited.value ? favoriteCount.value + 1 : favoriteCount.value - 1
    }
  } catch (error) {
    console.error('收藏失败:', error)
  }
}

const togglePlay = () => {
  if (!videoRef.value) return
  if (isPlaying.value) {
    videoRef.value.pause()
  } else {
    videoRef.value.play()
  }
}

// ========== 视频播放事件 ==========
const onPlay = () => {
  isPlaying.value = true
  showControls.value = true
  resetHideTimer()
  startDanmakuSync() // 启动弹幕同步
  incrementPlayCount() // 增加播放量
}

const onPause = () => {
  isPlaying.value = false
  showControls.value = true
  clearTimeout(hideControlsTimer.value)
  stopDanmakuSync() // 停止弹幕同步
}

const onEnded = () => {
  isPlaying.value = false
  showControls.value = true
  stopDanmakuSync() // 停止弹幕同步
}

const onLoadedMetadata = () => {
  console.log('视频元数据加载完成')
  if (videoRef.value) {
    duration.value = videoRef.value.duration
  }
}

const onCanPlay = () => {
  console.log('视频可以播放了')
  isVideoReady.value = true
}

const onVideoError = (event) => {
  console.error('视频播放出错:', event)
  console.error('错误详情:', event.target.error)
}

const onWaiting = () => {
  console.log('视频正在缓冲...')
  isLoading.value = true
}

// ========== 视频进度更新事件 ==========
const onTimeUpdate = () => {
  if (!videoRef.value) return
  const currentVideoTime = videoRef.value.currentTime
  currentTime.value = currentVideoTime
  duration.value = videoRef.value.duration || 0
  currentProgress.value = (currentTime.value / duration.value) * 100

  if (videoRef.value.buffered.length > 0) {
    const bufferedEnd = videoRef.value.buffered.end(videoRef.value.buffered.length - 1)
    bufferedProgress.value = (bufferedEnd / duration.value) * 100
  }
}

const onVolumeChange = () => {
  if (!videoRef.value) return
  volume.value = videoRef.value.volume
  isMuted.value = videoRef.value.muted
}

const onVolumeInput = () => {
  if (!videoRef.value) return
  videoRef.value.volume = volume.value
  if (volume.value > 0) {
    videoRef.value.muted = false
  }
}

const toggleMute = () => {
  if (!videoRef.value) return
  videoRef.value.muted = !videoRef.value.muted
}

// ========== 进度条操作 ==========
const seekVideo = (e) => {
  if (!videoRef.value || !videoArea.value) return
  const rect = videoArea.value.getBoundingClientRect()
  const percent = (e.clientX - rect.left) / rect.width
  videoRef.value.currentTime = percent * duration.value
  resetDanmakuState()
  // ✅ 重新对齐指针，跳过已播放的弹幕
  alignDanmakuIndex()
}

const startDragProgress = (e) => {
  e.stopPropagation()
  isDraggingProgress.value = true
  updateProgressFromEvent(e)
}

const updateProgressFromEvent = (e) => {
  if (!videoRef.value || !videoArea.value) return
  const rect = videoArea.value.getBoundingClientRect()
  const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  videoRef.value.currentTime = percent * duration.value
  resetDanmakuState()
  // ✅ 重新对齐指针
  alignDanmakuIndex()
}

const stopDragProgress = () => {
  isDraggingProgress.value = false
}

// ========== 对齐弹幕指针（跳过已播放的弹幕）==========
const alignDanmakuIndex = () => {
  if (!videoRef.value) return
  const currentVideoTime = videoRef.value.currentTime
  danmakuIndex = 0
  while (danmakuIndex < danmakuListSorted.length && 
         danmakuListSorted[danmakuIndex].time <= currentVideoTime) {
    danmakuIndex++
  }
}

const toggleSpeedDropdown = () => {
  showSpeedDropdown.value = !showSpeedDropdown.value
  showQualityDropdown.value = false
}

const toggleDanmaku = () => {
  danmakuEnabled.value = !danmakuEnabled.value
}

const showDanmakuSettings = ref(false)

const toggleDanmakuSettings = () => {
  showDanmakuSettings.value = !showDanmakuSettings.value
}

let clickTimer = null
let lastClickTime = 0
const DOUBLE_CLICK_THRESHOLD = 300 // 双击判断阈值（毫秒）

const handleVideoClick = (e) => {
  const target = e.target
  if (target.closest('.controls-overlay') ||
      target.closest('.danmaku-input-area') ||
      target.closest('.dropdown') ||
      target.closest('.control-btn')) {
    return
  }
  
  const currentTime = Date.now()
  const timeSinceLastClick = currentTime - lastClickTime
  
  // 判断是否为双击
  if (timeSinceLastClick < DOUBLE_CLICK_THRESHOLD && timeSinceLastClick > 0) {
    // 双击：点赞
    clearTimeout(clickTimer)
    lastClickTime = 0 // 重置点击时间
    toggleLike()
    showLikeAnimation.value = true
    setTimeout(() => {
      showLikeAnimation.value = false
    }, 1000)
    return
  }
  
  // 单击：设置延迟执行播放/暂停
  lastClickTime = currentTime
  clearTimeout(clickTimer)
  clickTimer = setTimeout(() => {
    lastClickTime = 0 // 重置点击时间
    togglePlay()
  }, DOUBLE_CLICK_THRESHOLD)
}

const handleKeyDown = (e) => {
  if (!videoRef.value) return
  
  const target = e.target
  const isInputField = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA'
  
  if (isInputField) return
  
  switch (e.key) {
    case 'ArrowRight':
      e.preventDefault()
      videoRef.value.currentTime = Math.min(videoRef.value.currentTime + 5, duration.value)
      break
    case 'ArrowLeft':
      e.preventDefault()
      videoRef.value.currentTime = Math.max(videoRef.value.currentTime - 5, 0)
      break
  }
}

const changeSpeed = (speed) => {
  if (!videoRef.value) return
  playbackRate.value = speed
  videoRef.value.playbackRate = speed
  showSpeedDropdown.value = false
}

const changeQuality = (quality) => {
  if (quality === currentQuality.value) return
  
  isLoading.value = true
  const savedTime = currentTime.value
  const wasPlaying = isPlaying.value
  
  videoRef.value.pause()
  
  setTimeout(() => {
    currentQuality.value = quality
    
    // 显式切换视频源，确保新清晰度生效
    if (videoRef.value) {
      videoRef.value.src = currentSrc.value
      videoRef.value.load()
      videoRef.value.currentTime = savedTime
      
      setTimeout(() => {
        if (wasPlaying) {
          videoRef.value.play().catch(() => {})
        }
        isLoading.value = false
      }, 500)
    }
  }, 300)
  
  showQualityDropdown.value = false
}

const toggleQualityDropdown = () => {
  showQualityDropdown.value = !showQualityDropdown.value
  showSpeedDropdown.value = false
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    if (videoArea.value) {
      videoArea.value.requestFullscreen().catch(err => {
        console.error('全屏请求失败:', err)
      })
    }
  } else {
    document.exitFullscreen()
  }
}

const resetHideTimer = () => {
  clearTimeout(hideControlsTimer.value)
  hideControlsTimer.value = setTimeout(() => {
    if (isPlaying.value && !showSpeedDropdown.value && !showQualityDropdown.value) {
      showControls.value = false
    }
  }, 3000)
}

const handleMouseMove = () => {
  if (isPlaying.value) {
    showControls.value = true
    resetHideTimer()
  }
}

// ========== 发送弹幕（按照 API 规范调用）==========
const sendDanmaku = async () => {
  if (!danmakuInput.value.trim()) return
  
  // 获取当前登录用户ID
  const userId = typeof currentUserId !== 'undefined' ? currentUserId : 
                 localStorage.getItem('userId') || 'anonymous'
  
  // 使用视频元素的当前时间（最准确）
  const currentVideoTime = videoRef.value ? videoRef.value.currentTime : currentTime.value
  
  console.log('[发送弹幕] 当前时间:', currentVideoTime, '内容:', danmakuInput.value)
  
  // 前端内部使用的弹幕对象
  const newDanmaku = {
    id: Date.now(),
    content: danmakuInput.value,
    color: danmakuColor.value,
    time: currentVideoTime,
    userId: userId,
    isUser: true
  }
  
  // 添加到弹幕列表
  danmakuList.value.push(newDanmaku)
  danmakuInput.value = ''
  
  // ✅ 立即显示刚发送的弹幕
  console.log('[立即显示弹幕]', newDanmaku.content)
  showSingleDanmaku(newDanmaku)
  
  // ✅ 重新排序列表，确保后续播放时弹幕顺序正确
  sortDanmakuByTime()
  
  try {
    // 使用 videoId 调用标准 API
    const videoId = props.videoData.id
    // ✅ 按照 API 规范构造请求体
    const requestBody = {
      content: newDanmaku.content,
      timeSeconds: newDanmaku.time,
      color: newDanmaku.color
    }
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/danmakus`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody)
    })
    const result = await response.json()
    if (result.code !== 200) {
      console.warn('发送弹幕失败:', result.message)
    }
  } catch (error) {
    console.error('发送弹幕失败:', error)
  }
}

const goBack = () => {
  emit('back')
}

// ========== 评论相关方法 ==========
const loadComments = async (page = 1, append = false) => {
  loadingComments.value = true
  try {
    const videoId = props.videoData.id
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/comments?page=${page}&pageSize=${commentPageSize}`)
    const result = await response.json()
    
    if (result.code === 200 && result.data) {
      const data = result.data
      if (append) {
        commentsListData.value = [...commentsListData.value, ...data.list]
      } else {
        commentsListData.value = data.list
      }
      commentsTotal.value = data.total
      currentCommentPage.value = data.page
      hasMoreComments.value = data.list.length >= commentPageSize
    }
  } catch (error) {
    console.error('加载评论失败:', error)
  }
  loadingComments.value = false
}

const sendComment = async () => {
  if (!commentInput.value.trim()) return
  
  const videoId = props.videoData.id
  const requestBody = {
    content: commentInput.value,
    parentId: null,
    userId: currentUserId.value
  }
  
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/comments`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody)
    })
    const result = await response.json()
    if (result.code === 200) {
      commentInput.value = ''
      // 重新加载第一页评论
      currentCommentPage.value = 1
      loadComments(1, false)
    } else {
      console.warn('发送评论失败:', result.message)
      alert('发送评论失败: ' + (result.message || '未知错误'))
    }
  } catch (error) {
    console.error('发送评论失败:', error)
    alert('发送评论失败，请检查网络连接: ' + error.message)
  }
}

const likeComment = async (commentId) => {
  const videoId = props.videoData.id
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/comments/${commentId}/like`, {
      method: 'POST'
    })
    const result = await response.json()
    if (result.code === 200) {
      // 更新本地点赞数
      const comment = commentsListData.value.find(c => c.commentId === commentId)
      if (comment) {
        comment.likeCount = (comment.likeCount || 0) + 1
      }
    }
  } catch (error) {
    console.error('点赞失败:', error)
  }
}

const replyComment = (comment) => {
  commentInput.value = `@${comment.user?.nickname || '匿名用户'} `
}

const loadMoreComments = () => {
  if (hasMoreComments.value && !loadingComments.value) {
    loadComments(currentCommentPage.value + 1, true)
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  
  return date.toLocaleDateString('zh-CN')
}

// ========== 页面可见性变化处理 ==========
const onVisibilityChange = () => {
  if (!document.hidden && videoRef.value && !videoRef.value.paused) {
    resetDanmakuState()
    alignDanmakuIndex()
    startDanmakuSync()
  }
}

onMounted(() => {
  document.addEventListener('mousemove', handleGlobalMouseMove)
  document.addEventListener('mouseup', stopDragProgress)
  document.addEventListener('keydown', handleKeyDown)
  document.addEventListener('visibilitychange', onVisibilityChange)
  // ✅ 从服务器加载当前视频的弹幕数据
  loadDanmakuFromServer()
  // ✅ 从服务器加载评论
  loadComments()
  // ✅ 从服务器加载用户对该视频的点赞和收藏状态
  loadUserVideoStatus()
})

onUnmounted(() => {
  clearTimeout(hideControlsTimer.value)
  document.removeEventListener('mousemove', handleGlobalMouseMove)
  document.removeEventListener('mouseup', stopDragProgress)
  document.removeEventListener('keydown', handleKeyDown)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  stopDanmakuSync()
})

const handleGlobalMouseMove = (e) => {
  if (isDraggingProgress.value) {
    updateProgressFromEvent(e)
  }
}
</script>

<style scoped>
.video-player-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #f0f0f0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  margin-bottom: 16px;
  color: #333;
  transition: background 0.2s;
}

.back-btn:hover {
  background: #e0e0e0;
}

.player-wrapper {
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.video-area {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  cursor: pointer;
}

.main-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
}

.danmaku-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
  z-index: 10;
}

.danmaku-item {
  position: absolute;
  left: 100%;               /* 从右边界外侧开始 */
  transform: translateX(0); /* 初始位置右侧外部 */
  white-space: nowrap;
  font-size: 22px;
  font-weight: bold;
  color: #ffffff;
  text-shadow: 1px 1px 2px rgba(0,0,0,0.8);
  pointer-events: none;
  will-change: transform;
}

.danmaku-scroll {
  animation: danmaku-scroll var(--duration, 8s) linear forwards;
  white-space: nowrap;
}

@keyframes danmaku-scroll {
  to {
    transform: translateX(calc(-100% - 100vw)); /* 移动弹幕宽度+视口宽度，确保完全移出左侧 */
  }
}

.danmaku-color-1 { color: #ffffff; }
.danmaku-color-2 { color: #ff0000; }
.danmaku-color-3 { color: #ffff00; }
.danmaku-color-4 { color: #00ff00; }
.danmaku-color-5 { color: #00a0ff; }
.danmaku-color-6 { color: #ff00ff; }

.user-danmaku {
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 4px;
  padding: 2px 6px;
}

.danmaku-input-area {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-35%);
  display: flex;
  gap: 4px;
  align-items: center;
  z-index: 90;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 16px;
  backdrop-filter: blur(8px);
}

.danmaku-color-picker {
  display: flex;
  gap: 4px;
}

.danmaku-toggle {
  padding: 8px 8px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 20px;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.danmaku-toggle:hover {
  background: rgba(255, 255, 255, 0.3);
}

.danmaku-toggle.disabled {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.5);
}

.danmaku-settings-btn {
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 12px;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
}

.danmaku-settings-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.danmaku-settings-panel {
  position: absolute;
  bottom: 100%;
  left: 0;
  margin-bottom: 10px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.8);
  border-radius: 12px;
  min-width: 200px;
  backdrop-filter: blur(10px);
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.setting-item:last-child {
  margin-bottom: 0;
}

.setting-label {
  color: #fff;
  font-size: 12px;
  min-width: 40px;
}

.setting-slider {
  flex: 1;
  height: 4px;
  -webkit-appearance: none;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  outline: none;
}

.setting-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  cursor: pointer;
}

.setting-value {
  color: #fff;
  font-size: 12px;
  min-width: 40px;
  text-align: right;
}

.speed-options {
  display: flex;
  gap: 5px;
  flex: 1;
}

.speed-btn {
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.speed-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.speed-btn.active {
  background: rgba(255, 107, 107, 0.8);
}

.danmaku-container.hidden {
  display: none;
}

.color-btn {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
}

.color-btn:hover {
  transform: scale(1.1);
}

.color-btn.active {
  border-color: rgba(255, 255, 255, 0.8);
  transform: scale(1.15);
}

.danmaku-input {
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 20px;
  width: 250px;
  font-size: 14px;
  color: #fff;
  outline: none;
}

.danmaku-input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.send-danmaku-btn {
  padding: 8px 20px;
  background: #ff6b6b;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-weight: 500;
  font-size: 14px;
  transition: all 0.2s ease;
}

.send-danmaku-btn:hover {
  background: #ff5252;
  transform: scale(1.02);
}

.controls-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0,0,0,0.7));
  padding: 40px 20px 20px;
  transition: opacity 0.3s, transform 0.3s;
  z-index: 50;
}

.show-controls {
  opacity: 1;
  transform: translateY(0);
}

.hide-controls {
  opacity: 0;
  transform: translateY(10px);
  pointer-events: none;
}

.progress-area {
  margin-bottom: 12px;
}

.progress-bar {
  position: relative;
  height: 4px;
  cursor: pointer;
  border-radius: 2px;
}

.progress-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255,255,255,0.3);
  border-radius: 2px;
}

.progress-buffered {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: rgba(255,255,255,0.4);
  border-radius: 2px;
}

.progress-played {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: #1890ff;
  border-radius: 2px;
}

.progress-thumb {
  position: absolute;
  right: -6px;
  top: 50%;
  transform: translateY(-50%);
  width: 12px;
  height: 12px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0,0,0,0.3);
}

.bottom-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.left-controls, .right-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.control-btn {
  background: transparent;
  border: none;
  color: white;
  font-size: 20px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 4px;
  transition: background 0.2s;
}

.control-btn:hover {
  background: rgba(255,255,255,0.1);
}

.control-btn.small {
  font-size: 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
}

.speed-label, .quality-label {
  font-size: 10px;
  opacity: 0.8;
}

.speed-value, .quality-value {
  font-size: 14px;
  font-weight: bold;
}

.volume-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.volume-slider {
  width: 80px;
  cursor: pointer;
}

.time-display {
  color: white;
  font-size: 14px;
  min-width: 120px;
}

.speed-control, .quality-control {
  position: relative;
}

.speed-dropdown, .quality-dropdown {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0,0,0,0.9);
  border-radius: 8px;
  padding: 8px 0;
  margin-bottom: 8px;
  display: none;
  min-width: 80px;
  z-index: 100;
}

.show-speed-dropdown, .show-quality-dropdown {
  display: block;
}

.speed-option, .quality-option {
  display: block;
  width: 100%;
  padding: 8px 16px;
  background: transparent;
  border: none;
  color: white;
  cursor: pointer;
  font-size: 14px;
  text-align: center;
}

.speed-option:hover, .quality-option:hover {
  background: rgba(255,255,255,0.1);
}

.speed-option.active, .quality-option.active {
  color: #1890ff;
  font-weight: bold;
}

.play-button-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 40;
}

.big-play-btn {
  width: 80px;
  height: 80px;
  background: rgba(0,0,0,0.6);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 36px;
  transition: background 0.2s;
}

.big-play-btn:hover {
  background: rgba(24,144,255,0.8);
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.8);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 3px solid rgba(255,255,255,0.3);
  border-top-color: #1890ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  color: white;
  margin-top: 15px;
  font-size: 14px;
}

.video-info-area {
  padding: 24px 0;
}

.video-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.video-title {
  font-size: 22px;
  font-weight: bold;
  color: #1e1e1e;
  flex: 1;
}

.video-meta {
  font-size: 14px;
  color: #999;
  margin-bottom: 20px;
}

.meta-divider {
  margin: 0 8px;
}

.video-author {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.author-avatar {
  font-size: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.author-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  flex: 1;
}

.follow-btn {
  padding: 8px 24px;
  background: #ff4757;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: bold;
  font-size: 14px;
}

.follow-btn:hover {
  background: #e63946;
}

/* 操作按钮区域 */
.video-actions {
  display: flex;
  gap: 16px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px 12px;
  transition: all 0.2s;
  border-radius: 20px;
}

.action-btn:hover {
  background: #f5f5f5;
}

.action-icon {
  font-size: 20px;
}

.action-text {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.action-btn.active .action-text {
  color: #ff4757;
}

.like-btn.active .action-icon {
  animation: like-bounce 0.3s ease;
}

.favorite-btn.active .action-icon {
  animation: favorite-glow 0.3s ease;
}

@keyframes like-bounce {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.3); }
}

@keyframes favorite-glow {
  0%, 100% { filter: none; }
  50% { filter: drop-shadow(0 0 10px #ffd700); }
}

/* 双击点赞动画 */
.like-animation {
  position: absolute;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 80px;
  animation: float-up 1s ease-out forwards;
  pointer-events: none;
  z-index: 100;
}

@keyframes float-up {
  0% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(0.5);
  }
  50% {
    opacity: 1;
    transform: translate(-50%, -70%) scale(1.2);
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -100%) scale(0.8);
  }
}

/* 评论区样式 */
.comments-area {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

.comments-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.comments-header h3 {
  font-size: 18px;
  font-weight: bold;
  color: #1e1e1e;
  margin: 0;
}

.comments-count {
  font-size: 14px;
  color: #999;
}

.comment-input-area {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.comment-input {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 24px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.comment-input:focus {
  border-color: #1890ff;
}

.send-comment-btn {
  padding: 12px 24px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 24px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.send-comment-btn:hover {
  background: #096dd9;
}

.comments-list {
  max-height: 500px;
  overflow-y: auto;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f5f5f5;
}

.comment-avatar {
  font-size: 28px;
  background: #f0f0f0;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.comment-content {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-nickname {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.comment-time {
  font-size: 12px;
  color: #999;
}

.comment-text {
  font-size: 14px;
  color: #1e1e1e;
  line-height: 1.6;
  margin: 0 0 12px 0;
  word-break: break-all;
}

.comment-actions {
  display: flex;
  gap: 24px;
}

.comment-action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  color: #999;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s;
}

.comment-action-btn:hover {
  color: #1890ff;
}

.load-more {
  text-align: center;
  padding: 20px 0;
}

.load-more-btn {
  padding: 10px 32px;
  background: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.load-more-btn:hover {
  background: #e8e8e8;
}

.no-comments {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.no-comments p {
  margin: 0;
}

.loading-comments {
  text-align: center;
  padding: 20px 0;
  color: #999;
}
</style>
