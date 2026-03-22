package com.pawever.backend.memorial.service;

import com.pawever.backend.farewellpreview.repository.FarewellPreviewProgressRepository;
import com.pawever.backend.funeral.repository.PetFuneralCompanyRepository;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.memorial.dto.*;
import com.pawever.backend.memorial.entity.Comment;
import com.pawever.backend.memorial.entity.CommentReport;
import com.pawever.backend.memorial.entity.ReportReason;
import com.pawever.backend.memorial.repository.CommentReportRepository;
import com.pawever.backend.memorial.repository.CommentRepository;
import com.pawever.backend.memorial.repository.ReportReasonRepository;
import com.pawever.backend.pet.dto.PetResponse;
import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemorialService {

    private static final int DEFAULT_RECENT_SIZE = 6;
    private static final int DEFAULT_PAST_SIZE = 24;
    private static final int MAX_RECENT_SIZE = 10;
    private static final int MAX_PAST_SIZE = 30;
    private static final LocalDateTime NULL_CURSOR_DEATH_DATE = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final DateTimeFormatter CURSOR_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;
    private final FarewellPreviewProgressRepository farewellPreviewProgressRepository;
    private final PetFuneralCompanyRepository petFuneralCompanyRepository;
    private final PetRepository petRepository;
    private final ReportReasonRepository reportReasonRepository;
    private final UserPetRepository userPetRepository;
    private final UserRepository userRepository;

    /**
     * 긴급 대처 모드 - 추모관 생성 및 반려동물 상태 전환
     */
    @Transactional
    public EmergencyResponse activateEmergencyMode(Long userId, Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        getAccessibleUserPet(userId, petId);

        if (pet.getLifecycleStatus() == LifecycleStatus.AFTER_FAREWELL) {
            throw new CustomException(ErrorCode.MEMORIAL_ALREADY_EXISTS);
        }

        // 반려동물 긴급 모드 활성화 (lifecycleStatus -> AFTER_FAREWELL, deathDate 설정)
        pet.activateEmergencyMode();

        return EmergencyResponse.builder()
                .memorial(MemorialResponse.from(pet))
                .build();
    }

    /**
     * 긴급 대처 모드 완료 - 이별 후 상태 유지, 긴급 모드 종료
     */
    @Transactional
    public PetResponse completeEmergencyMode(Long userId, Long petId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserPet userPet = getAccessibleUserPet(userId, petId);
        Pet pet = userPet.getPet();

        validateEmergencyModeActive(pet);

        pet.completeEmergencyMode();
        resetEmergencyModeData(petId);

        return PetResponse.of(pet, user.getSelectedPetId(), userPet.getIsOwner());
    }

    /**
     * 긴급 대처 모드 해제 - 이별 전 상태로 되돌리고 긴급 모드 종료
     */
    @Transactional
    public PetResponse deactivateEmergencyMode(Long userId, Long petId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserPet userPet = getOwnerUserPet(userId, petId);
        Pet pet = userPet.getPet();

        validateEmergencyModeActive(pet);

        pet.deactivateEmergencyMode();
        resetEmergencyModeData(petId);

        return PetResponse.of(pet, user.getSelectedPetId(), true);
    }

    /**
     * 별자리 추모관 feed 조회 (recent/past 버퍼 단위 cursor pagination)
     */
    public MemorialFeedResponse getMemorialFeed(
            Integer recentSize,
            Integer pastSize,
            String recentCursor,
            String pastCursor,
            LocalDateTime referenceTime
    ) {
        int normalizedRecentSize = normalizeSize(recentSize, DEFAULT_RECENT_SIZE, 1, MAX_RECENT_SIZE);
        int normalizedPastSize = normalizeSize(pastSize, DEFAULT_PAST_SIZE, 1, MAX_PAST_SIZE);
        LocalDateTime effectiveReferenceTime = referenceTime != null ? referenceTime : LocalDateTime.now();
        LocalDateTime sevenDaysAgo = effectiveReferenceTime.minusDays(7);

        MemorialCursor recentCursorInfo = parseCursor(recentCursor);
        MemorialCursor pastCursorInfo = parseCursor(pastCursor);

        CursorSlice recentSlice = fetchRecentMemorials(sevenDaysAgo, recentCursorInfo, normalizedRecentSize);
        CursorSlice pastSlice = fetchPastMemorials(sevenDaysAgo, pastCursorInfo, normalizedPastSize);

        return MemorialFeedResponse.builder()
                .referenceTime(effectiveReferenceTime)
                .recentMemorials(recentSlice.memorials())
                .pastMemorials(pastSlice.memorials())
                .recentPageInfo(recentSlice.pageInfo())
                .pastPageInfo(pastSlice.pageInfo())
                .build();
    }

    /**
     * 별자리 상세 조회 (펫 정보 + 댓글 목록)
     */
    public MemorialDetailResponse getMemorialDetail(Long userId, Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        if (pet.getLifecycleStatus() != LifecycleStatus.AFTER_FAREWELL) {
            throw new CustomException(ErrorCode.MEMORIAL_NOT_FOUND);
        }

        // 해당 펫과 연결된 유저인지 확인하여 댓글 삭제 권한 판단
        Set<Long> connectedUserIds = userPetRepository.findByPetId(petId).stream()
                .map(up -> up.getUser().getId())
                .collect(Collectors.toSet());

        List<CommentResponse> comments = commentRepository.findByPetIdOrderByCreatedAtDesc(petId)
                .stream()
                .map(comment -> {
                    boolean canDelete = comment.getUser().getId().equals(userId)
                            || connectedUserIds.contains(userId);
                    return CommentResponse.of(comment, canDelete);
                })
                .toList();

        return MemorialDetailResponse.builder()
                .memorial(MemorialResponse.from(pet))
                .comments(comments)
                .build();
    }

    /**
     * 댓글 작성
     */
    @Transactional
    public CommentResponse createComment(Long userId, Long petId, CommentCreateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        Comment comment = Comment.builder()
                .user(user)
                .pet(pet)
                .content(request.getContent())
                .build();
        comment = commentRepository.save(comment);

        return CommentResponse.of(comment, true);
    }

    /**
     * 댓글 수정
     */
    @Transactional
    public CommentResponse updateComment(Long userId, Long commentId, CommentUpdateRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        comment.updateContent(request.getContent());

        return CommentResponse.of(comment, true);
    }

    /**
     * 댓글 삭제 (작성자 본인 or 해당 펫과 연결된 유저)
     */
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        Long petId = comment.getPet().getId();
        boolean isConnectedUser = userPetRepository.existsByUserIdAndPetId(userId, petId);
        boolean isCommentOwner = comment.getUser().getId().equals(userId);

        if (!isCommentOwner && !isConnectedUser) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        commentRepository.delete(comment);
    }

    /**
     * 댓글 신고 사유 목록 조회 (추모관 댓글 신고용)
     */
    public List<ReportReasonResponse> getReportReasons() {
        return reportReasonRepository.findAllByOrderByOrderIndexAscIdAsc().stream()
                .map(ReportReasonResponse::from)
                .toList();
    }

    /**
     * 추모관 댓글 신고. 사유는 N개 선택 가능하고, 해당 없으면 customText만 입력 가능.
     */
    @Transactional
    public void reportComment(Long userId, Long commentId, CommentReportRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        User reporter = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        boolean hasReasons = request.getReasonIds() != null && !request.getReasonIds().isEmpty();
        boolean hasCustomText = request.getCustomText() != null && !request.getCustomText().isBlank();

        if (!hasReasons && !hasCustomText) {
            throw new CustomException(ErrorCode.REPORT_REASON_REQUIRED);
        }

        List<ReportReason> reasons = List.of();
        if (hasReasons) {
            reasons = reportReasonRepository.findAllById(request.getReasonIds());
            if (reasons.size() != request.getReasonIds().size()) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
        }

        String customText = hasCustomText ? request.getCustomText().trim() : null;

        CommentReport report = CommentReport.builder()
                .comment(comment)
                .reporter(reporter)
                .customText(customText)
                .reasons(reasons)
                .build();
        commentReportRepository.save(report);
    }

    private UserPet getOwnerUserPet(Long userId, Long petId) {
        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (!Boolean.TRUE.equals(userPet.getIsOwner())) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }

        return userPet;
    }

    private UserPet getAccessibleUserPet(Long userId, Long petId) {
        return userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));
    }

    private void validateEmergencyModeActive(Pet pet) {
        if (!Boolean.TRUE.equals(pet.getEmergencyMode())) {
            throw new CustomException(ErrorCode.EMERGENCY_MODE_NOT_ACTIVE);
        }
    }

    private void resetEmergencyModeData(Long petId) {
        farewellPreviewProgressRepository.deleteByPetId(petId);
        petFuneralCompanyRepository.deleteByPetId(petId);
    }

    private CursorSlice fetchRecentMemorials(LocalDateTime sevenDaysAgo, MemorialCursor cursor, int size) {
        List<Pet> pets = petRepository.findRecentMemorialFeed(
                LifecycleStatus.AFTER_FAREWELL,
                sevenDaysAgo,
                cursor.deathDate(),
                cursor.id(),
                PageRequest.of(0, size + 1)
        );
        return toCursorSlice(pets, size);
    }

    private CursorSlice fetchPastMemorials(LocalDateTime sevenDaysAgo, MemorialCursor cursor, int size) {
        LocalDateTime cursorSortDeathDate = null;
        Long cursorId = null;
        if (!cursor.isEmpty()) {
            cursorSortDeathDate = cursor.hasNullDeathDate() ? NULL_CURSOR_DEATH_DATE : cursor.deathDate();
            cursorId = cursor.id();
        }

        List<Pet> pets = petRepository.findPastMemorialFeed(
                LifecycleStatus.AFTER_FAREWELL,
                sevenDaysAgo,
                cursorSortDeathDate,
                cursorId,
                NULL_CURSOR_DEATH_DATE,
                PageRequest.of(0, size + 1)
        );
        return toCursorSlice(pets, size);
    }

    private CursorSlice toCursorSlice(List<Pet> pets, int size) {
        boolean hasNext = pets.size() > size;
        List<Pet> pagePets = hasNext ? pets.subList(0, size) : pets;
        String nextCursor = null;

        if (hasNext && !pagePets.isEmpty()) {
            Pet lastPet = pagePets.get(pagePets.size() - 1);
            nextCursor = encodeCursor(lastPet.getDeathDate(), lastPet.getId());
        }

        return new CursorSlice(
                pagePets.stream().map(MemorialResponse::from).toList(),
                MemorialCursorPageInfo.builder()
                        .hasNext(hasNext)
                        .nextCursor(nextCursor)
                        .build()
        );
    }

    private int normalizeSize(Integer requestedSize, int defaultSize, int min, int max) {
        if (requestedSize == null) {
            return defaultSize;
        }
        if (requestedSize < min || requestedSize > max) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return requestedSize;
    }

    private MemorialCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return MemorialCursor.empty();
        }

        int separatorIndex = cursor.lastIndexOf('_');
        if (separatorIndex <= 0 || separatorIndex == cursor.length() - 1) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String deathDateToken = cursor.substring(0, separatorIndex);
        String idToken = cursor.substring(separatorIndex + 1);

        try {
            Long id = Long.parseLong(idToken);
            if ("null".equalsIgnoreCase(deathDateToken)) {
                return new MemorialCursor(null, id, true);
            }

            return new MemorialCursor(LocalDateTime.parse(deathDateToken, CURSOR_FORMATTER), id, false);
        } catch (NumberFormatException | DateTimeParseException ex) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String encodeCursor(LocalDateTime deathDate, Long petId) {
        if (deathDate == null) {
            return "null_" + petId;
        }
        return deathDate.format(CURSOR_FORMATTER) + "_" + petId;
    }

    private record CursorSlice(List<MemorialResponse> memorials, MemorialCursorPageInfo pageInfo) {}

    private record MemorialCursor(LocalDateTime deathDate, Long id, boolean hasNullDeathDate) {
        private static MemorialCursor empty() {
            return new MemorialCursor(null, null, false);
        }

        private boolean isEmpty() {
            return id == null;
        }
    }
}
