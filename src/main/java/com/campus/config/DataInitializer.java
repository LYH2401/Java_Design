package com.campus.config;

import com.campus.entity.SysUser;
import com.campus.repository.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder encoder;

    public DataInitializer(SysUserMapper sysUserMapper, BCryptPasswordEncoder encoder) {
        this.sysUserMapper = sysUserMapper;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        try {
            SysUser existing = sysUserMapper.selectById(1L);
            if (existing == null) {
                SysUser admin = new SysUser();
                admin.setId(1L);
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin123"));
                admin.setRole("ADMIN");
                sysUserMapper.insert(admin);
                log.info("管理员账号已创建: admin / admin123");
            }
        } catch (Exception e) {
            log.warn("管理员账号初始化跳过: {}", e.getMessage());
        }
    }
}
