package com.pawever.backend.funeral.dto;

import com.pawever.backend.funeral.entity.FuneralCompany;
import com.pawever.backend.funeral.entity.RegistrationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FuneralCompanyResponse {
    private Long id;
    private String name;
    private String location;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String email;
    private String introduction;
    private String guideText;
    private String serviceDescription;
    private Boolean fullObservation;
    private Boolean open24Hours;
    private Boolean pickupService;
    private Boolean memorialStone;
    private Boolean privateMemorialRoom;
    private Boolean ossuary;
    private Boolean freeBasicUrn;
    private RegistrationType userRegistrationType;

    public static FuneralCompanyResponse of(FuneralCompany company, RegistrationType userType) {
        return FuneralCompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .location(company.getLocation())
                .latitude(company.getLatitude())
                .longitude(company.getLongitude())
                .phone(company.getPhone())
                .email(company.getEmail())
                .introduction(company.getIntroduction())
                .guideText(company.getGuideText())
                .serviceDescription(company.getServiceDescription())
                .fullObservation(company.getFullObservation())
                .open24Hours(company.getOpen24Hours())
                .pickupService(company.getPickupService())
                .memorialStone(company.getMemorialStone())
                .privateMemorialRoom(company.getPrivateMemorialRoom())
                .ossuary(company.getOssuary())
                .freeBasicUrn(company.getFreeBasicUrn())
                .userRegistrationType(userType)
                .build();
    }
}
