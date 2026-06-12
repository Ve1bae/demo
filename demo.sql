-- Combined schema for live + videoplayer

-- 1. User table
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `username` varchar(50) NOT NULL COMMENT 'Username',
  `password` varchar(100) NOT NULL COMMENT 'Encrypted password',
  `nickname` varchar(50) DEFAULT NULL COMMENT 'Nickname',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT 'Avatar URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User table';

-- 2. Live room table
CREATE TABLE `live_room` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `category_id` bigint(20) DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `stream_name` varchar(100) NOT NULL,
  `push_url` varchar(255) NOT NULL,
  `play_url` varchar(255) NOT NULL,
  `cover_url` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'offline',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_live_room_stream_name` (`stream_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Live room table';

-- 3. Video table
CREATE TABLE `video` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Video ID',
  `title` varchar(255) NOT NULL COMMENT 'Video title',
  `description` text DEFAULT NULL COMMENT 'Video description',
  `cover_url` varchar(500) DEFAULT NULL COMMENT 'Cover URL',
  `play_url` varchar(500) DEFAULT NULL COMMENT 'Primary play URL',
  `author` varchar(100) DEFAULT NULL COMMENT 'Author name',
  `user_id` bigint(20) DEFAULT NULL COMMENT 'Uploader user ID',
  `category_id` int(11) DEFAULT NULL COMMENT 'Category ID',
  `duration` int(11) DEFAULT NULL COMMENT 'Duration in seconds',
  `status` varchar(20) DEFAULT 'public' COMMENT 'Video status',
  `play_count` int(11) DEFAULT '0' COMMENT 'Play count',
  `like_count` int(11) DEFAULT '0' COMMENT 'Like count',
  `favorite_count` int(11) DEFAULT '0' COMMENT 'Favorite count',
  `comment_count` int(11) DEFAULT '0' COMMENT 'Comment count',
  `video_url` varchar(500) DEFAULT NULL COMMENT 'Original video URL',
  `url_240p` varchar(500) DEFAULT NULL,
  `url_360p` varchar(500) DEFAULT NULL,
  `url_480p` varchar(500) DEFAULT NULL,
  `url_720p` varchar(500) DEFAULT NULL,
  `url_1080p` varchar(500) DEFAULT NULL,
  `default_quality` varchar(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`),
  KEY `idx_video_created_at` (`created_at`),
  KEY `idx_video_video_url` (`video_url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Video table';

-- 4. Danmaku table
CREATE TABLE `danmaku` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Danmaku ID',
  `video_url` varchar(500) NOT NULL COMMENT 'Related video URL',
  `content` text NOT NULL COMMENT 'Danmaku content',
  `color` varchar(20) DEFAULT '#ffffff' COMMENT 'Danmaku color',
  `time` double NOT NULL COMMENT 'Danmaku time in seconds',
  `user_id` varchar(100) NOT NULL COMMENT 'Sender ID',
  `is_user` tinyint(1) DEFAULT '0' COMMENT 'Whether sent by current user',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  KEY `idx_danmaku_video_url` (`video_url`),
  KEY `idx_danmaku_user_id` (`user_id`),
  KEY `idx_danmaku_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Danmaku table';

-- 5. Comment table
CREATE TABLE `comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Comment ID',
  `video_id` bigint(20) NOT NULL COMMENT 'Related video ID',
  `user_id` bigint(20) NOT NULL COMMENT 'Comment user ID',
  `content` text NOT NULL COMMENT 'Comment content',
  `parent_id` bigint(20) DEFAULT NULL COMMENT 'Parent comment ID',
  `like_count` int(11) DEFAULT '0' COMMENT 'Like count',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  KEY `idx_comment_video_id` (`video_id`),
  KEY `idx_comment_user_id` (`user_id`),
  KEY `idx_comment_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Comment table';

-- 6. User-video relation table
CREATE TABLE `user_video` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_id` bigint(20) NOT NULL COMMENT 'User ID',
  `video_id` bigint(20) NOT NULL COMMENT 'Video ID',
  `liked` tinyint(1) DEFAULT '0' COMMENT 'Liked flag',
  `favorited` tinyint(1) DEFAULT '0' COMMENT 'Favorited flag',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
  KEY `idx_user_video_user_id` (`user_id`),
  KEY `idx_user_video_video_id` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User video relation table';

-- Live interaction like counts
CREATE TABLE IF NOT EXISTS `room_likes` (
  `room_id` bigint(20) NOT NULL COMMENT 'Live room ID',
  `like_count` bigint(20) NOT NULL DEFAULT '0' COMMENT 'Live room like count',
  PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Live room like count table';

-- Live interaction danmu history
CREATE TABLE IF NOT EXISTS `live_danmu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Live danmu ID',
  `room_id` bigint(20) NOT NULL COMMENT 'Live room ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT 'Sender user ID',
  `username` varchar(50) DEFAULT NULL COMMENT 'Sender display name',
  `content` varchar(255) NOT NULL COMMENT 'Danmu content',
  `color` varchar(20) DEFAULT '#ffffff' COMMENT 'Danmu color',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Send time',
  PRIMARY KEY (`id`),
  KEY `idx_live_danmu_room_time` (`room_id`, `send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Live room danmu table';
