package com.example.userservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.userservice.common.ApiResponse;
import com.example.userservice.dto.UserLoginDTO;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserFollow;
import com.example.userservice.entity.UserInterest;
import com.example.userservice.mapper.UserFollowMapper;
import com.example.userservice.mapper.UserInterestMapper;
import com.example.userservice.service.UserAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping({"/api/user", "/api/users"})
public class UserController {
    private final UserAccountService accounts;
    private final UserFollowMapper follows;
    private final UserInterestMapper interests;
    public UserController(UserAccountService accounts, UserFollowMapper follows) {
        this(accounts, follows, null);
    }
    @Autowired
    public UserController(UserAccountService accounts, UserFollowMapper follows, UserInterestMapper interests) {
        this.accounts = accounts; this.follows = follows; this.interests = interests;
    }

    @PostMapping("/register") public ApiResponse<String> register(@RequestBody UserLoginDTO dto) { try { accounts.register(dto); return ApiResponse.success("注册成功", null); } catch (IllegalArgumentException e) { return ApiResponse.error(400, e.getMessage()); } }
    @PostMapping("/login") public ApiResponse<?> login(@RequestBody UserLoginDTO dto) { try { return ApiResponse.success(accounts.login(dto)); } catch (IllegalArgumentException e) { return ApiResponse.error(401, e.getMessage()); } }
    @GetMapping("/{userId}/profile") public ApiResponse<Map<String,Object>> profile(@PathVariable Long userId, @RequestParam(required=false) Long viewerId) {
        User user = accounts.getById(userId); if (user == null) return ApiResponse.error(404, "用户不存在");
        Map<String,Object> result = summary(user, viewerId); result.put("followingCount", countFollowing(userId)); result.put("followerCount", countFollowers(userId)); result.put("following", viewerId != null && isFollowing(viewerId, userId)); return ApiResponse.success(result);
    }
    @PutMapping("/{userId}/avatar") public ApiResponse<Map<String,Object>> avatar(@PathVariable Long userId, @RequestBody Map<String,Object> body) { User user=accounts.getById(userId); if(user==null)return ApiResponse.error(404,"用户不存在"); user.setAvatarUrl(Objects.toString(body.get("avatarUrl"),"")); accounts.updateById(user); return ApiResponse.success(Map.of("avatarUrl", user.getAvatarUrl())); }
    @PostMapping("/{userId}/follow") public ApiResponse<String> follow(@PathVariable Long userId,@RequestHeader(value="X-User-Id",required=false) Long viewer){ if(viewer==null||accounts.getById(viewer)==null)return ApiResponse.error(401,"请先登录后关注用户"); if(Objects.equals(viewer,userId))return ApiResponse.error(400,"不能关注自己"); if(accounts.getById(userId)==null)return ApiResponse.error(404,"目标用户不存在"); UserFollow f=new UserFollow();f.setUserId(viewer);f.setFollowUserId(userId);try{follows.insert(f);}catch(DuplicateKeyException e){return ApiResponse.success("已经关注过该用户",null);}return ApiResponse.success("关注成功",null); }
    @DeleteMapping("/{userId}/follow") public ApiResponse<String> unfollow(@PathVariable Long userId,@RequestHeader(value="X-User-Id",required=false) Long viewer){if(viewer==null)return ApiResponse.error(401,"请先登录后取消关注"); follows.delete(new QueryWrapper<UserFollow>().eq("user_id",viewer).eq("follow_user_id",userId));return ApiResponse.success("取消关注成功",null);}
    @GetMapping("/{userId}/following") public ApiResponse<List<Map<String,Object>>> following(@PathVariable Long userId){return ApiResponse.success(list(userId,true));}
    @GetMapping("/{userId}/followers") public ApiResponse<List<Map<String,Object>>> followers(@PathVariable Long userId){return ApiResponse.success(list(userId,false));}
    @GetMapping("/{userId}/preferences")
    public ApiResponse<Map<String,Object>> preferences(@PathVariable Long userId) {
        if (accounts.getById(userId) == null) return ApiResponse.error(404, "用户不存在");
        Set<Long> followedAuthorIds = follows.selectList(new QueryWrapper<UserFollow>().eq("user_id", userId))
                .stream().map(UserFollow::getFollowUserId).collect(java.util.stream.Collectors.toSet());
        Map<String,Integer> preferenceInterests = new LinkedHashMap<>();
        if (interests != null) {
            interests.selectList(new QueryWrapper<UserInterest>().eq("user_id", userId).orderByDesc("score"))
                    .forEach(item -> preferenceInterests.put(item.getTag(), item.getScore()));
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("followedAuthorIds", followedAuthorIds);
        result.put("interests", preferenceInterests);
        result.put("viewedVideoIds", Set.of());
        return ApiResponse.success(result);
    }
    @GetMapping("/internal/{userId}") public ApiResponse<Map<String,Object>> internal(@PathVariable Long userId){User u=accounts.getById(userId);return u==null?ApiResponse.error(404,"用户不存在"):ApiResponse.success(summary(u,null));}
    private List<Map<String,Object>> list(Long id,boolean out){return follows.selectList(new QueryWrapper<UserFollow>().eq(out?"user_id":"follow_user_id",id).orderByDesc("created_at")).stream().map(f->summary(accounts.getById(out?f.getFollowUserId():f.getUserId()),id)).filter(Objects::nonNull).toList();}
    private Map<String,Object> summary(User u,Long viewer){if(u==null)return null;Map<String,Object> m=new HashMap<>();m.put("id",u.getId());m.put("userId",u.getId());m.put("username",u.getUsername());m.put("nickname",u.getNickname());m.put("avatarUrl",u.getAvatarUrl());m.put("bio",u.getBio());if(viewer!=null)m.put("following",isFollowing(viewer,u.getId()));return m;}
    private long countFollowing(Long id){return follows.selectCount(new QueryWrapper<UserFollow>().eq("user_id",id));} private long countFollowers(Long id){return follows.selectCount(new QueryWrapper<UserFollow>().eq("follow_user_id",id));} private boolean isFollowing(Long a,Long b){return !Objects.equals(a,b)&&follows.selectCount(new QueryWrapper<UserFollow>().eq("user_id",a).eq("follow_user_id",b))>0;}
}
