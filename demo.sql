-- 弹幕数据库建表脚本

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

-- 2. 弹幕表 (Danmaku)
CREATE TABLE `danmaku` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '弹幕ID',
  `video_url` varchar(500) NOT NULL COMMENT '关联视频URL',
  `content` text NOT NULL COMMENT '弹幕内容',
  `color` varchar(20) DEFAULT '#ffffff' COMMENT '弹幕颜色(十六进制)',
  `time` double NOT NULL COMMENT '弹幕时间点(秒)',
  `user_id` varchar(100) NOT NULL COMMENT '发送者ID',
  `is_user` tinyint(1) DEFAULT '0' COMMENT '是否为当前用户发送',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_video_url` (`video_url`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弹幕表';

-- 3. 评论表 (Comment)
CREATE TABLE `comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `video_id` bigint(20) NOT NULL COMMENT '关联视频ID',
  `user_id` bigint(20) NOT NULL COMMENT '评论用户ID',
  `content` text NOT NULL COMMENT '评论内容',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父评论ID（用于回复）',
  `like_count` int(11) DEFAULT '0' COMMENT '点赞数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_video_id` (`video_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';