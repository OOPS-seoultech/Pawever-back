package com.pawever.backend.user.repository;

import com.pawever.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(String kakaoId);

    Optional<User> findByNaverId(String naverId);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByPhoneHashAndDeletedAtIsNull(String phoneHash);
}
