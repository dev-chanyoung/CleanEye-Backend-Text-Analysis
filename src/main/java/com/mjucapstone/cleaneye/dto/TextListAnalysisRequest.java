package com.mjucapstone.cleaneye.dto;

import lombok.Data;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 클라이언트의 텍스트 분석 요청 데이터를 매핑하고 유효성을 검증하는 객체입니다.
 */
@Data
public class TextListAnalysisRequest {

    @NotNull(message = "words 목록은 필수 항목입니다.")
    @Size(min = 1, message = "분석을 위해 최소 1개 이상의 단어를 포함해야 합니다.")
    private List<String> words;

    @NotNull(message = "rate(필터링 강도) 값은 필수 항목입니다.")
    @Min(value = 0) @Max(value = 100)
    private Double rate;

    @NotNull(message = "type(분석 타입) 값은 필수 항목입니다.")
    @Min(1) @Max(4)
    private Integer type;

    private String requestUrl;
}