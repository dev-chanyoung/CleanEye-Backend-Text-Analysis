package com.mjucapstone.cleaneye.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Gemini API 호출에 쓰는 WebClient 빈을 등록합니다.
 * Builder는 Spring Boot가 자동 구성한 것(Jackson 코덱 등 포함)을 그대로 주입받아 사용합니다.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
