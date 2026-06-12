package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.dto.UserProfileUpdateDTO;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import com.example.demo.vo.LoginUserVO;
import com.example.demo.vo.UserProfileVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JdbcTemplate jdbcTemplate;

    public UserServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void register(UserLoginDTO dto) {
        QueryWrapper<User> usernameWrapper = new QueryWrapper<>();
        usernameWrapper.eq("username", dto.getUsername());
        if (this.count(usernameWrapper) > 0) {
            throw new RuntimeException("用户名已被注册");
        }

        QueryWrapper<User> nicknameWrapper = new QueryWrapper<>();
        nicknameWrapper.eq("nickname", dto.getNickname());
        if (dto.getNickname() != null && !dto.getNickname().isBlank() && this.count(nicknameWrapper) > 0) {
            throw new RuntimeException("该昵称已被使用");
        }

        User newUser = new User();
        newUser.setUsername(dto.getUsername());
        newUser.setPassword(dto.getPassword());
        newUser.setNickname(dto.getNickname());
        this.save(newUser);
    }

    @Override
    public LoginUserVO login(UserLoginDTO dto) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername());
        User user = this.getOne(queryWrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!user.getPassword().equals(dto.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        return toLoginVO(user);
    }

    @Override
    public UserProfileVO getProfile(Long targetUserId, Long currentUserId) {
        User user = this.getById(targetUserId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserProfileVO profile = toProfileVO(user);
        profile.setFollowingCount(countFollowing(targetUserId));
        profile.setFollowerCount(countFollowers(targetUserId));
        if (currentUserId != null) {
            profile.setFollowing(isFollowing(currentUserId, targetUserId));
        } else {
            profile.setFollowing(false);
        }
        return profile;
    }

    @Override
    public UserProfileVO updateProfile(Long currentUserId, UserProfileUpdateDTO dto) {
        User user = this.getById(currentUserId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (dto.getNickname() != null && !dto.getNickname().isBlank() && !dto.getNickname().equals(user.getNickname())) {
            QueryWrapper<User> nicknameWrapper = new QueryWrapper<>();
            nicknameWrapper.eq("nickname", dto.getNickname());
            if (this.count(nicknameWrapper) > 0) {
                throw new RuntimeException("该昵称已被使用");
            }
            user.setNickname(dto.getNickname().trim());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl().trim());
        }
        if (dto.getBio() != null) {
            user.setBio(dto.getBio().trim());
        }

        this.updateById(user);
        return getProfile(currentUserId, currentUserId);
    }

    private Long countFollowing(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        addFollowIds(ids, "follow_user_id", "user_id", userId);
        addFollowIds(ids, "followee_id", "user_id", userId);
        addFollowIds(ids, "following_id", "user_id", userId);
        addFollowIds(ids, "follow_user_id", "follower_id", userId);
        addFollowIds(ids, "followee_id", "follower_id", userId);
        addFollowIds(ids, "following_id", "follower_id", userId);
        ids.remove(userId);
        ids.remove(null);
        return (long) ids.size();
    }

    private Long countFollowers(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        addFollowIds(ids, "user_id", "follow_user_id", userId);
        addFollowIds(ids, "user_id", "followee_id", userId);
        addFollowIds(ids, "user_id", "following_id", userId);
        addFollowIds(ids, "follower_id", "follow_user_id", userId);
        addFollowIds(ids, "follower_id", "followee_id", userId);
        addFollowIds(ids, "follower_id", "following_id", userId);
        ids.remove(userId);
        ids.remove(null);
        return (long) ids.size();
    }

    private boolean isFollowing(Long userId, Long targetUserId) {
        return relationExists("user_id", "follow_user_id", userId, targetUserId)
                || relationExists("user_id", "followee_id", userId, targetUserId)
                || relationExists("user_id", "following_id", userId, targetUserId)
                || relationExists("follower_id", "follow_user_id", userId, targetUserId)
                || relationExists("follower_id", "followee_id", userId, targetUserId)
                || relationExists("follower_id", "following_id", userId, targetUserId);
    }

    private void addFollowIds(Set<Long> ids, String selectColumn, String whereColumn, Long userId) {
        if (!columnExists(selectColumn) || !columnExists(whereColumn)) {
            return;
        }
        ids.addAll(jdbcTemplate.queryForList(
                "SELECT DISTINCT " + selectColumn + " FROM user_follow WHERE " + whereColumn + " = ? AND " + selectColumn + " IS NOT NULL",
                Long.class,
                userId
        ));
    }

    private boolean relationExists(String followerColumn, String targetColumn, Long userId, Long targetUserId) {
        if (!columnExists(followerColumn) || !columnExists(targetColumn)) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE " + followerColumn + " = ? AND " + targetColumn + " = ?",
                Long.class,
                userId,
                targetUserId
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'user_follow' AND column_name = ?",
                Integer.class,
                columnName
        );
        return count != null && count > 0;
    }

    private LoginUserVO toLoginVO(User user) {
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setBio(user.getBio());
        return vo;
    }

    private UserProfileVO toProfileVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setBio(user.getBio());
        return vo;
    }
}
