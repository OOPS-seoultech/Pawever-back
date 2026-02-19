package com.pawever.backend.pet.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.pet.dto.*;
import com.pawever.backend.pet.entity.AnimalType;
import com.pawever.backend.pet.entity.Breed;
import com.pawever.backend.pet.repository.AnimalTypeRepository;
import com.pawever.backend.pet.repository.BreedRepository;
import com.pawever.backend.pet.service.PetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Pet", description = "반려동물 관련 API")
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final AnimalTypeRepository animalTypeRepository;
    private final BreedRepository breedRepository;

    @Operation(summary = "반려동물 등록", description = "새로운 반려동물을 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PetResponse>> createPet(@Valid @RequestBody PetCreateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.createPet(userId, request)));
    }

    @Operation(summary = "내 반려동물 목록 조회", description = "현재 로그인한 사용자의 반려동물 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PetListResponse>>> getMyPets() {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.getMyPets(userId)));
    }

    @Operation(summary = "현재 선택된 반려동물 조회", description = "현재 선택(활성화)된 반려동물의 상세 정보를 조회합니다.")
    @GetMapping("/selected")
    public ResponseEntity<ApiResponse<PetResponse>> getSelectedPet() {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.getSelectedPet(userId)));
    }

    @Operation(summary = "반려동물 상세 조회", description = "특정 반려동물의 상세 정보를 조회합니다.")
    @GetMapping("/{petId}")
    public ResponseEntity<ApiResponse<PetResponse>> getPet(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.getPet(userId, petId)));
    }

    @Operation(summary = "반려동물 정보 수정", description = "반려동물의 정보를 수정합니다.")
    @PutMapping("/{petId}")
    public ResponseEntity<ApiResponse<PetResponse>> updatePet(
            @PathVariable Long petId,
            @Valid @RequestBody PetUpdateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.updatePet(userId, petId, request)));
    }

    @Operation(summary = "반려동물 삭제", description = "반려동물을 삭제합니다.")
    @DeleteMapping("/{petId}")
    public ResponseEntity<ApiResponse<Void>> deletePet(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        petService.deletePet(userId, petId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "반려동물 전환", description = "현재 선택된 반려동물을 변경합니다.")
    @PostMapping("/{petId}/switch")
    public ResponseEntity<ApiResponse<PetResponse>> switchPet(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.switchPet(userId, petId)));
    }

    @Operation(summary = "반려동물 대표 이미지 업로드", description = "반려동물의 대표 이미지를 업로드합니다.")
    @PostMapping("/{petId}/image")
    public ResponseEntity<ApiResponse<PetResponse>> uploadPetImage(
            @PathVariable Long petId,
            @RequestPart("file") MultipartFile file) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(petService.uploadPetImage(userId, petId, file)));
    }

    @Operation(summary = "동물 종류 목록 조회", description = "등록 가능한 동물 종류(개, 고양이 등) 목록을 조회합니다.")
    @GetMapping("/animal-types")
    public ResponseEntity<ApiResponse<List<AnimalType>>> getAnimalTypes() {
        return ResponseEntity.ok(ApiResponse.ok(animalTypeRepository.findAll()));
    }

    @Operation(summary = "품종 목록 조회", description = "특정 동물 종류에 해당하는 품종 목록을 조회합니다.")
    @GetMapping("/animal-types/{animalTypeId}/breeds")
    public ResponseEntity<ApiResponse<List<Breed>>> getBreeds(@PathVariable Long animalTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(breedRepository.findByAnimalTypeId(animalTypeId)));
    }
}
