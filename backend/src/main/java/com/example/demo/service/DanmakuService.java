package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Danmaku;
import java.util.List;

public interface DanmakuService extends IService<Danmaku> {
    boolean saveDanmaku(Danmaku danmaku);
    
    List<Danmaku> getDanmakuByVideoUrl(String videoUrl);
    
    List<Danmaku> getDanmakuByUserId(String userId);
    
    boolean deleteDanmaku(Long id);
}