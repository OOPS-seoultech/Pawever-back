package com.pawever.backend.memorial.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.pet.entity.Pet;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "emergency_progresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmergencyProgress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false, unique = true)
    private Pet pet;

    @Column(nullable = false)
    private Integer restingActiveStepNumber;

    @Column(nullable = false)
    private Integer restingCompletedStepCount;

    @Column(nullable = false)
    private Boolean funeralCompanyCompleted;

    public void update(int restingActiveStepNumber, int restingCompletedStepCount, boolean funeralCompanyCompleted) {
        this.restingActiveStepNumber = restingActiveStepNumber;
        this.restingCompletedStepCount = restingCompletedStepCount;
        this.funeralCompanyCompleted = funeralCompanyCompleted;
    }
}
