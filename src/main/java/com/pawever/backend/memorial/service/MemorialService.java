package com.pawever.backend.memorial.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.memorial.dto.*;
import com.pawever.backend.memorial.entity.Comment;
import com.pawever.backend.memorial.entity.CommentReport;
import com.pawever.backend.memorial.entity.ReportReason;
import com.pawever.backend.memorial.repository.CommentReportRepository;
import com.pawever.backend.memorial.repository.CommentRepository;
import com.pawever.backend.memorial.repository.ReportReasonRepository;
import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemorialService {

    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;
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

        userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

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
     * 별자리 추모관 목록 조회 (7일 이내 / 이전 분리)
     */
    public MemorialListResponse getMemorialList() {
        List<Pet> deceasedPets = petRepository.findByLifecycleStatus(LifecycleStatus.AFTER_FAREWELL);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        List<MemorialResponse> recentMemorials = deceasedPets.stream()
                .filter(p -> p.getDeathDate() != null && p.getDeathDate().isAfter(sevenDaysAgo))
                .map(MemorialResponse::from)
                .toList();

        List<MemorialResponse> pastMemorials = deceasedPets.stream()
                .filter(p -> p.getDeathDate() == null || !p.getDeathDate().isAfter(sevenDaysAgo))
                .map(MemorialResponse::from)
                .toList();

        return MemorialListResponse.builder()
                .recentMemorials(recentMemorials)
                .pastMemorials(pastMemorials)
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
}
