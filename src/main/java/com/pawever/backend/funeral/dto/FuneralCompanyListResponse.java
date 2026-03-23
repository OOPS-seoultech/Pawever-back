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
    private Double latitude;
    private Double longitude;
    private String introduction;
    private String thumbnailUrl;
    private String basicProductName;
    private Integer basicProductPrice;
    private FuneralCompanyOptionsResponse options;

    public static FuneralCompanyListResponse of(FuneralCompany company, RegistrationType userType, Double distanceKm) {
        return FuneralCompanyListResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .location(company.getLocation())
                .distanceKm(distanceKm)
                .userRegistrationType(userType)
                .latitude(company.getLatitude())
                .longitude(company.getLongitude())
                .introduction(company.getIntroduction())
                .thumbnailUrl(company.getThumbnailUrl())
                .basicProductName(company.getBasicProductName())
                .basicProductPrice(company.getBasicProductPrice())
                .options(FuneralCompanyOptionsResponse.from(company))
                .build();
    }
}
