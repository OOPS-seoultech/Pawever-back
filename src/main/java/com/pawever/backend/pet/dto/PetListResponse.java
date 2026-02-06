package com.pawever.backend.pet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PetListResponse {
    private Long id;
    private String name;
    private String profileImageUrl;
    private Boolean selected;
}
