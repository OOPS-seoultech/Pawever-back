package com.pawever.backend.memorial.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comment_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommentReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    /** 선택 사유에 해당하지 않을 때 사용자 직접 입력 텍스트 */
    @Column(columnDefinition = "TEXT")
    private String customText;

    @ManyToMany
    @JoinTable(
            name = "comment_report_reasons",
            joinColumns = @JoinColumn(name = "comment_report_id"),
            inverseJoinColumns = @JoinColumn(name = "report_reason_id")
    )
    @Builder.Default
    private List<ReportReason> reasons = new ArrayList<>();
}
