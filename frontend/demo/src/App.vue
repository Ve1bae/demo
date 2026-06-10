<template>
  <div class="app-container">
    <header class="navbar" v-show="!showVideoPlayer">
      <div class="logo">🚀 航音视频</div>

      <div class="search-box">
        <input type="text" placeholder="搜索感兴趣的视频、直播..." />
        <button class="search-btn">搜索</button>
      </div>

      <div class="user-actions">
        <button class="upload-btn">投稿</button>
        
        <template v-if="!isLoggedIn">
          <button class="login-action-btn" @click="openLoginModal">登 录</button>
        </template>
        
        <template v-else>
          <div class="user-profile">
            <div class="avatar">👨‍💻</div>
            <span class="username">{{ currentUser }}</span>
            <button class="logout-btn" @click="logout">退出</button>
          </div>
        </template>
      </div>
    </header>

    <div class="main-layout" v-show="!showVideoPlayer">
      <aside class="sidebar">
        <ul class="nav-links">
          <li class="active">🏠 首页推荐</li>
          <li>🔥 热门排行</li>
          <li>📡 直播大厅</li>
          <div class="divider"></div>
          <li>❤️ 我的关注</li>
          <li>🕒 历史记录</li>
          <li>⚙️ 创作者中心</li>
        </ul>
      </aside>

      <main class="content">
        <div class="video-grid">
          <div class="video-card" v-for="video in videoList" :key="video.id" @click="openVideoPlayer(video)">
            <div class="thumbnail">
              <span class="duration">{{ video.duration }}</span>
            </div>
            <div class="info">
              <h3 class="title">{{ video.title }}</h3>
              <p class="author">{{ video.author }}</p>
              <p class="stats">{{ video.views }} 观看 · {{ video.date }}</p>
            </div>
          </div>
        </div>
      </main>
    </div>

    <VideoPlayer v-if="showVideoPlayer" :videoData="selectedVideo" @back="showVideoPlayer = false" />

    <div class="modal-overlay" v-if="showModal" @click.self="closeModal">
      <div class="modal-content">
        <h2>{{ isRegisterMode ? '注册新账号' : '欢迎来到航音' }}</h2>
        <p class="subtitle">{{ isRegisterMode ? '加入我们，探索更多精彩内容' : '登录畅享高清视频与实时弹幕' }}</p>
        
        <div class="form-group">
          <input v-model="authForm.username" type="text" placeholder="请输入用户名" />
        </div>
        
        <div class="form-group" v-if="isRegisterMode">
          <input v-model="authForm.nickname" type="text" placeholder="请输入你的昵称" />
        </div>
        
        <div class="form-group">
          <input v-model="authForm.password" type="password" placeholder="请输入密码" @keyup.enter="isRegisterMode ? handleRegister() : handleLogin()" />
        </div>
        
        <div class="form-group" v-if="isRegisterMode">
          <input v-model="authForm.confirmPassword" type="password" placeholder="请再次输入密码确认" @keyup.enter="handleRegister" />
        </div>
        
        <div class="modal-actions">
          <button class="confirm-btn" @click="isRegisterMode ? handleRegister() : handleLogin()">
            {{ isRegisterMode ? '立 即 注 册' : '登 录' }}
          </button>
          <button class="cancel-btn" @click="showModal = false">取消</button>
        </div>

        <div class="switch-mode">
          <a href="#" @click.prevent="toggleMode">
            {{ isRegisterMode ? '已有账号？点击直接登录' : '还没有账号？点击这里注册' }}
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import axios from 'axios'
import VideoPlayer from './videocomponents/VideoPlayer.vue'

// --- 登录/注册 核心状态 ---
const isLoggedIn = ref(false)
const showModal = ref(false)
const isRegisterMode = ref(false)
const currentUser = ref('')
const showVideoPlayer = ref(false)
const selectedVideo = ref(null)

const authForm = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: ''
})

onMounted(() => {
  const savedUser = localStorage.getItem('loginUser')
  if (savedUser) {
    isLoggedIn.value = true
    currentUser.value = savedUser
  }
})

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
}

const openVideoPlayer = (video) => {
  selectedVideo.value = video
  showVideoPlayer.value = true
  window.scrollTo(0, 0)
}

// 注册逻辑
const handleRegister = async () => {
  if (!authForm.username || !authForm.password || !authForm.confirmPassword || !authForm.nickname) {
    return alert("请填写完整的注册信息！")
  }
  if (authForm.password !== authForm.confirmPassword) {
    return alert("两次输入的密码不一致，请重新输入！")
  }
  
  try {
    const res = await axios.post('http://localhost:8080/api/user/register', {
      username: authForm.username,
      password: authForm.password,
      nickname: authForm.nickname
    })
    if (res.data === '注册成功！') {
      alert('🎉 注册成功！请使用新账号登录。')
      isRegisterMode.value = false 
      authForm.password = ''
      authForm.confirmPassword = ''
      authForm.nickname = ''
    } else {
      alert(res.data) 
    }
  } catch (error) {
    alert('网络请求失败，请检查后端是否启动。')
  }
}

const currentUserId = ref(null)

// 2. 页面加载时恢复状态（同时取出 ID 和 昵称）
onMounted(() => {
  const savedNickname = localStorage.getItem('loginUserNickname')
  const savedUserId = localStorage.getItem('loginUserId')
  if (savedNickname && savedUserId) {
    isLoggedIn.value = true
    currentUser.value = savedNickname
    currentUserId.value = savedUserId
  }
})

// 3. 修改登录逻辑 (重点)
const handleLogin = async () => {
  if (!authForm.username || !authForm.password) {
    alert("账号和密码不能为空！")
    return
  }
  
  try {
    const res = await axios.post('http://localhost:8080/api/user/login', {
      username: authForm.username,
      password: authForm.password
    })

    // 判断后端返回的是不是对象（对象说明登录成功，字符串说明抛出了异常）
    if (typeof res.data === 'object' && res.data.id) {
      isLoggedIn.value = true
      
      // 取出后端返回的 id 和 昵称
      currentUser.value = res.data.nickname
      currentUserId.value = res.data.id
      
      // 存入小仓库 (localStorage)
      localStorage.setItem('loginUserNickname', res.data.nickname)
      localStorage.setItem('loginUserId', res.data.id)
      
      showModal.value = false
    } else {
      alert(res.data) // 显示 "登录失败：密码错误！" 等
    }
  } catch (error) {
    alert('网络请求失败，请确保 Spring Boot 已启动。')
  }
}

// 4. 修改退出逻辑
const logout = () => {
  isLoggedIn.value = false
  currentUser.value = ''
  currentUserId.value = null
  localStorage.removeItem('loginUserNickname')
  localStorage.removeItem('loginUserId')
}

// ==========================================
// 2. 从后端 API 获取视频列表
//    - 视频上传模块（他人负责）会把视频信息写入数据库
//    - 播放模块（本模块）从数据库读取视频列表
//    - 如果后端未启动或数据库无数据，使用 fallback 的示例数据
// ==========================================
const videoList = ref([]);

// 将后端 Video 实体转换为前端需要的格式（组装 sources 对象）
// 按照 API 规范处理字段
const convertVideoFromBackend = (v) => {
  // 优先使用后端返回的 sources 字段
  let sources = v.sources || {};
  
  // 如果后端没有返回 sources，尝试从各清晰度字段构建
  if (Object.keys(sources).length === 0) {
    if (v.url240p) sources['240P'] = v.url240p;
    if (v.url360p) sources['360P'] = v.url360p;
    if (v.url480p) sources['480P'] = v.url480p;
    if (v.url720p) sources['720P'] = v.url720p;
    if (v.url1080p) sources['1080P'] = v.url1080p;
    
    // 如果还是没有，使用 playUrl 作为所有清晰度的源
    if (Object.keys(sources).length === 0 && v.playUrl) {
      sources = {
        '240P': v.playUrl,
        '360P': v.playUrl,
        '480P': v.playUrl,
        '720P': v.playUrl,
        '1080P': v.playUrl
      };
    }
  }
  
  // 使用 videoId 或 id（兼容新旧字段）
  const videoId = v.videoId || v.id;
  
  // 使用 playCount 或 views（兼容新旧字段）
  const views = v.views || (v.playCount ? formatPlayCount(v.playCount) : '0');
  
  return {
    id: videoId,
    videoId: videoId,
    title: v.title,
    author: v.author || '',
    views: views,
    duration: v.duration || '',
    date: v.createdAt ? formatDate(v.createdAt) : '',
    videoUrl: v.videoUrl || '',
    playUrl: v.playUrl || '',
    coverUrl: v.coverUrl || '',
    description: v.description || '',
    sources: sources,
    defaultQuality: v.defaultQuality || '720P',
    likeCount: v.likeCount || 0,
    favoriteCount: v.favoriteCount || 0,
    commentCount: v.commentCount || 0
  };
};

const formatPlayCount = (count) => {
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + '万';
  }
  return count.toString();
};

const formatDate = (dateStr) => {
  try {
    const d = new Date(dateStr);
    const now = new Date();
    const diff = Math.floor((now - d) / 1000);
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + '分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + '小时前';
    if (diff < 604800) return Math.floor(diff / 86400) + '天前';
    return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate();
  } catch (e) {
    return '';
  }
};

// Fallback 示例数据（当后端未启动或无数据时使用）
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
  },
  {
    id: 2,
    title: '软件工程基础大作业避坑指南！',
    author: '学长小王',
    views: '8000',
    duration: '12:05',
    date: '3天前',
    videoUrl: 'video-se-guide',
    sources: {
      '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '480P'
  },
  {
    id: 3,
    title: 'Vue3 + SpringBoot 从零实战',
    author: '码农老李',
    views: '5万',
    duration: '45:20',
    date: '1周前',
    videoUrl: 'video-vue3-springboot-tutorial',
    sources: {
      '240P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '1080P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 4,
    title: '校园街访：你最喜欢的食堂是哪家？',
    author: '航音生活圈',
    views: '3200',
    duration: '08:45',
    date: '5小时前',
    videoUrl: 'video-campus-interview',
    sources: {
      '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '480P'
  },
  {
    id: 5,
    title: '高数期末突击复习（全集）',
    author: '数学教研室',
    views: '10万+',
    duration: '04:00:00',
    date: '1个月前',
    videoUrl: 'video-calculus-review',
    sources: {
      '240P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '360P'
  },
  {
    id: 6,
    title: '晚霞绝美！航拍延时摄影',
    author: '光影社',
    views: '1500',
    duration: '03:12',
    date: '刚刚',
    videoUrl: 'video-sunset-timelapse',
    sources: {
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '1080P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '1080P'
  },
  {
    id: 7,
    title: '前端CSS Grid布局完全指南',
    author: 'TechBro',
    views: '2.3万',
    duration: '28:10',
    date: '2天前',
    videoUrl: 'video-css-grid-guide',
    sources: {
      '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '1080P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 8,
    title: '宿舍日常：当你的室友是个极客',
    author: '602寝室',
    views: '900',
    duration: '05:40',
    date: '10分钟前',
    videoUrl: 'video-dorm-daily',
    sources: {
      '240P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '360P': 'https://www.w3schools.com/html/mov_bbb.mp4',
      '480P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '360P'
  }
];

// 从后端加载视频列表（按照 API 规范调用）
const loadVideoList = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/videos/recommend');
    // 按照 API 规范，响应格式为 { code, message, data }
    if (res.data && res.data.code === 200 && res.data.data && res.data.data.length > 0) {
      videoList.value = res.data.data.map(convertVideoFromBackend);
      return;
    }
  } catch (e) {
    console.log('从后端加载视频列表失败，使用 fallback 数据');
  }
  videoList.value = fallbackVideos;
};

loadVideoList();
</script>

<style scoped>
/* 原有的基础样式保留 */
.app-container {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
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
}

.search-box {
  display: flex;
  flex: 1;
  max-width: 400px;
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

/* 用户操作区样式 */
.user-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.upload-btn {
  background: #ff4757;
  color: white;
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
  transition: background 0.3s;
}

.login-action-btn:hover {
  background: #d0d0d0;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  font-size: 24px;
  background: #f0f0f0;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #ddd;
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

.logout-btn:hover {
  color: #ff4757;
  border-color: #ff4757;
}

/* 主体内容区样式 */
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
  flex-shrink: 0; /* 防止侧边栏被压缩 */
  transition: all 0.3s ease; /* 添加过渡动画 */
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
}

.duration {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background-color: rgba(0,0,0,0.7);
  color: white;
  padding: 2px 6px;
  font-size: 12px;
  border-radius: 4px;
}

.info {
  padding-top: 12px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin: 0 0 8px 0;
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
  margin: 0 0 4px 0;
}

/* 登录弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0,0,0,0.4);
  backdrop-filter: blur(2px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
}

.modal-content {
  background: white;
  padding: 40px;
  border-radius: 12px;
  width: 360px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.2);
}

.modal-content h2 {
  margin: 0 0 5px 0;
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

.form-group input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  outline: none;
  font-size: 14px;
  box-sizing: border-box;
}

.form-group input:focus {
  border-color: #1890ff;
}

.modal-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.confirm-btn {
  background: #1890ff;
  color: white;
  border: none;
  padding: 12px;
  border-radius: 6px;
  font-size: 16px;
  cursor: pointer;
  font-weight: bold;
}

.confirm-btn:hover {
  background: #1166b5;
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

.cancel-btn:hover {
  background: #e0e0e0;
}

.switch-mode { text-align: center; margin-top: 20px; font-size: 13px; }
.switch-mode a { color: #1890ff; text-decoration: none; transition: color 0.3s; }
.switch-mode a:hover { color: #096dd9; text-decoration: underline; }

/* 响应式适配 */
@media screen and (max-width: 1024px) {
  .search-box {
    margin: 0 16px;
  }
}
</style>
