package com.pawever.backend.memorial.dto;

import com.pawever.backend.pet.entity.Pet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.Period;

@Getter
@Builder
@AllArgsConstructor
public class CommentAuthorPetResponse {
    private Long petId;
    private String petName;
    private String animalTypeName;
    private String breedName;
    private String gender;
    private Integer age;

    public static CommentAuthorPetResponse from(Pet pet) {
        if (pet == null) {
            return null;
        }

        Integer age = null;
        if (pet.getBirthDate() != null) {
            LocalDate endDate = pet.getDeathDate() != null
                    ? pet.getDeathDate().toLocalDate()
                    : LocalDate.now();
            age = Period.between(pet.getBirthDate(), endDate).getYears();
        }

        return CommentAuthorPetResponse.builder()
                .petId(pet.getId())
                .petName(pet.getName())
                .animalTypeName(pet.getBreed() != null ? pet.getBreed().getAnimalType().getName() : null)
                .breedName(pet.getBreed() != null ? pet.getBreed().getName() : null)
                .gender(pet.getGender() != null ? pet.getGender().name() : null)
                .age(age)
                .build();
    }
}
