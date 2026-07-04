package com.pawever.backend.memorial.service;

import com.pawever.backend.farewellpreview.repository.FarewellPreviewProgressRepository;
import com.pawever.backend.funeral.repository.PetFuneralCompanyRepository;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.memorial.repository.CommentReportRepository;
import com.pawever.backend.memorial.repository.CommentRepository;
import com.pawever.backend.memorial.repository.EmergencyProgressRepository;
import com.pawever.backend.memorial.repository.ReportReasonRepository;
import com.pawever.backend.notification.service.FcmService;
import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 긴급 대처(사망) 모드 전환은 소유자만 가능해야 한다 — 게스트의 권한상승 방지 검증.
 */
@ExtendWith(MockitoExtension.class)
class MemorialServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentReportRepository commentReportRepository;
    @Mock private EmergencyProgressRepository emergencyProgressRepository;
    @Mock private FarewellPreviewProgressRepository farewellPreviewProgressRepository;
    @Mock private PetFuneralCompanyRepository petFuneralCompanyRepository;
    @Mock private PetRepository petRepository;
    @Mock private ReportReasonRepository reportReasonRepository;
    @Mock private UserPetRepository userPetRepository;
    @Mock private UserRepository userRepository;
    @Mock private FcmService fcmService;

    @InjectMocks
    private MemorialService memorialService;

    private UserPet guest(Long userId, Pet pet) {
        return UserPet.builder().user(User.builder().id(userId).build()).pet(pet).isOwner(false).build();
    }

    @Test
    void activateEmergencyMode_whenGuest_throwsNotOwner_andPetStaysAlive() {
        Pet pet = Pet.builder().id(10L).lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL).build();
        when(petRepository.findById(10L)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByUserIdAndPetId(1L, 10L)).thenReturn(Optional.of(guest(1L, pet)));

        CustomException ex = assertThrows(CustomException.class, () -> memorialService.activateEmergencyMode(1L, 10L));

        assertEquals(ErrorCode.NOT_OWNER, ex.getErrorCode());
        assertEquals(LifecycleStatus.BEFORE_FAREWELL, pet.getLifecycleStatus()); // 사망 전환 안 됨
    }

    @Test
    void completeEmergencyMode_whenGuest_throwsNotOwner() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        Pet pet = Pet.builder().id(10L).build();
        when(userPetRepository.findByUserIdAndPetId(1L, 10L)).thenReturn(Optional.of(guest(1L, pet)));

        CustomException ex = assertThrows(CustomException.class, () -> memorialService.completeEmergencyMode(1L, 10L));

        assertEquals(ErrorCode.NOT_OWNER, ex.getErrorCode());
        verify(emergencyProgressRepository, never()).deleteByPetId(anyLong()); // 데이터 삭제 안 됨
    }

    @Test
    void deactivateEmergencyMode_whenGuest_throwsNotOwner() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        Pet pet = Pet.builder().id(10L).build();
        when(userPetRepository.findByUserIdAndPetId(1L, 10L)).thenReturn(Optional.of(guest(1L, pet)));

        CustomException ex = assertThrows(CustomException.class, () -> memorialService.deactivateEmergencyMode(1L, 10L));

        assertEquals(ErrorCode.NOT_OWNER, ex.getErrorCode());
    }
}
