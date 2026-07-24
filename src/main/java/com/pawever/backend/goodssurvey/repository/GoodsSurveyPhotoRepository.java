package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GoodsSurveyPhotoRepository extends JpaRepository<GoodsSurveyPhoto, String> {

    Optional<GoodsSurveyPhoto> findByResponseIdAndClientFileId(String responseId, String clientFileId);

    Optional<GoodsSurveyPhoto> findByIdAndResponseId(String id, String responseId);

    @Query("""
            select count(photo)
            from GoodsSurveyPhoto photo
            where photo.responseId = :responseId
              and (
                    photo.status = :confirmed
                    or (
                        photo.status = :pending
                        and photo.uploadExpiresAt > :now
                    )
              )
            """)
    long countUsablePhotos(
            @Param("responseId") String responseId,
            @Param("now") Instant now,
            @Param("pending") GoodsSurveyPhotoStatus pending,
            @Param("confirmed") GoodsSurveyPhotoStatus confirmed
    );

    List<GoodsSurveyPhoto> findAllByIdInAndResponseIdAndStatus(
            Collection<String> ids,
            String responseId,
            GoodsSurveyPhotoStatus status
    );
}
