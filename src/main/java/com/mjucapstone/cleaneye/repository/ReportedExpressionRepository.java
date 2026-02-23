package com.mjucapstone.cleaneye.repository;

import com.mjucapstone.cleaneye.domain.ReportedExpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportedExpressionRepository extends JpaRepository<ReportedExpression, Long> {

    // [추가] 리스트로 받은 단어들을 한 번에 조회하는 쿼리 메서드
    List<ReportedExpression> findAllByTextIn(Collection<String> texts);

    // [추가] 단건 조회 (기존에 쓰던 findByText가 있다면 유지)
    Optional<ReportedExpression> findByText(String text);

    boolean existsByText(String text);
}