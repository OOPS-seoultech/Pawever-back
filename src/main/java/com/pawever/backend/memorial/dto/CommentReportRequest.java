package com.pawever.backend.memorial.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CommentReportRequest {

    /** 신고 사유 ID 목록 (N개 선택 가능). 비어 있으면 직접 입력만 사용 */
    @Size(max = 10)
    private List<Long> reasonIds;

    /** 위 사유에 해당하지 않을 때 직접 입력 텍스트 */
    @Size(max = 500)
    private String customText;
}
