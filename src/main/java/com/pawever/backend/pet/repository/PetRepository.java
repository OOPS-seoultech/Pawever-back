package com.pawever.backend.pet.repository;

import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {

    Optional<Pet> findByInviteCode(String inviteCode);

    List<Pet> findByLifecycleStatus(LifecycleStatus lifecycleStatus);

    @Query("""
            select p
            from Pet p
            where p.lifecycleStatus = :lifecycleStatus
              and p.deathDate is not null
              and p.deathDate > :threshold
              and (
                :cursorDeathDate is null
                or p.deathDate < :cursorDeathDate
                or (p.deathDate = :cursorDeathDate and p.id < :cursorId)
              )
            order by p.deathDate desc, p.id desc
            """)
    List<Pet> findRecentMemorialFeed(
            @Param("lifecycleStatus") LifecycleStatus lifecycleStatus,
            @Param("threshold") LocalDateTime threshold,
            @Param("cursorDeathDate") LocalDateTime cursorDeathDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select p
            from Pet p
            where p.lifecycleStatus = :lifecycleStatus
              and (p.deathDate is null or p.deathDate <= :threshold)
              and (
                :cursorSortDeathDate is null
                or coalesce(p.deathDate, :nullCursorDeathDate) < :cursorSortDeathDate
                or (
                  coalesce(p.deathDate, :nullCursorDeathDate) = :cursorSortDeathDate
                  and p.id < :cursorId
                )
              )
            order by
              case when p.deathDate is null then 1 else 0 end asc,
              p.deathDate desc,
              p.id desc
            """)
    List<Pet> findPastMemorialFeed(
            @Param("lifecycleStatus") LifecycleStatus lifecycleStatus,
            @Param("threshold") LocalDateTime threshold,
            @Param("cursorSortDeathDate") LocalDateTime cursorSortDeathDate,
            @Param("cursorId") Long cursorId,
            @Param("nullCursorDeathDate") LocalDateTime nullCursorDeathDate,
            Pageable pageable
    );
}
