<template>
  <div class="app-container">
    <header class="navbar">
      <div class="logo">🚀 航音视频</div>
      <div class="search-box">
        <input type="text" placeholder="搜索感兴趣的视频、直播..." />
        <button class="search-btn">搜索</button>
      </div>
      <div class="user-actions">
        <button class="upload-btn">投稿</button>
        <div class="avatar">👨‍💻</div>
      </div>
    </header>

    <div class="main-layout">
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
          <div class="video-card" v-for="video in videoList" :key="video.id">
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
  </div>
</template>

<script setup>
import { ref } from 'vue';

// 模拟后端返回的视频列表数据 后替换为数据库请求
const videoList = ref([
  { id: 1, title: '【航音】2026届校园十佳歌手总决赛', author: '校学生会', views: '1.2万', duration: '02:15:30', date: '昨天' },
  { id: 2, title: '软件工程基础大作业避坑指南！', author: '学长小王', views: '8000', duration: '12:05', date: '3天前' },
  { id: 3, title: 'Vue3 + SpringBoot 从零实战', author: '码农老李', views: '5万', duration: '45:20', date: '1周前' },
  { id: 4, title: '校园街访：你最喜欢的食堂是哪家？', author: '航音生活圈', views: '3200', duration: '08:45', date: '5小时前' },
  { id: 5, title: '高数期末突击复习（全集）', author: '数学教研室', views: '10万+', duration: '04:00:00', date: '1个月前' },
  { id: 6, title: '晚霞绝美！航拍延时摄影', author: '光影社', views: '1500', duration: '03:12', date: '刚刚' },
  { id: 7, title: '前端CSS Grid布局完全指南', author: 'TechBro', views: '2.3万', duration: '28:10', date: '2天前' },
  { id: 8, title: '宿舍日常：当你的室友是个极客', author: '602寝室', views: '900', duration: '05:40', date: '10分钟前' }
]);
</script>

<style scoped>
/* --- 全局容器 --- */
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f9f9f9;
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
}

/* --- 导航栏样式 (响应式升级) --- */
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  height: 64px;
  background-color: #ffffff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  position: sticky;
  top: 0;
  z-index: 100;
}
.logo { 
  font-size: 20px; 
  font-weight: bold; 
  color: #1890ff; 
  white-space: nowrap; /* 防止换行 */
}

/* 【关键修改1：弹性搜索框】 */
.search-box { 
  display: flex; 
  flex: 1; /* 占据剩余空间 */
  max-width: 500px; /* 限制最大宽度，避免大屏时太长 */
  margin: 0 24px; /* 两侧留白，防止和 Logo/头像挤在一起 */
}
.search-box input {
  flex: 1; 
  padding: 8px 16px; 
  border: 1px solid #d9d9d9; 
  border-radius: 4px 0 0 4px; 
  outline: none;
  min-width: 0; /* 防止由于内容过长撑破 flex 容器 */
}
.search-btn {
  padding: 8px 16px; 
  background-color: #1890ff; 
  color: white; 
  border: none; 
  border-radius: 0 4px 4px 0; 
  cursor: pointer;
  white-space: nowrap;
}

.user-actions { 
  display: flex; 
  align-items: center; 
  gap: 16px; 
}
.upload-btn { 
  background-color: #ff4d4f; 
  color: white; 
  border: none; 
  padding: 8px 20px; 
  border-radius: 4px; 
  cursor: pointer; 
  font-weight: bold;
  white-space: nowrap;
}
.avatar { 
  width: 36px; 
  height: 36px; 
  background-color: #e6f7ff; 
  border-radius: 50%; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  font-size: 20px; 
  cursor: pointer;
  flex-shrink: 0; /* 防止头像被压缩 */
}

/* --- 主体与侧边栏样式 --- */
.main-layout { 
  display: flex; 
  flex: 1; 
  overflow: hidden; 
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
.nav-links { list-style: none; padding: 0; margin: 0; }
.nav-links li {
  padding: 12px 24px; color: #333; cursor: pointer; transition: background 0.2s;
}
.nav-links li:hover { background-color: #f5f5f5; }
.nav-links li.active { background-color: #e6f7ff; color: #1890ff; font-weight: bold; }
.divider { height: 1px; background-color: #f0f0f0; margin: 12px 0; }

/* --- 右侧内容与视频网格 --- */
.content { 
  flex: 1; 
  padding: 24px; 
  overflow-y: auto; 
}

/* 视频网格自适应 auto-fill 配合 minmax，让浏览器自己算能塞下几列 */
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}
.video-card { background: transparent; cursor: pointer; transition: transform 0.2s; }
.video-card:hover { transform: translateY(-4px); }
.thumbnail {
  width: 100%; aspect-ratio: 16 / 9; background-color: #d9d9d9; border-radius: 8px; position: relative;
}
.duration {
  position: absolute; bottom: 8px; right: 8px; background-color: rgba(0,0,0,0.7); color: white; padding: 2px 6px; font-size: 12px; border-radius: 4px;
}
.info { padding-top: 12px; }
.title { font-size: 15px; font-weight: 500; margin: 0 0 8px 0; color: #222; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.author, .stats { font-size: 13px; color: #999; margin: 0 0 4px 0; }

/* =========================================
   【针对不同屏幕尺寸做适配
   ========================================= */

/* 平板尺寸 (小于 1024px) */
@media screen and (max-width: 1024px) {
  .search-box {
    margin: 0 16px;
  }
  .video-grid {
    /* 稍微缩小卡片的最小宽度，适应平板 */
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  }
}

/* 手机尺寸 (小于 768px) */
@media screen and (max-width: 768px) {
  .navbar {
    padding: 0 16px;
  }
  
  /* 隐藏左侧侧边栏，实际开发中这里会改成点击按钮弹出的抽屉(Drawer) */
  .sidebar {
    display: none; 
  }
  
  /* 隐藏顶部的投稿按钮，节约空间 */
  .upload-btn {
    display: none; 
  }

  /* 缩小搜索框 */
  .search-box {
    margin: 0 10px;
  }
  
  .content {
    padding: 16px;
  }

  /* 手机端通常视频直接占满屏幕一行，或者两列小图 */
  .video-grid {
    grid-template-columns: repeat(auto-fill, minmax(100%, 1fr));
    gap: 16px;
  }
}
</style>