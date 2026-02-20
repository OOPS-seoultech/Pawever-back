package com.pawever.backend.funeral.dto;

import com.pawever.backend.funeral.entity.FuneralCompany;
import com.pawever.backend.funeral.entity.RegistrationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FuneralCompanyListResponse {
    private Long id;
    private String name;
    private String location;
    private Double distanceKm;
    private RegistrationType userRegistrationType;

    public static FuneralCompanyListResponse of(FuneralCompany company, RegistrationType userType, Double distanceKm) {
        return FuneralCompanyListResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .location(company.getLocation())
                .distanceKm(distanceKm)
                .userRegistrationType(userType)
                .build();
    }
}
