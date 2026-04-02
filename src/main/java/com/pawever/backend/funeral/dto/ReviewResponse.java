package com.pawever.backend.funeral.dto;

import com.pawever.backend.funeral.entity.Review;
import com.pawever.backend.global.util.UrlUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ReviewResponse {
    private Long reviewId;
    private Long userId;
    private String userNickname;
    private String petName;
    private Integer rating;
    private String content;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private boolean canDelete;

    public static ReviewResponse of(Review review, List<String> imageUrls, boolean canDelete) {
        return ReviewResponse.builder()
                .reviewId(review.getId())
                .userId(review.getUser().getId())
                .userNickname(review.getUser().getNickname())
                .petName(review.getPet().getName())
                .rating(review.getRating())
                .content(review.getContent())
                .imageUrls(imageUrls == null ? List.of() : imageUrls.stream().map(UrlUtils::toHttpsUrl).toList())
                .createdAt(review.getCreatedAt())
                .canDelete(canDelete)
                .build();
    }
}
