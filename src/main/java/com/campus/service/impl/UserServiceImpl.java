package com.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.dto.BusinessException;
import com.campus.dto.ResultCode;
import com.campus.entity.Conversation;
import com.campus.entity.Message;
import com.campus.entity.SysUser;
import com.campus.repository.ConversationMapper;
import com.campus.repository.MessageMapper;
import com.campus.repository.SysUserMapper;
import com.campus.service.UserService;
import com.campus.util.JwtUtil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$");

    private final SysUserMapper sysUserMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper sysUserMapper,
                           ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           BCryptPasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SysUser register(String username, String phone, String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度不能少于6位");
        }

        boolean hasPhone = phone != null && !phone.trim().isEmpty();
        boolean hasEmail = email != null && !email.trim().isEmpty();

        if (!hasPhone && !hasEmail) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号和邮箱至少填写一项");
        }
        if (hasPhone && !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号格式不正确（11位数字）");
        }
        if (hasEmail && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
        }

        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username.trim()));
        if (count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(hasPhone ? phone.trim() : null);
        user.setEmail(hasEmail ? email.trim() : null);
        user.setRole("STUDENT");

        sysUserMapper.insert(user);
        return user;
    }

    @Override
    public String login(String account, String password) {
        if (account == null || account.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号不能为空");
        }
        if (password == null || password.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空");
        }

        SysUser user = sysUserMapper.findByAccount(account.trim());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }

        return JwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public SysUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof SysUser) {
            return (SysUser) principal;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    @Override
    @Transactional
    public void deleteAccount(String password) {
        SysUser user = getCurrentUser();
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码错误");
        }

        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getUserId, user.getId()));
        for (Conversation conv : conversations) {
            messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, conv.getId()));
            conversationMapper.deleteById(conv.getId());
        }

        sysUserMapper.deleteById(user.getId());
    }
}
