package com.parking.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("共享停车位系统 API")
                        .version("1.0.0")
                        .description("基于SpringCloud微服务的共享停车位平台接口文档")
                        .contact(new Contact().name("开发者").email("admin@parking.com")));
    }
}
