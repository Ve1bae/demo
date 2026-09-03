package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Danmaku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DanmakuMapper extends BaseMapper<Danmaku> {
    // 零代码！内置的 insert, selectById, updateById, deleteById 等方法已经直接可用
}