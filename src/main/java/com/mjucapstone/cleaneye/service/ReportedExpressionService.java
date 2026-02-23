package com.mjucapstone.cleaneye.service;

import com.mjucapstone.cleaneye.domain.ReportedExpression;
import com.mjucapstone.cleaneye.repository.ReportedExpressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportedExpressionService {

    private final ReportedExpressionRepository repository;

    @Transactional
    public void saveOrUpdate(String text, Double score) {
        repository.findByText(text)
                .ifPresentOrElse(
                        expression -> {
                            expression.incrementReportCount();
                            // 점수가 업데이트되었을 경우를 대비해 갱신 로직을 넣을 수도 있습니다.
                            log.info("[DB] 기존 데이터 카운트 증가: {}", text);
                        },
                        () -> {
                            ReportedExpression newExpression = ReportedExpression.builder()
                                    .text(text)
                                    .score(score) // Gemini가 분석한 점수 저장
                                    .reportCount(1)
                                    .build();
                            repository.save(newExpression);
                            log.info("[DB] 신규 데이터 등록: {} (점수: {})", text, score);
                        }
                );
    }
}