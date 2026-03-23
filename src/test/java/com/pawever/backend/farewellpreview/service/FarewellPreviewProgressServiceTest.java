package com.pawever.backend.farewellpreview.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarewellPreviewProgressServiceTest {

    @Test
    void computeAfterFarewellProgress_appliesSupportPercentagesByCompletedItemCount() {
        assertEquals(74, FarewellPreviewProgressService.computeAfterFarewellProgress(5, true, 1, false));
        assertEquals(81, FarewellPreviewProgressService.computeAfterFarewellProgress(5, true, 2, false));
        assertEquals(88, FarewellPreviewProgressService.computeAfterFarewellProgress(5, true, 3, false));
        assertEquals(95, FarewellPreviewProgressService.computeAfterFarewellProgress(5, true, 4, false));
        assertEquals(100, FarewellPreviewProgressService.computeAfterFarewellProgress(5, true, 4, true));
    }

    @Test
    void computeAfterFarewellProgress_fallsBackToBelongingsAndAdministrationWhenSupportIsIncomplete() {
        assertEquals(67, FarewellPreviewProgressService.computeAfterFarewellProgress(5, true, 0, false));
        assertEquals(50, FarewellPreviewProgressService.computeAfterFarewellProgress(5, false, 0, false));
        assertEquals(20, FarewellPreviewProgressService.computeAfterFarewellProgress(2, false, 0, false));
        assertEquals(0, FarewellPreviewProgressService.computeAfterFarewellProgress(0, false, 0, false));
    }
}
