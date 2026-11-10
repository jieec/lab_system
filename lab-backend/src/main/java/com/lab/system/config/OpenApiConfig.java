package com.lab.system.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 全局信息。
 * 在 /doc.html 右上角「Authorize」填入登录返回的 token（不含 Bearer 前缀也可），
 * 即可直接调试需要鉴权的接口。
 */
@Configuration
public class OpenApiConfig {

    public static final String AUTH_HEADER = "Authorization";

    @Bean
    public OpenAPI labOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("高校实验室预约与设备运维系统 · 接口文档")
                        .version("1.0.0")
                        .description("登录认证、实验室预约审批、设备报修运维、站内信、信用分与审计日志")
                        .contact(new Contact().name("课程设计小组")))
                .components(new Components().addSecuritySchemes(AUTH_HEADER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name(AUTH_HEADER)
                                .description("登录接口返回的 token")))
                .addSecurityItem(new SecurityRequirement().addList(AUTH_HEADER));
    }
}
