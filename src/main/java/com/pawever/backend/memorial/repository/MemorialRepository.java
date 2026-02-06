package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.Memorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemorialRepository extends JpaRepository<Memorial, Long> {

    Optional<Memorial> findByPetId(Long petId);

    boolean existsByPetId(Long petId);

    List<Memorial> findByDeathDateAfter(LocalDateTime dateTime);

    List<Memorial> findByDeathDateBeforeOrDeathDateIsNull(LocalDateTime dateTime);
}
