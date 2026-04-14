package com.pawever.backend.farewellpreview.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.IntegerListJsonConverter;
import com.pawever.backend.pet.entity.Pet;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "farewell_preview_progresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FarewellPreviewProgress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false, unique = true)
    private Pet pet;

    @Column(nullable = false)
    private Boolean hasCompletedGuide;

    @Column(nullable = false)
    private Integer currentStep;

    @Builder.Default
    @Convert(converter = IntegerListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Integer> enteredSteps = new ArrayList<>();

    // 완료된 메인 스텝 번호 목록 (1~5, 독립적)
    @Builder.Default
    @Convert(converter = IntegerListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Integer> completedMainSteps = new ArrayList<>();

    // 안치 준비 완료 이벤트 번호 목록 (1~7, 독립적)
    // 1~5: 각 단계 다음으로 클릭, 6: 6단계 페이지 열기, 7: 6단계 완료 클릭
    @Builder.Default
    @Convert(converter = IntegerListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Integer> restingCompletedSubStepNumbers = new ArrayList<>();

    // 행정처리 완료 단계 번호 목록 (1~5, 독립적)
    @Builder.Default
    @Convert(converter = IntegerListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Integer> administrationCompletedSubStepNumbers = new ArrayList<>();

    // 물건정리 선택 옵션 번호 목록 (1~4, 독립적)
    @Builder.Default
    @Convert(converter = IntegerListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Integer> belongingsSelectedOptionNumbers = new ArrayList<>();

    // 지원사업 완료 이벤트 번호 목록 (1~5, 독립적)
    // 1~4: 각 토글 확인 완료, 5: 최종 확인 완료 버튼 클릭
    @Builder.Default
    @Convert(converter = IntegerListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Integer> supportCompletedSubStepNumbers = new ArrayList<>();

    public void update(
            boolean hasCompletedGuide,
            int currentStep,
            List<Integer> enteredSteps,
            List<Integer> completedMainSteps,
            List<Integer> restingCompletedSubStepNumbers,
            List<Integer> administrationCompletedSubStepNumbers,
            List<Integer> belongingsSelectedOptionNumbers,
            List<Integer> supportCompletedSubStepNumbers
    ) {
        this.hasCompletedGuide = hasCompletedGuide;
        this.currentStep = currentStep;
        this.enteredSteps = new ArrayList<>(enteredSteps);
        this.completedMainSteps = new ArrayList<>(completedMainSteps);
        this.restingCompletedSubStepNumbers = new ArrayList<>(restingCompletedSubStepNumbers);
        this.administrationCompletedSubStepNumbers = new ArrayList<>(administrationCompletedSubStepNumbers);
        this.belongingsSelectedOptionNumbers = new ArrayList<>(belongingsSelectedOptionNumbers);
        this.supportCompletedSubStepNumbers = new ArrayList<>(supportCompletedSubStepNumbers);
    }
}
