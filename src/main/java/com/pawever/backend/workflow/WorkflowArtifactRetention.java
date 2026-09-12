package com.pawever.backend.workflow;

import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowArtifactRetention {
  private final ProductionArtifactRepository artifacts;
  private final GoodsSurveyFulfillmentRepository orders;
  private final GoodsSurveyPhotoStorage storage;
  private final WorkflowCommandRepository commands;
  private final jakarta.persistence.EntityManager entityManager;

  @Transactional
  public int purge(Instant now) {
    var candidates = artifacts.purgeCandidates(now, PageRequest.of(0, 100));
    for (var artifact : candidates) {
      // Synchronize with assignment and completion before deleting the object.
      var order = orders.lockByOrderNumber(artifact.getOrderNumber()).orElse(null);
      entityManager.refresh(artifact);
      if (order != null
          && !java.util.Set.of("CANCELED", "EXPIRED").contains(order.orderStatus())
          && (order.getDeleteAfter() == null || order.getDeleteAfter().isAfter(now))
          && (artifact.isConfirmed() || artifact.getExpiresAt().isAfter(now))) continue;
      storage.delete(artifact.getObjectKey());
      artifacts.delete(artifact);
    }
    commands.purgeOrderSnapshots(now);
    return candidates.size();
  }
}
