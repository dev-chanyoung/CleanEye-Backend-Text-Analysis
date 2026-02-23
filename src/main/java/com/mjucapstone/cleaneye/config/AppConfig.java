package com.mjucapstone.cleaneye.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 애플리케이션의 핵심 비즈니스 로직 수행에 필요한 인프라스트럭처 빈(Bean) 설정을 관리합니다.
 * 외부 API 통신을 위한 WebClient와 시스템 전역 인코딩 정책을 정의합니다.
 */
@Configuration
public class AppConfig {

    /**
     * HTTP 요청 및 응답 시 데이터의 일관성을 보장하기 위해 UTF-8 인코딩을 강제합니다.
     * Servlet Filter 레벨에서 동작하며, 프로퍼티 설정보다 우선순위를 갖도록 설정되었습니다.
     *
     * @return 설정이 완료된 {@link CharacterEncodingFilter} 객체
     */
    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        return filter;
    }

    /**
     * Gemini API 등 외부 REST 서비스와의 Non-blocking 통신을 위한 WebClient.Builder를 등록합니다.
     * 서비스 계층에서 의존성 주입(DI)을 통해 설정이 커스터마이징된 WebClient 객체를 생성할 수 있게 합니다.
     *
     * @return 기본 설정이 포함된 {@link WebClient.Builder}
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}