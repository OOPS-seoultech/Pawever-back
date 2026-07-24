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
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyStory;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyCampaignRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoodsSurveyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> GOODS_TYPES =
            Set.of("acrylic", "face", "backplate", "figure", "custom");
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
    private final GoodsSurveyPhotoStorage photoStorage;
    private final GoodsSurveyAnswerValidator answerValidator;
    private final ObjectMapper objectMapper;
    private final HmacHasher hmacHasher;
    private final GoodsSurveyProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GoodsSurveyCampaignResponse getCampaign() {
        Instant now = clock.instant();
        GoodsSurveyCampaign campaign = findCampaign();
        long active = countActiveAllocations(campaign.getId(), now);
        return new GoodsSurveyCampaignResponse(
                campaign.getId(),
                campaign.getCapacity(),
                campaign.allocated(active),
                campaign.remaining(active),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                campaign.isOpenAt(now) && campaign.remaining(active) > 0
        );
    }

    @Transactional
    public GoodsSurveyDraftResponse createDraft(CreateGoodsSurveyRequest request) {
        validateQuestionnaireVersion(request.questionnaireVersion());
        validateGoodsTypeId(request.selectedGoods());
        GoodsSurveyCampaign campaign = findCampaign();
        Instant now = clock.instant();
        if (!campaign.isOpenAt(now)) {
            throw new CustomException(ErrorCode.SURVEY_CAMPAIGN_CLOSED);
        }
        long active = countActiveAllocations(campaign.getId(), now);
        if (campaign.remaining(active) <= 0) {
            throw new CustomException(ErrorCode.SURVEY_CAMPAIGN_FULL);
        }

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

        if (response.hasActiveReservation(now)) {
            GoodsSurveyCampaign campaign = findCampaign();
            int remaining = campaign.remaining(countActiveAllocations(campaign.getId(), now));
            return completion(response, remaining);
        }
        if (response.getStatus() == GoodsSurveyResponseStatus.COMPLETED_NO_SLOT
                || response.getStatus() == GoodsSurveyResponseStatus.TERMINATED) {
            GoodsSurveyCampaign campaign = findCampaign();
            int remaining = campaign.remaining(countActiveAllocations(campaign.getId(), now));
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
        if (!campaign.isOpenAt(now)) {
            throw new CustomException(ErrorCode.SURVEY_CAMPAIGN_CLOSED);
        }

        long activeBeforeReservation = countActiveAllocations(campaign.getId(), now);
        int remainingBeforeReservation = campaign.remaining(activeBeforeReservation);
        if (remainingBeforeReservation <= 0) {
            response.completeWithoutSlot(now);
            return completion(response, 0);
        }

        response.reserve(
                now,
                now.plus(properties.getReservationMinutes(), ChronoUnit.MINUTES)
        );
        return completion(response, remainingBeforeReservation - 1);
    }

    @Transactional
    public void saveStory(
            String responseId,
            String editToken,
            SaveGoodsSurveyStoryRequest request
    ) {
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        requireReservedOrSubmitted(response);
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
                        request.reviewContactAgreed(),
                        request.interviewAgreed(),
                        properties.getPrivacyConsentVersion(),
                        now
                ));
        story.update(
                storyJson,
                request.analysisAgreed(),
                request.publishAgreed(),
                request.reviewContactAgreed(),
                request.interviewAgreed(),
                properties.getPrivacyConsentVersion(),
                now
        );
        storyRepository.save(story);
    }

    @Transactional
    public GoodsSurveyPhotoUploadResponse createPhotoUpload(
            String responseId,
            String editToken,
            CreateGoodsSurveyPhotoUploadRequest request
    ) {
        Instant now = clock.instant();
        GoodsSurveyResponse response = findAndAuthenticate(responseId, editToken);
        requireActiveReservation(response, now);
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
        requireActiveReservation(response, now);
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
        requireActiveReservation(response, now);
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
        int confirmedPhotos = photoRepository.findAllByIdInAndResponseIdAndStatus(
                uniquePhotoIds,
                responseId,
                GoodsSurveyPhotoStatus.CONFIRMED
        ).size();
        if (confirmedPhotos != uniquePhotoIds.size()) {
            throw new CustomException(ErrorCode.SURVEY_PHOTO_NOT_READY);
        }

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
                countActiveAllocations(campaign.getId(), clock.instant())
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

    private long countActiveAllocations(String campaignId, Instant now) {
        return responseRepository.countActiveAllocations(
                campaignId,
                now,
                GoodsSurveyResponseStatus.RESERVED,
                GoodsSurveyResponseStatus.SUBMITTED
        );
    }

    private void requireActiveReservation(GoodsSurveyResponse response, Instant now) {
        if (!response.hasActiveReservation(now)) {
            if (response.getStatus() == GoodsSurveyResponseStatus.RESERVED) {
                throw new CustomException(ErrorCode.SURVEY_RESERVATION_EXPIRED);
            }
            throw new CustomException(ErrorCode.SURVEY_INVALID_STATE);
        }
    }

    private void requireReservedOrSubmitted(GoodsSurveyResponse response) {
        if (response.getStatus() == GoodsSurveyResponseStatus.SUBMITTED) {
            return;
        }
        requireActiveReservation(response, clock.instant());
    }

    private void validateQuestionnaireVersion(String version) {
        if (!properties.getQuestionnaireVersion().equals(version)) {
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
