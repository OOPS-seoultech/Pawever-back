package com.pawever.backend.sharing.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetExpiredInviteCodeRepository;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.sharing.dto.InviteCodeResponse;
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

@ExtendWith(MockitoExtension.class)
class SharingServiceTest {

    @Mock private PetRepository petRepository;
    @Mock private UserPetRepository userPetRepository;
    @Mock private UserRepository userRepository;
    @Mock private PetExpiredInviteCodeRepository petExpiredInviteCodeRepository;

    @InjectMocks
    private SharingService sharingService;

    @Test
    void getInviteCode_whenNotOwned_throwsPetNotOwned() {
        when(userPetRepository.findByUserIdAndPetId(1L, 10L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> sharingService.getInviteCode(1L, 10L));

        assertEquals(ErrorCode.PET_NOT_OWNED, ex.getErrorCode());
    }

    @Test
    void getInviteCode_success_returnsInviteCodeAndPetInfo() {
        Pet pet = Pet.builder()
                .id(10L)
                .name("pet")
                .inviteCode("ABCDEFGH")
                .build();
        User user = User.builder().id(1L).build();
        UserPet userPet = UserPet.builder().user(user).pet(pet).isOwner(true).build();

        when(userPetRepository.findByUserIdAndPetId(1L, 10L)).thenReturn(Optional.of(userPet));

        InviteCodeResponse response = sharingService.getInviteCode(1L, 10L);

        assertEquals("ABCDEFGH", response.getInviteCode());
        assertEquals(10L, response.getPetId());
        assertEquals("pet", response.getPetName());
    }

    @Test
    void joinByInviteCode_whenInviteCodeExpired_throwsExpiredInviteCode() {
        when(petRepository.findByInviteCode("CODE")).thenReturn(Optional.empty());
        when(petExpiredInviteCodeRepository.existsByInviteCode("CODE")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class, () -> sharingService.joinByInviteCode(1L, "CODE"));

        assertEquals(ErrorCode.EXPIRED_INVITE_CODE, ex.getErrorCode());
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
    }

    @Test
    void joinByInviteCode_whenAlreadyShared_throwsAlreadyShared() {
        Pet pet = Pet.builder().id(10L).name("pet").inviteCode("CODE").build();
        when(petRepository.findByInviteCode("CODE")).thenReturn(Optional.of(pet));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userPetRepository.existsByUserIdAndPetId(1L, 10L)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class, () -> sharingService.joinByInviteCode(1L, "CODE"));

        assertEquals(ErrorCode.ALREADY_SHARED, ex.getErrorCode());
        verify(userPetRepository, never()).save(any());
    }

    @Test
    void joinByInviteCode_success_savesGuestMemberAndSelectsPet() {
        Pet pet = Pet.builder().id(10L).name("pet").inviteCode("CODE").build();
        User user = User.builder().id(1L).selectedPetId(null).build();

        when(petRepository.findByInviteCode("CODE")).thenReturn(Optional.of(pet));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userPetRepository.existsByUserIdAndPetId(1L, 10L)).thenReturn(false);
        when(userPetRepository.countByUserIdAndIsOwnerFalse(1L)).thenReturn(0L);

        sharingService.joinByInviteCode(1L, "CODE");

        assertEquals(10L, user.getSelectedPetId());
        verify(userPetRepository).save(argThat(up ->
                up.getUser().getId().equals(1L) &&
                up.getPet().getId().equals(10L) &&
                Boolean.FALSE.equals(up.getIsOwner())
        ));
    }
}

