package com.pawever.backend.pet.repository;

import com.pawever.backend.pet.entity.PetExpiredInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetExpiredInviteCodeRepository extends JpaRepository<PetExpiredInviteCode, Long> {

    boolean existsByInviteCode(String inviteCode);
}
