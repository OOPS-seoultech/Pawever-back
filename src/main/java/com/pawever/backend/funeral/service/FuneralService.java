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

import java.util.Comparator;
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

    private static final double DEFAULT_LATITUDE = 37.5559;   // 서울역
    private static final double DEFAULT_LONGITUDE = 126.9723;

    /**
     * 장례업체 목록 조회 (거리순 오름차순)
     */
    public List<FuneralCompanyListResponse> getFuneralCompanyList(Long userId, Double latitude, Double longitude) {
        double userLat = latitude != null ? latitude : DEFAULT_LATITUDE;
        double userLng = longitude != null ? longitude : DEFAULT_LONGITUDE;

        Map<Long, RegistrationType> userRegistrations = buildUserRegistrationMap(userId);

        return funeralCompanyRepository.findAll().stream()
                .map(company -> {
                    Double distance = calculateDistance(userLat, userLng, company.getLatitude(), company.getLongitude());
                    return FuneralCompanyListResponse.of(company, userRegistrations.get(company.getId()), distance);
                })
                .sorted(Comparator.comparingDouble(r -> r.getDistanceKm() != null ? r.getDistanceKm() : Double.MAX_VALUE))
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

        RegistrationType newType = request.getType();

        // 타입이 변경되거나 새로 등록하는 경우에만 한도 체크
        if (existing == null || existing.getType() != newType) {
            validateRegistrationLimit(userId, newType);
        }

        if (existing != null) {
            existing.updateType(newType);
        } else {
            UserFuneralCompany ufc = UserFuneralCompany.builder()
                    .user(user)
                    .funeralCompany(company)
                    .type(newType)
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

    private void validateRegistrationLimit(Long userId, RegistrationType type) {
        if (type == RegistrationType.SAVED) {
            if (userFuneralCompanyRepository.countByUserIdAndType(userId, RegistrationType.SAVED) >= 5) {
                throw new CustomException(ErrorCode.SAVED_COMPANY_LIMIT_EXCEEDED);
            }
        } else if (type == RegistrationType.BLOCKED) {
            if (userFuneralCompanyRepository.countByUserIdAndType(userId, RegistrationType.BLOCKED) >= 15) {
                throw new CustomException(ErrorCode.BLOCKED_COMPANY_LIMIT_EXCEEDED);
            }
        }
    }

    private Double calculateDistance(double userLat, double userLng, Double companyLat, Double companyLng) {
        if (companyLat == null || companyLng == null) return null;
        final double R = 6371.0;
        double dLat = Math.toRadians(companyLat - userLat);
        double dLng = Math.toRadians(companyLng - userLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(companyLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Map<Long, RegistrationType> buildUserRegistrationMap(Long userId) {
        return userFuneralCompanyRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        ufc -> ufc.getFuneralCompany().getId(),
                        UserFuneralCompany::getType
                ));
    }
}
