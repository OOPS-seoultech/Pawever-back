package com.pawever.backend.funeral.repository;

import com.pawever.backend.funeral.entity.PetFuneralCompany;
import com.pawever.backend.funeral.entity.RegistrationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PetFuneralCompanyRepository extends JpaRepository<PetFuneralCompany, Long> {

    Optional<PetFuneralCompany> findByPetIdAndFuneralCompanyId(Long petId, Long funeralCompanyId);

    List<PetFuneralCompany> findByPetId(Long petId);

    @Query("""
            select petFuneralCompany
            from PetFuneralCompany petFuneralCompany
            join fetch petFuneralCompany.funeralCompany
            where petFuneralCompany.pet.id = :petId
              and petFuneralCompany.type = :type
            """)
    List<PetFuneralCompany> findByPetIdAndTypeWithFuneralCompany(
            @Param("petId") Long petId,
            @Param("type") RegistrationType type
    );

    int countByPetIdAndType(Long petId, RegistrationType type);

    void deleteByPetId(Long petId);

    void deleteByPetIdAndFuneralCompanyId(Long petId, Long companyId);
}
