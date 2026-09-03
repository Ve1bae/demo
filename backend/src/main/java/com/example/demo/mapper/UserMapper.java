package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper // 告诉Spring这是数据持久层，启动时会自动扫描
public interface UserMapper extends BaseMapper<User> {
    // 零代码！内置的 insert, selectById, updateById, deleteById 等方法已经直接可用
}