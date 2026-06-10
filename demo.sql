-- 1. 用户表 (User)
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '加密后的密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像地址(存在MinIO中)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基本信息表';

-- 2. 视频表 (Video)
CREATE TABLE `video` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '视频ID',
  `user_id` bigint(20) NOT NULL COMMENT '上传者用户ID',
  `title` varchar(200) NOT NULL COMMENT '视频标题',
  `description` text DEFAULT NULL COMMENT '视频描述',
  `category_id` bigint(20) DEFAULT 0 COMMENT '分类ID',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面地址',
  `play_url` varchar(500) NOT NULL COMMENT '视频播放地址',
  `duration` int(11) DEFAULT 0 COMMENT '视频时长(秒)',
  `play_count` bigint(20) DEFAULT 0 COMMENT '播放量',
  `like_count` bigint(20) DEFAULT 0 COMMENT '点赞数',
  `favorite_count` bigint(20) DEFAULT 0 COMMENT '收藏数',
  `comment_count` bigint(20) DEFAULT 0 COMMENT '评论数',
  `status` varchar(20) DEFAULT 'uploading' COMMENT '状态: uploading/processing/published/failed',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频表';
