package com.pawever.backend.funeral.repository;

import com.pawever.backend.funeral.entity.PetFuneralCompany;
import com.pawever.backend.funeral.entity.RegistrationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PetFuneralCompanyRepository extends JpaRepository<PetFuneralCompany, Long> {

    Optional<PetFuneralCompany> findByPetIdAndFuneralCompanyId(Long petId, Long funeralCompanyId);

    List<PetFuneralCompany> findByPetId(Long petId);

    int countByPetIdAndType(Long petId, RegistrationType type);

    void deleteByPetId(Long petId);

    void deleteByPetIdAndFuneralCompanyId(Long petId, Long companyId);
}
