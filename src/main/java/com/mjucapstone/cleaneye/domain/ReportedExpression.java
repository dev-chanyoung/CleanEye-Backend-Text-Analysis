package com.mjucapstone.cleaneye.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "reported_expression", indexes = @Index(name = "idx_reported_text", columnList = "text"))
public class ReportedExpression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String text;

    @Column(nullable = false)
    private int reportCount;

    // 추가: 해당 단어의 유해 점수 (0.0 ~ 100.0)
    @Column(nullable = false)
    private Double score;

    /**
     * 비즈니스 로직: 신고 횟수 증가
     */
    public void incrementReportCount() {
        this.reportCount++;
    }
}