package com.pawever.backend.review.service;

import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.review.entity.ServiceReview;
import com.pawever.backend.review.entity.ServiceReviewImage;
import com.pawever.backend.review.repository.ServiceReviewImageRepository;
import com.pawever.backend.review.repository.ServiceReviewRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceReviewService {

    private final ServiceReviewRepository serviceReviewRepository;
    private final ServiceReviewImageRepository serviceReviewImageRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional
    public void createServiceReview(Long userId, String content, List<MultipartFile> images) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ServiceReview review = serviceReviewRepository.save(
                ServiceReview.builder()
                        .user(user)
                        .content(content)
                        .build()
        );

        if (images != null) {
            images.stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .forEach(file -> {
                        String url = storageService.upload(file, "service-reviews/" + review.getId());
                        serviceReviewImageRepository.save(
                                ServiceReviewImage.builder()
                                        .serviceReview(review)
                                        .imageUrl(url)
                                        .build()
                        );
                    });
        }
    }
}
