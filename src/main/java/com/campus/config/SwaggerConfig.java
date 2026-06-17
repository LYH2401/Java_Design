package com.campus.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI campusAssistantOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("校园智能服务小助手 API")
                        .version("1.0.0")
                        .description("基于 Spring AI Alibaba 的智能校园服务对话系统接口文档"));
    }
}
