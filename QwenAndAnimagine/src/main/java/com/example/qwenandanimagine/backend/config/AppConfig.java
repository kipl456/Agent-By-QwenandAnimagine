package com.example.qwenandanimagine.backend.config;

import org.springframework.context.annotation.Configuration;

/**
 * 自定义配置的落点。
 *
 * 目前这里【不需要写任何 @Bean】,原因:
 * - Spring AI 的 ollama starter 已经自动配置好 ChatClient.Builder,并读走了
 *   application.yml 里的 spring.ai.ollama.* 配置(QwenService 直接注入它即可);
 * - 数据源 / JPA / Redis / ObjectMapper 也都由 Spring Boot 自动配置。
 *
 * 以后要加自定义 bean(比如换 HttpClient、加拦截器、注册全局工具)时,写在这里。
 */
@Configuration
public class AppConfig {
}
