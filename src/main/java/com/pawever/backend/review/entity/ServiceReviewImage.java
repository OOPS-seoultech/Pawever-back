package com.pawever.backend.review.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_review_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ServiceReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_review_id", nullable = false)
    private ServiceReview serviceReview;

    @Column(nullable = false)
    private String imageUrl;
}
