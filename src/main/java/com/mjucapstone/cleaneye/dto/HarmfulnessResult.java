package com.mjucapstone.cleaneye.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 개별 텍스트에 대한 유해성 분석 결과 및 AI 순화 텍스트를 담는 데이터 전송 객체입니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarmfulnessResult {
    private String inputText;            // 분석 대상 원본 텍스트
    private String refinedText;          // AI가 생성한 순화된 텍스트
    private boolean isConsideredHarmful;   // 설정된 임계값 기반 유해 여부 판별 결과
    private double score;                // 유해성 점수 (0.0 ~ 100.0)
    private String reason;               // 유해성 판단 근거 (DB 매칭 또는 AI 분석 사유)
    private Integer type;                // 요청된 분석 타입
    private String requestUrl;           // 분석 요청이 발생한 출처 URL
}