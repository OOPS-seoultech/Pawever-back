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
    private String introduction;
    private String guideText;
    private String serviceDescription;
    private Boolean fullObservation;
    private Boolean available24Hours;
    private Boolean pickupService;
    private Boolean memorialStone;
    private Boolean privateMemorialRoom;
    private Boolean ossuary;
    private RegistrationType userRegistrationType;

    public static FuneralCompanyResponse of(FuneralCompany company, RegistrationType userType) {
        return FuneralCompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .location(company.getLocation())
                .introduction(company.getIntroduction())
                .guideText(company.getGuideText())
                .serviceDescription(company.getServiceDescription())
                .fullObservation(company.getFullObservation())
                .available24Hours(company.getAvailable24Hours())
                .pickupService(company.getPickupService())
                .memorialStone(company.getMemorialStone())
                .privateMemorialRoom(company.getPrivateMemorialRoom())
                .ossuary(company.getOssuary())
                .userRegistrationType(userType)
                .build();
    }
}
