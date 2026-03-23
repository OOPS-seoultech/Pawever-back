package com.pawever.backend.funeral.dto;

import com.pawever.backend.funeral.entity.FuneralCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FuneralCompanyOptionsResponse {
    private Boolean fullObservation;
    private Boolean open24Hours;
    private Boolean pickupService;
    private Boolean memorialStone;
    private Boolean privateMemorialRoom;
    private Boolean ossuary;
    private Boolean freeBasicUrn;

    public static FuneralCompanyOptionsResponse from(FuneralCompany company) {
        return FuneralCompanyOptionsResponse.builder()
                .fullObservation(company.getFullObservation())
                .open24Hours(company.getOpen24Hours())
                .pickupService(company.getPickupService())
                .memorialStone(company.getMemorialStone())
                .privateMemorialRoom(company.getPrivateMemorialRoom())
                .ossuary(company.getOssuary())
                .freeBasicUrn(company.getFreeBasicUrn())
                .build();
    }
}
