package com.mjucapstone.cleaneye.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Cacheable이 스프링 프록시를 거쳐 실제로 적용되는지 검증합니다.
 * 같은 텍스트를 두 번 요청하면 Gemini 호출은 한 번만 일어나야 합니다.
 */
@SpringJUnitConfig(GeminiAnalysisCachingTest.Config.class)
@TestPropertySource(properties = {"gemini.api.url=http://gemini.test", "gemini.api.key=test-key"})
class GeminiAnalysisCachingTest {

    static final AtomicInteger GEMINI_CALLS = new AtomicInteger();

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("geminiResults", "refinedTextResults");
        }

        @Bean
        WebClient webClient() {
            WebClient stub = GeminiAnalysisCacheServiceImplTest.webClientReturning("42");
            return stub.mutate()
                    .filter((req, next) -> {
                        GEMINI_CALLS.incrementAndGet();
                        return next.exchange(req);
                    })
                    .build();
        }

        @Bean
        GeminiAnalysisCacheService geminiAnalysisCacheService(WebClient webClient) {
            return new GeminiAnalysisCacheServiceImpl(webClient, new ObjectMapper());
        }
    }

    @Autowired
    private GeminiAnalysisCacheService service;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void reset() {
        GEMINI_CALLS.set(0);
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("같은 텍스트의 순화 요청은 두 번째부터 캐시에서 응답한다")
    void refinedTextIsCached() {
        String first = service.getRefinedText("바보");
        String second = service.getRefinedText("바보");

        assertThat(second).isEqualTo(first).isEqualTo("42");
        assertThat(GEMINI_CALLS).hasValue(1);
    }

    @Test
    @DisplayName("배치 분석은 캐시 대상이 아니므로 매번 호출한다")
    void batchIsNotCached() {
        service.analyzeWordsInBatch(List.of("바보"));
        service.analyzeWordsInBatch(List.of("바보"));

        assertThat(GEMINI_CALLS).hasValue(2);
    }
}
