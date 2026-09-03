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
import com.pawever.backend.goodssurvey.dto.SubscribeGoodsSurveyNoticeRequest;
import com.pawever.backend.goodssurvey.service.GoodsSurveyService;
import jakarta.validation.Valid;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyUnsubscribeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/goods-survey")
@RequiredArgsConstructor
public class GoodsSurveyController {

    private static final String EDIT_TOKEN_HEADER = "X-Survey-Edit-Token";

    private final GoodsSurveyService service;
    private final com.pawever.backend.goodssurvey.service.GoodsSurveyFulfillmentOpsService opsService;

    /**
     * @param channel 판매 경로. 값이 없으면 상시 온라인 판매를 본다.
     *                플리마켓 랜딩은 {@code ?channel=flea} 로 자기 모집의
     *                남은 자리와 개폐 여부를 묻는다.
     */
    @GetMapping("/campaign")
    public ApiResponse<GoodsSurveyCampaignResponse> getCampaign(
            @RequestParam(required = false) String channel
    ) {
        return ApiResponse.ok(service.getCampaign(channel));
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

    /** 설문을 건너뛰고 바로 굿즈 신청으로 간다. 적용가는 정가다. */
    @PostMapping("/responses/{responseId}/direct-purchase")
    public ApiResponse<GoodsSurveyCompletionResponse> startDirectPurchase(
            @PathVariable String responseId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken
    ) {
        return ApiResponse.ok(service.startDirectPurchase(responseId, editToken));
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

    @PostMapping("/responses/{responseId}/notice-subscription")
    public ApiResponse<Void> subscribeNotice(
            @PathVariable String responseId,
            @RequestHeader(EDIT_TOKEN_HEADER) String editToken,
            @Valid @RequestBody SubscribeGoodsSurveyNoticeRequest request
    ) {
        service.subscribeNotice(responseId, editToken, request);
        return ApiResponse.ok();
    }

    /**
     * 안내 메일 수신을 거부한다.
     *
     * 값은 본문으로 받는다. 주소에 실으면 링크를 거치는 모든 곳에 남는다.
     *
     * 그리고 GET 이 아니라 POST 다. 회사 메일 검사기는 메일 안의 링크를 미리
     * 열어 본다. GET 으로 해지되게 두면 사람이 누르지 않았는데 해지된다.
     *
     * 값이 맞지 않아도 성공으로 답한다. 어떤 값이 살아 있는지 알려 주면
     * 그것으로 하나씩 넣어 볼 수 있게 된다.
     */
    @PostMapping("/notice-subscriptions/unsubscribe")
    public ApiResponse<Void> unsubscribeNotice(
            @Valid @RequestBody GoodsSurveyUnsubscribeRequest request) {
        opsService.unsubscribeByToken(request.token());
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
