package com.campus.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.entity.CourseSchedule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseScheduleMapper extends BaseMapper<CourseSchedule> {
}
