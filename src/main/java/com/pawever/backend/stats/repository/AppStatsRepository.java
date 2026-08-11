package com.pawever.backend.stats.repository;

import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.stats.dto.UserStatsRow;
import com.pawever.backend.user.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 앱 통계 전용 조회.
 *
 * 도메인 리포지토리에 집계 쿼리를 섞으면 서비스 로직과 통계가 함께 흔들린다.
 * 읽기 전용 창구를 따로 두고 여기에만 모은다.
 */
public interface AppStatsRepository extends Repository<User, Long> {

    @Query("""
            select new com.pawever.backend.stats.dto.UserStatsRow(
                u.createdAt,
                u.deletedAt,
                u.onboardingComplete,
                u.kakaoId,
                u.naverId,
                u.appleId,
                u.referralType,
                u.ageRange,
                u.gender,
                u.notificationAgreedAt,
                u.marketingAgreedAt
            )
            from User u
            """)
    List<UserStatsRow> findUserRows();

    /** 푸시를 실제로 보낼 수 있는 회원. 동의와 별개로 토큰이 있어야 도달한다. */
    @Query("select count(u) from User u where u.deletedAt is null and u.fcmToken is not null")
    long countUsersWithPushToken();

    @Query("select count(p) from Pet p")
    long countPets();

    @Query("select count(p) from Pet p where p.lifecycleStatus = :status")
    long countPetsByLifecycleStatus(@Param("status") LifecycleStatus status);

    @Query("select count(p) from Pet p where p.emergencyMode = true")
    long countPetsInEmergencyMode();

    /** 보호자-반려동물 연결. 한 마리를 여러 보호자가 함께 보므로 펫 수와 다르다. */
    @Query("select count(up) from UserPet up")
    long countUserPetLinks();

    @Query("select count(distinct up.user.id) from UserPet up where up.user.deletedAt is null")
    long countUsersWithPet();

    @Query("select count(pm) from PetMission pm")
    long countPetMissions();

    @Query("select count(pm) from PetMission pm where pm.completed = true")
    long countCompletedPetMissions();

    @Query("select count(c) from Comment c")
    long countMemorialComments();

    @Query("select count(e) from EmergencyProgress e")
    long countEmergencyProgresses();

    @Query("select count(f) from FarewellPreviewProgress f")
    long countFarewellPreviewProgresses();

    @Query("select count(r) from ServiceReview r")
    long countServiceReviews();
}
