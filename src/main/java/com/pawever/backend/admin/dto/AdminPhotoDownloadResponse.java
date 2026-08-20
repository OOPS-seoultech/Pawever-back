package com.pawever.backend.admin.dto;

import java.time.Instant;
import java.util.List;

/** 사진 다운로드 링크. 잠깐만 열린다. */
public record AdminPhotoDownloadResponse(List<Item> photos) {

    public record Item(int slot, String url, Instant expiresAt) {
    }
}
