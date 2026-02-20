package com.pawever.backend.funeral.repository;

import com.pawever.backend.funeral.entity.RegistrationType;
import com.pawever.backend.funeral.entity.UserFuneralCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFuneralCompanyRepository extends JpaRepository<UserFuneralCompany, Long> {

    Optional<UserFuneralCompany> findByUserIdAndFuneralCompanyId(Long userId, Long funeralCompanyId);

    List<UserFuneralCompany> findByUserId(Long userId);

    List<UserFuneralCompany> findByUserIdAndType(Long userId, RegistrationType type);

    int countByUserIdAndType(Long userId, RegistrationType type);

    void deleteByUserIdAndFuneralCompanyId(Long userId, Long funeralCompanyId);
}
