package com.campus.service;

import com.campus.entity.SysUser;

public interface UserService {

    SysUser register(String username, String phone, String email, String password);

    String login(String account, String password);

    SysUser getCurrentUser();

    void deleteAccount(String password);
}
