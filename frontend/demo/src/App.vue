<template>
  <div class="app-container">
    <header class="navbar">
      <div class="logo" @click="goHome">🚀航音视频</div>

      <div class="search-box">
        <input
          v-model.trim="keyword"
          type="text"
          :placeholder="currentPage === 'live' || currentPage === 'live-room' ? '搜索直播间、主播...' : '搜索感兴趣的视频...'"
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
            <div class="avatar" @click="openProfile(currentUserId)">
              <img v-if="currentUserAvatar" :src="currentUserAvatar" alt="" />
              <span v-else>{{ avatarText }}</span>
            </div>
            <span class="username" @click="openProfile(currentUserId)">{{ currentUser }}</span>
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
          <li :class="{ active: currentPage === 'profile' }" @click="openProfile(currentUserId)">个人主页</li>
          <li :class="{ active: currentPage === 'following' }" @click="openFollowingPage">我的关注</li>
          <li :class="{ active: currentPage === 'history' }" @click="openHistoryPage">历史记录</li>
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

            <div class='live-room-layout'>
              <div
                ref='livePlayerPanelRef'
                class='live-player-panel'
                @mousemove="handleLiveMouseMove"
                @mouseleave="handleLiveMouseLeave"
              >
                <video
                  ref='liveVideoRef'
                  class='live-player'
                  autoplay
                  playsinline
                  muted
                  @play="onLivePlay"
                  @pause="onLivePause"
                  @volumechange="onLiveVolumeChange"
                  @click="toggleLivePlay"
                ></video>
                <div class="live-player-topbar" :class="{ 'show-controls': showLiveControls, 'hide-controls': !showLiveControls && livePlaying }">
                  <span class="live-player-badge">LIVE</span>
                  <span>{{ liveQuality }}</span>
                </div>
                <div v-if='!selectedLiveRoom.pullUrl' class='player-empty'>等待主播推流后即可观看</div>
                <button v-if="!livePlaying && selectedLiveRoom.pullUrl" class="live-big-play-btn" @click="toggleLivePlay">▶</button>
                <div class="live-controls-overlay" :class="{ 'show-controls': showLiveControls, 'hide-controls': !showLiveControls && livePlaying }" @click.stop>
                  <div class="live-controls-row">
                    <div class="live-left-controls">
                      <button class="live-control-btn icon-btn" :title="livePlaying ? '暂停' : '播放'" @click="toggleLivePlay">
                        <svg v-if="livePlaying" viewBox="0 0 24 24" aria-hidden="true">
                          <rect x="6" y="5" width="4" height="14" rx="1"></rect>
                          <rect x="14" y="5" width="4" height="14" rx="1"></rect>
                        </svg>
                        <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                          <path d="M8 5v14l11-7z"></path>
                        </svg>
                      </button>
                      <div class="live-volume-control">
                        <button class="live-control-btn icon-btn" :title="liveMuted ? '取消静音' : '静音'" @click="toggleLiveMute">
                          <svg v-if="liveMuted || liveVolume === 0" viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M4 9v6h4l5 4V5L8 9H4z"></path>
                            <path d="M17 9l4 4m0-4l-4 4"></path>
                          </svg>
                          <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M4 9v6h4l5 4V5L8 9H4z"></path>
                            <path d="M16 8c1.3 1 2 2.4 2 4s-.7 3-2 4"></path>
                            <path d="M18.5 5.5A8 8 0 0 1 21 12a8 8 0 0 1-2.5 6.5"></path>
                          </svg>
                        </button>
                        <input
                          class="live-volume-slider"
                          type="range"
                          min="0"
                          max="1"
                          step="0.01"
                          :value="liveMuted ? 0 : liveVolume"
                          aria-label="直播音量"
                          @input="setLiveVolume"
                        />
                      </div>
                      <span class="live-status-dot" title="直播中"></span>
                    </div>
                    <div class="live-right-controls">
                      <div v-if="liveQualityOptions.length > 1" class="live-quality-control">
                        <button class="live-control-btn quality-btn" title="清晰度" @click="toggleLiveQualityMenu">
                          <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M4 7h10"></path>
                            <path d="M18 7h2"></path>
                            <circle cx="16" cy="7" r="2"></circle>
                            <path d="M4 17h2"></path>
                            <path d="M10 17h10"></path>
                            <circle cx="8" cy="17" r="2"></circle>
                          </svg>
                          <strong>{{ liveQuality }}</strong>
                        </button>
                        <div class="live-quality-dropdown" :class="{ show: showLiveQualityMenu }">
                          <button
                            v-for="quality in liveQualityOptions"
                            :key="quality"
                            :class="{ active: liveQuality === quality }"
                            @click="changeLiveQuality(quality)"
                          >
                            {{ quality }}
                          </button>
                        </div>
                      </div>
                      <button class="live-control-btn icon-btn" title="全屏" @click="toggleLiveFullscreen">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                          <path d="M8 4H4v4"></path>
                          <path d="M16 4h4v4"></path>
                          <path d="M20 16v4h-4"></path>
                          <path d="M4 16v4h4"></path>
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>
                <div ref='liveDanmuLayerRef' class='live-danmu-layer'></div>
              </div>

              <aside ref='liveChatPanelRef' class='live-chat-panel'>
                <div class='live-chat-header'>
                  <div>
                    <h3>直播互动</h3>
                    <p>{{ onlineCount }} 人在线</p>
                  </div>
                  <button class='live-like-btn' @click='sendLiveLike'>赞 {{ likeCount }}</button>
                </div>

                <div ref='liveMessageListRef' class='live-message-list'>
                  <div v-for='message in liveMessages' :key='message.id' class='live-message-item'>
                    <span class='live-message-user'>{{ message.username }}</span>
                    <span class='live-message-content'>{{ message.content }}</span>
                  </div>
                  <div v-if='liveMessages.length === 0' class='live-message-empty'>还没有弹幕，来发第一条</div>
                </div>

                <div class='live-chat-input'>
                  <label class='live-color-picker' :style='{ backgroundColor: liveDanmuColor }'>
                    <input v-model='liveDanmuColor' type='color' aria-label='弹幕颜色' />
                  </label>
                  <input
                    v-model.trim='liveDanmuInput'
                    type='text'
                    maxlength='100'
                    :placeholder="isLoggedIn ? '说点什么...' : '登录后才能发送弹幕'"
                    :readonly="!isLoggedIn"
                    @focus="requireLoginForLiveDanmu"
                    @keyup.enter='sendLiveDanmu'
                  />
                  <button @click='sendLiveDanmu'>发送</button>
                </div>
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

        <template v-else-if="currentPage === 'profile'">
          <section class="page-panel profile-page">
            <div v-if="profileLoading" class="empty-state">正在加载个人主页...</div>
            <template v-else>
              <div class="profile-hero">
                <div class="profile-avatar">
                  <img v-if="profileData?.avatarUrl" :src="profileData.avatarUrl" alt="" />
                  <span v-else>{{ (profileData?.nickname || currentUser || '用').slice(0, 1) }}</span>
                </div>
                <div class="profile-main">
                  <h2>{{ profileData?.nickname || currentUser || '用户' }}</h2>
                  <p>UID {{ profileData?.id || currentUserId || '-' }}</p>
                </div>
                <div class="profile-stats">
                  <div>
                    <strong>{{ profileData?.followerCount ?? profileData?.followers ?? 0 }}</strong>
                    <span>粉丝</span>
                  </div>
                  <div>
                    <strong>{{ profileData?.followingCount || 0 }}</strong>
                    <span>关注</span>
                  </div>
                  <div>
                    <strong>{{ profileData?.uploadCount || 0 }}</strong>
                    <span>投稿</span>
                  </div>
                  <div>
                    <strong>{{ profileData?.favoriteCount || 0 }}</strong>
                    <span>收藏</span>
                  </div>
                </div>
                <button
                  v-if="!isOwnProfile"
                  class="profile-follow-btn"
                  :class="{ followed: profileData?.following }"
                  @click="toggleFollowUser(profileData)"
                >
                  {{ profileData?.following ? '已关注' : '+ 关注' }}
                </button>
              </div>

              <div v-if="isOwnProfile" class="profile-editor">
                <input v-model.trim="avatarForm" type="text" placeholder="输入头像图片 URL" />
                <button @click="updateAvatar">修改头像</button>
              </div>

              <div class="profile-tabs">
                <button :class="{ active: profileTab === 'uploads' }" @click="profileTab = 'uploads'">投稿</button>
                <button :class="{ active: profileTab === 'favorites' }" @click="profileTab = 'favorites'">收藏</button>
              </div>

              <div v-if="profileVideos.length === 0" class="empty-state">
                {{ profileTab === 'uploads' ? '还没有投稿视频' : '还没有收藏视频' }}
              </div>
              <div v-else class="video-grid">
                <div class="video-card" v-for="video in profileVideos" :key="`${profileTab}-${video.id}`" @click="openVideoPlayer(video)">
                  <div class="thumbnail">
                    <img v-if="video.coverUrl" class="cover-image" :src="video.coverUrl" alt="" />
                    <span class="duration">{{ video.duration }}</span>
                  </div>
                  <div class="info">
                    <h3 class="title">{{ video.title }}</h3>
                    <p class="author">{{ video.author }}</p>
                    <div v-if="video.tags?.length" class="video-tags">
                      <span v-for="tag in video.tags" :key="`${video.id}-${tag}`">{{ tag }}</span>
                    </div>
                    <p class="stats">{{ video.views }} 观看 · {{ video.date }}</p>
                    <div v-if="isOwnProfile && profileTab === 'uploads'" class="video-manage" @click.stop>
                      <span class="visibility-tag">{{ video.status === 'private' ? '仅自己可见' : '公开' }}</span>
                      <button @click="toggleVideoVisibility(video)">{{ video.status === 'private' ? '设为公开' : '仅自己可见' }}</button>
                      <button class="danger" @click="deleteCreatorVideo(video)">删除</button>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </section>
        </template>

        <template v-else-if="currentPage === 'following'">
          <section class="page-panel social-page">
            <div class="page-header">
              <div>
                <h2>我的关注</h2>
                <p>查看你关注的人和关注你的用户。</p>
              </div>
              <button class="refresh-btn" @click="fetchSocialData">刷新</button>
            </div>

            <div v-if="socialLoading" class="empty-state">正在加载关注列表...</div>
            <div v-else class="social-grid">
              <section class="social-list">
                <h3>关注的人</h3>
                <div v-if="followingList.length === 0" class="empty-state">还没有关注任何用户</div>
                <div v-for="user in followingList" :key="`following-${user.id}`" class="social-user">
                  <div class="social-avatar" @click="openProfile(user.id)">
                    <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                    <span v-else>{{ (user.nickname || user.username || '用').slice(0, 1) }}</span>
                  </div>
                  <div class="social-main" @click="openProfile(user.id)">
                    <strong>{{ user.nickname || user.username || '用户' }}</strong>
                    <span>{{ user.followerCount || 0 }} 粉丝</span>
                  </div>
                  <button class="profile-follow-btn followed" @click="toggleFollowUser(user)">已关注</button>
                </div>
              </section>

              <section class="social-list">
                <h3>关注我的人</h3>
                <div v-if="followerList.length === 0" class="empty-state">暂时还没有粉丝</div>
                <div v-for="user in followerList" :key="`follower-${user.id}`" class="social-user">
                  <div class="social-avatar" @click="openProfile(user.id)">
                    <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                    <span v-else>{{ (user.nickname || user.username || '用').slice(0, 1) }}</span>
                  </div>
                  <div class="social-main" @click="openProfile(user.id)">
                    <strong>{{ user.nickname || user.username || '用户' }}</strong>
                    <span>{{ user.followerCount || 0 }} 粉丝</span>
                  </div>
                  <button
                    v-if="Number(user.id) !== Number(currentUserId)"
                    class="profile-follow-btn"
                    :class="{ followed: user.following }"
                    @click="toggleFollowUser(user)"
                  >
                    {{ user.following ? '已关注' : '+ 关注' }}
                  </button>
                </div>
              </section>
            </div>
          </section>
        </template>

        <template v-else-if="currentPage === 'history'">
          <section class="page-panel history-page">
            <div class="page-header">
              <div>
                <h2>历史记录</h2>
                <p>按最近观看时间排列，继续看你刚才浏览过的视频。</p>
              </div>
              <button class="refresh-btn" @click="fetchHistoryData">刷新</button>
            </div>

            <div v-if="historyLoading" class="empty-state">正在加载历史记录...</div>
            <div v-else-if="historyList.length === 0" class="creator-empty">还没有观看历史，去首页看看感兴趣的视频吧。</div>
            <div v-else class="creator-manuscript-list history-manuscript-list">
              <div v-for="item in historyList" :key="`history-${item.id || item.videoId}`" class="creator-manuscript-item history-manuscript-item">
                <div class="creator-manuscript-cover" @click="openVideoPlayer(item.video)">
                  <img v-if="item.video?.coverUrl" :src="item.video.coverUrl" alt="" />
                  <div v-else class="creator-cover-fallback">{{ item.video?.title?.slice(0, 1) || 'V' }}</div>
                  <span>{{ item.video?.duration || '00:00' }}</span>
                </div>

                <div class="creator-manuscript-main">
                  <h4 @click="openVideoPlayer(item.video)">{{ item.video?.title || '已失效视频' }}</h4>
                  <p>{{ item.video?.description || '暂无简介' }}</p>
                  <div v-if="item.video?.tags?.length" class="video-tags">
                    <span v-for="tag in item.video.tags" :key="`history-${item.video.id}-${tag}`">{{ tag }}</span>
                  </div>
                  <div class="creator-manuscript-metrics">
                    <span>{{ item.video?.author || '匿名用户' }}</span>
                    <span>{{ formatCompactNumber(item.video?.playCount || parseViews(item.video?.views)) }} 播放</span>
                    <span>{{ item.viewCount || 1 }} 次观看</span>
                    <span>上次观看 {{ formatHistoryDate(item.lastViewedAt) }}</span>
                  </div>
                </div>

                <div class="creator-manuscript-actions">
                  <button class="outline-btn" :disabled="!item.video" @click="openVideoPlayer(item.video)">继续观看</button>
                  <button class="outline-btn" :disabled="!item.video?.userId" @click="openProfile(item.video?.userId)">作者主页</button>
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
                <p>查看稿件表现，管理已发布视频，也可以继续发布新作品。</p>
              </div>
              <button class="refresh-btn" :disabled="creatorRefreshing" @click="refreshCreatorCenter">
                {{ creatorRefreshing ? '刷新中...' : '刷新数据' }}
              </button>
            </div>

            <div class="creator-dashboard">
              <section class="creator-overview-card">
                <div class="creator-overview-profile">
                  <div class="creator-avatar">
                    <img v-if="currentUserAvatar" :src="currentUserAvatar" alt="" />
                    <span v-else>{{ avatarText }}</span>
                  </div>
                  <div>
                    <h3>{{ currentUser || '创作者' }}</h3>
                    <p>UID {{ currentUserId || '-' }}</p>
                  </div>
                </div>

                <div class="creator-stat-grid">
                  <div class="creator-stat-card">
                    <span>粉丝数</span>
                    <strong>{{ formatCompactNumber(profileData?.followerCount ?? profileData?.followers ?? 0) }}</strong>
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
                      <div v-if="video.tags?.length" class="video-tags">
                        <span v-for="tag in video.tags" :key="`creator-${video.id}-${tag}`">{{ tag }}</span>
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
                    <p>上传本地视频，加入站内列表。</p>
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
                  <input v-model.trim="uploadForm.coverUrl" type="text" placeholder="可选，填写封面图片 URL" />
                  <div v-if="generatedUploadCoverUrl && !uploadForm.coverUrl" class="auto-cover-preview">
                    <img :src="generatedUploadCoverUrl" alt="" />
                    <span>已自动截取视频首帧作为封面</span>
                  </div>
                </div>
                <div class="creator-row">
                  <label>视频标签</label>
                  <input v-model.trim="uploadForm.tags" type="text" placeholder="例如：音乐 校园 舞台，可用空格或逗号分隔" />
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

        <template v-else>
          <section class="page-panel">
            <div v-if="currentPage === 'ranking'" class="page-header">
              <div>
                <h2>热门排行</h2>
                <p>按播放热度排序。</p>
              </div>
            </div>

            <div v-if="displayedVideos.length === 0" class="empty-state">
              {{ currentPage === 'ranking' ? '暂无可排行的视频' : '暂无视频，去创作者中心上传第一条作品吧。' }}
            </div>

            <div v-else class="video-grid">
              <div class="video-card" v-for="video in displayedVideos" :key="video.id" @click="openVideoPlayer(video)">
                <div class="thumbnail">
                  <img v-if="video.coverUrl" class="cover-image" :src="video.coverUrl" alt="" />
                  <span class="duration">{{ video.duration }}</span>
                </div>
                <div class="info">
                  <h3 class="title">{{ video.title }}</h3>
                  <p class="author">{{ video.author }}</p>
                  <div v-if="video.tags?.length" class="video-tags">
                    <span v-for="tag in video.tags" :key="`feed-${video.id}-${tag}`">{{ tag }}</span>
                  </div>
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
      :videoData="selectedVideo"
      @back="closeVideoPlayer"
      @login-required="openLoginModal"
      @open-profile="openProfile"
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
import { API_BASE, getWsOrigin } from './config/network'

const keyword = ref('')
const submittedKeyword = ref('')
const isLoggedIn = ref(false)
const showModal = ref(false)
const isRegisterMode = ref(false)
const currentUser = ref('')
const currentUserId = ref(null)
const currentUserAvatar = ref('')
const showVideoPlayer = ref(false)
const selectedVideo = ref(null)
const currentPage = ref('home')
const selectedUploadFile = ref(null)
const selectedUploadDuration = ref(null)
const generatedUploadCoverUrl = ref('')
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
const liveQuality = ref('原画')
const livePlaying = ref(false)
const liveMuted = ref(true)
const liveVolume = ref(1)
const showLiveQualityMenu = ref(false)
const showLiveControls = ref(true)
const liveControlsTimer = ref(null)
const liveMessages = ref([])
const liveDanmuInput = ref('')
const liveDanmuColor = ref('#ffffff')
const livePlayerPanelRef = ref(null)
const liveChatPanelRef = ref(null)
const liveDanmuLayerRef = ref(null)
const liveMessageListRef = ref(null)
const livePanelResizeObserver = ref(null)
const liveWs = ref(null)
const liveReconnectTimer = ref(null)
const onlineCount = ref(0)
const likeCount = ref(0)
const profileLoading = ref(false)
const profileData = ref(null)
const profileTab = ref('uploads')
const socialLoading = ref(false)
const followingList = ref([])
const followerList = ref([])
const historyLoading = ref(false)
const historyList = ref([])
const avatarForm = ref('')
const authForm = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: ''
})

const uploadForm = reactive({
  title: '',
  description: '',
  coverUrl: '',
  tags: ''
})

const liveForm = reactive({
  title: '',
  categoryId: 1,
  coverUrl: ''
})

const videoList = ref([])

const avatarText = computed(() => currentUser.value?.slice(0, 1) || '用')

const filteredVideos = computed(() => {
  const lowerKeyword = submittedKeyword.value.toLowerCase()
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

const profileVideos = computed(() => {
  if (!profileData.value) {
    return []
  }
  return profileTab.value === 'favorites' ? profileData.value.favorites || [] : profileData.value.uploads || []
})

const isOwnProfile = computed(() => Number(profileData.value?.id) === Number(currentUserId.value))

const creatorVideos = computed(() => {
  const uploads = Array.isArray(profileData.value?.uploads) ? profileData.value.uploads : []
  if (uploads.length > 0) {
    return [...uploads].sort((a, b) => calculateHotScore(b) - calculateHotScore(a))
  }

  const currentId = currentUserId.value == null ? '' : String(currentUserId.value)
  const currentName = String(currentUser.value || '').trim()
  return videoList.value
    .filter((video) => {
      if (currentId && video.userId != null && String(video.userId) === currentId) {
        return true
      }
      return currentName && String(video.author || '').trim() === currentName
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

const filteredLiveRooms = computed(() => {
  const selectedCategoryId = Number(liveCategoryFilter.value || 0)
  const categoryFilteredRooms = selectedCategoryId > 0
    ? liveRooms.value.filter((room) => Number(room.categoryId) === selectedCategoryId)
    : liveRooms.value
  const lowerKeyword = submittedKeyword.value.toLowerCase()
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

const liveQualityOptions = computed(() => {
  const urls = selectedLiveRoom.value?.qualityUrls || {}
  return Object.keys(urls).filter((quality) => urls[quality])
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
  clearLiveControlsTimer()
  destroyLivePlayer()
  disconnectLiveInteract()
  stopLivePanelResizeObserver()
})

watch(
  () => selectedLiveRoom.value?.pullUrl,
  () => {
    if (currentPage.value === 'live-room') {
      setupLivePlayer()
    }
  }
)

watch(
  () => selectedLiveRoom.value?.roomId,
  () => {
    liveQuality.value = '原画'
  }
)

const restoreLoginState = () => {
  const savedNickname = localStorage.getItem('loginUserNickname') || localStorage.getItem('loginUser')
  const savedUserId = localStorage.getItem('loginUserId')
  const savedAvatar = localStorage.getItem('loginUserAvatar') || ''
  if (savedNickname && savedUserId) {
    isLoggedIn.value = true
    currentUser.value = savedNickname
    currentUserId.value = Number(savedUserId)
    currentUserAvatar.value = savedAvatar
  }
}

const goHome = () => {
  setPage('home')
}

const setRouteHash = (page, id = null) => {
  let nextHash = `#/${page}`
  if (page === 'live-room' && id) {
    nextHash = `#/live/${id}`
  } else if (page === 'profile' && id) {
    nextHash = `#/profile/${id}`
  }
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
  if (page === 'profile') {
    return { page: 'profile', userId: roomId }
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
  if (route.page === 'profile') {
    await loadProfile(route.userId || currentUserId.value, false)
    return
  }
  await setPage(route.page, false)
}

const setPage = async (page, updateRoute = true) => {
  currentPage.value = page
  keyword.value = ''
  submittedKeyword.value = ''
  if (page !== 'live-room') {
    selectedLiveRoom.value = null
    destroyLivePlayer()
    disconnectLiveInteract()
    resetLiveInteractState()
    stopLivePanelResizeObserver()
  }
  localStorage.setItem('currentPage', page)
  if (updateRoute) {
    setRouteHash(page)
  }
  if (page === 'live') {
    await fetchLiveRooms()
  }
  if (page === 'creator') {
    await refreshCreatorCenter()
  }
  if (page === 'following') {
    await fetchSocialData()
  }
  if (page === 'history') {
    await fetchHistoryData()
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
  if (!video) {
    return
  }
  selectedVideo.value = video
  showVideoPlayer.value = true
  window.scrollTo(0, 0)
}

const closeVideoPlayer = () => {
  showVideoPlayer.value = false
  selectedVideo.value = null
}

const openProfile = async (userId) => {
  if (!userId) {
    openLoginModal()
    return
  }
  await loadProfile(userId)
}

const openFollowingPage = async () => {
  if (!isLoggedIn.value || !currentUserId.value) {
    openLoginModal()
    return
  }
  await setPage('following')
}

const openHistoryPage = async () => {
  if (!isLoggedIn.value || !currentUserId.value) {
    openLoginModal()
    return
  }
  await setPage('history')
}

const getAuthHeaders = () => (
  currentUserId.value ? { 'X-User-Id': currentUserId.value } : {}
)

const fetchHistoryData = async () => {
  if (!isLoggedIn.value || !currentUserId.value) {
    historyList.value = []
    return
  }

  historyLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/user/${currentUserId.value}/history`)
    const list = Array.isArray(res.data?.data) ? res.data.data : []
    historyList.value = list
      .filter((item) => item.video)
      .map((item) => ({
        ...item,
        video: convertVideoFromBackend(item.video)
      }))
  } catch (error) {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

const fetchSocialData = async () => {
  if (!isLoggedIn.value || !currentUserId.value) {
    followingList.value = []
    followerList.value = []
    return
  }

  socialLoading.value = true
  try {
    const [followingRes, followerRes] = await Promise.all([
      axios.get(`${API_BASE}/user/${currentUserId.value}/following`),
      axios.get(`${API_BASE}/user/${currentUserId.value}/followers`)
    ])
    followingList.value = Array.isArray(followingRes.data?.data) ? followingRes.data.data : []
    followerList.value = Array.isArray(followerRes.data?.data) ? followerRes.data.data : []
  } catch (error) {
    followingList.value = []
    followerList.value = []
  } finally {
    socialLoading.value = false
  }
}

const toggleFollowUser = async (user) => {
  const targetId = user?.id || user?.userId || user?.authorInfo?.id || user?.authorInfo?.userId
  if (!targetId) {
    return
  }
  if (!isLoggedIn.value || !currentUserId.value) {
    openLoginModal()
    return
  }
  if (Number(targetId) === Number(currentUserId.value)) {
    return
  }

  const alreadyFollowing = !!user.following
  try {
    if (alreadyFollowing) {
      await axios.delete(`${API_BASE}/user/${targetId}/follow`, {
        headers: getAuthHeaders()
      })
    } else {
      await axios.post(`${API_BASE}/user/${targetId}/follow`, null, {
        headers: getAuthHeaders()
      })
    }
    user.following = !alreadyFollowing
    const delta = user.following ? 1 : -1
    user.followerCount = Math.max(0, Number(user.followerCount || 0) + delta)
    if (profileData.value && Number(profileData.value.id) === Number(targetId)) {
      profileData.value.following = user.following
      profileData.value.followerCount = Math.max(0, Number(profileData.value.followerCount || profileData.value.followers || 0) + delta)
      profileData.value.followers = profileData.value.followerCount
    }
    if (selectedVideo.value && Number(selectedVideo.value.userId) === Number(targetId)) {
      selectedVideo.value.authorInfo = {
        ...(selectedVideo.value.authorInfo || {}),
        following: user.following,
        followerCount: user.followerCount
      }
    }
    if (currentPage.value === 'following') {
      await fetchSocialData()
    }
    await loadVideoList()
  } catch (error) {
    alert(error.response?.data?.message || '关注操作失败')
  }
}

const loadProfile = async (userId, updateRoute = true) => {
  if (!userId) {
    await setPage('home', updateRoute)
    return
  }

  profileLoading.value = true
  showVideoPlayer.value = false
  selectedVideo.value = null
  currentPage.value = 'profile'
  selectedLiveRoom.value = null
  destroyLivePlayer()
  disconnectLiveInteract()
  resetLiveInteractState()
  stopLivePanelResizeObserver()
  localStorage.setItem('currentPage', 'profile')
  if (updateRoute) {
    setRouteHash('profile', userId)
  }

  try {
    const res = await axios.get(`${API_BASE}/user/${userId}/profile`, {
      params: currentUserId.value ? { viewerId: currentUserId.value } : {}
    })
    const data = res.data?.data || {}
    profileData.value = {
      ...data,
      uploads: Array.isArray(data.uploads) ? data.uploads.map(convertVideoFromBackend) : [],
      favorites: Array.isArray(data.favorites) ? data.favorites.map(convertVideoFromBackend) : []
    }
    avatarForm.value = profileData.value.avatarUrl || ''
  } catch (error) {
    profileData.value = {
      id: userId,
      nickname: currentUser.value || '用户',
      followers: 0,
      uploads: [],
      favorites: []
    }
    avatarForm.value = profileData.value.avatarUrl || ''
  } finally {
    profileLoading.value = false
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
      avatarUrl: data.user.avatarUrl
    }
  }
  if (data.data?.user?.userId) {
    return {
      id: data.data.user.userId,
      username: data.data.user.username,
      nickname: data.data.user.nickname,
      avatarUrl: data.data.user.avatarUrl
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
      currentUserAvatar.value = loginUser.avatarUrl || ''
      localStorage.setItem('loginUser', currentUser.value)
      localStorage.setItem('loginUserNickname', currentUser.value)
      localStorage.setItem('loginUserId', String(loginUser.id))
      localStorage.setItem('loginUserAvatar', currentUserAvatar.value)
      showModal.value = false
      resetForm()
      await loadVideoList()
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
  currentUserAvatar.value = ''
  localStorage.removeItem('loginUser')
  localStorage.removeItem('loginUserNickname')
  localStorage.removeItem('loginUserId')
  localStorage.removeItem('loginUserAvatar')
  followingList.value = []
  followerList.value = []
  historyList.value = []
  loadVideoList()
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
    userId: video.userId,
    title: video.title,
    author: video.authorInfo?.nickname || video.author || '匿名用户',
    authorInfo: video.authorInfo || null,
    authorAvatarUrl: video.authorInfo?.avatarUrl || '',
    tags: Array.isArray(video.tagList)
      ? video.tagList
      : String(video.tags || '').split(/[,，\s]+/).map((tag) => tag.trim()).filter(Boolean),
    views: video.views || formatPlayCount(video.playCount || 0),
    duration: formatDuration(video.duration),
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
    status: video.status || 'public',
    liked: video.liked || false,
    favorited: video.favorited || false
  }
}

const loadVideoList = async () => {
  try {
    const res = await axios.get(`${API_BASE}/videos/recommend`, {
      params: {
        page: 1,
        pageSize: 50,
        categoryId: 0,
        keyword: submittedKeyword.value || undefined
      },
      headers: getAuthHeaders()
    })
    if (res.data?.code === 200 && Array.isArray(res.data.data) && res.data.data.length > 0) {
      videoList.value = res.data.data
        .map(convertVideoFromBackend)
        .filter((video) => video.sources['720P'] || Object.values(video.sources).length > 0)
      return
    }
  } catch (error) {
    videoList.value = []
  }
}

const formatPlayCount = (count) => {
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1)}万`
  }
  return String(count)
}

const formatDuration = (value) => {
  const seconds = Number(value || 0)
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return '00:00'
  }
  const totalSeconds = Math.floor(seconds)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const restSeconds = totalSeconds % 60
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(restSeconds).padStart(2, '0')}`
  }
  return `${String(minutes).padStart(2, '0')}:${String(restSeconds).padStart(2, '0')}`
}

const readVideoDuration = (file) => {
  return new Promise((resolve) => {
    if (!file) {
      resolve(null)
      return
    }
    const video = document.createElement('video')
    const objectUrl = URL.createObjectURL(file)
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(objectUrl)
      resolve(Number.isFinite(video.duration) ? Math.round(video.duration) : null)
    }
    video.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      resolve(null)
    }
    video.src = objectUrl
  })
}

const captureVideoFirstFrame = (file) => {
  return new Promise((resolve) => {
    if (!file) {
      resolve('')
      return
    }

    const video = document.createElement('video')
    const canvas = document.createElement('canvas')
    const objectUrl = URL.createObjectURL(file)

    const cleanup = () => {
      URL.revokeObjectURL(objectUrl)
      video.removeAttribute('src')
      video.load()
    }

    video.preload = 'metadata'
    video.muted = true
    video.playsInline = true
    video.onloadedmetadata = () => {
      video.currentTime = Math.min(0.1, Math.max(0, (video.duration || 1) - 0.01))
    }
    video.onseeked = () => {
      const width = video.videoWidth || 1280
      const height = video.videoHeight || 720
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d')
      if (!context) {
        cleanup()
        resolve('')
        return
      }
      context.drawImage(video, 0, 0, width, height)
      cleanup()
      resolve(canvas.toDataURL('image/jpeg', 0.78))
    }
    video.onerror = () => {
      cleanup()
      resolve('')
    }
    video.src = objectUrl
  })
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

const formatHistoryDate = (dateString) => {
  if (!dateString) {
    return '刚刚'
  }
  const relative = formatRelativeDate(dateString)
  if (relative !== '刚刚') {
    return relative
  }
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) {
    return '刚刚'
  }
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
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

const syncAuthorAvatar = (userId, avatarUrl) => {
  const patchVideo = (video) => {
    if (!video || String(video.userId) !== String(userId)) {
      return
    }
    video.authorAvatarUrl = avatarUrl || ''
    video.authorInfo = {
      ...(video.authorInfo || {}),
      userId,
      avatarUrl: avatarUrl || ''
    }
  }

  videoList.value.forEach(patchVideo)
  profileData.value?.uploads?.forEach(patchVideo)
  profileData.value?.favorites?.forEach(patchVideo)
  patchVideo(selectedVideo.value)
}

const formatCompactNumber = (value) => {
  const number = Number(value || 0)
  if (!Number.isFinite(number)) {
    return '0'
  }
  if (number >= 10000) {
    return `${(number / 10000).toFixed(1)}万`
  }
  return String(Math.round(number))
}

const calculateHotScore = (video) => {
  return Number(video?.playCount || parseViews(video?.views) || 0)
    + Number(video?.likeCount || 0) * 5
    + Number(video?.favoriteCount || 0) * 8
    + Number(video?.commentCount || 0) * 10
}

const refreshCreatorCenter = async () => {
  if (!isLoggedIn.value) {
    alert('请先登录后进入创作者中心')
    openLoginModal()
    return
  }

  creatorRefreshing.value = true
  try {
    await Promise.all([
      loadVideoList(),
      loadProfile(currentUserId.value, false)
    ])
    currentPage.value = 'creator'
    localStorage.setItem('currentPage', 'creator')
    setRouteHash('creator')
  } finally {
    creatorRefreshing.value = false
  }
}

const handleSearch = async () => {
  submittedKeyword.value = keyword.value.trim()
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

const handleFileChange = async (event) => {
  const files = event.target.files
  selectedUploadFile.value = files && files[0] ? files[0] : null
  selectedUploadDuration.value = selectedUploadFile.value ? await readVideoDuration(selectedUploadFile.value) : null
  if (selectedUploadFile.value && !uploadForm.coverUrl.trim()) {
    generatedUploadCoverUrl.value = await captureVideoFirstFrame(selectedUploadFile.value)
  } else {
    generatedUploadCoverUrl.value = ''
  }
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
  const autoCoverUrl = uploadForm.coverUrl.trim() || generatedUploadCoverUrl.value || await captureVideoFirstFrame(selectedUploadFile.value)
  formData.append('title', uploadForm.title)
  formData.append('description', uploadForm.description)
  formData.append('coverUrl', autoCoverUrl)
  formData.append('tags', uploadForm.tags)
  formData.append('author', currentUser.value || '匿名用户')
  formData.append('userId', currentUserId.value)
  if (selectedUploadDuration.value) {
    formData.append('duration', selectedUploadDuration.value)
  }
  formData.append('file', selectedUploadFile.value)

  uploadingVideo.value = true
  try {
    const res = await axios.post(`${API_BASE}/videos/upload`, formData)
    if (res.data?.code === 200 && res.data.data) {
      alert('上传成功，已加入视频列表')
      resetUploadForm()
      await refreshCreatorCenter()
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
  uploadForm.tags = ''
  selectedUploadFile.value = null
  selectedUploadDuration.value = null
  generatedUploadCoverUrl.value = ''
}


const syncLiveChatPanelHeight = () => {
  if (!livePlayerPanelRef.value || !liveChatPanelRef.value) {
    return
  }
  const height = livePlayerPanelRef.value.offsetHeight
  if (height > 0) {
    liveChatPanelRef.value.style.height = `${height}px`
  }
}

const observeLivePanelSize = () => {
  stopLivePanelResizeObserver()
  if (!livePlayerPanelRef.value || typeof ResizeObserver === 'undefined') {
    syncLiveChatPanelHeight()
    return
  }
  livePanelResizeObserver.value = new ResizeObserver(syncLiveChatPanelHeight)
  livePanelResizeObserver.value.observe(livePlayerPanelRef.value)
  syncLiveChatPanelHeight()
}

const stopLivePanelResizeObserver = () => {
  if (livePanelResizeObserver.value) {
    livePanelResizeObserver.value.disconnect()
    livePanelResizeObserver.value = null
  }
  if (liveChatPanelRef.value) {
    liveChatPanelRef.value.style.height = ''
  }
}
const resetLiveInteractState = () => {
  liveMessages.value = []
  liveDanmuInput.value = ''
  onlineCount.value = 0
  likeCount.value = 0
  if (liveDanmuLayerRef.value) {
    liveDanmuLayerRef.value.innerHTML = ''
  }
}

const scrollLiveMessagesToBottom = async () => {
  await nextTick()
  if (liveMessageListRef.value) {
    liveMessageListRef.value.scrollTop = liveMessageListRef.value.scrollHeight
  }
}

const appendLiveMessage = (message) => {
  if (!message?.content) {
    return
  }
  liveMessages.value.push({
    id: message.id || `${Date.now()}-${Math.random()}`,
    username: message.username || '游客',
    content: message.content,
    color: message.color || '#ffffff',
    sendTime: message.sendTime || new Date().toISOString()
  })
  if (liveMessages.value.length > 200) {
    liveMessages.value.splice(0, liveMessages.value.length - 200)
  }
  scrollLiveMessagesToBottom()
}

const showFloatingLiveDanmu = (content, color = '#ffffff', isSelf = false) => {
  const layer = liveDanmuLayerRef.value
  if (!layer || !content) {
    return
  }

  const item = document.createElement('div')
  item.className = isSelf ? 'live-danmu-float live-danmu-self' : 'live-danmu-float'
  item.textContent = content
  item.style.position = 'absolute'
  item.style.left = '100%'
  item.style.top = `${12 + Math.random() * 62}%`
  item.style.color = color
  layer.appendChild(item)

  const distance = layer.offsetWidth + item.offsetWidth + 80
  const animation = item.animate(
    [
      { transform: 'translateX(0)' },
      { transform: `translateX(-${distance}px)` }
    ],
    {
      duration: 11000,
      easing: 'linear',
      fill: 'forwards'
    }
  )
  animation.finished.finally(() => item.remove())
}

const loadLiveDanmuHistory = async (roomId) => {
  if (!roomId) {
    return
  }
  try {
    const res = await axios.get(`${API_BASE}/live/rooms/${roomId}/danmus`, {
      params: { limit: 50 }
    })
    const history = res.data?.data || []
    liveMessages.value = []
    history.forEach((item) => {
      appendLiveMessage({
        id: item.id,
        username: item.username,
        content: item.content,
        color: item.color,
        sendTime: item.sendTime
      })
    })
  } catch (error) {
    liveMessages.value = []
  }
}

const fetchLiveLikeCount = async (roomId) => {
  if (!roomId) {
    return
  }
  try {
    const res = await axios.get(`${API_BASE}/live/rooms/${roomId}/like`)
    likeCount.value = Number(res.data?.data?.likeCount || 0)
  } catch (error) {
    likeCount.value = 0
  }
}

const getLiveWsUrl = (roomId) => {
  return `${getWsOrigin()}/ws/live/${roomId}`
}

const connectLiveInteract = (roomId) => {
  if (!roomId || (liveWs.value && liveWs.value.readyState === WebSocket.OPEN)) {
    return
  }
  disconnectLiveInteract(false)
  liveWs.value = new WebSocket(getLiveWsUrl(roomId))

  liveWs.value.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'online_count') {
        onlineCount.value = Number(data.count || 0)
        return
      }
      if (data.type === 'like') {
        likeCount.value = Number(data.likeCount || 0)
        return
      }
      if (data.type === 'danmu') {
        appendLiveMessage(data)
        showFloatingLiveDanmu(data.content, data.color || '#ffffff', String(data.userId) === String(currentUserId.value))
      }
    } catch (error) {
      appendLiveMessage({ username: '游客', content: event.data, color: '#ffffff' })
      showFloatingLiveDanmu(event.data, '#ffffff')
    }
  }

  liveWs.value.onclose = () => {
    liveWs.value = null
    if (currentPage.value === 'live-room' && selectedLiveRoom.value?.roomId) {
      liveReconnectTimer.value = window.setTimeout(() => connectLiveInteract(selectedLiveRoom.value.roomId), 3000)
    }
  }
}

const disconnectLiveInteract = (clearTimer = true) => {
  if (clearTimer && liveReconnectTimer.value) {
    window.clearTimeout(liveReconnectTimer.value)
    liveReconnectTimer.value = null
  }
  if (liveWs.value) {
    liveWs.value.onclose = null
    liveWs.value.close()
    liveWs.value = null
  }
}

const initializeLiveInteract = async (roomId) => {
  resetLiveInteractState()
  await loadLiveDanmuHistory(roomId)
  await fetchLiveLikeCount(roomId)
  connectLiveInteract(roomId)
}

const requireLoginForLiveDanmu = () => {
  if (isLoggedIn.value && currentUserId.value) {
    return false
  }
  openLoginModal()
  return true
}

const sendLiveDanmu = () => {
  if (requireLoginForLiveDanmu()) {
    return
  }
  const content = liveDanmuInput.value.trim()
  if (!content) {
    return
  }
  if (!liveWs.value || liveWs.value.readyState !== WebSocket.OPEN) {
    alert('直播互动连接未建立，请稍后再试')
    return
  }
  liveWs.value.send(JSON.stringify({
    type: 'danmu',
    userId: currentUserId.value,
    username: currentUser.value || '游客',
    content,
    color: liveDanmuColor.value
  }))
  liveDanmuInput.value = ''
}

const sendLiveLike = () => {
  if (!liveWs.value || liveWs.value.readyState !== WebSocket.OPEN) {
    alert('直播互动连接未建立，请稍后再试')
    return
  }
  liveWs.value.send(JSON.stringify({
    type: 'like',
    userId: currentUserId.value
  }))
}
const updateAvatar = async () => {
  if (!isOwnProfile.value || !currentUserId.value) return
  try {
    const res = await axios.put(`${API_BASE}/user/${currentUserId.value}/avatar`, { avatarUrl: avatarForm.value })
    if (res.data?.code === 200) {
      profileData.value.avatarUrl = res.data.data?.avatarUrl || avatarForm.value
      currentUserAvatar.value = profileData.value.avatarUrl || ''
      localStorage.setItem('loginUserAvatar', currentUserAvatar.value)
      syncAuthorAvatar(currentUserId.value, currentUserAvatar.value)
      alert('头像已更新')
    } else {
      alert(res.data?.message || '头像更新失败')
    }
  } catch (error) {
    alert(error.response?.data?.message || error.message || '头像更新失败')
  }
}

const toggleVideoVisibility = async (video) => {
  if (!currentUserId.value || !video?.id) return
  const nextVisible = video.status === 'private'
  try {
    const res = await axios.post(`${API_BASE}/videos/${video.id}/visibility`, {
      userId: currentUserId.value,
      visible: nextVisible
    })
    if (res.data?.code === 200) {
      video.status = nextVisible ? 'public' : 'private'
      await loadVideoList()
    } else {
      alert(res.data?.message || '操作失败')
    }
  } catch (error) {
    alert(error.response?.data?.message || error.message || '操作失败')
  }
}

const deleteCreatorVideo = async (video) => {
  if (!currentUserId.value || !video?.id) {
    return
  }
  if (!window.confirm(`确定删除《${video.title || '该稿件'}》吗？删除后将不会再出现在列表中。`)) {
    return
  }

  deletingVideoId.value = video.id
  try {
    const res = await axios.delete(`${API_BASE}/videos/${video.id}`, {
      data: { userId: currentUserId.value },
      headers: { 'X-User-Id': currentUserId.value }
    })
    if (res.data?.code === 200) {
      videoList.value = videoList.value.filter((item) => String(item.id) !== String(video.id))
      if (profileData.value?.uploads) {
        profileData.value.uploads = profileData.value.uploads.filter((item) => String(item.id) !== String(video.id))
        profileData.value.uploadCount = Math.max(0, Number(profileData.value.uploadCount || 0) - 1)
      }
      if (selectedVideo.value && String(selectedVideo.value.id) === String(video.id)) {
        closeVideoPlayer()
      }
      alert('稿件已删除')
    } else {
      alert(res.data?.message || '删除稿件失败')
    }
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
    qualityUrls: normalizeLiveQualityUrls(raw),
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

const normalizeLiveQualityUrls = (raw) => {
  const pullUrl = raw.pullUrl || raw.playUrl || ''
  const qualityUrls = raw.qualityUrls || raw.liveSources || {}
  const result = {}
  if (pullUrl) {
    result['原画'] = qualityUrls['原画'] || qualityUrls.origin || qualityUrls.Origin || pullUrl
  }
  if (qualityUrls['720P'] || raw.pullUrl720p) {
    result['720P'] = qualityUrls['720P'] || raw.pullUrl720p
  }
  if (qualityUrls['480P'] || raw.pullUrl480p) {
    result['480P'] = qualityUrls['480P'] || raw.pullUrl480p
  }
  return result
}

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
  await initializeLiveInteract(room.roomId)
  await nextTick()
  observeLivePanelSize()
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
  await initializeLiveInteract(room.roomId)
  await nextTick()
  observeLivePanelSize()
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
  let res
  try {
    res = await axios.post(
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
  } catch (error) {
    if (error.response) {
      alert(error.response.data?.message || ('创建直播间失败（HTTP ' + error.response.status + '）'))
    } else {
      alert('无法连接后端服务，请确认 Spring Boot 已启动并监听 8080 端口')
    }
    creatingLive.value = false
    return
  }

  try {
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
    console.error('创建直播间后的页面更新失败:', error)
    alert('直播间已创建，但页面刷新失败，请手动刷新直播大厅')
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

  const qualityUrls = selectedLiveRoom.value.qualityUrls || {}
  if (liveQuality.value !== '原画' && !qualityUrls[liveQuality.value]) {
    liveQuality.value = '原画'
  }
  const pullUrl = qualityUrls[liveQuality.value] || selectedLiveRoom.value.pullUrl
  if (pullUrl.endsWith('.flv') && flvjs.isSupported()) {
    flvPlayer.value = flvjs.createPlayer({
      type: 'flv',
      url: pullUrl,
      isLive: true
    })
    flvPlayer.value.attachMediaElement(liveVideoRef.value)
    flvPlayer.value.load()
    flvPlayer.value.play().catch(() => {})
    liveMuted.value = liveVideoRef.value.muted
    liveVolume.value = liveVideoRef.value.volume
    return
  }

  liveVideoRef.value.src = pullUrl
  liveVideoRef.value.play().catch(() => {})
  liveMuted.value = liveVideoRef.value.muted
  liveVolume.value = liveVideoRef.value.volume
}

const onLivePlay = () => {
  livePlaying.value = true
  resetLiveControlsTimer()
}

const onLivePause = () => {
  livePlaying.value = false
  showLiveControls.value = true
  clearLiveControlsTimer()
}

const onLiveVolumeChange = () => {
  if (!liveVideoRef.value) {
    return
  }
  liveMuted.value = liveVideoRef.value.muted || liveVideoRef.value.volume === 0
  liveVolume.value = liveVideoRef.value.volume
}

const toggleLivePlay = () => {
  if (!liveVideoRef.value || !selectedLiveRoom.value?.pullUrl) {
    return
  }
  if (liveVideoRef.value.paused) {
    liveVideoRef.value.play().catch(() => {})
  } else {
    liveVideoRef.value.pause()
  }
}

const toggleLiveMute = () => {
  if (!liveVideoRef.value) {
    return
  }
  liveVideoRef.value.muted = !liveVideoRef.value.muted
  if (!liveVideoRef.value.muted && liveVideoRef.value.volume === 0) {
    liveVideoRef.value.volume = liveVolume.value || 0.6
  }
  liveMuted.value = liveVideoRef.value.muted
}

const setLiveVolume = (event) => {
  if (!liveVideoRef.value) {
    return
  }
  const nextVolume = Number(event.target.value)
  liveVideoRef.value.volume = nextVolume
  liveVideoRef.value.muted = nextVolume === 0
  liveVolume.value = nextVolume
  liveMuted.value = liveVideoRef.value.muted
  showLiveControls.value = true
  resetLiveControlsTimer()
}

const toggleLiveQualityMenu = () => {
  showLiveQualityMenu.value = !showLiveQualityMenu.value
  if (showLiveQualityMenu.value) {
    showLiveControls.value = true
    clearLiveControlsTimer()
  } else {
    resetLiveControlsTimer()
  }
}

const clearLiveControlsTimer = () => {
  if (liveControlsTimer.value) {
    window.clearTimeout(liveControlsTimer.value)
    liveControlsTimer.value = null
  }
}

const resetLiveControlsTimer = () => {
  clearLiveControlsTimer()
  if (!livePlaying.value || showLiveQualityMenu.value) {
    return
  }
  liveControlsTimer.value = window.setTimeout(() => {
    if (livePlaying.value && !showLiveQualityMenu.value) {
      showLiveControls.value = false
    }
  }, 3000)
}

const isPointerNearLivePlayerEdge = (event) => {
  const rect = livePlayerPanelRef.value?.getBoundingClientRect()
  if (!rect) {
    return false
  }

  const edgeSize = Math.min(96, Math.max(42, Math.min(rect.width, rect.height) * 0.14))
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top

  return (
    x <= edgeSize ||
    rect.width - x <= edgeSize ||
    y <= edgeSize ||
    rect.height - y <= edgeSize
  )
}

const handleLiveMouseMove = (event) => {
  if (!livePlaying.value) {
    showLiveControls.value = true
    return
  }

  if (isPointerNearLivePlayerEdge(event)) {
    showLiveControls.value = true
    resetLiveControlsTimer()
    return
  }

  clearLiveControlsTimer()
  if (!showLiveQualityMenu.value) {
    showLiveControls.value = false
  }
}

const handleLiveMouseLeave = () => {
  clearLiveControlsTimer()
  if (livePlaying.value && !showLiveQualityMenu.value) {
    showLiveControls.value = false
  }
}

const toggleLiveFullscreen = () => {
  if (!livePlayerPanelRef.value) {
    return
  }
  if (!document.fullscreenElement) {
    livePlayerPanelRef.value.requestFullscreen?.()
    return
  }
  document.exitFullscreen?.()
}

const changeLiveQuality = async (quality) => {
  if (quality === liveQuality.value) {
    return
  }
  liveQuality.value = quality
  showLiveQualityMenu.value = false
  await nextTick()
  setupLivePlayer()
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
  cursor: pointer;
  overflow: hidden;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  cursor: pointer;
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

.profile-page {
  display: grid;
  gap: 18px;
}

.profile-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: end;
  gap: 18px;
  min-height: 170px;
  padding: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #7cc8ff, #f7d8ff 55%, #fff2b8);
  color: #1f2630;
}

.profile-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 86px;
  height: 86px;
  border: 4px solid rgba(255, 255, 255, 0.86);
  border-radius: 50%;
  background: #fff;
  color: #1890ff;
  font-size: 36px;
  font-weight: 800;
  box-shadow: 0 8px 20px rgba(24, 144, 255, 0.18);
  overflow: hidden;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-main h2 {
  margin: 0 0 8px;
  font-size: 26px;
}

.profile-main p {
  margin: 0;
  color: rgba(31, 38, 48, 0.72);
  font-size: 14px;
}

.profile-stats {
  display: flex;
  gap: 22px;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.profile-stats div {
  display: grid;
  gap: 4px;
  text-align: center;
}

.profile-stats strong {
  font-size: 20px;
}

.profile-stats span {
  color: #667085;
  font-size: 12px;
}

.profile-follow-btn {
  border: 0;
  border-radius: 6px;
  background: #1890ff;
  color: #fff;
  padding: 9px 14px;
  cursor: pointer;
  font-weight: 700;
  white-space: nowrap;
}

.profile-follow-btn.followed {
  background: #eef4ff;
  color: #2563eb;
}

.social-page {
  display: grid;
  gap: 18px;
}

.social-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.social-list {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.social-list h3 {
  margin: 0;
  font-size: 18px;
}

.social-user {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e5e8ef;
  border-radius: 8px;
  background: #fff;
}

.social-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: #eef4ff;
  color: #2563eb;
  font-weight: 800;
  overflow: hidden;
  cursor: pointer;
}

.social-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.social-main {
  display: grid;
  gap: 4px;
  min-width: 0;
  cursor: pointer;
}

.social-main strong,
.social-main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.social-main span {
  color: #667085;
  font-size: 13px;
}

.profile-editor {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 14px;
  background: #fff;
  border: 1px solid #e5e8ef;
  border-radius: 8px;
}

.profile-editor input {
  flex: 1;
  min-width: 0;
  border: 1px solid #d9dfe8;
  border-radius: 6px;
  padding: 9px 11px;
  outline: none;
}

.profile-editor button,
.video-manage button {
  border: 0;
  border-radius: 6px;
  background: #1890ff;
  color: #fff;
  padding: 7px 11px;
  cursor: pointer;
  font-weight: 700;
}

.video-manage {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}

.visibility-tag {
  padding: 4px 8px;
  border-radius: 999px;
  background: #eef4ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.video-manage button.danger {
  background: #ff4757;
}

.video-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.video-tags span {
  max-width: 120px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eef4ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #e5e8ef;
}

.profile-tabs button {
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  padding: 12px 18px;
  color: #667085;
  cursor: pointer;
  font-size: 15px;
  font-weight: 700;
}

.profile-tabs button.active {
  border-bottom-color: #1890ff;
  color: #1890ff;
}

.creator-overview-card,
.creator-manage-card,
.creator-upload-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.creator-page {
  background: transparent;
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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #dff6ff, #fff0f6);
  color: #0b76b7;
  font-size: 28px;
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
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fbff, #fff);
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
  border-radius: 8px;
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
  border-radius: 8px;
  background: #fff;
}

.creator-manuscript-cover {
  position: relative;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
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

.creator-manuscript-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 10px;
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

.history-page {
  display: grid;
  gap: 18px;
}

.history-manuscript-item {
  grid-template-columns: 176px minmax(0, 1fr) 120px;
}

.outline-btn,
.danger-btn {
  border: 1px solid #d8e0ea;
  border-radius: 6px;
  padding: 8px 10px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  font-weight: 700;
}

.danger-btn {
  border-color: #ffd1d7;
  background: #fff5f6;
  color: #e11d48;
}

.danger-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.creator-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.auto-cover-preview {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  color: #667085;
  font-size: 12px;
}

.auto-cover-preview img {
  width: 96px;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid #e5e8ef;
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
  align-items: start;
}

.live-player-panel {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  cursor: pointer;
}

.live-player {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
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

.live-player-topbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px 40px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.62), transparent);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  pointer-events: none;
  opacity: 1;
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.live-player-topbar.hide-controls {
  opacity: 0;
  transform: translateY(-10px);
}

.live-player-topbar.show-controls {
  opacity: 1;
  transform: translateY(0);
}

.live-player-badge {
  padding: 4px 8px;
  border-radius: 999px;
  background: #ff4757;
  font-size: 12px;
  letter-spacing: 0;
}

.live-big-play-btn {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 0;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 32px;
  cursor: pointer;
  z-index: 60;
}

.live-big-play-btn:hover {
  background: rgba(24, 144, 255, 0.8);
}

.live-controls-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 55;
  padding: 40px 20px 20px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  opacity: 1;
  transform: translateY(0);
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.live-controls-overlay.hide-controls {
  opacity: 0;
  transform: translateY(18px);
  pointer-events: none;
}

.live-controls-overlay.show-controls {
  opacity: 1;
  transform: translateY(0);
}

.live-controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.live-left-controls,
.live-right-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.live-control-btn {
  border: 0;
  border-radius: 4px;
  padding: 6px;
  background: transparent;
  color: #fff;
  cursor: pointer;
  font-size: 16px;
  transition: background 0.2s;
}

.live-control-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.live-control-btn svg {
  width: 22px;
  height: 22px;
  display: block;
  fill: currentColor;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.icon-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
}

.live-volume-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.live-volume-slider {
  width: 96px;
  height: 4px;
  accent-color: #fff;
  cursor: pointer;
}

.quality-btn {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
}

.quality-btn svg {
  fill: none;
}

.quality-btn strong {
  font-size: 13px;
  line-height: 1;
}

.live-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4757;
  box-shadow: 0 0 0 4px rgba(255, 71, 87, 0.18);
}

.live-quality-control {
  position: relative;
}

.live-quality-dropdown {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  display: none;
  min-width: 82px;
  margin-bottom: 8px;
  padding: 8px 0;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.9);
  z-index: 100;
}

.live-quality-dropdown.show {
  display: block;
}

.live-quality-dropdown button {
  display: block;
  width: 100%;
  border: 0;
  padding: 8px 16px;
  background: transparent;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
  text-align: center;
}

.live-quality-dropdown button:hover {
  background: rgba(255, 255, 255, 0.1);
}

.live-quality-dropdown button.active {
  color: #1890ff;
  font-weight: 700;
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
  .creator-dashboard {
    grid-template-columns: 1fr;
  }

  .creator-overview-card,
  .creator-manage-card,
  .creator-upload-card {
    grid-column: 1;
  }

  .creator-upload-card {
    grid-row: auto;
    position: static;
  }

  .creator-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .creator-manuscript-item {
    grid-template-columns: 140px minmax(0, 1fr);
  }

  .creator-manuscript-actions {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .live-room-layout {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .creator-stat-grid,
  .creator-manuscript-item,
  .creator-manuscript-actions {
    grid-template-columns: 1fr;
  }

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
.live-danmu-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 45;
}

.live-danmu-float {
  position: absolute;
  left: 100%;
  max-width: 70%;
  white-space: nowrap;
  font-size: 22px;
  line-height: 1.35;
  font-weight: 700;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.9);
  pointer-events: none;
  will-change: transform;
}

.live-danmu-self {
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 4px;
  padding: 2px 6px;
}

.live-chat-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  max-height: 100%;
  min-height: 320px;
  background: #fff;
  border: 1px solid #e4e8ed;
  border-radius: 8px;
  overflow: hidden;
}

.live-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid #eef1f4;
}

.live-chat-header h3 {
  margin: 0 0 3px;
  font-size: 15px;
}

.live-chat-header p {
  margin: 0;
  color: #8a94a3;
  font-size: 12px;
}

.live-like-btn {
  flex-shrink: 0;
  border: 0;
  border-radius: 999px;
  background: #ff4757;
  color: #fff;
  padding: 7px 12px;
  cursor: pointer;
  font-weight: 700;
}

.live-message-list {
  flex: 1;
  min-height: 0;
  max-height: 100%;
  padding: 10px 12px;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: #f8fafc;
}

.live-message-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.45;
}

.live-message-user {
  flex-shrink: 0;
  color: #1890ff;
  font-weight: 700;
}

.live-message-content {
  min-width: 0;
  word-break: break-word;
  text-shadow: 0 1px 1px rgba(255, 255, 255, 0.75);
}

.live-message-empty {
  padding: 24px 0;
  color: #8a94a3;
  text-align: center;
  font-size: 13px;
}

.live-chat-input {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #eef1f4;
  background: #fff;
}

.live-color-picker {
  width: 28px;
  height: 28px;
  align-self: center;
  border-radius: 50%;
  border: 2px solid #d8dde3;
  cursor: pointer;
  overflow: hidden;
}

.live-color-picker input {
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.live-chat-input input[type='text'] {
  min-width: 0;
  border: 1px solid #d8dde3;
  border-radius: 999px;
  padding: 7px 11px;
  outline: none;
}

.live-chat-input input[type='text']:focus {
  border-color: #1890ff;
}

.live-chat-input button {
  border: 0;
  border-radius: 999px;
  background: #1890ff;
  color: #fff;
  padding: 7px 13px;
  cursor: pointer;
  font-weight: 700;
}

@media screen and (max-width: 960px) {
  .live-chat-panel {
    height: 360px;
    max-height: 360px;
  }
}
</style>
