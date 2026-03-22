package com.pawever.backend.mission.entity;

import com.pawever.backend.pet.entity.Pet;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pet_missions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PetMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Builder.Default
    private Boolean completed = false;

    private LocalDateTime completedAt;

    private String imageUrl;

    private String mediaUrl;

    private String mediaType;

    private String mediaFormat;

    private Long mediaSizeBytes;

    private Integer mediaDurationSec;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String mediaWaveform;

    public void complete() {
        this.completed = true;
        this.completedAt = LocalDateTime.now();
    }

    public void complete(String imageUrl) {
        this.completed = true;
        this.completedAt = LocalDateTime.now();
        this.imageUrl = imageUrl;
    }

    public void saveMedia(
            String mediaUrl,
            String mediaType,
            String mediaFormat,
            Long mediaSizeBytes,
            Integer mediaDurationSec,
            String mediaWaveform
    ) {
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.mediaFormat = mediaFormat;
        this.mediaSizeBytes = mediaSizeBytes;
        this.mediaDurationSec = mediaDurationSec;
        this.mediaWaveform = mediaWaveform;
    }
}
