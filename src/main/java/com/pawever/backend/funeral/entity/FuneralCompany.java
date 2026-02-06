package com.pawever.backend.funeral.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "funeral_company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FuneralCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String introduction;

    @Column(columnDefinition = "TEXT")
    private String guideText;

    @Column(columnDefinition = "TEXT")
    private String serviceDescription;

    @Builder.Default
    private Boolean fullObservation = false;

    @Builder.Default
    private Boolean available24Hours = false;

    @Builder.Default
    private Boolean pickupService = false;

    @Builder.Default
    private Boolean memorialStone = false;

    @Builder.Default
    private Boolean privateMemorialRoom = false;

    @Builder.Default
    private Boolean ossuary = false;
}
