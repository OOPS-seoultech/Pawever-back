package com.pawever.backend.user.service;

import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.memorial.repository.CommentRepository;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.pet.service.PetService;
import com.pawever.backend.user.dto.UserUpdateRequest;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserPetRepository userPetRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private PetService petService;
    @Mock private StorageService storageService;
    @Mock private HmacHasher hmacHasher;

    @InjectMocks
    private UserService userService;

    @Test
    void checkNicknameAvailable_whenBlank_returnsUnavailableFalse() {
        assertFalse(userService.checkNicknameAvailable(1L, null).isAvailable());
        assertFalse(userService.checkNicknameAvailable(1L, "").isAvailable());
        assertFalse(userService.checkNicknameAvailable(1L, "   ").isAvailable());
        verifyNoInteractions(userRepository);
    }

    @Test
    void updateProfile_whenPhoneTaken_throwsDuplicatePhone() {
        User existing = User.builder().id(1L).phoneHash("oldHash").build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(hmacHasher.hash("010")).thenReturn("newHash");
        when(userRepository.existsByPhoneHashAndDeletedAtIsNull("newHash")).thenReturn(true);

        UserUpdateRequest request = new UserUpdateRequest();
        ReflectionTestUtils.setField(request, "name", "name");
        ReflectionTestUtils.setField(request, "phone", "010");

        CustomException ex = assertThrows(CustomException.class, () -> userService.updateProfile(1L, request));
        assertEquals(ErrorCode.DUPLICATE_PHONE, ex.getErrorCode());
    }

    @Test
    void withdraw_success_deletesOwnedPets_detachesComments_andMarksUserDeleted() {
        User user = User.builder().id(1L).deletedAt(null).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        Pet ownedPet1 = Pet.builder().id(10L).name("p1").build();
        Pet ownedPet2 = Pet.builder().id(20L).name("p2").build();
        UserPet ownerUp1 = UserPet.builder().user(user).pet(ownedPet1).isOwner(true).build();
        UserPet ownerUp2 = UserPet.builder().user(user).pet(ownedPet2).isOwner(true).build();
        UserPet guestUp = UserPet.builder().user(user).pet(Pet.builder().id(30L).build()).isOwner(false).build();
        when(userPetRepository.findByUserId(1L)).thenReturn(List.of(ownerUp1, ownerUp2, guestUp));

        userService.withdraw(1L);

        verify(petService).deletePetCascade(10L);
        verify(petService).deletePetCascade(20L);
        verify(commentRepository).detachUserFromComments(1L);
        verify(userRepository).save(user);
        assertTrue(user.isDeleted());
    }
}

