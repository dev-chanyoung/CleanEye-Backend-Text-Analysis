package com.mjucapstone.cleaneye.service;

import com.mjucapstone.cleaneye.domain.ReportedExpression;
import com.mjucapstone.cleaneye.dto.HarmfulnessResult;
import com.mjucapstone.cleaneye.dto.TextListAnalysisRequest;
import com.mjucapstone.cleaneye.repository.ReportedExpressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DB 선조회 -> 캐시 선조회 -> 캐시 미스만 Gemini 배치 호출로 이어지는 분석 파이프라인을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class TextAnalysisServiceTest {

    @Mock
    private GeminiAnalysisCacheService geminiService;
    @Mock
    private ReportedExpressionRepository reportedRepository;
    @Mock
    private ReportedExpressionService reportedExpressionService;

    private ConcurrentMapCacheManager cacheManager;
    private TextAnalysisService service;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager("geminiResults");
        service = new TextAnalysisService(geminiService, reportedRepository, reportedExpressionService, cacheManager);
        lenient().when(reportedRepository.findAllByTextIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("DB에 등록된 단어는 Gemini를 호출하지 않고 DB 점수를 쓰며 신고 횟수를 올린다")
    void dbHitSkipsGemini() {
        ReportedExpression known = expression("욕설", 100.0);
        when(reportedRepository.findAllByTextIn(anyCollection())).thenReturn(List.of(known));

        List<HarmfulnessResult> results = service.analyze(request(List.of("욕설"), 50.0, 1));

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.getInputText()).isEqualTo("욕설");
            assertThat(r.getScore()).isEqualTo(100.0);
            assertThat(r.getReason()).isEqualTo("DB Dataset Hit");
            assertThat(r.isConsideredHarmful()).isTrue();
        });
        assertThat(known.getReportCount()).isEqualTo(2);
        verify(geminiService, never()).analyzeWordsInBatch(anyList());
    }

    @Test
    @DisplayName("캐시에 점수가 있는 단어는 Gemini를 다시 호출하지 않는다")
    void cacheHitSkipsGemini() {
        cacheManager.getCache("geminiResults").put("바보", 70.0);

        List<HarmfulnessResult> results = service.analyze(request(List.of("바보"), 50.0, 1));

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.getScore()).isEqualTo(70.0);
            assertThat(r.getReason()).startsWith("Cache Hit");
        });
        verify(geminiService, never()).analyzeWordsInBatch(anyList());
    }

    @Test
    @DisplayName("캐시 미스 단어는 Gemini 배치로 분석한 뒤 DB에 저장하고 캐시에 적재해, 다음 요청에서는 재호출하지 않는다")
    void cacheMissCallsGeminiThenCaches() {
        when(geminiService.analyzeWordsInBatch(anyList())).thenAnswer(inv -> scoreAll(inv.getArgument(0), 80.0));

        List<HarmfulnessResult> first = service.analyze(request(List.of("신조어"), 50.0, 1));

        assertThat(first).singleElement().satisfies(r -> {
            assertThat(r.getScore()).isEqualTo(80.0);
            assertThat(r.getReason()).isEqualTo("AI Batch Analysis Score: 80");
            assertThat(r.isConsideredHarmful()).isTrue();
        });
        verify(reportedExpressionService).saveOrUpdate("신조어", 80.0);
        assertThat(cacheManager.getCache("geminiResults").get("신조어", Double.class)).isEqualTo(80.0);

        List<HarmfulnessResult> second = service.analyze(request(List.of("신조어"), 50.0, 1));

        assertThat(second).singleElement().satisfies(r -> assertThat(r.getReason()).startsWith("Cache Hit"));
        verify(geminiService, times(1)).analyzeWordsInBatch(anyList());
    }

    @Test
    @DisplayName("캐시 미스 단어는 50개 단위 청크로 나눠 배치 호출한다")
    void partitionsMissesIntoChunksOfFifty() {
        List<String> words = IntStream.range(0, 120).mapToObj(i -> "w" + i).toList();
        when(geminiService.analyzeWordsInBatch(anyList())).thenAnswer(inv -> scoreAll(inv.getArgument(0), 10.0));

        List<HarmfulnessResult> results = service.analyze(request(words, 50.0, 1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> chunks = ArgumentCaptor.forClass(List.class);
        verify(geminiService, times(3)).analyzeWordsInBatch(chunks.capture());
        assertThat(chunks.getAllValues()).extracting(List::size).containsExactlyInAnyOrder(50, 50, 20);
        assertThat(results).hasSize(120);
    }

    @Test
    @DisplayName("같은 요청 안의 중복 단어는 한 번만 Gemini로 보낸다")
    void deduplicatesMissesWithinRequest() {
        when(geminiService.analyzeWordsInBatch(anyList())).thenAnswer(inv -> scoreAll(inv.getArgument(0), 10.0));

        service.analyze(request(List.of("a", "a", "b"), 50.0, 1));

        verify(geminiService).analyzeWordsInBatch(List.of("a", "b"));
    }

    @Test
    @DisplayName("rate가 높을수록 임계값이 낮아진다 (임계값 = 101 - rate)")
    void thresholdFollowsRate() {
        when(geminiService.analyzeWordsInBatch(anyList()))
                .thenReturn(List.of(result("경계", 31.0), result("미만", 30.0)));

        Map<String, HarmfulnessResult> byWord = index(service.analyze(request(List.of("경계", "미만"), 70.0, 1)));

        assertThat(byWord.get("경계").isConsideredHarmful()).isTrue();
        assertThat(byWord.get("미만").isConsideredHarmful()).isFalse();
    }

    @Test
    @DisplayName("type 4 요청에서는 유해로 판정된 단어만 순화 텍스트를 받는다")
    void refinesOnlyHarmfulWordsForType4() {
        when(reportedRepository.findAllByTextIn(anyCollection())).thenReturn(List.of(expression("욕설", 100.0)));
        when(geminiService.analyzeWordsInBatch(anyList()))
                .thenReturn(List.of(result("나쁜말", 90.0), result("인사", 5.0)));
        when(geminiService.getRefinedText(anyString())).thenAnswer(inv -> "순화-" + inv.getArgument(0));

        Map<String, HarmfulnessResult> byWord = index(service.analyze(request(List.of("욕설", "나쁜말", "인사"), 50.0, 4)));

        assertThat(byWord.get("욕설").getRefinedText()).isEqualTo("순화-욕설");
        assertThat(byWord.get("나쁜말").getRefinedText()).isEqualTo("순화-나쁜말");
        assertThat(byWord.get("인사").getRefinedText()).isNull();
        verify(geminiService, never()).getRefinedText("인사");
    }

    @Test
    @DisplayName("type 4가 아니면 유해 단어라도 순화를 요청하지 않는다")
    void doesNotRefineForOtherTypes() {
        when(reportedRepository.findAllByTextIn(anyCollection())).thenReturn(List.of(expression("욕설", 100.0)));

        service.analyze(request(List.of("욕설"), 50.0, 1));

        verify(geminiService, never()).getRefinedText(anyString());
    }

    private static TextListAnalysisRequest request(List<String> words, double rate, int type) {
        TextListAnalysisRequest request = new TextListAnalysisRequest();
        request.setWords(words);
        request.setRate(rate);
        request.setType(type);
        request.setRequestUrl("https://example.com");
        return request;
    }

    private static ReportedExpression expression(String text, double score) {
        return ReportedExpression.builder().text(text).score(score).reportCount(1).build();
    }

    private static HarmfulnessResult result(String word, double score) {
        return HarmfulnessResult.builder().inputText(word).score(score).build();
    }

    private static List<HarmfulnessResult> scoreAll(List<String> words, double score) {
        return words.stream().map(w -> result(w, score)).collect(Collectors.toList());
    }

    private static Map<String, HarmfulnessResult> index(List<HarmfulnessResult> results) {
        return results.stream().collect(Collectors.toMap(HarmfulnessResult::getInputText, Function.identity()));
    }
}
