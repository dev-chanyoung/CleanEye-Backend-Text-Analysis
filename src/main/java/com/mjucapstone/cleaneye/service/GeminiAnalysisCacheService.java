package com.mjucapstone.cleaneye.service;

import com.mjucapstone.cleaneye.dto.HarmfulnessResult;

import java.util.List;

/**
 * 외부 Generative AI(Gemini) 연동 및 응답 캐싱을 관리하는 서비스 인터페이스입니다.
 * 텍스트 기반 유해성 점수 분석과 AI 기반 문장 순화(Refinement) 기능을 정의합니다.
 */
public interface GeminiAnalysisCacheService {

    /**
     * 입력 텍스트의 유해성 점수를 분석하고 결과를 반환합니다.
     * 구현체에서 @Cacheable을 적용하여 불필요한 API 호출 비용을 최소화해야 합니다.
     *
     * @param text 분석할 대상 문자열
     * @return 유해성 수치와 분석 근거를 포함한 {@link HarmfulnessResult}
     */
    HarmfulnessResult callGeminiAndParse(String text);

    /**
     * 유해 표현이 포함된 텍스트를 비유해적인 부드러운 표현으로 재구성(Refining)합니다.
     *
     * @param originalText 순화 처리가 필요한 원본 문자열
     * @return AI에 의해 순화된 단어 또는 문장
     */
    String getRefinedText(String originalText);

    List<HarmfulnessResult> analyzeWordsInBatch(List<String> words);
}