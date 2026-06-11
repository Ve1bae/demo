<template>
  <div class="app-container">
    <header class="navbar">
      <div class="logo" @click="$router.push('/')">🚀 航音视频</div>

      <div class="search-box">
        <input type="text" placeholder="搜索感兴趣的视频、直播..." />
        <button class="search-btn">搜索</button>
      </div>

      <div class="user-actions">
        <button class="upload-btn" @click="goUpload">投稿</button>

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

    <div class="main-layout">
      <aside class="sidebar">
        <ul class="nav-links">
          <li :class="{ active: $route.path === '/' }" @click="$router.push('/')">🏠 首页推荐</li>
          <li>🔥 热门排行</li>
          <li>📡 直播大厅</li>
          <div class="divider"></div>
          <li>❤️ 我的关注</li>
          <li>🕒 历史记录</li>
          <li :class="{ active: $route.path === '/creator' }" @click="$router.push('/creator')">⚙️ 创作者中心</li>
        </ul>
      </aside>

      <main class="content">
        <router-view :key="$route.fullPath" />
      </main>
    </div>

    <!-- 登录弹窗 -->
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
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

const isLoggedIn = ref(false)
const showModal = ref(false)
const isRegisterMode = ref(false)
const currentUser = ref('')
let token = ref('')

const authForm = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: ''
})

onMounted(() => {
  const savedNickname = localStorage.getItem('loginUserNickname')
  const savedToken = localStorage.getItem('loginToken')
  if (savedNickname && savedToken) {
    isLoggedIn.value = true
    currentUser.value = savedNickname
    token.value = savedToken
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
  authForm.nickname = ''
}

const handleRegister = async () => {
  if (!authForm.username || !authForm.password || !authForm.confirmPassword || !authForm.nickname) {
    return alert("请填写完整的注册信息！")
  }
  if (authForm.password !== authForm.confirmPassword) {
    return alert("两次输入的密码不一致，请重新输入！")
  }

  try {
    const res = await axios.post('http://localhost:9090/api/user/register', {
      username: authForm.username,
      password: authForm.password,
      nickname: authForm.nickname
    })
    if (res.data.code === 200) {
      alert('🎉 注册成功！请使用新账号登录。')
      isRegisterMode.value = false
      authForm.password = ''
      authForm.confirmPassword = ''
      authForm.nickname = ''
    } else {
      alert(res.data.message)
    }
  } catch (error) {
    alert('网络请求失败，请检查后端是否启动。')
  }
}

const handleLogin = async () => {
  if (!authForm.username || !authForm.password) {
    alert("账号和密码不能为空！")
    return
  }

  try {
    const res = await axios.post('http://localhost:9090/api/user/login', {
      username: authForm.username,
      password: authForm.password
    })

    if (res.data.code === 200 && res.data.data) {
      isLoggedIn.value = true
      currentUser.value = res.data.data.user.nickname
      token.value = res.data.data.token

      localStorage.setItem('loginUserNickname', res.data.data.user.nickname)
      localStorage.setItem('loginUserId', res.data.data.user.id)
      localStorage.setItem('loginToken', res.data.data.token)

      showModal.value = false
    } else {
      alert(res.data.message)
    }
  } catch (error) {
    alert('网络请求失败，请确保 Spring Boot 已启动。')
  }
}

const logout = () => {
  isLoggedIn.value = false
  currentUser.value = ''
  token.value = ''
  localStorage.removeItem('loginUserNickname')
  localStorage.removeItem('loginUserId')
  localStorage.removeItem('loginToken')
}

const goUpload = () => {
  if (!isLoggedIn.value) {
    alert('请先登录')
    openLoginModal()
    return
  }
  router.push('/creator')
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }

.app-container {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f9f9f9;
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 24px;
  border-bottom: 1px solid #eaeaea;
  background: #fff;
  height: 60px;
  flex-shrink: 0;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  color: #1e1e1e;
  cursor: pointer;
  white-space: nowrap;
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
  background: #f4f4f4;
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

.login-action-btn:hover { background: #d0d0d0; }

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

.logout-btn:hover { color: #ff4757; border-color: #ff4757; }

.main-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 220px;
  background: #fff;
  padding: 16px 0;
  border-right: 1px solid #f0f0f0;
  overflow-y: auto;
  flex-shrink: 0;
}

.nav-links { list-style: none; padding: 0; margin: 0; }

.nav-links li {
  padding: 12px 24px;
  color: #333;
  cursor: pointer;
  transition: background 0.2s;
}

.nav-links li:hover { background: #eee; }

.nav-links li.active {
  background: #e6f7ff;
  color: #1890ff;
  font-weight: bold;
}

.divider { height: 1px; background: #ddd; margin: 15px 0; }

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: #f9f9f9;
}

/* 登录弹窗 */
.modal-overlay {
  position: fixed;
  top: 0; left: 0;
  width: 100vw; height: 100vh;
  background: rgba(0, 0, 0, 0.4);
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
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
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

.form-group { margin-bottom: 15px; }

.form-group input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  outline: none;
  font-size: 14px;
  box-sizing: border-box;
}

.form-group input:focus { border-color: #1890ff; }

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

.confirm-btn:hover { background: #1166b5; }

.cancel-btn {
  background: #f0f0f0;
  color: #666;
  border: none;
  padding: 10px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.cancel-btn:hover { background: #e0e0e0; }

.switch-mode { text-align: center; margin-top: 20px; font-size: 13px; }
.switch-mode a { color: #1890ff; text-decoration: none; }
.switch-mode a:hover { color: #096dd9; text-decoration: underline; }

@media screen and (max-width: 768px) {
  .sidebar { display: none; }
  .upload-btn { display: none; }
  .content { padding: 16px; }
}
</style>
