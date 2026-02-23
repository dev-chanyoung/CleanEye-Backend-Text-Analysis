package com.mjucapstone.cleaneye.service;

import com.mjucapstone.cleaneye.domain.ReportedExpression;
import com.mjucapstone.cleaneye.dto.HarmfulnessResult;
import com.mjucapstone.cleaneye.dto.TextListAnalysisRequest;
import com.mjucapstone.cleaneye.repository.ReportedExpressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class TextAnalysisService {

    private final GeminiAnalysisCacheService geminiService;
    private final ReportedExpressionRepository reportedRepository;
    private final ReportedExpressionService reportedExpressionService;

    // 자소서 및 성능 최적화 기준이 되는 배치 사이즈
    private static final int BATCH_SIZE = 50;

    /**
     * [Final Optimized Version]
     * DB 선조회 -> 미탐지 단어 50개 단위 청크 분할 -> 비동기 병렬 AI 분석 -> 자가 학습형 DB 저장
     */
    @Transactional
    public List<HarmfulnessResult> analyze(TextListAnalysisRequest request) {
        List<String> inputTexts = request.getWords();
        if (inputTexts == null || inputTexts.isEmpty()) return Collections.emptyList();

        long startTime = System.currentTimeMillis();
        double thresholdScore = 101.0 - Math.max(1.0, Math.min(100.0, request.getRate()));

        log.info("[분석 시작] 계층형 병렬 아키텍처 - 총 {}개 단어 (Batch Size: {})", inputTexts.size(), BATCH_SIZE);

        // 1. DB/캐시 선조회 (1차 방어선)
        List<ReportedExpression> dbResults = reportedRepository.findAllByTextIn(inputTexts);
        Map<String, Double> dbMap = dbResults.stream()
                .peek(ReportedExpression::incrementReportCount)
                .collect(Collectors.toMap(ReportedExpression::getText, ReportedExpression::getScore, (v1, v2) -> v1));

        // 2. DB 미탐지 단어 선별 (AI 분석 대상)
        List<String> wordsForAi = inputTexts.stream()
                .filter(text -> !dbMap.containsKey(text))
                .distinct()
                .collect(Collectors.toList());

        List<HarmfulnessResult> finalResults = new ArrayList<>();

        // 3. DB Hit 데이터 결과 매핑
        dbMap.forEach((text, score) ->
                finalResults.add(buildResult(text, score, "DB Dataset Hit", thresholdScore, request)));

        // 4. AI 분석 대상 존재 시 병렬 배치 처리 실행
        if (!wordsForAi.isEmpty()) {
            log.info("[AI 분석] {}개 단어에 대해 50개 단위 병렬 배칭 시작", wordsForAi.size());

            // [핵심] 리스트를 50개 단위로 쪼개기
            List<List<String>> chunks = partitionList(wordsForAi, BATCH_SIZE);

            // [핵심] 각 청크를 비동기 병렬 호출
            List<CompletableFuture<List<HarmfulnessResult>>> futures = chunks.stream()
                    .map(chunk -> CompletableFuture.supplyAsync(() -> {
                        log.info("[Parallel Task] {}개 단어 배치 분석 요청", chunk.size());
                        return geminiService.analyzeWordsInBatch(chunk);
                    }))
                    .collect(Collectors.toList());

            // 병렬 작업 완료 대기 및 결과 취합
            List<HarmfulnessResult> aiTotalResults = futures.stream()
                    .map(CompletableFuture::join) // 각 병렬 작업 결과 취합
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            // 5. AI 분석 결과 메타데이터 주입 및 DB 아카이빙
            aiTotalResults.forEach(r -> {
                updateMetaData(r, thresholdScore, request);
                finalResults.add(r);

                // [데이터 아카이빙] 자가 학습형 DB 저장
                reportedExpressionService.saveOrUpdate(r.getInputText(), r.getScore());
            });
        }

        long endTime = System.currentTimeMillis();
        log.info("[분석 완료] 최종 소요 시간: {} ms (DB: {}건, AI 병렬분석: {}건)",
                (endTime - startTime), dbResults.size(), wordsForAi.size());

        return finalResults;
    }

    /**
     * 리스트를 지정된 사이즈(50)로 쪼개주는 순수 자바 헬퍼 메서드
     */
    private <T> List<List<T>> partitionList(List<T> list, int size) {
        return IntStream.range(0, (list.size() + size - 1) / size)
                .mapToObj(i -> list.subList(i * size, Math.min((i + 1) * size, list.size())))
                .collect(Collectors.toList());
    }

    private HarmfulnessResult buildResult(String text, Double score, String reason, double threshold, TextListAnalysisRequest request) {
        boolean isHarmful = score >= threshold;
        HarmfulnessResult result = HarmfulnessResult.builder()
                .inputText(text)
                .score(score)
                .isConsideredHarmful(isHarmful)
                .reason(reason)
                .type(request.getType())
                .requestUrl(request.getRequestUrl())
                .build();

        if (request.getType() == 4 && isHarmful) {
            result.setRefinedText(geminiService.getRefinedText(text));
        }
        return result;
    }

    private void updateMetaData(HarmfulnessResult result, double threshold, TextListAnalysisRequest request) {
        boolean isHarmful = result.getScore() >= threshold;
        result.setType(request.getType());
        result.setRequestUrl(request.getRequestUrl());
        result.setConsideredHarmful(isHarmful);
        result.setReason(String.format("AI Batch Analysis Score: %.0f", result.getScore()));

        if (request.getType() == 4 && isHarmful) {
            result.setRefinedText(geminiService.getRefinedText(result.getInputText()));
        }
    }
}