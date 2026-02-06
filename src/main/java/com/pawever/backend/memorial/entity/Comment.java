package com.pawever.backend.memorial.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memorial_id", nullable = false)
    private Memorial memorial;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public void updateContent(String content) {
        this.content = content;
    }
}
