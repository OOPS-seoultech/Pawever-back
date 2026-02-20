package com.pawever.backend.funeral.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "funeral_companies")
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

    private String location; // 주소 텍스트

    private Double latitude;

    private Double longitude;

    private String phone;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String introduction; // 소개

    @Column(columnDefinition = "TEXT")
    private String guideText; // 안내

    @Column(columnDefinition = "TEXT")
    private String serviceDescription; // 제공 서비스

    @Builder.Default
    private Boolean fullObservation = false; // 전 과정 참관 가능

    @Builder.Default
    private Boolean open24Hours = false; // 24시간 장례 & 상담

    @Builder.Default
    private Boolean pickupService = false; // 픽업 & 운구 서비스

    @Builder.Default
    private Boolean memorialStone = false; // 메모리얼 스톤 (루세떼) 제작

    @Builder.Default
    private Boolean privateMemorialRoom = false; // 단독 추모실 제공

    @Builder.Default
    private Boolean ossuary = false; // 납골당 & 수목장 보유

    @Builder.Default
    private Boolean freeBasicUrn = false; // 기본 유골함 무료 제공
}
