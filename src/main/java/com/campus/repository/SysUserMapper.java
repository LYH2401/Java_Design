package com.campus.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{account} OR phone = #{account} OR email = #{account}")
    SysUser findByAccount(String account);
}
