package com.pawever.backend.pet.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Pet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "breed_id")
    private Breed breed;

    @Column(nullable = false)
    private String name;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private Float weight;

    @Builder.Default
    private Boolean isNeutered = false;

    @Column(unique = true)
    private String inviteCode;

    private String profileImageUrl;

    @Builder.Default
    private Boolean emergencyMode = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LifecycleStatus lifecycleStatus;

    private LocalDateTime deathDate;

    @PrePersist
    public void generateInviteCode() {
        if (this.inviteCode == null) {
            this.inviteCode = newInviteCode();
        }
    }

    // 대시 제거 후 12자(48비트) — 8자(32비트) 대비 충돌·온라인 열거 위험을 크게 낮춘다.
    private static String newInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public void update(
            String name,
            LocalDate birthDate,
            Gender gender,
            Float weight,
            Boolean isNeutered,
            Breed breed,
            LocalDateTime deathDate
    ) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.weight = weight;
        this.isNeutered = isNeutered;
        this.breed = breed;
        this.deathDate = deathDate;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void activateEmergencyMode() {
        this.emergencyMode = true;
        this.lifecycleStatus = LifecycleStatus.AFTER_FAREWELL;
        if (this.deathDate == null) {
            this.deathDate = LocalDateTime.now();
        }
    }

    public void completeEmergencyMode() {
        this.emergencyMode = false;
        this.lifecycleStatus = LifecycleStatus.AFTER_FAREWELL;
        if (this.deathDate == null) {
            this.deathDate = LocalDateTime.now();
        }
    }

    public void deactivateEmergencyMode() {
        this.emergencyMode = false;
        this.lifecycleStatus = LifecycleStatus.BEFORE_FAREWELL;
        this.deathDate = null;
    }

    public void regenerateInviteCode() {
        this.inviteCode = newInviteCode();
    }
}
