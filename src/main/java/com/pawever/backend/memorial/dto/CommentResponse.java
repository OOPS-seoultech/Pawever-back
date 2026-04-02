package com.pawever.backend.memorial.dto;

import com.pawever.backend.global.util.UrlUtils;
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
    private CommentAuthorRole authorRole;
    private CommentAuthorPetResponse authorPet;
    private String content;
    private LocalDateTime createdAt;
    private boolean canDelete;

    public static CommentResponse of(
            Comment comment,
            boolean canDelete,
            CommentAuthorRole authorRole,
            CommentAuthorPetResponse authorPet
    ) {
        boolean isDeletedUser = comment.getUser() == null || comment.getUser().isDeleted();

        return CommentResponse.builder()
                .commentId(comment.getId())
                .userId(isDeletedUser ? null : comment.getUser().getId())
                .userNickname(isDeletedUser ? "탈퇴한 유저" : comment.getUser().getNickname())
                .userProfileImageUrl(isDeletedUser ? null : UrlUtils.toHttpsUrl(comment.getUser().getProfileImageUrl()))
                .authorRole(authorRole)
                .authorPet(isDeletedUser ? null : authorPet)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .canDelete(canDelete)
                .build();
    }
}
