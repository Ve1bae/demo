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
            <div class="avatar">
              <img v-if="currentUserAvatar" :src="currentUserAvatar" alt="" class="avatar-image" />
              <span v-else>{{ avatarText }}</span>
            </div>
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
          <li :class="{ active: currentPage === 'profile' }" @click="openProtectedPage('profile')">个人主页</li>
          <li :class="{ active: currentPage === 'following' }" @click="openProtectedPage('following')">我的关注</li>
          <li :class="{ active: currentPage === 'history' }" @click="openProtectedPage('history')">历史记录</li>
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
          <section class="page-panel creator-page">
            <div class="page-header">
              <div>
                <h2>创作者中心</h2>
                <p>查看粉丝、热度和稿件表现，也可以继续发布新作品。</p>
              </div>
              <button class="refresh-btn" :disabled="creatorRefreshing" @click="refreshCreatorCenter">
                {{ creatorRefreshing ? '刷新中...' : '刷新数据' }}
              </button>
            </div>

            <div class="creator-dashboard">
              <section class="creator-overview-card">
                <div class="creator-overview-profile">
                  <div class="creator-avatar">
                    <img v-if="profileData.avatarUrl" :src="profileData.avatarUrl" alt="" />
                    <span v-else>{{ profileAvatarText }}</span>
                  </div>
                  <div>
                    <h3>{{ profileDisplayName }}</h3>
                    <p>@{{ profileData.username || currentUser || 'creator' }}</p>
                  </div>
                </div>

                <div class="creator-stat-grid">
                  <div class="creator-stat-card">
                    <span>粉丝数</span>
                    <strong>{{ formatCompactNumber(profileData.followerCount || 0) }}</strong>
                    <small>关注你的人</small>
                  </div>
                  <div class="creator-stat-card">
                    <span>稿件数</span>
                    <strong>{{ creatorVideos.length }}</strong>
                    <small>已发布作品</small>
                  </div>
                  <div class="creator-stat-card">
                    <span>总播放</span>
                    <strong>{{ formatCompactNumber(creatorStats.playCount) }}</strong>
                    <small>作品累计播放</small>
                  </div>
                  <div class="creator-stat-card hot">
                    <span>综合热度</span>
                    <strong>{{ formatCompactNumber(creatorStats.hotScore) }}</strong>
                    <small>播放 + 互动加权</small>
                  </div>
                </div>
              </section>

              <section class="creator-manage-card">
                <div class="creator-section-head">
                  <div>
                    <h3>稿件管理</h3>
                    <p>管理已发布的视频，查看播放、点赞、收藏和评论表现。</p>
                  </div>
                  <span>{{ creatorVideos.length }} 个稿件</span>
                </div>

                <div v-if="creatorVideos.length === 0" class="creator-empty">
                  还没有发布稿件，先从右侧投稿开始吧。
                </div>
                <div v-else class="creator-manuscript-list">
                  <div v-for="video in creatorVideos" :key="`creator-video-${video.id}`" class="creator-manuscript-item">
                    <div class="creator-manuscript-cover" @click="openVideoPlayer(video)">
                      <img v-if="video.coverUrl" :src="video.coverUrl" alt="" />
                      <div v-else class="creator-cover-fallback">{{ video.title?.slice(0, 1) || 'V' }}</div>
                      <span>{{ video.duration || '00:00' }}</span>
                    </div>
                    <div class="creator-manuscript-main">
                      <h4 @click="openVideoPlayer(video)">{{ video.title }}</h4>
                      <p>{{ video.description || '暂无简介' }}</p>
                      <div class="creator-manuscript-tags">
                        <span v-for="tag in normalizeTagNames(video.tags)" :key="`${video.id}-${tag}`"># {{ tag }}</span>
                      </div>
                      <div class="creator-manuscript-metrics">
                        <span>{{ formatCompactNumber(video.playCount || parseViews(video.views)) }} 播放</span>
                        <span>{{ formatCompactNumber(video.likeCount || 0) }} 点赞</span>
                        <span>{{ formatCompactNumber(video.favoriteCount || 0) }} 收藏</span>
                        <span>{{ formatCompactNumber(video.commentCount || 0) }} 评论</span>
                        <span>热度 {{ formatCompactNumber(calculateHotScore(video)) }}</span>
                      </div>
                    </div>
                    <div class="creator-manuscript-actions">
                      <button class="outline-btn" @click="openVideoPlayer(video)">查看</button>
                      <button
                        class="danger-btn"
                        :disabled="deletingVideoId === video.id"
                        @click="deleteCreatorVideo(video)"
                      >
                        {{ deletingVideoId === video.id ? '删除中...' : '删除稿件' }}
                      </button>
                    </div>
                  </div>
                </div>
              </section>

              <section class="creator-upload-card">
                <div class="creator-section-head compact">
                  <div>
                    <h3>发布新稿件</h3>
                    <p>上传本地视频，支持自动截取封面。</p>
                  </div>
                </div>

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
                  <input v-model.trim="uploadForm.coverUrl" type="text" placeholder="可选，留空则自动生成封面" />
                </div>
                <div class="creator-row">
                  <label>自定义标签</label>
                  <div class="tag-editor">
                    <input
                      v-model.trim="uploadTagInput"
                      type="text"
                      list="upload-tag-suggestions"
                      placeholder="输入标签后回车，可用逗号分隔多个标签"
                      @keydown.enter.prevent="commitUploadTagInput"
                      @blur="commitUploadTagInput"
                    />
                    <datalist id="upload-tag-suggestions">
                      <option v-for="tag in availableTags" :key="`tag-${tag.id || tag.name}`" :value="tag.name" />
                    </datalist>
                    <div v-if="uploadTags.length > 0" class="tag-chip-list">
                      <button
                        v-for="tag in uploadTags"
                        :key="`upload-tag-${tag}`"
                        type="button"
                        class="tag-chip"
                        @click="removeUploadTag(tag)"
                      >
                        <span># {{ tag }}</span>
                        <span>x</span>
                      </button>
                    </div>
                  </div>
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
              </section>
            </div>
          </section>
        </template>

        <template v-else-if="currentPage === 'creator-legacy'">
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
                <input v-model.trim="uploadForm.coverUrl" type="text" placeholder="可选，留空则自动生成封面" />
              </div>
              <div class="creator-row">
                <label>自定义标签</label>
                <div class="tag-editor">
                  <input
                    v-model.trim="uploadTagInput"
                    type="text"
                    list="upload-tag-suggestions"
                    placeholder="输入标签后回车，可用逗号分隔多个标签"
                    @keydown.enter.prevent="commitUploadTagInput"
                    @blur="commitUploadTagInput"
                  />
                  <datalist id="upload-tag-suggestions">
                    <option v-for="tag in availableTags" :key="`tag-${tag.id || tag.name}`" :value="tag.name" />
                  </datalist>
                  <div v-if="uploadTags.length > 0" class="tag-chip-list">
                    <button
                      v-for="tag in uploadTags"
                      :key="`upload-tag-${tag}`"
                      type="button"
                      class="tag-chip"
                      @click="removeUploadTag(tag)"
                    >
                      <span># {{ tag }}</span>
                      <span>×</span>
                    </button>
                  </div>
                </div>
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

        <template v-else-if="currentPage === 'profile'">
          <section class="page-panel">
            <div class="page-header">
              <div>
                <h2>{{ profilePageTitle }}</h2>
                <p>{{ profilePageSubtitle }}</p>
              </div>
            </div>

            <div v-if="profileLoading" class="empty-state">正在加载个人主页...</div>

            <div v-else class="profile-shell">
              <div class="profile-hero-card">
                <div class="profile-hero-banner">
                  <div v-if="!isOwnProfileView" class="profile-hero-actions">
                    <button class="profile-follow-btn" @click="toggleFollowUser(profileData)">
                      {{ profileData.following ? '已关注' : '+ 关注' }}
                    </button>
                  </div>
                </div>
                <div class="profile-hero-content">
                  <button
                    v-if="isOwnProfileView"
                    type="button"
                    class="profile-avatar-button"
                    :disabled="avatarUploading"
                    @click="openAvatarUploader"
                  >
                    <div class="profile-avatar-large">
                      <img v-if="profileData.avatarUrl" :src="profileData.avatarUrl" alt="" />
                      <span v-else>{{ profileAvatarText }}</span>
                    </div>
                    <span class="profile-avatar-tip">{{ avatarUploading ? '上传中...' : '点击上传本地头像' }}</span>
                  </button>
                  <div v-else class="profile-avatar-static">
                    <div class="profile-avatar-large">
                      <img v-if="profileData.avatarUrl" :src="profileData.avatarUrl" alt="" />
                      <span v-else>{{ profileAvatarText }}</span>
                    </div>
                  </div>

                  <input
                    ref="avatarInputRef"
                    class="hidden-file-input"
                    type="file"
                    accept="image/png,image/jpeg,image/jpg,image/webp,image/gif"
                    @change="handleAvatarFileChange"
                  />

                  <div class="profile-hero-main">
                    <h3>{{ profileDisplayName }}</h3>
                    <p class="profile-account">@{{ profileData.username || 'user' }}</p>
                    <p class="profile-bio-text">{{ profileData.bio || '这个人很神秘，还没有留下简介。' }}</p>
                    <div class="profile-stats-row">
                      <span>{{ profileData.followingCount || 0 }} 关注</span>
                      <span>{{ profileData.followerCount || 0 }} 粉丝</span>
                      <span>{{ profileCreatedVideos.length }} 投稿</span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="profile-space-grid">
                <div class="profile-works-card">
                  <div class="profile-section-head">
                    <div>
                      <h3>创作内容</h3>
                      <p>{{ isOwnProfileView ? '我的投稿和作品会展示在这里。' : 'TA 发布过的视频内容。' }}</p>
                    </div>
                    <span>{{ profileCreatedVideos.length }} 个视频</span>
                  </div>

                  <div v-if="profileCreatedVideos.length === 0" class="profile-empty-works">
                    暂时还没有发布视频
                  </div>
                  <div v-else class="profile-video-grid">
                    <div
                      v-for="video in profileCreatedVideos"
                      :key="`profile-video-${video.id}`"
                      class="profile-video-card"
                      @click="openVideoPlayer(video)"
                    >
                      <div class="profile-video-cover">
                        <img v-if="video.coverUrl" :src="video.coverUrl" alt="" />
                        <div v-else class="profile-video-cover-fallback">{{ video.title?.slice(0, 1) || 'V' }}</div>
                        <span class="duration">{{ video.duration }}</span>
                      </div>
                      <div class="profile-video-title">{{ video.title }}</div>
                      <div class="profile-video-meta">{{ video.views }} 播放 · {{ video.date }}</div>
                    </div>
                  </div>
                </div>

                <aside class="profile-right-rail">
                  <div v-if="isOwnProfileView" class="profile-detail-card">
                    <div class="profile-card-title">编辑资料</div>
                    <div class="profile-detail-row">
                      <div class="profile-detail-label">昵称</div>
                      <div class="profile-detail-body">
                        <input v-model.trim="profileForm.nickname" type="text" placeholder="请输入昵称" />
                      </div>
                    </div>

                    <div class="profile-detail-row">
                      <div class="profile-detail-label">简介</div>
                      <div class="profile-detail-body">
                        <textarea v-model.trim="profileForm.bio" rows="4" placeholder="介绍一下自己吧"></textarea>
                      </div>
                    </div>

                    <div class="inline-editor-actions">
                      <button class="confirm-btn small-btn" :disabled="profileSaving" @click="submitProfileEdit">
                        {{ profileSaving ? '保存中...' : '保存资料' }}
                      </button>
                    </div>
                  </div>
                  <div v-else class="profile-detail-card">
                    <div class="profile-card-title">个人资料</div>
                    <div class="profile-detail-row compact">
                      <div class="profile-detail-label">昵称</div>
                      <div class="profile-detail-body profile-static-text">{{ profileData.nickname || '-' }}</div>
                    </div>
                    <div class="profile-detail-row compact">
                      <div class="profile-detail-label">简介</div>
                      <div class="profile-detail-body profile-static-text">{{ profileData.bio || '这个用户还没有简介。' }}</div>
                    </div>
                  </div>

                  <div class="profile-side-card">
                    <div class="profile-card-title">空间概览</div>
                    <div class="profile-metric-row">
                      <span>关注</span>
                      <strong>{{ profileData.followingCount || 0 }}</strong>
                    </div>
                    <div class="profile-metric-row">
                      <span>粉丝</span>
                      <strong>{{ profileData.followerCount || 0 }}</strong>
                    </div>
                    <div class="profile-metric-row">
                      <span>投稿</span>
                      <strong>{{ profileCreatedVideos.length }}</strong>
                    </div>
                    <div class="profile-metric-row">
                      <span>身份</span>
                      <strong>{{ isOwnProfileView ? '我自己' : '创作者' }}</strong>
                    </div>
                  </div>
                </aside>
              </div>
            </div>
          </section>
        </template>

        <template v-else-if="currentPage === 'following'">
          <section class="page-panel">
            <div class="page-header">
              <div>
                <h2>我的关注</h2>
                <p>查看关注和粉丝列表。</p>
              </div>
            </div>

            <div v-if="socialLoading" class="empty-state">正在加载关注关系...</div>

            <div v-else class="social-columns">
              <div class="social-card">
                <div class="social-card-header">
                  <h3>关注</h3>
                  <span>{{ followingList.length }}</span>
                </div>
                <div v-if="followingList.length === 0" class="empty-inline">还没有关注任何人</div>
                <div v-else class="social-list">
                  <div v-for="user in followingList" :key="`following-${user.id}`" class="social-item" @click="openUserProfile(user)">
                    <div class="social-avatar clickable-avatar">
                      <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                      <span v-else>{{ getUserAvatarFallback(user) }}</span>
                    </div>
                    <div class="social-info clickable-user">
                      <strong>{{ user.nickname || user.username }}</strong>
                      <span>@{{ user.username }}</span>
                      <p>{{ user.bio || '这个用户还没有简介。' }}</p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="social-card">
                <div class="social-card-header">
                  <h3>粉丝</h3>
                  <span>{{ followerList.length }}</span>
                </div>
                <div v-if="followerList.length === 0" class="empty-inline">暂时还没有粉丝</div>
                <div v-else class="social-list">
                  <div v-for="user in followerList" :key="`follower-${user.id}`" class="social-item" @click="openUserProfile(user)">
                    <div class="social-avatar clickable-avatar">
                      <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                      <span v-else>{{ getUserAvatarFallback(user) }}</span>
                    </div>
                    <div class="social-info clickable-user">
                      <strong>{{ user.nickname || user.username }}</strong>
                      <span>@{{ user.username }}</span>
                      <p>{{ user.bio || '这个用户还没有简介。' }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else-if="currentPage === 'history'">
          <section class="page-panel">
            <div class="page-header">
              <div>
                <h2>历史记录</h2>
                <p>这里会展示最近观看的视频、上次观看时间和上次观看位置。</p>
              </div>
            </div>

            <div v-if="historyLoading" class="empty-state">正在加载历史记录...</div>
            <div v-else-if="historyVideos.length === 0" class="empty-state">还没有历史记录</div>

            <div v-else class="video-grid">
              <div class="video-card" v-for="video in historyVideos" :key="`history-${video.id}`" @click="openVideoPlayer(video)">
                <div class="thumbnail">
                  <img v-if="video.coverUrl" class="cover-image" :src="video.coverUrl" alt="" />
                  <span class="duration">{{ video.duration }}</span>
                </div>
                <div class="info">
                  <h3 class="title">{{ video.title }}</h3>
                  <p class="author">{{ video.author }}</p>
                  <p class="stats">{{ video.views }} 观看 · {{ video.date }}</p>
                  <p class="stats">上次观看：{{ formatHistoryDate(video.historyLastViewedAt) }}</p>
                  <p class="stats">上次看到：{{ formatDuration(video.historyProgressSeconds) }}</p>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="page-panel">
            <div class="page-header">
              <div>
                <h2>{{ currentPage === 'ranking' ? '热门排行' : '首页推荐' }}</h2>
                <p>{{ currentPage === 'ranking' ? '综合播放、点赞、收藏和评论热度排序。' : '根据热度、标签和你的互动行为推荐。' }}</p>
              </div>
            </div>

            <div v-if="displayedVideos.length === 0" class="empty-state">暂时没有找到相关视频</div>
            <div v-else class="video-grid">
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

    <VideoPlayer
      v-if="showVideoPlayer"
      :key="selectedVideo?.id || 'video-player'"
      :videoData="selectedVideo"
      @back="closeVideoPlayer"
      @open-video="openVideoPlayer"
      @open-profile="openUserProfile"
      @toggle-follow="toggleFollowUser"
    />

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
const creatorRefreshing = ref(false)
const deletingVideoId = ref(null)
const liveLoading = ref(false)
const liveRooms = ref([])
const selectedLiveRoom = ref(null)
const liveVideoRef = ref(null)
const flvPlayer = ref(null)
const showLiveModal = ref(false)
const creatingLive = ref(false)
const createdRoom = ref(null)
const liveCategoryFilter = ref(0)
const profileLoading = ref(false)
const profileSaving = ref(false)
const avatarUploading = ref(false)
const socialLoading = ref(false)
const historyLoading = ref(false)
const avatarInputRef = ref(null)
const viewedProfileUserId = ref(null)
const profileData = ref({
  id: null,
  username: '',
  nickname: '',
  avatarUrl: '',
  bio: '',
  followingCount: 0,
  followerCount: 0,
  following: false
})
const profileForm = reactive({
  nickname: '',
  avatarUrl: '',
  bio: ''
})
const followingList = ref([])
const followerList = ref([])
const historyList = ref([])
const availableTags = ref([])
const uploadTags = ref([])
const uploadTagInput = ref('')

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
  },
  {
    id: 2,
    title: '校园乐队排练室：夏夜合奏片段',
    author: '航音乐队社',
    views: '8600',
    duration: '08:42',
    date: '2天前',
    videoUrl: 'video-band-room-summer',
    coverUrl: '',
    description: '吉他、键盘和鼓组的排练记录。',
    tags: [{ name: '音乐' }, { name: '校园' }, { name: '乐队' }],
    playCount: 8600,
    likeCount: 642,
    favoriteCount: 188,
    sources: {
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 3,
    title: '一分钟看完社团招新现场',
    author: '航音新闻部',
    views: '2.4万',
    duration: '01:12',
    date: '3天前',
    videoUrl: 'video-club-recruit',
    description: '把热闹的招新现场压缩成一分钟。',
    tags: [{ name: '校园' }, { name: '社团' }, { name: '短视频' }],
    playCount: 24000,
    likeCount: 1220,
    favoriteCount: 390,
    sources: {
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 4,
    title: '公开课回放：如何做一支完整的视频',
    author: '影像工作坊',
    views: '1.1万',
    duration: '26:30',
    date: '4天前',
    videoUrl: 'video-workshop-editing',
    description: '从选题、拍摄到剪辑的一次完整拆解。',
    tags: [{ name: '学习' }, { name: '剪辑' }, { name: '课程' }],
    playCount: 11000,
    likeCount: 730,
    favoriteCount: 520,
    sources: {
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 5,
    title: '晚风操场 Vlog：下课后的十分钟',
    author: '普通同学小林',
    views: '7300',
    duration: '03:45',
    date: '5天前',
    videoUrl: 'video-playground-vlog',
    description: '操场、晚风和朋友们的随手记录。',
    tags: [{ name: 'vlog' }, { name: '日常' }, { name: '校园' }],
    playCount: 7300,
    likeCount: 410,
    favoriteCount: 96,
    sources: {
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 6,
    title: '新生歌会幕后：候场区发生了什么',
    author: '校学生会',
    views: '1.8万',
    duration: '12:08',
    date: '1周前',
    videoUrl: 'video-freshman-concert-backstage',
    description: '从彩排到登台前的幕后花絮。',
    tags: [{ name: '音乐' }, { name: '活动' }, { name: '校园' }],
    playCount: 18000,
    likeCount: 980,
    favoriteCount: 310,
    sources: {
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 7,
    title: '游戏社周赛精彩集锦',
    author: '航音游戏社',
    views: '9500',
    duration: '06:18',
    date: '1周前',
    videoUrl: 'video-game-club-highlights',
    description: '本周社内赛的关键操作合集。',
    tags: [{ name: '游戏' }, { name: '比赛' }, { name: '集锦' }],
    playCount: 9500,
    likeCount: 690,
    favoriteCount: 210,
    sources: {
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  },
  {
    id: 8,
    title: '图书馆自习陪伴：雨天白噪音',
    author: '学习分享站',
    views: '3.2万',
    duration: '45:00',
    date: '2周前',
    videoUrl: 'video-library-rain-study',
    description: '适合自习时播放的图书馆环境声。',
    tags: [{ name: '学习' }, { name: '白噪音' }, { name: '日常' }],
    playCount: 32000,
    likeCount: 1500,
    favoriteCount: 860,
    sources: {
      '720P': 'https://www.w3schools.com/html/mov_bbb.mp4'
    },
    defaultQuality: '720P'
  }
]

const videoList = ref([])

const currentUserAvatar = computed(() => localStorage.getItem('loginUserAvatar') || profileData.value.avatarUrl || '')
const avatarText = computed(() => currentUser.value?.slice(0, 1) || '用')
const profileAvatarText = computed(() => {
  const text = profileData.value.nickname || profileData.value.username || currentUser.value || '用'
  return text.slice(0, 1)
})
const profileDisplayName = computed(() => {
  return profileData.value.nickname || profileData.value.username || currentUser.value || '未登录用户'
})
const isOwnProfileView = computed(() => {
  if (!profileData.value?.id || !currentUserId.value) {
    return true
  }
  return Number(profileData.value.id) === Number(currentUserId.value)
})
const profilePageTitle = computed(() => isOwnProfileView.value ? '个人主页' : `${profileDisplayName.value} 的主页`)
const profilePageSubtitle = computed(() => (
  isOwnProfileView.value
    ? '点击头像可直接从本地上传新头像。'
    : '查看 TA 的资料、关注状态和创作内容。'
))

const profileCreatedVideos = computed(() => {
  const profile = profileData.value || {}
  const profileId = profile.id == null ? '' : String(profile.id)
  const profileNames = [
    profile.nickname,
    profile.username,
    isOwnProfileView.value ? currentUser.value : ''
  ]
    .filter(Boolean)
    .map((name) => String(name).trim())

  return videoList.value.filter((video) => {
    const authorUserId = video?.authorInfo?.userId || video?.authorInfo?.id
    if (profileId && authorUserId != null && String(authorUserId) === profileId) {
      return true
    }
    return profileNames.some((name) => name && String(video.author || '').trim() === name)
  })
})

const creatorVideos = computed(() => {
  const currentId = currentUserId.value == null ? '' : String(currentUserId.value)
  const names = [currentUser.value, profileData.value.nickname, profileData.value.username]
    .filter(Boolean)
    .map((name) => String(name).trim())

  return videoList.value
    .filter((video) => {
      const authorUserId = video?.authorInfo?.userId || video?.authorInfo?.id
      if (currentId && authorUserId != null && String(authorUserId) === currentId) {
        return true
      }
      return names.some((name) => name && String(video.author || '').trim() === name)
    })
    .sort((a, b) => calculateHotScore(b) - calculateHotScore(a))
})

const creatorStats = computed(() => creatorVideos.value.reduce((stats, video) => ({
  playCount: stats.playCount + Number(video.playCount || parseViews(video.views) || 0),
  likeCount: stats.likeCount + Number(video.likeCount || 0),
  favoriteCount: stats.favoriteCount + Number(video.favoriteCount || 0),
  commentCount: stats.commentCount + Number(video.commentCount || 0),
  hotScore: stats.hotScore + calculateHotScore(video)
}), {
  playCount: 0,
  likeCount: 0,
  favoriteCount: 0,
  commentCount: 0,
  hotScore: 0
}))

const filteredVideos = computed(() => {
  const lowerKeyword = keyword.value.toLowerCase()
  if (!lowerKeyword) {
    return videoList.value
  }
  return videoList.value.filter((video) => {
    return videoMatchesKeyword(video, lowerKeyword)
  })
})

const displayedVideos = computed(() => {
  const list = filteredVideos.value
  if (currentPage.value === 'ranking') {
    return [...list].sort((a, b) => calculateHotScore(b) - calculateHotScore(a))
  }
  return list
})

const videoMatchesKeyword = (video, lowerKeyword) => {
  const tagText = Array.isArray(video.tags)
    ? video.tags.map((tag) => tag.name || tag).join(' ')
    : ''
  return [
    video.title,
    video.author,
    video.description,
    tagText
  ].some((value) => String(value || '').toLowerCase().includes(lowerKeyword))
}

const normalizeTagNames = (tags) => {
  if (!Array.isArray(tags)) {
    return []
  }
  return tags
    .map((tag) => String(tag?.name || tag || '').trim())
    .filter(Boolean)
}

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

const historyVideos = computed(() => {
  return historyList.value
    .map((history) => {
      const video = history?.video ? convertVideoFromBackend(history.video) : null
      if (!video) {
        return null
      }
      return {
        ...video,
        historyLastViewedAt: history.lastViewedAt,
        historyViewCount: history.viewCount || 0,
        historyProgressSeconds: history.progressSeconds || 0
      }
    })
    .filter(Boolean)
})

const durationMetadataCache = reactive({})

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

const getAuthHeaders = () => {
  if (!currentUserId.value) {
    return {}
  }
  return {
    'X-User-Id': currentUserId.value
  }
}

onMounted(async () => {
  restoreLoginState()
  window.addEventListener('hashchange', syncRouteFromHash)
  await loadVideoList()
  const openedSharedVideo = await openSharedVideoFromUrl()
  if (!openedSharedVideo) {
    await syncRouteFromHash()
  }
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
    viewedProfileUserId.value = Number(savedUserId)
  }
}

const goHome = () => {
  setPage('home')
}

const setRouteHash = (page, routeId = null) => {
  const nextHash = page === 'live-room' && routeId
    ? `#/live/${routeId}`
    : page === 'video' && routeId
      ? `#/video/${routeId}`
      : `#/${page}`
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
  if (page === 'video' && roomId) {
    return { page: 'video', videoId: roomId }
  }

  if (['home', 'ranking', 'live', 'creator', 'profile', 'following', 'history'].includes(page)) {
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
  if (route.page === 'video') {
    await openVideoById(route.videoId, false)
    return
  }
  await setPage(route.page, false)
}

const setPage = async (page, updateRoute = true) => {
  if (['profile', 'following', 'history'].includes(page) && !ensureLoggedIn(`请先登录后查看${page === 'profile' ? '个人主页' : page === 'following' ? '关注列表' : '历史记录'}`)) {
    currentPage.value = 'home'
    if (updateRoute) {
      setRouteHash('home')
    }
    return
  }

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
    return
  }
  if (page === 'creator') {
    await refreshCreatorCenter()
    return
  }
  if (page === 'profile') {
    await fetchCurrentProfile()
    return
  }
  if (page === 'following') {
    await fetchSocialData()
    return
  }
  if (page === 'history') {
    await fetchHistoryData()
  }
}

const ensureLoggedIn = (message = '请先登录') => {
  if (isLoggedIn.value && currentUserId.value) {
    return true
  }
  alert(message)
  openLoginModal()
  return false
}

const openProtectedPage = async (page) => {
  if (!ensureLoggedIn(`请先登录后查看${page === 'profile' ? '个人主页' : page === 'following' ? '关注列表' : '历史记录'}`)) {
    return
  }
  if (page === 'profile') {
    viewedProfileUserId.value = currentUserId.value
  }
  await setPage(page)
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

const syncProfileForm = (profile) => {
  profileForm.nickname = profile?.nickname || ''
  profileForm.avatarUrl = profile?.avatarUrl || ''
  profileForm.bio = profile?.bio || ''
}

const syncLoginUserCache = (profile) => {
  const nickname = profile?.nickname || profile?.username || currentUser.value || ''
  const userId = profile?.id || currentUserId.value
  const avatarUrl = profile?.avatarUrl || ''

  if (nickname) {
    currentUser.value = nickname
    localStorage.setItem('loginUser', nickname)
    localStorage.setItem('loginUserNickname', nickname)
  }
  if (userId != null) {
    localStorage.setItem('loginUserId', String(userId))
  }
  localStorage.setItem('loginUserAvatar', avatarUrl)
}

const syncSelectedVideoAuthorProfile = (profile) => {
  if (!selectedVideo.value?.authorInfo || !profile?.id) {
    return
  }
  const authorUserId = selectedVideo.value.authorInfo.userId || selectedVideo.value.authorInfo.id
  if (String(authorUserId) !== String(profile.id)) {
    return
  }

  selectedVideo.value = {
    ...selectedVideo.value,
    author: profile.nickname || profile.username || selectedVideo.value.author,
    authorInfo: {
      ...selectedVideo.value.authorInfo,
      id: profile.id,
      userId: profile.id,
      nickname: profile.nickname || selectedVideo.value.authorInfo.nickname,
      username: profile.username || selectedVideo.value.authorInfo.username,
      avatarUrl: profile.avatarUrl || '',
      bio: profile.bio || selectedVideo.value.authorInfo.bio,
      following: profile.following,
      followingCount: profile.followingCount,
      followerCount: profile.followerCount
    }
  }
}

const normalizeAuthorInfo = (authorInfo, authorName = '') => {
  if (!authorInfo) {
    return null
  }
  const userId = authorInfo.userId || authorInfo.id || null
  return {
    ...authorInfo,
    id: userId,
    userId,
    nickname: authorInfo.nickname || authorName || '',
    username: authorInfo.username || '',
    avatarUrl: authorInfo.avatarUrl || '',
    bio: authorInfo.bio || '',
    following: Boolean(authorInfo.following)
  }
}

const mergeProfileIntoVideo = (video, profile) => {
  if (!video || !profile?.id) {
    return video
  }
  return {
    ...video,
    author: profile.nickname || profile.username || video.author,
    authorInfo: {
      ...(video.authorInfo || {}),
      id: profile.id,
      userId: profile.id,
      username: profile.username || video.authorInfo?.username || '',
      nickname: profile.nickname || video.authorInfo?.nickname || video.author,
      avatarUrl: profile.avatarUrl || '',
      bio: profile.bio || '',
      following: profile.following,
      followingCount: profile.followingCount,
      followerCount: profile.followerCount
    }
  }
}

const syncAuthorProfileAcrossLists = (profile) => {
  if (!profile?.id) {
    return
  }

  videoList.value = videoList.value.map((video) => {
    const authorUserId = video?.authorInfo?.userId || video?.authorInfo?.id
    return String(authorUserId) === String(profile.id) ? mergeProfileIntoVideo(video, profile) : video
  })

  historyList.value = historyList.value.map((history) => {
    if (!history?.video) {
      return history
    }
    const authorUserId = history.video.authorInfo?.userId || history.video.authorInfo?.id
    if (String(authorUserId) !== String(profile.id)) {
      return history
    }
    return {
      ...history,
      video: mergeProfileIntoVideo(history.video, profile)
    }
  })

  syncSelectedVideoAuthorProfile(profile)
}

const fetchProfileById = async (userId) => {
  if (!userId) {
    return null
  }
  const res = await axios.get(`${API_BASE}/user/profile/${userId}`, {
    headers: getAuthHeaders()
  })
  return res.data?.code === 200 ? res.data.data : null
}

const fetchProfileByName = async (name) => {
  if (!name) {
    return null
  }
  const res = await axios.get(`${API_BASE}/user/profile/by-name`, {
    params: { name },
    headers: getAuthHeaders()
  })
  return res.data?.code === 200 ? res.data.data : null
}

const ensureVideoAuthorProfile = async (video) => {
  if (!video) {
    return null
  }
  try {
    let profile = null
    const authorUserId = video.authorInfo?.userId || video.authorInfo?.id
    if (authorUserId) {
      profile = await fetchProfileById(authorUserId)
    } else if (video.author) {
      profile = await fetchProfileByName(video.author)
    }
    if (!profile) {
      return video
    }
    return mergeProfileIntoVideo(video, profile)
  } catch (error) {
    console.warn('加载作者资料失败', error)
    return video
  }
}

const openUserProfile = async (targetUser) => {
  let profile = null
  try {
    const targetUserId = targetUser?.userId || targetUser?.id
    if (targetUserId) {
      profile = await fetchProfileById(targetUserId)
    } else if (targetUser?.nickname || targetUser?.username || targetUser?.name) {
      profile = await fetchProfileByName(targetUser.nickname || targetUser.username || targetUser.name)
    }
  } catch (error) {
    alert(error.response?.data?.message || '加载用户主页失败')
    return
  }

  if (!profile?.id) {
    alert('未找到对应用户')
    return
  }

  viewedProfileUserId.value = profile.id
  showVideoPlayer.value = false
  profileData.value = {
    ...profileData.value,
    ...profile
  }
  if (Number(profile.id) === Number(currentUserId.value)) {
    syncProfileForm(profileData.value)
    syncLoginUserCache(profileData.value)
  }
  await setPage('profile')
}

const toggleFollowUser = async (targetUser) => {
  if (!ensureLoggedIn('请先登录后再关注用户')) {
    return
  }
  const targetUserId = targetUser?.userId || targetUser?.id
  if (!targetUserId) {
    alert('未找到可关注的用户')
    return
  }
  if (Number(targetUserId) === Number(currentUserId.value)) {
    alert('不能关注自己')
    return
  }

  const followed = Boolean(targetUser?.following)
  try {
    const url = `${API_BASE}/user/${targetUserId}/follow`
    const method = followed ? 'delete' : 'post'
    const res = await axios({
      url,
      method,
      headers: getAuthHeaders()
    })
    if (res.data?.code && res.data.code !== 200) {
      alert(res.data.message || '关注操作失败')
      return
    }

    const latestProfile = await fetchProfileById(targetUserId)
    if (latestProfile?.id) {
      if (Number(profileData.value.id) === Number(targetUserId)) {
        profileData.value = {
          ...profileData.value,
          ...latestProfile
        }
      }
      if (selectedVideo.value?.authorInfo && Number(selectedVideo.value.authorInfo.userId || selectedVideo.value.authorInfo.id) === Number(targetUserId)) {
        selectedVideo.value = mergeProfileIntoVideo(selectedVideo.value, latestProfile)
      }
      followingList.value = followingList.value.map((user) => (
        Number(user.id) === Number(targetUserId) ? { ...user, ...latestProfile } : user
      ))
      followerList.value = followerList.value.map((user) => (
        Number(user.id) === Number(targetUserId) ? { ...user, ...latestProfile } : user
      ))
    }

    if (currentPage.value === 'following') {
      await fetchSocialData()
    }
  } catch (error) {
    alert(error.response?.data?.message || '关注操作失败')
  }
}

const recordViewHistory = async (video) => {
  if (!isLoggedIn.value || !currentUserId.value || !video?.id) {
    return
  }
  try {
    await axios.post(`${API_BASE}/videos/${video.id}/history`, null, {
      headers: {
        'X-User-Id': currentUserId.value
      }
    })
  } catch (error) {
    console.warn('记录历史失败', error)
  }
}

const openVideoPlayer = async (video, updateRoute = true) => {
  selectedVideo.value = await ensureVideoAuthorProfile(video)
  showVideoPlayer.value = true
  if (updateRoute && selectedVideo.value?.id) {
    setRouteHash('video', selectedVideo.value.id)
  }
  window.scrollTo(0, 0)
  await recordViewHistory(selectedVideo.value)
  loadRelatedVideos(selectedVideo.value.id)
}

const openVideoById = async (videoId, updateRoute = true) => {
  if (!videoId) {
    await setPage('home')
    return false
  }
  if (showVideoPlayer.value && selectedVideo.value && String(selectedVideo.value.id) === String(videoId)) {
    return true
  }

  let video = videoList.value.find((item) => String(item.id) === String(videoId))
  if (!video) {
    try {
      const res = await axios.get(`${API_BASE}/videos/${videoId}`)
      if (res.data?.code === 200 && res.data.data) {
        video = convertVideoFromBackend(res.data.data)
      }
    } catch (error) {
      console.warn('打开视频失败', error)
    }
  }

  if (!video) {
    alert('视频不存在或已被删除')
    showVideoPlayer.value = false
    selectedVideo.value = null
    await setPage('home')
    return false
  }

  await openVideoPlayer(video, updateRoute)
  return true
}

const openSharedVideoFromUrl = async () => {
  const params = new URLSearchParams(window.location.search)
  const sharedVideoId = params.get('videoId')
  if (!sharedVideoId) {
    return false
  }

  let video = videoList.value.find((item) => String(item.id) === String(sharedVideoId))
  if (!video) {
    try {
      const res = await axios.get(`${API_BASE}/videos/${sharedVideoId}`)
      if (res.data?.code === 200 && res.data.data) {
        video = convertVideoFromBackend(res.data.data)
      }
    } catch (error) {
      console.warn('打开分享视频失败', error)
    }
  }

  if (!video) {
    alert('分享的视频不存在或已被删除')
    return false
  }

  await openVideoPlayer(video, true)
  return true
}

const closeVideoPlayer = () => {
  showVideoPlayer.value = false
  selectedVideo.value = null
  const url = new URL(window.location.href)
  if (url.searchParams.has('videoId')) {
    url.searchParams.delete('videoId')
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash || '#/home'}`)
  }
  if (window.location.hash.startsWith('#/video/')) {
    setRouteHash(currentPage.value || 'home')
  }
}

const loadRelatedVideos = async (videoId) => {
  if (!videoId || !selectedVideo.value || String(selectedVideo.value.id) !== String(videoId)) {
    return
  }
  try {
    const res = await axios.get(`${API_BASE}/videos/${videoId}/related?limit=6`, {
      headers: getAuthHeaders()
    })
    if (res.data?.code === 200 && Array.isArray(res.data.data)) {
      selectedVideo.value = {
        ...selectedVideo.value,
        relatedVideos: res.data.data.map(convertVideoFromBackend)
      }
    }
  } catch (error) {
    console.warn('加载相关推荐失败', error)
  }
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
      nickname: data.user.nickname,
      avatarUrl: data.user.avatarUrl || '',
      bio: data.user.bio || ''
    }
  }
  if (data.data?.user?.userId) {
    return {
      id: data.data.user.userId,
      username: data.data.user.username,
      nickname: data.data.user.nickname,
      avatarUrl: data.data.user.avatarUrl || '',
      bio: data.data.user.bio || ''
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
      viewedProfileUserId.value = Number(loginUser.id)
      syncLoginUserCache(loginUser)
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
  viewedProfileUserId.value = null
  profileData.value = {
    id: null,
    username: '',
    nickname: '',
    avatarUrl: '',
    bio: '',
    followingCount: 0,
    followerCount: 0,
    following: false
  }
  followingList.value = []
  followerList.value = []
  historyList.value = []
  localStorage.removeItem('loginUser')
  localStorage.removeItem('loginUserNickname')
  localStorage.removeItem('loginUserId')
  localStorage.removeItem('loginUserAvatar')
  if (['profile', 'following', 'history'].includes(currentPage.value)) {
    setPage('home')
  }
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

  const videoId = video.videoId || video.id
  const rawDuration = video.duration
  const normalizedDuration = normalizeVideoDuration(rawDuration, videoId)

  return {
    id: videoId,
    videoId,
    title: video.title,
    author: video.author || '匿名用户',
    views: video.views || formatPlayCount(video.playCount || 0),
    duration: normalizedDuration,
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
    favorited: video.favorited || false,
    authorInfo: normalizeAuthorInfo(video.authorInfo, video.author || ''),
    tags: Array.isArray(video.tags) ? video.tags : [],
    relatedVideos: Array.isArray(video.relatedVideos) ? video.relatedVideos.map(convertVideoFromBackend) : []
  }
}

const normalizeVideoDuration = (value, videoId = null) => {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (/^\d{1,2}:\d{2}(:\d{2})?$/.test(trimmed)) {
      return trimmed.length === 4 ? `00:${trimmed}` : trimmed
    }
    const numericValue = Number(trimmed)
    if (!Number.isNaN(numericValue) && numericValue > 0) {
      return formatDuration(numericValue)
    }
  }

  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return formatDuration(value)
  }

  if (videoId != null && durationMetadataCache[String(videoId)]) {
    return durationMetadataCache[String(videoId)]
  }

  return '00:00'
}

const updateVideoDurationById = (videoId, durationText) => {
  if (!videoId || !durationText || durationText === '00:00') {
    return
  }
  durationMetadataCache[String(videoId)] = durationText

  videoList.value = videoList.value.map((video) => (
    String(video.id) === String(videoId)
      ? { ...video, duration: durationText }
      : video
  ))

  historyList.value = historyList.value.map((history) => {
    if (!history?.video || String(history.video.videoId || history.video.id) !== String(videoId)) {
      return history
    }
    return {
      ...history,
      video: {
        ...history.video,
        duration: durationText
      }
    }
  })
}

const probeVideoDuration = (video) => {
  const videoId = video?.id || video?.videoId
  if (!videoId || durationMetadataCache[String(videoId)] || video?.duration !== '00:00') {
    return
  }

  const sourceUrl = video?.sources?.['720P'] || Object.values(video?.sources || {})[0] || video?.playUrl
  if (!sourceUrl) {
    return
  }

  const preview = document.createElement('video')
  preview.preload = 'metadata'
  preview.src = sourceUrl
  preview.onloadedmetadata = () => {
    const seconds = Number(preview.duration || 0)
    if (seconds > 0) {
      updateVideoDurationById(videoId, formatDuration(seconds))
    }
    preview.removeAttribute('src')
    preview.load()
  }
  preview.onerror = () => {
    preview.removeAttribute('src')
    preview.load()
  }
}

const loadVideoList = async () => {
  try {
    const res = await axios.get(`${API_BASE}/videos/recommend`, {
      params: {
        page: 1,
        pageSize: 50,
        keyword: keyword.value || undefined
      },
      headers: getAuthHeaders()
    })
    if (res.data?.code === 200 && Array.isArray(res.data.data) && res.data.data.length > 0) {
      videoList.value = res.data.data
        .map(convertVideoFromBackend)
        .filter((video) => video.sources['720P'] || Object.values(video.sources).length > 0)
      videoList.value.forEach(probeVideoDuration)
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

const formatCompactNumber = (count) => {
  const safeCount = Number(count || 0)
  if (safeCount >= 10000) {
    return `${(safeCount / 10000).toFixed(1)}万`
  }
  return String(safeCount)
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

const calculateHotScore = (video) => {
  const play = Number(video.playCount || parseViews(video.views) || 0)
  const likes = Number(video.likeCount || 0)
  const favorites = Number(video.favoriteCount || 0)
  const comments = Number(video.commentCount || 0)
  return play + likes * 12 + favorites * 18 + comments * 8
}

const handleSearch = async () => {
  if (currentPage.value === 'live' || currentPage.value === 'live-room') {
    await fetchLiveRooms()
    return
  }
  await loadVideoList()
}

const handlePrimaryAction = async () => {
  if (!ensureLoggedIn('请先登录')) {
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

const refreshCreatorCenter = async () => {
  if (!ensureLoggedIn('请先登录后进入创作者中心')) {
    return
  }
  creatorRefreshing.value = true
  try {
    viewedProfileUserId.value = currentUserId.value
    await Promise.all([
      fetchAvailableTags(),
      fetchCurrentProfile(),
      loadVideoList()
    ])
  } finally {
    creatorRefreshing.value = false
  }
}

const openAvatarUploader = () => {
  avatarInputRef.value?.click()
}

const handleAvatarFileChange = async (event) => {
  const file = event.target.files && event.target.files[0]
  if (!file) {
    return
  }
  if (!ensureLoggedIn('请先登录后上传头像')) {
    event.target.value = ''
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  avatarUploading.value = true
  try {
    const res = await axios.post(`${API_BASE}/user/profile/avatar`, formData, {
      headers: {
        ...getAuthHeaders()
      }
    })
    if (res.data?.code === 200 && res.data.data) {
      profileData.value = {
        ...profileData.value,
        ...res.data.data
      }
      syncProfileForm(profileData.value)
      syncLoginUserCache(profileData.value)
      syncAuthorProfileAcrossLists(profileData.value)
      alert('头像已更新')
    } else {
      alert(res.data?.message || '头像上传失败')
    }
  } catch (error) {
    alert(error.response?.data?.message || '头像上传失败')
  } finally {
    avatarUploading.value = false
    event.target.value = ''
  }
}

const fetchCurrentProfile = async () => {
  const targetUserId = viewedProfileUserId.value || currentUserId.value
  if (!targetUserId) {
    if (!ensureLoggedIn('请先登录后查看个人主页')) {
      return
    }
  }
  if (!targetUserId) {
    return
  }

  profileLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/user/profile/${targetUserId}`, {
      headers: getAuthHeaders()
    })
    if (res.data?.code === 200 && res.data.data) {
      profileData.value = {
        ...profileData.value,
        ...res.data.data
      }
      if (Number(profileData.value.id) === Number(currentUserId.value)) {
        syncProfileForm(profileData.value)
        syncLoginUserCache(profileData.value)
        syncAuthorProfileAcrossLists(profileData.value)
      }
    } else {
      alert(res.data?.message || '加载个人主页失败')
    }
  } catch (error) {
    alert(error.response?.data?.message || '加载个人主页失败')
  } finally {
    profileLoading.value = false
  }
}

const submitProfileEdit = async () => {
  if (!ensureLoggedIn('请先登录后编辑资料')) {
    return
  }

  profileSaving.value = true
  try {
    const res = await axios.put(
      `${API_BASE}/user/profile`,
      {
        nickname: profileForm.nickname,
        avatarUrl: profileData.value.avatarUrl,
        bio: profileForm.bio
      },
      {
        headers: getAuthHeaders()
      }
    )

    if (res.data?.code === 200 && res.data.data) {
      profileData.value = {
        ...profileData.value,
        ...res.data.data
      }
      syncProfileForm(profileData.value)
      syncLoginUserCache(profileData.value)
      syncAuthorProfileAcrossLists(profileData.value)
      alert('个人资料已保存')
    } else {
      alert(res.data?.message || '保存个人资料失败')
    }
  } catch (error) {
    alert(error.response?.data?.message || '保存个人资料失败')
  } finally {
    profileSaving.value = false
  }
}

const fetchSocialData = async () => {
  if (!ensureLoggedIn('请先登录后查看关注列表')) {
    return
  }

  socialLoading.value = true
  try {
    const [followingRes, followerRes] = await Promise.all([
      axios.get(`${API_BASE}/user/${currentUserId.value}/following`),
      axios.get(`${API_BASE}/user/${currentUserId.value}/followers`)
    ])
    followingList.value = followingRes.data?.code === 200 && Array.isArray(followingRes.data.data)
      ? followingRes.data.data
      : []
    followerList.value = followerRes.data?.code === 200 && Array.isArray(followerRes.data.data)
      ? followerRes.data.data
      : []
  } catch (error) {
    followingList.value = []
    followerList.value = []
    alert(error.response?.data?.message || '加载关注关系失败')
  } finally {
    socialLoading.value = false
  }
}

const formatHistoryDate = (value) => {
  if (!value) {
    return '暂无记录'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

const formatDuration = (seconds) => {
  const totalSeconds = Math.max(0, Number(seconds || 0))
  const hrs = Math.floor(totalSeconds / 3600)
  const mins = Math.floor((totalSeconds % 3600) / 60)
  const secs = Math.floor(totalSeconds % 60)
  if (hrs > 0) {
    return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  }
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

const fetchHistoryData = async () => {
  if (!ensureLoggedIn('请先登录后查看历史记录')) {
    return
  }

  historyLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/user/${currentUserId.value}/history`)
    historyList.value = res.data?.code === 200 && Array.isArray(res.data.data) ? res.data.data : []
    historyVideos.value.forEach(probeVideoDuration)
  } catch (error) {
    historyList.value = []
    alert(error.response?.data?.message || '加载历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

const getUserAvatarFallback = (user) => {
  const text = user?.nickname || user?.username || '用'
  return text.slice(0, 1)
}

const fetchAvailableTags = async () => {
  try {
    const res = await axios.get(`${API_BASE}/user/tags`)
    availableTags.value = res.data?.code === 200 && Array.isArray(res.data.data) ? res.data.data : []
  } catch (error) {
    availableTags.value = []
  }
}

const appendUploadTag = (rawTag) => {
  const normalized = String(rawTag || '').trim().replace(/^#/, '')
  if (!normalized || normalized.length > 20) {
    return
  }
  if (!uploadTags.value.includes(normalized)) {
    uploadTags.value.push(normalized)
  }
  if (!availableTags.value.some((tag) => tag.name === normalized)) {
    availableTags.value.push({ name: normalized })
  }
}

const commitUploadTagInput = () => {
  if (!uploadTagInput.value) {
    return
  }
  uploadTagInput.value.split(/[,，]/).forEach(appendUploadTag)
  uploadTagInput.value = ''
}

const removeUploadTag = (tagName) => {
  uploadTags.value = uploadTags.value.filter((tag) => tag !== tagName)
}

const handleFileChange = (event) => {
  const files = event.target.files
  selectedUploadFile.value = files && files[0] ? files[0] : null
}

const extractVideoDuration = (file) => new Promise((resolve) => {
  if (!file) {
    resolve('')
    return
  }

  const objectUrl = URL.createObjectURL(file)
  const preview = document.createElement('video')
  preview.preload = 'metadata'

  const cleanup = () => {
    URL.revokeObjectURL(objectUrl)
    preview.removeAttribute('src')
    preview.load()
  }

  preview.onloadedmetadata = () => {
    const seconds = Number(preview.duration || 0)
    cleanup()
    resolve(seconds > 0 ? formatDuration(seconds) : '')
  }

  preview.onerror = () => {
    cleanup()
    resolve('')
  }

  preview.src = objectUrl
})

const extractVideoCoverFile = (file) => new Promise((resolve) => {
  if (!file) {
    resolve(null)
    return
  }

  const objectUrl = URL.createObjectURL(file)
  const preview = document.createElement('video')
  preview.preload = 'metadata'
  preview.muted = true
  preview.playsInline = true

  const cleanup = () => {
    URL.revokeObjectURL(objectUrl)
    preview.removeAttribute('src')
    preview.load()
  }

  const captureFrame = () => {
    const width = preview.videoWidth || 1280
    const height = preview.videoHeight || 720
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) {
      cleanup()
      resolve(null)
      return
    }
    context.drawImage(preview, 0, 0, width, height)
    canvas.toBlob((blob) => {
      cleanup()
      if (!blob) {
        resolve(null)
        return
      }
      resolve(new File([blob], `${file.name.replace(/\.[^.]+$/, '') || 'cover'}.png`, { type: 'image/png' }))
    }, 'image/png')
  }

  preview.onloadeddata = () => {
    if (preview.duration && preview.duration > 0.2) {
      preview.currentTime = 0.1
    } else {
      captureFrame()
    }
  }
  preview.onseeked = captureFrame
  preview.onerror = () => {
    cleanup()
    resolve(null)
  }

  preview.src = objectUrl
})

const submitUpload = async () => {
  if (!isLoggedIn.value) {
    alert('请先登录后再投稿')
    return
  }
  commitUploadTagInput()
  if (!uploadForm.title) {
    alert('请填写视频标题')
    return
  }
  if (!selectedUploadFile.value) {
    alert('请选择一个视频文件')
    return
  }

  const fileDuration = await extractVideoDuration(selectedUploadFile.value)
  const generatedCoverFile = uploadForm.coverUrl ? null : await extractVideoCoverFile(selectedUploadFile.value)
  const formData = new FormData()
  formData.append('title', uploadForm.title)
  formData.append('description', uploadForm.description)
  formData.append('coverUrl', uploadForm.coverUrl)
  formData.append('duration', fileDuration)
  formData.append('author', currentUser.value || '匿名用户')
  uploadTags.value.forEach((tag) => formData.append('tags', tag))
  if (generatedCoverFile) {
    formData.append('coverImage', generatedCoverFile)
  }
  formData.append('file', selectedUploadFile.value)

  uploadingVideo.value = true
  try {
    const res = await axios.post(`${API_BASE}/videos/upload`, formData, {
      headers: getAuthHeaders()
    })
    if (res.data?.code === 200 && res.data.data) {
      alert('上传成功，已加入视频列表')
      await fetchAvailableTags()
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
  uploadTagInput.value = ''
  uploadTags.value = []
  selectedUploadFile.value = null
}

const deleteCreatorVideo = async (video) => {
  if (!video?.id) {
    return
  }
  if (!ensureLoggedIn('请先登录后管理稿件')) {
    return
  }
  if (!confirm(`确定删除《${video.title || '该稿件'}》吗？删除后相关评论、点赞和历史记录也会被清理。`)) {
    return
  }

  deletingVideoId.value = video.id
  try {
    const res = await axios.delete(`${API_BASE}/videos/${video.id}`, {
      headers: getAuthHeaders()
    })
    if (res.data?.code === 200) {
      videoList.value = videoList.value.filter((item) => String(item.id) !== String(video.id))
      historyList.value = historyList.value.filter((history) => String(history?.video?.id || history?.videoId) !== String(video.id))
      if (selectedVideo.value && String(selectedVideo.value.id) === String(video.id)) {
        closeVideoPlayer()
      }
      alert('稿件已删除')
      return
    }
    alert(res.data?.message || '删除稿件失败')
  } catch (error) {
    alert(error.response?.data?.message || error.message || '删除稿件失败')
  } finally {
    deletingVideoId.value = null
  }
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
  flex-shrink: 0;
  overflow: hidden;
}

.avatar-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
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
.creator-overview-card,
.creator-manage-card,
.creator-upload-card,
.live-side-panel {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.creator-page {
  background:
    radial-gradient(circle at 12% 0%, rgba(0, 174, 236, 0.12), transparent 34%),
    radial-gradient(circle at 92% 8%, rgba(255, 102, 153, 0.1), transparent 30%),
    #f6f8fb;
}

.creator-dashboard {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 20px;
}

.creator-overview-card,
.creator-manage-card {
  grid-column: 1;
}

.creator-upload-card {
  grid-column: 2;
  grid-row: 1 / span 2;
  align-self: start;
  position: sticky;
  top: 20px;
}

.creator-overview-profile {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.creator-overview-profile h3 {
  margin: 0 0 4px;
  font-size: 24px;
  color: #111827;
}

.creator-overview-profile p {
  margin: 0;
  color: #6b7280;
}

.creator-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #dff6ff, #fff0f6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0b76b7;
  font-size: 24px;
  font-weight: 800;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
}

.creator-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.creator-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.creator-stat-card {
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff, #fff);
  border: 1px solid #eef2f7;
}

.creator-stat-card.hot {
  background: linear-gradient(135deg, rgba(0, 174, 236, 0.12), rgba(255, 102, 153, 0.12));
}

.creator-stat-card span,
.creator-stat-card small {
  display: block;
  color: #7a8494;
  font-size: 12px;
}

.creator-stat-card strong {
  display: block;
  margin: 8px 0 4px;
  color: #111827;
  font-size: 26px;
}

.creator-section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.creator-section-head.compact {
  align-items: flex-start;
}

.creator-section-head h3 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

.creator-section-head p {
  margin: 6px 0 0;
  color: #8a8f99;
  font-size: 13px;
}

.creator-section-head > span {
  color: #8a8f99;
  font-size: 13px;
  white-space: nowrap;
}

.creator-empty {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: #f8fafc;
  color: #94a3b8;
}

.creator-manuscript-list {
  display: grid;
  gap: 14px;
}

.creator-manuscript-item {
  display: grid;
  grid-template-columns: 176px minmax(0, 1fr) 104px;
  gap: 16px;
  padding: 14px;
  border: 1px solid #edf1f5;
  border-radius: 14px;
  background: #fff;
}

.creator-manuscript-cover {
  position: relative;
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  overflow: hidden;
  background: #e5e7eb;
  cursor: pointer;
}

.creator-manuscript-cover img,
.creator-cover-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.creator-cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0f2fe, #ffe4ee);
  color: #0b76b7;
  font-size: 26px;
  font-weight: 800;
}

.creator-manuscript-cover span {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  font-size: 12px;
}

.creator-manuscript-main {
  min-width: 0;
}

.creator-manuscript-main h4 {
  margin: 2px 0 6px;
  color: #111827;
  font-size: 16px;
  cursor: pointer;
}

.creator-manuscript-main p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.creator-manuscript-tags,
.creator-manuscript-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 10px;
}

.creator-manuscript-tags span {
  color: #0b76b7;
  font-size: 12px;
}

.creator-manuscript-metrics span {
  color: #7a8494;
  font-size: 12px;
}

.creator-manuscript-actions {
  display: grid;
  gap: 10px;
  align-content: center;
}

.outline-btn,
.danger-btn {
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  padding: 8px 10px;
  background: #fff;
  color: #334155;
  cursor: pointer;
}

.outline-btn:hover {
  border-color: #00aeec;
  color: #00aeec;
}

.danger-btn {
  border-color: #ffd6df;
  color: #d92d52;
}

.danger-btn:hover:not(:disabled) {
  background: #fff1f4;
}

.danger-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.profile-shell {
  display: grid;
  gap: 20px;
}

.profile-hero-card,
.profile-detail-card,
.profile-side-card,
.social-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
}

.profile-hero-card {
  padding: 0;
  overflow: hidden;
}

.profile-hero-banner {
  position: relative;
  height: 150px;
  background:
    radial-gradient(circle at 18% 18%, rgba(255, 255, 255, 0.42), transparent 26%),
    linear-gradient(135deg, rgba(0, 174, 236, 0.95), rgba(83, 153, 224, 0.84) 45%, rgba(255, 102, 153, 0.78)),
    repeating-linear-gradient(45deg, rgba(255,255,255,0.18) 0 1px, transparent 1px 16px);
}

.profile-hero-actions {
  position: absolute;
  right: 28px;
  bottom: 28px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.profile-follow-btn {
  min-width: 138px;
  padding: 12px 28px;
  border: none;
  border-radius: 10px;
  background: #00aeec;
  color: #fff;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 12px 28px rgba(0, 174, 236, 0.34);
  transition: transform 0.18s ease, background 0.18s ease;
}

.profile-follow-btn:hover {
  background: #0098d8;
  transform: translateY(-1px);
}

.profile-hero-content {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  padding: 0 28px 28px;
  margin-top: -28px;
  position: relative;
  z-index: 2;
  background: #fff;
}

.profile-avatar-button {
  display: grid;
  gap: 10px;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: center;
}

.profile-avatar-static {
  display: flex;
  align-items: center;
}

.hidden-file-input {
  display: none;
}

.profile-avatar-tip {
  color: #7a7a7a;
  font-size: 12px;
  min-height: 18px;
}

.profile-hero-main {
  min-width: 0;
  flex: 1;
  padding-top: 34px;
}

.profile-avatar-large,
.social-avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #e6f7ff, #fff0f6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  color: #1f4f8f;
  flex-shrink: 0;
  border: 4px solid #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.15);
  position: relative;
  z-index: 3;
}

.social-avatar {
  width: 52px;
  height: 52px;
  font-size: 18px;
  border: none;
  box-shadow: none;
}

.profile-avatar-large img,
.social-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-hero-main h3,
.social-card-header h3 {
  margin: 0;
}

.profile-hero-main h3 {
  font-size: 26px;
  line-height: 1.2;
}

.profile-account {
  margin: 6px 0 10px;
  font-size: 13px;
  color: #7a7a7a;
}

.profile-bio-text {
  margin: 0;
  line-height: 1.7;
  color: #444;
}

.profile-stats-row {
  display: flex;
  gap: 20px;
  margin-top: 14px;
  color: #555;
  font-size: 14px;
}

.profile-space-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
}

.profile-right-rail {
  display: grid;
  gap: 20px;
  align-self: start;
}

.profile-works-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px 28px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
  min-width: 0;
}

.profile-section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 22px;
}

.profile-section-head h3 {
  margin: 0;
  color: #111827;
  font-size: 24px;
}

.profile-section-head p {
  margin: 6px 0 0;
  color: #8a8f99;
  font-size: 13px;
}

.profile-section-head span {
  color: #8a8f99;
  font-size: 14px;
  white-space: nowrap;
}

.profile-video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 22px 18px;
}

.profile-video-card {
  cursor: pointer;
}

.profile-video-card:hover .profile-video-cover img,
.profile-video-card:hover .profile-video-cover-fallback {
  transform: scale(1.04);
}

.profile-video-cover {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  background: #e5e7eb;
}

.profile-video-cover img,
.profile-video-cover-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.22s ease;
}

.profile-video-cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dff6ff, #fff0f6);
  color: #2563eb;
  font-size: 30px;
  font-weight: 800;
}

.profile-video-title {
  margin-top: 10px;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.profile-video-meta {
  margin-top: 6px;
  color: #9ca3af;
  font-size: 12px;
}

.profile-empty-works {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  color: #94a3b8;
  font-size: 14px;
}

.profile-card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 18px;
}

.profile-side-card {
  align-self: start;
}

.profile-metric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  color: #64748b;
}

.profile-metric-row:last-child {
  border-bottom: none;
}

.profile-metric-row strong {
  color: #0f172a;
  font-size: 18px;
}

.profile-detail-card {
  display: grid;
  gap: 18px;
}

.profile-detail-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.profile-detail-row.compact {
  grid-template-columns: 64px minmax(0, 1fr);
}

.profile-detail-label {
  color: #7a7a7a;
  font-size: 14px;
  padding-top: 10px;
}

.profile-detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-static-text {
  color: #444;
  line-height: 1.8;
  padding-top: 8px;
}

.inline-editor-actions {
  display: flex;
  gap: 10px;
}

.small-btn {
  min-width: 96px;
  padding: 8px 14px;
  font-size: 13px;
}

.social-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.social-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.social-card-header span {
  color: #777;
  font-size: 14px;
}

.social-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.social-item {
  display: flex;
  gap: 14px;
  padding: 14px;
  border-radius: 10px;
  background: #fafafa;
  cursor: pointer;
}

.social-info {
  min-width: 0;
}

.social-info strong,
.social-info span,
.social-info p {
  display: block;
}

.social-info span {
  margin-top: 4px;
  font-size: 13px;
  color: #7a7a7a;
}

.social-info p {
  margin: 8px 0 0;
  font-size: 13px;
  color: #555;
  line-height: 1.6;
}

.clickable-avatar,
.clickable-user {
  cursor: pointer;
}

.empty-inline {
  padding: 24px 0;
  text-align: center;
  color: #888;
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

.tag-editor {
  display: grid;
  gap: 10px;
}

.tag-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  border-radius: 999px;
  padding: 8px 12px;
  background: #eef4ff;
  color: #1d4ed8;
  font-size: 13px;
  cursor: pointer;
}

.tag-chip:hover {
  background: #dbe7ff;
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

  .social-columns {
    grid-template-columns: 1fr;
  }

  .profile-space-grid {
    grid-template-columns: 1fr;
  }

  .profile-hero-content {
    align-items: flex-start;
    margin-top: -20px;
  }

  .profile-hero-actions {
    right: 18px;
    bottom: 18px;
  }

  .profile-detail-row {
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

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
