package com.pawever.backend.memorial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MemorialCursorPageInfo {
    private boolean hasNext;
    private String nextCursor;
}
