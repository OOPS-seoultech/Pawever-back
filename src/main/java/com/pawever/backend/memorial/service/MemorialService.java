package com.pawever.backend.memorial.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.memorial.dto.*;
import com.pawever.backend.memorial.entity.Comment;
import com.pawever.backend.memorial.entity.Guide;
import com.pawever.backend.memorial.entity.Memorial;
import com.pawever.backend.memorial.repository.CommentRepository;
import com.pawever.backend.memorial.repository.GuideRepository;
import com.pawever.backend.memorial.repository.MemorialRepository;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
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

    private final MemorialRepository memorialRepository;
    private final CommentRepository commentRepository;
    private final GuideRepository guideRepository;
    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;
    private final UserRepository userRepository;

    /**
     * 긴급 대처 모드 - Memorial 생성 + 이별 가이드 반환
     */
    @Transactional
    public EmergencyResponse activateEmergencyMode(Long userId, Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (memorialRepository.existsByPetId(petId)) {
            throw new CustomException(ErrorCode.MEMORIAL_ALREADY_EXISTS);
        }

        // 반려동물 긴급 모드 활성화 (lifecycleStatus -> AFTER_FAREWELL)
        pet.activateEmergencyMode();

        // Memorial 생성
        Memorial memorial = Memorial.builder()
                .pet(pet)
                .deathDate(LocalDateTime.now())
                .build();
        memorial = memorialRepository.save(memorial);

        // 이별 가이드 데이터 반환
        List<Guide> guides = guideRepository.findAll();
        List<GuideResponse> guideResponses = guides.stream()
                .map(GuideResponse::from)
                .toList();

        return EmergencyResponse.builder()
                .memorial(MemorialResponse.from(memorial))
                .guides(guideResponses)
                .build();
    }

    /**
     * 별자리 추모관 목록 조회 (7일 이내 / 이전 분리)
     */
    public MemorialListResponse getMemorialList() {
        List<Memorial> allMemorials = memorialRepository.findAll();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        List<MemorialResponse> recentMemorials = allMemorials.stream()
                .filter(m -> m.getDeathDate() != null && m.getDeathDate().isAfter(sevenDaysAgo))
                .map(MemorialResponse::from)
                .toList();

        List<MemorialResponse> pastMemorials = allMemorials.stream()
                .filter(m -> m.getDeathDate() == null || m.getDeathDate().isBefore(sevenDaysAgo) || m.getDeathDate().isEqual(sevenDaysAgo))
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
        Memorial memorial = memorialRepository.findByPetId(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMORIAL_NOT_FOUND));

        // 해당 펫과 연결된 유저인지 확인하여 댓글 삭제 권한 판단
        Set<Long> connectedUserIds = userPetRepository.findByPetId(petId).stream()
                .map(up -> up.getUser().getId())
                .collect(Collectors.toSet());

        List<CommentResponse> comments = commentRepository.findByMemorialIdOrderByCreatedAtDesc(memorial.getId())
                .stream()
                .map(comment -> {
                    // 댓글 삭제 가능: 댓글 작성자 본인 or 해당 펫과 연결된 유저
                    boolean canDelete = comment.getUser().getId().equals(userId)
                            || connectedUserIds.contains(userId);
                    return CommentResponse.of(comment, canDelete);
                })
                .toList();

        return MemorialDetailResponse.builder()
                .memorial(MemorialResponse.from(memorial))
                .comments(comments)
                .build();
    }

    /**
     * 댓글 작성
     */
    @Transactional
    public CommentResponse createComment(Long userId, Long memorialId, CommentCreateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Memorial memorial = memorialRepository.findById(memorialId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMORIAL_NOT_FOUND));

        Comment comment = Comment.builder()
                .user(user)
                .memorial(memorial)
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

        Long petId = comment.getMemorial().getPet().getId();
        boolean isConnectedUser = userPetRepository.existsByUserIdAndPetId(userId, petId);
        boolean isCommentOwner = comment.getUser().getId().equals(userId);

        if (!isCommentOwner && !isConnectedUser) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        commentRepository.delete(comment);
    }

    /**
     * 이별 가이드 데이터 조회
     */
    public List<GuideResponse> getGuides() {
        return guideRepository.findAll().stream()
                .map(GuideResponse::from)
                .toList();
    }
}
