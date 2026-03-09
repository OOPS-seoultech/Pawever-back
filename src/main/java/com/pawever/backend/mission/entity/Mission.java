package com.pawever.backend.mission.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "missions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카테고리: 추억 남기기, 음성 녹음, 마음 전하기 */
    @Column(length = 50)
    private String category;

    @Column(nullable = false, length = 255)
    private String name;

    /** 부연 설명 (##) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 행동하는 법 (###) */
    @Column(name = "action_guide", columnDefinition = "TEXT")
    private String actionGuide;

    /** 일러스트 AI 프롬프트 (추억 남기기만 해당) */
    @Column(name = "illustration_prompt", columnDefinition = "TEXT")
    private String illustrationPrompt;

    /** 카테고리·목록 내 정렬 순서 */
    private Integer orderIndex;
}
