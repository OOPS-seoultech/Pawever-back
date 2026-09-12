package com.pawever.backend.workflow;

import static org.mockito.Mockito.*;

import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRetentionTest {
  @Test
  void failedObjectDeletionKeepsMetadataForRetry() {
    var artifacts = mock(ProductionArtifactRepository.class);
    var orders = mock(GoodsSurveyFulfillmentRepository.class);
    var storage = mock(GoodsSurveyPhotoStorage.class);
    var commands = mock(WorkflowCommandRepository.class);
    var a =
        ProductionArtifact.pending(
            "expired-file",
            "PE-RETIRED",
            1L,
            1L,
            "MODEL_SOURCE",
            "model.stl",
            "application/octet-stream",
            10,
            Instant.EPOCH);
    when(artifacts.purgeCandidates(any(), any())).thenReturn(List.of(a));
    doThrow(new IllegalStateException("storage unavailable"))
        .when(storage)
        .delete(a.getObjectKey());
    var service =
        new WorkflowArtifactRetention(
            artifacts, orders, storage, commands, mock(jakarta.persistence.EntityManager.class));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.purge(Instant.now()))
        .isInstanceOf(IllegalStateException.class);
    verify(artifacts, never()).delete(any());
  }
}
