package com.mjucapstone.cleaneye.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjucapstone.cleaneye.dto.HarmfulnessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiAnalysisCacheServiceImpl implements GeminiAnalysisCacheService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /**
     * [Final Version] Batching - 여러 단어를 한 번의 API 호출로 처리
     */
    @Override
    public List<HarmfulnessResult> analyzeWordsInBatch(List<String> words) {
        String joinedWords = String.join(", ", words);

        // 프롬프트: JSON 배열 응답을 강제함
        String batchPrompt = String.format(
                "Analyze the following Korean words for harmfulness (0 to 100). " +
                        "Respond ONLY as a valid JSON array where each object has 'word' and 'score' fields. " +
                        "Example: [{\"word\": \"단어1\", \"score\": 80}, {\"word\": \"단어2\", \"score\": 10}] " +
                        "Words: [%s]", joinedWords
        );

        log.info("[API 호출] Batch 실행 - 단어 {}개 묶음 전송", words.size());

        try {
            Map<String, Object> response = executeGeminiRequest(batchPrompt);
            return parseBatchResponse(response);
        } catch (Exception e) {
            log.error("[API Error] Batch 분석 실패: {}", e.getMessage());
            return words.stream()
                    .map(w -> HarmfulnessResult.builder().inputText(w).score(0.0).build())
                    .collect(Collectors.toList());
        }
    }

    /**
     * [After Version] Caching - 단건 분석 시 캐시 적용
     */
    @Override
    @Cacheable(value = "geminiResults", key = "#text")
    public HarmfulnessResult callGeminiAndParse(String text) {
        log.info("[API 호출] 캐시 미스 - 단건 직접 호출: '{}'", text);
        try {
            String prompt = String.format("Analyze the harmfulness of the word '%s' (0-100). Respond with just the number.", text);
            Map<String, Object> response = executeGeminiRequest(prompt);

            // 기존 단건 파싱 로직 활용
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String scoreStr = parts.get(0).get("text").toString().replaceAll("[^0-9.]", "").trim();

            return HarmfulnessResult.builder()
                    .inputText(text)
                    .score(scoreStr.isEmpty() ? 0.0 : Double.parseDouble(scoreStr))
                    .build();
        } catch (Exception e) {
            return HarmfulnessResult.builder().inputText(text).score(0.0).build();
        }
    }

    // 실제 Gemini API와 통신하는 공통 메서드
    private Map<String, Object> executeGeminiRequest(String prompt) {
        return webClient.post()
                .uri(geminiApiUrl + "?key=" + geminiApiKey)
                .bodyValue(Map.of("contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    private List<HarmfulnessResult> parseBatchResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String rawJson = (String) parts.get(0).get("text");

            String cleanedJson = rawJson.replaceAll("```json|```", "").trim();
            List<Map<String, Object>> resultList = objectMapper.readValue(cleanedJson, new TypeReference<>() {});

            return resultList.stream()
                    .map(m -> HarmfulnessResult.builder()
                            .inputText(m.get("word").toString())
                            .score(Double.parseDouble(m.get("score").toString()))
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Batch JSON 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("응답 파싱 오류", e);
        }
    }

    /**
     * [복원] 캡스톤 전시회 버전의 순화 로직 포팅.
     * 유해 단어를 비공격적인 표현 1개로 순화해서 반환한다. 같은 텍스트는 캐시(refinedTextResults)로
     * 재호출을 막는다.
     */
    @Override
    @Cacheable(value = "refinedTextResults", key = "#text")
    public String getRefinedText(String text) {
        log.info("[API 순화 요청] 캐시 미스 - Gemini API 순화 호출 수행: '{}'", text);
        String processedText = text.trim();
        try {
            String prompt = String.format(
                    "다음 텍스트를 유해하지 않거나 부드럽거나 비공격적인 표현으로 순화해서 순화된 단어 1개로만 대답해: \"%s\"",
                    processedText
            );

            Map<String, Object> response = executeGeminiRequest(prompt);
            String refinedText = parseRefinedTextFromResult(response, processedText);

            log.info("[API 순화 결과] 원본: '{}' -> 순화: '{}'", processedText, refinedText);
            return refinedText;
        } catch (Exception e) {
            log.error("텍스트 '{}' 순화 중 Gemini API 호출 오류 발생: {}", processedText, e.getMessage());
            return "순화불가";
        }
    }

    // Gemini 응답에서 순화된 텍스트를 파싱하고, 무의미한 응답(원문 그대로/너무 길거나 여러 줄 등)은
    // 클라이언트와 약속된 "순화불가" 문자열로 정리하는 헬퍼
    @SuppressWarnings("unchecked")
    private String parseRefinedTextFromResult(Map<String, Object> response, String originalText) {
        String rawRefinedText;
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            rawRefinedText = parts.get(0).get("text").toString().trim();
        } catch (Exception e) {
            log.error("Gemini 순화 응답 파싱 중 오류 발생: {}", e.getMessage());
            return "순화불가";
        }

        if ("대체어없음".equalsIgnoreCase(rawRefinedText) ||
                "순화불가".equalsIgnoreCase(rawRefinedText) ||
                rawRefinedText.isEmpty()) {
            log.warn("Gemini가 텍스트 '{}' 순화에 대해 명시적으로 실패/대체어 없음을 응답했습니다: {}", originalText, rawRefinedText);
            return "순화불가";
        }

        // 마크다운 강조 표시(**굵게**, *기울임*) 제거
        String cleanedRefinedText = rawRefinedText
                .replaceAll("(?s)\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("(?s)\\*([^*]+)\\*", "$1")
                .trim();

        if (originalText.equalsIgnoreCase(cleanedRefinedText)) {
            log.warn("Gemini가 텍스트 '{}' 순화 시 원본을 그대로 반환했습니다. '순화불가'로 처리합니다.", originalText);
            return "순화불가";
        }

        if (cleanedRefinedText.split("\\s+").length > 5 || cleanedRefinedText.contains("\n")) {
            log.warn("순화된 텍스트 '{}'가 너무 길거나 여러 줄입니다. '순화불가'로 처리합니다.", cleanedRefinedText);
            return "순화불가";
        }

        return cleanedRefinedText;
    }
}