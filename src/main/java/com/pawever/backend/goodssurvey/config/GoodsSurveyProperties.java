package com.pawever.backend.goodssurvey.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "survey.goods")
public class GoodsSurveyProperties {

    private String campaignId = "goods-2026-07";
    private int reservationMinutes = 15;
    private int uploadUrlMinutes = 10;
    private int personalDataRetentionDays = 90;
    private String questionnaireVersion = "2026-07-23-v1";
    private String privacyConsentVersion = "2026-07-23";
    private String photoBucket;
}
