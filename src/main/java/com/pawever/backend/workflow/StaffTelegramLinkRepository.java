package com.pawever.backend.workflow;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StaffTelegramLinkRepository extends JpaRepository<StaffTelegramLink,Long> {
  Optional<StaffTelegramLink> findByTelegramUserId(Long id);
  Optional<StaffTelegramLink> findByCodeHash(String hash);
}
