package com.pawever.backend.goodssurvey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGoodsSurveyPhotoUploadRequest(
        @NotBlank @Size(max = 36) String clientFileId,
        @NotBlank @Size(max = 50) String contentType,
        @Min(1) @Max(10 * 1024 * 1024) long size
) {
}
