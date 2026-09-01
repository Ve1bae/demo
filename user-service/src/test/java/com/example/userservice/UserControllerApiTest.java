package com.example.userservice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.userservice.controller.UserController;
import com.example.userservice.dto.UserLoginDTO;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserFollow;
import com.example.userservice.mapper.UserFollowMapper;
import com.example.userservice.service.UserAccountService;
import com.example.userservice.vo.LoginUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UserControllerApiTest {
    private UserAccountService accounts;
    private UserFollowMapper follows;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountService.class);
        follows = mock(UserFollowMapper.class);
        when(accounts.getById(anyLong())).thenReturn(user(1L, "alice"));
        when(follows.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(follows.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        mvc = standaloneSetup(new UserController(accounts, follows)).build();
    }

    @Test void registerReturnsSuccess() throws Exception {
        mvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\",\"password\":\"secret\",\"nickname\":\"Alice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test void loginReturnsAccountSummary() throws Exception {
        LoginUserVO login = new LoginUserVO(); login.setId(1L); login.setUsername("alice"); login.setNickname("Alice");
        when(accounts.login(any(UserLoginDTO.class))).thenReturn(login);
        mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1));
    }

    @Test void profileReturnsUserAndFollowCounts() throws Exception {
        mvc.perform(get("/api/user/1/profile").param("viewerId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("alice")).andExpect(jsonPath("$.data.followingCount").value(0));
    }

    @Test void updateAvatarReturnsChangedUrl() throws Exception {
        mvc.perform(put("/api/user/1/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarUrl\":\"https://example.test/a.png\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/a.png"));
    }

    @Test void followAndUnfollowReturnSuccess() throws Exception {
        when(follows.insert(any(UserFollow.class))).thenReturn(1);
        mvc.perform(post("/api/user/2/follow").header("X-User-Id", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("关注成功"));
        mvc.perform(delete("/api/user/2/follow").header("X-User-Id", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("取消关注成功"));
    }

    @Test void followingAndFollowersReturnLists() throws Exception {
        mvc.perform(get("/api/user/1/following")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/user/1/followers")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    @Test void internalLookupReturnsUserSummary() throws Exception {
        mvc.perform(get("/api/user/internal/1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("Alice"));
    }

    @Test void missingProfileReturnsBusinessNotFound() throws Exception {
        when(accounts.getById(99L)).thenReturn(null);
        mvc.perform(get("/api/user/99/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
    }

    private User user(Long id, String username) {
        User user = new User(); user.setId(id); user.setUsername(username); user.setNickname("Alice"); user.setAvatarUrl("https://example.test/a.png"); return user;
    }
}
