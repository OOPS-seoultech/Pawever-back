package com.pawever.backend.checklist;

import com.pawever.backend.checklist.dto.ChecklistProgressResponse;
import com.pawever.backend.checklist.dto.ChecklistResponse;
import com.pawever.backend.checklist.entity.ChecklistItem;
import com.pawever.backend.checklist.entity.PetChecklist;
import com.pawever.backend.checklist.repository.ChecklistItemRepository;
import com.pawever.backend.checklist.repository.PetChecklistRepository;
import com.pawever.backend.checklist.service.ChecklistService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChecklistServiceTest {

    @Mock
    private ChecklistItemRepository checklistItemRepository;
    @Mock
    private PetChecklistRepository petChecklistRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private UserPetRepository userPetRepository;

    @InjectMocks
    private ChecklistService checklistService;

    private final Long userId = 1L;
    private final Long petId = 1L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getChecklistProgress_success() {
        when(userPetRepository.existsByUserIdAndPetId(userId, petId)).thenReturn(true);

        ChecklistItem item1 = ChecklistItem.builder().id(1L).title("Item1").description("Desc1").build();
        ChecklistItem item2 = ChecklistItem.builder().id(2L).title("Item2").description("Desc2").build();
        when(checklistItemRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(item1, item2));

        PetChecklist pc1 = PetChecklist.builder().checklistItem(item1).completed(true).build();
        PetChecklist pc2 = PetChecklist.builder().checklistItem(item2).completed(false).build();
        when(petChecklistRepository.findByPetId(petId)).thenReturn(List.of(pc1, pc2));

        ChecklistProgressResponse response = checklistService.getChecklistProgress(userId, petId);

        assertEquals(50.0, response.getProgressPercent());
        assertEquals(2, response.getTotal());
        assertEquals(1, response.getCompleted());
    }

    @Test
    void getChecklistProgress_petNotOwned() {
        when(userPetRepository.existsByUserIdAndPetId(userId, petId)).thenReturn(false);
        assertThrows(CustomException.class, () -> checklistService.getChecklistProgress(userId, petId));
    }

    @Test
    void toggleChecklistItem_markCompleteAndUncheck() {
        UserPet userPet = UserPet.builder().isOwner(true).build();
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));

        Pet pet = Pet.builder().id(petId).build();
        when(petRepository.findById(petId)).thenReturn(Optional.of(pet));

        ChecklistItem item = ChecklistItem.builder().id(1L).title("Item").description("Desc").build();
        when(checklistItemRepository.findById(1L)).thenReturn(Optional.of(item));

        PetChecklist pc = PetChecklist.builder().pet(pet).checklistItem(item).completed(false).build();
        when(petChecklistRepository.findByPetIdAndChecklistItemId(petId, 1L)).thenReturn(Optional.of(pc));

        ChecklistResponse response = checklistService.toggleChecklistItem(userId, petId, 1L);
        assertTrue(response.getCompleted());

        ChecklistResponse response2 = checklistService.toggleChecklistItem(userId, petId, 1L);
        assertFalse(response2.getCompleted());
    }

    @Test
    void toggleChecklistItem_notOwner() {
        UserPet userPet = UserPet.builder().isOwner(false).build();
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));
        assertThrows(CustomException.class,
                () -> checklistService.toggleChecklistItem(userId, petId, 1L));
    }

    @Test
    void toggleChecklistItem_petNotFound() {
        UserPet userPet = UserPet.builder().isOwner(true).build();
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));
        when(petRepository.findById(petId)).thenReturn(Optional.empty());

        assertThrows(CustomException.class,
                () -> checklistService.toggleChecklistItem(userId, petId, 1L));
    }
}