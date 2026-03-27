package com.pawever.backend.farewellpreview.repository;

import com.pawever.backend.farewellpreview.entity.FarewellPreviewProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarewellPreviewProgressRepository extends JpaRepository<FarewellPreviewProgress, Long> {

    Optional<FarewellPreviewProgress> findByPetId(Long petId);

    void deleteByPetId(Long petId);
}
