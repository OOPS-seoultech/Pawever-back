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
            this.inviteCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    public void update(String name, LocalDate birthDate, Gender gender, Float weight, Breed breed) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.weight = weight;
        this.breed = breed;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void activateEmergencyMode() {
        this.emergencyMode = true;
        this.lifecycleStatus = LifecycleStatus.AFTER_FAREWELL;
        this.deathDate = LocalDateTime.now();
    }

    public void regenerateInviteCode() {
        this.inviteCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
