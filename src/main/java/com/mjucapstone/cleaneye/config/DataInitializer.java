package com.mjucapstone.cleaneye.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjucapstone.cleaneye.domain.ReportedExpression;
import com.mjucapstone.cleaneye.dto.BadWordListDto;
import com.mjucapstone.cleaneye.repository.ReportedExpressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ReportedExpressionRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("[DataInitializer] 유해 단어 데이터셋 초기화 시작...");

        // 1. JSON 파일 읽기
        ClassPathResource resource = new ClassPathResource("badwords.json");
        if (!resource.exists()) {
            log.warn("[DataInitializer] badwords.json 파일이 존재하지 않아 초기화를 건너뜁니다.");
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            BadWordListDto data = objectMapper.readValue(inputStream, BadWordListDto.class);
            List<String> words = data.getBadwords();

            int count = 0;
            for (String word : words) {
                // 2. 중복 저장 방지 (이미 DB에 있는지 확인)
                if (!repository.existsByText(word)) {
                    ReportedExpression expression = ReportedExpression.builder()
                            .text(word)
                            .score(100.0) // 기본 데이터셋이므로 유해 점수 최대치 부여
                            .reportCount(1)
                            .build();
                    repository.save(expression);
                    count++;
                }
            }
            log.info("[DataInitializer] 총 {}개의 유해 단어가 신규 등록되었습니다.", count);
        } catch (Exception e) {
            log.error("[DataInitializer] 초기화 중 오류 발생: {}", e.getMessage());
        }
    }
}