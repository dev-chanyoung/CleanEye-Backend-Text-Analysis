package com.mjucapstone.cleaneye.controller;

import com.mjucapstone.cleaneye.dto.HarmfulWordListResponse;
import com.mjucapstone.cleaneye.dto.HarmfulnessResult;
import com.mjucapstone.cleaneye.dto.TextListAnalysisRequest;
import com.mjucapstone.cleaneye.service.TextAnalysisService;
import com.mjucapstone.cleaneye.service.ReportedExpressionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/text")
@RequiredArgsConstructor
public class TextAnalysisController {

    private final TextAnalysisService textAnalysisService;
    private final ReportedExpressionService reportedExpressionService;

    private static final int TYPE_AI_REFINEMENT = 4;

    /**
     * [메인 분석 엔드포인트]
     * DB 선조회 -> 캐시 확인 -> Gemini Batch 호출 -> 신규 단어 DB 저장까지
     * 서비스 계층에서 한 번에 처리됩니다.
     */
    @PostMapping("/analyze")
    public ResponseEntity<HarmfulWordListResponse> analyzeText(@Valid @RequestBody TextListAnalysisRequest request) {
        log.info("[API Request] 분석 시작 - 단어 수: {}, 요청 타입: {}",
                request.getWords().size(), request.getType());

        List<HarmfulnessResult> serviceResults = textAnalysisService.analyze(request);
        return ResponseEntity.ok(createResponseByType(request, serviceResults));
    }

    /**
     * [수동 신고 엔드포인트]
     * 사용자가 직접 유해 단어를 신고할 때 사용합니다.
     * 기본 점수를 100.0으로 설정하여 즉시 블랙리스트에 반영되도록 합니다.
     */
    @PostMapping("/report")
    public ResponseEntity<String> reportHarmfulText(
            @RequestParam String text,
            @RequestParam(defaultValue = "100.0") Double score) {

        log.info("[Report Request] 수동 신고 등록: {} (부여 점수: {})", text, score);

        // 기존 ReportedExpressionService의 메서드 이름을 saveOrUpdate로 고쳤다면 그에 맞춰 호출
        reportedExpressionService.saveOrUpdate(text, score);

        return ResponseEntity.ok("'" + text + "' 단어가 유해 단어 데이터셋에 등록되었습니다.");
    }

    private HarmfulWordListResponse createResponseByType(TextListAnalysisRequest request, List<HarmfulnessResult> results) {
        // 유해하다고 판단된 아이템만 필터링
        List<HarmfulnessResult> harmfulItems = results.stream()
                .filter(HarmfulnessResult::isConsideredHarmful)
                .toList();

        // AI 순화 모드일 경우 (TYPE 4)
        if (request.getType() == TYPE_AI_REFINEMENT) {
            List<String> originalWords = harmfulItems.stream()
                    .map(HarmfulnessResult::getInputText)
                    .toList();

            List<String> refinedWords = harmfulItems.stream()
                    .map(res -> res.getRefinedText() != null ? res.getRefinedText() : res.getInputText())
                    .toList();

            return new HarmfulWordListResponse(originalWords, refinedWords, request.getRequestUrl(), TYPE_AI_REFINEMENT);
        }

        // 일반 분석 모드일 경우
        List<String> harmfulWords = harmfulItems.stream()
                .map(HarmfulnessResult::getInputText)
                .toList();

        return new HarmfulWordListResponse(harmfulWords, request.getRequestUrl(), request.getType());
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Clean Eye Text Analyzer Service is operational.");
    }
}