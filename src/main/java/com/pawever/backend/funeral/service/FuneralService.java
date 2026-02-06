package com.pawever.backend.funeral.service;

import com.pawever.backend.funeral.dto.*;
import com.pawever.backend.funeral.entity.*;
import com.pawever.backend.funeral.repository.*;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FuneralService {

    private final FuneralCompanyRepository funeralCompanyRepository;
    private final UserFuneralCompanyRepository userFuneralCompanyRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;

    /**
     * 장례업체 목록 조회
     */
    public List<FuneralCompanyListResponse> getFuneralCompanyList(Long userId) {
        List<FuneralCompany> companies = funeralCompanyRepository.findAll();

        Map<Long, RegistrationType> userRegistrations = buildUserRegistrationMap(userId);

        return companies.stream()
                .map(company -> FuneralCompanyListResponse.of(company, userRegistrations.get(company.getId())))
                .toList();
    }

    /**
     * 장례업체 상세 조회
     */
    public FuneralCompanyResponse getFuneralCompanyDetail(Long userId, Long companyId) {
        FuneralCompany company = funeralCompanyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUNERAL_COMPANY_NOT_FOUND));

        RegistrationType userType = userFuneralCompanyRepository
                .findByUserIdAndFuneralCompanyId(userId, companyId)
                .map(UserFuneralCompany::getType)
                .orElse(null);

        return FuneralCompanyResponse.of(company, userType);
    }

    /**
     * 장례업체 저장/피하기 등록
     */
    @Transactional
    public void registerFuneralCompany(Long userId, Long companyId, RegisterFuneralCompanyRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        FuneralCompany company = funeralCompanyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUNERAL_COMPANY_NOT_FOUND));

        UserFuneralCompany existing = userFuneralCompanyRepository
                .findByUserIdAndFuneralCompanyId(userId, companyId)
                .orElse(null);

        if (existing != null) {
            existing.updateType(request.getType());
        } else {
            UserFuneralCompany ufc = UserFuneralCompany.builder()
                    .user(user)
                    .funeralCompany(company)
                    .type(request.getType())
                    .build();
            userFuneralCompanyRepository.save(ufc);
        }
    }

    /**
     * 장례업체 저장/피하기 해제
     */
    @Transactional
    public void unregisterFuneralCompany(Long userId, Long companyId) {
        userFuneralCompanyRepository.deleteByUserIdAndFuneralCompanyId(userId, companyId);
    }

    /**
     * 리뷰 작성
     */
    @Transactional
    public ReviewResponse createReview(Long userId, Long companyId, ReviewCreateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        FuneralCompany company = funeralCompanyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUNERAL_COMPANY_NOT_FOUND));

        Pet pet = petRepository.findById(request.getPetId())
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        userPetRepository.findByUserIdAndPetId(userId, pet.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        Review review = Review.builder()
                .user(user)
                .pet(pet)
                .funeralCompany(company)
                .rating(request.getRating())
                .content(request.getContent())
                .build();
        review = reviewRepository.save(review);

        return ReviewResponse.of(review, true);
    }

    /**
     * 장례업체 리뷰 목록 조회
     */
    public List<ReviewResponse> getReviews(Long userId, Long companyId) {
        return reviewRepository.findByFuneralCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(review -> ReviewResponse.of(review, review.getUser().getId().equals(userId)))
                .toList();
    }

    /**
     * 리뷰 삭제
     */
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        reviewRepository.delete(review);
    }

    private Map<Long, RegistrationType> buildUserRegistrationMap(Long userId) {
        return userFuneralCompanyRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        ufc -> ufc.getFuneralCompany().getId(),
                        UserFuneralCompany::getType
                ));
    }
}
