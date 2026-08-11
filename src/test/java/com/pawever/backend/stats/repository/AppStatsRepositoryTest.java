package com.pawever.backend.stats.repository;

import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.stats.dto.UserStatsRow;
import com.pawever.backend.user.entity.ReferralType;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 집계 쿼리는 애플리케이션이 뜰 때까지 잘못을 알려 주지 않는다.
 * 실제 DB에 걸어 보고 넘어간다.
 */
@DataJpaTest
@ActiveProfiles("test")
class AppStatsRepositoryTest {

    @Autowired private AppStatsRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private PetRepository petRepository;
    @Autowired private UserPetRepository userPetRepository;

    @Test
    void userRowsCarryOnlyTheColumnsStatisticsNeed() {
        userRepository.save(User.builder()
                .kakaoId("kakao-1")
                .ageRange("20~29")
                .gender("male")
                .referralType(ReferralType.INSTAGRAM)
                .onboardingComplete(true)
                .build());
        userRepository.save(User.builder()
                .naverId("naver-1")
                .deletedAt(LocalDateTime.of(2026, 7, 20, 0, 0))
                .build());

        List<UserStatsRow> rows = repository.findUserRows();

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(UserStatsRow::active).singleElement()
                .satisfies(row -> {
                    assertThat(row.kakaoId()).isEqualTo("kakao-1");
                    assertThat(row.ageRange()).isEqualTo("20~29");
                    assertThat(row.referralType()).isEqualTo(ReferralType.INSTAGRAM);
                    assertThat(row.onboardingComplete()).isTrue();
                });
    }

    @Test
    void pushTokenCountSkipsWithdrawnMembers() {
        userRepository.save(User.builder().kakaoId("kakao-1").fcmToken("token-1").build());
        userRepository.save(User.builder()
                .kakaoId("kakao-2")
                .fcmToken("token-2")
                .deletedAt(LocalDateTime.of(2026, 7, 20, 0, 0))
                .build());

        assertThat(repository.countUsersWithPushToken()).isEqualTo(1);
    }

    @Test
    void aMemberWithTwoPetsIsStillOneMember() {
        User user = userRepository.save(User.builder().kakaoId("kakao-1").build());
        Pet first = savePet("first");
        Pet second = savePet("second");
        userPetRepository.save(UserPet.builder().user(user).pet(first).isOwner(true).build());
        userPetRepository.save(UserPet.builder().user(user).pet(second).isOwner(false).build());

        assertThat(repository.countUserPetLinks()).isEqualTo(2);
        assertThat(repository.countUsersWithPet()).isEqualTo(1);
        assertThat(repository.countPets()).isEqualTo(2);
        assertThat(repository.countPetsByLifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)).isEqualTo(2);
    }

    @Test
    void everyActivityQueryRuns() {
        // 다른 도메인 테이블을 세는 쿼리다. 수치보다 쿼리가 도는지가 중요하다.
        assertThat(repository.countPetsInEmergencyMode()).isZero();
        assertThat(repository.countPetMissions()).isZero();
        assertThat(repository.countCompletedPetMissions()).isZero();
        assertThat(repository.countMemorialComments()).isZero();
        assertThat(repository.countEmergencyProgresses()).isZero();
        assertThat(repository.countFarewellPreviewProgresses()).isZero();
        assertThat(repository.countServiceReviews()).isZero();
    }

    private Pet savePet(String name) {
        return petRepository.save(Pet.builder()
                .name(name)
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .build());
    }
}
