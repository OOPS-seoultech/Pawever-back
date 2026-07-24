package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "goods_survey_stories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsSurveyStory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String responseId;

    @Lob
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String storyJson;

    @Column(nullable = false)
    private boolean analysisAgreed;

    @Column(nullable = false)
    private boolean publishAgreed;

    @Column(nullable = false)
    private boolean reviewContactAgreed;

    @Column(nullable = false)
    private boolean interviewAgreed;

    @Column(nullable = false, length = 30)
    private String consentVersion;

    @Column(nullable = false)
    private Instant consentedAt;

    public static GoodsSurveyStory create(
            String responseId,
            String storyJson,
            boolean analysisAgreed,
            boolean publishAgreed,
            boolean reviewContactAgreed,
            boolean interviewAgreed,
            String consentVersion,
            Instant consentedAt
    ) {
        GoodsSurveyStory story = new GoodsSurveyStory();
        story.responseId = responseId;
        story.update(
                storyJson,
                analysisAgreed,
                publishAgreed,
                reviewContactAgreed,
                interviewAgreed,
                consentVersion,
                consentedAt
        );
        return story;
    }

    public void update(
            String storyJson,
            boolean analysisAgreed,
            boolean publishAgreed,
            boolean reviewContactAgreed,
            boolean interviewAgreed,
            String consentVersion,
            Instant consentedAt
    ) {
        this.storyJson = storyJson;
        this.analysisAgreed = analysisAgreed;
        this.publishAgreed = publishAgreed;
        this.reviewContactAgreed = reviewContactAgreed;
        this.interviewAgreed = interviewAgreed;
        this.consentVersion = consentVersion;
        this.consentedAt = consentedAt;
    }
}
