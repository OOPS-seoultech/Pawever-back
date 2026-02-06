package com.pawever.backend.pet.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.pet.dto.*;
import com.pawever.backend.pet.entity.AnimalType;
import com.pawever.backend.pet.entity.Breed;
import com.pawever.backend.pet.repository.AnimalTypeRepository;
import com.pawever.backend.pet.repository.BreedRepository;
import com.pawever.backend.pet.service.PetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final AnimalTypeRepository animalTypeRepository;
    private final BreedRepository breedRepository;

    /**
     * 반려동물 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PetResponse>> createPet(@Valid @RequestBody PetCreateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.createPet(userId, request)));
    }

    /**
     * 내 반려동물 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PetListResponse>>> getMyPets() {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.getMyPets(userId)));
    }

    /**
     * 현재 선택된 반려동물 조회
     */
    @GetMapping("/selected")
    public ResponseEntity<ApiResponse<PetResponse>> getSelectedPet() {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.getSelectedPet(userId)));
    }

    /**
     * 반려동물 상세 조회
     */
    @GetMapping("/{petId}")
    public ResponseEntity<ApiResponse<PetResponse>> getPet(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.getPet(userId, petId)));
    }

    /**
     * 반려동물 정보 수정
     */
    @PutMapping("/{petId}")
    public ResponseEntity<ApiResponse<PetResponse>> updatePet(
            @PathVariable Long petId,
            @Valid @RequestBody PetUpdateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.updatePet(userId, petId, request)));
    }

    /**
     * 반려동물 삭제
     */
    @DeleteMapping("/{petId}")
    public ResponseEntity<ApiResponse<Void>> deletePet(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        petService.deletePet(userId, petId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 반려동물 전환 (selected)
     */
    @PostMapping("/{petId}/switch")
    public ResponseEntity<ApiResponse<PetResponse>> switchPet(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.switchPet(userId, petId)));
    }

    /**
     * 반려동물 대표 이미지 업로드
     */
    @PostMapping("/{petId}/image")
    public ResponseEntity<ApiResponse<PetResponse>> uploadPetImage(
            @PathVariable Long petId,
            @RequestParam("file") MultipartFile file) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.uploadPetImage(userId, petId, file)));
    }

    /**
     * 동물 종류 목록 조회
     */
    @GetMapping("/animal-types")
    public ResponseEntity<ApiResponse<List<AnimalType>>> getAnimalTypes() {
        return ResponseEntity.ok(ApiResponse.ok(animalTypeRepository.findAll()));
    }

    /**
     * 품종 목록 조회 (동물 종류별)
     */
    @GetMapping("/animal-types/{animalTypeId}/breeds")
    public ResponseEntity<ApiResponse<List<Breed>>> getBreeds(@PathVariable Long animalTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(breedRepository.findByAnimalTypeId(animalTypeId)));
    }
}
