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


-- 直播间表
CREATE TABLE live_room (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  stream_name VARCHAR(100) NOT NULL UNIQUE,
  push_url VARCHAR(255) NOT NULL,
  play_url VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'offline',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 点赞数
CREATE TABLE IF NOT EXISTS `room_likes` (
  `room_id` varchar(50) NOT NULL,
  `like_count` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`room_id`)
);

-- 历史弹幕
CREATE TABLE IF NOT EXISTS `danmu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` varchar(50) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(50) DEFAULT NULL,
  `content` varchar(255) NOT NULL,
  `color` varchar(20) DEFAULT '#ffffff',
  `send_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room_id` (`room_id`)
);