package com.pawever.backend.memorial.dto;

import com.pawever.backend.memorial.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class CommentResponse {
    private Long commentId;
    private Long userId;
    private String userNickname;
    private String userProfileImageUrl;
    private String content;
    private LocalDateTime createdAt;
    private boolean canDelete;

    public static CommentResponse of(Comment comment, boolean canDelete) {
        return CommentResponse.builder()
                .commentId(comment.getId())
                .userId(comment.getUser().getId())
                .userNickname(comment.getUser().getNickname())
                .userProfileImageUrl(comment.getUser().getProfileImageUrl())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .canDelete(canDelete)
                .build();
    }
}
