package com.pawever.backend.pet.dto;

import com.pawever.backend.global.util.UrlUtils;
import com.pawever.backend.pet.entity.Gender;
import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class PetResponse {
    private Long id;
    private String name;
    private String animalTypeName;
    private String breedName;
    private LocalDate birthDate;
    private Gender gender;
    private Float weight;
    private Boolean isNeutered;
    private String profileImageUrl;
    private LifecycleStatus lifecycleStatus;
    private String inviteCode;
    private LocalDateTime deathDate;
    private Boolean emergencyMode;
    private Boolean selected;
    private Boolean isOwner;

    public static PetResponse of(Pet pet, Long selectedPetId, Boolean isOwner) {
        return PetResponse.builder()
                .id(pet.getId())
                .name(pet.getName())
                .animalTypeName(pet.getBreed() != null ? pet.getBreed().getAnimalType().getName() : null)
                .breedName(pet.getBreed() != null ? pet.getBreed().getName() : null)
                .birthDate(pet.getBirthDate())
                .gender(pet.getGender())
                .weight(pet.getWeight())
                .isNeutered(pet.getIsNeutered())
                .profileImageUrl(UrlUtils.toHttpsUrl(pet.getProfileImageUrl()))
                .lifecycleStatus(pet.getLifecycleStatus())
                .inviteCode(pet.getInviteCode())
                .deathDate(pet.getDeathDate())
                .emergencyMode(pet.getEmergencyMode())
                .selected(pet.getId().equals(selectedPetId))
                .isOwner(isOwner)
                .build();
    }
}
