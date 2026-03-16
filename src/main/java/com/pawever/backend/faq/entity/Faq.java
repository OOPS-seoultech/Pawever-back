package com.pawever.backend.faq.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** 상세 답변 (JSON: detail_answer) */
    @Column(name = "detail_answer", columnDefinition = "TEXT")
    private String detailAnswer;

    private Integer orderIndex;
}
