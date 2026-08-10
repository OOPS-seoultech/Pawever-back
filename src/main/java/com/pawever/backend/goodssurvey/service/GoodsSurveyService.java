package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
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
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyNoticeSubscription;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyStory;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyCampaignRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyNoticeSubscriptionRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoodsSurveyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> GOODS_TYPES =
            Set.of("acrylic", "face", "backplate", "figure", "custom");
    /**
     * 랜딩에서 굿즈를 고르지 않고 설문에 들어온 경우.
     *
     * 예전에는 아무것도 고르지 않아도 아크릴이 자동으로 붙어, 실제 선호가 아닌
     * 기본값이 선호도 집계에 섞였다. 고르지 않았다는 사실도 데이터라서 그대로 남긴다.
     * 굿즈 신청(제작할 물건을 확정하는 단계)에는 쓸 수 없는 값이다.
     */
    private static final String GOODS_UNSELECTED = "unselected";
    private static final Map<String, String> PHOTO_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    private final GoodsSurveyCampaignRepository campaignRepository;
    private final GoodsSurveyResponseRepository responseRepository;
    private final GoodsSurveyStoryRepository storyRepository;
    private final GoodsSurveyFulfillmentRepository fulfillmentRepository;
    private final GoodsSurveyPhotoRepository photoRepository;
    private final GoodsSurveyNoticeSubscriptionRepository noticeSubscriptionRepository;
    private final GoodsSurveyPhotoStorage photoStorage;
    private final GoodsSurveyAnswerValidator answerValidator;
    private final ObjectMapper objectMapper;
    private final HmacHasher hmacHasher;
    private final GoodsSurveyProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GoodsSurveyCampaignResponse getCampaign() {
        GoodsSurveyCampaign campaign = findCampaign();
        long active = countSubmittedAllocations(campaign.getId());
        boolean goodsOpen = campaign.isGoodsAvailable(active);
        return new GoodsSurveyCampaignResponse(
                campaign.getId(),
                campaign.getCapacity(),
                campaign.allocated(active),
                campaign.remaining(active),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                goodsOpen,
                campaign.isSurveyOpen(),
                goodsOpen
        );
    }

    @Transactional
    public GoodsSurveyDraftResponse createDraft(CreateGoodsSurveyRequest request) {
        validateQuestionnaireVersion(request.questionnaireVersion());
        validateSelectedGoods(request.selectedGoods());
        GoodsSurveyCampaign campaign = findCampaign();
        // 굿즈 정원은 보지 않는다. 굿즈가 마감돼도 설문은 계속 받는다.
        if (!campaign.isSurveyOpen()) {
            throw new CustomException(ErrorCode.SURVEY_CAMPAIGN_CLOSED);
        }
        long active = countSubmittedAllocations(campaign.getId());

        String editToken = createEditToken();
        GoodsSurveyResponse response = GoodsSurveyResponse.draft(
                UUID.randomUUID().toString(),
                campaign.getId(),
                request.questionnaireVersion(),
                hmacHasher.hash(editToken),
                request.selectedGoods(),
                serialize(request.tracking())
        );
        responseRepository.save(response);
        return new GoodsSurveyDraftResponse(
                response.getId(),
                editToken,
                response.getStatus().name(),
                campaign.remaining(active)
        );
    }

    @Transactional
    public void saveDraft(
            String responseId,
            String editToken,
            SaveGoodsSurveyDraftRequest request
    ) {
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        if (response.getStatus() != GoodsSurveyResponseStatus.DRAFT) {
            throw new CustomException(ErrorCode.SURVEY_INVALID_STATE);
        }
        saveDraftValues(response, request, false);
    }

    @Transactional
    public GoodsSurveyCompletionResponse completeSurvey(
            String responseId,
            String editToken,
            SaveGoodsSurveyDraftRequest request
    ) {
        Instant now = clock.instant();
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);

        // 예약 시간이 지났어도 설문을 끝낸 사실은 그대로다.
        // 만료를 이유로 튕기면 이미 다 답한 사람이 영영 신청을 못 한다.
        if (response.getStatus() == GoodsSurveyResponseStatus.RESERVED) {
            GoodsSurveyCampaign campaign = findCampaign();
            int remaining = campaign.remaining(countSubmittedAllocations(campaign.getId()));
            return completion(response, remaining);
        }
        if (response.getStatus() == GoodsSurveyResponseStatus.COMPLETED_NO_SLOT
                || response.getStatus() == GoodsSurveyResponseStatus.TERMINATED) {
            GoodsSurveyCampaign campaign = findCampaign();
            int remaining = campaign.remaining(countSubmittedAllocations(campaign.getId()));
            return completion(response, remaining);
        }
        if (response.getStatus() != GoodsSurveyResponseStatus.DRAFT) {
            throw new CustomException(ErrorCode.SURVEY_INVALID_STATE);
        }

        saveDraftValues(response, request, true);
        if (answerValidator.isTerminated(request.answers())) {
            response.terminate(now);
            return completion(response, 0);
        }

        GoodsSurveyCampaign campaign = campaignRepository
                .findByIdForUpdate(properties.getCampaignId())
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_CAMPAIGN_NOT_FOUND));

        long activeBeforeReservation = countSubmittedAllocations(campaign.getId());
        int remainingBeforeReservation = campaign.remaining(activeBeforeReservation);
        // 굿즈가 닫혀 있어도 설문 응답은 그대로 저장한다. 줄 자리가 없을 뿐이라
        // 여기서 예외를 던지면 끝까지 답한 사람이 마지막 한 번에 통째로 잃는다.
        if (!campaign.isGoodsAvailable(activeBeforeReservation)) {
            response.completeWithoutSlot(now);
            return completion(response, remainingBeforeReservation);
        }

        // 예약은 제출 자격을 확인하는 표시일 뿐 자리를 잡아두지 않는다.
        // 자리는 굿즈 제작 정보까지 제출해야 줄어들므로 여기서 1을 빼지 않는다.
        response.reserve(
                now,
                now.plus(properties.getReservationMinutes(), ChronoUnit.MINUTES)
        );
        return completion(response, remainingBeforeReservation);
    }

    @Transactional
    public void saveStory(
            String responseId,
            String editToken,
            SaveGoodsSurveyStoryRequest request
    ) {
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        requireCompletedSurvey(response);
        if (!request.analysisAgreed()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String storyJson = serialize(request);
        Instant now = clock.instant();
        GoodsSurveyStory story = storyRepository.findByResponseId(responseId)
                .orElseGet(() -> GoodsSurveyStory.create(
                        responseId,
                        storyJson,
                        request.analysisAgreed(),
                        request.publishAgreed(),
                        properties.getPrivacyConsentVersion(),
                        now
                ));
        story.update(
                storyJson,
                request.analysisAgreed(),
                request.publishAgreed(),
                properties.getPrivacyConsentVersion(),
                now
        );
        storyRepository.save(story);
    }

    /**
     * 2차 제작 안내를 받을 이메일을 남긴다.
     *
     * 설문을 마친 사람인지는 요청 시점에만 확인하고 저장하지 않는다. 어떤 응답을 한
     * 사람의 주소인지 남기면 익명으로 분석한다는 고지가 거짓이 되기 때문이다.
     */
    @Transactional
    public void subscribeNotice(
            String responseId,
            String editToken,
            SubscribeGoodsSurveyNoticeRequest request
    ) {
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        requireCompletedSurvey(response);
        if (!request.noticeAgreed()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String email = normalizeEmail(request.email());
        String emailHash = hmacHasher.hash("notice:" + email);
        // 이미 신청한 주소라고 알려주면 남의 주소가 신청돼 있는지 확인하는 통로가 된다.
        // 두 번 눌러도 같은 결과로 보이게 두는 편이 안전하고, 사용자에게도 자연스럽다.
        if (noticeSubscriptionRepository.existsByEmailHash(emailHash)) {
            return;
        }

        noticeSubscriptionRepository.save(GoodsSurveyNoticeSubscription.create(
                response.getCampaignId(),
                email,
                emailHash,
                properties.getPrivacyConsentVersion(),
                clock.instant(),
                properties.getNoticeRetentionDays()
        ));
    }

    @Transactional
    public GoodsSurveyPhotoUploadResponse createPhotoUpload(
            String responseId,
            String editToken,
            CreateGoodsSurveyPhotoUploadRequest request
    ) {
        Instant now = clock.instant();
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        requireGoodsSlot(response);
        validateClientFileId(request.clientFileId());
        String extension = PHOTO_EXTENSIONS.get(request.contentType());
        if (extension == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (request.size() < 1 || request.size() > MAX_PHOTO_BYTES) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE);
        }

        GoodsSurveyPhoto photo = photoRepository
                .findByResponseIdAndClientFileId(responseId, request.clientFileId())
                .orElse(null);
        if (photo != null && photo.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED) {
            return new GoodsSurveyPhotoUploadResponse(
                    photo.getId(),
                    photo.getStatus().name(),
                    null,
                    Map.of(),
                    photo.getUploadExpiresAt()
            );
        }

        if (photo == null) {
            long usablePhotos = photoRepository.countUsablePhotos(
                    responseId,
                    now,
                    GoodsSurveyPhotoStatus.PENDING,
                    GoodsSurveyPhotoStatus.CONFIRMED
            );
            if (usablePhotos >= 5) {
                throw new CustomException(ErrorCode.SURVEY_PHOTO_LIMIT_EXCEEDED);
            }
            String photoId = UUID.randomUUID().toString();
            Instant expiresAt = now.plus(properties.getUploadUrlMinutes(), ChronoUnit.MINUTES);
            photo = GoodsSurveyPhoto.pending(
                    photoId,
                    responseId,
                    request.clientFileId(),
                    "goods-survey/" + response.getCampaignId() + "/" + responseId
                            + "/" + photoId + "." + extension,
                    request.contentType(),
                    request.size(),
                    expiresAt
            );
        } else {
            if (!photo.getContentType().equals(request.contentType())
                    || photo.getExpectedSize() != request.size()) {
                throw new CustomException(ErrorCode.SURVEY_IDEMPOTENCY_CONFLICT);
            }
            photo.renewUpload(now.plus(properties.getUploadUrlMinutes(), ChronoUnit.MINUTES));
        }

        GoodsSurveyPhotoStorage.PresignedUpload upload = photoStorage.presignUpload(
                photo.getObjectKey(),
                photo.getContentType(),
                photo.getExpectedSize(),
                Duration.ofMinutes(properties.getUploadUrlMinutes()),
                photo.getUploadExpiresAt()
        );
        photoRepository.save(photo);
        return new GoodsSurveyPhotoUploadResponse(
                photo.getId(),
                photo.getStatus().name(),
                upload.url(),
                upload.headers(),
                upload.expiresAt()
        );
    }

    @Transactional
    public GoodsSurveyPhotoUploadResponse confirmPhoto(
            String responseId,
            String editToken,
            String photoId
    ) {
        Instant now = clock.instant();
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        requireGoodsSlot(response);
        GoodsSurveyPhoto photo = photoRepository.findByIdAndResponseId(photoId, responseId)
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_PHOTO_NOT_FOUND));
        if (photo.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED) {
            return confirmedPhoto(photo);
        }

        GoodsSurveyPhotoStorage.StoredObject stored = photoStorage.head(photo.getObjectKey());
        if (stored.contentLength() != photo.getExpectedSize()
                || !photo.getContentType().equals(stored.contentType())
                || !GoodsSurveyImageSignature.matches(
                        photo.getContentType(),
                        stored.signatureBytes()
                )) {
            throw new CustomException(ErrorCode.SURVEY_PHOTO_NOT_READY);
        }
        photo.confirm(stored.contentLength(), now);
        photoRepository.save(photo);
        return confirmedPhoto(photo);
    }

    @Transactional
    public GoodsSurveyApplicationResponse submitApplication(
            String responseId,
            String editToken,
            String idempotencyKey,
            SubmitGoodsSurveyApplicationRequest request
    ) {
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        GoodsSurveyFulfillment existing = fulfillmentRepository.findByResponseId(responseId)
                .orElse(null);
        if (existing != null) {
            if (!existing.getIdempotencyKey().equals(idempotencyKey)) {
                throw new CustomException(ErrorCode.SURVEY_IDEMPOTENCY_CONFLICT);
            }
            return applicationResponse(response, existing);
        }

        Instant now = clock.instant();
        requireGoodsSlot(response);

        // 굿즈 접수 여부는 여기서 확정한다. 예약이 자리를 잡아두지 않기 때문에
        // 마지막 한 자리를 여러 명이 동시에 향할 수 있고, 스위치를 내린 뒤에도
        // 이미 예약을 받아 둔 사람이 남아 있다. 이 확인이 없으면 둘 다 통과한다.
        //
        // 다 채우고 나서 거절당하는 일이 없도록, 프런트는 이어서 진행할 때
        // 캠페인 상태를 먼저 확인해 마감 화면을 앞에서 보여준다.
        GoodsSurveyCampaign campaign = campaignRepository
                .findByIdForUpdate(response.getCampaignId())
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_CAMPAIGN_NOT_FOUND));
        if (!campaign.isGoodsAvailable(countSubmittedAllocations(campaign.getId()))) {
            throw new CustomException(ErrorCode.SURVEY_CAMPAIGN_FULL);
        }

        validateIdempotencyKey(idempotencyKey);
        validateGoodsType(request.goodsType(), request.customGoods());
        answerValidator.validateTrackingOnly(request.tracking());
        if (!request.privacyAgreed() || !request.shippingConfirmed()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Set<String> uniquePhotoIds = new LinkedHashSet<>(request.photoIds());
        if (uniquePhotoIds.size() != request.photoIds().size()
                || uniquePhotoIds.isEmpty()
                || uniquePhotoIds.size() > 5) {
            throw new CustomException(ErrorCode.SURVEY_PHOTO_NOT_READY);
        }
        List<String> requestedPublicPhotoIds =
                request.publicPhotoIds() == null ? List.of() : request.publicPhotoIds();
        Set<String> publicPhotoIds = new LinkedHashSet<>(requestedPublicPhotoIds);
        if (publicPhotoIds.size() != requestedPublicPhotoIds.size()
                || !uniquePhotoIds.containsAll(publicPhotoIds)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        List<GoodsSurveyPhoto> confirmedPhotos = photoRepository.findAllByIdInAndResponseIdAndStatus(
                uniquePhotoIds,
                responseId,
                GoodsSurveyPhotoStatus.CONFIRMED
        );
        if (confirmedPhotos.size() != uniquePhotoIds.size()) {
            throw new CustomException(ErrorCode.SURVEY_PHOTO_NOT_READY);
        }
        confirmedPhotos.forEach(
                photo -> photo.setPublicationAgreed(publicPhotoIds.contains(photo.getId()))
        );
        photoRepository.saveAll(confirmedPhotos);

        String normalizedPhone = normalizePhone(request.phone());
        String phoneHash = hmacHasher.hash(response.getCampaignId() + ":" + normalizedPhone);
        if (fulfillmentRepository.existsByPhoneHash(phoneHash)) {
            throw new CustomException(ErrorCode.SURVEY_DUPLICATE_PHONE);
        }
        if (fulfillmentRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new CustomException(ErrorCode.SURVEY_IDEMPOTENCY_CONFLICT);
        }

        GoodsSurveyFulfillment fulfillment = GoodsSurveyFulfillment.create(
                responseId,
                idempotencyKey,
                request.conversionEventId(),
                serialize(request.tracking()),
                request.goodsType(),
                trimToNull(request.customGoods()),
                request.petName().trim(),
                request.guardianName().trim(),
                normalizedPhone,
                phoneHash,
                request.postalCode().trim(),
                request.address().trim(),
                trimToNull(request.addressDetail()),
                properties.getPrivacyConsentVersion(),
                now
        );
        fulfillmentRepository.save(fulfillment);
        response.submit();
        return applicationResponse(response, fulfillment);
    }

    private void saveDraftValues(
            GoodsSurveyResponse response,
            SaveGoodsSurveyDraftRequest request,
            boolean complete
    ) {
        answerValidator.validateCurrentQuestionId(request.currentQuestionId());
        if (complete) {
            answerValidator.validateComplete(
                    request.answers(),
                    request.surveyActiveMs(),
                    request.questionActiveMs(),
                    request.tracking()
            );
        } else {
            answerValidator.validateDraft(
                    request.answers(),
                    request.surveyActiveMs(),
                    request.questionActiveMs(),
                    request.tracking()
            );
        }
        response.saveDraft(
                serialize(request.answers()),
                request.currentQuestionId(),
                request.surveyActiveMs(),
                serialize(request.questionActiveMs()),
                serialize(request.tracking())
        );
        responseRepository.save(response);
    }

    private GoodsSurveyApplicationResponse applicationResponse(
            GoodsSurveyResponse response,
            GoodsSurveyFulfillment fulfillment
    ) {
        GoodsSurveyCampaign campaign = findCampaign();
        int remaining = campaign.remaining(
                countSubmittedAllocations(campaign.getId())
        );
        return new GoodsSurveyApplicationResponse(
                response.getId(),
                fulfillment.getId(),
                GoodsSurveyResponseStatus.SUBMITTED.name(),
                remaining
        );
    }

    private GoodsSurveyPhotoUploadResponse confirmedPhoto(GoodsSurveyPhoto photo) {
        return new GoodsSurveyPhotoUploadResponse(
                photo.getId(),
                photo.getStatus().name(),
                null,
                Map.of(),
                photo.getUploadExpiresAt()
        );
    }

    private GoodsSurveyCompletionResponse completion(
            GoodsSurveyResponse response,
            int remaining
    ) {
        return new GoodsSurveyCompletionResponse(
                response.getId(),
                response.getStatus().name(),
                remaining,
                response.getReservationExpiresAt()
        );
    }

    private GoodsSurveyResponse findAndAuthenticate(String responseId, String editToken) {
        GoodsSurveyResponse response = responseRepository.findById(responseId)
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND));
        if (editToken == null || editToken.isBlank()) {
            throw new CustomException(ErrorCode.SURVEY_EDIT_TOKEN_INVALID);
        }
        byte[] expected = response.getEditTokenHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = hmacHasher.hash(editToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new CustomException(ErrorCode.SURVEY_EDIT_TOKEN_INVALID);
        }
        return response;
    }

    private GoodsSurveyCampaign findCampaign() {
        return campaignRepository.findById(properties.getCampaignId())
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_CAMPAIGN_NOT_FOUND));
    }

    private long countSubmittedAllocations(String campaignId) {
        return responseRepository.countSubmittedAllocations(
                campaignId,
                GoodsSurveyResponseStatus.SUBMITTED
        );
    }

    /**
     * 설문을 끝낸 사람인지 확인한다.
     *
     * 굿즈 자리를 받았는지는 보지 않는다. 굿즈가 마감돼도 사연은 계속 받아야 하고,
     * 사연은 굿즈와 달리 자리 수와 상관없이 남길 수 있기 때문이다.
     * 굿즈를 전제로 하는 요청은 {@link #requireGoodsSlot(GoodsSurveyResponse)}를 쓴다.
     */
    private void requireCompletedSurvey(GoodsSurveyResponse response) {
        GoodsSurveyResponseStatus status = response.getStatus();
        if (status == GoodsSurveyResponseStatus.RESERVED
                || status == GoodsSurveyResponseStatus.SUBMITTED
                || status == GoodsSurveyResponseStatus.COMPLETED_NO_SLOT) {
            return;
        }
        throw new CustomException(ErrorCode.SURVEY_INVALID_STATE);
    }

    /**
     * 굿즈 자리를 받은 사람인지 확인한다.
     *
     * 사진은 굿즈 제작에만 쓴다고 고지하고 받는다. 자리가 없는 사람의 사진을
     * 받아 두면 만들지도 않을 반려견 사진을 목적 없이 보관하게 되므로,
     * 사연과 달리 자리를 받은 사람만 통과시킨다.
     *
     * 예약 만료는 보지 않는다. 예약은 더 이상 선착순 자리를 잡아두지 않고
     * (자리는 제출 시점에 센다), 사연을 쓰고 주소를 찾고 사진을 올리다 보면
     * 15분은 쉽게 넘는다. 특히 사진 업로드가 끝난 뒤 확정 단계에서 만료되면
     * 그때까지 쓴 게 다 날아간다. 정원 확인은 submitApplication이 제출 직전에 따로 한다.
     */
    private void requireGoodsSlot(GoodsSurveyResponse response) {
        GoodsSurveyResponseStatus status = response.getStatus();
        if (status == GoodsSurveyResponseStatus.RESERVED
                || status == GoodsSurveyResponseStatus.SUBMITTED) {
            return;
        }
        throw new CustomException(ErrorCode.SURVEY_INVALID_STATE);
    }

    private void validateQuestionnaireVersion(String version) {
        if (!properties.getQuestionnaireVersion().equals(version)
                && !properties.getLegacyQuestionnaireVersions().contains(version)) {
            throw new CustomException(ErrorCode.SURVEY_INVALID_ANSWERS);
        }
    }

    private void validateGoodsType(String goodsType, String customGoods) {
        validateGoodsTypeId(goodsType);
        if ("custom".equals(goodsType)
                && (customGoods == null || customGoods.isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateGoodsTypeId(String goodsType) {
        if (!GOODS_TYPES.contains(goodsType)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 설문 시작 시점의 관심 굿즈. 아직 고르지 않았을 수 있다. */
    private void validateSelectedGoods(String selectedGoods) {
        if (GOODS_UNSELECTED.equals(selectedGoods)) {
            return;
        }
        validateGoodsTypeId(selectedGoods);
    }

    private void validateClientFileId(String clientFileId) {
        try {
            UUID.fromString(clientFileId);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.length() < 8
                || idempotencyKey.length() > 80) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 같은 주소가 대소문자만 달라 두 번 들어오지 않도록 맞춰 둔다. */
    private String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        if (!digits.matches("^01[016789][0-9]{7,8}$")) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return digits;
    }

    private String createEditToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
