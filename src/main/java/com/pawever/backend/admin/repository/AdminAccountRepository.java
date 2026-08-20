package com.pawever.backend.admin.repository;

import com.pawever.backend.admin.entity.AdminAccount;
import com.pawever.backend.admin.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByEmail(String email);

    Optional<AdminAccount> findByInviteTokenHash(String inviteTokenHash);

    boolean existsByRole(AdminRole role);

    List<AdminAccount> findAllByOrderByCreatedAtAsc();
}
