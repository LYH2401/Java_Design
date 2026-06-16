package com.campus;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CampusApplication {

    public static void main(String[] args) {
        // 加载 .env 文件到系统属性（Spring Boot 属性解析之前）
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .directory(System.getProperty("user.dir"))
                    .load();
            dotenv.entries().forEach(e -> {
                // 仅设置尚未存在的系统属性（命令行 -D 参数优先）
                if (System.getProperty(e.getKey()) == null) {
                    System.setProperty(e.getKey(), e.getValue());
                }
            });
            System.out.println("[dotenv] .env 文件加载完成");
        } catch (Exception e) {
            System.err.println("[dotenv] .env 文件加载失败: " + e.getMessage());
        }

        SpringApplication.run(CampusApplication.class, args);
    }

}
