package com.pawever.backend.funeral.dto;

import com.pawever.backend.funeral.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private boolean canDelete;

    public static ReviewResponse of(Review review, boolean canDelete) {
        return ReviewResponse.builder()
                .reviewId(review.getId())
                .userId(review.getUser().getId())
                .userNickname(review.getUser().getNickname())
                .petName(review.getPet().getName())
                .rating(review.getRating())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .canDelete(canDelete)
                .build();
    }
}
