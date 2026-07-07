package com.pawever.backend.user.repository;

import com.pawever.backend.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoIdAndDeletedAtIsNull(String kakaoId);

    Optional<User> findByNaverIdAndDeletedAtIsNull(String naverId);

    Optional<User> findByAppleIdAndDeletedAtIsNull(String appleId);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 동시 생성/참여 레이스에서 한도(소유 펫 1, 게스트 10) 초과를 막기 위해 유저 행을 비관적 락으로 조회한다.
     * 같은 유저에 대한 createPet / joinByInviteCode 가 직렬화된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    boolean existsByPhoneHashAndDeletedAtIsNull(String phoneHash);

    Optional<User> findByPhoneHashAndDeletedAtIsNull(String phoneHash);

    Optional<User> findByEmailHashAndDeletedAtIsNull(String emailHash);

    /** 닉네임 중복 확인 (탈퇴 유저 제외, 특정 유저 제외 가능) */
    boolean existsByNicknameAndDeletedAtIsNullAndIdNot(String nickname, Long excludeUserId);
}
