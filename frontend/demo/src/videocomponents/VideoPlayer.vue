<template>
  <div class="video-player-container">
    <div class="back-btn" @click="goBack">
      <span class="back-icon">←</span> 返回
    </div>

    <div class="player-content-layout">
      <div class="video-main-column">
        <div class="video-header-section">
          <div class="video-title-row">
            <div class="video-title">{{ videoData.title }}</div>
          </div>
          <div class="video-meta title-meta">
            <span>{{ formatNumber(playCount) }} 播放</span>
            <span class="meta-divider">|</span>
            <span>{{ videoData.date }}</span>
          </div>
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
                <div class="progress-bar" @mousedown="startDragProgress" @click="seekVideo">
                  <div class="progress-bg"></div>
                  <div class="progress-buffered" :style="{ width: bufferedProgress + '%' }"></div>
                  <div class="progress-played" :style="{ width: currentProgress + '%' }">
                    <div class="progress-thumb"></div>
                  </div>
                </div>
              </div>

              <div class="danmaku-input-area">
                <button :class="['danmaku-toggle', { disabled: !danmakuEnabled }]" title="弹幕" @click="toggleDanmaku">弹</button>
                <button class="danmaku-settings-btn" title="弹幕设置" @click="toggleDanmakuSettings">
                  <span>⚙</span>
                </button>
                <div class="danmaku-settings-panel" v-if="showDanmakuSettings">
                  <div class="setting-item">
                    <span class="setting-label">透明度</span>
                    <input type="range" min="0" max="100" v-model="danmakuOpacity" class="setting-slider" />
                    <span class="setting-value">{{ danmakuOpacity }}%</span>
                  </div>
                  <div class="setting-item">
                    <span class="setting-label">速度</span>
                    <div class="speed-options">
                      <button :class="['speed-btn', { active: danmakuSpeed === 0.6 }]" @click="danmakuSpeed = 0.6">慢</button>
                      <button :class="['speed-btn', { active: danmakuSpeed === 1 }]" @click="danmakuSpeed = 1">正常</button>
                      <button :class="['speed-btn', { active: danmakuSpeed === 1.4 }]" @click="danmakuSpeed = 1.4">快</button>
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
                <input v-model="danmakuInput" class="danmaku-input" placeholder="发一条弹幕..." @keyup.enter="sendDanmaku" />
                <button class="send-danmaku-btn" @click="sendDanmaku">发送</button>
              </div>

              <div class="bottom-controls">
                <div class="left-controls">
                  <button class="control-btn icon-control" :title="isPlaying ? '暂停' : '播放'" @click="togglePlay">
                    {{ isPlaying ? '⏸' : '▶' }}
                  </button>
                  <div class="volume-control">
                    <button class="control-btn icon-control" :title="isMuted ? '取消静音' : '静音'" @click="toggleMute">
                      {{ isMuted ? '🔇' : '🔊' }}
                    </button>
                    <input type="range" min="0" max="1" step="0.1" v-model="volume" @input="onVolumeInput" class="volume-slider" />
                  </div>
                  <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
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
                        :class="{ active: playbackRate === speed }"
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
                        :class="{ active: currentQuality === q }"
                        @click="changeQuality(q)"
                      >
                        {{ q }}
                      </button>
                    </div>
                  </div>

                  <button class="control-btn icon-control" title="全屏" @click="toggleFullscreen">⛶</button>
                </div>
              </div>
            </div>

            <div class="play-button-overlay" v-show="currentTime === 0 && !isPlaying && !showControls" @click="togglePlay">
              <div class="big-play-btn">▶</div>
            </div>

            <div class="loading-overlay" v-show="isLoading">
              <div class="loading-spinner"></div>
              <span class="loading-text">正在切换到 {{ currentQuality }}...</span>
            </div>
          </div>
        </div>

        <div class="video-info-area">
          <div class="video-stats-actions">
            <div class="video-actions">
              <button :class="['action-btn', 'like-btn', { active: liked }]" @click="toggleLike">
                <span class="action-icon" v-html="liked ? iconThumbFilled : iconThumbOutline"></span>
                <span class="action-text">{{ formatNumber(likeCount) }}</span>
              </button>
              <button :class="['action-btn', 'favorite-btn', { active: favorited }]" @click="toggleFavorite">
                <span class="action-icon" v-html="favorited ? iconStarFilled : iconStarOutline"></span>
                <span class="action-text">{{ formatNumber(favoriteCount) }}</span>
              </button>
              <button class="action-btn share-btn" @click="shareVideo">
                <span class="action-icon" v-html="iconShare"></span>
                <span class="action-text">{{ shareCopied ? '已复制' : '分享' }}</span>
              </button>
            </div>
          </div>
          <div class="video-description" v-if="videoData.description">{{ videoData.description }}</div>
          <div class="video-tags" v-if="videoTags.length > 0">
            <span v-for="tag in videoTags" :key="tag.id || tag.name" class="video-tag"># {{ tag.name }}</span>
          </div>

          <div class="comments-area">
            <div class="comments-header">
              <h3>评论</h3>
              <span class="comments-count">{{ commentsTotal }}</span>
            </div>

            <div class="comment-input-area">
              <div v-if="replyTarget" class="reply-target-bar">
                正在回复 {{ resolveCommentNickname(replyTarget) }}
                <button @click="clearReplyTarget">取消</button>
              </div>
              <input v-model="commentInput" class="comment-input" placeholder="发表评论..." @keyup.enter="sendComment" />
              <button class="send-comment-btn" @click="sendComment">发送</button>
            </div>

            <div class="comments-list" ref="commentsList">
              <div v-for="comment in commentsListData" :key="comment.commentId" class="comment-item">
                <div class="comment-avatar clickable-avatar" @click="openCommentUserProfile(comment)">
                  <img v-if="resolveCommentAvatar(comment)" :src="resolveCommentAvatar(comment)" alt="" class="comment-avatar-image" />
                  <span v-else>{{ resolveCommentAvatarFallback(comment) }}</span>
                </div>
                <div class="comment-content">
                  <div class="comment-header">
                    <span class="comment-nickname clickable-user" @click="openCommentUserProfile(comment)">{{ resolveCommentNickname(comment) }}</span>
                    <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
                    <button v-if="canDeleteComment(comment)" class="comment-delete-btn" @click="deleteComment(comment.commentId)">删除</button>
                  </div>
                  <p class="comment-text">{{ comment.content }}</p>
                  <div class="comment-actions">
                    <button :class="['comment-action-btn', { active: comment.liked }]" @click="handleCommentLike(comment.commentId)">
                      <span v-html="comment.liked ? iconThumbFilled : iconThumbOutline"></span>
                      <span>{{ comment.likeCount || 0 }}</span>
                    </button>
                    <button class="comment-action-btn" @click="replyComment(comment)">
                      <span>回复</span>
                    </button>
                  </div>
                  <div v-if="comment.replies?.length" class="comment-replies">
                    <div v-for="reply in comment.replies" :key="reply.commentId" class="reply-item">
                      <div class="reply-avatar clickable-avatar" @click="openCommentUserProfile(reply)">
                        <img v-if="resolveCommentAvatar(reply)" :src="resolveCommentAvatar(reply)" alt="" class="comment-avatar-image" />
                        <span v-else>{{ resolveCommentAvatarFallback(reply) }}</span>
                      </div>
                      <div class="reply-content">
                        <div class="reply-line">
                          <span class="comment-nickname clickable-user" @click="openCommentUserProfile(reply)">{{ resolveCommentNickname(reply) }}</span>
                          <span v-if="reply.replyToUser" class="reply-to">回复 {{ resolveReplyToNickname(reply) }}</span>
                          <span class="comment-time">{{ formatDate(reply.createdAt) }}</span>
                          <button v-if="canDeleteComment(reply)" class="comment-delete-btn" @click="deleteComment(reply.commentId)">删除</button>
                        </div>
                        <p class="comment-text reply-text">{{ reply.content }}</p>
                        <div class="comment-actions">
                          <button :class="['comment-action-btn', { active: reply.liked }]" @click="handleCommentLike(reply.commentId)">
                            <span v-html="reply.liked ? iconThumbFilled : iconThumbOutline"></span>
                            <span>{{ reply.likeCount || 0 }}</span>
                          </button>
                          <button class="comment-action-btn" @click="replyComment(reply)">
                            <span>回复</span>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="hasMoreComments" class="load-more">
                <button class="load-more-btn" @click="loadMoreComments">加载更多</button>
              </div>

              <div v-if="commentsListData.length === 0 && !loadingComments" class="no-comments">
                <p>还没有评论。</p>
              </div>

              <div v-if="loadingComments" class="loading-comments">
                <span>正在加载...</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="video-sidebar-column">
        <div class="video-author">
          <div class="author-avatar clickable-avatar" @click="openAuthorProfile">
            <img v-if="authorAvatarUrl" :src="authorAvatarUrl" alt="" class="author-avatar-image" />
            <span v-else>{{ authorInitial }}</span>
          </div>
          <div class="author-meta clickable-user" @click="openAuthorProfile">
            <div class="author-name">{{ authorDisplayName }}</div>
            <div class="author-subtitle">{{ authorSubtitle }}</div>
          </div>
          <button v-if="canFollowAuthor" class="follow-btn" @click.stop="triggerFollowAuthor">
            {{ authorProfileTarget.following ? '已关注' : '+ 关注' }}
          </button>
        </div>

        <div class="related-videos">
          <div class="related-header">相关推荐</div>
          <div v-if="relatedVideos.length === 0" class="related-empty">暂无相关推荐</div>
          <button v-for="item in relatedVideos" :key="item.id" class="related-item" @click="openRelatedVideo(item)">
            <img v-if="item.coverUrl" :src="item.coverUrl" alt="" class="related-cover" />
            <div v-else class="related-cover related-cover-fallback">{{ item.title?.slice(0, 1) || 'V' }}</div>
            <div class="related-info">
              <div class="related-title">{{ item.title }}</div>
              <div class="related-author">{{ item.author }}</div>
              <div class="related-meta">{{ item.duration }} | {{ formatNumber(item.playCount || 0) }} 播放</div>
            </div>
          </button>
        </div>
      </div>
    </div>

    <div class="like-animation" v-if="showLikeAnimation">点赞</div>
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
      videoUrl: 'video-demo-bigbuckbunny',
      sources: {
        '240P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '720P': 'https://www.w3schools.com/html/mov_bbb.mp4',
        '1080P': 'https://www.w3schools.com/html/mov_bbb.mp4'
      },
      defaultQuality: '720P'
    })
  }
})

const emit = defineEmits(['back', 'open-video', 'open-profile', 'toggle-follow'])

const videoRef = ref(null)
const videoArea = ref(null)
const danmakuContainer = ref(null)

const isLoggedIn = ref(!!localStorage.getItem('loginUserNickname'))
const currentUserId = ref(localStorage.getItem('loginUserId'))
const getCurrentLoginUserId = () => localStorage.getItem('loginUserId')
const getCurrentLoginNickname = () => localStorage.getItem('loginUserNickname') || localStorage.getItem('loginUser')
const getAuthHeaders = (includeJson = false) => {
  const headers = {}
  const loginUserId = getCurrentLoginUserId()
  if (includeJson) {
    headers['Content-Type'] = 'application/json'
  }
  if (loginUserId) {
    headers['X-User-Id'] = loginUserId
  }
  return headers
}

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

const qualities = computed(() => {
  if (props.videoData && props.videoData.sources) {
    return Object.keys(props.videoData.sources)
  }
  return ['720P']
})

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

const commentsList = ref(null)
const commentsListData = ref([])
const commentInput = ref('')
const replyTarget = ref(null)
const commentsTotal = ref(0)
const currentCommentPage = ref(1)
const commentPageSize = 20
const hasMoreComments = ref(true)
const loadingComments = ref(false)

const liked = ref(props.videoData.liked || false)
const favorited = ref(props.videoData.favorited || false)
const likeCount = ref(props.videoData.likeCount || 0)
const favoriteCount = ref(props.videoData.favoriteCount || 0)
const playCount = ref(props.videoData.playCount || 0)
const showLikeAnimation = ref(false)
const shareCopied = ref(false)
const hasIncrementedPlayCount = ref(false)
const resumeApplied = ref(false)
const lastSavedProgressSeconds = ref(-1)

const danmakuList = ref([])
const visibleDanmakuList = ref([])
let danmakuIdCounter = 1
let danmakuTracks = [null, null, null, null, null, null, null, null, null, null]
const displayedDanmakuIds = new Set()

let rafId = null
let danmakuListSorted = []
let danmakuIndex = 0
let progressSaveTimer = null
let shareCopiedTimer = null

const iconThumbOutline = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.5 21H5.2a2 2 0 0 1-2-2v-7.2a2 2 0 0 1 2-2h3.3V21Zm0-11.2 4.2-7.1c.4-.7 1.3-.9 2-.5.7.4 1 1.2.7 1.9l-1.1 3.2h4.2c1.6 0 2.8 1.4 2.5 3l-1.2 7.7a3.6 3.6 0 0 1-3.6 3H8.5V9.8Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>'
const iconThumbFilled = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.5 21H5.2a2 2 0 0 1-2-2v-7.2a2 2 0 0 1 2-2h3.3V21Zm1.8 0V9.8l4.2-7.1c.4-.7 1.3-.9 2-.5.7.4 1 1.2.7 1.9l-1.1 3.2h4.2c1.6 0 2.8 1.4 2.5 3l-1.2 7.7a3.6 3.6 0 0 1-3.6 3h-7.7Z" fill="currentColor"/></svg>'
const iconStarOutline = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3.2 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9L6.6 20l1-6.1-4.4-4.3 6.1-.9L12 3.2Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>'
const iconStarFilled = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 2.8 2.9 5.9 6.5.9-4.7 4.6 1.1 6.5-5.8-3-5.8 3 1.1-6.5-4.7-4.6 6.5-.9L12 2.8Z" fill="currentColor"/></svg>'
const iconShare = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.8 12.7 15.2 16m-.1-8L8.8 11.3M18 6.4a2.7 2.7 0 1 0 0-5.4 2.7 2.7 0 0 0 0 5.4ZM6 14.7a2.7 2.7 0 1 0 0-5.4 2.7 2.7 0 0 0 0 5.4Zm12 8.3a2.7 2.7 0 1 0 0-5.4 2.7 2.7 0 0 0 0 5.4Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>'

const videoUrl = computed(() => props.videoData.videoUrl || props.videoData.id || 'video-' + Date.now())
const resumeProgressSeconds = computed(() => Number(props.videoData?.historyProgressSeconds || 0))
const videoTags = computed(() => Array.isArray(props.videoData?.tags) ? props.videoData.tags : [])
const relatedVideos = computed(() => Array.isArray(props.videoData?.relatedVideos) ? props.videoData.relatedVideos : [])
const loginUserAvatar = computed(() => localStorage.getItem('loginUserAvatar') || '')
const authorDisplayName = computed(() => props.videoData?.authorInfo?.nickname || props.videoData?.authorInfo?.username || props.videoData?.author || 'UP')
const authorProfileTarget = computed(() => {
  const authorInfo = props.videoData?.authorInfo || {}
  return {
    id: authorInfo.userId || authorInfo.id || null,
    userId: authorInfo.userId || authorInfo.id || null,
    username: authorInfo.username || '',
    nickname: authorInfo.nickname || props.videoData?.author || '',
    avatarUrl: authorInfo.avatarUrl || '',
    bio: authorInfo.bio || '',
    following: Boolean(authorInfo.following)
  }
})
const authorAvatarUrl = computed(() => {
  const authorInfo = props.videoData?.authorInfo
  if (authorInfo?.avatarUrl) {
    return authorInfo.avatarUrl
  }
  const authorUserId = authorInfo?.userId || authorInfo?.id
  if (authorUserId != null && String(authorUserId) === String(getCurrentLoginUserId() || '')) {
    return loginUserAvatar.value
  }
  return ''
})
const authorSubtitle = computed(() => props.videoData?.authorInfo?.bio || '创作者')
const authorInitial = computed(() => String(authorDisplayName.value || 'UP').slice(0, 1))
const isOwnAuthorProfile = computed(() => {
  const authorUserId = authorProfileTarget.value.userId || authorProfileTarget.value.id
  return authorUserId != null && String(authorUserId) === String(getCurrentLoginUserId() || '')
})
const canFollowAuthor = computed(() => !isOwnAuthorProfile.value && Boolean(authorProfileTarget.value.userId || authorProfileTarget.value.nickname))

const currentSrc = computed(() => {
  const sources = props.videoData?.sources || {}
  return sources[currentQuality.value] || sources['720P'] || Object.values(sources)[0] || ''
})

const sortDanmakuByTime = () => {
  danmakuListSorted = [...danmakuList.value].sort((a, b) => a.time - b.time)
}

const isCurrentUserDanmaku = (userId) => {
  const currentLoginUserId = getCurrentLoginUserId()
  if (!currentLoginUserId || userId == null) {
    return false
  }
  return String(userId) === String(currentLoginUserId)
}

const resolveCommentNickname = (comment) => {
  if (comment.user?.nickname) {
    return comment.user.nickname
  }
  if (comment.userId != null && String(comment.userId) === String(getCurrentLoginUserId())) {
    return getCurrentLoginNickname() || '匿名用户'
  }
  return '匿名用户'
}

const resolveReplyToNickname = (comment) => {
  if (comment?.replyToUser?.nickname) {
    return comment.replyToUser.nickname
  }
  if (comment?.replyToUser?.username) {
    return comment.replyToUser.username
  }
  return '对方'
}

const resolveCommentAvatar = (comment) => {
  if (comment?.user?.avatarUrl) {
    return comment.user.avatarUrl
  }
  if (comment?.userId != null && String(comment.userId) === String(getCurrentLoginUserId() || '')) {
    return loginUserAvatar.value
  }
  return ''
}

const resolveCommentAvatarFallback = (comment) => {
  const nickname = resolveCommentNickname(comment)
  return String(nickname || 'U').slice(0, 1).toUpperCase()
}

const openAuthorProfile = () => {
  emit('open-profile', authorProfileTarget.value)
}

const openCommentUserProfile = (comment) => {
  emit('open-profile', {
    id: comment?.userId || comment?.user?.id || null,
    userId: comment?.userId || comment?.user?.id || null,
    username: comment?.user?.username || '',
    nickname: comment?.user?.nickname || resolveCommentNickname(comment),
    avatarUrl: comment?.user?.avatarUrl || resolveCommentAvatar(comment) || ''
  })
}

const triggerFollowAuthor = () => {
  emit('toggle-follow', authorProfileTarget.value)
}

const buildShareUrl = () => {
  const url = new URL(window.location.href)
  url.searchParams.set('videoId', props.videoData.id)
  url.hash = '#/home'
  return url.toString()
}

const shareVideo = async () => {
  const shareUrl = buildShareUrl()
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(shareUrl)
    } else {
      const input = document.createElement('input')
      input.value = shareUrl
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
    }
    shareCopied.value = true
    clearTimeout(shareCopiedTimer)
    shareCopiedTimer = setTimeout(() => {
      shareCopied.value = false
    }, 1800)
  } catch (error) {
    alert(`复制分享链接失败：${error.message}`)
  }
}

const resolveDanmakuColor = (color) => {
  if (!color) {
    return '#ffffff'
  }
  return danmakuLegacyColorMap[color] || color
}

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
  sortDanmakuByTime()
}

const loadDanmakuFromServer = async () => {
  try {
    const videoId = props.videoData.id
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/danmakus?startTime=0&endTime=${duration.value || 3600}`)
    const result = await response.json()
    if (result.code !== 200 || !result.data) {
      console.warn('加载弹幕失败:', result.message)
      return
    }
    result.data.forEach(item => {
      if (!danmakuList.value.find(d => d.id === item.id)) {
        danmakuList.value.push({
          id: item.id,
          content: item.content,
          color: item.color,
          time: item.time,
          userId: item.userId,
          isUser: isCurrentUserDanmaku(item.userId)
        })
      }
    })
    sortDanmakuByTime()
  } catch (error) {
    console.error('加载弹幕失败:', error)
    initMockDanmaku()
  }
}

const SCROLL_SPEED = 180

const getDanmakuDuration = (text) => {
  const area = document.querySelector('.video-area')
  const containerWidth = area ? area.offsetWidth : 1200
  const charWidth = 16
  const danmakuWidth = text.length * charWidth
  const totalDistance = containerWidth + danmakuWidth
  const speedMultiplier = danmakuSpeed.value || 1
  const baseDuration = totalDistance / SCROLL_SPEED
  return Math.max(0.5, Math.min(15, baseDuration / speedMultiplier))
}

const getAvailableTrack = (itemDuration) => {
  let trackIndex = 0
  const now = Date.now()
  const durationMs = itemDuration * 1000
  for (let i = 0; i < danmakuTracks.length; i += 1) {
    if (!danmakuTracks[i] || now - danmakuTracks[i] > durationMs) {
      trackIndex = i
      break
    }
  }
  danmakuTracks[trackIndex] = now
  return trackIndex
}

const showSingleDanmaku = (danmaku) => {
  const itemDuration = getDanmakuDuration(danmaku.content)
  const track = getAvailableTrack(itemDuration)
  const newVisibleDanmaku = {
    ...danmaku,
    track,
    duration: itemDuration,
    displayId: `${danmaku.id}-${Date.now()}-${Math.random()}`
  }
  visibleDanmakuList.value.push(newVisibleDanmaku)
  displayedDanmakuIds.add(danmaku.id)

  setTimeout(() => {
    const index = visibleDanmakuList.value.findIndex(d => d.displayId === newVisibleDanmaku.displayId)
    if (index > -1) {
      visibleDanmakuList.value.splice(index, 1)
    }
  }, itemDuration * 1000 + 100)
}

const checkAndShowDanmaku = () => {
  if (!videoRef.value || videoRef.value.paused || videoRef.value.ended) {
    return
  }
  const currentVideoTime = videoRef.value.currentTime
  while (danmakuIndex < danmakuListSorted.length && danmakuListSorted[danmakuIndex].time <= currentVideoTime) {
    const danmaku = danmakuListSorted[danmakuIndex]
    if (!displayedDanmakuIds.has(danmaku.id)) {
      showSingleDanmaku(danmaku)
      displayedDanmakuIds.add(danmaku.id)
    }
    danmakuIndex += 1
  }
}

const danmakuSyncLoop = () => {
  if (!videoRef.value || videoRef.value.paused || videoRef.value.ended) {
    rafId = null
    return
  }
  checkAndShowDanmaku()
  rafId = requestAnimationFrame(danmakuSyncLoop)
}

const startDanmakuSync = () => {
  if (!danmakuEnabled.value || rafId) return
  rafId = requestAnimationFrame(danmakuSyncLoop)
}

const stopDanmakuSync = () => {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
}

const alignDanmakuIndex = () => {
  if (!videoRef.value) return
  const currentVideoTime = videoRef.value.currentTime
  danmakuIndex = danmakuListSorted.findIndex(d => d.time > currentVideoTime)
  if (danmakuIndex === -1) {
    danmakuIndex = danmakuListSorted.length
  }
}

const resetDanmakuState = () => {
  visibleDanmakuList.value = []
  displayedDanmakuIds.clear()
  danmakuTracks = [null, null, null, null, null, null, null, null, null, null]
}

const formatTime = (seconds) => {
  const totalSeconds = Math.floor(seconds || 0)
  const hrs = Math.floor(totalSeconds / 3600)
  const mins = Math.floor((totalSeconds % 3600) / 60)
  const secs = totalSeconds % 60
  if (hrs > 0) {
    return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 10000) {
    return `${(num / 10000).toFixed(1)}万`
  }
  return String(num)
}

const incrementPlayCount = async () => {
  if (hasIncrementedPlayCount.value) return
  hasIncrementedPlayCount.value = true
  const videoId = props.videoData.id
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/play`, {
      method: 'POST',
      headers: getAuthHeaders(true)
    })
    const result = await response.json()
    if (result.code === 200) {
      playCount.value = result.data.playCount || playCount.value + 1
    }
  } catch (error) {
    console.error('增加播放量失败:', error)
  }
}

const loadUserVideoStatus = async () => {
  if (!isLoggedIn.value || !currentUserId.value) {
    return
  }
  const videoId = props.videoData.id
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/status`, {
      headers: getAuthHeaders()
    })
    const result = await response.json()
    if (result.code === 200) {
      liked.value = result.data.liked
      favorited.value = result.data.favorited
    }
  } catch (error) {
    console.error('加载用户视频状态失败:', error)
  }
}

const toggleLike = async () => {
  if (!currentUserId.value) {
    alert('请先登录')
    return
  }
  const videoId = props.videoData.id
  try {
    const method = liked.value ? 'DELETE' : 'POST'
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/likes`, {
      method,
      headers: getAuthHeaders()
    })
    const result = await response.json()
    if (result.code === 200) {
      liked.value = result.data.liked
      likeCount.value = result.data.likeCount
    } else if (result.message) {
      alert(result.message)
    }
  } catch (error) {
    console.error('点赞失败:', error)
  }
}

const toggleFavorite = async () => {
  if (!currentUserId.value) {
    alert('请先登录')
    return
  }
  const videoId = props.videoData.id
  try {
    const method = favorited.value ? 'DELETE' : 'POST'
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/favorites`, {
      method,
      headers: getAuthHeaders()
    })
    const result = await response.json()
    if (result.code === 200) {
      favorited.value = result.data.favorited
      favoriteCount.value = result.data.favoriteCount
    } else if (result.message) {
      alert(result.message)
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
    videoRef.value.play().catch(() => {})
  }
}

const onPlay = () => {
  isPlaying.value = true
  showControls.value = true
  resetHideTimer()
  startDanmakuSync()
  incrementPlayCount()
}

const onPause = () => {
  isPlaying.value = false
  showControls.value = true
  stopDanmakuSync()
  saveWatchProgress(true)
}

const onEnded = () => {
  isPlaying.value = false
  currentTime.value = 0
  currentProgress.value = 0
  showControls.value = true
  stopDanmakuSync()
  saveWatchProgress(true, 0)
}

const onLoadedMetadata = () => {
  if (videoRef.value) {
    duration.value = videoRef.value.duration
    const resumeSeconds = resumeProgressSeconds.value
    const videoDuration = Number(videoRef.value.duration || 0)
    if (!resumeApplied.value && resumeSeconds > 0) {
      const targetTime = videoDuration > 1 ? Math.min(resumeSeconds, Math.max(videoDuration - 1, 0)) : resumeSeconds
      videoRef.value.currentTime = Math.max(targetTime, 0)
      currentTime.value = videoRef.value.currentTime
      currentProgress.value = duration.value ? (currentTime.value / duration.value) * 100 : 0
      resumeApplied.value = true
      lastSavedProgressSeconds.value = Math.floor(currentTime.value)
    }
  }
}

const onCanPlay = () => {
  isVideoReady.value = true
}

const onVideoError = (event) => {
  console.error('视频播放出错:', event)
}

const onWaiting = () => {
  isLoading.value = true
}

const onVolumeChange = () => {
  if (!videoRef.value) return
  volume.value = videoRef.value.volume
  isMuted.value = videoRef.value.muted
}

const onTimeUpdate = () => {
  if (!videoRef.value) return
  currentTime.value = videoRef.value.currentTime
  duration.value = videoRef.value.duration || duration.value
  currentProgress.value = duration.value ? (currentTime.value / duration.value) * 100 : 0
  const buffered = videoRef.value.buffered
  if (buffered.length > 0 && duration.value) {
    bufferedProgress.value = (buffered.end(buffered.length - 1) / duration.value) * 100
  }
  isLoading.value = false
  saveWatchProgress()
}

const saveWatchProgress = async (force = false, explicitSeconds = null) => {
  if (!currentUserId.value || !props.videoData?.id) {
    return
  }

  const nextSeconds = explicitSeconds == null
    ? Math.max(0, Math.floor(videoRef.value?.currentTime || currentTime.value || 0))
    : Math.max(0, Math.floor(explicitSeconds))

  if (!force && nextSeconds <= 0) {
    return
  }

  if (!force && nextSeconds === lastSavedProgressSeconds.value) {
    return
  }

  if (!force && progressSaveTimer) {
    return
  }

  const doSave = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/videos/${props.videoData.id}/history/progress?progressSeconds=${nextSeconds}`, {
        method: 'PUT',
        headers: getAuthHeaders()
      })
      const result = await response.json()
      if (result.code === 200) {
        lastSavedProgressSeconds.value = nextSeconds
      }
    } catch (error) {
      console.warn('保存观看进度失败:', error)
    }
  }

  if (force) {
    if (progressSaveTimer) {
      clearTimeout(progressSaveTimer)
      progressSaveTimer = null
    }
    await doSave()
    return
  }

  progressSaveTimer = setTimeout(async () => {
    progressSaveTimer = null
    await doSave()
  }, 5000)
}

const onVolumeInput = () => {
  if (!videoRef.value) return
  videoRef.value.volume = volume.value
  videoRef.value.muted = volume.value === 0
}

const toggleMute = () => {
  if (!videoRef.value) return
  videoRef.value.muted = !videoRef.value.muted
  if (!videoRef.value.muted && videoRef.value.volume === 0) {
    videoRef.value.volume = 0.5
  }
}

const updateProgressFromEvent = (event) => {
  if (!videoRef.value) return
  const rect = event.currentTarget ? event.currentTarget.getBoundingClientRect() : document.querySelector('.progress-bar')?.getBoundingClientRect()
  if (!rect) return
  const ratio = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1)
  currentProgress.value = ratio * 100
  currentTime.value = ratio * duration.value
  videoRef.value.currentTime = currentTime.value
}

const seekVideo = (event) => {
  updateProgressFromEvent(event)
  resetDanmakuState()
  alignDanmakuIndex()
}

const startDragProgress = () => {
  isDraggingProgress.value = true
}

const stopDragProgress = () => {
  if (!isDraggingProgress.value) return
  isDraggingProgress.value = false
  resetDanmakuState()
  alignDanmakuIndex()
}

const toggleDanmaku = () => {
  danmakuEnabled.value = !danmakuEnabled.value
  if (!danmakuEnabled.value) {
    stopDanmakuSync()
    visibleDanmakuList.value = []
    return
  }
  resetDanmakuState()
  alignDanmakuIndex()
  startDanmakuSync()
}

const showDanmakuSettings = ref(false)
const toggleDanmakuSettings = () => {
  showDanmakuSettings.value = !showDanmakuSettings.value
}

let clickTimer = null
let lastClickTime = 0
const DOUBLE_CLICK_THRESHOLD = 300

const handleVideoClick = (e) => {
  const target = e.target
  if (target.closest('.controls-overlay') ||
      target.closest('.danmaku-input-area') ||
      target.closest('.dropdown') ||
      target.closest('.control-btn')) {
    return
  }

  const now = Date.now()
  const diff = now - lastClickTime
  if (diff < DOUBLE_CLICK_THRESHOLD && diff > 0) {
    clearTimeout(clickTimer)
    lastClickTime = 0
    toggleLike()
    showLikeAnimation.value = true
    setTimeout(() => {
      showLikeAnimation.value = false
    }, 1000)
    return
  }

  lastClickTime = now
  clearTimeout(clickTimer)
  clickTimer = setTimeout(() => {
    lastClickTime = 0
    togglePlay()
  }, DOUBLE_CLICK_THRESHOLD)
}

const handleKeyDown = (e) => {
  if (!videoRef.value) return
  const isInputField = ['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)
  if (isInputField) return

  switch (e.key) {
    case ' ':
      e.preventDefault()
      togglePlay()
      break
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
  if (quality === currentQuality.value || !videoRef.value) return
  isLoading.value = true
  const savedTime = currentTime.value
  const wasPlaying = isPlaying.value
  videoRef.value.pause()
  setTimeout(() => {
    currentQuality.value = quality
    videoRef.value.src = currentSrc.value
    videoRef.value.load()
    videoRef.value.currentTime = savedTime
    setTimeout(() => {
      if (wasPlaying) {
        videoRef.value.play().catch(() => {})
      }
      isLoading.value = false
    }, 500)
  }, 300)
  showQualityDropdown.value = false
}

const toggleQualityDropdown = () => {
  showQualityDropdown.value = !showQualityDropdown.value
  showSpeedDropdown.value = false
}

const toggleSpeedDropdown = () => {
  showSpeedDropdown.value = !showSpeedDropdown.value
  showQualityDropdown.value = false
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

const hideControlsTimer = ref(null)

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

const sendDanmaku = async () => {
  if (!danmakuInput.value.trim()) return

  const userId = localStorage.getItem('loginUserId') || 'anonymous'
  const currentVideoTime = videoRef.value ? videoRef.value.currentTime : currentTime.value
  const newDanmaku = {
    id: Date.now(),
    content: danmakuInput.value,
    color: danmakuColor.value,
    time: currentVideoTime,
    userId,
    isUser: true
  }

  danmakuList.value.push(newDanmaku)
  danmakuInput.value = ''
  showSingleDanmaku(newDanmaku)
  sortDanmakuByTime()

  try {
    const videoId = props.videoData.id
    const requestBody = {
      content: newDanmaku.content,
      timeSeconds: newDanmaku.time,
      color: newDanmaku.color,
      userId: newDanmaku.userId
    }
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/danmakus`, {
      method: 'POST',
      headers: getAuthHeaders(true),
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
  saveWatchProgress(true)
  emit('back')
}

const openRelatedVideo = (video) => {
  if (!video?.id) {
    return
  }
  saveWatchProgress(true)
  emit('open-video', video)
}

const loadComments = async (page = 1, append = false) => {
  loadingComments.value = true
  try {
    const videoId = props.videoData.id
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/comments?page=${page}&pageSize=${commentPageSize}`, {
      headers: getAuthHeaders()
    })
    const result = await response.json()

    if (result.code === 200 && result.data) {
      const data = result.data
      const normalizedList = normalizeCommentTree(data.list || [])
      if (append) {
        commentsListData.value = normalizeCommentTree([...commentsListData.value, ...normalizedList])
      } else {
        commentsListData.value = normalizedList
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
  const targetParentId = replyTarget.value
    ? (replyTarget.value.rootId || replyTarget.value.parentId || replyTarget.value.parent_id || replyTarget.value.commentId || replyTarget.value.id)
    : null
  const requestBody = {
    content: commentInput.value,
    parentId: targetParentId,
    userId: currentUserId.value ? Number(currentUserId.value) : null
  }

  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/comments`, {
      method: 'POST',
      headers: getAuthHeaders(true),
      body: JSON.stringify(requestBody)
    })
    const result = await response.json()
    if (result.code === 200) {
      commentInput.value = ''
      replyTarget.value = null
      currentCommentPage.value = 1
      loadComments(1, false)
    } else {
      alert(`发送评论失败: ${result.message || '未知错误'}`)
    }
  } catch (error) {
    alert(`发送评论失败，请检查网络连接: ${error.message}`)
  }
}

const handleCommentLike = async (commentId) => {
  if (!currentUserId.value) {
    alert('请先登录')
    return
  }

  const videoId = props.videoData.id
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/comments/${commentId}/like`, {
      method: 'POST',
      headers: getAuthHeaders()
    })
    const result = await response.json()
    if (result.code === 200) {
      const comment = findCommentInTree(commentId)
      if (comment && result.data) {
        comment.likeCount = result.data.likeCount
        comment.liked = result.data.liked
      }
    } else {
      alert(result.message || '评论点赞失败')
    }
  } catch (error) {
    alert(`评论点赞失败: ${error.message}`)
  }
}

const replyComment = (comment) => {
  replyTarget.value = comment
  commentInput.value = ''
}

const clearReplyTarget = () => {
  replyTarget.value = null
}

const canDeleteComment = (comment) => {
  if (!currentUserId.value || !comment?.userId) {
    return false
  }
  return String(comment.userId) === String(currentUserId.value)
}

const deleteComment = async (commentId) => {
  if (!currentUserId.value) {
    alert('请先登录')
    return
  }
  if (!confirm('确定删除这条评论吗？')) {
    return
  }

  const videoId = props.videoData.id
  try {
    const response = await fetch(`http://localhost:8080/api/videos/${videoId}/comments/${commentId}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    })
    const result = await response.json()
    if (result.code === 200) {
      const removedRootComment = removeCommentFromTree(commentId)
      if (removedRootComment) {
        commentsTotal.value = Math.max((commentsTotal.value || 0) - 1, 0)
      }
    } else {
      alert(result.message || '删除评论失败')
    }
  } catch (error) {
    alert(`删除评论失败: ${error.message}`)
  }
}

const loadMoreComments = () => {
  if (hasMoreComments.value && !loadingComments.value) {
    loadComments(currentCommentPage.value + 1, true)
  }
}

const getCommentId = (comment) => comment?.commentId || comment?.id

const normalizeCommentTree = (list) => {
  const source = Array.isArray(list) ? list : []
  const flattened = []
  source.forEach((comment) => {
    flattened.push({
      ...comment,
      commentId: getCommentId(comment),
      replies: []
    })
    ;(comment.replies || []).forEach((reply) => {
      flattened.push({
        ...reply,
        commentId: getCommentId(reply),
        parentId: reply.parentId || reply.parent_id || getCommentId(comment),
        replies: []
      })
    })
  })

  const map = new Map()
  flattened.forEach((comment) => {
    const id = getCommentId(comment)
    if (id != null) {
      map.set(String(id), {
        ...comment,
        commentId: id,
        replies: []
      })
    }
  })

  const roots = []
  map.forEach((comment) => {
    const parentId = comment.parentId || comment.parent_id
    const parent = parentId != null ? map.get(String(parentId)) : null
    if (parent) {
      comment.replyToUser = comment.replyToUser || parent.user || null
      parent.replies.push(comment)
      parent.replyCount = parent.replies.length
    } else {
      roots.push(comment)
    }
  })

  roots.forEach((comment) => {
    comment.replies = (comment.replies || []).sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0))
    comment.replyCount = comment.replies.length
  })

  return roots.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
}

const findCommentInTree = (commentId) => {
  for (const comment of commentsListData.value) {
    if (String(comment.commentId) === String(commentId)) {
      return comment
    }
    const reply = (comment.replies || []).find(item => String(item.commentId) === String(commentId))
    if (reply) {
      return reply
    }
  }
  return null
}

const removeCommentFromTree = (commentId) => {
  const beforeRootCount = commentsListData.value.length
  commentsListData.value = commentsListData.value.filter(comment => String(comment.commentId) !== String(commentId))
  if (commentsListData.value.length !== beforeRootCount) {
    return true
  }

  commentsListData.value = commentsListData.value.map((comment) => {
    const removedReply = (comment.replies || []).some(reply => String(reply.commentId) === String(commentId))
    return {
      ...comment,
      replies: (comment.replies || []).filter(reply => String(reply.commentId) !== String(commentId)),
      replyCount: Math.max(Number(comment.replyCount || 0) - (removedReply ? 1 : 0), 0)
    }
  })
  return false
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  return date.toLocaleDateString('zh-CN')
}

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
  loadDanmakuFromServer()
  loadComments()
  loadUserVideoStatus()
})

onUnmounted(() => {
  saveWatchProgress(true)
  clearTimeout(hideControlsTimer.value)
  if (progressSaveTimer) {
    clearTimeout(progressSaveTimer)
    progressSaveTimer = null
  }
  if (shareCopiedTimer) {
    clearTimeout(shareCopiedTimer)
    shareCopiedTimer = null
  }
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
  padding: 20px 28px 32px;
  max-width: 1680px;
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
  left: 100%;
  transform: translateX(0);
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
    transform: translateX(calc(-100% - 100vw));
  }
}

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

.icon-control {
  width: 38px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  line-height: 1;
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
  font-size: 34px;
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

.video-header-section {
  padding: 0 0 14px;
}

.player-content-layout {
  display: grid;
  grid-template-columns: minmax(720px, 1fr) 360px;
  gap: 30px;
  align-items: start;
}

.video-main-column {
  min-width: 0;
}

.video-info-area {
  padding: 16px 0 0;
}

.video-stats-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  padding: 16px 0 14px;
  margin-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.video-sidebar-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 20px;
}

.video-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 8px;
}

.video-title {
  font-size: 24px;
  font-weight: 700;
  color: #1e1e1e;
  flex: 1;
  line-height: 1.45;
}

.video-meta {
  font-size: 13px;
  color: #999;
}

.title-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.video-description {
  font-size: 14px;
  line-height: 1.7;
  color: #444;
  margin-bottom: 16px;
}

.video-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 24px;
}

.video-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f3f6fb;
  color: #2563eb;
  font-size: 13px;
  font-weight: 500;
}

.meta-divider {
  margin: 0 8px;
}

.video-author {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 0;
  padding: 4px 0 18px;
  border-bottom: 1px solid #eef2f7;
  background: transparent;
}

.author-avatar {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #2563eb 0%, #0ea5e9 100%);
  border-radius: 50%;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  overflow: hidden;
}

.author-avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-meta {
  flex: 1;
  min-width: 0;
}

.clickable-avatar,
.clickable-user {
  cursor: pointer;
}

.author-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  line-height: 1.4;
}

.author-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.follow-btn {
  padding: 9px 24px;
  background: #00aeec;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: bold;
  font-size: 14px;
  margin-left: auto;
  white-space: nowrap;
}

.follow-btn:hover {
  background: #0098d8;
}

.related-videos {
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
}

.related-header {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 14px;
}

.related-empty {
  color: #94a3b8;
  font-size: 13px;
}

.related-item {
  width: 100%;
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.related-item + .related-item {
  border-top: 1px solid #f1f5f9;
}

.related-cover {
  width: 132px;
  height: 74px;
  border-radius: 10px;
  object-fit: cover;
  background: #e2e8f0;
  flex-shrink: 0;
}

.related-cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: #475569;
}

.related-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.related-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
}

.related-author,
.related-meta {
  font-size: 12px;
  color: #6b7280;
}

.video-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-shrink: 0;
  padding: 12px 0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f6f8fb;
  border: 1px solid transparent;
  cursor: pointer;
  padding: 10px 16px;
  transition: all 0.2s ease;
  border-radius: 999px;
  color: #61666d;
}

.action-btn:hover {
  background: #eef7ff;
  color: #00aeec;
  transform: translateY(-1px);
}

.action-icon {
  width: 22px;
  height: 22px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-icon :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.action-text {
  font-size: 14px;
  color: inherit;
  font-weight: 700;
}

.action-btn.active {
  background: #e8f7ff;
  border-color: rgba(0, 174, 236, 0.18);
  color: #00aeec;
}

.like-btn.active .action-icon {
  animation: like-bounce 0.3s ease;
}

.favorite-btn.active .action-icon {
  animation: favorite-glow 0.3s ease;
}

.share-btn {
  color: #6b7280;
}

@keyframes like-bounce {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.3); }
}

@keyframes favorite-glow {
  0%, 100% { filter: none; }
  50% { filter: drop-shadow(0 0 10px #ffd700); }
}

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

.comments-area {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 960px) {
  .player-content-layout {
    grid-template-columns: 1fr;
  }

  .video-stats-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .video-sidebar-column {
    position: static;
  }

  .related-cover {
    width: 112px;
    height: 64px;
  }
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  margin-bottom: 24px;
}

.reply-target-bar {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 14px;
  border-radius: 12px;
  background: #eef7ff;
  color: #32749d;
  font-size: 13px;
}

.reply-target-bar button {
  border: none;
  background: transparent;
  color: #00aeec;
  cursor: pointer;
  font-weight: 700;
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
  overflow: hidden;
  color: #475569;
  font-size: 14px;
  font-weight: 700;
}

.comment-avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

.comment-delete-btn {
  margin-left: auto;
  background: transparent;
  border: none;
  color: #ff4d4f;
  font-size: 12px;
  cursor: pointer;
}

.comment-delete-btn:hover {
  color: #cf1322;
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
  gap: 18px;
}

.comment-action-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  background: transparent;
  border: none;
  color: #999;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s;
}

.comment-action-btn :deep(svg) {
  width: 15px;
  height: 15px;
  display: block;
}

.comment-action-btn.active {
  color: #00aeec;
}

.comment-action-btn:hover {
  color: #00aeec;
}

.comment-replies {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  display: grid;
  gap: 12px;
}

.reply-item {
  display: flex;
  gap: 10px;
}

.reply-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.reply-content {
  flex: 1;
  min-width: 0;
}

.reply-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.reply-to {
  color: #8a8f99;
  font-size: 12px;
}

.reply-text {
  margin-bottom: 8px;
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
