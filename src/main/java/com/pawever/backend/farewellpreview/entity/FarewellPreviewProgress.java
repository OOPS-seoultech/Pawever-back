package com.pawever.backend.farewellpreview.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.StringListJsonConverter;
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
    private String currentStepId;

    @Builder.Default
    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> enteredStepIds = new ArrayList<>();

    @Column(nullable = false)
    private Boolean farewellMethodConfirmed;

    @Column(nullable = false)
    private Integer restingActiveStepNumber;

    @Column(nullable = false)
    private Integer restingCompletedStepCount;

    @Builder.Default
    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> administrationCompletedItemIds = new ArrayList<>();

    @Builder.Default
    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> belongingsSelectedOptionIds = new ArrayList<>();

    @Column(nullable = false)
    private Boolean belongingsConfirmed;

    @Builder.Default
    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> supportCompletedItemIds = new ArrayList<>();

    @Column(nullable = false)
    private Boolean supportConfirmed;

    public void update(
            boolean hasCompletedGuide,
            String currentStepId,
            List<String> enteredStepIds,
            boolean farewellMethodConfirmed,
            int restingActiveStepNumber,
            int restingCompletedStepCount,
            List<String> administrationCompletedItemIds,
            List<String> belongingsSelectedOptionIds,
            boolean belongingsConfirmed,
            List<String> supportCompletedItemIds,
            boolean supportConfirmed
    ) {
        this.hasCompletedGuide = hasCompletedGuide;
        this.currentStepId = currentStepId;
        this.enteredStepIds = new ArrayList<>(enteredStepIds);
        this.farewellMethodConfirmed = farewellMethodConfirmed;
        this.restingActiveStepNumber = restingActiveStepNumber;
        this.restingCompletedStepCount = restingCompletedStepCount;
        this.administrationCompletedItemIds = new ArrayList<>(administrationCompletedItemIds);
        this.belongingsSelectedOptionIds = new ArrayList<>(belongingsSelectedOptionIds);
        this.belongingsConfirmed = belongingsConfirmed;
        this.supportCompletedItemIds = new ArrayList<>(supportCompletedItemIds);
        this.supportConfirmed = supportConfirmed;
    }
}
