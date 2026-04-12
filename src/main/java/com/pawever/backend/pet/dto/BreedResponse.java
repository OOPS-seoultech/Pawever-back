package com.pawever.backend.pet.dto;

import com.pawever.backend.pet.entity.Breed;
import lombok.Getter;

@Getter
public class BreedResponse {

    private final Long id;
    private final String name;

    private BreedResponse(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static BreedResponse from(Breed breed) {
        return new BreedResponse(breed.getId(), breed.getName());
    }
}
