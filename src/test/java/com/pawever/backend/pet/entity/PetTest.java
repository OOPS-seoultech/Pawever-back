package com.pawever.backend.pet.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PetTest {

    @Test
    void generateInviteCode_prePersist_setsInviteCodeWhenNull() {
        Pet pet = Pet.builder()
                .name("name")
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .inviteCode(null)
                .build();

        pet.generateInviteCode();

        assertNotNull(pet.getInviteCode());
        assertEquals(8, pet.getInviteCode().length());
        assertEquals(pet.getInviteCode(), pet.getInviteCode().toUpperCase());
    }

    @Test
    void generateInviteCode_prePersist_keepsExistingInviteCode() {
        Pet pet = Pet.builder()
                .name("name")
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .inviteCode("EXISTING")
                .build();

        pet.generateInviteCode();

        assertEquals("EXISTING", pet.getInviteCode());
    }

    @Test
    void regenerateInviteCode_changesInviteCode() {
        Pet pet = Pet.builder()
                .name("name")
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .inviteCode("AAAAAAAA")
                .build();

        pet.regenerateInviteCode();

        assertNotNull(pet.getInviteCode());
        assertEquals(8, pet.getInviteCode().length());
        assertNotEquals("AAAAAAAA", pet.getInviteCode());
    }

    @Test
    void activateEmergencyMode_setsFlagsAndDeathDateWhenNull() {
        Pet pet = Pet.builder()
                .name("name")
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .emergencyMode(false)
                .deathDate(null)
                .build();

        pet.activateEmergencyMode();

        assertTrue(pet.getEmergencyMode());
        assertEquals(LifecycleStatus.AFTER_FAREWELL, pet.getLifecycleStatus());
        assertNotNull(pet.getDeathDate());
    }

    @Test
    void activateEmergencyMode_keepsExistingDeathDate() {
        LocalDateTime existing = LocalDateTime.of(2026, 1, 1, 0, 0);
        Pet pet = Pet.builder()
                .name("name")
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .emergencyMode(false)
                .deathDate(existing)
                .build();

        pet.activateEmergencyMode();

        assertEquals(existing, pet.getDeathDate());
    }

    @Test
    void completeEmergencyMode_turnsOffEmergencyModeButKeepsAfterFarewellAndDeathDate() {
        Pet pet = Pet.builder()
                .name("name")
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .emergencyMode(true)
                .deathDate(null)
                .build();

        pet.completeEmergencyMode();

        assertFalse(pet.getEmergencyMode());
        assertEquals(LifecycleStatus.AFTER_FAREWELL, pet.getLifecycleStatus());
        assertNotNull(pet.getDeathDate());
    }

    @Test
    void deactivateEmergencyMode_resetsBeforeFarewellAndClearsDeathDate() {
        Pet pet = Pet.builder()
                .name("name")
                .lifecycleStatus(LifecycleStatus.AFTER_FAREWELL)
                .emergencyMode(true)
                .deathDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();

        pet.deactivateEmergencyMode();

        assertFalse(pet.getEmergencyMode());
        assertEquals(LifecycleStatus.BEFORE_FAREWELL, pet.getLifecycleStatus());
        assertNull(pet.getDeathDate());
    }
}

