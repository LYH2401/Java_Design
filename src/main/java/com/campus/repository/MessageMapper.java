package com.campus.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
