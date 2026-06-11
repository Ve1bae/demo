<template>
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
</template>

<script setup>
import { ref } from 'vue'

const videoList = ref([
  { id: 1, title: '【航音】2026届校园十佳歌手总决赛', author: '校学生会', views: '1.2万', duration: '02:15:30', date: '昨天' },
  { id: 2, title: '软件工程基础大作业避坑指南！', author: '学长小王', views: '8000', duration: '12:05', date: '3天前' },
  { id: 3, title: 'Vue3 + SpringBoot 从零实战', author: '码农老李', views: '5万', duration: '45:20', date: '1周前' },
  { id: 4, title: '校园街访：你最喜欢的食堂是哪家？', author: '航音生活圈', views: '3200', duration: '08:45', date: '5小时前' },
  { id: 5, title: '高数期末突击复习（全集）', author: '数学教研室', views: '10万+', duration: '04:00:00', date: '1个月前' },
  { id: 6, title: '晚霞绝美！航拍延时摄影', author: '光影社', views: '1500', duration: '03:12', date: '刚刚' },
  { id: 7, title: '前端CSS Grid布局完全指南', author: 'TechBro', views: '2.3万', duration: '28:10', date: '2天前' },
  { id: 8, title: '宿舍日常：当你的室友是个极客', author: '602寝室', views: '900', duration: '05:40', date: '10分钟前' }
])
</script>

<style scoped>
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.video-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.video-card:hover { transform: translateY(-4px); }

.thumbnail {
  width: 100%;
  aspect-ratio: 16/9;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  position: relative;
}

.duration {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 2px 6px;
  font-size: 12px;
  border-radius: 4px;
}

.info { padding-top: 12px; }

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

.author, .stats {
  font-size: 13px;
  color: #999;
  margin: 0 0 4px 0;
}
</style>
