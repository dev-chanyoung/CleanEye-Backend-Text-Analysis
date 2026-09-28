package com.mjucapstone.cleaneye.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjucapstone.cleaneye.dto.HarmfulnessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Gemini 응답 파싱과 "순화불가" 처리 규칙을 검증합니다.
 * 실제 네트워크 대신 WebClient의 ExchangeFunction을 바꿔 고정된 응답을 돌려줍니다.
 */
class GeminiAnalysisCacheServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest(name = "Gemini 응답 ''{0}'' -> ''{1}''")
    @CsvSource(delimiter = '|', value = {
            "친구                              | 친구",
            "**친구**                          | 친구",
            "*친구*                            | 친구",
            "바보                              | 순화불가",
            "대체어없음                        | 순화불가",
            "이것은 너무 길어서 쓸 수 없는 응답 | 순화불가",
    })
    @DisplayName("순화 응답을 정리하고, 원문 그대로이거나 대체어 없음이거나 너무 길면 순화불가로 처리한다")
    void refinesText(String geminiText, String expected) {
        assertThat(serviceReturning(geminiText).getRefinedText("바보")).isEqualTo(expected);
    }

    @Test
    @DisplayName("여러 줄로 온 순화 응답은 순화불가로 처리한다")
    void multiLineRefinementIsRejected() {
        assertThat(serviceReturning("친구\n동료").getRefinedText("바보")).isEqualTo("순화불가");
    }

    @Test
    @DisplayName("Gemini 호출이 실패하면 순화불가를 반환한다")
    void refinementFallsBackOnApiError() {
        assertThat(failingService().getRefinedText("바보")).isEqualTo("순화불가");
    }

    @Test
    @DisplayName("코드 펜스로 감싼 JSON 배열 응답도 단어별 점수로 파싱한다")
    void parsesFencedBatchResponse() {
        String json = "```json\n[{\"word\": \"바보\", \"score\": 80}, {\"word\": \"안녕\", \"score\": 0}]\n```";

        List<HarmfulnessResult> results = serviceReturning(json).analyzeWordsInBatch(List.of("바보", "안녕"));

        assertThat(results).extracting(HarmfulnessResult::getInputText, HarmfulnessResult::getScore)
                .containsExactly(tuple("바보", 80.0), tuple("안녕", 0.0));
    }

    @Test
    @DisplayName("배치 응답을 파싱할 수 없으면 모든 단어를 0점으로 돌려준다")
    void batchFallsBackToZeroOnBadJson() {
        List<HarmfulnessResult> results = serviceReturning("분석할 수 없습니다").analyzeWordsInBatch(List.of("a", "b"));

        assertThat(results).extracting(HarmfulnessResult::getInputText).containsExactly("a", "b");
        assertThat(results).allSatisfy(r -> assertThat(r.getScore()).isZero());
    }

    @Test
    @DisplayName("단건 분석은 응답 텍스트에서 숫자만 뽑아 점수로 쓴다")
    void parsesSingleScore() {
        assertThat(serviceReturning("Score: 75").callGeminiAndParse("바보").getScore()).isEqualTo(75.0);
    }

    /**
     * Gemini generateContent 응답 형식(candidates[0].content.parts[0].text)으로 감싼 JSON 본문을 만듭니다.
     */
    static String geminiBody(String text) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("candidates", List.of(
                    Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static WebClient webClientReturning(String geminiText) {
        return WebClient.builder()
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(geminiBody(geminiText))
                        .build()))
                .build();
    }

    private GeminiAnalysisCacheServiceImpl serviceReturning(String geminiText) {
        return create(webClientReturning(geminiText));
    }

    private GeminiAnalysisCacheServiceImpl failingService() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()))
                .build();
        return create(webClient);
    }

    private GeminiAnalysisCacheServiceImpl create(WebClient webClient) {
        GeminiAnalysisCacheServiceImpl service = new GeminiAnalysisCacheServiceImpl(webClient, OBJECT_MAPPER);
        ReflectionTestUtils.setField(service, "geminiApiUrl", "http://gemini.test");
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
        return service;
    }
}
