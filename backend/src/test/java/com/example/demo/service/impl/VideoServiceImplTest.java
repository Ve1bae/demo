package com.example.demo.service.impl;

import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserFollowMapper;
import com.example.demo.mapper.UserInterestMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.UserVideoMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.mapper.ViewHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试：VideoServiceImpl（UC-02 管理个人视频 核心业务规则）
 *
 * 说明：
 * 1. 纯 Mockito 单元测试，不启动 Spring 容器、不连接 MySQL/MinIO，直接运行即可通过；
 * 2. 覆盖业务规则：删除归属校验（本人/非本人/不存在/已删除）、删除失败的异常分支、
 *    可见性设置、本人视频列表查询与字段装饰（播放量格式化）；
 * 3. 每个测试都有断言（assertEquals/assertTrue/assertFalse），不是只看有没有报错。
 */
class VideoServiceImplTest {

    private VideoMapper videoMapper;
    private UserVideoMapper userVideoMapper;
    private UserMapper userMapper;
    private UserFollowMapper userFollowMapper;
    private UserInterestMapper userInterestMapper;
    private ViewHistoryMapper viewHistoryMapper;

    private VideoServiceImpl videoService;

    @BeforeEach
    void setUp() {
        videoMapper = mock(VideoMapper.class);
        userVideoMapper = mock(UserVideoMapper.class);
        userMapper = mock(UserMapper.class);
        userFollowMapper = mock(UserFollowMapper.class);
        userInterestMapper = mock(UserInterestMapper.class);
        viewHistoryMapper = mock(ViewHistoryMapper.class);

        videoService = new VideoServiceImpl(
                userVideoMapper, userMapper, userFollowMapper, userInterestMapper, viewHistoryMapper);
        // ServiceImpl 的 baseMapper 由 MyBatis Plus 框架注入，这里手动注入 Mock
        ReflectionTestUtils.setField(videoService, "baseMapper", videoMapper);
    }

    /** 构造一个属于用户 100 的、状态为 public 的视频 */
    private Video ownedVideo(Long videoId, Long ownerId, String status) {
        Video video = new Video();
        video.setId(videoId);
        video.setUserId(ownerId);
        video.setStatus(status);
        video.setTitle("测试视频-" + videoId);
        return video;
    }

    // ==================== TC-02-2：删除本人视频（主成功流） ====================

    @Test
    @DisplayName("删除本人视频成功：状态应改为 deleted，返回 true")
    void deleteOwnVideo_whenOwnerDeletes_shouldSucceed() {
        Video video = ownedVideo(1L, 100L, "public");
        when(videoMapper.selectById(1L)).thenReturn(video);
        when(videoMapper.updateById(video)).thenReturn(1);

        boolean result = videoService.deleteOwnVideo(100L, 1L);

        assertTrue(result, "本人删除自己的视频应当成功");
        assertEquals("deleted", video.getStatus(), "删除后视频状态应为 deleted（软删除）");
        verify(videoMapper).updateById(video);
    }

    // ==================== TC-02-3：删除非本人视频（越权分支） ====================

    @Test
    @DisplayName("删除他人视频应被拒绝：返回 false，且不改动数据库")
    void deleteOwnVideo_whenNotOwner_shouldBeRejected() {
        Video video = ownedVideo(1L, 200L, "public");
        when(videoMapper.selectById(1L)).thenReturn(video);

        boolean result = videoService.deleteOwnVideo(100L, 1L);

        assertFalse(result, "非本人删除应当失败");
        assertEquals("public", video.getStatus(), "越权删除时视频状态不应被修改");
        verify(videoMapper, never()).updateById(any(Video.class));
    }

    @Test
    @DisplayName("视频不存在时删除：返回 false")
    void deleteOwnVideo_whenVideoNotExists_shouldReturnFalse() {
        when(videoMapper.selectById(999L)).thenReturn(null);

        boolean result = videoService.deleteOwnVideo(100L, 999L);

        assertFalse(result, "视频不存在时删除应当失败");
        verify(videoMapper, never()).updateById(any(Video.class));
    }

    @Test
    @DisplayName("userId 为空时删除：返回 false（未登录异常分支）")
    void deleteOwnVideo_whenUserIdNull_shouldReturnFalse() {
        Video video = ownedVideo(1L, 100L, "public");
        when(videoMapper.selectById(1L)).thenReturn(video);

        boolean result = videoService.deleteOwnVideo(null, 1L);

        assertFalse(result, "缺少用户身份时删除应当失败");
        verify(videoMapper, never()).updateById(any(Video.class));
    }

    @Test
    @DisplayName("重复删除已删除的视频：返回 false")
    void deleteOwnVideo_whenVideoAlreadyDeleted_shouldReturnFalse() {
        Video video = ownedVideo(1L, 100L, "deleted");
        when(videoMapper.selectById(1L)).thenReturn(video);

        boolean result = videoService.deleteOwnVideo(100L, 1L);

        assertFalse(result, "已删除的视频不能再删除");
        verify(videoMapper, never()).updateById(any(Video.class));
    }

    @Test
    @DisplayName("数据库更新影响 0 行（异常分支）：返回 false")
    void deleteOwnVideo_whenUpdateAffectsZeroRows_shouldReturnFalse() {
        Video video = ownedVideo(1L, 100L, "public");
        when(videoMapper.selectById(1L)).thenReturn(video);
        when(videoMapper.updateById(video)).thenReturn(0);

        boolean result = videoService.deleteOwnVideo(100L, 1L);

        assertFalse(result, "数据库更新失败（影响 0 行）时删除应当失败");
        assertEquals("deleted", video.getStatus(), "业务上先改了内存状态（随后由事务回滚）");
    }

    // ==================== UC-02：可见性设置（管理个人视频的另一业务规则） ====================

    @Test
    @DisplayName("本人把视频设为仅自己可见：返回 true，状态变为 private")
    void setVisibility_whenOwnerSetsPrivate_shouldSucceed() {
        Video video = ownedVideo(1L, 100L, "public");
        when(videoMapper.selectById(1L)).thenReturn(video);
        when(videoMapper.updateById(video)).thenReturn(1);

        boolean result = videoService.setVisibility(100L, 1L, false);

        assertTrue(result, "本人设置可见性应当成功");
        assertEquals("private", video.getStatus(), "visible=false 时状态应为 private");
    }

    @Test
    @DisplayName("本人把视频设为公开：状态变为 public")
    void setVisibility_whenOwnerSetsPublic_shouldSucceed() {
        Video video = ownedVideo(1L, 100L, "private");
        when(videoMapper.selectById(1L)).thenReturn(video);
        when(videoMapper.updateById(video)).thenReturn(1);

        boolean result = videoService.setVisibility(100L, 1L, true);

        assertTrue(result);
        assertEquals("public", video.getStatus(), "visible=true 时状态应为 public");
    }

    @Test
    @DisplayName("非本人设置可见性：返回 false（越权分支）")
    void setVisibility_whenNotOwner_shouldBeRejected() {
        Video video = ownedVideo(1L, 200L, "public");
        when(videoMapper.selectById(1L)).thenReturn(video);

        boolean result = videoService.setVisibility(100L, 1L, false);

        assertFalse(result, "非本人设置可见性应当失败");
        verify(videoMapper, never()).updateById(any(Video.class));
    }

    // ==================== TC-02-1：查看本人视频列表（主成功流 + 字段装饰规则） ====================

    @Test
    @DisplayName("查看本人视频列表：返回列表并完成字段装饰（播放量格式化、作者信息）")
    void getVideosByUserId_shouldReturnDecoratedList() {
        Video video = ownedVideo(1L, 100L, "public");
        video.setPlayCount(12345);
        video.setPlayUrl("http://localhost:8082/video/videos/demo.mp4");
        when(videoMapper.selectList(any())).thenReturn(List.of(video));

        User author = new User();
        author.setId(100L);
        author.setNickname("航音小站");
        author.setAvatarUrl("");
        when(userMapper.selectById(anyLong())).thenReturn(author);

        List<Video> result = videoService.getVideosByUserId(100L);

        assertNotNull(result);
        assertEquals(1, result.size(), "本人视频列表应包含 1 条视频");
        assertEquals("1.2万", result.get(0).getViews(), "播放量 12345 应格式化为 1.2万");
        assertNotNull(result.get(0).getAuthorInfo(), "应装饰作者信息");
        assertEquals("航音小站", result.get(0).getAuthorInfo().getNickname());
    }
}
