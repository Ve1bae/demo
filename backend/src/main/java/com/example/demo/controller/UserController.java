package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.common.ApiResponse;
import com.example.demo.dto.TagCreateDTO;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.dto.UserProfileUpdateDTO;
import com.example.demo.entity.Notification;
import com.example.demo.entity.Tag;
import com.example.demo.entity.User;
import com.example.demo.entity.ViewHistory;
import com.example.demo.entity.Video;
import com.example.demo.mapper.NotificationMapper;
import com.example.demo.mapper.TagMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.ViewHistoryMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.UserService;
import com.example.demo.vo.UserProfileVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Value("${app.upload-dir:backend/uploads}")
    private String uploadDir;

    private final UserService userService;
    private final UserMapper userMapper;
    private final ViewHistoryMapper viewHistoryMapper;
    private final VideoMapper videoMapper;
    private final TagMapper tagMapper;
    private final NotificationMapper notificationMapper;
    private final JdbcTemplate jdbcTemplate;

    public UserController(
            UserService userService,
            UserMapper userMapper,
            ViewHistoryMapper viewHistoryMapper,
            VideoMapper videoMapper,
            TagMapper tagMapper,
            NotificationMapper notificationMapper,
            JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.viewHistoryMapper = viewHistoryMapper;
        this.videoMapper = videoMapper;
        this.tagMapper = tagMapper;
        this.notificationMapper = notificationMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/register")
    public Object register(@RequestBody UserLoginDTO dto) {
        try {
            userService.register(dto);
            return "注册成功";
        } catch (Exception e) {
            return "注册失败: " + e.getMessage();
        }
    }

    @PostMapping("/login")
    public Object login(@RequestBody UserLoginDTO dto) {
        try {
            return userService.login(dto);
        } catch (Exception e) {
            return "登录失败: " + e.getMessage();
        }
    }

    @GetMapping("/profile/{userId}")
    public ApiResponse<UserProfileVO> getProfile(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        try {
            return ApiResponse.success(userService.getProfile(userId, currentUserId));
        } catch (Exception e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    @GetMapping("/profile/by-name")
    public ApiResponse<UserProfileVO> getProfileByName(
            @RequestParam String name,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        if (!StringUtils.hasText(name)) {
            return ApiResponse.fail(400, "用户名不能为空");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("nickname", name.trim()).or().eq("username", name.trim());
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            return ApiResponse.fail(404, "用户不存在");
        }
        return ApiResponse.success(userService.getProfile(user.getId(), currentUserId));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileVO> updateProfile(
            @RequestBody UserProfileUpdateDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        if (currentUserId == null || userMapper.selectById(currentUserId) == null) {
            return ApiResponse.fail(401, "请先登录后编辑个人资料");
        }
        try {
            return ApiResponse.success(userService.updateProfile(currentUserId, dto));
        } catch (Exception e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/profile/avatar")
    public ApiResponse<UserProfileVO> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ApiResponse.fail(401, "请先登录后上传头像");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(400, "请选择头像文件");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!isAllowedImageExtension(extension)) {
            return ApiResponse.fail(400, "仅支持 png、jpg、jpeg、webp、gif 格式头像");
        }

        try {
            String storedName = "avatar-" + currentUserId + "-" + UUID.randomUUID() + extension;
            Path avatarDir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            UserProfileUpdateDTO dto = new UserProfileUpdateDTO();
            dto.setAvatarUrl("http://localhost:8080/uploads/avatars/" + storedName);
            return ApiResponse.success(userService.updateProfile(currentUserId, dto));
        } catch (IOException e) {
            return ApiResponse.fail(500, "头像上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/{userId}/follow")
    public ApiResponse<String> followUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ApiResponse.fail(401, "请先登录后关注用户");
        }
        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            return ApiResponse.fail(404, "目标用户不存在");
        }
        if (currentUserId.equals(userId)) {
            return ApiResponse.fail(400, "不能关注自己");
        }

        if (followRelationExists(currentUserId, userId)) {
            return ApiResponse.fail(400, "已经关注过该用户");
        }

        insertFollowRelation(currentUserId, userId);

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("follow");
        notification.setContent((currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername()) + " 关注了你");
        notification.setRelatedUserId(currentUserId);
        notification.setIsRead(false);
        notificationMapper.insert(notification);

        return ApiResponse.success("关注成功", null);
    }

    @DeleteMapping("/{userId}/follow")
    public ApiResponse<String> unfollowUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ApiResponse.fail(401, "请先登录后取消关注");
        }

        if (!followRelationExists(currentUserId, userId)) {
            return ApiResponse.fail(404, "尚未关注该用户");
        }

        deleteFollowRelation(currentUserId, userId);
        return ApiResponse.success("取消关注成功", null);
    }

    @GetMapping("/{userId}/following")
    public ApiResponse<List<UserProfileVO>> getFollowingList(@PathVariable Long userId) {
        Set<Long> followUserIds = selectFollowingUserIds(userId);
        List<UserProfileVO> result = new ArrayList<>();
        for (Long followUserId : followUserIds) {
            User targetUser = userMapper.selectById(followUserId);
            if (targetUser != null) {
                result.add(userService.getProfile(targetUser.getId(), userId));
            }
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<List<UserProfileVO>> getFollowerList(@PathVariable Long userId) {
        Set<Long> followerUserIds = selectFollowerUserIds(userId);
        List<UserProfileVO> result = new ArrayList<>();
        for (Long followerUserId : followerUserIds) {
            User targetUser = userMapper.selectById(followerUserId);
            if (targetUser != null) {
                result.add(userService.getProfile(targetUser.getId(), userId));
            }
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{userId}/history")
    public ApiResponse<List<ViewHistory>> getViewHistory(@PathVariable Long userId) {
        QueryWrapper<ViewHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).orderByDesc("last_viewed_at");
        List<ViewHistory> histories = viewHistoryMapper.selectList(queryWrapper);
        histories.forEach(history -> {
            Video video = videoMapper.selectById(history.getVideoId());
            history.setVideo(video);
        });
        return ApiResponse.success(histories);
    }

    @GetMapping("/tags")
    public ApiResponse<List<Tag>> getTags() {
        QueryWrapper<Tag> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("name");
        return ApiResponse.success(tagMapper.selectList(queryWrapper));
    }

    @PostMapping("/tags")
    public ApiResponse<Tag> createTag(
            @RequestBody TagCreateDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ApiResponse.fail(401, "请先登录后创建标签");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            return ApiResponse.fail(400, "标签名称不能为空");
        }

        QueryWrapper<Tag> existingWrapper = new QueryWrapper<>();
        existingWrapper.eq("name", dto.getName().trim());
        Tag existing = tagMapper.selectOne(existingWrapper);
        if (existing != null) {
            return ApiResponse.success(existing);
        }

        Tag tag = new Tag();
        tag.setName(dto.getName().trim());
        tag.setCreatorUserId(currentUserId);
        tagMapper.insert(tag);
        return ApiResponse.success("标签创建成功", tag);
    }

    @GetMapping("/notifications")
    public ApiResponse<List<Notification>> getNotifications(
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ApiResponse.fail(401, "请先登录后查看通知");
        }

        QueryWrapper<Notification> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId).orderByDesc("created_at");
        return ApiResponse.success(notificationMapper.selectList(queryWrapper));
    }

    @PutMapping("/notifications/{notificationId}/read")
    public ApiResponse<String> markNotificationRead(
            @PathVariable Long notificationId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ApiResponse.fail(401, "请先登录后操作通知");
        }

        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !currentUserId.equals(notification.getUserId())) {
            return ApiResponse.fail(404, "通知不存在");
        }

        notification.setIsRead(true);
        notificationMapper.updateById(notification);
        return ApiResponse.success("通知已标记为已读", null);
    }

    private User validateCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userMapper.selectById(currentUserId);
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex).toLowerCase() : "";
    }

    private boolean isAllowedImageExtension(String extension) {
        return ".png".equals(extension)
                || ".jpg".equals(extension)
                || ".jpeg".equals(extension)
                || ".webp".equals(extension)
                || ".gif".equals(extension);
    }

    private void insertFollowRelation(Long currentUserId, Long targetUserId) {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        addFollowColumn(columns, values, "user_id", currentUserId);
        addFollowColumn(columns, values, "follow_user_id", targetUserId);
        addFollowColumn(columns, values, "follower_id", currentUserId);
        addFollowColumn(columns, values, "followee_id", targetUserId);
        addFollowColumn(columns, values, "following_id", targetUserId);

        String placeholders = String.join(",", columns.stream().map(column -> "?").toList());
        String sql = "INSERT INTO user_follow (" + String.join(",", columns) + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, values.toArray());
    }

    private void addFollowColumn(List<String> columns, List<Object> values, String columnName, Long value) {
        if (columnExists("user_follow", columnName)) {
            columns.add(columnName);
            values.add(value);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private Set<Long> selectFollowingUserIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        addIds(ids, "follow_user_id", "user_id", userId);
        addIds(ids, "follow_user_id", "follower_id", userId);
        addIds(ids, "followee_id", "user_id", userId);
        addIds(ids, "followee_id", "follower_id", userId);
        addIds(ids, "following_id", "user_id", userId);
        addIds(ids, "following_id", "follower_id", userId);
        ids.remove(null);
        ids.remove(userId);
        return ids;
    }

    private Set<Long> selectFollowerUserIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        addIds(ids, "user_id", "follow_user_id", userId);
        addIds(ids, "user_id", "followee_id", userId);
        addIds(ids, "user_id", "following_id", userId);
        addIds(ids, "follower_id", "follow_user_id", userId);
        addIds(ids, "follower_id", "followee_id", userId);
        addIds(ids, "follower_id", "following_id", userId);
        ids.remove(null);
        ids.remove(userId);
        return ids;
    }

    private void addIds(Set<Long> ids, String selectColumn, String whereColumn, Long userId) {
        if (!columnExists("user_follow", selectColumn) || !columnExists("user_follow", whereColumn)) {
            return;
        }
        ids.addAll(jdbcTemplate.queryForList(
                "SELECT " + selectColumn + " FROM user_follow WHERE " + whereColumn + " = ? AND " + selectColumn + " IS NOT NULL ORDER BY created_at DESC",
                Long.class,
                userId
        ));
    }

    private boolean followRelationExists(Long currentUserId, Long targetUserId) {
        return countFollowRelation("user_id", "follow_user_id", currentUserId, targetUserId) > 0
                || countFollowRelation("user_id", "followee_id", currentUserId, targetUserId) > 0
                || countFollowRelation("user_id", "following_id", currentUserId, targetUserId) > 0
                || countFollowRelation("follower_id", "follow_user_id", currentUserId, targetUserId) > 0
                || countFollowRelation("follower_id", "followee_id", currentUserId, targetUserId) > 0
                || countFollowRelation("follower_id", "following_id", currentUserId, targetUserId) > 0;
    }

    private int countFollowRelation(String followerColumn, String targetColumn, Long currentUserId, Long targetUserId) {
        if (!columnExists("user_follow", followerColumn) || !columnExists("user_follow", targetColumn)) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE " + followerColumn + " = ? AND " + targetColumn + " = ?",
                Integer.class,
                currentUserId,
                targetUserId
        );
        return count == null ? 0 : count;
    }

    private void deleteFollowRelation(Long currentUserId, Long targetUserId) {
        deleteFollowRelationByColumns("user_id", "follow_user_id", currentUserId, targetUserId);
        deleteFollowRelationByColumns("user_id", "followee_id", currentUserId, targetUserId);
        deleteFollowRelationByColumns("user_id", "following_id", currentUserId, targetUserId);
        deleteFollowRelationByColumns("follower_id", "follow_user_id", currentUserId, targetUserId);
        deleteFollowRelationByColumns("follower_id", "followee_id", currentUserId, targetUserId);
        deleteFollowRelationByColumns("follower_id", "following_id", currentUserId, targetUserId);
    }

    private void deleteFollowRelationByColumns(String followerColumn, String targetColumn, Long currentUserId, Long targetUserId) {
        if (!columnExists("user_follow", followerColumn) || !columnExists("user_follow", targetColumn)) {
            return;
        }
        jdbcTemplate.update(
                "DELETE FROM user_follow WHERE " + followerColumn + " = ? AND " + targetColumn + " = ?",
                currentUserId,
                targetUserId
        );
    }
}
