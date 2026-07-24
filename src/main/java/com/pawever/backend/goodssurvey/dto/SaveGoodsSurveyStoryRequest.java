package com.pawever.backend.goodssurvey.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveGoodsSurveyStoryRequest(
        @NotBlank @Size(max = 30) String status,
        @NotBlank @Size(max = 30) String age,
        @NotBlank @Size(max = 50) String condition,
        @NotBlank @Size(max = 1000) String scene,
        @Size(max = 700) String changedDay,
        @Size(max = 700) String startedNow,
        @Size(max = 500) String unsaidSearch,
        @Size(max = 700) String neededHelp,
        @Size(max = 700) String postponed,
        @Size(max = 800) String wishKnownEarlier,
        @Size(max = 800) String finalHelp,
        @Size(max = 100) String oneLine,
        @AssertTrue boolean analysisAgreed,
        boolean publishAgreed,
        boolean reviewContactAgreed,
        boolean interviewAgreed
) {
}
