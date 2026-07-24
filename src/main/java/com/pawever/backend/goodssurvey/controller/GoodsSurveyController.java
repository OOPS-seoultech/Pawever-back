package com.pawever.backend.goodssurvey.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.goodssurvey.dto.CreateGoodsSurveyPhotoUploadRequest;
import com.pawever.backend.goodssurvey.dto.CreateGoodsSurveyRequest;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyApplicationResponse;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyCampaignResponse;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyCompletionResponse;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyDraftResponse;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyPhotoUploadResponse;
import com.pawever.backend.goodssurvey.dto.SaveGoodsSurveyDraftRequest;
import com.pawever.backend.goodssurvey.dto.SaveGoodsSurveyStoryRequest;
import com.pawever.backend.goodssurvey.dto.SubmitGoodsSurveyApplicationRequest;
import com.pawever.backend.goodssurvey.service.GoodsSurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/goods-survey")
@RequiredArgsConstructor
public class GoodsSurveyController {

    private static final String EDIT_TOKEN_HEADER = "X-Survey-Edit-Token";

    private final GoodsSurveyService service;

    @GetMapping("/campaign")
    public ApiResponse<GoodsSurveyCampaignResponse> getCampaign() {
        return ApiResponse.ok(service.getCampaign());
    }

    @PostMapping("/responses")
    public ApiResponse<GoodsSurveyDraftResponse> createDraft(
            @Valid @RequestBody CreateGoodsSurveyRequest request
    ) {
        return ApiResponse.ok(service.createDraft(request));
    }

    @PatchMapping("/responses/{responseId}")
    public ApiResponse<Void> saveDraft(
            @PathVariable String responseId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken,
            @Valid @RequestBody SaveGoodsSurveyDraftRequest request
    ) {
        service.saveDraft(responseId, editToken, request);
        return ApiResponse.ok();
    }

    @PostMapping("/responses/{responseId}/complete")
    public ApiResponse<GoodsSurveyCompletionResponse> completeSurvey(
            @PathVariable String responseId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken,
            @Valid @RequestBody SaveGoodsSurveyDraftRequest request
    ) {
        return ApiResponse.ok(service.completeSurvey(responseId, editToken, request));
    }

    @PutMapping("/responses/{responseId}/story")
    public ApiResponse<Void> saveStory(
            @PathVariable String responseId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken,
            @Valid @RequestBody SaveGoodsSurveyStoryRequest request
    ) {
        service.saveStory(responseId, editToken, request);
        return ApiResponse.ok();
    }

    @PostMapping("/responses/{responseId}/photos/presign")
    public ApiResponse<GoodsSurveyPhotoUploadResponse> createPhotoUpload(
            @PathVariable String responseId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken,
            @Valid @RequestBody CreateGoodsSurveyPhotoUploadRequest request
    ) {
        return ApiResponse.ok(service.createPhotoUpload(responseId, editToken, request));
    }

    @PostMapping("/responses/{responseId}/photos/{photoId}/confirm")
    public ApiResponse<GoodsSurveyPhotoUploadResponse> confirmPhoto(
            @PathVariable String responseId,
            @PathVariable String photoId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken
    ) {
        return ApiResponse.ok(service.confirmPhoto(responseId, editToken, photoId));
    }

    @PostMapping("/responses/{responseId}/application")
    public ApiResponse<GoodsSurveyApplicationResponse> submitApplication(
            @PathVariable String responseId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitGoodsSurveyApplicationRequest request
    ) {
        return ApiResponse.ok(
                service.submitApplication(
                        responseId,
                        editToken,
                        idempotencyKey,
                        request
                )
        );
    }
}
