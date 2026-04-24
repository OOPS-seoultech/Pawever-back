package com.pawever.backend.user.repository;

import com.pawever.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoIdAndDeletedAtIsNull(String kakaoId);

    Optional<User> findByNaverIdAndDeletedAtIsNull(String naverId);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByPhoneHashAndDeletedAtIsNull(String phoneHash);

    Optional<User> findByPhoneHashAndDeletedAtIsNull(String phoneHash);

    /** 닉네임 중복 확인 (탈퇴 유저 제외, 특정 유저 제외 가능) */
    boolean existsByNicknameAndDeletedAtIsNullAndIdNot(String nickname, Long excludeUserId);
}
