package com.pawever.backend.checklist.dto;

import com.pawever.backend.checklist.entity.ChecklistItem;
import com.pawever.backend.checklist.entity.PetChecklist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ChecklistResponse {
    private Long checklistItemId;
    private String title;
    private String description;
    private Boolean completed;

    public static ChecklistResponse of(ChecklistItem item, PetChecklist petChecklist) {
        return ChecklistResponse.builder()
                .checklistItemId(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .completed(petChecklist != null && petChecklist.getCompleted())
                .build();
    }
}
