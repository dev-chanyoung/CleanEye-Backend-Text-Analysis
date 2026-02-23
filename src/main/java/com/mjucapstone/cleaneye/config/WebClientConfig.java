package com.mjucapstone.cleaneye.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // 기본 설정을 포함한 WebClient 빈 생성
        return builder.build();
    }
}