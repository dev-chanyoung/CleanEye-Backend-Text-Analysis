package com.mjucapstone.cleaneye.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.NoArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Collections;

/**
 * 텍스트 필터링 결과 리스트를 클라이언트에게 반환하기 위한 응답 객체입니다.
 * 필터링 모드에 따라 유해 단어 목록 또는 AI 순화 목록을 포함합니다.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarmfulWordListResponse {
    private List<String> words;          // 탐지된 유해 단어 원문 리스트
    private List<String> refinedWords;   // AI에 의해 순화된 단어 리스트 (Type 4 전용)
    private String requestUrl;           // 분석 대상 출처 URL
    private Integer type;                // 적용된 분석 타입

    /**
     * 기본 필터링(Type 1~3) 응답 생성을 위한 생성자입니다.
     */
    public HarmfulWordListResponse(List<String> words, String requestUrl, Integer type) {
        this.words = (words != null) ? words : Collections.emptyList();
        this.requestUrl = requestUrl;
        this.type = type;
    }

    /**
     * AI 순화 기능(Type 4)이 포함된 응답 생성을 위한 생성자입니다.
     */
    public HarmfulWordListResponse(List<String> original, List<String> refined, String requestUrl, Integer type) {
        this.words = (original != null) ? original : Collections.emptyList();
        this.refinedWords = (refined != null) ? refined : Collections.emptyList();
        this.requestUrl = requestUrl;
        this.type = type;
    }
}