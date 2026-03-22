package com.pawever.backend.mission.service;

import com.pawever.backend.farewellpreview.service.FarewellPreviewProgressService;
import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.mission.dto.*;
import com.pawever.backend.mission.entity.*;
import com.pawever.backend.mission.repository.*;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    private final MissionRepository missionRepository;
    private final PetMissionRepository petMissionRepository;
    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;
    private final StorageService storageService;
    private final FarewellPreviewProgressService farewellPreviewProgressService;

    /**
     * 발자국 남기기 미션 목록 + 달성 현황 조회
     */
    public MissionProgressResponse getMissionProgress(Long userId, Long petId) {
        validatePetAccess(userId, petId);

        List<Mission> allMissions = missionRepository.findAllByOrderByOrderIndexAscIdAsc();
        List<PetMission> petMissions = petMissionRepository.findByPetId(petId);
        Map<Long, PetMission> petMissionMap = petMissions.stream()
                .collect(Collectors.toMap(pm -> pm.getMission().getId(), Function.identity()));

        List<MissionResponse> missionResponses = allMissions.stream()
                .map(mission -> MissionResponse.of(mission, petMissionMap.get(mission.getId())))
                .toList();

        long completed = petMissions.stream().filter(PetMission::getCompleted).count();

        return MissionProgressResponse.builder()
                .completed(completed)
                .total(allMissions.size())
                .missions(missionResponses)
                .build();
    }

    /**
     * 발자국 남기기 미션 완료 처리
     */
    @Transactional
    public MissionResponse completeMission(Long userId, Long petId, Long missionId, MultipartFile file) {
        validatePetAccess(userId, petId);

        Pet pet = getPet(petId);
        Mission mission = getMission(missionId);
        PetMission petMission = getOrCreatePetMission(pet, mission);

        if (file != null && !file.isEmpty()) {
            if (petMission.getImageUrl() != null) {
                storageService.delete(petMission.getImageUrl());
            }
            String imageUrl = storageService.upload(file, "pets/" + petId + "/missions/" + petMission.getId());
            petMission.complete(imageUrl);
        } else {
            petMission.complete();
        }

        return MissionResponse.of(mission, petMission);
    }

    @Transactional
    public MissionResponse saveMissionRecording(
            Long userId,
            Long petId,
            Long missionId,
            MultipartFile file,
            Integer durationSec,
            String format,
            Long sizeBytes,
            String waveform
    ) {
        validatePetAccess(userId, petId);

        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Pet pet = getPet(petId);
        Mission mission = getMission(missionId);
        PetMission petMission = getOrCreatePetMission(pet, mission);

        if (petMission.getMediaUrl() != null) {
            storageService.delete(petMission.getMediaUrl());
        }

        log.info(
                "미션 녹음 저장 시작. userId={}, petId={}, missionId={}, petMissionId={}, originalFilename={}, contentType={}, size={}",
                userId,
                petId,
                missionId,
                petMission.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );
        String mediaUrl = storageService.upload(file, "pets/" + petId + "/missions/" + petMission.getId() + "/recordings");
        petMission.saveMedia(
                mediaUrl,
                "AUDIO",
                normalizeMediaFormat(format, file.getOriginalFilename()),
                normalizeMediaSizeBytes(sizeBytes, file),
                normalizeDurationSec(durationSec),
                normalizeWaveform(waveform)
        );

        return MissionResponse.of(mission, petMission);
    }

    /**
     * 홈화면 진행률 요약 조회 (미리 살펴보기 %, 미션 완료/전체)
     */
    public HomeProgressResponse getHomeProgress(Long userId, Long petId) {
        validatePetAccess(userId, petId);

        int farewellPreviewProgressPercent = farewellPreviewProgressService.getProgressPercent(userId, petId);
        long missionTotal = missionRepository.count();
        long missionCompleted = petMissionRepository.countByPetIdAndCompletedTrue(petId);

        return HomeProgressResponse.builder()
                .farewellPreviewProgressPercent(farewellPreviewProgressPercent)
                .missionCompleted(missionCompleted)
                .missionTotal(missionTotal)
                .build();
    }

    private void validatePetAccess(Long userId, Long petId) {
        if (!userPetRepository.existsByUserIdAndPetId(userId, petId)) {
            throw new CustomException(ErrorCode.PET_NOT_OWNED);
        }
    }

    private Pet getPet(Long petId) {
        return petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));
    }

    private Mission getMission(Long missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));
    }

    private PetMission getOrCreatePetMission(Pet pet, Mission mission) {
        return petMissionRepository.findByPetIdAndMissionId(pet.getId(), mission.getId())
                .orElseGet(() -> petMissionRepository.save(
                        PetMission.builder()
                                .pet(pet)
                                .mission(mission)
                                .build()
                ));
    }

    private Integer normalizeDurationSec(Integer durationSec) {
        if (durationSec == null) {
            return null;
        }

        return Math.max(0, Math.min(10, durationSec));
    }

    private Long normalizeMediaSizeBytes(Long sizeBytes, MultipartFile file) {
        if (sizeBytes != null && sizeBytes > 0) {
            return sizeBytes;
        }

        long fileSize = file.getSize();
        return fileSize > 0 ? fileSize : null;
    }

    private String normalizeMediaFormat(String format, String originalFilename) {
        if (format != null && !format.isBlank()) {
            return format.trim().toUpperCase(Locale.ROOT);
        }

        if (originalFilename == null || !originalFilename.contains(".")) {
            return null;
        }

        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeWaveform(String waveform) {
        if (waveform == null || waveform.isBlank()) {
            return null;
        }

        return List.of(waveform.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .limit(128)
                .collect(Collectors.joining(","));
    }
}
